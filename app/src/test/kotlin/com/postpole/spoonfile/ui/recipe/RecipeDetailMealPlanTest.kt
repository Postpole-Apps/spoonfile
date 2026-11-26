package com.postpole.spoonfile.ui.recipe

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.postpole.spoonfile.analytics.AnalyticsEvents
import com.postpole.spoonfile.data.AppDatabase
import com.postpole.spoonfile.data.ImageStore
import com.postpole.spoonfile.data.RecipeEntity
import com.postpole.spoonfile.data.RecipeRepository
import com.postpole.spoonfile.ui.mealplan.weekStartOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RecipeDetailMealPlanTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var recipes: RecipeRepository
    private lateinit var analytics: RecordedAnalytics
    private lateinit var signals: CountingSignals
    private var recipeId = 0L

    private val wednesday = LocalDate.of(2026, 11, 25)
    private val monday = LocalDate.of(2026, 11, 23)

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        scope = CoroutineScope(Dispatchers.Unconfined + Job())
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "t.preferences_pb") }
        recipes = RecipeRepository(db.recipeDao(), ImageStore(File(tmp.root, "images")), Dispatchers.Unconfined)
        analytics = RecordedAnalytics()
        signals = CountingSignals()
        recipeId = recipes.upsertWithChildren(
            RecipeEntity(uid = "u1", title = "Soup", createdAt = 1, updatedAt = 1),
            emptyList(),
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

    private fun newVm(firstDay: DayOfWeek = DayOfWeek.MONDAY, now: LocalDate = wednesday) =
        detailViewModel(recipeId, db, recipes, dataStore, scope, analytics, signals).also {
            it.today = { now }
            it.firstDayOfWeek = firstDay
        }

    private suspend fun makePro() = dataStore.edit { it[booleanPreferencesKey("is_pro")] = true }

    private suspend fun RecipeDetailViewModel.nextEvent() = withContext(Dispatchers.Default) { withTimeout(10_000) { events.first() } }

    @Test
    fun proUser_addsRecipe_writesOneRow_andLogsOnce() = runTest {
        makePro()
        val vm = newVm()
        vm.addToMealPlan(wednesday)

        assertEquals(RecipeDetailEvent.AddedToMealPlan(wednesday), vm.nextEvent())
        val rows = db.mealPlanDao().getAll()
        assertEquals(1, rows.size)
        assertEquals(wednesday.toString(), rows[0].date)
        assertEquals(recipeId, rows[0].recipeId)
        assertEquals(1, analytics.events.count { it.first == AnalyticsEvents.MEAL_PLAN_ADD })
        assertEquals(1, analytics.events.size)
        assertEquals(0, signals.hardGates.get())
    }

    @Test
    fun proUser_duplicateOnSameDay_isSkipped() = runTest {
        makePro()
        val vm = newVm()
        vm.addToMealPlan(wednesday)
        assertEquals(RecipeDetailEvent.AddedToMealPlan(wednesday), vm.nextEvent())
        vm.addToMealPlan(wednesday)

        assertEquals(RecipeDetailEvent.AlreadyInMealPlan, vm.nextEvent())
        assertEquals(1, db.mealPlanDao().getAll().size)
        assertEquals(1, analytics.events.count { it.first == AnalyticsEvents.MEAL_PLAN_ADD })
    }

    @Test
    fun proUser_sameRecipeOnAnotherDay_isAdded() = runTest {
        makePro()
        val vm = newVm()
        vm.addToMealPlan(wednesday)
        vm.nextEvent()
        vm.addToMealPlan(monday)

        assertEquals(RecipeDetailEvent.AddedToMealPlan(monday), vm.nextEvent())
        assertEquals(2, db.mealPlanDao().getAll().size)
    }

    @Test
    fun freeUser_writesNothing_andRequestsOpenPro() = runTest {
        val vm = newVm()
        vm.addToMealPlan(wednesday)

        assertEquals(RecipeDetailEvent.OpenPro, vm.nextEvent())
        assertEquals(0, db.mealPlanDao().getAll().size)
        assertEquals(1, signals.hardGates.get())
        assertEquals(0, analytics.events.count { it.first == AnalyticsEvents.MEAL_PLAN_ADD })
    }

    @Test
    fun revokedPro_isTreatedAsFree() = runTest {
        makePro()
        dataStore.edit { it[booleanPreferencesKey("is_pro")] = false }
        val vm = newVm()
        vm.addToMealPlan(wednesday)

        assertEquals(RecipeDetailEvent.OpenPro, vm.nextEvent())
        assertEquals(0, db.mealPlanDao().getAll().size)
        assertEquals(1, signals.hardGates.get())
    }

    @Test
    fun weekDays_matchWeekStartOf_forMondayAndSundayLocales() {
        // Sunday 2026-11-29 is the last day of a Monday-first week and the first day of a Sunday-first one.
        val sunday = LocalDate.of(2026, 11, 29)
        for ((firstDay, now) in listOf(DayOfWeek.MONDAY to sunday, DayOfWeek.SUNDAY to sunday, DayOfWeek.MONDAY to wednesday, DayOfWeek.SUNDAY to wednesday)) {
            val days = newVm(firstDay, now).weekDays()
            val start = weekStartOf(now, firstDay)
            assertEquals(List(7) { start.plusDays(it.toLong()) }, days)
            assertEquals(firstDay, days.first().dayOfWeek)
        }
        assertEquals(LocalDate.of(2026, 11, 23), newVm(DayOfWeek.MONDAY, sunday).weekDays().first())
        assertEquals(sunday, newVm(DayOfWeek.SUNDAY, sunday).weekDays().first())
    }
}
