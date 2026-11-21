package com.postpole.spoonfile.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class MealPlanDao {

    /** Entries from [start] to [end] inclusive (ISO dates sort like dates), in day then plan order. */
    @Query(
        """
        SELECT m.id AS id, m.date AS date, m.recipeId AS recipeId, m.position AS position,
               r.title AS title, r.imageFile AS imageFile
        FROM meal_plan_entries m JOIN recipes r ON r.id = m.recipeId
        WHERE m.date >= :start AND m.date <= :end
        ORDER BY m.date, m.position, m.id
        """,
    )
    abstract fun observeRange(start: String, end: String): Flow<List<MealPlanRow>>

    @Query("SELECT recipeId FROM meal_plan_entries WHERE date = :date ORDER BY position, id")
    abstract suspend fun recipeIdsOn(date: String): List<Long>

    @Query("SELECT COALESCE(MAX(position), -1) FROM meal_plan_entries WHERE date = :date")
    protected abstract suspend fun maxPosition(date: String): Int

    @Insert
    protected abstract suspend fun insert(entry: MealPlanEntity): Long

    /** Appends [recipeId] to the end of [date]. Returns the new entry's id. */
    @Transaction
    open suspend fun append(date: String, recipeId: Long, now: Long): Long =
        insert(MealPlanEntity(date = date, recipeId = recipeId, position = maxPosition(date) + 1, createdAt = now))

    @Query("DELETE FROM meal_plan_entries WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("SELECT * FROM meal_plan_entries ORDER BY id")
    abstract suspend fun getAll(): List<MealPlanEntity>

    @Query("SELECT COUNT(*) FROM meal_plan_entries")
    abstract suspend fun countNow(): Int

    @Query("DELETE FROM meal_plan_entries")
    abstract suspend fun deleteAll()

    @Insert
    abstract suspend fun insertAll(items: List<MealPlanEntity>)
}
