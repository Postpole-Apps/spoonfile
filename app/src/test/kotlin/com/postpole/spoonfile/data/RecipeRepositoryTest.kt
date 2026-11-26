package com.postpole.spoonfile.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class RecipeRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: RecipeRepository
    private lateinit var imageDir: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        imageDir = File(context.cacheDir, "images-test-${System.nanoTime()}").also { it.mkdirs() }
        repo = RecipeRepository(db.recipeDao(), ImageStore(imageDir))
    }

    @After
    fun tearDown() {
        db.close()
        imageDir.deleteRecursively()
    }

    private suspend fun fill(n: Int) {
        repeat(n) {
            repo.upsertWithChildren(
                RecipeEntity(uid = "u$it", title = "r$it", createdAt = 1, updatedAt = 1),
                emptyList(),
                emptyList(),
                emptyList(),
            )
        }
    }

    private suspend fun add(uid: String, updatedAt: Long, lastCookedAt: Long? = null) = repo.upsertWithChildren(
        RecipeEntity(uid = uid, title = uid, createdAt = 1, updatedAt = updatedAt, lastCookedAt = lastCookedAt),
        emptyList(),
        emptyList(),
        emptyList(),
    )

    private suspend fun titles(sort: RecipeSort) = repo.observeList(sort = sort).first().map { it.title }

    @Test
    fun markCooked_setsOnlyLastCookedAtAndOverwrites() = runBlocking {
        val id = add("a", updatedAt = 50)
        assertNull(db.recipeDao().getById(id)!!.lastCookedAt)

        repo.markCooked(id, 1_000)
        repo.markCooked(id, 2_000)

        val r = db.recipeDao().getById(id)!!
        assertEquals(2_000L, r.lastCookedAt)
        assertEquals(50L, r.updatedAt)
    }

    @Test
    fun editingARecipe_keepsLastCookedAt_andNewOnesAreNull() = runBlocking {
        val id = add("a", updatedAt = 1)
        repo.markCooked(id, 777)
        val stored = db.recipeDao().getById(id)!!

        repo.upsertWithChildren(stored.copy(title = "Renamed", updatedAt = 9), emptyList(), emptyList(), emptyList())

        assertEquals(777L, db.recipeDao().getById(id)!!.lastCookedAt)
        assertNull(db.recipeDao().getById(add("b", updatedAt = 1))!!.lastCookedAt)
    }

    @Test
    fun lastCookedSort_ordersByStampDescending_nullsLast_thenUpdatedAt() = runBlocking {
        add("old", updatedAt = 10, lastCookedAt = 100)
        add("new", updatedAt = 20, lastCookedAt = 300)
        add("neverNewer", updatedAt = 40)
        add("neverOlder", updatedAt = 30)
        add("mid", updatedAt = 5, lastCookedAt = 200)

        assertEquals(listOf("new", "mid", "old", "neverNewer", "neverOlder"), titles(RecipeSort.LAST_COOKED))
        // Recent is unchanged: last edited first, cooking does not reorder it.
        assertEquals(listOf("neverNewer", "neverOlder", "new", "old", "mid"), titles(RecipeSort.RECENT))
    }

    @Test
    fun limit_is75() = assertEquals(75, Limits.FREE_RECIPE_LIMIT)

    @Test
    fun at74_canAdd() = runBlocking {
        fill(74)
        assertEquals(1, repo.remainingSlots(isPro = false))
        assertTrue(repo.canAdd(isPro = false))
    }

    @Test
    fun at75_blocked() = runBlocking {
        fill(75)
        assertEquals(0, repo.remainingSlots(isPro = false))
        assertFalse(repo.canAdd(isPro = false))
    }

    @Test
    fun pro_neverBlocked() = runBlocking {
        fill(75)
        assertEquals(Int.MAX_VALUE, repo.remainingSlots(isPro = true))
        assertTrue(repo.canAdd(isPro = true, n = 1000))
    }

    @Test
    fun batchOf10_with5Slots_onlyFiveFit() = runBlocking {
        fill(70)
        assertEquals(5, repo.remainingSlots(isPro = false))
        assertFalse(repo.canAdd(isPro = false, n = 10))
        assertTrue(repo.canAdd(isPro = false, n = 5))
    }

    @Test
    fun delete_removesImageFile() = runBlocking {
        val photo = File(imageDir, "abc.jpg").also { it.writeText("x") }
        val id = repo.upsertWithChildren(
            RecipeEntity(uid = "u", title = "t", imageFile = photo.name, createdAt = 1, updatedAt = 1),
            listOf(IngredientEntity(position = 0, text = "egg")),
            emptyList(),
            emptyList(),
        )
        assertTrue(photo.exists())

        repo.delete(id)

        assertFalse(photo.exists())
        assertEquals(0, db.recipeDao().countNow())
    }

    @Test
    fun replacingPhoto_removesOldFile() = runBlocking {
        val old = File(imageDir, "old.jpg").also { it.writeText("x") }
        val recipe = RecipeEntity(uid = "u", title = "t", imageFile = old.name, createdAt = 1, updatedAt = 1)
        val id = repo.upsertWithChildren(recipe, emptyList(), emptyList(), emptyList())

        repo.upsertWithChildren(recipe.copy(id = id, imageFile = "new.jpg"), emptyList(), emptyList(), emptyList())

        assertFalse(old.exists())
    }
}
