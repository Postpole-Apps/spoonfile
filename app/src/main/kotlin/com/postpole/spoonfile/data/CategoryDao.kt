package com.postpole.spoonfile.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class CategoryWithCount(
    @Embedded val category: CategoryEntity,
    val recipeCount: Int,
)

@Dao
abstract class CategoryDao {

    @Query(
        """
        SELECT c.*, (SELECT COUNT(*) FROM recipe_categories rc WHERE rc.categoryId = c.id) AS recipeCount
        FROM categories c
        ORDER BY c.sortOrder ASC, c.name COLLATE NOCASE ASC
        """,
    )
    abstract fun observeAllWithCounts(): Flow<List<CategoryWithCount>>

    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    abstract suspend fun findByName(name: String): CategoryEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM categories")
    abstract suspend fun nextSortOrder(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(category: CategoryEntity): Long

    /** Returns the id of the category with this name (case-insensitive), creating it if needed. */
    @Transaction
    open suspend fun getOrCreate(name: String): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Category name must not be blank" }
        findByName(trimmed)?.let { return it.id }
        return insert(CategoryEntity(name = trimmed, sortOrder = nextSortOrder()))
    }
}
