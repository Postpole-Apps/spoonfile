package com.postpole.spoonfile.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.spoonfile.data.ShoppingItemEntity
import com.postpole.spoonfile.data.ShoppingRepository
import com.postpole.spoonfile.data.ShoppingRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One block of the list. [recipeId] / [title] are null for the trailing "Other" group. */
data class ShoppingGroup(
    val recipeId: Long?,
    val title: String?,
    val items: List<ShoppingItemEntity>,
)

data class ShoppingListUiState(
    val loaded: Boolean = false,
    val groups: List<ShoppingGroup> = emptyList(),
) {
    val hasChecked: Boolean get() = groups.any { g -> g.items.any { it.checked } }
    val isEmpty: Boolean get() = groups.isEmpty()
}

/**
 * Recipe groups follow the order their recipes were first added to the list; hand-added items (and items
 * whose recipe was deleted) go last under "Other". Checked items stay where they are until cleared.
 */
internal fun buildGroups(rows: List<ShoppingRow>): List<ShoppingGroup> {
    val byRecipe = LinkedHashMap<Long, MutableList<ShoppingRow>>()
    val other = mutableListOf<ShoppingRow>()
    // Rows arrive ordered by id, so the first row of a recipe fixes that recipe's place.
    for (row in rows.sortedBy { it.item.id }) {
        val recipeId = row.item.recipeId
        if (recipeId == null || row.recipeTitle == null) other += row else byRecipe.getOrPut(recipeId) { mutableListOf() } += row
    }
    val inOrder = compareBy<ShoppingRow>({ it.item.position }, { it.item.id })
    val groups = byRecipe.map { (id, list) ->
        ShoppingGroup(id, list.first().recipeTitle, list.sortedWith(inOrder).map { it.item })
    }
    return if (other.isEmpty()) groups else groups + ShoppingGroup(null, null, other.sortedWith(inOrder).map { it.item })
}

/** Plain-text version of the list for the Share sheet. */
internal fun shareText(groups: List<ShoppingGroup>, otherLabel: String): String = groups.joinToString("\n\n") { g ->
    val header = g.title ?: otherLabel
    header + "\n" + g.items.joinToString("\n") { (if (it.checked) "☑ " else "☐ ") + it.text }
}

@HiltViewModel
class ShoppingListViewModel @Inject constructor(
    private val repository: ShoppingRepository,
) : ViewModel() {
    val state: StateFlow<ShoppingListUiState> = repository.observeRows()
        .map { ShoppingListUiState(loaded = true, groups = buildGroups(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShoppingListUiState())

    fun addItem(text: String) {
        viewModelScope.launch { repository.addByHand(text) }
    }

    fun setChecked(id: Long, checked: Boolean) {
        viewModelScope.launch { repository.setChecked(id, checked) }
    }

    fun clearChecked() {
        viewModelScope.launch { repository.clearChecked() }
    }
}
