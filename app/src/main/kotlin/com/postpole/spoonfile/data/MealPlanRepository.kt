package com.postpole.spoonfile.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MealPlanRepository @Inject constructor(
    private val dao: MealPlanDao,
    private val recipes: RecipeDao,
    private val shopping: ShoppingRepository,
) {
    /** Entries for [from]..[to] (inclusive), ordered by day then position. */
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<MealPlanRow>> =
        dao.observeRange(from.toString(), to.toString())

    /** Appends [recipeId] to the end of [date]. Returns the new entry's id. */
    suspend fun add(date: LocalDate, recipeId: Long, now: Long = System.currentTimeMillis()): Long {
        val key = date.toString()
        return dao.insert(
            MealPlanEntryEntity(date = key, recipeId = recipeId, position = dao.maxPosition(key) + 1, createdAt = now),
        )
    }

    suspend fun remove(entryId: Long) = dao.delete(entryId)

    /**
     * Adds the ingredients (default servings, headers skipped) of each of [recipeIds], in order, to the
     * shopping list, grouped by recipe through [ShoppingRepository.addRecipeLines]. Returns how many lines
     * were added.
     */
    suspend fun addToShopping(recipeIds: List<Long>, now: Long = System.currentTimeMillis()): Int {
        val ingredients = recipes.allIngredients().filter { !it.isHeader }.groupBy { it.recipeId }
        var added = 0
        for (id in recipeIds) {
            added += shopping.addRecipeLines(id, ingredients[id].orEmpty().map { it.text }, now)
        }
        return added
    }
}
