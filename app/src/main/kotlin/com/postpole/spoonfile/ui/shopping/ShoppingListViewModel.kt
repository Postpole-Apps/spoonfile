package com.postpole.spoonfile.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.spoonfile.data.ShoppingGroup
import com.postpole.spoonfile.data.ShoppingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShoppingUiState(
    val loading: Boolean = true,
    val groups: List<ShoppingGroup> = emptyList(),
) {
    val isEmpty: Boolean get() = groups.isEmpty()
    val hasChecked: Boolean get() = groups.any { g -> g.items.any { it.checked } }
}

@HiltViewModel
class ShoppingListViewModel @Inject constructor(
    private val repository: ShoppingRepository,
) : ViewModel() {
    val state: StateFlow<ShoppingUiState> = repository.observeGroups()
        .map { ShoppingUiState(loading = false, groups = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShoppingUiState())

    fun toggle(id: Long, checked: Boolean) {
        viewModelScope.launch { repository.setChecked(id, checked) }
    }

    fun clearChecked() {
        viewModelScope.launch { repository.clearChecked() }
    }

    /** Items added by hand always land in "Other". */
    fun addItem(text: String) {
        viewModelScope.launch { repository.addManual(text) }
    }

    companion object {
        /** Plain-text share: unchecked items only, grouped under their recipe titles. */
        fun shareText(groups: List<ShoppingGroup>, otherLabel: String): String =
            groups.mapNotNull { g ->
                val open = g.items.filter { !it.checked }
                if (open.isEmpty()) null else (g.title ?: otherLabel) + "\n" + open.joinToString("\n") { "• " + it.text }
            }.joinToString("\n\n")
    }
}
