package com.postpole.spoonfile.ui.recipe

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.spoonfile.data.RecipeRepository
import com.postpole.spoonfile.data.RecipeWithDetails
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class RecipeDetailState(
    val loaded: Boolean = false,
    val details: RecipeWithDetails? = null,
    /** Servings currently shown; null when the recipe has no servings number (so no scaling). */
    val targetServings: Double? = null,
)

/** Shared by the detail and cook-mode routes (each gets its own instance keyed by the `id` argument). */
@HiltViewModel
class RecipeDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: RecipeRepository,
) : ViewModel() {
    val recipeId: Long = savedStateHandle.get<Long>(ARG_ID) ?: 0L

    private val servingsOverride = MutableStateFlow<Double?>(null)

    val state: StateFlow<RecipeDetailState> = combine(repo.observeDetails(recipeId), servingsOverride) { d, override ->
        RecipeDetailState(loaded = true, details = d, targetServings = override ?: d?.recipe?.servings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeDetailState())

    fun photoFile(name: String): File = repo.photoFile(name)

    fun changeServings(delta: Int) {
        val current = state.value.targetServings ?: return
        servingsOverride.update { (current + delta).coerceAtLeast(1.0) }
    }

    fun toggleFavorite() {
        val recipe = state.value.details?.recipe ?: return
        viewModelScope.launch { repo.setFavorite(recipe.id, !recipe.favorite) }
    }

    fun setRating(rating: Int) {
        viewModelScope.launch { repo.setRating(recipeId, rating.coerceIn(0, 5)) }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.delete(recipeId)
            onDone()
        }
    }

    companion object {
        const val ARG_ID = "id"
    }
}
