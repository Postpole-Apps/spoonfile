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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RemoveAdsAnalyticsTest {
    @get:Rule val tmp = TemporaryFolder()

    private class Gateway : BillingGateway {
        val updatesFlow = MutableSharedFlow<PurchaseUpdate>(extraBufferCapacity = 16)
        override val updates: Flow<PurchaseUpdate> = updatesFlow
        override val disconnects: Flow<Unit> = MutableSharedFlow()
        var owned: List<OwnedPurchase> = emptyList()
        var launchResult = true
        override suspend fun connect() = true
        override suspend fun queryProduct(product: BillingProduct): ProductResult =
            ProductResult.Loaded("p", priceMicros = 99_000_000, currencyCode = "INR")
        override suspend fun queryPurchases() = owned
        override fun launchPurchase(activity: Activity, product: BillingProduct) = launchResult
        override suspend fun acknowledge(token: String) = true
    }

    private class Recorder : Analytics {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        override fun logEvent(name: String, params: Map<String, Any>) { events += name to params }
        override fun setUserProperty(name: String, value: String?) = Unit
        override fun logScreenView(screenName: String) = Unit
        override fun recordNonFatal(throwable: Throwable) = Unit
        fun names() = events.map { it.first }
    }

    private val gateway = Gateway()
    private val analytics = Recorder()
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).get()

    private fun TestScope.repo(): BillingRepository {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val store: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "r.preferences_pb") }
        return BillingRepository(
            gateway, store, scope, log = {}, analytics = analytics,
            proProductId = "pro_id", removeAdsProductId = "remove_ads",
        )
    }

    private val removeAdsBought =
        OwnedPurchase("ra", PurchaseStatus.PURCHASED, acknowledged = false, product = BillingProduct.REMOVE_ADS)

    @Test fun startThenSuccess_logsStartAndSuccessOnceWithParams() = runTest {
        val repo = repo()
        repo.start(); advanceUntilIdle()
        repo.launchPurchase(activity, BillingProduct.REMOVE_ADS)
        gateway.updatesFlow.emit(PurchaseUpdate.Purchases(listOf(removeAdsBought)))
        advanceUntilIdle()
        // A repeated delivery of the same purchase must not double count.
        gateway.updatesFlow.emit(PurchaseUpdate.Purchases(listOf(removeAdsBought)))
        advanceUntilIdle()
        assertEquals(listOf("remove_ads_purchase_start", "remove_ads_purchase_success"), analytics.names())
        val expected = mapOf<String, Any>("product_id" to "remove_ads", "price_micros" to 99_000_000L, "currency" to "INR")
        assertEquals(expected, analytics.events[0].second)
        assertEquals(expected, analytics.events[1].second)
    }

    @Test fun cancel_logsFail() = runTest {
        val repo = repo()
        repo.start(); advanceUntilIdle()
        repo.launchPurchase(activity, BillingProduct.REMOVE_ADS)
        gateway.updatesFlow.emit(PurchaseUpdate.Canceled)
        advanceUntilIdle()
        assertEquals(listOf("remove_ads_purchase_start", "remove_ads_purchase_fail"), analytics.names())
        assertEquals("canceled", analytics.events[1].second["result"])
    }

    @Test fun error_logsFailWithErrorResult() = runTest {
        val repo = repo()
        repo.start(); advanceUntilIdle()
        repo.launchPurchase(activity, BillingProduct.REMOVE_ADS)
        gateway.updatesFlow.emit(PurchaseUpdate.Error(code = 6))
        advanceUntilIdle()
        assertEquals(listOf("remove_ads_purchase_start", "remove_ads_purchase_fail"), analytics.names())
        assertEquals("error", analytics.events[1].second["result"])
    }

    @Test fun alreadyOwned_clearsFlow_soLaterCancelLogsNoFail() = runTest {
        val repo = repo()
        repo.start(); advanceUntilIdle()
        repo.launchPurchase(activity, BillingProduct.REMOVE_ADS)
        gateway.updatesFlow.emit(PurchaseUpdate.AlreadyOwned)
        advanceUntilIdle()
        gateway.updatesFlow.emit(PurchaseUpdate.Canceled)
        advanceUntilIdle()
        assertEquals(listOf("remove_ads_purchase_start"), analytics.names())
    }

    @Test fun pending_clearsFlow_soLaterCancelLogsNoFail() = runTest {
        val repo = repo()
        repo.start(); advanceUntilIdle()
        repo.launchPurchase(activity, BillingProduct.REMOVE_ADS)
        val pending = OwnedPurchase("ra", PurchaseStatus.PENDING, acknowledged = false, product = BillingProduct.REMOVE_ADS)
        gateway.updatesFlow.emit(PurchaseUpdate.Purchases(listOf(pending)))
        advanceUntilIdle()
        gateway.updatesFlow.emit(PurchaseUpdate.Canceled)
        advanceUntilIdle()
        assertEquals(listOf("remove_ads_purchase_start"), analytics.names())
    }

    @Test fun launchFailure_logsFailWithoutStart() = runTest {
        val repo = repo()
        repo.start(); advanceUntilIdle()
        gateway.launchResult = false
        repo.launchPurchase(activity, BillingProduct.REMOVE_ADS)
        assertEquals(listOf("remove_ads_purchase_fail"), analytics.names())
    }

    @Test fun restore_logsNoSuccess() = runTest {
        val repo = repo()
        gateway.owned = listOf(removeAdsBought)
        repo.start(); advanceUntilIdle()
        repo.restore(); advanceUntilIdle()
        assertTrue(analytics.names().none { it.startsWith("remove_ads_") })
    }

    @Test fun proCancel_doesNotLogRemoveAdsFail_andProEventsCarryProductId() = runTest {
        val repo = repo()
        repo.start(); advanceUntilIdle()
        repo.launchPurchase(activity, BillingProduct.PRO)
        gateway.updatesFlow.emit(PurchaseUpdate.Canceled)
        advanceUntilIdle()
        assertEquals(listOf("purchase_started"), analytics.names())
        assertEquals("pro_id", analytics.events[0].second["product_id"])
        gateway.updatesFlow.emit(
            PurchaseUpdate.Purchases(listOf(OwnedPurchase("t", PurchaseStatus.PURCHASED, false))),
        )
        advanceUntilIdle()
        assertEquals("pro_id", analytics.events.single { it.first == "purchase" }.second["product_id"])
    }
}
