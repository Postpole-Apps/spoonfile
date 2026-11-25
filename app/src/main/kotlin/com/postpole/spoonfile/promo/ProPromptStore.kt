package com.postpole.spoonfile.promo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Writes the signals the automatic Pro prompts depend on. Safe to call from any screen's ViewModel. */
interface ProPromptRecorder {
    /** A cook-mode completion or a recipe save. A whole-file / bulk import calls this once, not per recipe. */
    suspend fun recordEngagedEvent()

    /** The user was sent to Pro by a hard gate (cap, meal plan, oversized file). Starts the 48 h quiet period. */
    suspend fun recordHardGate()
}

/** For tests and previews. */
object NoOpProPromptRecorder : ProPromptRecorder {
    override suspend fun recordEngagedEvent() = Unit
    override suspend fun recordHardGate() = Unit
}

private fun firstInstallTime(context: Context): Long = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
}.getOrDefault(System.currentTimeMillis())

/** DataStore-backed history plus the in-memory, per-process session flags. */
@Singleton
class ProPromptStore(
    private val dataStore: DataStore<Preferences>,
    private val installedAt: () -> Long,
    private val clock: () -> Long,
) : ProPromptRecorder {

    @Inject
    constructor(
        dataStore: DataStore<Preferences>,
        @ApplicationContext context: Context,
    ) : this(
        dataStore,
        installedAt = { firstInstallTime(context) },
        clock = System::currentTimeMillis,
    )

    /** An auto prompt was chosen this process. AppNavHost then skips the after-recipe interstitial. */
    @Volatile
    var shownThisSession: Boolean = false

    /** An interstitial was shown this process (set by `AdsController`). Suppresses auto prompts. */
    @Volatile
    var interstitialShownThisSession: Boolean = false

    /** Set when the list is reached by returning from another screen; consumed by the list's evaluation. */
    @Volatile
    var restPointPending: Boolean = false

    fun consumeRestPoint(): Boolean = restPointPending.also { restPointPending = false }

    fun now(): Long = clock()

    fun installAgeDays(): Int = ((clock() - installedAt()) / DAY).toInt().coerceAtLeast(0)

    /** Process starts so far, counted by `AdsController` under the same key. */
    suspend fun sessionCount(): Int = dataStore.data.first()[SESSIONS] ?: 0

    suspend fun history(): ProPromptHistory {
        val p = dataStore.data.first()
        val trigger = ProPromptTrigger.RECIPES_MILESTONE
        return ProPromptHistory(
            lastSheetAt = p[LAST_SHEET_AT],
            sheetDismissals = p[SHEET_DISMISSALS] ?: 0,
            lastCardAt = p[LAST_CARD_AT],
            cardDismissedAt = p[cardDismissedAtKey(trigger)]?.let { mapOf(trigger to it) }.orEmpty(),
            cardDismissals = mapOf(trigger to (p[cardDismissalsKey(trigger)] ?: 0)),
            lastHardGateAt = p[LAST_HARD_GATE_AT],
            engagedEvents = p[ENGAGED_EVENTS] ?: 0,
        )
    }

    override suspend fun recordEngagedEvent() {
        dataStore.edit { it[ENGAGED_EVENTS] = (it[ENGAGED_EVENTS] ?: 0) + 1 }
    }

    override suspend fun recordHardGate() {
        val now = clock()
        dataStore.edit { it[LAST_HARD_GATE_AT] = now }
    }

    /** The prompt was put on screen. A sheet also restarts the engaged-event count. */
    suspend fun recordShown(trigger: ProPromptTrigger) {
        shownThisSession = true
        val now = clock()
        dataStore.edit {
            when (trigger) {
                ProPromptTrigger.COOK_OR_SAVE_MILESTONE -> {
                    it[LAST_SHEET_AT] = now
                    it[ENGAGED_EVENTS] = 0
                }
                ProPromptTrigger.RECIPES_MILESTONE -> it[LAST_CARD_AT] = now
            }
        }
    }

    suspend fun recordDismissed(trigger: ProPromptTrigger) {
        val now = clock()
        dataStore.edit {
            when (trigger) {
                ProPromptTrigger.COOK_OR_SAVE_MILESTONE -> it[SHEET_DISMISSALS] = (it[SHEET_DISMISSALS] ?: 0) + 1
                ProPromptTrigger.RECIPES_MILESTONE -> {
                    it[cardDismissedAtKey(trigger)] = now
                    val key = cardDismissalsKey(trigger)
                    it[key] = (it[key] ?: 0) + 1
                }
            }
        }
    }

    private companion object {
        val DAY = TimeUnit.DAYS.toMillis(1)

        /** Written by `AdsController`; shared so there is one session count. */
        val SESSIONS = intPreferencesKey("ads_session_count")
        val LAST_SHEET_AT = longPreferencesKey("pro_prompt_last_sheet_at")
        val SHEET_DISMISSALS = intPreferencesKey("pro_prompt_sheet_dismissals")
        val LAST_CARD_AT = longPreferencesKey("pro_prompt_last_card_at")
        val LAST_HARD_GATE_AT = longPreferencesKey("pro_prompt_last_hard_gate_at")
        val ENGAGED_EVENTS = intPreferencesKey("pro_prompt_engaged_events")

        fun cardDismissedAtKey(t: ProPromptTrigger) = longPreferencesKey("pro_prompt_card_dismissed_${t.wire}_at")
        fun cardDismissalsKey(t: ProPromptTrigger) = intPreferencesKey("pro_prompt_card_dismissals_${t.wire}")
    }
}
