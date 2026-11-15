package com.postpole.spoonfile.ui.recipes

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.spoonfile.billing.BillingRepository
import com.postpole.spoonfile.data.CategoryDao
import com.postpole.spoonfile.data.CategoryWithCount
import com.postpole.spoonfile.data.Limits
import com.postpole.spoonfile.data.RecipeEntity
import com.postpole.spoonfile.data.RecipeRepository
import com.postpole.spoonfile.data.RecipeSort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
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
    billing: BillingRepository,
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

    val state: StateFlow<RecipeListUiState> = combine(
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeListUiState())

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
        _events.trySend(if (state.value.canAdd) RecipeListEvent.ShowAddSheet else RecipeListEvent.OpenPro)
    }

    /** A row in the Add sheet (or an empty-state button). Anything that creates a recipe respects the cap. */
    fun onAddAction(action: AddAction) {
        _events.trySend(if (state.value.canAdd) RecipeListEvent.Start(action) else RecipeListEvent.OpenPro)
    }

    private inline fun MutableStateFlow<Filters>.update(transform: (Filters) -> Filters) {
        value = transform(value)
    }

    companion object {
        const val MAX_CATEGORY_TILES = 6
    }
}
