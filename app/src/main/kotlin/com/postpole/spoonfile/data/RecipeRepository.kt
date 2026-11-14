package com.postpole.spoonfile.data

import com.postpole.spoonfile.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class RecipeRepository @Inject constructor(
    private val dao: RecipeDao,
    private val images: ImageStore,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /** Decodes and stores a picked photo off the main thread; returns the stored file name. */
    suspend fun savePhoto(bytes: ByteArray): String = withContext(io) { images.save(bytes) }

    suspend fun deletePhoto(name: String) {
        withContext(io) { images.delete(name) }
    }

    /** Photo file for [name], for loading in the UI. */
    fun photoFile(name: String) = images.resolve(name)

    fun observeList(
        query: String = "",
        categoryId: Long? = null,
        favoritesOnly: Boolean = false,
        sort: RecipeSort = RecipeSort.RECENT,
    ): Flow<List<RecipeEntity>> = dao.observeList(escapeLike(query.trim()), categoryId, favoritesOnly, sort)

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
        val id = dao.upsertWithChildren(recipe.copy(rating = recipe.rating.coerceIn(0, 5)), ingredients, steps, categoryIds)
        previous?.imageFile?.takeIf { it != recipe.imageFile }?.let { withContext(io) { images.delete(it) } }
        return id
    }

    /** Deletes the recipe (children cascade) and its photo file. */
    suspend fun delete(id: Long) {
        val image = dao.getById(id)?.imageFile
        dao.delete(id)
        image?.let { withContext(io) { images.delete(it) } }
    }

    suspend fun setFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite)

    /** Rating is always kept within 0..5. */
    suspend fun setRating(id: Long, rating: Int) = dao.setRating(id, rating.coerceIn(0, 5))

    suspend fun getDetails(id: Long): RecipeWithDetails? = dao.observeDetails(id).first()

    suspend fun remainingSlots(isPro: Boolean): Int =
        if (isPro) Int.MAX_VALUE else max(0, Limits.FREE_RECIPE_LIMIT - dao.countNow())

    suspend fun canAdd(isPro: Boolean, n: Int = 1): Boolean = remainingSlots(isPro) >= n

    companion object {
        /** Escapes LIKE wildcards so user text matches literally (the DAO query uses ESCAPE backslash). */
        fun escapeLike(query: String): String =
            query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    }
}
