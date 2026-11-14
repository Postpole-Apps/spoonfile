package com.postpole.spoonfile.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.spoonfile.billing.ProStatus
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** Entries of the Add sheet / empty state. */
enum class AddAction { PASTE_LINK, SCAN, TYPE_IT, IMPORT_FILE }

sealed interface RecipeListEvent {
    /** The free cap is reached: go to the Pro screen instead. */
    data object OpenPro : RecipeListEvent

    data object ShowAddSheet : RecipeListEvent

    data class Open(val action: AddAction) : RecipeListEvent
}

/** First [MAX_TILES] categories as tiles, plus a "See all" tile only when there are more than [MAX_TILES]. */
data class CategoryTiles(val visible: List<CategoryWithCount>, val showSeeAll: Boolean) {
    companion object {
        const val MAX_TILES = 6

        fun of(all: List<CategoryWithCount>) = CategoryTiles(all.take(MAX_TILES), all.size > MAX_TILES)
    }
}

data class RecipeListState(
    val recipes: List<RecipeEntity> = emptyList(),
    val categories: List<CategoryWithCount> = emptyList(),
    val total: Int = 0,
    val isPro: Boolean = false,
    val query: String = "",
    val categoryId: Long? = null,
    val favoritesOnly: Boolean = false,
    val sort: RecipeSort = RecipeSort.RECENT,
    val grid: Boolean = false,
) {
    val limit: Int get() = Limits.FREE_RECIPE_LIMIT
    val canAdd: Boolean get() = isPro || total < limit
    val showCounter: Boolean get() = !isPro
    val atCap: Boolean get() = !canAdd
    val tiles: CategoryTiles get() = CategoryTiles.of(categories)
    val isFiltered: Boolean get() = query.isNotBlank() || categoryId != null || favoritesOnly
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecipeListViewModel @Inject constructor(
    private val repo: RecipeRepository,
    categoryDao: CategoryDao,
    private val pro: ProStatus,
) : ViewModel() {

    private data class Filters(
        val query: String = "",
        val categoryId: Long? = null,
        val favoritesOnly: Boolean = false,
        val sort: RecipeSort = RecipeSort.RECENT,
        val grid: Boolean = false,
    )

    private val filters = MutableStateFlow(Filters())

    private val recipes: Flow<List<RecipeEntity>> = filters.flatMapLatest {
        repo.observeList(it.query, it.categoryId, it.favoritesOnly, it.sort)
    }

    val state: StateFlow<RecipeListState> = combine(
        filters,
        recipes,
        categoryDao.observeAllWithCounts(),
        repo.count(),
        pro.isPro,
    ) { f, list, categories, total, isPro ->
        RecipeListState(
            recipes = list,
            categories = categories,
            total = total,
            isPro = isPro,
            query = f.query,
            categoryId = f.categoryId,
            favoritesOnly = f.favoritesOnly,
            sort = f.sort,
            grid = f.grid,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeListState())

    private val _events = Channel<RecipeListEvent>(Channel.BUFFERED)
    val events: Flow<RecipeListEvent> = _events.receiveAsFlow()

    fun photoFile(name: String): File = repo.photoFile(name)

    fun setQuery(query: String) = filters.update { it.copy(query = query) }

    fun toggleCategory(id: Long) = filters.update { it.copy(categoryId = if (it.categoryId == id) null else id) }

    fun toggleFavoritesOnly() = filters.update { it.copy(favoritesOnly = !it.favoritesOnly) }

    fun setSort(sort: RecipeSort) = filters.update { it.copy(sort = sort) }

    fun toggleGrid() = filters.update { it.copy(grid = !it.grid) }

    /** FAB: opens the Add sheet, or the Pro screen when the free cap is reached. */
    fun onAddClicked() {
        viewModelScope.launch {
            _events.send(if (repo.canAdd(pro.isPro.value)) RecipeListEvent.ShowAddSheet else RecipeListEvent.OpenPro)
        }
    }

    /** An Add sheet / empty state action. All of them create a recipe, so all of them respect the cap. */
    fun onAddAction(action: AddAction) {
        viewModelScope.launch {
            _events.send(
                if (repo.canAdd(pro.isPro.value)) RecipeListEvent.Open(action) else RecipeListEvent.OpenPro,
            )
        }
    }
}
