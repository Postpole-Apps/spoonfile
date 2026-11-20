package com.postpole.spoonfile.billing

import android.app.Activity
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.postpole.spoonfile.analytics.Analytics
import com.postpole.spoonfile.analytics.AnalyticsEvents
import com.postpole.spoonfile.analytics.AnalyticsParams
import com.postpole.spoonfile.analytics.NoOpAnalytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the Pro and Remove ads entitlements. Both are cached in DataStore for instant startup but are
 * reconciled with Play on every connect, so refunds/revocations remove access. [isPro] gates Pro features;
 * [adsRemoved] is true for Pro **or** `remove_ads`.
 */
class BillingRepository(
    private val gateway: BillingGateway,
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
    private val maxConnectAttempts: Int = 5,
    private val initialBackoffMs: Long = 1_000,
    private val maxAcknowledgeAttempts: Int = 3,
    private val acknowledgeBackoffMs: Long = 500,
    private val log: (String) -> Unit = { Log.w("BillingRepository", it) },
    private val analytics: Analytics = NoOpAnalytics,
) {
    private enum class Source { PURCHASE_FLOW, REFRESH }

    @Volatile
    private var loadedPro: ProductResult.Loaded? = null

    /** Pro entitlement. Gates every Pro feature (recipe cap, whole-file import); never affected by `remove_ads`. */
    val isPro: StateFlow<Boolean> = dataStore.data
        .map { it[IS_PRO] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** `remove_ads` is owned. Hides ads only; it does not unlock any Pro feature. */
    val removeAdsOwned: StateFlow<Boolean> = dataStore.data
        .map { it[HAS_REMOVE_ADS] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** Ads are hidden: Pro **or** `remove_ads` is owned. */
    val adsRemoved: StateFlow<Boolean> = dataStore.data
        .map { (it[IS_PRO] ?: false) || (it[HAS_REMOVE_ADS] ?: false) }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /**
     * Same as [adsRemoved], but `null` until DataStore has been read. Ads wait for a non-null value so an
     * existing buyer is never shown (or sent) an ad request while the cache loads.
     */
    val adsRemovedOrUnknown: StateFlow<Boolean?> = dataStore.data
        .map<Preferences, Boolean?> { (it[IS_PRO] ?: false) || (it[HAS_REMOVE_ADS] ?: false) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    private val _price = MutableStateFlow<String?>(null)

    /** Localized Pro price from ProductDetails, null until loaded. */
    val price: StateFlow<String?> = _price.asStateFlow()

    private val _removeAdsPrice = MutableStateFlow<String?>(null)

    /** Localized Remove ads price from ProductDetails, null until loaded. Never typed in. */
    val removeAdsPrice: StateFlow<String?> = _removeAdsPrice.asStateFlow()

    private val _pending = MutableStateFlow(false)

    /** True while a Pro purchase is PENDING (not yet paid); Pro is not granted. */
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    private val _removeAdsPending = MutableStateFlow(false)

    /** True while a Remove ads purchase is PENDING; ads stay on until it completes. */
    val removeAdsPending: StateFlow<Boolean> = _removeAdsPending.asStateFlow()

    private val _productUnavailable = MutableStateFlow(false)

    /** True when Play answered but the Pro product/offer isn't available (so don't show "loading"). */
    val productUnavailable: StateFlow<Boolean> = _productUnavailable.asStateFlow()

    private val _removeAdsUnavailable = MutableStateFlow(false)

    /** Same as [productUnavailable] for `remove_ads` (e.g. the SKU is not created in Play yet). */
    val removeAdsUnavailable: StateFlow<Boolean> = _removeAdsUnavailable.asStateFlow()

    private val _messages = Channel<BillingMessage>(Channel.BUFFERED)

    /** One-shot messages for the UI (purchase errors, launch failures). */
    val messages: Flow<BillingMessage> = _messages.receiveAsFlow()

    private val refreshLock = Mutex()
    private var started = false

    /** Call once at app start. Connects, loads product, restores purchases. */
    fun start() {
        if (started) return
        started = true
        scope.launch { gateway.updates.collect(::onUpdate) }
        scope.launch { gateway.disconnects.collect { refresh() } }
        scope.launch { refresh() }
    }

    /** Reconnects if needed, then re-queries product details and owned purchases. */
    suspend fun refresh(): Boolean = refreshLock.withLock {
        if (!connectWithRetry()) return false
        when (val product = gateway.queryProduct(BillingProduct.PRO)) {
            is ProductResult.Loaded -> {
                loadedPro = product
                _price.value = product.formattedPrice
                _productUnavailable.value = false
            }
            ProductResult.Unavailable -> {
                _price.value = null
                _productUnavailable.value = true
            }
            ProductResult.Failed -> analytics.recordNonFatal(BillingException("Product details query failed"))
        }
        when (val product = gateway.queryProduct(BillingProduct.REMOVE_ADS)) {
            is ProductResult.Loaded -> {
                _removeAdsPrice.value = product.formattedPrice
                _removeAdsUnavailable.value = false
            }
            ProductResult.Unavailable -> {
                _removeAdsPrice.value = null
                _removeAdsUnavailable.value = true
            }
            ProductResult.Failed ->
                analytics.recordNonFatal(BillingException("Remove ads product details query failed"))
        }
        val owned = gateway.queryPurchases()
        if (owned == null) {
            analytics.recordNonFatal(BillingException("Purchases query failed"))
            return false
        }
        applyPurchases(owned, revokeIfAbsent = true, source = Source.REFRESH)
        true
    }

    /** User-initiated restore. */
    suspend fun restore(): Boolean = refresh()

    /** Call when the app comes to the foreground to pick up pending completions and refunds. */
    fun onForeground() {
        if (started) scope.launch { refresh() }
    }

    fun launchPurchase(activity: Activity, product: BillingProduct = BillingProduct.PRO): Boolean {
        val launched = gateway.launchPurchase(activity, product)
        if (launched) {
            // Remove ads events are wired in sf-04; the existing events describe Pro only.
            if (product == BillingProduct.PRO) analytics.logEvent(AnalyticsEvents.PURCHASE_STARTED)
        } else {
            analytics.recordNonFatal(BillingException("Could not launch purchase flow"))
            _messages.trySend(BillingMessage.LAUNCH_FAILED)
        }
        return launched
    }

    private suspend fun onUpdate(update: PurchaseUpdate) {
        when (update) {
            is PurchaseUpdate.Purchases ->
                applyPurchases(update.purchases, revokeIfAbsent = false, source = Source.PURCHASE_FLOW)
            PurchaseUpdate.AlreadyOwned -> refresh()
            PurchaseUpdate.Canceled -> Unit
            is PurchaseUpdate.Error -> {
                log("Purchase failed with code ${update.code}")
                analytics.recordNonFatal(BillingException("Purchase failed with code ${update.code}"))
                _messages.trySend(BillingMessage.PURCHASE_ERROR)
            }
        }
    }

    private suspend fun applyPurchases(purchases: List<OwnedPurchase>, revokeIfAbsent: Boolean, source: Source) {
        // Each product is reconciled on its own: refunding or revoking one never touches the other.
        val pro = purchases.filter { it.product == BillingProduct.PRO }
        val removeAds = purchases.filter { it.product == BillingProduct.REMOVE_ADS }
        val proPurchased = pro.filter { it.status == PurchaseStatus.PURCHASED }
        val removeAdsPurchased = removeAds.filter { it.status == PurchaseStatus.PURCHASED }
        _pending.value = proPurchased.isEmpty() && pro.any { it.status == PurchaseStatus.PENDING }
        _removeAdsPending.value = removeAdsPurchased.isEmpty() && removeAds.any { it.status == PurchaseStatus.PENDING }

        if (proPurchased.isNotEmpty()) {
            val wasPro = dataStore.data.first()[IS_PRO] ?: false
            dataStore.edit { it[IS_PRO] = true }
            if (!wasPro) logPurchaseEvent(source)
        } else if (revokeIfAbsent) {
            dataStore.edit { it[IS_PRO] = false }
        }
        if (removeAdsPurchased.isNotEmpty()) {
            dataStore.edit { it[HAS_REMOVE_ADS] = true }
        } else if (revokeIfAbsent) {
            dataStore.edit { it[HAS_REMOVE_ADS] = false }
        }
        (proPurchased + removeAdsPurchased).filter { !it.acknowledged }.forEach { acknowledgeWithRetry(it.token) }
    }

    /** `purchase` (with value + currency) for a fresh buy, `purchase_restored` when found on refresh/restore. */
    private fun logPurchaseEvent(source: Source) {
        when (source) {
            Source.PURCHASE_FLOW -> {
                val product = loadedPro
                val params = buildMap<String, Any> {
                    if (product != null && product.priceMicros > 0 && product.currencyCode.isNotEmpty()) {
                        put(AnalyticsParams.VALUE, product.priceMicros / 1_000_000.0)
                        put(AnalyticsParams.CURRENCY, product.currencyCode)
                    }
                }
                analytics.logEvent(AnalyticsEvents.PURCHASE, params)
            }
            Source.REFRESH -> analytics.logEvent(AnalyticsEvents.PURCHASE_RESTORED)
        }
    }

    /** Play refunds unacknowledged purchases after 3 days, so retry now; the next refresh retries again. */
    private suspend fun acknowledgeWithRetry(token: String) {
        var backoff = acknowledgeBackoffMs
        repeat(maxAcknowledgeAttempts) { attempt ->
            if (gateway.acknowledge(token)) return
            log("Acknowledge failed (attempt ${attempt + 1}/$maxAcknowledgeAttempts)")
            if (attempt < maxAcknowledgeAttempts - 1) {
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }
        analytics.recordNonFatal(BillingException("Acknowledge failed after $maxAcknowledgeAttempts attempts"))
    }

    private suspend fun connectWithRetry(): Boolean {
        var backoff = initialBackoffMs
        repeat(maxConnectAttempts) { attempt ->
            if (gateway.connect()) return true
            if (attempt < maxConnectAttempts - 1) {
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }
        return false
    }

    private companion object {
        val IS_PRO = booleanPreferencesKey("is_pro")
        val HAS_REMOVE_ADS = booleanPreferencesKey("has_remove_ads")
        const val MAX_BACKOFF_MS = 15_000L
    }
}
