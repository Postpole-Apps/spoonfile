package com.postpole.spoonfile.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.spoonfile.data.CategoryDao
import com.postpole.spoonfile.data.CategoryWithCount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Create, rename, reorder and delete categories. Deleting one never deletes its recipes. */
@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val dao: CategoryDao,
) : ViewModel() {
    val categories: StateFlow<List<CategoryWithCount>> = dao.observeAllWithCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Calls [onResult] with false when the name is blank or already used by another category. */
    fun create(name: String, onResult: (Boolean) -> Unit) {
        val clean = name.trim()
        viewModelScope.launch {
            if (clean.isEmpty() || dao.nameTaken(clean, exceptId = 0L)) {
                onResult(false)
            } else {
                dao.getOrCreate(clean)
                onResult(true)
            }
        }
    }

    fun rename(id: Long, name: String, onResult: (Boolean) -> Unit) {
        val clean = name.trim()
        viewModelScope.launch {
            if (clean.isEmpty() || dao.nameTaken(clean, exceptId = id)) {
                onResult(false)
            } else {
                dao.rename(id, clean)
                onResult(true)
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { dao.delete(id) }
    }

    fun move(id: Long, delta: Int) {
        val ids = categories.value.map { it.category.id }.toMutableList()
        val from = ids.indexOf(id)
        val to = from + delta
        if (from < 0 || to !in ids.indices) return
        ids.add(to, ids.removeAt(from))
        viewModelScope.launch { dao.reorder(ids) }
    }
}
