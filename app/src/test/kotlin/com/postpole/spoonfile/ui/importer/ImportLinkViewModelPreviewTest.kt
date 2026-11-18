package com.postpole.spoonfile.ui.importer

import android.app.Activity
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.postpole.spoonfile.analytics.NoOpAnalytics
import com.postpole.spoonfile.billing.BillingGateway
import com.postpole.spoonfile.billing.BillingRepository
import com.postpole.spoonfile.billing.OwnedPurchase
import com.postpole.spoonfile.billing.ProductResult
import com.postpole.spoonfile.billing.PurchaseUpdate
import com.postpole.spoonfile.data.AppDatabase
import com.postpole.spoonfile.data.ImageStore
import com.postpole.spoonfile.data.RecipeOrigin
import com.postpole.spoonfile.data.RecipeRepository
import com.postpole.spoonfile.importer.web.WebPageFetcher
import com.postpole.spoonfile.ui.recipe.RecipeEditForm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

private class PreviewNoopGateway : BillingGateway {
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
class ImportLinkViewModelPreviewTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var vm: ImportLinkViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        scope = CoroutineScope(Dispatchers.Unconfined + Job())
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "t.preferences_pb") }
        vm = ImportLinkViewModel(
            SavedStateHandle(),
            RecipeRepository(db.recipeDao(), ImageStore(File(tmp.root, "images")), Dispatchers.Unconfined),
            db.categoryDao(),
            BillingRepository(PreviewNoopGateway(), dataStore, scope, log = {}),
            WebPageFetcher(),
            NoOpAnalytics,
        )
    }

    @After
    fun tearDown() {
        scope.coroutineContext[Job]?.cancel()
        db.close()
        Dispatchers.resetMain()
    }

    @Test fun closePreviewLeavesThePreviewState() {
        vm.startFromCleanup(RecipeEditForm(title = "Soup"), null, RecipeOrigin.OCR)
        assertTrue(vm.state.value is ImportState.Preview)
        vm.closePreview()
        assertFalse(vm.state.value is ImportState.Preview)
    }

    @Test fun discardAloneKeepsTheStateForTheLinkFlowToLeave() {
        vm.startFromCleanup(RecipeEditForm(title = "Soup"), null, RecipeOrigin.OCR)
        vm.discard()
        assertTrue(vm.state.value is ImportState.Preview)
    }
}
