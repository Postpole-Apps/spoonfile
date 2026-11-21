package com.postpole.spoonfile.billing

import android.app.Activity
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

private class FakeGateway : BillingGateway {
    val updatesFlow = MutableSharedFlow<PurchaseUpdate>(extraBufferCapacity = 16)
    override val updates: Flow<PurchaseUpdate> = updatesFlow
    val disconnectsFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val disconnects: Flow<Unit> = disconnectsFlow

    var connectFailures = 0
    var connectCalls = 0
    var queryPurchasesCalls = 0
    var product: ProductResult = ProductResult.Loaded("$4.99")
    var owned: List<OwnedPurchase>? = emptyList()
    var acknowledgeFailures = 0
    val acknowledged = mutableListOf<String>()

    override suspend fun connect(): Boolean {
        connectCalls++
        if (connectFailures > 0) {
            connectFailures--
            return false
        }
        return true
    }

    var removeAdsProduct: ProductResult = ProductResult.Loaded("RA-PRICE")
    val queriedProducts = mutableListOf<BillingProduct>()
    val launchedProducts = mutableListOf<BillingProduct>()

    override suspend fun queryProduct(product: BillingProduct): ProductResult {
        queriedProducts += product
        return if (product == BillingProduct.PRO) this.product else removeAdsProduct
    }
    override suspend fun queryPurchases(): List<OwnedPurchase>? {
        queryPurchasesCalls++
        return owned
    }
    override fun launchPurchase(activity: Activity, product: BillingProduct): Boolean {
        launchedProducts += product
        return launchResult
    }
    var launchResult = true
    override suspend fun acknowledge(token: String): Boolean {
        acknowledged += token
        if (acknowledgeFailures > 0) {
            acknowledgeFailures--
            return false
        }
        return true
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BillingRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val gateway = FakeGateway()
    private lateinit var dataStore: DataStore<Preferences>

    private fun TestScope.repo(): BillingRepository {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "t.preferences_pb") }
        return BillingRepository(
            gateway, dataStore, scope,
            maxConnectAttempts = 3, initialBackoffMs = 10,
            maxAcknowledgeAttempts = 3, acknowledgeBackoffMs = 10,
            log = {},
        )
    }

    private val purchased = OwnedPurchase("tok", PurchaseStatus.PURCHASED, acknowledged = false)

    @Test fun purchased_grantsProAndAcknowledges() = runTest {
        val repo = repo()
        gateway.owned = listOf(purchased)
        repo.start()
        advanceUntilIdle()
        assertTrue(repo.isPro.value)
        assertEquals(listOf("tok"), gateway.acknowledged)
        assertEquals("$4.99", repo.price.value)
    }

    @Test fun alreadyAcknowledged_isNotAcknowledgedAgain() = runTest {
        val repo = repo()
        gateway.owned = listOf(purchased.copy(acknowledged = true))
        repo.start()
        advanceUntilIdle()
        assertTrue(repo.isPro.value)
        assertTrue(gateway.acknowledged.isEmpty())
    }

    @Test fun pending_doesNotGrantPro() = runTest {
        val repo = repo()
        repo.start()
        advanceUntilIdle()
        gateway.updatesFlow.emit(
            PurchaseUpdate.Purchases(listOf(OwnedPurchase("tok", PurchaseStatus.PENDING, false))),
        )
        advanceUntilIdle()
        assertFalse(repo.isPro.value)
        assertTrue(repo.pending.value)
        assertTrue(gateway.acknowledged.isEmpty())
    }

    @Test fun pending_thenPurchased_grantsPro() = runTest {
        val repo = repo()
        gateway.owned = listOf(OwnedPurchase("tok", PurchaseStatus.PENDING, false))
        repo.start()
        advanceUntilIdle()
        assertFalse(repo.isPro.value)
        gateway.owned = listOf(purchased)
        repo.restore()
        advanceUntilIdle()
        assertTrue(repo.isPro.value)
        assertFalse(repo.pending.value)
    }

    @Test fun itemAlreadyOwned_restoresFromPlay() = runTest {
        val repo = repo()
        repo.start()
        advanceUntilIdle()
        assertFalse(repo.isPro.value)
        gateway.owned = listOf(purchased.copy(acknowledged = true))
        gateway.updatesFlow.emit(PurchaseUpdate.AlreadyOwned)
        advanceUntilIdle()
        assertTrue(repo.isPro.value)
    }

    @Test fun refund_revokesCachedPro() = runTest {
        val repo = repo()
        gateway.owned = listOf(purchased)
        repo.start()
        advanceUntilIdle()
        assertTrue(repo.isPro.value)
        gateway.owned = emptyList() // refunded / revoked
        repo.restore()
        advanceUntilIdle()
        assertFalse(repo.isPro.value)
    }

    @Test fun failedQuery_keepsCachedPro() = runTest {
        val repo = repo()
        gateway.owned = listOf(purchased)
        repo.start()
        advanceUntilIdle()
        gateway.owned = null // Play query failed: must not revoke
        repo.restore()
        advanceUntilIdle()
        assertTrue(repo.isPro.value)
    }

    @Test fun connect_retriesWithBackoff() = runTest {
        val repo = repo()
        gateway.connectFailures = 2
        gateway.owned = listOf(purchased)
        repo.start()
        advanceUntilIdle()
        assertEquals(3, gateway.connectCalls)
        assertTrue(repo.isPro.value)
    }

    @Test fun acknowledgeFailure_isRetried() = runTest {
        val repo = repo()
        gateway.owned = listOf(purchased)
        gateway.acknowledgeFailures = 2
        repo.start()
        advanceUntilIdle()
        assertTrue(repo.isPro.value)
        assertEquals(listOf("tok", "tok", "tok"), gateway.acknowledged)
    }

    @Test fun acknowledgeFailure_givesUpAfterMaxAttemptsButKeepsPro() = runTest {
        val repo = repo()
        gateway.owned = listOf(purchased)
        gateway.acknowledgeFailures = 10
        repo.start()
        advanceUntilIdle()
        assertTrue(repo.isPro.value)
        assertEquals(3, gateway.acknowledged.size)
    }

    @Test fun disconnect_triggersRefresh() = runTest {
        val repo = repo()
        repo.start()
        advanceUntilIdle()
        val before = gateway.queryPurchasesCalls
        gateway.owned = listOf(purchased.copy(acknowledged = true))
        gateway.disconnectsFlow.emit(Unit)
        advanceUntilIdle()
        assertEquals(before + 1, gateway.queryPurchasesCalls)
        assertTrue(repo.isPro.value)
    }

    @Test fun foreground_refreshesPurchases() = runTest {
        val repo = repo()
        repo.start()
        advanceUntilIdle()
        gateway.owned = listOf(purchased.copy(acknowledged = true))
        repo.onForeground()
        advanceUntilIdle()
        assertTrue(repo.isPro.value)
    }

    @Test fun unspecifiedState_doesNotGrantPro() = runTest {
        val repo = repo()
        gateway.owned = listOf(OwnedPurchase("tok", PurchaseStatus.UNSPECIFIED, false))
        repo.start()
        advanceUntilIdle()
        assertFalse(repo.isPro.value)
        assertFalse(repo.pending.value)
        assertTrue(gateway.acknowledged.isEmpty())
    }

    @Test fun canceled_leavesStateUnchanged() = runTest {
        val repo = repo()
        repo.start()
        advanceUntilIdle()
        gateway.updatesFlow.emit(PurchaseUpdate.Canceled)
        advanceUntilIdle()
        assertFalse(repo.isPro.value)
        assertFalse(repo.pending.value)
        assertEquals("$4.99", repo.price.value)
    }

    @Test fun error_leavesStateUnchangedAndEmitsMessage() = runTest {
        val repo = repo()
        gateway.owned = listOf(purchased)
        repo.start()
        advanceUntilIdle()
        gateway.updatesFlow.emit(PurchaseUpdate.Error(6))
        advanceUntilIdle()
        assertTrue(repo.isPro.value)
        assertFalse(repo.pending.value)
        assertEquals(BillingMessage.PURCHASE_ERROR, repo.messages.first())
    }

    @Test fun missingProduct_marksUnavailable() = runTest {
        val repo = repo()
        gateway.product = ProductResult.Unavailable
        repo.start()
        advanceUntilIdle()
        assertTrue(repo.productUnavailable.value)
        assertEquals(null, repo.price.value)
        assertFalse(repo.isPro.value)
    }

    @Test fun failedProductQuery_isNotUnavailable() = runTest {
        val repo = repo()
        gateway.product = ProductResult.Failed
        repo.start()
        advanceUntilIdle()
        assertFalse(repo.productUnavailable.value)
        assertEquals(null, repo.price.value)
    }

    private class RecordingAnalytics : com.postpole.spoonfile.analytics.Analytics {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        val nonFatals = mutableListOf<Throwable>()
        override fun logEvent(name: String, params: Map<String, Any>) { events += name to params }
        override fun setUserProperty(name: String, value: String?) = Unit
        override fun logScreenView(screenName: String) = Unit
        override fun recordNonFatal(throwable: Throwable) { nonFatals += throwable }
    }

    private fun TestScope.repoWith(analytics: com.postpole.spoonfile.analytics.Analytics): BillingRepository {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "a.preferences_pb") }
        return BillingRepository(
            gateway, dataStore, scope,
            maxConnectAttempts = 3, initialBackoffMs = 10,
            maxAcknowledgeAttempts = 3, acknowledgeBackoffMs = 10,
            log = {}, analytics = analytics,
        )
    }

    @Test fun purchaseFlow_logsPurchaseWithValueAndCurrency() = runTest {
        val analytics = RecordingAnalytics()
        val repo = repoWith(analytics)
        gateway.product = ProductResult.Loaded("₹499", priceMicros = 499_000_000, currencyCode = "INR")
        repo.start()
        advanceUntilIdle()
        gateway.updatesFlow.emit(PurchaseUpdate.Purchases(listOf(purchased)))
        advanceUntilIdle()
        val purchase = analytics.events.single { it.first == "purchase" }.second
        assertEquals(499.0, purchase["value"])
        assertEquals("INR", purchase["currency"])
    }

    @Test fun refreshFindingPurchase_logsRestoredNotPurchase() = runTest {
        val analytics = RecordingAnalytics()
        val repo = repoWith(analytics)
        gateway.owned = listOf(purchased.copy(acknowledged = true))
        repo.start()
        advanceUntilIdle()
        assertEquals(listOf("purchase_restored"), analytics.events.map { it.first })
    }

    @Test fun purchaseError_isRecordedAsNonFatal() = runTest {
        val analytics = RecordingAnalytics()
        val repo = repoWith(analytics)
        repo.start()
        advanceUntilIdle()
        gateway.updatesFlow.emit(PurchaseUpdate.Error(6))
        advanceUntilIdle()
        assertTrue(analytics.nonFatals.single() is BillingException)
    }

    private val removeAdsBought =
        OwnedPurchase("ra", PurchaseStatus.PURCHASED, acknowledged = true, product = BillingProduct.REMOVE_ADS)

    @Test fun refresh_queriesBothProducts() = runTest {
        val repo = repo()
        repo.start()
        advanceUntilIdle()
        assertTrue(gateway.queriedProducts.containsAll(listOf(BillingProduct.PRO, BillingProduct.REMOVE_ADS)))
        assertEquals("RA-PRICE", repo.removeAdsPrice.value)
        assertEquals("$4.99", repo.price.value)
    }

    @Test fun removeAdsOnly_hidesAdsButIsNotPro() = runTest {
        val repo = repo()
        gateway.owned = listOf(removeAdsBought)
        repo.start()
        advanceUntilIdle()
        assertTrue(repo.removeAdsOwned.value)
        assertTrue(repo.adsRemoved.value)
        assertEquals(true, repo.adsRemovedOrUnknown.value)
        assertFalse(repo.isPro.value)
    }

    @Test fun proOnly_hidesAdsWithoutOwningRemoveAds() = runTest {
        val repo = repo()
        gateway.owned = listOf(purchased.copy(acknowledged = true))
        repo.start()
        advanceUntilIdle()
        assertTrue(repo.isPro.value)
        assertTrue(repo.adsRemoved.value)
        assertFalse(repo.removeAdsOwned.value)
    }

    @Test fun neverOwned_adsRemovedIsFalse_andUnknownBeforeLoad() = runTest {
        val repo = repo()
        assertEquals(null, repo.adsRemovedOrUnknown.value)
        repo.start()
        advanceUntilIdle()
        assertFalse(repo.adsRemoved.value)
        assertEquals(false, repo.adsRemovedOrUnknown.value)
    }

    @Test fun restore_unlocksBoth() = runTest {
        val repo = repo()
        repo.start()
        advanceUntilIdle()
        gateway.owned = listOf(purchased.copy(acknowledged = true), removeAdsBought)
        repo.restore()
        advanceUntilIdle()
        assertTrue(repo.isPro.value)
        assertTrue(repo.removeAdsOwned.value)
    }

    @Test fun refundOfRemoveAds_keepsPro() = runTest {
        val repo = repo()
        gateway.owned = listOf(purchased.copy(acknowledged = true), removeAdsBought)
        repo.start()
        advanceUntilIdle()
        gateway.owned = listOf(purchased.copy(acknowledged = true))
        repo.refresh()
        advanceUntilIdle()
        assertTrue(repo.isPro.value)
        assertFalse(repo.removeAdsOwned.value)
        assertTrue(repo.adsRemoved.value)
    }

    @Test fun refundOfPro_keepsRemoveAds_andCapReturns() = runTest {
        val repo = repo()
        gateway.owned = listOf(purchased.copy(acknowledged = true), removeAdsBought)
        repo.start()
        advanceUntilIdle()
        gateway.owned = listOf(removeAdsBought)
        repo.refresh()
        advanceUntilIdle()
        assertFalse(repo.isPro.value)
        assertTrue(repo.removeAdsOwned.value)
        assertTrue(repo.adsRemoved.value)
    }

    @Test fun refundOfBoth_showsAdsAgain() = runTest {
        val repo = repo()
        gateway.owned = listOf(purchased.copy(acknowledged = true), removeAdsBought)
        repo.start()
        advanceUntilIdle()
        gateway.owned = emptyList()
        repo.refresh()
        advanceUntilIdle()
        assertFalse(repo.adsRemoved.value)
    }

    @Test fun removeAdsPurchaseFlow_acknowledgesAndDoesNotTouchPro() = runTest {
        val repo = repo()
        repo.start()
        advanceUntilIdle()
        gateway.updatesFlow.emit(
            PurchaseUpdate.Purchases(listOf(removeAdsBought.copy(acknowledged = false))),
        )
        advanceUntilIdle()
        assertTrue(repo.removeAdsOwned.value)
        assertFalse(repo.isPro.value)
        assertEquals(listOf("ra"), gateway.acknowledged)
    }

    @Test fun removeAdsPending_doesNotHideAds() = runTest {
        val repo = repo()
        gateway.owned = listOf(removeAdsBought.copy(status = PurchaseStatus.PENDING, acknowledged = false))
        repo.start()
        advanceUntilIdle()
        assertTrue(repo.removeAdsPending.value)
        assertFalse(repo.pending.value)
        assertFalse(repo.adsRemoved.value)
    }

    @Test fun removeAdsMissingSku_isUnavailableWithoutPriceAndProStillLoads() = runTest {
        val repo = repo()
        gateway.removeAdsProduct = ProductResult.Unavailable
        repo.start()
        advanceUntilIdle()
        assertTrue(repo.removeAdsUnavailable.value)
        assertEquals(null, repo.removeAdsPrice.value)
        assertFalse(repo.productUnavailable.value)
        assertEquals("$4.99", repo.price.value)
    }

    @Test fun removeAdsRestore_logsNoPurchaseEvent() = runTest {
        val analytics = RecordingAnalytics()
        val repo = repoWith(analytics)
        gateway.owned = listOf(removeAdsBought)
        repo.start()
        advanceUntilIdle()
        assertTrue(analytics.events.none { it.first == "purchase" })
    }

    @Test fun connect_givesUpAfterMaxAttempts() = runTest {
        val repo = repo()
        gateway.connectFailures = 10
        repo.start()
        advanceUntilIdle()
        assertEquals(3, gateway.connectCalls)
        assertFalse(repo.isPro.value)
    }

    // The repo is never started, so the stateIn flows stay false: restore() must read the store.
    @Test fun restore_proFound_returnsRestoredImmediately() = runTest {
        val repo = repo()
        gateway.owned = listOf(purchased)
        assertEquals(RestoreResult.RESTORED, repo.restore())
    }

    @Test fun restore_removeAdsFound_returnsRestoredImmediately() = runTest {
        val repo = repo()
        gateway.owned = listOf(
            OwnedPurchase("ra", PurchaseStatus.PURCHASED, acknowledged = false, product = BillingProduct.REMOVE_ADS),
        )
        assertEquals(RestoreResult.RESTORED, repo.restore())
    }

    @Test fun restore_nothingOwned_returnsNothingToRestore() = runTest {
        val repo = repo()
        gateway.owned = emptyList()
        assertEquals(RestoreResult.NOTHING_TO_RESTORE, repo.restore())
    }

    @Test fun restore_queryFails_returnsFailed() = runTest {
        val repo = repo()
        gateway.owned = null
        assertEquals(RestoreResult.FAILED, repo.restore())
    }
}
