package com.postpole.spoonfile.data

import com.postpole.spoonfile.domain.QuantityScaler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/** One block of the shopping list: a recipe's items, or the hand-added "Other" block ([recipeId] null). */
data class ShoppingGroup(
    val recipeId: Long?,
    val title: String?,
    val items: List<ShoppingItemEntity>,
)

@Singleton
class ShoppingRepository @Inject constructor(private val dao: ShoppingDao) {
    /** Groups follow the order recipes were first added to the list; "Other" (hand-added) is always last. */
    fun observeGroups(): Flow<List<ShoppingGroup>> =
        combine(dao.observeAll(), dao.observeRecipeTitles()) { items, titles ->
            group(items, titles.associate { it.id to it.title })
        }

    /** Adds the given ingredient lines with quantities scaled by [scale]; section headers are skipped. */
    suspend fun addFromRecipe(recipeId: Long, ingredients: List<IngredientEntity>, scale: Double): Int {
        val lines = ingredients.filter { !it.isHeader && it.text.isNotBlank() }
            .map { QuantityScaler.scale(it.text.trim(), scale) }
        if (lines.isEmpty()) return 0
        val start = dao.nextPosition()
        val now = System.currentTimeMillis()
        dao.insertAll(
            lines.mapIndexed { i, text ->
                ShoppingItemEntity(text = text, recipeId = recipeId, position = start + i, createdAt = now)
            },
        )
        return lines.size
    }

    suspend fun addManual(text: String): Boolean {
        val clean = text.trim()
        if (clean.isEmpty()) return false
        dao.insert(ShoppingItemEntity(text = clean, position = dao.nextPosition(), createdAt = System.currentTimeMillis()))
        return true
    }

    suspend fun setChecked(id: Long, checked: Boolean) = dao.setChecked(id, checked)

    suspend fun clearChecked() = dao.deleteChecked()

    companion object {
        fun group(items: List<ShoppingItemEntity>, titles: Map<Long, String>): List<ShoppingGroup> {
            val ordered = items.sortedWith(compareBy({ it.position }, { it.id }))
            val (forRecipes, other) = ordered.partition { it.recipeId != null && it.recipeId in titles }
            val recipeGroups = forRecipes.groupBy { it.recipeId!! }
                .entries
                .sortedBy { (_, v) -> v.minOf { it.id } }
                .map { (id, v) -> ShoppingGroup(id, titles.getValue(id), v) }
            // Items whose recipe was deleted fall into Other as well.
            return recipeGroups + if (other.isEmpty()) emptyList() else listOf(ShoppingGroup(null, null, other))
        }
    }
}
