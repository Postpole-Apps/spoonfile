package com.postpole.spoonfile.ui.recipe

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.spoonfile.data.RecipeRepository
import com.postpole.spoonfile.data.RecipeWithDetails
import com.postpole.spoonfile.data.ShoppingRepository
import com.postpole.spoonfile.domain.QuantityScaler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

@HiltViewModel
class RecipeDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: RecipeRepository,
    private val shopping: ShoppingRepository,
) : ViewModel() {
    val recipeId: Long = savedStateHandle.get<Long>("id") ?: 0L

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
