package com.postpole.spoonfile.ui.recipes

import android.app.Activity
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.postpole.spoonfile.analytics.Analytics
import com.postpole.spoonfile.analytics.AnalyticsEvents
import com.postpole.spoonfile.analytics.AnalyticsParams
import com.postpole.spoonfile.analytics.TodayPlanTarget
import com.postpole.spoonfile.data.MealPlanEntryEntity
import com.postpole.spoonfile.data.MealPlanRepository
import com.postpole.spoonfile.data.ShoppingRepository
import com.postpole.spoonfile.billing.BillingGateway
import com.postpole.spoonfile.billing.BillingRepository
import com.postpole.spoonfile.billing.OwnedPurchase
import com.postpole.spoonfile.billing.ProductResult
import com.postpole.spoonfile.billing.PurchaseUpdate
import com.postpole.spoonfile.data.AppDatabase
import com.postpole.spoonfile.data.ImageStore
import com.postpole.spoonfile.data.RecipeEntity
import com.postpole.spoonfile.data.RecipeRepository
import com.postpole.spoonfile.data.RecipeSort
import com.postpole.spoonfile.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.delay

private class IdleGateway : BillingGateway {
    override val updates: Flow<PurchaseUpdate> = emptyFlow()
    override val disconnects: Flow<Unit> = emptyFlow()
    override suspend fun connect() = true
    override suspend fun queryProduct(product: com.postpole.spoonfile.billing.BillingProduct): ProductResult = ProductResult.Unavailable
    override suspend fun queryPurchases(): List<OwnedPurchase>? = emptyList()
    override fun launchPurchase(activity: Activity, product: com.postpole.spoonfile.billing.BillingProduct) = true
    override suspend fun acknowledge(token: String) = true
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RecipeListViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var repo: RecipeRepository
    private lateinit var scope: CoroutineScope
    private lateinit var vm: RecipeListViewModel
    private lateinit var dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
    private lateinit var plan: MealPlanRepository
    private val analytics = RecordingAnalytics()
    private val nov26 = LocalDate.of(2026, 11, 26)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = RecipeRepository(db.recipeDao(), ImageStore(File(tmp.root, "images")), Dispatchers.Unconfined)
        scope = CoroutineScope(Dispatchers.Unconfined + Job())
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "t.preferences_pb") }
        plan = MealPlanRepository(db.mealPlanDao(), db.recipeDao(), ShoppingRepository(db.shoppingDao()))
        vm = newVm(BillingRepository(IdleGateway(), dataStore, scope, log = {}))
    }

    private fun newVm(billing: BillingRepository) = RecipeListViewModel(
        repo,
        db.categoryDao(),
        billing,
        SettingsRepository(dataStore),
        mealPlan = plan,
        analytics = analytics,
    ).also { it.today = { nov26 } }

    private class RecordingAnalytics : Analytics {
        val events = CopyOnWriteArrayList<Pair<String, Map<String, Any>>>()
        override fun logEvent(name: String, params: Map<String, Any>) {
            events += name to params
        }
        override fun setUserProperty(name: String, value: String?) = Unit
        override fun logScreenView(screenName: String) = Unit
        override fun recordNonFatal(throwable: Throwable) = Unit
    }

    private suspend fun makePro(pro: Boolean = true) = dataStore.edit { it[booleanPreferencesKey("is_pro")] = pro }

    /** Adds a recipe and returns its id. */
    private suspend fun addRecipe(title: String, uid: String = title): Long = repo.upsertWithChildren(
        RecipeEntity(uid = uid, title = title, createdAt = 1, updatedAt = 1),
        emptyList(), emptyList(), emptyList(),
    )

    private suspend fun plan(date: LocalDate, recipeId: Long, position: Int) {
        db.mealPlanDao().insert(
            MealPlanEntryEntity(date = date.toString(), recipeId = recipeId, position = position, createdAt = 1),
        )
    }

    @After
    fun tearDown() {
        scope.coroutineContext[Job]?.cancel()
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun addRecipes(n: Int, favoriteEvery: Int = 0) {
        repeat(n) {
            repo.upsertWithChildren(
                RecipeEntity(
                    uid = "u$it", title = "r$it", favorite = favoriteEvery > 0 && it % favoriteEvery == 0,
                    createdAt = 1, updatedAt = it.toLong(),
                ),
                emptyList(), emptyList(), emptyList(),
            )
        }
    }

    private suspend fun addCategories(n: Int) = repeat(n) { db.categoryDao().getOrCreate("cat$it") }

    @Test
    fun freeUserAtCap_addRequestAndActionsGoToPro() = runTest {
        addRecipes(75)
        val job = launchCollect { it.loaded && it.count == 75 }
        assertTrue(vm.state.value.atLimit)
        assertFalse(vm.state.value.canAdd)

        vm.onAddRequested()
        assertEquals(RecipeListEvent.OpenPro, vm.events.first())
        vm.onAddAction(AddAction.TYPE_IT_IN)
        assertEquals(RecipeListEvent.OpenPro, vm.events.first())
        job.cancel()
    }

    @Test
    fun belowCap_addOpensSheetAndActionsStart() = runTest {
        addRecipes(74)
        val job = launchCollect { it.loaded && it.count == 74 }
        assertTrue(vm.state.value.canAdd)
        assertTrue(vm.state.value.showCounter)

        vm.onAddRequested()
        assertEquals(RecipeListEvent.ShowAddSheet, vm.events.first())
        vm.onAddAction(AddAction.PASTE_LINK)
        assertEquals(RecipeListEvent.Start(AddAction.PASTE_LINK), vm.events.first())
        job.cancel()
    }

    @Test
    fun moreThanSixCategories_firstSixPlusSeeAll() = runTest {
        addCategories(7)
        val job = launchCollect { it.categories.size == 7 }
        assertEquals(6, vm.state.value.visibleCategories.size)
        assertEquals(7, vm.state.value.categories.size)
        assertTrue(vm.state.value.showSeeAll)
        job.cancel()
    }

    @Test
    fun sixOrFewerCategories_noSeeAll() = runTest {
        addCategories(6)
        val job = launchCollect { it.categories.size == 6 }
        assertEquals(6, vm.state.value.visibleCategories.size)
        assertFalse(vm.state.value.showSeeAll)
        job.cancel()
    }

    @Test
    fun favoritesOnly_filtersList() = runTest {
        addRecipes(4, favoriteEvery = 2)
        val job = launchCollect { it.recipes.size == 4 }
        vm.toggleFavoritesOnly()
        val state = awaitState { it.favoritesOnly && it.recipes.size == 2 }
        assertEquals(setOf("r0", "r2"), state.recipes.map { it.title }.toSet())
        job.cancel()
    }

    @Test
    fun query_withLikeWildcardsMatchesLiterally() = runTest {
        repo.upsertWithChildren(
            RecipeEntity(uid = "a", title = "100% juice", createdAt = 1, updatedAt = 1), emptyList(), emptyList(), emptyList(),
        )
        repo.upsertWithChildren(
            RecipeEntity(uid = "b", title = "Salad", createdAt = 1, updatedAt = 2), emptyList(), emptyList(), emptyList(),
        )
        val job = launchCollect { it.recipes.size == 2 }
        vm.setQuery("%")
        assertEquals(listOf("100% juice"), awaitState { it.query == "%" && it.recipes.size == 1 }.recipes.map { it.title })
        vm.setQuery("_")
        assertEquals(emptyList<String>(), awaitState { it.query == "_" && it.recipes.isEmpty() }.recipes.map { it.title })
        job.cancel()
    }

    @Test
    fun todayPlan_proWithEntriesToday_listsThemInOrder() = runTest {
        makePro()
        val a = addRecipe("A")
        val b = addRecipe("B")
        plan(nov26, a, position = 1)
        plan(nov26, b, position = 0)
        val job = launchCollect { it.isPro && it.todayPlan.size == 2 }
        assertEquals(listOf("B", "A"), vm.state.value.todayPlan.map { it.title })
        assertEquals(listOf(b, a), vm.state.value.todayPlan.map { it.recipeId })
        assertTrue(vm.state.value.showTodayPlan)
        job.cancel()
    }

    @Test
    fun todayPlan_freeUser_isEmptyAndHidden() = runTest {
        plan(nov26, addRecipe("A"), position = 0)
        val job = launchCollect { it.loaded && it.count == 1 }
        assertFalse(vm.state.value.isPro)
        assertTrue(vm.state.value.todayPlan.isEmpty())
        assertFalse(vm.state.value.showTodayPlan)
        job.cancel()
    }

    @Test
    fun todayPlan_entitlementUnknown_isHidden() = runTest {
        // A billing scope that never runs keeps isProOrUnknown at its initial null.
        val idleScope = CoroutineScope(Job().also { it.cancel() })
        vm = newVm(BillingRepository(IdleGateway(), dataStore, idleScope, log = {}))
        plan(nov26, addRecipe("A"), position = 0)
        val job = scope.launch { vm.state.collect { } }
        awaitState { it.loaded && it.count == 1 }
        assertTrue(vm.state.value.todayPlan.isEmpty())
        assertFalse(vm.state.value.showTodayPlan)
        job.cancel()
    }

    @Test
    fun todayPlan_onlyTodaysDate() = runTest {
        makePro()
        val id = addRecipe("A")
        plan(nov26.minusDays(1), id, position = 0)
        plan(nov26, id, position = 0)
        plan(nov26.plusDays(1), id, position = 0)
        val job = launchCollect { it.isPro && it.todayPlan.size == 1 }
        // Give any wrongly included rows time to arrive before asserting.
        assertEquals(1, vm.state.value.todayPlan.size)
        job.cancel()
    }

    @Test
    fun todayPlan_onResumeAfterMidnight_switchesDay() = runTest {
        makePro()
        val a = addRecipe("A")
        val b = addRecipe("B")
        plan(nov26, a, position = 0)
        plan(nov26.plusDays(1), b, position = 0)
        val job = launchCollect { it.isPro && it.todayPlan.map { i -> i.title } == listOf("A") }

        vm.today = { nov26.plusDays(1) }
        vm.onResume()

        assertEquals(listOf("B"), awaitState { it.todayPlan.map { i -> i.title } == listOf("B") }.todayPlan.map { it.title })
        job.cancel()
    }

    @Test
    fun todayPlan_hiddenWhileSearchingOrFiltered() = runTest {
        makePro()
        plan(nov26, addRecipe("A"), position = 0)
        val job = launchCollect { it.isPro && it.todayPlan.size == 1 }
        assertTrue(vm.state.value.showTodayPlan)

        vm.setQuery("zzz")
        assertFalse(awaitState { it.query == "zzz" }.showTodayPlan)
        vm.setQuery("")
        assertTrue(awaitState { it.query.isEmpty() }.showTodayPlan)

        val category = db.categoryDao().getOrCreate("cat")
        vm.selectCategory(category)
        assertFalse(awaitState { it.selectedCategoryId == category }.showTodayPlan)
        vm.selectCategory(category)
        assertTrue(awaitState { it.selectedCategoryId == null }.showTodayPlan)

        vm.toggleFavoritesOnly()
        assertFalse(awaitState { it.favoritesOnly }.showTodayPlan)
        job.cancel()
    }

    @Test
    fun todayPlan_recipeDeleted_entryDisappears() = runTest {
        makePro()
        val id = addRecipe("A")
        plan(nov26, id, position = 0)
        val job = launchCollect { it.isPro && it.todayPlan.size == 1 }

        repo.delete(id)

        assertTrue(awaitState { it.todayPlan.isEmpty() }.todayPlan.isEmpty())
        assertFalse(vm.state.value.showTodayPlan)
        job.cancel()
    }

    @Test
    fun onTodayPlanTap_logsTarget() = runTest {
        vm.onTodayPlanTap(TodayPlanTarget.COOK)
        withContext(Dispatchers.Default) {
            withTimeout(10_000) { while (analytics.events.isEmpty()) delay(20) }
        }
        assertEquals(
            listOf(AnalyticsEvents.TODAY_PLAN_TAP to mapOf<String, Any>(AnalyticsParams.TARGET to "cook")),
            analytics.events.toList(),
        )
    }

    /** Room emits on its own threads, so wait (in real time) for the state to settle. */
    private suspend fun awaitState(predicate: (RecipeListUiState) -> Boolean): RecipeListUiState =
        withContext(Dispatchers.Default) { withTimeout(10_000) { vm.state.first(predicate) } }

    /** The state is `WhileSubscribed`, so keep a collector alive, then wait until [ready]. */
    private suspend fun launchCollect(ready: (RecipeListUiState) -> Boolean): Job {
        val job = scope.launch { vm.state.collect { } }
        awaitState(ready)
        return job
    }
}
