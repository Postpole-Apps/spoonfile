package com.postpole.spoonfile.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Every table a backup carries, in memory. Ids are kept as written so the links between tables survive. */
class BackupData(
    val recipes: List<RecipeEntity>,
    val ingredients: List<IngredientEntity>,
    val steps: List<StepEntity>,
    val categories: List<CategoryEntity>,
    val recipeCategories: List<RecipeCategoryCrossRef>,
    val shoppingItems: List<ShoppingItemEntity>,
    val exportedAt: Long,
)

/**
 * The backup file: a ZIP holding `backup.json` and an `images/` folder of recipe photos.
 *
 * `backup.json` is `{"format":"postpole-backup","version":1,"exportedAt":<ms>, "recipes":[…], "ingredients":[…],
 * "steps":[…], "categories":[…], "recipeCategories":[…], "shoppingItems":[…]}`. New tables become new top-level
 * arrays; bump [BackupRepository.FORMAT_VERSION] and keep reading older versions.
 */
object RecipeBackupZip {
    const val JSON_ENTRY = "backup.json"
    const val IMAGES_PREFIX = "images/"

    private const val MAX_JSON_BYTES = 64L * 1024 * 1024
    private const val MAX_IMAGE_BYTES = 32L * 1024 * 1024
    private const val MAX_IMAGES = 100_000
    private val SAFE_NAME = Regex("""[A-Za-z0-9][A-Za-z0-9._-]{0,127}""")

    /** Writes [data] and every photo it references that exists in [imageDir]. */
    fun write(out: OutputStream, data: BackupData, imageDir: File) {
        val zip = ZipOutputStream(out)
        zip.putNextEntry(ZipEntry(JSON_ENTRY))
        zip.write(toJson(data).toString(2).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        data.recipes.mapNotNull { it.imageFile }.distinct().forEach { name ->
            val file = File(imageDir, name)
            if (SAFE_NAME.matches(name) && file.isFile) {
                zip.putNextEntry(ZipEntry(IMAGES_PREFIX + name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        zip.finish()
        out.flush()
    }

    /**
     * Reads and fully validates a backup. Photos are written into [tempImageDir] (created here, left for the
     * caller to swap in or delete). Throws [InvalidBackupException] for anything that isn't a valid backup.
     */
    fun read(input: InputStream, tempImageDir: File): BackupData {
        var json: String? = null
        val images = HashSet<String>()
        try {
            tempImageDir.mkdirs()
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    when {
                        entry.name == JSON_ENTRY -> {
                            if (json != null) throw InvalidBackupException("Duplicate $JSON_ENTRY")
                            json = readLimited(zip, MAX_JSON_BYTES).toString(Charsets.UTF_8)
                        }
                        entry.name.startsWith(IMAGES_PREFIX) -> {
                            // The name is only ever a flat file name inside our own temp folder (no zip-slip).
                            val name = entry.name.removePrefix(IMAGES_PREFIX)
                            if (!SAFE_NAME.matches(name)) throw InvalidBackupException("Bad image name")
                            if (images.size >= MAX_IMAGES) throw InvalidBackupException("Too many images")
                            if (!images.add(name)) throw InvalidBackupException("Duplicate image $name")
                            File(tempImageDir, name).outputStream().use { out ->
                                var left = MAX_IMAGE_BYTES
                                val buf = ByteArray(8192)
                                while (true) {
                                    val n = zip.read(buf)
                                    if (n < 0) break
                                    left -= n
                                    if (left < 0) throw InvalidBackupException("Image too large")
                                    out.write(buf, 0, n)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: InvalidBackupException) {
            throw e
        } catch (e: IOException) {
            // Not a ZIP, or damaged.
            throw InvalidBackupException("Not a readable backup file", e)
        } catch (e: IllegalArgumentException) {
            throw InvalidBackupException("Not a readable backup file", e)
        }
        val text = json ?: throw InvalidBackupException("Missing $JSON_ENTRY")
        return parse(text, images)
    }

    private fun readLimited(zip: ZipInputStream, limit: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = zip.read(buf)
            if (n < 0) break
            if (out.size() + n > limit) throw InvalidBackupException("Backup data too large")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    internal fun toJson(d: BackupData): JSONObject = JSONObject()
        .put("format", BackupRepository.FORMAT_NAME)
        .put("version", BackupRepository.FORMAT_VERSION)
        .put("exportedAt", d.exportedAt)
        .put("recipes", array(d.recipes) { r ->
            JSONObject().put("id", r.id).put("uid", r.uid).put("title", r.title)
                .putN("description", r.description).putN("servingsText", r.servingsText)
                .putN("servings", r.servings).putN("prepMinutes", r.prepMinutes)
                .putN("cookMinutes", r.cookMinutes).putN("totalMinutes", r.totalMinutes)
                .putN("notes", r.notes).putN("nutritionText", r.nutritionText)
                .putN("sourceName", r.sourceName).putN("sourceUrl", r.sourceUrl)
                .putN("imageFile", r.imageFile).put("rating", r.rating).put("favorite", r.favorite)
                .put("origin", r.origin.name).put("createdAt", r.createdAt).put("updatedAt", r.updatedAt)
        })
        .put("ingredients", array(d.ingredients) { i ->
            JSONObject().put("id", i.id).put("recipeId", i.recipeId).put("position", i.position)
                .put("text", i.text).put("isHeader", i.isHeader)
        })
        .put("steps", array(d.steps) { s ->
            JSONObject().put("id", s.id).put("recipeId", s.recipeId).put("position", s.position)
                .put("text", s.text).put("isHeader", s.isHeader)
        })
        .put("categories", array(d.categories) { c ->
            JSONObject().put("id", c.id).put("name", c.name).put("sortOrder", c.sortOrder)
        })
        .put("recipeCategories", array(d.recipeCategories) { c ->
            JSONObject().put("recipeId", c.recipeId).put("categoryId", c.categoryId)
        })
        .put("shoppingItems", array(d.shoppingItems) { s ->
            JSONObject().put("id", s.id).put("text", s.text).put("checked", s.checked)
                .putN("recipeId", s.recipeId).put("position", s.position).put("createdAt", s.createdAt)
        })

    /** Parses and cross-checks [text]; [images] are the photo names present in the archive. */
    internal fun parse(text: String, images: Set<String>): BackupData {
        try {
            val root = JSONObject(text)
            if (root.optString("format") != BackupRepository.FORMAT_NAME) throw InvalidBackupException("Not a backup file")
            val version = root.getInt("version")
            if (version < 1 || version > BackupRepository.FORMAT_VERSION) {
                throw InvalidBackupException("Unsupported backup version $version")
            }
            val recipes = objects(root, "recipes").map { o ->
                val image = o.optStr("imageFile")
                RecipeEntity(
                    id = o.getLong("id"),
                    uid = o.getString("uid"),
                    title = o.getString("title"),
                    description = o.optStr("description"),
                    servingsText = o.optStr("servingsText"),
                    servings = if (o.isNull("servings")) null else o.getDouble("servings"),
                    prepMinutes = o.optInteger("prepMinutes"),
                    cookMinutes = o.optInteger("cookMinutes"),
                    totalMinutes = o.optInteger("totalMinutes"),
                    notes = o.optStr("notes"),
                    nutritionText = o.optStr("nutritionText"),
                    sourceName = o.optStr("sourceName"),
                    sourceUrl = o.optStr("sourceUrl"),
                    // A photo that didn't make it into the file just leaves the recipe without one.
                    imageFile = image?.takeIf { it in images },
                    rating = o.getInt("rating").coerceIn(0, 5),
                    favorite = o.getBoolean("favorite"),
                    origin = runCatching { RecipeOrigin.valueOf(o.getString("origin")) }.getOrDefault(RecipeOrigin.MANUAL),
                    createdAt = o.getLong("createdAt"),
                    updatedAt = o.getLong("updatedAt"),
                )
            }
            val categories = objects(root, "categories").map {
                CategoryEntity(id = it.getLong("id"), name = it.getString("name"), sortOrder = it.getInt("sortOrder"))
            }
            val recipeIds = unique(recipes.map { it.id }, "recipe id")
            unique(recipes.map { it.uid }, "recipe uid")
            val categoryIds = unique(categories.map { it.id }, "category id")
            unique(categories.map { it.name.lowercase() }, "category name")

            val ingredients = objects(root, "ingredients").map {
                IngredientEntity(
                    id = it.getLong("id"), recipeId = it.getLong("recipeId"), position = it.getInt("position"),
                    text = it.getString("text"), isHeader = it.getBoolean("isHeader"),
                )
            }
            val steps = objects(root, "steps").map {
                StepEntity(
                    id = it.getLong("id"), recipeId = it.getLong("recipeId"), position = it.getInt("position"),
                    text = it.getString("text"), isHeader = it.getBoolean("isHeader"),
                )
            }
            unique(ingredients.map { it.id }, "ingredient id")
            unique(steps.map { it.id }, "step id")
            if (ingredients.any { it.recipeId !in recipeIds } || steps.any { it.recipeId !in recipeIds }) {
                throw InvalidBackupException("Ingredient or step points at a missing recipe")
            }
            val crossRefs = objects(root, "recipeCategories").map {
                RecipeCategoryCrossRef(it.getLong("recipeId"), it.getLong("categoryId"))
            }
            unique(crossRefs.map { it.recipeId to it.categoryId }, "recipe category link")
            if (crossRefs.any { it.recipeId !in recipeIds || it.categoryId !in categoryIds }) {
                throw InvalidBackupException("Category link points at something missing")
            }
            val shopping = objects(root, "shoppingItems").map {
                ShoppingItemEntity(
                    id = it.getLong("id"), text = it.getString("text"), checked = it.getBoolean("checked"),
                    recipeId = if (it.isNull("recipeId")) null else it.getLong("recipeId"),
                    position = it.getInt("position"), createdAt = it.getLong("createdAt"),
                )
            }
            unique(shopping.map { it.id }, "shopping item id")
            if (shopping.any { it.recipeId != null && it.recipeId !in recipeIds }) {
                throw InvalidBackupException("Shopping item points at a missing recipe")
            }
            return BackupData(recipes, ingredients, steps, categories, crossRefs, shopping, root.optLong("exportedAt", 0L))
        } catch (e: JSONException) {
            throw InvalidBackupException("Malformed backup file", e)
        }
    }

    private fun <T> unique(values: List<T>, what: String): Set<T> {
        val set = HashSet<T>()
        values.forEach { if (!set.add(it)) throw InvalidBackupException("Duplicate $what") }
        return set
    }

    private fun objects(root: JSONObject, key: String): List<JSONObject> {
        val array = root.getJSONArray(key)
        return List(array.length()) { array.getJSONObject(it) }
    }

    private fun <T> array(items: List<T>, map: (T) -> JSONObject) = JSONArray().apply { items.forEach { put(map(it)) } }

    private fun JSONObject.putN(key: String, value: Any?): JSONObject = put(key, value ?: JSONObject.NULL)

    private fun JSONObject.optStr(key: String): String? = if (isNull(key)) null else getString(key)

    private fun JSONObject.optInteger(key: String): Int? = if (isNull(key)) null else getInt(key)
}
