package com.postpole.spoonfile.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecipeDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: RecipeDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var shoppingDao: ShoppingDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.recipeDao()
        categoryDao = db.categoryDao()
        shoppingDao = db.shoppingDao()
    }

    @After
    fun tearDown() = db.close()

    private fun recipe(title: String, id: Long = 0, rating: Int = 0, updatedAt: Long = 1) =
        RecipeEntity(id = id, title = title, rating = rating, createdAt = 1, updatedAt = updatedAt)

    private fun ing(text: String) = IngredientEntity(position = 0, text = text)

    private suspend fun list(
        q: String = "",
        cat: Long? = null,
        fav: Boolean = false,
        sort: RecipeSort = RecipeSort.RECENT,
    ) = dao.observeList(q, cat, fav, sort).first().map { it.title }

    @Test
    fun search_matchesTitleAndIngredientText() = runBlocking {
        dao.upsertWithChildren(recipe("Pancakes"), listOf(ing("1 cup flour")), emptyList(), emptyList())
        dao.upsertWithChildren(recipe("Salad"), listOf(ing("lettuce"), ing("tomato")), emptyList(), emptyList())
        assertEquals(listOf("Pancakes"), list("cake"))
        assertEquals(listOf("Salad"), list("TOMATO"))
        assertEquals(emptyList<String>(), list("zzz"))
        assertEquals(2, list("").size)
    }

    @Test
    fun sort_azAndRating() = runBlocking {
        dao.upsertWithChildren(recipe("banana", rating = 5, updatedAt = 1), emptyList(), emptyList(), emptyList())
        dao.upsertWithChildren(recipe("Apple", rating = 2, updatedAt = 2), emptyList(), emptyList(), emptyList())
        dao.upsertWithChildren(recipe("cherry", rating = 3, updatedAt = 3), emptyList(), emptyList(), emptyList())
        assertEquals(listOf("cherry", "Apple", "banana"), list(sort = RecipeSort.RECENT))
        assertEquals(listOf("Apple", "banana", "cherry"), list(sort = RecipeSort.AZ))
        assertEquals(listOf("banana", "cherry", "Apple"), list(sort = RecipeSort.RATING))
    }

    @Test
    fun favoritesOnly_filters() = runBlocking {
        dao.upsertWithChildren(recipe("A").copy(favorite = true), emptyList(), emptyList(), emptyList())
        dao.upsertWithChildren(recipe("B"), emptyList(), emptyList(), emptyList())
        assertEquals(listOf("A"), list(fav = true))
    }

    @Test
    fun categoryJunction_filtersCountsAndGetOrCreate() = runBlocking {
        val dinner = categoryDao.getOrCreate("Dinner")
        assertEquals(dinner, categoryDao.getOrCreate("  dinner "))
        val dessert = categoryDao.getOrCreate("Dessert")
        dao.upsertWithChildren(recipe("Stew"), emptyList(), emptyList(), listOf(dinner))
        val cakeId = dao.upsertWithChildren(recipe("Cake"), emptyList(), emptyList(), listOf(dinner, dessert))
        assertEquals(listOf("Cake"), list(cat = dessert))
        assertEquals(2, list(cat = dinner).size)
        val counts = categoryDao.observeAllWithCounts().first().associate { it.category.name to it.recipeCount }
        assertEquals(mapOf("Dinner" to 2, "Dessert" to 1), counts)
        val details = dao.observeDetails(cakeId).first()!!
        assertEquals(setOf("Dinner", "Dessert"), details.categories.map { it.name }.toSet())
    }

    @Test
    fun upsert_replacesChildren() = runBlocking {
        val id = dao.upsertWithChildren(
            recipe("Soup"),
            listOf(ing("water"), ing("salt")),
            listOf(StepEntity(position = 0, text = "boil")),
            emptyList(),
        )
        dao.upsertWithChildren(
            recipe("Soup", id = id),
            listOf(ing("stock")),
            emptyList(),
            emptyList(),
        )
        val d = dao.observeDetails(id).first()!!
        assertEquals(listOf("stock"), d.orderedIngredients.map { it.text })
        assertEquals(0, d.steps.size)
        assertEquals(1, dao.countNow())
    }

    @Test
    fun deleteRecipe_cascadesChildren() = runBlocking {
        val cat = categoryDao.getOrCreate("X")
        val id = dao.upsertWithChildren(
            recipe("Gone"),
            listOf(ing("a")),
            listOf(StepEntity(position = 0, text = "s")),
            listOf(cat),
        )
        dao.delete(id)
        assertNull(dao.observeDetails(id).first())
        val c = db.openHelper.writableDatabase
        for (t in listOf("recipe_ingredients", "recipe_steps", "recipe_categories")) {
            c.query("SELECT COUNT(*) FROM $t").use {
                it.moveToFirst()
                assertEquals(t, 0, it.getInt(0))
            }
        }
    }

    @Test
    fun shoppingItem_survivesRecipeDeleteWithNullRecipeId() = runBlocking {
        val id = dao.upsertWithChildren(recipe("R"), emptyList(), emptyList(), emptyList())
        shoppingDao.insertAll(listOf(ShoppingItemEntity(text = "milk", recipeId = id, createdAt = 1)))
        dao.delete(id)
        val items = shoppingDao.observeAll().first()
        assertEquals(1, items.size)
        assertNull(items[0].recipeId)
    }

    @Test
    fun shopping_checkAndDeleteChecked() = runBlocking {
        shoppingDao.insertAll(
            listOf(
                ShoppingItemEntity(text = "a", createdAt = 1),
                ShoppingItemEntity(text = "b", createdAt = 1),
            ),
        )
        val first = shoppingDao.observeAll().first().first { it.text == "a" }
        shoppingDao.setChecked(first.id, true)
        shoppingDao.deleteChecked()
        assertEquals(listOf("b"), shoppingDao.observeAll().first().map { it.text })
    }

    @Test
    fun lookups_byUidAndSourceUrl() = runBlocking {
        dao.upsertWithChildren(
            recipe("R").copy(uid = "u-1", sourceUrl = "https://example.com/r"),
            emptyList(),
            emptyList(),
            emptyList(),
        )
        assertEquals("R", dao.getByUid("u-1")?.title)
        assertEquals("R", dao.findBySourceUrl("https://example.com/r")?.title)
        assertNull(dao.getByUid("nope"))
    }
}
