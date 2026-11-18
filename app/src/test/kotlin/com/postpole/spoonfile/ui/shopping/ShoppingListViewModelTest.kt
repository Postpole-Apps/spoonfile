package com.postpole.spoonfile.ui.shopping

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.postpole.spoonfile.data.AppDatabase
import com.postpole.spoonfile.data.IngredientEntity
import com.postpole.spoonfile.data.RecipeEntity
import com.postpole.spoonfile.data.ShoppingGroup
import com.postpole.spoonfile.data.ShoppingRepository
import com.postpole.spoonfile.domain.QuantityScaler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ShoppingListViewModelTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: ShoppingRepository
    private lateinit var vm: ShoppingListViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = ShoppingRepository(db.shoppingDao())
        vm = ShoppingListViewModel(repo)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun addRecipe(title: String): Long = runBlocking {
        db.recipeDao().upsertWithChildren(
            RecipeEntity(uid = "u-$title", title = title, createdAt = 1, updatedAt = 1),
            emptyList(), emptyList(), emptyList(),
        )
    }

    private fun ingredients(vararg lines: String) = lines.mapIndexed { i, t -> IngredientEntity(id = i + 1L, position = i, text = t) }

    private fun groups(): List<ShoppingGroup> = runBlocking { repo.observeGroups().first() }

    /** ViewModel actions finish on Room's executor, so wait for the list to reach the expected state. */
    private fun groupsWhen(check: (List<ShoppingGroup>) -> Boolean): List<ShoppingGroup> =
        runBlocking { withTimeout(5_000) { repo.observeGroups().first(check) } }

    @Test
    fun groupsFollowTheOrderRecipesWereAdded_andOtherIsLast() = runBlocking {
        val soup = addRecipe("Soup")
        val bread = addRecipe("Bread")
        repo.addManual("Dish soap")
        // Bread goes on the list first, then Soup; "Other" was added before both but stays last.
        repo.addFromRecipe(bread, ingredients("500 g flour"), 1.0)
        repo.addFromRecipe(soup, ingredients("2 carrots"), 1.0)
        repo.addManual("Batteries")

        val result = groups()
        assertEquals(listOf("Bread", "Soup", null), result.map { it.title })
        assertEquals(listOf("Dish soap", "Batteries"), result.last().items.map { it.text })
    }

    @Test
    fun quantitiesAreScaledFromTheRecipeServings() = runBlocking {
        val id = addRecipe("Pancakes")
        val added = repo.addFromRecipe(
            id,
            ingredients("1 1/2 cups flour", "2 eggs", "salt to taste").plus(IngredientEntity(id = 9, position = 3, text = "Batter", isHeader = true)),
            scale = 2.0,
        )
        assertEquals(3, added) // the header is not an item
        assertEquals(listOf("3 cups flour", "4 eggs", "salt to taste"), groups().single().items.map { it.text })
    }

    @Test
    fun itemsAddedByHandGoToOther() = runBlocking {
        vm.addItem("  Milk  ")
        vm.addItem("   ") // blank is ignored
        val result = groupsWhen { it.isNotEmpty() }
        assertEquals(1, result.size)
        assertNull(result.single().recipeId)
        assertEquals(listOf("Milk"), result.single().items.map { it.text })
    }

    @Test
    fun checkOffInPlaceThenClearChecked() = runBlocking {
        val id = addRecipe("Soup")
        repo.addFromRecipe(id, ingredients("1 onion", "2 carrots", "1 leek"), 1.0)
        val items = groups().single().items

        vm.toggle(items[1].id, true)
        val afterCheck = groupsWhen { g -> g.single().items.any { it.checked } }.single().items
        assertEquals(listOf("1 onion", "2 carrots", "1 leek"), afterCheck.map { it.text }) // same place
        assertEquals(listOf(false, true, false), afterCheck.map { it.checked })

        vm.toggle(items[1].id, false)
        assertFalse(groupsWhen { g -> g.single().items.none { it.checked } }.single().items.any { it.checked })

        vm.toggle(items[0].id, true)
        vm.toggle(items[2].id, true)
        groupsWhen { g -> g.single().items.count { it.checked } == 2 }
        vm.clearChecked()
        assertEquals(listOf("2 carrots"), groupsWhen { g -> g.single().items.size == 1 }.single().items.map { it.text })
    }

    @Test
    fun itemsOfADeletedRecipeFallIntoOther() = runBlocking {
        val id = addRecipe("Soup")
        repo.addFromRecipe(id, ingredients("1 onion"), 1.0)
        db.recipeDao().delete(id)
        val result = groups()
        assertEquals(listOf(null), result.map { it.recipeId })
        assertEquals(listOf("1 onion"), result.single().items.map { it.text })
    }

    @Test
    fun shareTextListsUncheckedItemsUnderTheirGroups() = runBlocking {
        val id = addRecipe("Soup")
        repo.addFromRecipe(id, ingredients("1 onion", "2 carrots"), 1.0)
        repo.addManual("Batteries")
        val onion = groups().first().items.first()
        repo.setChecked(onion.id, true)

        assertEquals(
            "Soup\n• 2 carrots\n\nOther\n• Batteries",
            ShoppingListViewModel.shareText(groups(), "Other"),
        )
    }

    @Test
    fun splitQuantityPutsTheAmountInItsOwnColumn() {
        assertEquals("2 cups" to "flour", QuantityScaler.splitQuantity("2 cups flour"))
        assertEquals("1 1/2" to "onions", QuantityScaler.splitQuantity("1 1/2 onions"))
        assertEquals("" to "salt to taste", QuantityScaler.splitQuantity("salt to taste"))
        assertTrue(QuantityScaler.splitQuantity("3").second.isEmpty())
    }
}
