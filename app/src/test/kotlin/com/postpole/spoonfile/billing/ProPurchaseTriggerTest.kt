package com.postpole.spoonfile.billing

import android.app.Activity
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.postpole.spoonfile.analytics.Analytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.io.File

/** The automatic-prompt `trigger` rides on `purchase_started` and `purchase`; manual entries carry none. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProPurchaseTriggerTest {
    @get:Rule val tmp = TemporaryFolder()

    private class Gateway : BillingGateway {
        val updatesFlow = MutableSharedFlow<PurchaseUpdate>(extraBufferCapacity = 16)
        override val updates: Flow<PurchaseUpdate> = updatesFlow
        override val disconnects: Flow<Unit> = MutableSharedFlow()
        override suspend fun connect() = true
        override suspend fun queryProduct(product: BillingProduct): ProductResult =
            ProductResult.Loaded("p", priceMicros = 99_000_000, currencyCode = "INR")
        override suspend fun queryPurchases(): List<OwnedPurchase> = emptyList()
        override fun launchPurchase(activity: Activity, product: BillingProduct) = true
        override suspend fun acknowledge(token: String) = true
    }

    private class Recorder : Analytics {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        override fun logEvent(name: String, params: Map<String, Any>) { events += name to params }
        override fun setUserProperty(name: String, value: String?) = Unit
        override fun logScreenView(screenName: String) = Unit
        override fun recordNonFatal(throwable: Throwable) = Unit
    }

    private val gateway = Gateway()
    private val analytics = Recorder()
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).get()
    private val bought = OwnedPurchase("tok", PurchaseStatus.PURCHASED, acknowledged = true, product = BillingProduct.PRO)

    private fun TestScope.repo(): BillingRepository {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val store: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "t.preferences_pb") }
        return BillingRepository(gateway, store, scope, log = {}, analytics = analytics)
    }

    @Test fun autoTrigger_isOnPurchaseStartedAndPurchase() = runTest {
        val repo = repo()
        repo.start(); advanceUntilIdle()
        repo.launchPurchase(activity, trigger = "auto_recipes_milestone")
        gateway.updatesFlow.emit(PurchaseUpdate.Purchases(listOf(bought)))
        advanceUntilIdle()
        assertEquals("auto_recipes_milestone", analytics.events.single { it.first == "purchase_started" }.second["trigger"])
        assertEquals("auto_recipes_milestone", analytics.events.single { it.first == "purchase" }.second["trigger"])
    }

    @Test fun manualPurchase_carriesNoTrigger() = runTest {
        val repo = repo()
        repo.start(); advanceUntilIdle()
        repo.launchPurchase(activity)
        gateway.updatesFlow.emit(PurchaseUpdate.Purchases(listOf(bought)))
        advanceUntilIdle()
        assertFalse(analytics.events.single { it.first == "purchase_started" }.second.containsKey("trigger"))
        assertFalse(analytics.events.single { it.first == "purchase" }.second.containsKey("trigger"))
    }
}
