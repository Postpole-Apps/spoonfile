package com.postpole.spoonfile.pro

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** The few things other screens report to the automatic Pro prompts. */
interface ProPromptSignals {
    /** A cook-mode completion or a recipe save happened. A whole-file or bulk import is one call, not one per recipe. */
    suspend fun recordEngaged()

    /** An import saved at least one recipe. A whole-file import is one call, not one per recipe. */
    suspend fun recordImport()

    /** A hard gate (cap, meal plan, oversized import) sent the user to the Pro screen. */
    suspend fun recordHardGate()
}

/** Used where nothing listens (tests). */
object NoOpProPromptSignals : ProPromptSignals {
    override suspend fun recordEngaged() = Unit
    override suspend fun recordImport() = Unit
    override suspend fun recordHardGate() = Unit
}

/** DataStore-backed [ProPromptHistory]. */
@Singleton
class ProPromptRepository(
    private val dataStore: DataStore<Preferences>,
    private val clock: ProPromptClock,
) : ProPromptSignals {
    @Inject constructor(dataStore: DataStore<Preferences>) : this(dataStore, SystemProPromptClock)

    suspend fun history(): ProPromptHistory {
        val prefs = dataStore.data.first()
        val triggers = ProPromptTrigger.entries.map { it.id }
        return ProPromptHistory(
            lastSheetAt = prefs[LAST_SHEET_AT],
            sheetDismissals = prefs[SHEET_DISMISSALS] ?: 0,
            lastCardAt = prefs[LAST_CARD_AT],
            cardDismissedAt = triggers.mapNotNull { id -> prefs[cardDismissedKey(id)]?.let { id to it } }.toMap(),
            cardDismissals = triggers.mapNotNull { id -> prefs[cardDismissalsKey(id)]?.let { id to it } }.toMap(),
            lastHardGateAt = prefs[LAST_HARD_GATE_AT],
            engagedEvents = prefs[ENGAGED_EVENTS] ?: 0,
            importCount = prefs[IMPORT_COUNT] ?: 0,
        )
    }

    override suspend fun recordEngaged() {
        dataStore.edit { it[ENGAGED_EVENTS] = (it[ENGAGED_EVENTS] ?: 0) + 1 }
    }

    override suspend fun recordImport() {
        dataStore.edit { it[IMPORT_COUNT] = (it[IMPORT_COUNT] ?: 0) + 1 }
    }

    override suspend fun recordHardGate() {
        dataStore.edit { it[LAST_HARD_GATE_AT] = clock.now() }
    }

    /** A prompt was put on screen. A sheet also starts the next count of engaged events from zero. */
    suspend fun recordShown(trigger: ProPromptTrigger) {
        dataStore.edit {
            when (trigger.surface) {
                ProPromptSurface.SHEET -> {
                    it[LAST_SHEET_AT] = clock.now()
                    it[ENGAGED_EVENTS] = 0
                }
                ProPromptSurface.CARD -> it[LAST_CARD_AT] = clock.now()
            }
        }
    }

    suspend fun recordDismissed(trigger: ProPromptTrigger) {
        dataStore.edit {
            when (trigger.surface) {
                ProPromptSurface.SHEET -> it[SHEET_DISMISSALS] = (it[SHEET_DISMISSALS] ?: 0) + 1
                ProPromptSurface.CARD -> {
                    val count = cardDismissalsKey(trigger.id)
                    it[count] = (it[count] ?: 0) + 1
                    it[cardDismissedKey(trigger.id)] = clock.now()
                }
            }
        }
    }

    private companion object {
        val LAST_SHEET_AT = longPreferencesKey("pro_prompt_last_sheet_at")
        val SHEET_DISMISSALS = intPreferencesKey("pro_prompt_sheet_dismissals")
        val LAST_CARD_AT = longPreferencesKey("pro_prompt_last_card_at")
        val LAST_HARD_GATE_AT = longPreferencesKey("pro_prompt_last_hard_gate_at")
        val ENGAGED_EVENTS = intPreferencesKey("pro_prompt_engaged_events")
        val IMPORT_COUNT = intPreferencesKey("pro_prompt_import_count")

        fun cardDismissedKey(trigger: String) = longPreferencesKey("pro_prompt_card_dismissed_${trigger}_at")
        fun cardDismissalsKey(trigger: String) = intPreferencesKey("pro_prompt_card_dismissals_$trigger")
    }
}

/** Wall clock seam so tests can drive the cooldowns. */
fun interface ProPromptClock {
    fun now(): Long
}

object SystemProPromptClock : ProPromptClock {
    override fun now(): Long = System.currentTimeMillis()
}
