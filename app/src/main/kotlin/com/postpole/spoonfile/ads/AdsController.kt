package com.postpole.spoonfile.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.Lifecycle
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.postpole.spoonfile.BuildConfig
import com.postpole.spoonfile.billing.BillingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single owner of ads: initialises the SDK once UMP allows it, exposes whether banners may show, and runs the
 * one interstitial (after leaving recipe detail / cook mode back to the Recipes list).
 *
 * There is deliberately no app-open, exit, rewarded or native path here.
 */
@Singleton
class AdsController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configSource: AdsConfigSource,
    private val consent: ConsentManager,
    billing: BillingRepository,
    private val dataStore: DataStore<Preferences>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sdkReady = MutableStateFlow(false)

    // Pro or `remove_ads` hides every ad.
    private val inputs: StateFlow<AdInputs> = combine(
        configSource.config, consent.canRequestAds, sdkReady, billing.adsRemovedOrUnknown,
    ) { config, canRequest, ready, removed -> AdInputs(config, canRequest, ready, removed) }
        .stateIn(scope, SharingStarted.Eagerly, AdInputs(configSource.config.value, false, false, null))

    /** Banners show only while this is true. It flips false at once on Pro, kill switch or lost consent. */
    val bannersAllowed: StateFlow<Boolean> = MutableStateFlow(false).also { out ->
        scope.launch { inputs.collect { out.value = AdEligibility.banners(it) } }
    }

    private val historyLock = Mutex()
    private var history = InterstitialHistory()
    private var historyLoaded = false

    private var interstitial: InterstitialAd? = null
    private var interstitialLoadedAt = 0L
    private var interstitialLoading = false
    private var interstitialShowing = false
    private var started = false

    /** Call once from [android.app.Application.onCreate]. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            // UMP first. MobileAds is initialised only after UMP says ads can be requested.
            consent.canRequestAds.filter { it }.first()
            initializeSdk()
        }
        scope.launch { historyLock.withLock { loadHistory(countSession = true) } }
        scope.launch {
            // Anything that stops being allowed (Pro, kill switch, lost consent) drops a preloaded interstitial.
            inputs.collect { if (!AdEligibility.interstitials(it)) interstitial = null }
        }
        configSource.refresh()
    }

    /** Foreground: pick up Remote Config changes (the `ads_enabled` kill switch). */
    fun onForeground() = configSource.refresh()

    /** Runs UMP; the activity is needed for the consent form. Safe to call on every launch. */
    fun gatherConsent(activity: Activity) = consent.gather(activity)

    val privacyOptionsRequired: StateFlow<Boolean> get() = consent.privacyOptionsRequired

    fun showPrivacyOptions(activity: Activity) = consent.showPrivacyOptions(activity)

    private suspend fun initializeSdk() {
        val rating = when (configSource.config.value.maxContentRating.uppercase()) {
            "G" -> RequestConfiguration.MAX_AD_CONTENT_RATING_G
            "T" -> RequestConfiguration.MAX_AD_CONTENT_RATING_T
            "MA" -> RequestConfiguration.MAX_AD_CONTENT_RATING_MA
            else -> RequestConfiguration.MAX_AD_CONTENT_RATING_PG
        }
        // Content rating only. The child-directed tag is intentionally never set.
        MobileAds.setRequestConfiguration(RequestConfiguration.Builder().setMaxAdContentRating(rating).build())
        MobileAds.initialize(context) { sdkReady.value = true }
    }

    // --- Interstitial: after leaving recipe detail / cook mode back to the Recipes list ---

    /**
     * Warms an interstitial while the user is on recipe detail / cook mode. Nothing is requested unless one
     * could actually be shown (caps pass, ads allowed), so no request is wasted or made without consent.
     */
    fun preloadInterstitial() {
        scope.launch {
            if (interstitialLoading || hasFreshInterstitial()) return@launch
            if (!interstitialDue()) return@launch
            interstitialLoading = true
            InterstitialAd.load(
                context,
                BuildConfig.AD_UNIT_INTERSTITIAL_AFTER_RECIPE,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitialLoading = false
                        interstitial = ad
                        interstitialLoadedAt = System.currentTimeMillis()
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        // Fail quietly.
                        interstitialLoading = false
                        Log.d(TAG, "Interstitial not loaded: ${error.code}")
                    }
                },
            )
        }
    }

    /**
     * The user just arrived back on the Recipes list. After a settle delay (so a tap meant for the list can't
     * land on the ad) shows the interstitial if [stillOnList] is still true, the activity is resumed, and every
     * cap and entitlement check passes. Cancel the calling coroutine to abandon it.
     */
    suspend fun showAfterRecipe(activity: Activity, stillOnList: () -> Boolean) {
        delay(SETTLE_DELAY_MS)
        if (!stillOnList()) return
        if (!activity.isResumed() || interstitialShowing) return
        val ad = interstitial?.takeIf { hasFreshInterstitial() } ?: return
        if (!interstitialDue()) return
        interstitial = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                scope.launch { recordShown() }
            }

            override fun onAdDismissedFullScreenContent() {
                interstitialShowing = false
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitialShowing = false
                Log.d(TAG, "Interstitial not shown: ${error.code}")
            }
        }
        interstitialShowing = true
        ad.show(activity)
    }

    private fun hasFreshInterstitial() =
        interstitial != null && System.currentTimeMillis() - interstitialLoadedAt < AD_EXPIRY_MS

    private suspend fun interstitialDue(): Boolean {
        val current = inputs.value
        if (!AdEligibility.interstitials(current)) return false
        return historyLock.withLock {
            loadHistory(countSession = false)
            InterstitialPolicy.isDue(current.config, history, installedAt(), System.currentTimeMillis())
        }
    }

    private suspend fun recordShown() = historyLock.withLock {
        loadHistory(countSession = false)
        history = InterstitialPolicy.recordShown(history, System.currentTimeMillis())
        dataStore.edit {
            it[LAST_SHOWN] = history.lastShownAt ?: 0L
            it[DAY] = history.day.orEmpty()
            it[SHOWN_TODAY] = history.shownToday
        }
    }

    /** Reads persisted caps once; [countSession] adds this process as a session (done once, at start). */
    private suspend fun loadHistory(countSession: Boolean) {
        if (!historyLoaded) {
            val saved = dataStore.data.first()
            history = InterstitialHistory(
                sessionCount = saved[SESSIONS] ?: 0,
                lastShownAt = saved[LAST_SHOWN]?.takeIf { it > 0 },
                day = saved[DAY]?.takeIf { it.isNotEmpty() },
                shownToday = saved[SHOWN_TODAY] ?: 0,
            )
            historyLoaded = true
        }
        if (countSession) {
            history = history.copy(sessionCount = history.sessionCount + 1)
            dataStore.edit { it[SESSIONS] = history.sessionCount }
        }
    }

    /** App sessions so far, including this one. Waits for the start-up load, so it never reads a stale count. */
    suspend fun sessionCount(): Int = historyLock.withLock {
        loadHistory(countSession = false)
        history.sessionCount
    }

    /** True once an interstitial has actually been shown since this process started. */
    suspend fun interstitialShownThisSession(): Boolean = historyLock.withLock { history.shownThisSession > 0 }

    /** When the app was first installed (epoch millis). */
    fun installedAt(): Long = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
    }.getOrDefault(System.currentTimeMillis())

    private fun Activity.isResumed(): Boolean =
        (this as? androidx.lifecycle.LifecycleOwner)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true &&
            !isFinishing

    private companion object {
        const val TAG = "AdsController"

        /** Long enough for the navigation animation to finish and for the next tap to have landed on the list. */
        const val SETTLE_DELAY_MS = 1_500L

        /** Google expires loaded interstitials after an hour. */
        const val AD_EXPIRY_MS = 55 * 60 * 1000L

        val SESSIONS = intPreferencesKey("ads_session_count")
        val LAST_SHOWN = longPreferencesKey("ads_interstitial_last_shown")
        val DAY = stringPreferencesKey("ads_interstitial_day")
        val SHOWN_TODAY = intPreferencesKey("ads_interstitial_shown_today")
    }
}
