package com.postpole.spoonfile.data

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShoppingRepository @Inject constructor(private val dao: ShoppingDao) {
    fun observeRows(): Flow<List<ShoppingRow>> = dao.observeRows()

    /** Adds already-scaled ingredient [lines] under [recipeId]; blank lines are skipped. Returns how many were added. */
    suspend fun addRecipeLines(recipeId: Long, lines: List<String>, now: Long = System.currentTimeMillis()): Int {
        val clean = lines.map { it.trim() }.filter { it.isNotEmpty() }
        if (clean.isEmpty()) return 0
        val base = dao.maxPosition() + 1
        dao.insertAll(
            clean.mapIndexed { i, text ->
                ShoppingItemEntity(text = text, recipeId = recipeId, position = base + i, createdAt = now)
            },
        )
        return clean.size
    }

    /** Adds a hand-typed item; it lands in "Other". Returns false for blank text. */
    suspend fun addByHand(text: String, now: Long = System.currentTimeMillis()): Boolean {
        val clean = text.trim()
        if (clean.isEmpty()) return false
        dao.insertAll(listOf(ShoppingItemEntity(text = clean, position = dao.maxPosition() + 1, createdAt = now)))
        return true
    }

    suspend fun setChecked(id: Long, checked: Boolean) = dao.setChecked(id, checked)

    suspend fun clearChecked() = dao.deleteChecked()
}
