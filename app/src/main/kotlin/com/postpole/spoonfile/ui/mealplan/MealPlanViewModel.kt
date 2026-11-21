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
import com.postpole.spoonfile.data.ShoppingRepository
import com.postpole.spoonfile.domain.QuantityScaler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject

/** Whether the plan may be used. [Loading] until the entitlement has been read from DataStore. */
enum class MealPlanAccess { Loading, Pro, Locked }

data class MealPlanDay(val date: LocalDate, val isToday: Boolean, val entries: List<MealPlanRow>)

data class MealPlanUiState(
    val access: MealPlanAccess = MealPlanAccess.Loading,
    val weekStart: LocalDate = LocalDate.now(),
    val days: List<MealPlanDay> = emptyList(),
)

/** One-off results the screen shows as a snackbar. */
sealed interface MealPlanEvent {
    /** [count] lines went to the shopping list. */
    data class AddedToShopping(val count: Int) : MealPlanEvent
}

/** The first day of the week containing [date], per [firstDay]. */
internal fun weekStartOf(date: LocalDate, firstDay: DayOfWeek): LocalDate {
    val back = (date.dayOfWeek.value - firstDay.value + 7) % 7
    return date.minusDays(back.toLong())
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MealPlanViewModel @Inject constructor(
    private val plan: MealPlanRepository,
    private val recipes: RecipeRepository,
    private val shopping: ShoppingRepository,
    private val billing: BillingRepository,
    private val analytics: Analytics,
) : ViewModel() {
    // Overridable in tests; the app always uses the device locale and clock.
    internal var firstDayOfWeek: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    internal var todayProvider: () -> LocalDate = { LocalDate.now() }

    private val weekStart = MutableStateFlow(weekStartOf(todayProvider(), firstDayOfWeek))

    private val access: Flow<MealPlanAccess> = billing.isProOrUnknown.map { isPro ->
        when (isPro) {
            null -> MealPlanAccess.Loading
            true -> MealPlanAccess.Pro
            false -> MealPlanAccess.Locked
        }
    }

    /** Nothing is read for a free user: the plan is not shown to them at all. */
    private val rows: Flow<List<MealPlanRow>> = combine(weekStart, access) { start, a -> start to a }
        .flatMapLatest { (start, a) -> if (a == MealPlanAccess.Pro) plan.observeWeek(start) else flowOf(emptyList()) }

    val state: StateFlow<MealPlanUiState> = combine(weekStart, access, rows) { start, a, list ->
        val today = todayProvider()
        MealPlanUiState(
            access = a,
            weekStart = start,
            days = List(7) { i ->
                val date = start.plusDays(i.toLong())
                MealPlanDay(date, date == today, list.filter { it.date == date.toString() })
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MealPlanUiState(weekStart = weekStart.value))

    private val _events = Channel<MealPlanEvent>(Channel.BUFFERED)
    val events: Flow<MealPlanEvent> = _events.receiveAsFlow()

    /** Whether writes are allowed right now. False while the entitlement is still loading. */
    private val canWrite: Boolean get() = billing.isProOrUnknown.value == true

    private var viewLogged = false

    /** Logs `meal_plan_view` once per screen visit, for Pro users only, with the visible week's entry count. */
    fun onViewed(entryCount: Int) {
        if (viewLogged) return
        viewLogged = true
        analytics.logEvent(AnalyticsEvents.MEAL_PLAN_VIEW, mapOf(AnalyticsParams.ITEM_COUNT to entryCount))
    }

    fun previousWeek() = weekStart.update { it.minusWeeks(1) }

    fun nextWeek() = weekStart.update { it.plusWeeks(1) }

    fun thisWeek() {
        weekStart.value = weekStartOf(todayProvider(), firstDayOfWeek)
    }

    /** Recipe picker source: same search as the Recipes screen, unlimited by the free cap. */
    fun searchRecipes(query: String): Flow<List<RecipeEntity>> = recipes.observeList(query = query)

    suspend fun loadPhoto(name: String, maxEdge: Int): ImageBitmap? = recipes.loadPhoto(name, maxEdge)?.asImageBitmap()

    /** Plans [recipeId] on [date]. Returns false (and writes nothing) unless the user is Pro. */
    fun addRecipe(date: LocalDate, recipeId: Long): Boolean {
        if (!canWrite) return false
        viewModelScope.launch {
            plan.add(date, recipeId)
            analytics.logEvent(AnalyticsEvents.MEAL_PLAN_ADD, emptyMap())
        }
        return true
    }

    /** Removes one planned entry. Does nothing unless the user is Pro. */
    fun remove(entryId: Long): Boolean {
        if (!canWrite) return false
        viewModelScope.launch { plan.remove(entryId) }
        return true
    }

    /** Adds one recipe's ingredients (default servings) to the shopping list. */
    fun addRecipeToShopping(recipeId: Long): Boolean {
        if (!canWrite) return false
        viewModelScope.launch { finishShopping(linesFor(recipeId)) }
        return true
    }

    /** Adds every recipe planned on [date] (default servings, grouped per recipe). No-op for an empty day. */
    fun addDayToShopping(date: LocalDate): Boolean {
        if (!canWrite) return false
        viewModelScope.launch {
            val ids = plan.recipeIdsOn(date)
            if (ids.isEmpty()) return@launch
            finishShopping(ids.map { linesFor(it) })
        }
        return true
    }

    private class RecipeLines(val recipeId: Long, val lines: List<String>)

    private suspend fun linesFor(recipeId: Long): RecipeLines {
        val details = recipes.observeDetails(recipeId).first()
        val lines = details?.ingredients.orEmpty().filter { !it.isHeader }.map { QuantityScaler.scale(it.text, 1.0) }
        return RecipeLines(recipeId, lines)
    }

    private suspend fun finishShopping(one: RecipeLines) = finishShopping(listOf(one))

    private suspend fun finishShopping(all: List<RecipeLines>) {
        val added = all.sumOf { shopping.addRecipeLines(it.recipeId, it.lines) }
        if (added > 0) {
            analytics.logEvent(AnalyticsEvents.MEAL_PLAN_SHOP, mapOf(AnalyticsParams.ITEM_COUNT to added))
        }
        _events.send(MealPlanEvent.AddedToShopping(added))
    }

    private inline fun MutableStateFlow<LocalDate>.update(transform: (LocalDate) -> LocalDate) {
        value = transform(value)
    }
}
