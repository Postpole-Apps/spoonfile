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

    @Query("SELECT * FROM categories WHERE name = :name COLLATE NOCASE LIMIT 1")
    protected abstract suspend fun findByName(name: String): CategoryEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM categories")
    protected abstract suspend fun nextSortOrder(): Int

    @Insert
    protected abstract suspend fun insert(category: CategoryEntity): Long

    @Query("UPDATE categories SET name = :name WHERE id = :id")
    abstract suspend fun rename(id: Long, name: String)

    /** Recipes keep existing; only their link to this category goes (foreign-key cascade). */
    @Query("DELETE FROM categories WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("UPDATE categories SET sortOrder = :sortOrder WHERE id = :id")
    protected abstract suspend fun setSortOrder(id: Long, sortOrder: Int)

    /** Stores [orderedIds] as the new order, first to last. */
    @Transaction
    open suspend fun reorder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> setSortOrder(id, index) }
    }

    /** True when another category (not [exceptId]) already uses [name], ignoring case. */
    @Query("SELECT EXISTS(SELECT 1 FROM categories WHERE name = :name COLLATE NOCASE AND id != :exceptId)")
    abstract suspend fun nameTaken(name: String, exceptId: Long): Boolean

    /** Returns the id of the category named [name] (case-insensitive), creating it if needed. */
    @Transaction
    open suspend fun getOrCreate(name: String): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Category name is blank" }
        return findByName(trimmed)?.id ?: insert(CategoryEntity(name = trimmed, sortOrder = nextSortOrder()))
    }
}
