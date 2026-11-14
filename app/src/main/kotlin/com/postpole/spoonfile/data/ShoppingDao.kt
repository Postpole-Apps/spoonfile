package com.postpole.spoonfile.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_items ORDER BY checked, position, id")
    fun observeAll(): Flow<List<ShoppingItemEntity>>

    @Insert
    suspend fun insertAll(items: List<ShoppingItemEntity>)

    @Query("UPDATE shopping_items SET checked = :checked WHERE id = :id")
    suspend fun setChecked(id: Long, checked: Boolean)

    @Query("DELETE FROM shopping_items WHERE checked = 1")
    suspend fun deleteChecked()
}
