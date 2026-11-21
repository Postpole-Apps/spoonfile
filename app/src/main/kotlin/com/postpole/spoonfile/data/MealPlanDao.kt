package com.postpole.spoonfile.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** A plan entry plus what the screen shows for its recipe. */
data class MealPlanRow(
    @Embedded val entry: MealPlanEntryEntity,
    val recipeTitle: String,
    val imageFile: String?,
)

@Dao
interface MealPlanDao {
    /** Entries from [from] to [to] inclusive (ISO dates sort like calendar dates), in day / position order. */
    @Query(
        "SELECT m.*, r.title AS recipeTitle, r.imageFile AS imageFile FROM meal_plan_entries m " +
            "JOIN recipes r ON r.id = m.recipeId WHERE m.date >= :from AND m.date <= :to " +
            "ORDER BY m.date, m.position, m.id",
    )
    fun observeRange(from: String, to: String): Flow<List<MealPlanRow>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM meal_plan_entries WHERE date = :date")
    suspend fun maxPosition(date: String): Int

    @Query("SELECT * FROM meal_plan_entries ORDER BY id")
    suspend fun getAll(): List<MealPlanEntryEntity>

    @Insert
    suspend fun insert(entry: MealPlanEntryEntity): Long

    @Insert
    suspend fun insertAll(entries: List<MealPlanEntryEntity>)

    @Query("DELETE FROM meal_plan_entries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM meal_plan_entries")
    suspend fun deleteAll()
}
