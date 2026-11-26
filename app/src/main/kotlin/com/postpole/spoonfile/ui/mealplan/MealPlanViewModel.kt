package com.postpole.spoonfile.ui.mealplan

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.spoonfile.analytics.Analytics
import com.postpole.spoonfile.analytics.AnalyticsEvents
import com.postpole.spoonfile.analytics.AnalyticsParams
import com.postpole.spoonfile.billing.BillingRepository
import com.postpole.spoonfile.data.MealPlanRepository
import com.postpole.spoonfile.data.MealPlanRow
import com.postpole.spoonfile.data.RecipeEntity
import com.postpole.spoonfile.data.RecipeRepository
import com.postpole.spoonfile.pro.NoOpProPromptSignals
import com.postpole.spoonfile.pro.ProPromptSignals
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject

data class MealPlanDay(val date: LocalDate, val isToday: Boolean, val entries: List<MealPlanRow>)

data class MealPlanUiState(
    /** False until the Pro entitlement has been read. */
    val entitlementKnown: Boolean = false,
    val isPro: Boolean = false,
    val weekStart: LocalDate = LocalDate.now(),
    val days: List<MealPlanDay> = emptyList(),
    /** The day the recipe picker is open for; null when it is closed. */
    val pickerDate: LocalDate? = null,
    val pickerQuery: String = "",
    val pickerRecipes: List<RecipeEntity> = emptyList(),
)

sealed interface MealPlanEvent {
    /** The plan is a Pro feature and the user is not Pro. */
    data object OpenPro : MealPlanEvent

    /** Ingredients were added to the shopping list ([count] lines; 0 when there was nothing to add). */
    data class AddedToShopping(val count: Int) : MealPlanEvent

    /** Last week's meals were copied ([count] new entries; may be 0 if all were already planned). */
    data class Repeated(val count: Int) : MealPlanEvent

    /** Last week has no meals, so nothing was written. */
    data object RepeatEmpty : MealPlanEvent

    /** The user asked to share the visible week as text (Pro only). */
    data object ShareWeek : MealPlanEvent
}

/** First day of the week containing [date] under [firstDay]. */
internal fun weekStartOf(date: LocalDate, firstDay: DayOfWeek): LocalDate {
    val back = (date.dayOfWeek.value - firstDay.value + 7) % 7
    return date.minusDays(back.toLong())
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MealPlanViewModel @Inject constructor(
    private val plan: MealPlanRepository,
    private val recipes: RecipeRepository,
    private val billing: BillingRepository,
    private val analytics: Analytics,
    private val proPrompts: ProPromptSignals = NoOpProPromptSignals,
) : ViewModel() {

    /** Overridable in tests. */
    internal var today: () -> LocalDate = { LocalDate.now() }
    internal var firstDayOfWeek: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek

    private val weekStart = MutableStateFlow(weekStartOf(today(), firstDayOfWeek))
    private val pickerDate = MutableStateFlow<LocalDate?>(null)
    private val pickerQuery = MutableStateFlow("")

    private val entries: Flow<List<MealPlanRow>> = combine(weekStart, billing.isProOrUnknown) { start, pro -> start to pro }
        .flatMapLatest { (start, pro) ->
            // A free user never sees (or edits) a plan.
            if (pro == true) plan.observeRange(start, start.plusDays(6)) else flowOf(emptyList())
        }

    private val pickerList: Flow<List<RecipeEntity>> = combine(pickerDate, pickerQuery) { d, q -> d to q }
        .flatMapLatest { (d, q) -> if (d == null) flowOf(emptyList()) else recipes.observeList(query = q) }

    val state: StateFlow<MealPlanUiState> = combine(
        weekStart,
        entries,
        billing.isProOrUnknown,
        combine(pickerDate, pickerQuery) { d, q -> d to q },
        pickerList,
    ) { start, rows, pro, (pDate, pQuery), pList ->
        val now = today()
        MealPlanUiState(
            entitlementKnown = pro != null,
            isPro = pro == true,
            weekStart = start,
            days = List(7) { i ->
                val date = start.plusDays(i.toLong())
                MealPlanDay(date, date == now, rows.filter { it.entry.date == date.toString() })
            },
            pickerDate = pDate,
            pickerQuery = pQuery,
            pickerRecipes = pList,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MealPlanUiState(weekStart = weekStart.value))

    private val _events = Channel<MealPlanEvent>(Channel.BUFFERED)
    val events: Flow<MealPlanEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            if (billing.isProOrUnknown.filterNotNull().first()) {
                val count = plan.observeRange(weekStart.value, weekStart.value.plusDays(6)).first().size
                analytics.logEvent(AnalyticsEvents.MEAL_PLAN_VIEW, mapOf(AnalyticsParams.ITEM_COUNT to count))
            } else {
                proPrompts.recordHardGate()
                _events.send(MealPlanEvent.OpenPro)
            }
        }
    }

    suspend fun loadPhoto(name: String, maxEdge: Int): ImageBitmap? = recipes.loadPhoto(name, maxEdge)?.asImageBitmap()

    fun previousWeek() = weekStart.update { it.minusWeeks(1) }

    fun nextWeek() = weekStart.update { it.plusWeeks(1) }

    fun thisWeek() {
        weekStart.value = weekStartOf(today(), firstDayOfWeek)
    }

    fun openPicker(date: LocalDate) {
        if (!requirePro()) return
        pickerQuery.value = ""
        pickerDate.value = date
    }

    fun closePicker() {
        pickerDate.value = null
    }

    fun setPickerQuery(query: String) {
        pickerQuery.value = query
    }

    /** Plans [recipeId] on the day the picker was opened for. Does nothing unless the user is Pro. */
    fun addRecipe(recipeId: Long) {
        val date = pickerDate.value ?: return
        addRecipe(date, recipeId)
        pickerDate.value = null
    }

    /** Plans [recipeId] on [date]. Does nothing (and sends the user to Pro) unless the user is Pro. */
    fun addRecipe(date: LocalDate, recipeId: Long) {
        if (!requirePro()) return
        viewModelScope.launch {
            plan.add(date, recipeId)
            analytics.logEvent(AnalyticsEvents.MEAL_PLAN_ADD)
        }
    }

    /** Copies last week's meals onto the visible week, skipping recipes already planned on that day. */
    fun repeatLastWeek() {
        if (!requirePro()) return
        viewModelScope.launch {
            val start = weekStart.value
            val result = plan.copyWeek(start.minusWeeks(1), start)
            if (result.sourceCount == 0) {
                _events.send(MealPlanEvent.RepeatEmpty)
                return@launch
            }
            if (result.copied > 0) {
                analytics.logEvent(AnalyticsEvents.MEAL_PLAN_ADD, mapOf(AnalyticsParams.ITEM_COUNT to result.copied))
            }
            _events.send(MealPlanEvent.Repeated(result.copied))
        }
    }

    /** Asks the screen to open the share chooser for the visible week. Does nothing unless the user is Pro. */
    fun shareWeek() {
        if (!requirePro()) return
        _events.trySend(MealPlanEvent.ShareWeek)
    }

    fun remove(entryId: Long) {
        if (!requirePro()) return
        viewModelScope.launch { plan.remove(entryId) }
    }

    /** Adds one planned recipe's ingredients to the shopping list at default servings. */
    fun addEntryToShopping(recipeId: Long) = shop(listOf(recipeId))

    /** Adds every recipe planned on [day] (in order) to the shopping list at default servings. */
    fun addDayToShopping(day: MealPlanDay) = shop(day.entries.map { it.entry.recipeId })

    /**
     * Adds every planned recipe in the visible week (day, then position; repeats kept) to the shopping
     * list at default servings. An empty week writes nothing and reports 0 lines.
     */
    fun addWeekToShopping() {
        if (!requirePro()) return
        val recipeIds = state.value.days.flatMap { day -> day.entries.map { it.entry.recipeId } }
        if (recipeIds.isEmpty()) {
            _events.trySend(MealPlanEvent.AddedToShopping(0))
            return
        }
        shop(recipeIds)
    }

    private fun shop(recipeIds: List<Long>) {
        if (recipeIds.isEmpty() || !requirePro()) return
        viewModelScope.launch {
            val count = plan.addToShopping(recipeIds)
            analytics.logEvent(AnalyticsEvents.MEAL_PLAN_SHOP, mapOf(AnalyticsParams.ITEM_COUNT to count))
            _events.send(MealPlanEvent.AddedToShopping(count))
        }
    }

    private fun requirePro(): Boolean {
        if (billing.isPro.value) return true
        viewModelScope.launch { proPrompts.recordHardGate() }
        _events.trySend(MealPlanEvent.OpenPro)
        return false
    }
}
