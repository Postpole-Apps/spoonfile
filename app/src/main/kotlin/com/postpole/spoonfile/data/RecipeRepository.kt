package com.postpole.spoonfile.data

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class RecipeRepository @Inject constructor(
    private val dao: RecipeDao,
    private val images: ImageStore,
) {
    fun observeList(
        query: String = "",
        categoryId: Long? = null,
        favoritesOnly: Boolean = false,
        sort: RecipeSort = RecipeSort.RECENT,
    ): Flow<List<RecipeEntity>> = dao.observeList(query.trim(), categoryId, favoritesOnly, sort)

    fun observeDetails(id: Long): Flow<RecipeWithDetails?> = dao.observeDetails(id)

    fun count(): Flow<Int> = dao.count()

    suspend fun getByUid(uid: String): RecipeEntity? = dao.getByUid(uid)

    suspend fun findBySourceUrl(url: String): RecipeEntity? = dao.findBySourceUrl(url)

    /** One transaction; removes the previous photo file if the recipe's photo was replaced or cleared. */
    suspend fun upsertWithChildren(
        recipe: RecipeEntity,
        ingredients: List<IngredientEntity>,
        steps: List<StepEntity>,
        categoryIds: List<Long>,
    ): Long {
        val previous = if (recipe.id != 0L) dao.getById(recipe.id) else dao.getByUid(recipe.uid)
        val id = dao.upsertWithChildren(recipe, ingredients, steps, categoryIds)
        previous?.imageFile?.takeIf { it != recipe.imageFile }?.let { images.delete(it) }
        return id
    }

    /** Deletes the recipe (children cascade) and its photo file. */
    suspend fun delete(id: Long) {
        val image = dao.getById(id)?.imageFile
        dao.delete(id)
        image?.let { images.delete(it) }
    }

    suspend fun remainingSlots(isPro: Boolean): Int =
        if (isPro) Int.MAX_VALUE else max(0, Limits.FREE_RECIPE_LIMIT - dao.countNow())

    suspend fun canAdd(isPro: Boolean, n: Int = 1): Boolean = remainingSlots(isPro) >= n
}
