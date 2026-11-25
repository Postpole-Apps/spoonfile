package com.postpole.spoonfile.promo

import java.util.concurrent.TimeUnit

/** The two automatic Pro prompts. [wire] and [surface] are the enum values sent to analytics. */
enum class ProPromptTrigger(val wire: String, val surface: String) {
    RECIPES_MILESTONE("recipes_milestone", "card"),
    COOK_OR_SAVE_MILESTONE("cook_or_save_milestone", "sheet"),
    ;

    /** Value of the `trigger` param on `pro_screen_view`, `purchase_started` and `purchase` after the CTA. */
    val proScreenTrigger: String get() = "auto_$wire"
}

/** Remote Config keys for the automatic Pro prompts. */
object ProPromptConfigKeys {
    const val ENABLED = "pro_auto_prompt_enabled"
    const val MIN_SESSION = "pro_auto_prompt_min_session"
    const val RECIPE_COUNT = "pro_prompt_recipe_count"
    const val COOK_OR_IMPORT_COUNT = "pro_prompt_cook_or_import_count"
}

/** Remote Config values. Out-of-range values are clamped by [sanitized]. */
data class ProPromptConfig(
    val enabled: Boolean = true,
    val minSession: Int = 3,
    val recipeCount: Int = 15,
    val cookOrImportCount: Int = 3,
) {
    fun sanitized() = copy(
        minSession = minSession.coerceAtLeast(MIN_SESSION_FLOOR),
        recipeCount = recipeCount.coerceIn(RECIPE_COUNT_RANGE),
        cookOrImportCount = cookOrImportCount.coerceIn(COOK_OR_IMPORT_RANGE),
    )

    companion object {
        const val MIN_SESSION_FLOOR = 2
        val RECIPE_COUNT_RANGE = 10..40
        val COOK_OR_IMPORT_RANGE = 2..10
    }
}

/** What is persisted between sessions. */
data class ProPromptHistory(
    val lastSheetAt: Long? = null,
    val sheetDismissals: Int = 0,
    val lastCardAt: Long? = null,
    val cardDismissedAt: Map<ProPromptTrigger, Long> = emptyMap(),
    val cardDismissals: Map<ProPromptTrigger, Int> = emptyMap(),
    val lastHardGateAt: Long? = null,
    /** Cook-mode completions + recipe saves since the last sheet. A whole-file / bulk import is one. */
    val engagedEvents: Int = 0,
)

data class ProPromptInputs(
    val now: Long,
    val isPro: Boolean,
    val removeAdsOwned: Boolean,
    /** Process starts including this one; 1 is the first session. */
    val sessionCount: Int,
    val installAgeDays: Int,
    val recipeCount: Int,
    val atLimit: Boolean,
    val showBackupNudge: Boolean,
    /** The list was just reached by returning from another screen (not a cold start). */
    val restPoint: Boolean,
    /** An auto prompt was already chosen in this session. */
    val promptShownThisSession: Boolean,
    val interstitialShownThisSession: Boolean,
    val history: ProPromptHistory = ProPromptHistory(),
)

/** Pure studio rules for the automatic Pro prompts. */
object ProPromptPolicy {
    private val DAY = TimeUnit.DAYS.toMillis(1)
    const val MAX_SHEET_DISMISSALS = 3
    const val MAX_CARD_DISMISSALS = 3
    const val MIN_INSTALL_AGE_DAYS = 2
    val SHEET_INTERVAL_MS = 7 * DAY
    val CARD_INTERVAL_MS = 3 * DAY
    val CARD_COOLDOWN_MS = 14 * DAY
    val HARD_GATE_QUIET_MS = TimeUnit.HOURS.toMillis(48)

    /** The prompt to show now, or null. At most one; the sheet wins when both qualify. */
    fun evaluate(config: ProPromptConfig, inputs: ProPromptInputs): ProPromptTrigger? {
        val c = config.sanitized()
        val i = inputs
        val h = i.history
        if (i.isPro || !c.enabled) return null
        if (i.sessionCount <= 1) return null
        if (i.promptShownThisSession || i.interstitialShownThisSession) return null
        if (h.lastHardGateAt != null && i.now - h.lastHardGateAt < HARD_GATE_QUIET_MS) return null
        if (sheetEligible(c, i)) return ProPromptTrigger.COOK_OR_SAVE_MILESTONE
        if (cardEligible(c, i)) return ProPromptTrigger.RECIPES_MILESTONE
        return null
    }

    private fun sheetEligible(c: ProPromptConfig, i: ProPromptInputs): Boolean {
        val h = i.history
        return i.restPoint &&
            !i.removeAdsOwned &&
            i.sessionCount >= c.minSession &&
            i.installAgeDays >= MIN_INSTALL_AGE_DAYS &&
            h.sheetDismissals < MAX_SHEET_DISMISSALS &&
            (h.lastSheetAt == null || i.now - h.lastSheetAt >= SHEET_INTERVAL_MS) &&
            h.engagedEvents >= c.cookOrImportCount
    }

    private fun cardEligible(c: ProPromptConfig, i: ProPromptInputs): Boolean {
        val h = i.history
        val trigger = ProPromptTrigger.RECIPES_MILESTONE
        val dismissedAt = h.cardDismissedAt[trigger]
        return i.sessionCount >= 2 &&
            (h.cardDismissals[trigger] ?: 0) < MAX_CARD_DISMISSALS &&
            (h.lastCardAt == null || i.now - h.lastCardAt >= CARD_INTERVAL_MS) &&
            (dismissedAt == null || i.now - dismissedAt >= CARD_COOLDOWN_MS) &&
            i.recipeCount >= c.recipeCount &&
            !i.showBackupNudge &&
            !i.atLimit
    }
}
