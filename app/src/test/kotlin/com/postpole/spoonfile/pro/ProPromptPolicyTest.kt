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

    private val cardCtx = sheetCtx.copy(recipeCount = 15, atRestPoint = true, sessionCount = 2)
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

    @Test fun card_showsFromSessionTwoAtRestPoint() {
        assertNull(eval(cardHistory, cardCtx.copy(sessionCount = 1)))
        assertEquals(ProPromptTrigger.RECIPES_MILESTONE, eval(cardHistory, cardCtx.copy(sessionCount = 2)))
    }

    @Test fun card_neverOnColdStart() {
        assertEquals(ProPromptTrigger.RECIPES_MILESTONE, eval(cardHistory, cardCtx))
        assertNull(eval(cardHistory, cardCtx.copy(atRestPoint = false)))
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

    // --- recipe_cap_near ---

    private val capCtx = cardCtx.copy(recipeCount = 65)
    private val importsHistory = ProPromptHistory(importCount = 3)
    private val importsCtx = cardCtx.copy(recipeCount = 5)

    @Test fun capNear_showsAtDefaultThresholdOf65_notBelow() {
        assertEquals(ProPromptTrigger.RECIPE_CAP_NEAR, eval(cardHistory, capCtx))
        assertEquals(ProPromptTrigger.RECIPES_MILESTONE, eval(cardHistory, capCtx.copy(recipeCount = 64)))
    }

    @Test fun capNear_neverAtTheHardCapItself() {
        assertNull(eval(cardHistory, capCtx.copy(recipeCount = 75, atLimit = true)))
    }

    @Test fun capNear_remoteConfigMovesTheThreshold() {
        val cfg = ProPromptConfig.of(true, 3, 15, 3, capNearCount = 70)
        assertEquals(ProPromptTrigger.RECIPES_MILESTONE, eval(cardHistory, capCtx, cfg))
        assertEquals(ProPromptTrigger.RECIPE_CAP_NEAR, eval(cardHistory, capCtx.copy(recipeCount = 70), cfg))
    }

    @Test fun capNear_dismissalCooldownAndStopApplyPerTrigger() {
        val id = ProPromptTrigger.RECIPE_CAP_NEAR.id
        val dismissed = cardHistory.copy(cardDismissedAt = mapOf(id to now - 13 * day), cardDismissals = mapOf(id to 1))
        // Cap-near is cooling down, so the next card in priority order is used.
        assertEquals(ProPromptTrigger.RECIPES_MILESTONE, eval(dismissed, capCtx))
        assertEquals(
            ProPromptTrigger.RECIPE_CAP_NEAR,
            eval(dismissed.copy(cardDismissedAt = mapOf(id to now - 14 * day)), capCtx),
        )
        val stopped = cardHistory.copy(cardDismissals = mapOf(id to 3), cardDismissedAt = mapOf(id to now - 90 * day))
        assertEquals(ProPromptTrigger.RECIPES_MILESTONE, eval(stopped, capCtx))
    }

    // --- imports_3 ---

    @Test fun imports_showsAtThirdImport_notBefore() {
        assertEquals(ProPromptTrigger.IMPORTS_3, eval(importsHistory, importsCtx))
        assertNull(eval(importsHistory.copy(importCount = 2), importsCtx))
    }

    @Test fun imports_needsARestPoint() {
        assertNull(eval(importsHistory, importsCtx.copy(atRestPoint = false)))
    }

    @Test fun imports_remoteConfigMovesTheThreshold() {
        val cfg = ProPromptConfig.of(true, 3, 15, 3, importCount = 5)
        assertNull(eval(importsHistory, importsCtx, cfg))
        assertEquals(ProPromptTrigger.IMPORTS_3, eval(importsHistory.copy(importCount = 5), importsCtx, cfg))
    }

    @Test fun imports_dismissalCooldownAndStop() {
        val id = ProPromptTrigger.IMPORTS_3.id
        val dismissed = importsHistory.copy(cardDismissedAt = mapOf(id to now - 14 * day + 1), cardDismissals = mapOf(id to 1))
        assertNull(eval(dismissed, importsCtx))
        assertEquals(ProPromptTrigger.IMPORTS_3, eval(dismissed.copy(cardDismissedAt = mapOf(id to now - 14 * day)), importsCtx))
        assertNull(eval(importsHistory.copy(cardDismissals = mapOf(id to 3), cardDismissedAt = mapOf(id to now - 90 * day)), importsCtx))
    }

    // --- priority ---

    @Test fun priority_capNearThenImportsThenMilestone() {
        val all = importsHistory
        assertEquals(ProPromptTrigger.RECIPE_CAP_NEAR, eval(all, capCtx))
        assertEquals(ProPromptTrigger.IMPORTS_3, eval(all, cardCtx))
        assertEquals(ProPromptTrigger.RECIPES_MILESTONE, eval(cardHistory, cardCtx))
    }

    @Test fun priority_aSheetBeatsEveryCard() {
        val both = sheetHistory.copy(importCount = 3)
        assertEquals(ProPromptTrigger.COOK_OR_SAVE_MILESTONE, eval(both, sheetCtx.copy(recipeCount = 65)))
    }

    // --- cap / cooldown interactions for the new cards ---

    @Test fun newCards_shareTheThreeDayCardIntervalAcrossTriggers() {
        // A milestone card shown 2 days ago holds back cap-near and imports too.
        val recent = importsHistory.copy(lastCardAt = now - 2 * day)
        assertNull(eval(recent, capCtx))
        assertNull(eval(recent, importsCtx))
    }

    @Test fun newCards_followTheGlobalRules() {
        for ((h, c) in listOf(cardHistory to capCtx, importsHistory to importsCtx)) {
            assertNull(eval(h, c.copy(isPro = true)))
            assertNull(eval(h, c, config.copy(enabled = false)))
            assertNull(eval(h, c.copy(sessionCount = 1)))
            assertNull(eval(h, c.copy(promptShownThisSession = true)))
            assertNull(eval(h, c.copy(interstitialShownThisSession = true)))
            assertNull(eval(h, c.copy(showBackupNudge = true)))
            assertNull(eval(h, c.copy(atLimit = true)))
            assertNull(eval(h.copy(lastHardGateAt = now - 47 * ProPromptPolicy.HOUR_MS), c))
            // Remove-ads owners still see cards.
            assertEquals(ProPromptPolicy.evaluate(config, h, c), ProPromptPolicy.evaluate(config, h, c.copy(removeAdsOwned = true)))
        }
    }

    @Test fun newCardConfigIsClamped() {
        assertEquals(55, ProPromptConfig.of(true, 3, 15, 3, capNearCount = 0).capNearCount)
        assertEquals(72, ProPromptConfig.of(true, 3, 15, 3, capNearCount = 75).capNearCount)
        assertEquals(2, ProPromptConfig.of(true, 3, 15, 3, importCount = 0).importCount)
        assertEquals(10, ProPromptConfig.of(true, 3, 15, 3, importCount = 99).importCount)
        assertEquals(65, ProPromptConfig.DEFAULTS.capNearCount)
        assertEquals(3, ProPromptConfig.DEFAULTS.importCount)
    }

    @Test fun newTriggerIdsAndSurfaces() {
        assertEquals("auto_recipe_cap_near", ProPromptTrigger.RECIPE_CAP_NEAR.proScreenTrigger)
        assertEquals("auto_imports_3", ProPromptTrigger.IMPORTS_3.proScreenTrigger)
        assertEquals("card", ProPromptTrigger.RECIPE_CAP_NEAR.surface.id)
        assertEquals("card", ProPromptTrigger.IMPORTS_3.surface.id)
    }

    @Test fun proScreenTriggerIsPrefixedWithAuto() {
        assertEquals("auto_recipes_milestone", ProPromptTrigger.RECIPES_MILESTONE.proScreenTrigger)
        assertEquals("auto_cook_or_save_milestone", ProPromptTrigger.COOK_OR_SAVE_MILESTONE.proScreenTrigger)
        assertEquals("card", ProPromptTrigger.RECIPES_MILESTONE.surface.id)
        assertEquals("sheet", ProPromptTrigger.COOK_OR_SAVE_MILESTONE.surface.id)
    }
}
