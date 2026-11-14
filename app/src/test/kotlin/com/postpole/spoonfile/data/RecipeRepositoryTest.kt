package com.postpole.spoonfile.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private class FakeImageStore : ImageStore {
    val deleted = mutableListOf<String>()
    override suspend fun save(bytes: ByteArray): String? = null
    override fun delete(fileName: String?) {
        if (fileName != null) deleted += fileName
    }
    override fun resolve(fileName: String) = java.io.File(fileName)
}

@RunWith(RobolectricTestRunner::class)
class RecipeRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var images: FakeImageStore
    private lateinit var repo: RecipeRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        images = FakeImageStore()
        repo = RecipeRepository(db.recipeDao(), db.categoryDao(), images)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun add(n: Int) = repeat(n) {
        repo.upsertWithChildren(
            RecipeEntity(title = "r$it", createdAt = 1, updatedAt = 1),
            emptyList(),
            emptyList(),
            emptyList(),
        )
    }

    @Test
    fun limitIsSeventyFive() = assertEquals(75, Limits.FREE_RECIPE_LIMIT)

    @Test
    fun at74_canAdd() = runBlocking {
        add(74)
        assertEquals(1, repo.remainingSlots(false))
        assertTrue(repo.canAdd(false))
    }

    @Test
    fun at75_blocked() = runBlocking {
        add(75)
        assertEquals(0, repo.remainingSlots(false))
        assertFalse(repo.canAdd(false))
    }

    @Test
    fun pro_alwaysOk() = runBlocking {
        add(75)
        assertEquals(Int.MAX_VALUE, repo.remainingSlots(true))
        assertTrue(repo.canAdd(true, 1000))
    }

    @Test
    fun batchOfTenWithFiveSlots_allowsFive() = runBlocking {
        add(70)
        assertEquals(5, minOf(10, repo.remainingSlots(false)))
        assertFalse(repo.canAdd(false, 10))
        assertTrue(repo.canAdd(false, 5))
    }

    @Test
    fun delete_removesRecipeAndImageFile() = runBlocking {
        val id = repo.upsertWithChildren(
            RecipeEntity(title = "  Pie ", imageFile = "abc.jpg", createdAt = 1, updatedAt = 1),
            listOf(IngredientEntity(position = 0, text = "apple")),
            emptyList(),
            emptyList(),
        )
        repo.delete(id)
        assertEquals(listOf("abc.jpg"), images.deleted)
        assertEquals(0, db.recipeDao().countNow())
    }

    @Test
    fun upsert_trimsTitleAndDeletesReplacedImage() = runBlocking {
        val id = repo.upsertWithChildren(
            RecipeEntity(title = "  Pie ", imageFile = "old.jpg", createdAt = 1, updatedAt = 1),
            emptyList(),
            emptyList(),
            emptyList(),
        )
        val saved = db.recipeDao().getById(id)!!
        assertEquals("Pie", saved.title)
        repo.upsertWithChildren(saved.copy(imageFile = "new.jpg"), emptyList(), emptyList(), emptyList())
        assertEquals(listOf("old.jpg"), images.deleted)
    }
}
