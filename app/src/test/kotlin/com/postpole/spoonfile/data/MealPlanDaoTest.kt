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
    private lateinit var repo: MealPlanRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = MealPlanRepository(db.mealPlanDao())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun recipe(title: String, ingredients: List<String> = emptyList()) = db.recipeDao().upsertWithChildren(
        RecipeEntity(uid = title, title = title, createdAt = 1, updatedAt = 1),
        ingredients.mapIndexed { i, t -> IngredientEntity(position = i, text = t) },
        emptyList(),
        emptyList(),
    )

    private val monday = LocalDate.of(2026, 11, 16)

    @Test
    fun add_ordersEntriesWithinADayAndAllowsRepeats() = runBlocking {
        val soup = recipe("Soup")
        val salad = recipe("Salad")
        val day = monday.plusDays(2)
        repo.add(day, soup)
        repo.add(day, salad)
        repo.add(day, soup) // same recipe twice on one day
        repo.add(monday, soup) // and on another day

        val week = repo.observeWeek(monday).first()
        assertEquals(listOf("Soup"), week.filter { it.date == monday.toString() }.map { it.title })
        val onDay = week.filter { it.date == day.toString() }
        assertEquals(listOf("Soup", "Salad", "Soup"), onDay.map { it.title })
        assertEquals(listOf(0, 1, 2), onDay.map { it.position })
        assertEquals(listOf(soup, salad, soup), repo.recipeIdsOn(day))
    }

    @Test
    fun observeWeek_coversSevenDaysOnly() = runBlocking {
        val r = recipe("Soup")
        repo.add(monday.minusDays(1), r)
        repo.add(monday, r)
        repo.add(monday.plusDays(6), r)
        repo.add(monday.plusDays(7), r)

        val week = repo.observeWeek(monday).first()
        assertEquals(listOf(monday.toString(), monday.plusDays(6).toString()), week.map { it.date })
        assertEquals(1, repo.observeWeek(monday.plusDays(7)).first().size)
    }

    @Test
    fun remove_deletesOneEntry() = runBlocking {
        val r = recipe("Soup")
        val first = repo.add(monday, r)
        repo.add(monday, r)
        repo.remove(first)
        assertEquals(1, repo.observeWeek(monday).first().size)
    }

    @Test
    fun deletingARecipe_cascadesItsPlanRows_butKeepsShoppingRowsAsOther() = runBlocking {
        val soup = recipe("Soup")
        val salad = recipe("Salad")
        repo.add(monday, soup)
        repo.add(monday, salad)
        db.shoppingDao().insertAll(listOf(ShoppingItemEntity(text = "milk", recipeId = soup, createdAt = 1)))

        db.recipeDao().delete(soup)

        assertEquals(listOf("Salad"), repo.observeWeek(monday).first().map { it.title })
        assertEquals(1, db.mealPlanDao().countNow())
        // Shopping behaviour is unchanged: the row stays, detached from the deleted recipe.
        assertEquals(null, db.shoppingDao().getAll().single().recipeId)
    }

    @Test
    fun addingADaysIngredients_groupsByRecipeLikeTheDetailScreen() = runBlocking {
        val soup = recipe("Soup", listOf("2 tomatoes", "1 onion"))
        val salad = recipe("Salad", listOf("lettuce"))
        repo.add(monday, soup)
        repo.add(monday, salad)
        val shopping = ShoppingRepository(db.shoppingDao())

        repo.recipeIdsOn(monday).forEach { id ->
            val lines = db.recipeDao().observeDetails(id).first()!!.ingredients.map { it.text }
            shopping.addRecipeLines(id, lines)
        }

        val items = db.shoppingDao().getAll()
        assertEquals(listOf("2 tomatoes", "1 onion", "lettuce"), items.map { it.text })
        assertEquals(listOf(soup, soup, salad), items.map { it.recipeId })
    }
}
