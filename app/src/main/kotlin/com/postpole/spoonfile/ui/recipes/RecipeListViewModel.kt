package com.postpole.spoonfile.ui.recipes

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.spoonfile.analytics.Analytics
import com.postpole.spoonfile.analytics.AnalyticsEvents
import com.postpole.spoonfile.analytics.AnalyticsParams
import com.postpole.spoonfile.billing.BillingRepository
import com.postpole.spoonfile.data.BackupNudge
import com.postpole.spoonfile.data.CategoryDao
import com.postpole.spoonfile.data.SettingsRepository
import com.postpole.spoonfile.data.CategoryWithCount
import com.postpole.spoonfile.data.Limits
import com.postpole.spoonfile.data.RecipeEntity
import com.postpole.spoonfile.data.RecipeRepository
import com.postpole.spoonfile.data.RecipeSort
import com.postpole.spoonfile.promo.ProPromptConfigSource
import com.postpole.spoonfile.promo.ProPromptInputs
import com.postpole.spoonfile.promo.ProPromptPolicy
import com.postpole.spoonfile.promo.ProPromptStore
import com.postpole.spoonfile.promo.ProPromptTrigger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The ways to start a new recipe from the Add sheet / empty state. */
enum class AddAction { PASTE_LINK, SCAN_PAGE, TYPE_IT_IN, IMPORT_FILE }

sealed interface RecipeListEvent {
    data object ShowAddSheet : RecipeListEvent

    /** The free cap is reached: send the user to the Pro screen. */
    data object OpenPro : RecipeListEvent
    data class Start(val action: AddAction) : RecipeListEvent
}

data class RecipeListUiState(
    val query: String = "",
    val favoritesOnly: Boolean = false,
    val sort: RecipeSort = RecipeSort.RECENT,
    val grid: Boolean = false,
    val selectedCategoryId: Long? = null,
    val recipes: List<RecipeEntity> = emptyList(),
    val categories: List<CategoryWithCount> = emptyList(),
    val count: Int = 0,
    val isPro: Boolean = false,
    val loaded: Boolean = false,
    val showBackupNudge: Boolean = false,
) {
    /** First [RecipeListViewModel.MAX_CATEGORY_TILES] categories; all of them when there are that many or fewer. */
    val visibleCategories: List<CategoryWithCount> get() = categories.take(RecipeListViewModel.MAX_CATEGORY_TILES)
    val showSeeAll: Boolean get() = categories.size > RecipeListViewModel.MAX_CATEGORY_TILES
    val canAdd: Boolean get() = isPro || count < Limits.FREE_RECIPE_LIMIT
    val showCounter: Boolean get() = !isPro
    val atLimit: Boolean get() = !isPro && count >= Limits.FREE_RECIPE_LIMIT
    val isEmptyLibrary: Boolean get() = loaded && count == 0
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecipeListViewModel @Inject constructor(
    private val repository: RecipeRepository,
    categoryDao: CategoryDao,
    private val billing: BillingRepository,
    private val settings: SettingsRepository,
    private val proPrompts: ProPromptStore,
    private val promptConfig: ProPromptConfigSource,
    private val analytics: Analytics,
) : ViewModel() {

    private data class Filters(
        val query: String = "",
        val favoritesOnly: Boolean = false,
        val sort: RecipeSort = RecipeSort.RECENT,
        val grid: Boolean = false,
        val categoryId: Long? = null,
    )

    private val filters = MutableStateFlow(Filters())

    private val recipes = filters.flatMapLatest {
        repository.observeList(it.query, it.categoryId, it.favoritesOnly, it.sort)
    }

    /** The monthly backup reminder: evaluated when the list is shown, not on a timer. */
    private val showBackupNudge: Flow<Boolean> = combine(
        settings.remindBackup,
        repository.count(),
        settings.lastExportAt,
        settings.nudgeDismissedAt,
    ) { remind, count, lastExport, dismissed ->
        BackupNudge.shouldShow(remind, count, lastExport, dismissed, System.currentTimeMillis())
    }

    private val base = combine(
        filters,
        recipes,
        categoryDao.observeAllWithCounts(),
        repository.count(),
        billing.isPro,
    ) { f, list, categories, count, isPro ->
        RecipeListUiState(
            query = f.query,
            favoritesOnly = f.favoritesOnly,
            sort = f.sort,
            grid = f.grid,
            selectedCategoryId = f.categoryId,
            recipes = list,
            categories = categories,
            count = count,
            isPro = isPro,
            loaded = true,
        )
    }

    val state: StateFlow<RecipeListUiState> = combine(base, showBackupNudge) { s, nudge ->
        s.copy(showBackupNudge = nudge)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeListUiState())

    fun dismissBackupNudge() {
        viewModelScope.launch { settings.dismissNudge(System.currentTimeMillis()) }
    }

    private val _events = Channel<RecipeListEvent>(Channel.BUFFERED)
    val events: Flow<RecipeListEvent> = _events.receiveAsFlow()

    suspend fun loadPhoto(name: String, maxEdge: Int): ImageBitmap? = repository.loadPhoto(name, maxEdge)?.asImageBitmap()

    fun setQuery(query: String) = filters.update { it.copy(query = query) }

    fun toggleFavoritesOnly() = filters.update { it.copy(favoritesOnly = !it.favoritesOnly) }

    fun setSort(sort: RecipeSort) = filters.update { it.copy(sort = sort) }

    fun toggleGrid() = filters.update { it.copy(grid = !it.grid) }

    /** Tapping the selected category again clears the filter. */
    fun selectCategory(id: Long?) = filters.update { it.copy(categoryId = if (it.categoryId == id) null else id) }

    /** FAB / counter-free entry: opens the Add sheet, or Pro when the free cap is reached. */
    fun onAddRequested() {
        if (state.value.canAdd) _events.trySend(RecipeListEvent.ShowAddSheet) else openProFromCap()
    }

    /** A row in the Add sheet (or an empty-state button). Anything that creates a recipe respects the cap. */
    fun onAddAction(action: AddAction) {
        if (state.value.canAdd) _events.trySend(RecipeListEvent.Start(action)) else openProFromCap()
    }

    /** The free cap sent the user to Pro: a hard gate, so automatic prompts rest for 48 hours. */
    private fun openProFromCap() {
        viewModelScope.launch { proPrompts.recordHardGate() }
        _events.trySend(RecipeListEvent.OpenPro)
    }

    // ---- automatic Pro prompts (card + sheet) ----

    private val _prompt = MutableStateFlow<ProPromptTrigger?>(null)

    /** The automatic Pro prompt chosen for this session, until it is dismissed or its CTA is tapped. */
    val prompt: StateFlow<ProPromptTrigger?> = _prompt.asStateFlow()

    /** Localized Play price, null until loaded. */
    val price: StateFlow<String?> = billing.price

    /**
     * The list is on screen (cold start or a return). Picks at most one automatic prompt for the session. A return
     * from another screen is the rest point for the sheet; a cold start never shows one.
     */
    fun onListShown() {
        val restPoint = proPrompts.consumeRestPoint()
        if (_prompt.value != null) return
        viewModelScope.launch {
            val isPro = billing.isProOrUnknown.filterNotNull().first()
            val s = state.first { it.loaded }
            val inputs = ProPromptInputs(
                now = proPrompts.now(),
                isPro = isPro,
                removeAdsOwned = billing.removeAdsOwned.value,
                sessionCount = proPrompts.sessionCount(),
                installAgeDays = proPrompts.installAgeDays(),
                recipeCount = s.count,
                atLimit = s.atLimit,
                showBackupNudge = s.showBackupNudge,
                restPoint = restPoint,
                promptShownThisSession = proPrompts.shownThisSession,
                interstitialShownThisSession = proPrompts.interstitialShownThisSession,
                history = proPrompts.history(),
            )
            val trigger = ProPromptPolicy.evaluate(promptConfig.current(), inputs) ?: return@launch
            proPrompts.recordShown(trigger)
            analytics.logEvent(AnalyticsEvents.PRO_PROMPT_SHOWN, promptParams(trigger))
            _prompt.value = trigger
        }
    }

    /** [method] is a `ProPromptMethod` value. */
    fun onPromptDismissed(method: String) {
        val trigger = _prompt.value ?: return
        _prompt.value = null
        analytics.logEvent(AnalyticsEvents.PRO_PROMPT_DISMISSED, promptParams(trigger) + (AnalyticsParams.METHOD to method))
        viewModelScope.launch { proPrompts.recordDismissed(trigger) }
    }

    /** The CTA was tapped: the prompt goes away (it is not a dismissal) and the caller opens Pro. */
    fun onPromptCta(): String? {
        val trigger = _prompt.value ?: return null
        _prompt.value = null
        analytics.logEvent(AnalyticsEvents.PRO_PROMPT_CTA_TAP, promptParams(trigger))
        return trigger.proScreenTrigger
    }

    private fun promptParams(trigger: ProPromptTrigger): Map<String, Any> =
        mapOf(AnalyticsParams.TRIGGER to trigger.wire, AnalyticsParams.SURFACE to trigger.surface)

    private inline fun MutableStateFlow<Filters>.update(transform: (Filters) -> Filters) {
        value = transform(value)
    }

    companion object {
        const val MAX_CATEGORY_TILES = 6
    }
}
