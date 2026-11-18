package com.postpole.spoonfile.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CategoryDao {

    @Query(
        """
        SELECT c.*, (SELECT COUNT(*) FROM recipe_categories rc WHERE rc.categoryId = c.id) AS recipeCount
        FROM categories c ORDER BY c.sortOrder, c.name COLLATE NOCASE
        """,
    )
    abstract fun observeAllWithCounts(): Flow<List<CategoryWithCount>>

    @Query("UPDATE categories SET name = :name WHERE id = :id")
    abstract suspend fun rename(id: Long, name: String)

    @Query("UPDATE categories SET sortOrder = :order WHERE id = :id")
    protected abstract suspend fun setSortOrder(id: Long, order: Int)

    /** Recipes keep existing; only the links to this category are removed (cascade). */
    @Query("DELETE FROM categories WHERE id = :id")
    abstract suspend fun delete(id: Long)

    /** Renumbers the categories 0..n-1 in the given order. */
    @Transaction
    open suspend fun reorder(idsInOrder: List<Long>) {
        idsInOrder.forEachIndexed { index, id -> setSortOrder(id, index) }
    }

    @Query("SELECT * FROM categories ORDER BY id")
    abstract suspend fun allCategories(): List<CategoryEntity>

    @Query("DELETE FROM categories")
    abstract suspend fun deleteAllCategories()

    @Insert
    abstract suspend fun restoreCategories(items: List<CategoryEntity>)

    @Query("SELECT * FROM categories WHERE name = :name COLLATE NOCASE LIMIT 1")
    protected abstract suspend fun findByName(name: String): CategoryEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM categories")
    protected abstract suspend fun nextSortOrder(): Int

    @Insert
    protected abstract suspend fun insert(category: CategoryEntity): Long

    /** Returns the id of the category named [name] (case-insensitive), creating it if needed. */
    @Transaction
    open suspend fun getOrCreate(name: String): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Category name is blank" }
        return findByName(trimmed)?.id ?: insert(CategoryEntity(name = trimmed, sortOrder = nextSortOrder()))
    }
}
