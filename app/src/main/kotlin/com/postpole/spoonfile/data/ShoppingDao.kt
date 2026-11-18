package com.postpole.spoonfile.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** A shopping item plus the title of the recipe it came from (null for items added by hand). */
data class ShoppingRow(
    @Embedded val item: ShoppingItemEntity,
    val recipeTitle: String?,
)

@Dao
interface ShoppingDao {
    @Query(
        "SELECT s.*, r.title AS recipeTitle FROM shopping_items s " +
            "LEFT JOIN recipes r ON r.id = s.recipeId ORDER BY s.id",
    )
    fun observeRows(): Flow<List<ShoppingRow>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM shopping_items")
    suspend fun maxPosition(): Int

    @Query("SELECT * FROM shopping_items ORDER BY checked, position, id")
    fun observeAll(): Flow<List<ShoppingItemEntity>>

    @Query("SELECT * FROM shopping_items ORDER BY id")
    suspend fun getAll(): List<ShoppingItemEntity>

    @Query("DELETE FROM shopping_items")
    suspend fun deleteAll()

    @Insert
    suspend fun insertAll(items: List<ShoppingItemEntity>)

    @Query("UPDATE shopping_items SET checked = :checked WHERE id = :id")
    suspend fun setChecked(id: Long, checked: Boolean)

    @Query("DELETE FROM shopping_items WHERE checked = 1")
    suspend fun deleteChecked()
}
