package com.postpole.spoonfile.ui.mealplan

import android.app.Activity
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.postpole.spoonfile.analytics.Analytics
import com.postpole.spoonfile.analytics.AnalyticsEvents
import com.postpole.spoonfile.analytics.AnalyticsParams
import com.postpole.spoonfile.billing.BillingGateway
import com.postpole.spoonfile.billing.BillingRepository
import com.postpole.spoonfile.billing.OwnedPurchase
import com.postpole.spoonfile.billing.ProductResult
import com.postpole.spoonfile.billing.PurchaseUpdate
import com.postpole.spoonfile.data.AppDatabase
import com.postpole.spoonfile.data.ImageStore
import com.postpole.spoonfile.data.IngredientEntity
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

private class IdleBilling : BillingGateway {
    override val updates: Flow<PurchaseUpdate> = emptyFlow()
    override val disconnects: Flow<Unit> = emptyFlow()
    override suspend fun connect() = true
    override suspend fun queryProduct(product: com.postpole.spoonfile.billing.BillingProduct): ProductResult = ProductResult.Unavailable
    override suspend fun queryPurchases(): List<OwnedPurchase>? = emptyList()
    override fun launchPurchase(activity: Activity, product: com.postpole.spoonfile.billing.BillingProduct) = true
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
    private lateinit var dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
    private lateinit var analytics: RecordingAnalytics
    private lateinit var vm: MealPlanViewModel
    private lateinit var plan: MealPlanRepository

    private val today = LocalDate.of(2026, 11, 18) // a Wednesday

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        scope = CoroutineScope(Dispatchers.Unconfined + Job())
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "t.preferences_pb") }
        analytics = RecordingAnalytics()
        val billing = BillingRepository(IdleBilling(), dataStore, scope, log = {})
        plan = MealPlanRepository(db.mealPlanDao())
        val recipes = RecipeRepository(db.recipeDao(), ImageStore(File(tmp.root, "images")), Dispatchers.Unconfined)
        vm = MealPlanViewModel(plan, recipes, ShoppingRepository(db.shoppingDao()), billing, analytics)
        vm.firstDayOfWeek = DayOfWeek.MONDAY
        vm.todayProvider = { today }
        vm.thisWeek()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        scope.coroutineContext[Job]?.cancel()
        db.close()
    }

    private suspend fun setPro(pro: Boolean) {
        dataStore.edit { it[booleanPreferencesKey("is_pro")] = pro }
    }

    private suspend fun recipe(title: String, vararg ingredients: String) = db.recipeDao().upsertWithChildren(
        RecipeEntity(uid = title, title = title, createdAt = 1, updatedAt = 1),
        ingredients.mapIndexed { i, t -> IngredientEntity(position = i, text = t) },
        emptyList(),
        emptyList(),
    )

    /** Subscribes (the state is WhileSubscribed) and waits for [predicate]. */
    private suspend fun awaitState(predicate: (MealPlanUiState) -> Boolean): MealPlanUiState =
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { vm.state.first(predicate) }
        }

    @Test
    fun weekStartFollowsTheFirstDayOfTheWeek() {
        assertEquals(LocalDate.of(2026, 11, 16), weekStartOf(today, DayOfWeek.MONDAY))
        assertEquals(LocalDate.of(2026, 11, 15), weekStartOf(today, DayOfWeek.SUNDAY))
        assertEquals(LocalDate.of(2026, 11, 14), weekStartOf(today, DayOfWeek.SATURDAY))
        assertEquals(today, weekStartOf(today, DayOfWeek.WEDNESDAY))
    }

    @Test
    fun freeUser_cannotWrite_andNothingIsPersisted() = runTest {
        val r = recipe("Soup", "2 tomatoes")
        setPro(false)
        awaitState { it.access == MealPlanAccess.Locked }

        assertFalse(vm.addRecipe(today, r))
        assertFalse(vm.remove(1))
        assertFalse(vm.addRecipeToShopping(r))
        assertFalse(vm.addDayToShopping(today))

        assertEquals(0, db.mealPlanDao().countNow())
        assertEquals(0, db.shoppingDao().getAll().size)
        assertTrue(analytics.events.isEmpty())
    }

    @Test
    fun initialState_isLoading_notLocked() {
        // Until DataStore has been read the screen neither redirects to Pro nor shows the plan.
        assertEquals(MealPlanAccess.Loading, MealPlanUiState().access)
    }

    @Test
    fun proUser_plansRecipes_andSeesTodayMarkedInTheWeek() = runTest {
        val soup = recipe("Soup", "2 tomatoes")
        setPro(true)
        awaitState { it.access == MealPlanAccess.Pro }

        assertTrue(vm.addRecipe(today, soup))
        val state = awaitState { s -> s.days.any { it.entries.isNotEmpty() } }

        assertEquals(7, state.days.size)
        assertEquals(LocalDate.of(2026, 11, 16), state.days.first().date)
        assertEquals(listOf(today), state.days.filter { it.isToday }.map { it.date })
        assertEquals(listOf("Soup"), state.days.single { it.date == today }.entries.map { it.title })
        assertTrue(analytics.events.any { it.first == AnalyticsEvents.MEAL_PLAN_ADD && it.second.isEmpty() })

        // Adjacent weeks are separate.
        vm.nextWeek()
        assertTrue(awaitState { it.weekStart == today.plusWeeks(1).let { d -> weekStartOf(d, DayOfWeek.MONDAY) } }.days.all { it.entries.isEmpty() })
        vm.thisWeek()
        val id = awaitState { s -> s.days.any { it.entries.isNotEmpty() } }.days.flatMap { it.entries }.single().id
        assertTrue(vm.remove(id))
        awaitState { s -> s.days.all { it.entries.isEmpty() } }
    }

    @Test
    fun proUser_addsADaysIngredients_atDefaultServings_andLogsOnlyACount() = runTest {
        val soup = recipe("Soup", "2 tomatoes", "1 onion")
        val salad = recipe("Salad", "lettuce")
        setPro(true)
        awaitState { it.access == MealPlanAccess.Pro }
        vm.addRecipe(today, soup)
        vm.addRecipe(today, salad)

        assertTrue(vm.addDayToShopping(today))
        withContext(Dispatchers.Default) { withTimeout(5_000) { while (db.shoppingDao().getAll().size < 3) kotlinx.coroutines.delay(20) } }

        assertEquals(listOf(soup, soup, salad), db.shoppingDao().getAll().map { it.recipeId })
        val shop = analytics.events.single { it.first == AnalyticsEvents.MEAL_PLAN_SHOP }
        assertEquals(mapOf<String, Any>(AnalyticsParams.ITEM_COUNT to 3), shop.second)

        // An empty day is a harmless no-op.
        assertTrue(vm.addDayToShopping(today.plusDays(1)))
        assertEquals(3, db.shoppingDao().getAll().size)
    }

    @Test
    fun viewEvent_isLoggedOncePerVisit_withOnlyACount() {
        vm.onViewed(4)
        vm.onViewed(5)
        val views = analytics.events.filter { it.first == AnalyticsEvents.MEAL_PLAN_VIEW }
        assertEquals(1, views.size)
        assertEquals(mapOf<String, Any>(AnalyticsParams.ITEM_COUNT to 4), views.single().second)
    }
}
