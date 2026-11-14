package com.postpole.spoonfile.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class RecipeRepository @Inject constructor(
    private val recipeDao: RecipeDao,
    private val categoryDao: CategoryDao,
    private val imageStore: ImageStore,
) {
    fun observeList(query: String, categoryId: Long?, favoritesOnly: Boolean, sort: RecipeSort) =
        recipeDao.observeList(query.trim(), categoryId, favoritesOnly, sort)

    fun count() = recipeDao.count()

    fun observeDetails(id: Long) = recipeDao.observeDetails(id)

    suspend fun getByUid(uid: String) = recipeDao.getByUid(uid)

    suspend fun findBySourceUrl(url: String) = recipeDao.findBySourceUrl(url)

    suspend fun getOrCreateCategory(name: String): Long = categoryDao.getOrCreate(name)

    /** Saves recipe and children in one transaction; removes a replaced image file. Returns the recipe id. */
    suspend fun upsertWithChildren(
        recipe: RecipeEntity,
        ingredients: List<IngredientEntity>,
        steps: List<StepEntity>,
        categoryIds: List<Long>,
    ): Long {
        val previousImage = if (recipe.id != 0L) recipeDao.getById(recipe.id)?.imageFile else null
        val id = recipeDao.upsertWithChildren(recipe.copy(title = recipe.title.trim()), ingredients, steps, categoryIds)
        if (previousImage != null && previousImage != recipe.imageFile) imageStore.delete(previousImage)
        return id
    }

    /** Deletes the recipe (children cascade) and its image file. */
    suspend fun delete(id: Long) {
        val image = recipeDao.getById(id)?.imageFile
        recipeDao.delete(id)
        imageStore.delete(image)
    }

    suspend fun remainingSlots(isPro: Boolean): Int =
        if (isPro) Int.MAX_VALUE else max(0, Limits.FREE_RECIPE_LIMIT - recipeDao.countNow())

    suspend fun canAdd(isPro: Boolean, n: Int = 1): Boolean = remainingSlots(isPro) >= n
}
