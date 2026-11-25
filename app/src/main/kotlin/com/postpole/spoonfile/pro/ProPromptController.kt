package com.postpole.spoonfile.pro

import com.postpole.spoonfile.ads.AdsController
import com.postpole.spoonfile.analytics.Analytics
import com.postpole.spoonfile.analytics.AnalyticsEvents
import com.postpole.spoonfile.analytics.AnalyticsParams
import com.postpole.spoonfile.billing.BillingRepository
import com.postpole.spoonfile.data.BackupNudge
import com.postpole.spoonfile.data.Limits
import com.postpole.spoonfile.data.RecipeRepository
import com.postpole.spoonfile.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Session-level owner of the automatic Pro prompts: asks [ProPromptPolicy] when the user arrives on the Recipes
 * list, remembers what is on screen, and records shown / dismissed / CTA. At most one prompt per session.
 * A "session" here is a process start (stricter than the 30-minute rule).
 */
@Singleton
class ProPromptController @Inject constructor(
    private val ads: AdsController,
    private val billing: BillingRepository,
    private val recipes: RecipeRepository,
    private val settings: SettingsRepository,
    private val repository: ProPromptRepository,
    private val configSource: ProPromptConfigSource,
    private val analytics: Analytics,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()
    private val _active = MutableStateFlow<ProPromptTrigger?>(null)

    /** The prompt currently on the Recipes list, or null. */
    val active: StateFlow<ProPromptTrigger?> = _active.asStateFlow()

    /**
     * True once a prompt was selected in this process. `AppNavHost` skips the same-session interstitial when
     * this is set (the Pro prompt wins).
     */
    @Volatile
    var shownThisSession: Boolean = false
        private set

    /** Foreground: pick up Remote Config changes. */
    fun onForeground() = configSource.refresh()

    /**
     * The Recipes list was entered. [atRestPoint] is true when the user came back from another screen and false
     * on a cold start. Returns true if a prompt was selected (and so the interstitial must be skipped).
     */
    suspend fun onListArrival(atRestPoint: Boolean): Boolean = lock.withLock {
        if (shownThisSession) return@withLock true
        val config = configSource.config.value
        val now = System.currentTimeMillis()
        // Wait for the entitlement cache: a buyer must never be treated as free while DataStore loads.
        val isPro = billing.isProOrUnknown.filterNotNull().first()
        val adsRemoved = billing.adsRemovedOrUnknown.filterNotNull().first()
        if (isPro || !config.enabled) return@withLock false
        val count = recipes.count().first()
        val nudge = BackupNudge.shouldShow(
            remindEnabled = settings.remindBackup.first(),
            recipeCount = count,
            lastExportAt = settings.lastExportAt.first(),
            dismissedAt = settings.nudgeDismissedAt.first(),
            now = now,
        )
        val ctx = ProPromptContext(
            now = now,
            isPro = isPro,
            removeAdsOwned = adsRemoved,
            sessionCount = ads.sessionCount(),
            installAgeDays = ((now - ads.installedAt()) / ProPromptPolicy.DAY_MS).toInt(),
            recipeCount = count,
            atLimit = count >= Limits.FREE_RECIPE_LIMIT,
            showBackupNudge = nudge,
            promptShownThisSession = false,
            interstitialShownThisSession = ads.interstitialShownThisSession(),
            atRestPoint = atRestPoint,
        )
        val trigger = ProPromptPolicy.evaluate(config, repository.history(), ctx) ?: return@withLock false
        shownThisSession = true
        _active.value = trigger
        // Navigating away cancels the caller; the cap must still be recorded once the prompt is selected.
        withContext(NonCancellable) { repository.recordShown(trigger) }
        analytics.logEvent(AnalyticsEvents.PRO_PROMPT_SHOWN, params(trigger))
        true
    }

    /** The user dismissed the visible prompt. [method] is a `ProPromptDismissMethod` value. */
    fun onDismissed(method: String) {
        val trigger = takeActive() ?: return
        analytics.logEvent(AnalyticsEvents.PRO_PROMPT_DISMISSED, params(trigger) + (AnalyticsParams.METHOD to method))
        scope.launch { repository.recordDismissed(trigger) }
    }

    /**
     * The user tapped the call to action. Hides the prompt without counting a dismissal and returns the trigger
     * to pass to the Pro screen (`auto_<trigger>`), or null if nothing was showing.
     */
    fun onCtaTap(): ProPromptTrigger? {
        val trigger = takeActive() ?: return null
        analytics.logEvent(AnalyticsEvents.PRO_PROMPT_CTA_TAP, params(trigger))
        return trigger
    }

    /** Clears the visible prompt and returns it; called from the main thread only. */
    private fun takeActive(): ProPromptTrigger? = _active.value.also { _active.value = null }

    private fun params(trigger: ProPromptTrigger): Map<String, Any> =
        mapOf(AnalyticsParams.TRIGGER to trigger.id, AnalyticsParams.SURFACE to trigger.surface.id)
}
