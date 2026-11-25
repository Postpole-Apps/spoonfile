package com.postpole.spoonfile.pro

/** Where an automatic Pro prompt appears. Values are the GA4 `surface` enum. */
enum class ProPromptSurface(val id: String) { CARD("card"), SHEET("sheet") }

/** What caused an automatic Pro prompt. [id] is the GA4 `trigger` enum; the Pro screen gets `auto_<id>`. */
enum class ProPromptTrigger(val id: String, val surface: ProPromptSurface) {
    RECIPES_MILESTONE("recipes_milestone", ProPromptSurface.CARD),
    COOK_OR_SAVE_MILESTONE("cook_or_save_milestone", ProPromptSurface.SHEET),
    ;

    /** The `trigger` value carried into the Pro screen, purchase-started and purchase events. */
    val proScreenTrigger: String get() = "auto_$id"
}

/** Remote Config keys for the automatic Pro prompts. */
object ProPromptConfigKeys {
    const val ENABLED = "pro_auto_prompt_enabled"
    const val MIN_SESSION = "pro_auto_prompt_min_session"
    const val RECIPE_COUNT = "pro_prompt_recipe_count"
    const val COOK_OR_IMPORT_COUNT = "pro_prompt_cook_or_import_count"
}

/** The Remote Config values behind the policy. Build from raw values with [of], which clamps to the allowed bounds. */
data class ProPromptConfig(
    val enabled: Boolean = true,
    val minSession: Int = DEFAULT_MIN_SESSION,
    val recipeCount: Int = DEFAULT_RECIPE_COUNT,
    val cookOrImportCount: Int = DEFAULT_COOK_OR_IMPORT_COUNT,
) {
    companion object {
        const val DEFAULT_MIN_SESSION = 3
        const val DEFAULT_RECIPE_COUNT = 15
        const val DEFAULT_COOK_OR_IMPORT_COUNT = 3
        val DEFAULTS = ProPromptConfig()

        fun of(enabled: Boolean, minSession: Long, recipeCount: Long, cookOrImportCount: Long) = ProPromptConfig(
            enabled = enabled,
            minSession = minSession.coerceIn(2L, Int.MAX_VALUE.toLong()).toInt(),
            recipeCount = recipeCount.coerceIn(10L, 40L).toInt(),
            cookOrImportCount = cookOrImportCount.coerceIn(2L, 10L).toInt(),
        )
    }

    fun toDefaultsMap(): Map<String, Any> = mapOf(
        ProPromptConfigKeys.ENABLED to enabled,
        ProPromptConfigKeys.MIN_SESSION to minSession.toLong(),
        ProPromptConfigKeys.RECIPE_COUNT to recipeCount.toLong(),
        ProPromptConfigKeys.COOK_OR_IMPORT_COUNT to cookOrImportCount.toLong(),
    )
}

/** What the caps need to remember between launches (persisted in DataStore). */
data class ProPromptHistory(
    val lastSheetAt: Long? = null,
    val sheetDismissals: Int = 0,
    val lastCardAt: Long? = null,
    /** Per trigger id: when the card was last dismissed. */
    val cardDismissedAt: Map<String, Long> = emptyMap(),
    /** Per trigger id: how many times the card was dismissed. */
    val cardDismissals: Map<String, Int> = emptyMap(),
    val lastHardGateAt: Long? = null,
    /** Cook-mode completions + recipe saves since the last sheet (a whole-file import counts once). */
    val engagedEvents: Int = 0,
)

/** The live situation the policy is asked about. */
data class ProPromptContext(
    val now: Long,
    val isPro: Boolean,
    val removeAdsOwned: Boolean,
    /** App sessions (process starts), including the current one. */
    val sessionCount: Int,
    val installAgeDays: Int,
    val recipeCount: Int,
    val atLimit: Boolean,
    val showBackupNudge: Boolean,
    /** An auto prompt was already shown in this session. */
    val promptShownThisSession: Boolean,
    /** An interstitial was already shown in this session. */
    val interstitialShownThisSession: Boolean,
    /** The user just came back to the Recipes list from another screen (never a cold start). */
    val atRestPoint: Boolean,
)

/** Pure rules for the automatic Pro prompts. Drive it with a fake clock via [ProPromptContext.now]. */
object ProPromptPolicy {
    const val HOUR_MS = 60L * 60 * 1000
    const val DAY_MS = 24 * HOUR_MS
    const val HARD_GATE_QUIET_MS = 48 * HOUR_MS
    const val SHEET_INTERVAL_MS = 7 * DAY_MS
    const val CARD_INTERVAL_MS = 3 * DAY_MS
    const val CARD_COOLDOWN_MS = 14 * DAY_MS
    const val MAX_DISMISSALS = 3
    const val MIN_INSTALL_AGE_DAYS = 2
    const val MIN_CARD_SESSION = 2

    /** The prompt to show now, or null. A sheet wins over a card (at most one per session either way). */
    fun evaluate(config: ProPromptConfig, history: ProPromptHistory, ctx: ProPromptContext): ProPromptTrigger? {
        if (ctx.isPro || !config.enabled) return null
        if (ctx.sessionCount <= 1) return null
        if (ctx.promptShownThisSession || ctx.interstitialShownThisSession) return null
        val gate = history.lastHardGateAt
        // A clock that moved backwards counts as "too soon".
        if (gate != null && ctx.now - gate < HARD_GATE_QUIET_MS) return null
        if (sheetEligible(config, history, ctx)) return ProPromptTrigger.COOK_OR_SAVE_MILESTONE
        if (cardEligible(config, history, ctx)) return ProPromptTrigger.RECIPES_MILESTONE
        return null
    }

    fun sheetEligible(config: ProPromptConfig, history: ProPromptHistory, ctx: ProPromptContext): Boolean {
        if (ctx.removeAdsOwned || !ctx.atRestPoint) return false
        if (ctx.sessionCount < config.minSession || ctx.installAgeDays < MIN_INSTALL_AGE_DAYS) return false
        if (history.sheetDismissals >= MAX_DISMISSALS) return false
        val last = history.lastSheetAt
        if (last != null && ctx.now - last < SHEET_INTERVAL_MS) return false
        return history.engagedEvents >= config.cookOrImportCount
    }

    fun cardEligible(config: ProPromptConfig, history: ProPromptHistory, ctx: ProPromptContext): Boolean {
        val id = ProPromptTrigger.RECIPES_MILESTONE.id
        if (ctx.sessionCount < MIN_CARD_SESSION) return false
        if (ctx.showBackupNudge || ctx.atLimit) return false
        if ((history.cardDismissals[id] ?: 0) >= MAX_DISMISSALS) return false
        val last = history.lastCardAt
        if (last != null && ctx.now - last < CARD_INTERVAL_MS) return false
        val dismissed = history.cardDismissedAt[id]
        if (dismissed != null && ctx.now - dismissed < CARD_COOLDOWN_MS) return false
        return ctx.recipeCount >= config.recipeCount
    }
}
