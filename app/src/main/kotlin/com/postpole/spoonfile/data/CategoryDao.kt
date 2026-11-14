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

    /** Returns the id of the category named [name] (case-insensitive), creating it if needed. */
    @Transaction
    open suspend fun getOrCreate(name: String): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Category name is blank" }
        return findByName(trimmed)?.id ?: insert(CategoryEntity(name = trimmed, sortOrder = nextSortOrder()))
    }
}
