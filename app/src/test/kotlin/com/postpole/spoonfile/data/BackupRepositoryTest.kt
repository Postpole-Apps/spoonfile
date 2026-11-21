package com.postpole.spoonfile.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class BackupRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var imagesDir: File
    private lateinit var images: ImageStore
    private lateinit var repo: BackupRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        imagesDir = File(tmp.root, "files/images").also { it.mkdirs() }
        images = ImageStore(imagesDir)
        repo = BackupRepository(db, images)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun addRecipe(uid: String, title: String, image: String? = null, categories: List<Long> = emptyList()) =
        db.recipeDao().upsertWithChildren(
            RecipeEntity(
                uid = uid, title = title, description = "d \"quoted\"\nnewline ₹ 日本語", servings = 4.0, servingsText = "4 people",
                prepMinutes = 5, notes = "n", sourceUrl = "https://example.com/$uid", imageFile = image, rating = 3,
                favorite = true, origin = RecipeOrigin.WEB, createdAt = 10, updatedAt = 20,
            ),
            listOf(IngredientEntity(position = 0, text = "For the sauce", isHeader = true), IngredientEntity(position = 1, text = "2 tomatoes")),
            listOf(StepEntity(position = 0, text = "Chop"), StepEntity(position = 1, text = "Cook")),
            categories,
        )

    private fun export(): ByteArray = ByteArrayOutputStream().also { runBlocking { repo.exportZip(it, now = 1) } }.toByteArray()

    private suspend fun import(bytes: ByteArray) = repo.importZip(ByteArrayInputStream(bytes))

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            entries.forEach { (name, bytes) ->
                z.putNextEntry(ZipEntry(name))
                z.write(bytes)
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun entry(zip: ByteArray, name: String): ByteArray? =
        java.util.zip.ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            generateSequence { z.nextEntry }.firstOrNull { it.name == name }?.let { z.readBytes() }
        }

    private data class Snapshot(
        val recipes: List<RecipeEntity>,
        val ingredients: List<IngredientEntity>,
        val steps: List<StepEntity>,
        val categories: List<CategoryEntity>,
        val links: List<RecipeCategoryCrossRef>,
        val shopping: List<ShoppingItemEntity>,
        val plan: List<MealPlanEntity>,
    )

    private suspend fun snapshot() = Snapshot(
        db.recipeDao().allRecipes(), db.recipeDao().allIngredients(), db.recipeDao().allSteps(),
        db.categoryDao().allCategories(), db.recipeDao().allCrossRefs(), db.shoppingDao().getAll(),
        db.mealPlanDao().getAll(),
    )

    @Test
    fun roundTrip_restoresAllTablesAndPhotos() = runBlocking {
        File(imagesDir, "a.jpg").writeBytes(byteArrayOf(1, 2, 3))
        File(imagesDir, "b.jpg").writeBytes(byteArrayOf(9, 8, 7, 6))
        val cat = db.categoryDao().getOrCreate("Dinner")
        val r1 = addRecipe("u1", "Soup", image = "a.jpg", categories = listOf(cat))
        val r2 = addRecipe("u2", "Salad", image = "b.jpg")
        db.shoppingDao().insertAll(listOf(ShoppingItemEntity(text = "milk", recipeId = r1, position = 1, createdAt = 5)))
        db.mealPlanDao().append("2026-11-21", r1, now = 7)
        db.mealPlanDao().append("2026-11-21", r2, now = 8)
        db.mealPlanDao().append("2026-11-22", r1, now = 9)
        val before = snapshot()
        assertEquals(3, before.plan.size)
        val zip = export()

        assertTrue(entry(zip, "backup.json") != null)
        assertArrayEquals(byteArrayOf(1, 2, 3), entry(zip, "images/a.jpg"))

        // Different data and photos on the device that must be replaced.
        db.recipeDao().deleteAllRecipes()
        db.categoryDao().deleteAllCategories()
        addRecipe("other", "Other", image = "c.jpg")
        imagesDir.deleteRecursively()
        imagesDir.mkdirs()
        File(imagesDir, "c.jpg").writeBytes(byteArrayOf(5))

        assertEquals(2, import(zip))

        assertEquals(before, snapshot())
        assertArrayEquals(byteArrayOf(1, 2, 3), File(imagesDir, "a.jpg").readBytes())
        assertArrayEquals(byteArrayOf(9, 8, 7, 6), File(imagesDir, "b.jpg").readBytes())
        assertFalse(File(imagesDir, "c.jpg").exists())
        assertEquals(listOf("images"), imagesDir.parentFile!!.list()!!.toList())
    }

    @Test
    fun roundTrip_emptyDatabase() = runBlocking {
        val zip = export()
        addRecipe("x", "x")
        assertEquals(0, import(zip))
        assertEquals(0, db.recipeDao().countNow())
    }

    @Test
    fun export_isVersionedAndNamed() {
        val json = JSONObject(String(entry(export(), "backup.json")!!))
        assertEquals("postpole-backup", json.getString("format"))
        assertEquals(2, json.getInt("version"))
        listOf("recipes", "ingredients", "steps", "categories", "recipeCategories", "shoppingItems", "mealPlanEntries").forEach {
            assertTrue(it, json.has(it))
        }
    }

    @Test
    fun export_writesMealPlanFields() = runBlocking {
        val r = addRecipe("u1", "Soup")
        db.mealPlanDao().append("2026-11-21", r, now = 7)
        val entries = JSONObject(String(entry(export(), "backup.json")!!)).getJSONArray("mealPlanEntries")
        val e = entries.getJSONObject(0)
        assertEquals(1, entries.length())
        assertEquals("2026-11-21", e.getString("date"))
        assertEquals(r, e.getLong("recipeId"))
        assertEquals(0, e.getInt("position"))
        assertEquals(7L, e.getLong("createdAt"))
        assertTrue(e.has("id"))
    }

    @Test
    fun v1Backup_withoutMealPlan_restoresWithAnEmptyPlan() = runBlocking {
        addRecipe("u1", "Soup")
        val v1 = JSONObject(String(entry(export(), "backup.json")!!)).apply {
            put("version", 1)
            remove("mealPlanEntries")
        }
        // The device has a plan that the v1 restore must replace (with nothing).
        val other = addRecipe("u2", "Other")
        db.mealPlanDao().append("2026-11-21", other, now = 1)

        assertEquals(1, import(zipOf("backup.json" to v1.toString().toByteArray())))

        assertEquals(listOf("Soup"), db.recipeDao().allRecipes().map { it.title })
        assertEquals(0, db.mealPlanDao().countNow())
    }

    @Test
    fun malformedImport_leavesDataAndPhotosUntouched() = runBlocking {
        File(imagesDir, "keep.jpg").writeBytes(byteArrayOf(4, 2))
        val keep = addRecipe("keep", "Keep", image = "keep.jpg")
        db.mealPlanDao().append("2026-11-21", keep, now = 3)
        val before = snapshot()
        assertEquals(1, before.plan.size)
        val good = JSONObject(String(entry(export(), "backup.json")!!))

        fun withJson(edit: (JSONObject) -> Unit): ByteArray {
            val copy = JSONObject(good.toString()).also(edit)
            return zipOf("backup.json" to copy.toString().toByteArray(), "images/zzz.jpg" to byteArrayOf(1))
        }
        val badRecipe = JSONObject(good.getJSONArray("recipes").getJSONObject(0).toString())
        val bad = listOf(
            "not a zip".toByteArray(),
            zipOf("other.txt" to byteArrayOf(1)),
            zipOf("backup.json" to "not json".toByteArray()),
            withJson { it.put("format", "other") },
            withJson { it.put("version", 999) },
            withJson { it.remove("steps") },
            withJson { it.put("recipes", org.json.JSONArray().put(badRecipe).put(badRecipe)) },
            withJson { it.getJSONArray("ingredients").getJSONObject(0).put("recipeId", 12345) },
            withJson { it.getJSONArray("recipeCategories").put(JSONObject().put("recipeId", 1).put("categoryId", 77)) },
            withJson { it.getJSONArray("recipes").getJSONObject(0).put("imageFile", "../escape.jpg") },
            withJson { it.getJSONArray("recipes").getJSONObject(0).remove("title") },
            withJson { it.getJSONArray("mealPlanEntries").getJSONObject(0).put("recipeId", 12345) },
            withJson { it.getJSONArray("mealPlanEntries").getJSONObject(0).put("date", "not-a-date") },
            withJson { it.getJSONArray("mealPlanEntries").getJSONObject(0).remove("position") },
            withJson { it.put("mealPlanEntries", org.json.JSONArray().put(it.getJSONArray("mealPlanEntries").getJSONObject(0)).put(it.getJSONArray("mealPlanEntries").getJSONObject(0))) },
        )
        bad.forEach { bytes ->
            assertThrows(InvalidBackupException::class.java) { runBlocking { import(bytes) } }
        }

        assertEquals(before, snapshot())
        assertArrayEquals(byteArrayOf(4, 2), File(imagesDir, "keep.jpg").readBytes())
        assertEquals(listOf("images"), imagesDir.parentFile!!.list()!!.toList())
    }

    @Test
    fun restore_isNotCappedByTheFreeLimit() = runBlocking {
        repeat(Limits.FREE_RECIPE_LIMIT + 5) { addRecipe("u$it", "r$it") }
        val zip = export()
        db.recipeDao().deleteAllRecipes()

        assertEquals(Limits.FREE_RECIPE_LIMIT + 5, import(zip))

        assertEquals(Limits.FREE_RECIPE_LIMIT + 5, db.recipeDao().countNow())
        // Over the cap: new recipes stay blocked for free users until Pro.
        val recipes = RecipeRepository(db.recipeDao(), images, Dispatchers.Unconfined)
        assertFalse(recipes.canAdd(isPro = false))
        assertTrue(recipes.canAdd(isPro = true))
    }

    @Test
    fun missingPhotoInArchive_isDroppedNotFatal() = runBlocking {
        File(imagesDir, "a.jpg").writeBytes(byteArrayOf(1))
        addRecipe("u1", "Soup", image = "a.jpg")
        val json = entry(export(), "backup.json")!!
        assertEquals(1, import(zipOf("backup.json" to json)))
        assertEquals(null, db.recipeDao().allRecipes().single().imageFile)
    }
}
