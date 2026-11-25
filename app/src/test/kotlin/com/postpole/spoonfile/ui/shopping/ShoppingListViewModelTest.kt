package com.postpole.spoonfile.ui.shopping

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.postpole.spoonfile.data.AppDatabase
import com.postpole.spoonfile.data.ImageStore
import com.postpole.spoonfile.data.IngredientEntity
import com.postpole.spoonfile.data.RecipeEntity
import com.postpole.spoonfile.data.RecipeRepository
import com.postpole.spoonfile.data.ShoppingRepository
import com.postpole.spoonfile.ui.recipe.RecipeDetailViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ShoppingListViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var recipes: RecipeRepository
    private lateinit var shopping: ShoppingRepository
    private lateinit var vm: ShoppingListViewModel
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        recipes = RecipeRepository(db.recipeDao(), ImageStore(File(tmp.root, "images")), Dispatchers.Unconfined)
        shopping = ShoppingRepository(db.shoppingDao())
        vm = ShoppingListViewModel(shopping)
        scope = CoroutineScope(Dispatchers.Unconfined + Job())
    }

    @After
    fun tearDown() {
        scope.coroutineContext[Job]?.cancel()
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun addRecipe(title: String, servings: Double?, vararg lines: String): Long =
        recipes.upsertWithChildren(
            RecipeEntity(uid = title, title = title, servings = servings, createdAt = 1, updatedAt = 1),
            lines.mapIndexed { i, t -> IngredientEntity(position = i, text = t, isHeader = t.startsWith("#")) },
            emptyList(),
            emptyList(),
        )

    private fun detailVm(recipeId: Long) =
        RecipeDetailViewModel(SavedStateHandle(mapOf("id" to recipeId)), recipes, shopping, com.postpole.spoonfile.promo.NoOpProPromptRecorder)

    private suspend fun awaitState(predicate: (ShoppingListUiState) -> Boolean): ShoppingListUiState =
        withContext(Dispatchers.Default) { withTimeout(10_000) { vm.state.first(predicate) } }

    /** The state is `WhileSubscribed`, so keep a collector alive while the test acts. */
    private fun keepCollecting(): Job = scope.launch { vm.state.collect { } }

    private fun ShoppingListUiState.texts() = groups.map { g -> (g.title ?: "Other") to g.items.map { it.text } }

    @Test
    fun groupsFollowTheOrderRecipesWereAdded_andOtherIsLast() = runTest {
        val soup = addRecipe("Soup", 2.0, "1 onion")
        val bread = addRecipe("Bread", 4.0, "500 g flour")
        // Added by hand first, then Bread, then Soup: Other still comes last and Bread precedes Soup.
        shopping.addByHand("milk")
        shopping.addRecipeLines(bread, listOf("500 g flour", "1 tsp salt"))
        shopping.addRecipeLines(soup, listOf("1 onion"))
        shopping.addByHand("batteries")

        val job = keepCollecting()
        val state = awaitState { it.groups.size == 3 }
        assertEquals(
            listOf(
                "Bread" to listOf("500 g flour", "1 tsp salt"),
                "Soup" to listOf("1 onion"),
                "Other" to listOf("milk", "batteries"),
            ),
            state.texts(),
        )
        assertNull(state.groups.last().recipeId)
        job.cancel()
    }

    @Test
    fun addingTheSameRecipeAgainKeepsOneGroup() = runTest {
        val soup = addRecipe("Soup", 2.0, "1 onion")
        val bread = addRecipe("Bread", 4.0, "1 loaf")
        shopping.addRecipeLines(soup, listOf("1 onion"))
        shopping.addRecipeLines(bread, listOf("1 loaf"))
        shopping.addRecipeLines(soup, listOf("2 carrots"))

        val job = keepCollecting()
        val state = awaitState { it.groups.size == 2 && it.groups.first().items.size == 2 }
        assertEquals(listOf("Soup" to listOf("1 onion", "2 carrots"), "Bread" to listOf("1 loaf")), state.texts())
        job.cancel()
    }

    @Test
    fun itemsAddedByHandGoToOther_andBlankTextIsIgnored() = runTest {
        val job = keepCollecting()
        vm.addItem("  eggs  ")
        vm.addItem("   ")
        val state = awaitState { it.groups.isNotEmpty() }
        assertEquals(listOf("Other" to listOf("eggs")), state.texts())
        assertNull(state.groups.single().recipeId)
        job.cancel()
    }

    @Test
    fun checkingKeepsTheItemInPlace_andClearCheckedRemovesOnlyChecked() = runTest {
        val soup = addRecipe("Soup", 2.0, "1 onion")
        shopping.addRecipeLines(soup, listOf("1 onion", "2 carrots", "3 potatoes"))
        val job = keepCollecting()
        val items = awaitState { it.groups.isNotEmpty() }.groups.single().items

        vm.setChecked(items[0].id, true)
        val checked = awaitState { it.hasChecked }
        // Struck through in place: the order does not change when something is checked.
        assertEquals(listOf("1 onion", "2 carrots", "3 potatoes"), checked.groups.single().items.map { it.text })
        assertEquals(listOf(true, false, false), checked.groups.single().items.map { it.checked })

        vm.setChecked(items[0].id, false)
        assertFalse(awaitState { !it.hasChecked }.hasChecked)

        vm.setChecked(items[1].id, true)
        awaitState { it.hasChecked }
        vm.clearChecked()
        val cleared = awaitState { !it.hasChecked && it.groups.single().items.size == 2 }
        assertEquals(listOf("1 onion", "3 potatoes"), cleared.groups.single().items.map { it.text })
        job.cancel()
    }

    @Test
    fun clearingTheLastItemsEmptiesTheList() = runTest {
        shopping.addByHand("salt")
        val job = keepCollecting()
        val id = awaitState { it.groups.isNotEmpty() }.groups.single().items.single().id
        vm.setChecked(id, true)
        awaitState { it.hasChecked }
        vm.clearChecked()
        assertTrue(awaitState { it.isEmpty }.isEmpty)
        job.cancel()
    }

    @Test
    fun addingFromARecipeUsesQuantitiesScaledToTheServingsShown() = runTest {
        val id = addRecipe("Pancakes", 2.0, "#Batter", "1 kg flour", "1/2 tsp salt", "2 eggs", "butter")
        val detail = detailVm(id)
        val detailJob = scope.launch { detail.state.collect { } }
        withContext(Dispatchers.Default) { withTimeout(10_000) { detail.state.first { it.details != null } } }

        detail.stepServings(2) // 2 -> 4 servings: everything doubles
        val ingredients = detail.state.value.details!!.ingredients
        val everything = ingredients.map { it.id }.toSet() // includes the "Batter" heading id
        var added = -1
        detail.addToShopping(everything) { added = it }
        val job = keepCollecting()
        val state = awaitState { it.groups.isNotEmpty() }

        // The heading is never added; quantities are scaled.
        assertEquals(4, withContext(Dispatchers.Default) { withTimeout(10_000) { while (added < 0) kotlinx.coroutines.yield(); added } })
        assertEquals(listOf("Pancakes" to listOf("2 kg flour", "1 tsp salt", "4 eggs", "butter")), state.texts())
        job.cancel()
        detailJob.cancel()
    }

    @Test
    fun onlySelectedIngredientsAreAdded() = runTest {
        val id = addRecipe("Salad", 1.0, "2 tomatoes", "1 cucumber")
        val detail = detailVm(id)
        val detailJob = scope.launch { detail.state.collect { } }
        withContext(Dispatchers.Default) { withTimeout(10_000) { detail.state.first { it.details != null } } }
        val cucumber = detail.state.value.details!!.ingredients.single { it.text == "1 cucumber" }

        detail.addToShopping(setOf(cucumber.id)) { }
        val job = keepCollecting()
        assertEquals(listOf("Salad" to listOf("1 cucumber")), awaitState { it.groups.isNotEmpty() }.texts())
        job.cancel()
        detailJob.cancel()
    }

    @Test
    fun deletingARecipeMovesItsItemsToOther() = runTest {
        val id = addRecipe("Soup", 2.0, "1 onion")
        shopping.addRecipeLines(id, listOf("1 onion"))
        recipes.delete(id)
        val job = keepCollecting()
        val state = awaitState { it.groups.isNotEmpty() }
        assertEquals(listOf("Other" to listOf("1 onion")), state.texts())
        job.cancel()
    }

    @Test
    fun shareTextListsGroupsAndCheckState() {
        val groups = listOf(
            ShoppingGroup(1, "Soup", listOf(item(1, "1 onion", checked = true), item(2, "2 carrots"))),
            ShoppingGroup(null, null, listOf(item(3, "milk"))),
        )
        assertEquals("Soup\n☑ 1 onion\n☐ 2 carrots\n\nOther\n☐ milk", shareText(groups, "Other"))
    }

    private fun item(id: Long, text: String, checked: Boolean = false) =
        com.postpole.spoonfile.data.ShoppingItemEntity(id = id, text = text, checked = checked, createdAt = 1)
}
