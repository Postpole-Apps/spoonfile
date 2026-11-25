package com.postpole.spoonfile.pro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProPromptPolicyTest {
    private val day = ProPromptPolicy.DAY_MS
    private val now = 100 * day
    private val config = ProPromptConfig.DEFAULTS

    private val sheetCtx = ProPromptContext(
        now = now, isPro = false, removeAdsOwned = false, sessionCount = 3, installAgeDays = 5,
        recipeCount = 5, atLimit = false, showBackupNudge = false,
        promptShownThisSession = false, interstitialShownThisSession = false, atRestPoint = true,
    )
    private val sheetHistory = ProPromptHistory(engagedEvents = 3)

    private val cardCtx = sheetCtx.copy(recipeCount = 15, atRestPoint = false, sessionCount = 2)
    private val cardHistory = ProPromptHistory()

    private fun eval(h: ProPromptHistory, c: ProPromptContext, cfg: ProPromptConfig = config) =
        ProPromptPolicy.evaluate(cfg, h, c)

    // --- sheet ---

    @Test fun sheet_showsAtRestPointOnceEngagedEventsReachTheThreshold() {
        assertEquals(ProPromptTrigger.COOK_OR_SAVE_MILESTONE, eval(sheetHistory, sheetCtx))
        assertNull(eval(ProPromptHistory(engagedEvents = 2), sheetCtx))
    }

    @Test fun sheet_neverOnColdStart() {
        assertNull(eval(sheetHistory, sheetCtx.copy(atRestPoint = false)))
    }

    @Test fun sheet_needsSessionAtLeastMinSessionAndInstallAgeTwoDays() {
        assertNull(eval(sheetHistory, sheetCtx.copy(sessionCount = 2)))
        assertNull(eval(sheetHistory, sheetCtx.copy(installAgeDays = 1)))
        assertEquals(ProPromptTrigger.COOK_OR_SAVE_MILESTONE, eval(sheetHistory, sheetCtx.copy(sessionCount = 3, installAgeDays = 2)))
    }

    @Test fun sheet_sevenDayCooldown() {
        val recent = sheetHistory.copy(lastSheetAt = now - 7 * day + 1)
        assertNull(eval(recent, sheetCtx))
        assertEquals(ProPromptTrigger.COOK_OR_SAVE_MILESTONE, eval(sheetHistory.copy(lastSheetAt = now - 7 * day), sheetCtx))
    }

    @Test fun sheet_stopsAfterThreeDismissals() {
        assertEquals(ProPromptTrigger.COOK_OR_SAVE_MILESTONE, eval(sheetHistory.copy(sheetDismissals = 2), sheetCtx))
        assertNull(eval(sheetHistory.copy(sheetDismissals = 3), sheetCtx))
    }

    @Test fun sheet_neverForRemoveAdsOwners_butTheyGetTheCard() {
        val owner = sheetCtx.copy(removeAdsOwned = true)
        assertNull(eval(sheetHistory, owner))
        val ownerCard = owner.copy(recipeCount = 15)
        assertEquals(ProPromptTrigger.RECIPES_MILESTONE, eval(sheetHistory, ownerCard))
    }

    // --- card ---

    @Test fun card_showsAtRecipeThreshold() {
        assertEquals(ProPromptTrigger.RECIPES_MILESTONE, eval(cardHistory, cardCtx))
        assertNull(eval(cardHistory, cardCtx.copy(recipeCount = 14)))
    }

    @Test fun card_showsOnColdStartFromSessionTwo() {
        assertNull(eval(cardHistory, cardCtx.copy(sessionCount = 1)))
        assertEquals(ProPromptTrigger.RECIPES_MILESTONE, eval(cardHistory, cardCtx.copy(sessionCount = 2, atRestPoint = false)))
    }

    @Test fun card_hiddenWhileLimitOrBackupBannerShows() {
        assertNull(eval(cardHistory, cardCtx.copy(atLimit = true)))
        assertNull(eval(cardHistory, cardCtx.copy(showBackupNudge = true)))
    }

    @Test fun card_threeDayIntervalAndFourteenDayCooldownAfterDismissal() {
        val id = ProPromptTrigger.RECIPES_MILESTONE.id
        assertNull(eval(cardHistory.copy(lastCardAt = now - 3 * day + 1), cardCtx))
        assertEquals(ProPromptTrigger.RECIPES_MILESTONE, eval(cardHistory.copy(lastCardAt = now - 3 * day), cardCtx))
        val dismissed = cardHistory.copy(
            lastCardAt = now - 10 * day,
            cardDismissedAt = mapOf(id to now - 14 * day + 1),
            cardDismissals = mapOf(id to 1),
        )
        assertNull(eval(dismissed, cardCtx))
        assertEquals(
            ProPromptTrigger.RECIPES_MILESTONE,
            eval(dismissed.copy(cardDismissedAt = mapOf(id to now - 14 * day)), cardCtx),
        )
    }

    @Test fun card_stopsAfterThreeDismissalsOfThatTrigger() {
        val id = ProPromptTrigger.RECIPES_MILESTONE.id
        val twice = cardHistory.copy(cardDismissals = mapOf(id to 2), cardDismissedAt = mapOf(id to now - 30 * day))
        assertEquals(ProPromptTrigger.RECIPES_MILESTONE, eval(twice, cardCtx))
        assertNull(eval(twice.copy(cardDismissals = mapOf(id to 3)), cardCtx))
    }

    // --- global rules ---

    @Test fun neverForProOrWhenKillSwitchIsOff() {
        assertNull(eval(sheetHistory, sheetCtx.copy(isPro = true)))
        assertNull(eval(cardHistory, cardCtx.copy(isPro = true)))
        assertNull(eval(sheetHistory, sheetCtx, config.copy(enabled = false)))
        assertNull(eval(cardHistory, cardCtx, config.copy(enabled = false)))
    }

    @Test fun neverInSessionOne() {
        assertNull(eval(sheetHistory, sheetCtx.copy(sessionCount = 1)))
        assertNull(eval(cardHistory, cardCtx.copy(sessionCount = 1)))
    }

    @Test fun maxOnePerSession_andAnInterstitialAlreadyShownSuppresses() {
        assertNull(eval(sheetHistory, sheetCtx.copy(promptShownThisSession = true)))
        assertNull(eval(cardHistory, cardCtx.copy(promptShownThisSession = true)))
        assertNull(eval(sheetHistory, sheetCtx.copy(interstitialShownThisSession = true)))
        assertNull(eval(cardHistory, cardCtx.copy(interstitialShownThisSession = true)))
    }

    @Test fun hardGateSilencesPromptsFor48Hours() {
        val hours = ProPromptPolicy.HOUR_MS
        assertNull(eval(sheetHistory.copy(lastHardGateAt = now - 48 * hours + 1), sheetCtx))
        assertNull(eval(cardHistory.copy(lastHardGateAt = now - 1 * hours), cardCtx))
        assertEquals(
            ProPromptTrigger.COOK_OR_SAVE_MILESTONE,
            eval(sheetHistory.copy(lastHardGateAt = now - 48 * hours), sheetCtx),
        )
        // A clock that moved backwards counts as too soon.
        assertNull(eval(sheetHistory.copy(lastHardGateAt = now + hours), sheetCtx))
    }

    @Test fun sheetWinsOverCardWhenBothAreEligible() {
        assertEquals(ProPromptTrigger.COOK_OR_SAVE_MILESTONE, eval(sheetHistory, sheetCtx.copy(recipeCount = 20)))
    }

    @Test fun cardStillShowsWhenOnlyTheSheetIsOnCooldown() {
        val h = sheetHistory.copy(lastSheetAt = now - day)
        assertEquals(ProPromptTrigger.RECIPES_MILESTONE, eval(h, sheetCtx.copy(recipeCount = 20)))
    }

    // --- Remote Config overrides ---

    @Test fun configIsClampedToItsBounds() {
        assertEquals(ProPromptConfig(true, 2, 10, 2), ProPromptConfig.of(true, 0, 0, 0))
        assertEquals(ProPromptConfig(true, 9, 40, 10), ProPromptConfig.of(true, 9, 500, 99))
        assertEquals(ProPromptConfig(false, 3, 15, 3), ProPromptConfig.of(false, 3, 15, 3))
    }

    @Test fun overridesMoveTheThresholds() {
        val cfg = ProPromptConfig.of(true, minSession = 5, recipeCount = 25, cookOrImportCount = 6)
        assertNull(eval(ProPromptHistory(engagedEvents = 3), sheetCtx, cfg))
        assertNull(eval(ProPromptHistory(engagedEvents = 6), sheetCtx.copy(sessionCount = 4), cfg))
        assertEquals(ProPromptTrigger.COOK_OR_SAVE_MILESTONE, eval(ProPromptHistory(engagedEvents = 6), sheetCtx.copy(sessionCount = 5), cfg))
        assertNull(eval(cardHistory, cardCtx, cfg))
        assertEquals(ProPromptTrigger.RECIPES_MILESTONE, eval(cardHistory, cardCtx.copy(recipeCount = 25), cfg))
    }

    @Test fun proScreenTriggerIsPrefixedWithAuto() {
        assertEquals("auto_recipes_milestone", ProPromptTrigger.RECIPES_MILESTONE.proScreenTrigger)
        assertEquals("auto_cook_or_save_milestone", ProPromptTrigger.COOK_OR_SAVE_MILESTONE.proScreenTrigger)
        assertEquals("card", ProPromptTrigger.RECIPES_MILESTONE.surface.id)
        assertEquals("sheet", ProPromptTrigger.COOK_OR_SAVE_MILESTONE.surface.id)
    }
}
