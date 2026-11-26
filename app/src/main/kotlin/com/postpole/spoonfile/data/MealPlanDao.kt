package com.postpole.spoonfile.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** A plan entry plus what the screen shows for its recipe. */
data class MealPlanRow(
    @Embedded val entry: MealPlanEntryEntity,
    val recipeTitle: String,
    val imageFile: String?,
)

@Dao
abstract class MealPlanDao {
    /** Entries from [from] to [to] inclusive (ISO dates sort like calendar dates), in day / position order. */
    @Query(
        "SELECT m.*, r.title AS recipeTitle, r.imageFile AS imageFile FROM meal_plan_entries m " +
            "JOIN recipes r ON r.id = m.recipeId WHERE m.date >= :from AND m.date <= :to " +
            "ORDER BY m.date, m.position, m.id",
    )
    abstract fun observeRange(from: String, to: String): Flow<List<MealPlanRow>>

    /** Raw entries from [from] to [to] inclusive, in day / position order. */
    @Query("SELECT * FROM meal_plan_entries WHERE date >= :from AND date <= :to ORDER BY date, position, id")
    abstract suspend fun getRange(from: String, to: String): List<MealPlanEntryEntity>

    @Query("SELECT COALESCE(MAX(position), -1) FROM meal_plan_entries WHERE date = :date")
    abstract suspend fun maxPosition(date: String): Int

    @Query("SELECT * FROM meal_plan_entries ORDER BY id")
    abstract suspend fun getAll(): List<MealPlanEntryEntity>

    @Insert
    abstract suspend fun insert(entry: MealPlanEntryEntity): Long

    @Insert
    abstract suspend fun insertAll(entries: List<MealPlanEntryEntity>)

    @Query("DELETE FROM meal_plan_entries WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("DELETE FROM meal_plan_entries")
    abstract suspend fun deleteAll()

    /**
     * Copies each entry of the 7 days starting at [fromStart] onto the matching day of the week starting at
     * [toStart], all in one transaction (Room serialises writers, so concurrent calls cannot interleave and
     * duplicate). A recipe already planned on the destination day is skipped, and nothing is deleted.
     */
    @Transaction
    open suspend fun copyWeek(fromStart: LocalDate, toStart: LocalDate, now: Long): CopyWeekResult {
        val source = getRange(fromStart.toString(), fromStart.plusDays(6).toString())
        var copied = 0
        for (offset in 0L..6L) {
            val sourceKey = fromStart.plusDays(offset).toString()
            val destKey = toStart.plusDays(offset).toString()
            val planned = getRange(destKey, destKey).mapTo(mutableSetOf()) { it.recipeId }
            var position = maxPosition(destKey)
            for (entry in source.filter { it.date == sourceKey }) {
                if (!planned.add(entry.recipeId)) continue
                position++
                insert(MealPlanEntryEntity(date = destKey, recipeId = entry.recipeId, position = position, createdAt = now))
                copied++
            }
        }
        return CopyWeekResult(sourceCount = source.size, copied = copied)
    }
}
