package com.postpole.spoonfile.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        repo = RecipeRepository(db.recipeDao(), ImageStore(imageDir), Dispatchers.Unconfined)
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

    private suspend fun add(title: String, favorite: Boolean = false, rating: Int = 0, ingredient: String? = null) =
        repo.upsertWithChildren(
            RecipeEntity(uid = title, title = title, favorite = favorite, rating = rating, createdAt = 1, updatedAt = 1),
            listOfNotNull(ingredient?.let { IngredientEntity(position = 0, text = it) }),
            emptyList(),
            emptyList(),
        )

    @Test
    fun favoritesOnly_filtersList() = runBlocking {
        add("Soup", favorite = true)
        add("Salad")
        val all = repo.observeList().first().map { it.title }
        val favs = repo.observeList(favoritesOnly = true).first().map { it.title }
        assertEquals(setOf("Soup", "Salad"), all.toSet())
        assertEquals(listOf("Soup"), favs)
    }

    @Test
    fun search_treatsLikeWildcardsLiterally() = runBlocking {
        add("100% juice")
        add("Plain")
        add("a_b", ingredient = "1 cup x")
        assertEquals(listOf("100% juice"), repo.observeList(query = "%").first().map { it.title })
        assertEquals(listOf("a_b"), repo.observeList(query = "_").first().map { it.title })
        assertEquals(emptyList<String>(), repo.observeList(query = "\\").first().map { it.title })
    }

    @Test
    fun rating_isClampedTo0to5() = runBlocking {
        val id = add("Rated", rating = 9)
        assertEquals(5, repo.observeDetails(id).first()!!.recipe.rating)
    }
}
