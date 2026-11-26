package com.postpole.spoonfile.ui.recipe

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.spoonfile.analytics.Analytics
import com.postpole.spoonfile.analytics.AnalyticsEvents
import com.postpole.spoonfile.billing.BillingRepository
import com.postpole.spoonfile.data.MealPlanRepository
import com.postpole.spoonfile.data.RecipeRepository
import com.postpole.spoonfile.pro.NoOpProPromptSignals
import com.postpole.spoonfile.pro.ProPromptSignals
import com.postpole.spoonfile.data.RecipeWithDetails
import com.postpole.spoonfile.data.ShoppingRepository
import com.postpole.spoonfile.domain.QuantityScaler
import com.postpole.spoonfile.ui.mealplan.weekStartOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject

data class RecipeDetailUiState(
    val loading: Boolean = true,
    val details: RecipeWithDetails? = null,
    /** Servings currently shown; null when the recipe has no numeric servings. */
    val servings: Double? = null,
) {
    /** Factor applied to every ingredient quantity (1.0 when servings are unchanged or unknown). */
    val scale: Double
        get() {
            val base = details?.recipe?.servings
            return if (base != null && base > 0 && servings != null) servings / base else 1.0
        }
}

sealed interface RecipeDetailEvent {
    /** Adding to the meal plan is a Pro feature and the user is not Pro. */
    data object OpenPro : RecipeDetailEvent

    /** The recipe was planned on [date]. */
    data class AddedToMealPlan(val date: LocalDate) : RecipeDetailEvent

    /** The recipe is already planned on that day, so nothing was written. */
    data object AlreadyInMealPlan : RecipeDetailEvent
}

@HiltViewModel
class RecipeDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: RecipeRepository,
    private val shopping: ShoppingRepository,
    private val mealPlan: MealPlanRepository,
    private val billing: BillingRepository,
    private val analytics: Analytics,
    private val proPrompts: ProPromptSignals = NoOpProPromptSignals,
) : ViewModel() {
    val recipeId: Long = savedStateHandle.get<Long>("id") ?: 0L

    /** Overridable in tests. */
    internal var today: () -> LocalDate = { LocalDate.now() }
    internal var firstDayOfWeek: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek

    private val _events = Channel<RecipeDetailEvent>(Channel.BUFFERED)
    val events: Flow<RecipeDetailEvent> = _events.receiveAsFlow()

    private val servingsOverride = MutableStateFlow<Double?>(null)

    val state: StateFlow<RecipeDetailUiState> = combine(
        repository.observeDetails(recipeId),
        servingsOverride,
    ) { details, override ->
        RecipeDetailUiState(
            loading = false,
            details = details,
            servings = override ?: details?.recipe?.servings,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeDetailUiState())

    suspend fun loadPhoto(name: String, maxEdge: Int): ImageBitmap? = repository.loadPhoto(name, maxEdge)?.asImageBitmap()

    fun stepServings(delta: Int) {
        val current = shownServings(state.value) ?: return
        servingsOverride.value = (current + delta).coerceAtLeast(1.0)
    }

    /**
     * Servings shown now. Reads [servingsOverride] directly because [state] is a stateIn flow whose value
     * can lag a just-made change.
     */
    private fun shownServings(current: RecipeDetailUiState): Double? =
        servingsOverride.value ?: current.details?.recipe?.servings

    /** Adds the chosen ingredients to the shopping list using the quantities for the servings shown now. */
    fun addToShopping(ingredientIds: Set<Long>, onAdded: (Int) -> Unit) {
        val current = state.value
        val details = current.details ?: return
        val scale = current.copy(servings = shownServings(current)).scale
        val lines = details.ingredients
            .filter { !it.isHeader && it.id in ingredientIds }
            .map { QuantityScaler.scale(it.text, scale) }
        viewModelScope.launch { onAdded(shopping.addRecipeLines(recipeId, lines)) }
    }

    /** The 7 days of the week containing today, using the same week start as the meal plan. */
    fun weekDays(): List<LocalDate> {
        val start = weekStartOf(today(), firstDayOfWeek)
        return List(7) { start.plusDays(it.toLong()) }
    }

    /**
     * Plans this recipe on [date]. Waits for the entitlement to be known; a free user writes nothing and is
     * sent to Pro. A recipe already planned on [date] is not added twice.
     */
    fun addToMealPlan(date: LocalDate) {
        viewModelScope.launch {
            if (!billing.isProOrUnknown.filterNotNull().first()) {
                proPrompts.recordHardGate()
                _events.send(RecipeDetailEvent.OpenPro)
                return@launch
            }
            if (mealPlan.observeRange(date, date).first().any { it.entry.recipeId == recipeId }) {
                _events.send(RecipeDetailEvent.AlreadyInMealPlan)
                return@launch
            }
            mealPlan.add(date, recipeId)
            analytics.logEvent(AnalyticsEvents.MEAL_PLAN_ADD)
            _events.send(RecipeDetailEvent.AddedToMealPlan(date))
        }
    }

    /** Cook mode was finished with "Done" on the last step. */
    suspend fun onCookCompleted() {
        repository.markCooked(recipeId, System.currentTimeMillis())
        proPrompts.recordEngaged()
    }

    fun toggleFavorite() = update { it.copy(favorite = !it.favorite) }

    /** Rating is always kept within 0..5. */
    fun setRating(rating: Int) = update { it.copy(rating = rating.coerceIn(MIN_RATING, MAX_RATING)) }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.delete(recipeId)
            onDone()
        }
    }

    private fun update(change: (com.postpole.spoonfile.data.RecipeEntity) -> com.postpole.spoonfile.data.RecipeEntity) {
        val details = state.value.details ?: return
        viewModelScope.launch {
            repository.upsertWithChildren(
                change(details.recipe),
                details.ingredients,
                details.steps,
                details.categories.map { it.id },
            )
        }
    }

    companion object {
        const val MIN_RATING = 0
        const val MAX_RATING = 5
    }
}
