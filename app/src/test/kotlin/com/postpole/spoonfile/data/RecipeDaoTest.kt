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
    private lateinit var categories: CategoryDao
    private lateinit var shopping: ShoppingDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.recipeDao()
        categories = db.categoryDao()
        shopping = db.shoppingDao()
    }

    @After
    fun tearDown() = db.close()

    private fun recipe(title: String, uid: String = title, rating: Int = 0, updatedAt: Long = 1) =
        RecipeEntity(uid = uid, title = title, rating = rating, createdAt = 1, updatedAt = updatedAt)

    private fun ing(text: String) = IngredientEntity(position = 0, text = text)

    private suspend fun list(query: String = "", categoryId: Long? = null, sort: RecipeSort = RecipeSort.RECENT) =
        dao.observeList(query, categoryId, false, sort).first().map { it.title }

    @Test
    fun search_matchesTitleAndIngredientText() = runBlocking {
        dao.upsertWithChildren(recipe("Pancakes"), listOf(ing("1 cup flour")), emptyList(), emptyList())
        dao.upsertWithChildren(recipe("Salad"), listOf(ing("lettuce"), ing("Pancake syrup")), emptyList(), emptyList())
        dao.upsertWithChildren(recipe("Soup"), listOf(ing("water")), emptyList(), emptyList())

        assertEquals(setOf("Pancakes", "Salad"), list("pancake").toSet())
        assertEquals(listOf("Pancakes"), list("flour"))
        assertEquals(3, list("").size)
    }

    @Test
    fun sort_azAndRating() = runBlocking {
        dao.upsertWithChildren(recipe("banana", rating = 5, updatedAt = 1), emptyList(), emptyList(), emptyList())
        dao.upsertWithChildren(recipe("Apple", rating = 1, updatedAt = 2), emptyList(), emptyList(), emptyList())
        dao.upsertWithChildren(recipe("cherry", rating = 3, updatedAt = 3), emptyList(), emptyList(), emptyList())

        assertEquals(listOf("Apple", "banana", "cherry"), list(sort = RecipeSort.AZ))
        assertEquals(listOf("banana", "cherry", "Apple"), list(sort = RecipeSort.RATING))
        assertEquals(listOf("cherry", "Apple", "banana"), list(sort = RecipeSort.RECENT))
    }

    @Test
    fun categoryJunction_filtersAndCounts() = runBlocking {
        val dinner = categories.getOrCreate("Dinner")
        assertEquals(dinner, categories.getOrCreate("  dinner "))
        val a = dao.upsertWithChildren(recipe("A"), emptyList(), emptyList(), listOf(dinner))
        dao.upsertWithChildren(recipe("B"), emptyList(), emptyList(), emptyList())

        assertEquals(listOf("A"), list(categoryId = dinner))
        assertEquals(listOf(1), categories.observeAllWithCounts().first().map { it.recipeCount })
        assertEquals(listOf("Dinner"), dao.observeDetails(a).first()!!.categories.map { it.name })
    }

    @Test
    fun upsert_sameUid_replacesChildrenAndTrimsTitle() = runBlocking {
        val id = dao.upsertWithChildren(recipe("  Tea  ", uid = "u1"), listOf(ing("water"), ing("leaf")), emptyList(), emptyList())
        val id2 = dao.upsertWithChildren(recipe("Tea", uid = "u1"), listOf(ing("milk")), emptyList(), emptyList())

        assertEquals(id, id2)
        assertEquals(1, dao.countNow())
        val details = dao.observeDetails(id).first()!!
        assertEquals("Tea", details.recipe.title)
        assertEquals(listOf("milk"), details.ingredients.map { it.text })
    }

    @Test
    fun delete_cascadesChildren_andShoppingItemSurvivesWithNullRecipe() = runBlocking {
        val cat = categories.getOrCreate("X")
        val id = dao.upsertWithChildren(
            recipe("Gone"),
            listOf(ing("egg")),
            listOf(StepEntity(position = 0, text = "cook")),
            listOf(cat),
        )
        shopping.insertAll(listOf(ShoppingItemEntity(text = "egg", recipeId = id, createdAt = 1)))

        dao.delete(id)

        assertNull(dao.observeDetails(id).first())
        assertEquals(0, db.query("SELECT COUNT(*) FROM recipe_ingredients", null).use { it.moveToFirst(); it.getInt(0) })
        assertEquals(0, db.query("SELECT COUNT(*) FROM recipe_steps", null).use { it.moveToFirst(); it.getInt(0) })
        assertEquals(0, db.query("SELECT COUNT(*) FROM recipe_categories", null).use { it.moveToFirst(); it.getInt(0) })
        val items = shopping.observeAll().first()
        assertEquals(1, items.size)
        assertNull(items[0].recipeId)
    }

    @Test
    fun shopping_setCheckedAndDeleteChecked() = runBlocking {
        shopping.insertAll(
            listOf(
                ShoppingItemEntity(text = "a", createdAt = 1),
                ShoppingItemEntity(text = "b", createdAt = 1, position = 1),
            ),
        )
        val first = shopping.observeAll().first().first()
        shopping.setChecked(first.id, true)
        shopping.deleteChecked()
        assertEquals(listOf("b"), shopping.observeAll().first().map { it.text })
    }
}
