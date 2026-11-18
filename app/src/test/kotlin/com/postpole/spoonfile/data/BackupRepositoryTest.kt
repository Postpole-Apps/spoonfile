package com.postpole.spoonfile.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class BackupRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var imageDir: File
    private lateinit var repo: BackupRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        imageDir = File(tmp.root, "images").also { it.mkdirs() }
        repo = BackupRepository(db, ImageStore(imageDir))
    }

    @After
    fun tearDown() = db.close()

    private fun export(): ByteArray = ByteArrayOutputStream().also { runBlocking { repo.exportZip(it, now = 1) } }.toByteArray()

    private fun import(bytes: ByteArray) = runBlocking { repo.importZip(ByteArrayInputStream(bytes)) }

    private fun photo(name: String, content: String) = File(imageDir, name).also { it.writeText(content) }

    private suspend fun seed() {
        val dao = db.recipeDao()
        val cat = db.categoryDao().getOrCreate("Dinner")
        photo("one.jpg", "photo-one")
        dao.upsertWithChildren(
            RecipeEntity(
                uid = "u1", title = "Soup \"quoted\" ₹ 日本語\nline", description = "d", servingsText = "4", servings = 4.0,
                prepMinutes = 5, notes = "n", sourceUrl = "https://e.com", imageFile = "one.jpg", rating = 3,
                favorite = true, origin = RecipeOrigin.WEB, createdAt = 10, updatedAt = 20,
            ),
            listOf(IngredientEntity(position = 0, text = "Base", isHeader = true), IngredientEntity(position = 1, text = "water")),
            listOf(StepEntity(position = 0, text = "Boil")),
            listOf(cat),
        )
        dao.upsertWithChildren(RecipeEntity(uid = "u2", title = "Plain", createdAt = 11, updatedAt = 11), emptyList(), emptyList(), emptyList())
        db.shoppingDao().insertAll(
            listOf(
                ShoppingItemEntity(text = "salt", recipeId = db.backupDao().recipes().first().id, createdAt = 5),
                ShoppingItemEntity(text = "loose", checked = true, createdAt = 6),
            ),
        )
    }

    private suspend fun snapshot() = listOf(
        db.backupDao().recipes(), db.backupDao().ingredients(), db.backupDao().steps(),
        db.backupDao().categories(), db.backupDao().recipeCategories(), db.backupDao().shoppingItems(),
    )

    @Test
    fun roundTrip_restoresAllTablesAndImages() = runBlocking {
        seed()
        val before = snapshot()
        val zip = export()

        // Replace everything with different data and a different photo, then restore.
        db.backupDao().deleteRecipes()
        db.backupDao().deleteCategories()
        db.backupDao().deleteShoppingItems()
        db.recipeDao().upsertWithChildren(RecipeEntity(uid = "x", title = "Other", imageFile = "x.jpg", createdAt = 1, updatedAt = 1), emptyList(), emptyList(), emptyList())
        imageDir.listFiles()!!.forEach { it.delete() }
        photo("x.jpg", "other")

        val count = import(zip)

        assertEquals(2, count)
        assertEquals(before, snapshot())
        assertEquals(listOf("one.jpg"), imageDir.list()!!.toList())
        assertEquals("photo-one", File(imageDir, "one.jpg").readText())
        assertFalse(File(tmp.root, "images.restore").exists())
        assertFalse(File(tmp.root, "images.old").exists())
    }

    @Test
    fun zip_hasJsonAndImages_andLeavesOutMissingPhotos() = runBlocking {
        seed()
        db.recipeDao().upsertWithChildren(RecipeEntity(uid = "u3", title = "Ghost", imageFile = "gone.jpg", createdAt = 1, updatedAt = 1), emptyList(), emptyList(), emptyList())
        val names = ZipInputStream(ByteArrayInputStream(export())).let { z -> generateSequence { z.nextEntry }.map { it.name }.toList() }
        assertEquals(listOf("backup.json", "images/one.jpg"), names)
    }

    @Test
    fun roundTrip_emptyDatabase() = runBlocking {
        val zip = export()
        seed()
        assertEquals(0, import(zip))
        assertTrue(snapshot().all { it.isEmpty() })
        assertTrue(imageDir.list()!!.isEmpty())
    }

    @Test
    fun restore_isNotCappedByTheFreeLimit_butAddingStaysBlocked() = runBlocking {
        val big = Limits.FREE_RECIPE_LIMIT + 5
        val recipes = (1..big).joinToString(",") {
            """{"id":$it,"uid":"u$it","title":"R$it","rating":0,"favorite":false,"origin":"MANUAL","createdAt":1,"updatedAt":1}"""
        }
        val json = """{"format":"postpole-backup","version":1,"exportedAt":1,"recipes":[$recipes],"ingredients":[],"steps":[],"categories":[],"recipeCategories":[],"shoppingItems":[]}"""
        assertEquals(big, import(zipOf("backup.json" to json.toByteArray())))
        assertEquals(big, db.recipeDao().countNow())
        val repository = RecipeRepository(db.recipeDao(), ImageStore(imageDir))
        assertFalse(repository.canAdd(isPro = false))
        assertTrue(repository.canAdd(isPro = true))
    }

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

    private fun assertRejectedAndUntouched(bytes: ByteArray) = runBlocking {
        seed()
        val before = snapshot()
        val photos = imageDir.list()!!.sorted()
        assertThrows(InvalidBackupException::class.java) { import(bytes) }
        assertEquals(before, snapshot())
        assertEquals(photos, imageDir.list()!!.sorted())
        assertEquals("photo-one", File(imageDir, "one.jpg").readText())
        assertFalse(File(tmp.root, "images.restore").exists())
    }

    @Test
    fun malformed_notAZip() = assertRejectedAndUntouched("just text".toByteArray())

    @Test
    fun malformed_zipWithoutJson() = assertRejectedAndUntouched(zipOf("images/a.jpg" to byteArrayOf(1)))

    @Test
    fun malformed_brokenJson() = assertRejectedAndUntouched(zipOf("backup.json" to "{not json".toByteArray()))

    @Test
    fun malformed_wrongFormatName() =
        assertRejectedAndUntouched(zipOf("backup.json" to """{"format":"other","version":1}""".toByteArray()))

    @Test
    fun malformed_newerVersion() = runBlocking {
        seed()
        val json = JSONObject(String(ZipInputStream(ByteArrayInputStream(export())).also { it.nextEntry }.readBytes())).put("version", 2)
        assertRejectedAndUntouched(zipOf("backup.json" to json.toString().toByteArray()))
    }

    @Test
    fun malformed_missingArray() = runBlocking {
        seed()
        val json = JSONObject(String(ZipInputStream(ByteArrayInputStream(export())).also { it.nextEntry }.readBytes()))
        json.remove("steps")
        assertRejectedAndUntouched(zipOf("backup.json" to json.toString().toByteArray()))
    }

    @Test
    fun malformed_duplicateRecipeIds() = runBlocking {
        seed()
        val json = JSONObject(String(ZipInputStream(ByteArrayInputStream(export())).also { it.nextEntry }.readBytes()))
        val recipes = json.getJSONArray("recipes")
        recipes.getJSONObject(1).put("id", recipes.getJSONObject(0).getLong("id"))
        assertRejectedAndUntouched(zipOf("backup.json" to json.toString().toByteArray()))
    }

    @Test
    fun malformed_ingredientPointsAtMissingRecipe() = runBlocking {
        seed()
        val json = JSONObject(String(ZipInputStream(ByteArrayInputStream(export())).also { it.nextEntry }.readBytes()))
        json.getJSONArray("ingredients").getJSONObject(0).put("recipeId", 9999)
        assertRejectedAndUntouched(zipOf("backup.json" to json.toString().toByteArray()))
    }

    @Test
    fun malformed_imageNameEscapesTheFolder() = runBlocking {
        seed()
        val zip = ZipInputStream(ByteArrayInputStream(export())).let { z ->
            z.nextEntry
            z.readBytes()
        }
        assertRejectedAndUntouched(zipOf("backup.json" to zip, "images/../evil.jpg" to byteArrayOf(1)))
        assertFalse(File(tmp.root, "evil.jpg").exists())
    }

    @Test
    fun images_areRestoredByteForByte() = runBlocking {
        seed()
        File(imageDir, "one.jpg").writeBytes(byteArrayOf(0, 1, 2, -1, -128, 127))
        val zip = export()
        File(imageDir, "one.jpg").delete()
        import(zip)
        assertArrayEquals(byteArrayOf(0, 1, 2, -1, -128, 127), File(imageDir, "one.jpg").readBytes())
    }
}
