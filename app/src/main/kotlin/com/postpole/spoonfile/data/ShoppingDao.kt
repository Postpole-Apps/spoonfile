package com.postpole.spoonfile.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingDao {
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

    @Query("SELECT id, title FROM recipes")
    fun observeRecipeTitles(): Flow<List<RecipeTitle>>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM shopping_items")
    suspend fun nextPosition(): Int

    @Insert
    suspend fun insert(item: ShoppingItemEntity): Long
}

data class RecipeTitle(val id: Long, val title: String)
