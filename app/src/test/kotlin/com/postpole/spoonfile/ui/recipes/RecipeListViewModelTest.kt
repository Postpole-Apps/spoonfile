package com.postpole.spoonfile.ui.recipes

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.postpole.spoonfile.billing.ProStatus
import com.postpole.spoonfile.data.AppDatabase
import com.postpole.spoonfile.data.CategoryEntity
import com.postpole.spoonfile.data.CategoryWithCount
import com.postpole.spoonfile.data.ImageStore
import com.postpole.spoonfile.data.RecipeEntity
import com.postpole.spoonfile.data.RecipeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RecipeListViewModelTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: RecipeRepository
    private lateinit var imageDir: File
    private val isPro = MutableStateFlow(false)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        imageDir = File(context.cacheDir, "images-vm-${System.nanoTime()}").also { it.mkdirs() }
        repo = RecipeRepository(db.recipeDao(), ImageStore(imageDir), Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        db.close()
        imageDir.deleteRecursively()
        Dispatchers.resetMain()
    }

    private fun viewModel() = RecipeListViewModel(
        repo,
        db.categoryDao(),
        object : ProStatus {
            override val isPro: StateFlow<Boolean> = this@RecipeListViewModelTest.isPro
        },
    )

    private suspend fun fill(n: Int) {
        repeat(n) {
            repo.upsertWithChildren(
                RecipeEntity(uid = "u$it", title = "r$it", createdAt = 1, updatedAt = 1),
                emptyList(),
                emptyList(),
                emptyList(),
            )
        }
    }

    @Test
    fun addClick_belowCap_showsSheet() = runTest {
        fill(74)
        val vm = viewModel()
        vm.onAddClicked()
        assertEquals(RecipeListEvent.ShowAddSheet, vm.events.first())
    }

    @Test
    fun addClick_atCap_navigatesToPro() = runTest {
        fill(75)
        val vm = viewModel()
        vm.onAddClicked()
        assertEquals(RecipeListEvent.OpenPro, vm.events.first())
    }

    @Test
    fun addAction_atCap_navigatesToPro_notToTarget() = runTest {
        fill(75)
        val vm = viewModel()
        vm.onAddAction(AddAction.TYPE_IT)
        assertEquals(RecipeListEvent.OpenPro, vm.events.first())
    }

    @Test
    fun addAction_belowCap_opensTarget() = runTest {
        fill(3)
        val vm = viewModel()
        vm.onAddAction(AddAction.PASTE_LINK)
        assertEquals(RecipeListEvent.Open(AddAction.PASTE_LINK), vm.events.first())
    }

    @Test
    fun pro_atCap_stillCanAdd() = runTest {
        fill(75)
        isPro.value = true
        val vm = viewModel()
        vm.onAddClicked()
        assertEquals(RecipeListEvent.ShowAddSheet, vm.events.first())
    }

    @Test
    fun state_counterAndCap() = runTest {
        fill(75)
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }
        val s = vm.state.first { it.total == 75 }
        assertTrue(s.showCounter)
        assertTrue(s.atCap)
        assertFalse(s.canAdd)
    }

    private fun categories(n: Int) = (1..n).map { CategoryWithCount(CategoryEntity(id = it.toLong(), name = "c$it"), 0) }

    @Test
    fun seeAll_moreThanSix_showsFirstSixPlusSeeAll() {
        val tiles = CategoryTiles.of(categories(7))
        assertEquals(6, tiles.visible.size)
        assertTrue(tiles.showSeeAll)
        assertEquals((1L..6L).toList(), tiles.visible.map { it.category.id })
    }

    @Test
    fun seeAll_sixOrFewer_noSeeAll() {
        for (n in 0..6) {
            val tiles = CategoryTiles.of(categories(n))
            assertEquals(n, tiles.visible.size)
            assertFalse("n=$n", tiles.showSeeAll)
        }
    }
}
