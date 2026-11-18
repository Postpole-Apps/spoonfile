package com.postpole.spoonfile.data

import androidx.room.withTransaction
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** The backup file is not valid, or was written by a newer app version. */
class InvalidBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Backup and restore of all recipe data as a `.zip` (JSON + photos); see [RecipeBackupZip] for the layout.
 * Works on plain streams, which the UI gets from the Storage Access Framework, so no storage permission is needed.
 *
 * Restore is never capped: a user can always bring back their own data. If it lands over the free limit, adding
 * new recipes stays blocked until Pro (the cap is checked when adding, not here).
 */
@Singleton
class BackupRepository @Inject constructor(
    private val db: AppDatabase,
    private val images: ImageStore,
) {
    /** Writes the backup and returns the number of recipes exported. */
    suspend fun exportZip(out: OutputStream, now: Long = System.currentTimeMillis()): Int {
        val dao = db.backupDao()
        val data = db.withTransaction {
            BackupData(
                recipes = dao.recipes(),
                ingredients = dao.ingredients(),
                steps = dao.steps(),
                categories = dao.categories(),
                recipeCategories = dao.recipeCategories(),
                shoppingItems = dao.shoppingItems(),
                exportedAt = now,
            )
        }
        RecipeBackupZip.write(out, data, images.dir)
        return data.recipes.size
    }

    /**
     * Replaces all recipe data and photos with the backup's contents. The file is fully read and validated first,
     * with photos going to a temporary folder; only then are the tables replaced and the photos folder swapped, in
     * one transaction. Any failure leaves existing data and photos untouched. Returns the recipe count.
     */
    suspend fun importZip(input: InputStream): Int {
        val live = images.dir
        val tmp = File(live.parentFile, "${live.name}.restore")
        val old = File(live.parentFile, "${live.name}.old")
        tmp.deleteRecursively()
        old.deleteRecursively()
        try {
            val data = RecipeBackupZip.read(input, tmp)
            val dao = db.backupDao()
            db.withTransaction {
                // Recipes cascade to ingredients, steps and category links; shopping items only null their link.
                dao.deleteShoppingItems()
                dao.deleteRecipes()
                dao.deleteCategories()
                dao.insertCategories(data.categories)
                dao.insertRecipes(data.recipes)
                dao.insertIngredients(data.ingredients)
                dao.insertSteps(data.steps)
                dao.insertRecipeCategories(data.recipeCategories)
                dao.insertShoppingItems(data.shoppingItems)
                swapImages(live, tmp, old)
            }
            old.deleteRecursively()
            return data.recipes.size
        } finally {
            tmp.deleteRecursively()
        }
    }

    /** Moves [tmp] into place as [live]; puts the old folder back and throws if that can't be done. */
    private fun swapImages(live: File, tmp: File, old: File) {
        val hadLive = live.exists()
        if (hadLive && !live.renameTo(old)) throw IOException("Could not set aside the photos folder")
        if (!tmp.renameTo(live)) {
            if (hadLive) old.renameTo(live)
            throw IOException("Could not move the restored photos into place")
        }
    }

    companion object {
        const val FORMAT_NAME = "postpole-backup"
        const val FORMAT_VERSION = 1
    }
}
