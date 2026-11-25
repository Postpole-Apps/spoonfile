package com.postpole.spoonfile.promo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.TimeUnit

class ProPromptPolicyTest {
    private val day = TimeUnit.DAYS.toMillis(1)
    private val hour = TimeUnit.HOURS.toMillis(1)
    private val now = 100 * day
    private val config = ProPromptConfig()

    private val sheet = ProPromptTrigger.COOK_OR_SAVE_MILESTONE
    private val card = ProPromptTrigger.RECIPES_MILESTONE

    /** Qualifies for the sheet by default; the card needs [recipes] >= 15. */
    private fun inputs(
        isPro: Boolean = false,
        removeAdsOwned: Boolean = false,
        session: Int = 3,
        installAgeDays: Int = 5,
        recipes: Int = 5,
        atLimit: Boolean = false,
        nudge: Boolean = false,
        restPoint: Boolean = true,
        promptShown: Boolean = false,
        interstitialShown: Boolean = false,
        history: ProPromptHistory = ProPromptHistory(engagedEvents = 3),
    ) = ProPromptInputs(
        now, isPro, removeAdsOwned, session, installAgeDays, recipes, atLimit, nudge, restPoint,
        promptShown, interstitialShown, history,
    )

    private fun eval(i: ProPromptInputs, c: ProPromptConfig = config) = ProPromptPolicy.evaluate(c, i)

    // ---- global rules ----

    @Test
    fun sheetShowsWhenEverythingQualifies() = assertEquals(sheet, eval(inputs()))

    @Test
    fun neverForPro() {
        assertNull(eval(inputs(isPro = true)))
        assertNull(eval(inputs(isPro = true, recipes = 20, history = ProPromptHistory())))
    }

    @Test
    fun killSwitchDisablesBoth() {
        assertNull(eval(inputs(), config.copy(enabled = false)))
        assertNull(eval(inputs(recipes = 20, history = ProPromptHistory()), config.copy(enabled = false)))
    }

    @Test
    fun neverInFirstSession() {
        assertNull(eval(inputs(session = 1)))
        assertNull(eval(inputs(session = 1, recipes = 20, history = ProPromptHistory())))
    }

    @Test
    fun atMostOnePerSession() {
        assertNull(eval(inputs(promptShown = true)))
        assertNull(eval(inputs(promptShown = true, recipes = 20, history = ProPromptHistory())))
    }

    @Test
    fun interstitialShownThisSessionSuppressesPrompts() {
        assertNull(eval(inputs(interstitialShown = true)))
        assertNull(eval(inputs(interstitialShown = true, recipes = 20, history = ProPromptHistory())))
    }

    @Test
    fun hardGateQuietForFortyEightHours() {
        val gated = { ago: Long -> ProPromptHistory(engagedEvents = 3, lastHardGateAt = now - ago) }
        assertNull(eval(inputs(history = gated(47 * hour))))
        assertNull(eval(inputs(recipes = 20, history = gated(1 * hour).copy(engagedEvents = 0))))
        assertEquals(sheet, eval(inputs(history = gated(48 * hour))))
    }

    // ---- sheet: cook_or_save_milestone ----

    @Test
    fun sheetNeedsMinSession() {
        assertNull(eval(inputs(session = 2)))
        assertEquals(sheet, eval(inputs(session = 3)))
    }

    @Test
    fun sheetMinSessionFromRemoteConfigIsClampedToTwo() {
        // 0 and 1 clamp up to 2, so session 2 passes and session 1 is still blocked by the first-session rule.
        assertEquals(sheet, eval(inputs(session = 2), config.copy(minSession = 0)))
        assertNull(eval(inputs(session = 1), config.copy(minSession = 0)))
        assertNull(eval(inputs(session = 4), config.copy(minSession = 5)))
        assertEquals(sheet, eval(inputs(session = 5), config.copy(minSession = 5)))
    }

    @Test
    fun sheetNeedsTwoInstallDays() {
        assertNull(eval(inputs(installAgeDays = 1)))
        assertEquals(sheet, eval(inputs(installAgeDays = 2)))
    }

    @Test
    fun sheetNeedsEngagedEventsAtThreshold() {
        assertNull(eval(inputs(history = ProPromptHistory(engagedEvents = 2))))
        assertEquals(sheet, eval(inputs(history = ProPromptHistory(engagedEvents = 3))))
    }

    @Test
    fun sheetThresholdFromRemoteConfigIsClamped() {
        // 1 clamps up to 2; 50 clamps down to 10.
        assertEquals(sheet, eval(inputs(history = ProPromptHistory(engagedEvents = 2)), config.copy(cookOrImportCount = 1)))
        assertNull(eval(inputs(history = ProPromptHistory(engagedEvents = 9)), config.copy(cookOrImportCount = 50)))
        assertEquals(sheet, eval(inputs(history = ProPromptHistory(engagedEvents = 10)), config.copy(cookOrImportCount = 50)))
    }

    @Test
    fun sheetOnlyAtARestPoint() = assertNull(eval(inputs(restPoint = false)))

    @Test
    fun sheetOncePerSevenDays() {
        val h = { ago: Long -> ProPromptHistory(engagedEvents = 3, lastSheetAt = now - ago) }
        assertNull(eval(inputs(history = h(7 * day - 1))))
        assertEquals(sheet, eval(inputs(history = h(7 * day))))
    }

    @Test
    fun threeSheetDismissalsStopSheets() {
        assertEquals(sheet, eval(inputs(history = ProPromptHistory(engagedEvents = 3, sheetDismissals = 2))))
        assertNull(eval(inputs(history = ProPromptHistory(engagedEvents = 3, sheetDismissals = 3))))
    }

    @Test
    fun sheetStopForeverDoesNotBlockTheCard() {
        val h = ProPromptHistory(engagedEvents = 3, sheetDismissals = 3)
        assertEquals(card, eval(inputs(recipes = 15, history = h)))
    }

    // ---- remove_ads owners: card only ----

    @Test
    fun removeAdsOwnerNeverGetsTheSheet() {
        assertNull(eval(inputs(removeAdsOwned = true)))
    }

    @Test
    fun removeAdsOwnerStillGetsTheCard() {
        assertEquals(card, eval(inputs(removeAdsOwned = true, recipes = 15)))
    }

    // ---- card: recipes_milestone ----

    private val noEngagement = ProPromptHistory()

    @Test
    fun cardAtRecipeThreshold() {
        assertNull(eval(inputs(recipes = 14, history = noEngagement)))
        assertEquals(card, eval(inputs(recipes = 15, history = noEngagement)))
    }

    @Test
    fun cardThresholdFromRemoteConfigIsClamped() {
        // 3 clamps up to 10; 99 clamps down to 40.
        assertEquals(card, eval(inputs(recipes = 10, history = noEngagement), config.copy(recipeCount = 3)))
        assertNull(eval(inputs(recipes = 9, history = noEngagement), config.copy(recipeCount = 3)))
        assertNull(eval(inputs(recipes = 39, history = noEngagement), config.copy(recipeCount = 99)))
        assertEquals(card, eval(inputs(recipes = 40, history = noEngagement), config.copy(recipeCount = 99)))
    }

    @Test
    fun cardAllowedFromSessionTwoAndOnColdStart() {
        assertEquals(card, eval(inputs(session = 2, recipes = 15, restPoint = false, installAgeDays = 0, history = noEngagement)))
    }

    @Test
    fun cardHiddenWhileLimitOrBackupBannerShows() {
        assertNull(eval(inputs(recipes = 15, atLimit = true, history = noEngagement)))
        assertNull(eval(inputs(recipes = 15, nudge = true, history = noEngagement)))
    }

    @Test
    fun cardOncePerThreeDays() {
        val h = { ago: Long -> ProPromptHistory(lastCardAt = now - ago) }
        assertNull(eval(inputs(recipes = 15, history = h(3 * day - 1))))
        assertEquals(card, eval(inputs(recipes = 15, history = h(3 * day))))
    }

    @Test
    fun cardCooldownFourteenDaysAfterDismissal() {
        val h = { ago: Long -> ProPromptHistory(cardDismissedAt = mapOf(card to now - ago), cardDismissals = mapOf(card to 1)) }
        assertNull(eval(inputs(recipes = 15, history = h(14 * day - 1))))
        assertEquals(card, eval(inputs(recipes = 15, history = h(14 * day))))
    }

    @Test
    fun threeCardDismissalsStopTheCardTrigger() {
        val h = ProPromptHistory(cardDismissedAt = mapOf(card to now - 90 * day), cardDismissals = mapOf(card to 3))
        assertNull(eval(inputs(recipes = 15, history = h)))
        assertEquals(card, eval(inputs(recipes = 15, history = h.copy(cardDismissals = mapOf(card to 2)))))
    }

    // ---- precedence ----

    @Test
    fun sheetWinsWhenBothQualify() = assertEquals(sheet, eval(inputs(recipes = 20)))

    @Test
    fun bulkImportIsOneEvent() {
        // The store increments once per whole-file import: one event is below the default threshold of 3.
        assertNull(eval(inputs(history = ProPromptHistory(engagedEvents = 1))))
    }

    // ---- analytics enums ----

    @Test
    fun proScreenTriggerIsAutoPrefixed() {
        assertEquals("auto_recipes_milestone", card.proScreenTrigger)
        assertEquals("auto_cook_or_save_milestone", sheet.proScreenTrigger)
        assertEquals("card", card.surface)
        assertEquals("sheet", sheet.surface)
    }
}
