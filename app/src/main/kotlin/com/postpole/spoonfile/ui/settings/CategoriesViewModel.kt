package com.postpole.spoonfile.ui.settings

import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.spoonfile.data.CategoryDao
import com.postpole.spoonfile.data.CategoryWithCount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Create, rename, reorder and delete categories. Deleting one never deletes recipes. */
@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val dao: CategoryDao,
) : ViewModel() {
    val categories: StateFlow<List<CategoryWithCount>> = dao.observeAllWithCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** True after a create/rename hit a name that already exists; cleared by [clearNameError]. */
    private val _nameTaken = MutableStateFlow(false)
    val nameTaken: StateFlow<Boolean> = _nameTaken

    fun clearNameError() = _nameTaken.update { false }

    /** Returns via [onDone] true when saved. Blank names are ignored. */
    fun create(name: String, onDone: (Boolean) -> Unit) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return onDone(false)
        viewModelScope.launch {
            // getOrCreate is case-insensitive: an existing name is a "taken" error, not a silent no-op.
            val exists = categories.value.any { it.category.name.equals(trimmed, ignoreCase = true) }
            if (exists) {
                _nameTaken.value = true
                onDone(false)
            } else {
                dao.getOrCreate(trimmed)
                onDone(true)
            }
        }
    }

    fun rename(id: Long, name: String, onDone: (Boolean) -> Unit) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return onDone(false)
        viewModelScope.launch {
            val clash = categories.value.any { it.category.id != id && it.category.name.equals(trimmed, ignoreCase = true) }
            if (clash) {
                _nameTaken.value = true
                onDone(false)
                return@launch
            }
            try {
                dao.rename(id, trimmed)
                onDone(true)
            } catch (_: SQLiteConstraintException) {
                _nameTaken.value = true
                onDone(false)
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { dao.delete(id) }
    }

    /** Moves the category at [index] by [delta] places (-1 up, +1 down). */
    fun move(index: Int, delta: Int) {
        val ids = categories.value.map { it.category.id }.toMutableList()
        val target = index + delta
        if (index !in ids.indices || target !in ids.indices) return
        ids.add(target, ids.removeAt(index))
        viewModelScope.launch { dao.reorder(ids) }
    }
}
