package com.postpole.spoonfile.ui.mealplan

import android.app.Activity
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.postpole.spoonfile.analytics.Analytics
import com.postpole.spoonfile.analytics.AnalyticsEvents
import com.postpole.spoonfile.billing.BillingGateway
import com.postpole.spoonfile.billing.BillingProduct
import com.postpole.spoonfile.billing.BillingRepository
import com.postpole.spoonfile.billing.OwnedPurchase
import com.postpole.spoonfile.billing.ProductResult
import com.postpole.spoonfile.billing.PurchaseUpdate
import com.postpole.spoonfile.data.AppDatabase
import com.postpole.spoonfile.data.IngredientEntity
import com.postpole.spoonfile.data.ImageStore
import com.postpole.spoonfile.data.MealPlanRepository
import com.postpole.spoonfile.data.RecipeEntity
import com.postpole.spoonfile.data.RecipeRepository
import com.postpole.spoonfile.data.ShoppingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate

private class IdleBillingGateway : BillingGateway {
    override val updates: Flow<PurchaseUpdate> = emptyFlow()
    override val disconnects: Flow<Unit> = emptyFlow()
    override suspend fun connect() = true
    override suspend fun queryProduct(product: BillingProduct): ProductResult = ProductResult.Unavailable
    override suspend fun queryPurchases(): List<OwnedPurchase>? = emptyList()
    override fun launchPurchase(activity: Activity, product: BillingProduct) = true
    override suspend fun acknowledge(token: String) = true
}

private class RecordingAnalytics : Analytics {
    val events = mutableListOf<Pair<String, Map<String, Any>>>()
    override fun logEvent(name: String, params: Map<String, Any>) {
        events += name to params
    }
    override fun setUserProperty(name: String, value: String?) = Unit
    override fun logScreenView(screenName: String) = Unit
    override fun recordNonFatal(throwable: Throwable) = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MealPlanViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var recipes: RecipeRepository
    private lateinit var analytics: RecordingAnalytics
    private lateinit var vm: MealPlanViewModel
    private var recipeId = 0L

    private val monday = LocalDate.of(2026, 11, 23)

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        scope = CoroutineScope(Dispatchers.Unconfined + Job())
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "t.preferences_pb") }
        recipes = RecipeRepository(db.recipeDao(), ImageStore(File(tmp.root, "images")), Dispatchers.Unconfined)
        analytics = RecordingAnalytics()
        recipeId = recipes.upsertWithChildren(
            RecipeEntity(uid = "u1", title = "Soup", createdAt = 1, updatedAt = 1),
            listOf(IngredientEntity(position = 0, text = "2 tomatoes")),
            emptyList(),
            emptyList(),
        )
    }

    @After
    fun tearDown() {
        scope.coroutineContext[Job]?.cancel()
        db.close()
        Dispatchers.resetMain()
    }

    private fun newVm(): MealPlanViewModel {
        val billing = BillingRepository(IdleBillingGateway(), dataStore, scope, log = {})
        val shopping = ShoppingRepository(db.shoppingDao())
        val plan = MealPlanRepository(db.mealPlanDao(), db.recipeDao(), shopping)
        return MealPlanViewModel(plan, recipes, billing, analytics).also {
            it.today = { monday.plusDays(2) } // a Wednesday
            it.firstDayOfWeek = DayOfWeek.MONDAY
            it.thisWeek()
        }.also { vm = it }
    }

    private suspend fun makePro() = dataStore.edit { it[booleanPreferencesKey("is_pro")] = true }

    private suspend fun awaitState(predicate: (MealPlanUiState) -> Boolean): MealPlanUiState =
        withContext(Dispatchers.Default) { withTimeout(10_000) { vm.state.first(predicate) } }

    @Test
    fun freeUser_cannotWriteAnything() = runTest {
        newVm()
        awaitState { it.entitlementKnown }
        assertFalse(vm.state.value.isPro)

        vm.openPicker(monday)
        vm.addRecipe(monday, recipeId)
        vm.remove(1)
        vm.addEntryToShopping(recipeId)

        assertEquals(0, db.mealPlanDao().getAll().size)
        assertEquals(0, db.shoppingDao().getAll().size)
        assertEquals(null, vm.state.value.pickerDate)
        assertEquals(MealPlanEvent.OpenPro, vm.events.first())
        assertTrue(analytics.events.none { it.first == AnalyticsEvents.MEAL_PLAN_ADD || it.first == AnalyticsEvents.MEAL_PLAN_VIEW })
    }

    @Test
    fun freeUser_isSentToPro_andSeesNoPlan() = runTest {
        // A row that exists (for example from a restored backup) is not shown to a free user.
        db.mealPlanDao().insert(com.postpole.spoonfile.data.MealPlanEntryEntity(date = "2026-11-25", recipeId = recipeId, createdAt = 1))
        newVm()
        val state = awaitState { it.entitlementKnown }
        assertEquals(MealPlanEvent.OpenPro, vm.events.first())
        assertTrue(state.days.all { it.entries.isEmpty() })
    }

    @Test
    fun proUser_plansAWeekAndTodayIsMarked() = runTest {
        makePro()
        newVm()
        awaitState { it.isPro }

        vm.addRecipe(monday.plusDays(2), recipeId)
        vm.addRecipe(monday.plusDays(2), recipeId)
        val state = awaitState { s -> s.days.sumOf { it.entries.size } == 2 }

        assertEquals(7, state.days.size)
        assertEquals(monday, state.weekStart)
        assertEquals(listOf(monday.plusDays(2)), state.days.filter { it.isToday }.map { it.date })
        assertEquals(2, state.days[2].entries.size)
        assertEquals(1, analytics.events.count { it.first == AnalyticsEvents.MEAL_PLAN_VIEW })
        assertEquals(2, analytics.events.count { it.first == AnalyticsEvents.MEAL_PLAN_ADD })
        assertTrue(analytics.events.all { (_, params) -> params.keys.all { it == "item_count" } })
    }

    @Test
    fun proUser_picksARecipeForTheOpenDay_andNavigatesWeeks() = runTest {
        makePro()
        newVm()
        awaitState { it.isPro }

        vm.openPicker(monday)
        awaitState { it.pickerDate == monday && it.pickerRecipes.size == 1 }
        vm.setPickerQuery("tomato") // matches ingredient text
        awaitState { it.pickerRecipes.size == 1 }
        vm.setPickerQuery("zzz")
        awaitState { it.pickerRecipes.isEmpty() }
        vm.setPickerQuery("")
        awaitState { it.pickerRecipes.size == 1 }
        vm.addRecipe(recipeId)
        awaitState { it.pickerDate == null && it.days[0].entries.size == 1 }

        vm.nextWeek()
        val next = awaitState { it.weekStart == monday.plusWeeks(1) }
        assertTrue(next.days.all { it.entries.isEmpty() })
        vm.previousWeek()
        assertEquals(1, awaitState { it.weekStart == monday && it.days[0].entries.size == 1 }.days[0].entries.size)
    }

    @Test
    fun proUser_addsADayToTheShoppingList_andEmptyDayIsANoOp() = runTest {
        makePro()
        newVm()
        awaitState { it.isPro }
        vm.addRecipe(monday, recipeId)
        val state = awaitState { it.days[0].entries.size == 1 }

        vm.addDayToShopping(state.days[1]) // empty day: nothing happens
        assertEquals(0, db.shoppingDao().getAll().size)

        vm.addDayToShopping(state.days[0])
        assertEquals(MealPlanEvent.AddedToShopping(1), vm.events.first())
        assertEquals(listOf("2 tomatoes"), db.shoppingDao().getAll().map { it.text })
        assertEquals(1, analytics.events.count { it.first == AnalyticsEvents.MEAL_PLAN_SHOP })
    }

    private suspend fun planEntry(date: LocalDate, id: Long) =
        db.mealPlanDao().insert(com.postpole.spoonfile.data.MealPlanEntryEntity(date = date.toString(), recipeId = id, createdAt = 1))

    @Test
    fun repeatLastWeek_copiesOntoMatchingDays_skipsDuplicates_andKeepsThisWeek() = runTest {
        makePro()
        val other = recipes.upsertWithChildren(RecipeEntity(uid = "u2", title = "Stew", createdAt = 1, updatedAt = 1), emptyList(), emptyList(), emptyList())
        val lastMonday = monday.minusWeeks(1)
        planEntry(lastMonday, recipeId)
        planEntry(lastMonday, other)
        planEntry(lastMonday.plusDays(3), recipeId)
        planEntry(monday, recipeId) // already planned this week: must be skipped, not duplicated
        newVm()
        awaitState { it.isPro }

        vm.repeatLastWeek()

        assertEquals(MealPlanEvent.Repeated(2), vm.events.first())
        val state = awaitState { s -> s.days.sumOf { it.entries.size } == 3 }
        assertEquals(listOf(recipeId, other), state.days[0].entries.map { it.entry.recipeId })
        assertEquals(listOf(recipeId), state.days[3].entries.map { it.entry.recipeId })
        val adds = analytics.events.filter { it.first == AnalyticsEvents.MEAL_PLAN_ADD }
        assertEquals(1, adds.size)
        assertEquals(2, adds.single().second["item_count"])
    }

    @Test
    fun repeatLastWeek_emptyLastWeek_writesNothing() = runTest {
        makePro()
        newVm()
        awaitState { it.isPro }

        vm.repeatLastWeek()

        assertEquals(MealPlanEvent.RepeatEmpty, vm.events.first())
        assertEquals(0, db.mealPlanDao().getAll().size)
        assertTrue(analytics.events.none { it.first == AnalyticsEvents.MEAL_PLAN_ADD })
    }

    @Test
    fun repeatLastWeek_deletedRecipesAreOmitted() = runTest {
        makePro()
        val gone = recipes.upsertWithChildren(RecipeEntity(uid = "u3", title = "Gone", createdAt = 1, updatedAt = 1), emptyList(), emptyList(), emptyList())
        planEntry(monday.minusDays(7), gone)
        planEntry(monday.minusDays(7), recipeId)
        db.recipeDao().delete(gone) // ON DELETE CASCADE removes the plan entry
        newVm()
        awaitState { it.isPro }

        vm.repeatLastWeek()

        assertEquals(MealPlanEvent.Repeated(1), vm.events.first())
        assertEquals(listOf(recipeId), db.mealPlanDao().getAll().filter { it.date == monday.toString() }.map { it.recipeId })
    }

    @Test
    fun repeatLastWeek_andShare_requirePro() = runTest {
        planEntry(monday.minusDays(7), recipeId)
        newVm()
        awaitState { it.entitlementKnown }

        vm.repeatLastWeek()
        vm.shareWeek()

        assertEquals(1, db.mealPlanDao().getAll().size)
        assertEquals(MealPlanEvent.OpenPro, vm.events.first())
        assertTrue(analytics.events.none { it.first == AnalyticsEvents.MEAL_PLAN_ADD })
    }

    @Test
    fun shareWeek_asPro_sendsShareEvent() = runTest {
        makePro()
        newVm()
        awaitState { it.isPro }
        vm.shareWeek()
        assertEquals(MealPlanEvent.ShareWeek, vm.events.first())
    }

    @Test
    fun shareText_emptyWeek_isHeadingAndEmptyLine() {
        val text = formatMealPlanShare("Plan", listOf("Monday" to emptyList(), "Tuesday" to emptyList()), "Nothing") { d, t -> "$d: $t" }
        assertEquals("Plan\nNothing", text)
    }

    @Test
    fun shareText_oneRecipePerDay_omitsEmptyDays() {
        val days = listOf("Monday" to listOf("Soup"), "Tuesday" to emptyList(), "Wednesday" to listOf("Stew"))
        assertEquals("Plan\nMonday: Soup\nWednesday: Stew", formatMealPlanShare("Plan", days, "Nothing") { d, t -> "$d: $t" })
    }

    @Test
    fun shareText_severalRecipesOnOneDay_areJoinedInOrder() {
        val days = listOf("Monday" to listOf("Soup", "Stew", "Pie"))
        assertEquals("Plan\nMonday: Soup, Stew, Pie", formatMealPlanShare("Plan", days, "Nothing") { d, t -> "$d: $t" })
    }

    @Test
    fun weekStart_followsTheFirstDayOfTheWeek() {
        val wednesday = LocalDate.of(2026, 11, 25)
        assertEquals(LocalDate.of(2026, 11, 23), weekStartOf(wednesday, DayOfWeek.MONDAY))
        assertEquals(LocalDate.of(2026, 11, 22), weekStartOf(wednesday, DayOfWeek.SUNDAY))
        assertEquals(LocalDate.of(2026, 11, 21), weekStartOf(wednesday, DayOfWeek.SATURDAY))
        assertEquals(wednesday, weekStartOf(wednesday, DayOfWeek.WEDNESDAY))
    }
}
