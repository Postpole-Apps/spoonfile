package com.postpole.spoonfile.data

import com.postpole.spoonfile.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class RecipeRepository @Inject constructor(
    private val dao: RecipeDao,
    private val images: ImageStore,
    @IoDispatcher private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    /** [query] is matched literally: `%`, `_` and `\` are escaped before reaching the DAO's LIKE. */
    fun observeList(
        query: String = "",
        categoryId: Long? = null,
        favoritesOnly: Boolean = false,
        sort: RecipeSort = RecipeSort.RECENT,
    ): Flow<List<RecipeEntity>> = dao.observeList(escapeLike(query.trim()), categoryId, favoritesOnly, sort)

    fun observeDetails(id: Long): Flow<RecipeWithDetails?> = dao.observeDetails(id)

    fun count(): Flow<Int> = dao.count()

    suspend fun allDetails(): List<RecipeWithDetails> = dao.allDetails().map { it.ordered() }

    /** The stored photo file for [name], or null when it no longer exists. */
    fun photoFile(name: String): java.io.File? = images.resolve(name).takeIf { it.isFile }

    suspend fun getByUid(uid: String): RecipeEntity? = dao.getByUid(uid)

    suspend fun dedupeKeys(): List<DedupeKey> = dao.dedupeKeys()

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
        previous?.imageFile?.takeIf { it != recipe.imageFile }?.let { deleteImage(it) }
        return id
    }

    /** Records that Cook mode was finished for [id]. One timestamp (overwrites); does not touch `updatedAt`. */
    suspend fun markCooked(id: Long, nowMillis: Long = System.currentTimeMillis()) = dao.markCooked(id, nowMillis)

    /** Deletes the recipe (children cascade) and its photo file. */
    suspend fun delete(id: Long) {
        val image = dao.getById(id)?.imageFile
        dao.delete(id)
        image?.let { deleteImage(it) }
    }

    /** Decodes, downsamples and stores a photo off the main thread; returns the stored file name. */
    suspend fun savePhoto(bytes: ByteArray): String = withContext(io) { images.save(bytes) }

    /** Reads the stream (e.g. a Photo Picker uri) and stores it as a photo, all on the IO dispatcher. Null if unreadable. */
    suspend fun savePhotoFrom(open: () -> java.io.InputStream?): String? = withContext(io) {
        try {
            open()?.use { images.save(it.readBytes()) }
        } catch (_: java.io.IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** Removes a stored photo file, off the main thread. */
    suspend fun deleteImage(name: String) {
        withContext(io) { images.delete(name) }
    }

    /** Loads a stored photo for display, downsampled to about [maxEdge] px, off the main thread. */
    suspend fun loadPhoto(name: String, maxEdge: Int): android.graphics.Bitmap? =
        withContext(io) { images.decode(name, maxEdge) }

    suspend fun remainingSlots(isPro: Boolean): Int =
        if (isPro) Int.MAX_VALUE else max(0, Limits.FREE_RECIPE_LIMIT - dao.countNow())

    suspend fun canAdd(isPro: Boolean, n: Int = 1): Boolean = remainingSlots(isPro) >= n

    companion object {
        /** Escapes backslash, `%` and `_` so text matches literally under `LIKE ... ESCAPE '\'`. */
        fun escapeLike(query: String): String =
            query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    }
}
