package com.postpole.spoonfile.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class MealPlanDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var plan: MealPlanRepository
    private lateinit var shopping: ShoppingRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        shopping = ShoppingRepository(db.shoppingDao())
        plan = MealPlanRepository(db.mealPlanDao(), db.recipeDao(), shopping)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun recipe(title: String, vararg ingredients: String): Long =
        db.recipeDao().upsertWithChildren(
            RecipeEntity(uid = title, title = title, createdAt = 1, updatedAt = 1),
            listOf(IngredientEntity(position = 0, text = "For the sauce", isHeader = true)) +
                ingredients.map { IngredientEntity(position = 0, text = it) },
            emptyList(),
            emptyList(),
        )

    private val monday = LocalDate.of(2026, 11, 23)

    private suspend fun week(from: LocalDate = monday) = plan.observeRange(from, from.plusDays(6)).first()

    @Test
    fun add_ordersEntriesWithinADayAndAllowsDuplicates() = runBlocking {
        val soup = recipe("Soup")
        val salad = recipe("Salad")
        plan.add(monday, soup)
        plan.add(monday, salad)
        plan.add(monday, soup)

        val rows = week()
        assertEquals(listOf("Soup", "Salad", "Soup"), rows.map { it.recipeTitle })
        assertEquals(listOf(0, 1, 2), rows.map { it.entry.position })
        assertEquals(listOf("2026-11-23"), rows.map { it.entry.date }.distinct())
    }

    @Test
    fun observeRange_returnsOnlyThatWeekInDayOrder() = runBlocking {
        val soup = recipe("Soup")
        plan.add(monday.plusDays(7), soup) // next week
        plan.add(monday.plusDays(2), soup)
        plan.add(monday.minusDays(1), soup) // previous week
        plan.add(monday, soup)
        plan.add(monday.plusDays(6), soup)

        assertEquals(
            listOf("2026-11-23", "2026-11-25", "2026-11-29"),
            week().map { it.entry.date },
        )
        assertEquals(listOf("2026-11-30"), week(monday.plusDays(7)).map { it.entry.date })
    }

    @Test
    fun remove_deletesOnlyThatEntry() = runBlocking {
        val soup = recipe("Soup")
        val first = plan.add(monday, soup)
        plan.add(monday, soup)

        plan.remove(first)

        assertEquals(1, week().size)
    }

    @Test
    fun deletingARecipe_cascadesItsPlanEntries() = runBlocking {
        val soup = recipe("Soup")
        val salad = recipe("Salad")
        plan.add(monday, soup)
        plan.add(monday.plusDays(1), soup)
        plan.add(monday, salad)

        db.recipeDao().delete(soup)

        assertEquals(listOf("Salad"), week().map { it.recipeTitle })
        assertEquals(1, db.mealPlanDao().getAll().size)
    }

    @Test
    fun deletingARecipe_stillKeepsItsShoppingItems() = runBlocking {
        val soup = recipe("Soup", "2 tomatoes")
        shopping.addRecipeLines(soup, listOf("2 tomatoes"))
        plan.add(monday, soup)

        db.recipeDao().delete(soup)

        val items = db.shoppingDao().getAll()
        assertEquals(listOf("2 tomatoes"), items.map { it.text })
        assertEquals(listOf<Long?>(null), items.map { it.recipeId })
    }

    @Test
    fun addToShopping_groupsByRecipeAtDefaultServingsAndSkipsHeaders() = runBlocking {
        val soup = recipe("Soup", "2 tomatoes", "1 onion")
        val salad = recipe("Salad", "1 lettuce")

        val added = plan.addToShopping(listOf(soup, salad))

        assertEquals(3, added)
        val rows = db.shoppingDao().getAll()
        assertEquals(listOf("2 tomatoes", "1 onion", "1 lettuce"), rows.map { it.text })
        assertEquals(listOf(soup, soup, salad), rows.map { it.recipeId })
    }

    @Test
    fun addToShopping_withNothingToAddIsZero() = runBlocking {
        assertEquals(0, plan.addToShopping(emptyList()))
        assertEquals(0, plan.addToShopping(listOf(recipe("Empty"))))
    }
}
