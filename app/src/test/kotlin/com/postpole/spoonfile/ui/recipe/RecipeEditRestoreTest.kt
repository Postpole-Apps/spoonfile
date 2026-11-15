package com.postpole.spoonfile.ui.recipe

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
import com.postpole.spoonfile.review.ReviewPrompter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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

private class NoopGateway : BillingGateway {
    override val updates: Flow<PurchaseUpdate> = emptyFlow()
    override val disconnects: Flow<Unit> = emptyFlow()
    override suspend fun connect() = true
    override suspend fun queryProduct(): ProductResult = ProductResult.Unavailable
    override suspend fun queryPurchases(): List<OwnedPurchase>? = emptyList()
    override fun launchPurchase(activity: Activity) = true
    override suspend fun acknowledge(token: String) = true
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RecipeEditRestoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var repo: RecipeRepository
    private lateinit var scope: CoroutineScope
    private lateinit var billing: BillingRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = RecipeRepository(db.recipeDao(), ImageStore(File(tmp.root, "images")), Dispatchers.Unconfined)
        scope = CoroutineScope(Dispatchers.Unconfined + Job())
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "t.preferences_pb") }
        billing = BillingRepository(NoopGateway(), dataStore, scope, log = {})
    }

    @After
    fun tearDown() {
        scope.coroutineContext[Job]?.cancel()
        db.close()
        Dispatchers.resetMain()
    }

    private fun restoredHandle(id: Long, title: String, image: String?) = SavedStateHandle(mapOf("id" to id)).apply {
        set(
            "edit_draft",
            Bundle().apply {
                putString("title", title)
                putString("image", image)
            },
        )
    }

    private suspend fun seed(): Long {
        val dir = File(tmp.root, "images").also { it.mkdirs() }
        val file = File(dir, "photo.jpg").also { it.writeText("x") }
        return repo.upsertWithChildren(
            RecipeEntity(uid = "orig", title = "Original", imageFile = file.name, createdAt = 5, updatedAt = 5),
            emptyList(), emptyList(), emptyList(),
        )
    }

    private fun newVm(handle: SavedStateHandle) =
        RecipeEditViewModel(handle, repo, db.categoryDao(), billing, object : ReviewPrompter {
            override suspend fun recordPositiveAction() = false
        })

    @Test
    fun restoredDraftOfExistingRecipeUpdatesSameRow() = runBlocking {
        val id = seed()
        val vm = newVm(restoredHandle(id, "Edited", "photo.jpg"))
        withTimeout(5_000) { vm.form.first { it.loaded } }
        assertEquals("Edited", vm.form.value.title)
        vm.save()
        withTimeout(5_000) { vm.events.first() }
        val rows = db.recipeDao().observeList("", null, false, RecipeSort.RECENT).first()
        assertEquals(1, rows.size)
        assertEquals("orig", rows[0].uid)
        assertEquals(5L, rows[0].createdAt)
        assertEquals("Edited", rows[0].title)
    }

    @Test
    fun restoredDraftDiscardKeepsSavedPhoto() = runBlocking {
        val id = seed()
        val vm = newVm(restoredHandle(id, "Edited", "photo.jpg"))
        withTimeout(5_000) { vm.form.first { it.loaded } }
        vm.discard {}
        assertTrue(File(tmp.root, "images/photo.jpg").exists())
    }

    @Test
    fun missingRowKeepsNotLoadedGuardsOff() = runBlocking {
        // Existing id that is not in the database: form still becomes loaded, never crashes.
        val vm = newVm(restoredHandle(999L, "Ghost", null))
        withTimeout(5_000) { vm.form.first { it.loaded } }
        assertFalse(vm.form.value.saving)
    }
}
