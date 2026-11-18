package com.postpole.spoonfile.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** The backup file is not valid, or was written by a newer app version. */
class InvalidBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Everything a backup holds, already validated. */
internal class BackupData(
    val recipes: List<RecipeEntity>,
    val ingredients: List<IngredientEntity>,
    val steps: List<StepEntity>,
    val categories: List<CategoryEntity>,
    val recipeCategories: List<RecipeCategoryCrossRef>,
    val shoppingItems: List<ShoppingItemEntity>,
)

/**
 * Backup of all Room data plus recipe photos as one `.zip` (`backup.json` + `images/`). Works on plain
 * streams; the UI supplies them from the Storage Access Framework, so no storage permission is needed.
 *
 * Format v1: `{"format":"postpole-backup","version":1,"exportedAt":<ms>,` then the arrays `recipes`,
 * `ingredients`, `steps`, `categories`, `recipeCategories`, `shoppingItems`. Add new tables as new
 * top-level arrays and bump [FORMAT_VERSION]; keep reading older versions.
 *
 * Restore is never capped by the free recipe limit: it brings back the user's own data. Adding new
 * recipes stays blocked afterwards until Pro, through the normal add checks.
 */
@Singleton
class BackupRepository @Inject constructor(
    private val db: AppDatabase,
    private val images: ImageStore,
) {
    /** Writes the backup zip and returns the number of recipes exported. */
    suspend fun exportZip(out: OutputStream, now: Long = System.currentTimeMillis()): Int {
        val recipeDao = db.recipeDao()
        val recipes = recipeDao.allRecipes()
        val json = JSONObject()
            .put("format", FORMAT_NAME)
            .put("version", FORMAT_VERSION)
            .put("exportedAt", now)
            .put("recipes", array(recipes, ::recipeJson))
            .put("ingredients", array(recipeDao.allIngredients()) { lineJson(it.id, it.recipeId, it.position, it.text, it.isHeader) })
            .put("steps", array(recipeDao.allSteps()) { lineJson(it.id, it.recipeId, it.position, it.text, it.isHeader) })
            .put(
                "categories",
                array(db.categoryDao().allCategories()) {
                    JSONObject().put("id", it.id).put("name", it.name).put("sortOrder", it.sortOrder)
                },
            )
            .put(
                "recipeCategories",
                array(recipeDao.allCrossRefs()) { JSONObject().put("recipeId", it.recipeId).put("categoryId", it.categoryId) },
            )
            .put(
                "shoppingItems",
                array(db.shoppingDao().getAll()) {
                    JSONObject()
                        .put("id", it.id)
                        .put("text", it.text)
                        .put("checked", it.checked)
                        .put("recipeId", it.recipeId ?: JSONObject.NULL)
                        .put("position", it.position)
                        .put("createdAt", it.createdAt)
                },
            )
        val files = recipes.mapNotNull { r -> r.imageFile?.let { images.resolve(it) }?.takeIf { it.isFile } }.distinct()
        RecipeBackupZip.write(out, json.toString(2), files)
        return recipes.size
    }

    /**
     * Replaces all data and the photos with the backup's contents. The archive is fully read and
     * validated first (photos go to a temp directory), so an invalid file leaves existing data and
     * photos untouched. Tables are replaced in one transaction and the photo directory is swapped inside
     * it; if anything fails the old photos are put back. Returns the number of recipes restored.
     */
    suspend fun importZip(input: InputStream): Int {
        val imagesDir = images.dir
        val parent = imagesDir.absoluteFile.parentFile ?: throw IllegalStateException("No parent for images dir")
        parent.mkdirs()
        val token = UUID.randomUUID().toString().take(8)
        val temp = File(parent, "${imagesDir.name}.restore-$token")
        val old = File(parent, "${imagesDir.name}.old-$token")
        var swapped = false
        try {
            val contents = RecipeBackupZip.read(input, temp)
            val data = parse(contents.json, contents.imageNames)

            db.withTransaction {
                val recipeDao = db.recipeDao()
                recipeDao.deleteAllRecipes()
                db.categoryDao().deleteAllCategories()
                db.shoppingDao().deleteAll()
                db.categoryDao().restoreCategories(data.categories)
                recipeDao.restoreRecipes(data.recipes)
                recipeDao.restoreIngredients(data.ingredients)
                recipeDao.restoreSteps(data.steps)
                recipeDao.restoreCrossRefs(data.recipeCategories)
                db.shoppingDao().insertAll(data.shoppingItems)

                if (imagesDir.exists() && !imagesDir.renameTo(old)) error("Could not move the photo folder aside")
                swapped = true
                if (!temp.renameTo(imagesDir)) error("Could not put the restored photos in place")
            }
        } catch (e: Throwable) {
            if (swapped) {
                // The database rolled back; put the previous photos back too.
                imagesDir.deleteRecursively()
                if (old.exists()) old.renameTo(imagesDir)
            }
            temp.deleteRecursively()
            throw e
        }
        old.deleteRecursively()
        return db.recipeDao().countNow()
    }

    internal fun parse(text: String, availableImages: Set<String>): BackupData {
        try {
            val root = JSONObject(text)
            if (root.optString("format") != FORMAT_NAME) throw InvalidBackupException("Not a backup file")
            val version = root.getInt("version")
            if (version < 1 || version > FORMAT_VERSION) {
                throw InvalidBackupException("Unsupported backup version $version")
            }

            val recipes = root.getJSONArray("recipes").objects().map { o ->
                val image = o.optNullableString("imageFile")
                if (image != null && !RecipeBackupZip.isPlainFileName(image)) throw InvalidBackupException("Bad photo name")
                RecipeEntity(
                    id = o.getLong("id"),
                    uid = o.getString("uid").also { if (it.isBlank()) throw InvalidBackupException("Blank recipe uid") },
                    title = o.getString("title"),
                    description = o.optNullableString("description"),
                    servingsText = o.optNullableString("servingsText"),
                    servings = if (o.isNull("servings")) null else o.getDouble("servings"),
                    prepMinutes = o.optNullableInt("prepMinutes"),
                    cookMinutes = o.optNullableInt("cookMinutes"),
                    totalMinutes = o.optNullableInt("totalMinutes"),
                    notes = o.optNullableString("notes"),
                    nutritionText = o.optNullableString("nutritionText"),
                    sourceName = o.optNullableString("sourceName"),
                    sourceUrl = o.optNullableString("sourceUrl"),
                    // A photo the archive doesn't carry is dropped rather than failing the whole restore.
                    imageFile = image?.takeIf { it in availableImages },
                    rating = o.getInt("rating"),
                    favorite = o.getBoolean("favorite"),
                    origin = RecipeOrigin.entries.firstOrNull { it.name == o.getString("origin") } ?: RecipeOrigin.MANUAL,
                    createdAt = o.getLong("createdAt"),
                    updatedAt = o.getLong("updatedAt"),
                )
            }
            requireUnique(recipes.map { it.id }, "recipe id")
            requireUnique(recipes.map { it.uid }, "recipe uid")
            val recipeIds = recipes.mapTo(HashSet()) { it.id }

            val ingredients = root.getJSONArray("ingredients").objects().map { o ->
                IngredientEntity(o.getLong("id"), o.getLong("recipeId"), o.getInt("position"), o.getString("text"), o.getBoolean("isHeader"))
            }
            val steps = root.getJSONArray("steps").objects().map { o ->
                StepEntity(o.getLong("id"), o.getLong("recipeId"), o.getInt("position"), o.getString("text"), o.getBoolean("isHeader"))
            }
            requireUnique(ingredients.map { it.id }, "ingredient id")
            requireUnique(steps.map { it.id }, "step id")
            if (ingredients.any { it.recipeId !in recipeIds }) throw InvalidBackupException("Ingredient for an unknown recipe")
            if (steps.any { it.recipeId !in recipeIds }) throw InvalidBackupException("Step for an unknown recipe")

            val categories = root.getJSONArray("categories").objects().map { o ->
                CategoryEntity(o.getLong("id"), o.getString("name"), o.getInt("sortOrder"))
            }
            requireUnique(categories.map { it.id }, "category id")
            requireUnique(categories.map { it.name.lowercase() }, "category name")
            val categoryIds = categories.mapTo(HashSet()) { it.id }

            val links = root.getJSONArray("recipeCategories").objects().map { o ->
                RecipeCategoryCrossRef(o.getLong("recipeId"), o.getLong("categoryId"))
            }
            requireUnique(links.map { it.recipeId to it.categoryId }, "category link")
            if (links.any { it.recipeId !in recipeIds || it.categoryId !in categoryIds }) {
                throw InvalidBackupException("Category link to an unknown recipe or category")
            }

            val shopping = root.getJSONArray("shoppingItems").objects().map { o ->
                ShoppingItemEntity(
                    id = o.getLong("id"),
                    text = o.getString("text"),
                    checked = o.getBoolean("checked"),
                    recipeId = if (o.isNull("recipeId")) null else o.getLong("recipeId"),
                    position = o.getInt("position"),
                    createdAt = o.getLong("createdAt"),
                )
            }
            requireUnique(shopping.map { it.id }, "shopping item id")
            if (shopping.any { it.recipeId != null && it.recipeId !in recipeIds }) {
                throw InvalidBackupException("Shopping item for an unknown recipe")
            }
            return BackupData(recipes, ingredients, steps, categories, links, shopping)
        } catch (e: JSONException) {
            throw InvalidBackupException("Malformed backup file", e)
        }
    }

    private fun <T> requireUnique(values: List<T>, what: String) {
        if (values.toHashSet().size != values.size) throw InvalidBackupException("Duplicate $what")
    }

    private fun <T> array(items: List<T>, map: (T) -> JSONObject) = JSONArray().apply { items.forEach { put(map(it)) } }

    private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }

    private fun JSONObject.optNullableString(key: String): String? = if (isNull(key)) null else getString(key)

    private fun JSONObject.optNullableInt(key: String): Int? = if (isNull(key)) null else getInt(key)

    private fun lineJson(id: Long, recipeId: Long, position: Int, text: String, isHeader: Boolean) =
        JSONObject().put("id", id).put("recipeId", recipeId).put("position", position).put("text", text).put("isHeader", isHeader)

    private fun recipeJson(r: RecipeEntity) = JSONObject()
        .put("id", r.id)
        .put("uid", r.uid)
        .put("title", r.title)
        .put("description", r.description ?: JSONObject.NULL)
        .put("servingsText", r.servingsText ?: JSONObject.NULL)
        .put("servings", r.servings ?: JSONObject.NULL)
        .put("prepMinutes", r.prepMinutes ?: JSONObject.NULL)
        .put("cookMinutes", r.cookMinutes ?: JSONObject.NULL)
        .put("totalMinutes", r.totalMinutes ?: JSONObject.NULL)
        .put("notes", r.notes ?: JSONObject.NULL)
        .put("nutritionText", r.nutritionText ?: JSONObject.NULL)
        .put("sourceName", r.sourceName ?: JSONObject.NULL)
        .put("sourceUrl", r.sourceUrl ?: JSONObject.NULL)
        .put("imageFile", r.imageFile ?: JSONObject.NULL)
        .put("rating", r.rating)
        .put("favorite", r.favorite)
        .put("origin", r.origin.name)
        .put("createdAt", r.createdAt)
        .put("updatedAt", r.updatedAt)

    companion object {
        const val FORMAT_NAME = "postpole-backup"
        const val FORMAT_VERSION = 1
    }
}
