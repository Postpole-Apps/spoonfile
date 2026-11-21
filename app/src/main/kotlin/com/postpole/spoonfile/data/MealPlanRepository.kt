package com.postpole.spoonfile.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The weekly meal plan. Data access only: the Pro check lives in the ViewModel, which is the only caller
 * that writes.
 */
@Singleton
class MealPlanRepository @Inject constructor(private val dao: MealPlanDao) {
    /** Entries for the [days] consecutive days starting at [start]. */
    fun observeWeek(start: LocalDate, days: Int = 7): Flow<List<MealPlanRow>> =
        dao.observeRange(start.toString(), start.plusDays(days - 1L).toString())

    /** Adds [recipeId] at the end of [date]. Returns the entry id. */
    suspend fun add(date: LocalDate, recipeId: Long, now: Long = System.currentTimeMillis()): Long =
        dao.append(date.toString(), recipeId, now)

    suspend fun remove(entryId: Long) = dao.delete(entryId)

    /** Recipe ids planned on [date], in plan order (a recipe planned twice appears twice). */
    suspend fun recipeIdsOn(date: LocalDate): List<Long> = dao.recipeIdsOn(date.toString())
}
