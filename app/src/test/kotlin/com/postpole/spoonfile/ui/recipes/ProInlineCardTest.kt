package com.postpole.spoonfile.ui.recipes

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.postpole.spoonfile.pro.ProPromptTrigger
import com.postpole.spoonfile.ui.theme.SpoonfileTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders each inline Pro card and checks its copy, price and buttons. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProInlineCardTest {
    @get:Rule val rule = createComposeRule()

    private fun show(trigger: ProPromptTrigger, price: String?, onSeePro: () -> Unit = {}, onNotNow: () -> Unit = {}) {
        rule.setContent {
            SpoonfileTheme { ProInlineCard(price = price, onSeePro = onSeePro, onNotNow = onNotNow, trigger = trigger) }
        }
    }

    @Test fun capNearCard_showsItsCopyAndThePlayPrice() {
        show(ProPromptTrigger.RECIPE_CAP_NEAR, "$4.99")
        rule.onNodeWithText("Your recipe box is filling up").assertIsDisplayed()
        rule.onNodeWithText("Keep saving without a limit with Spoonfile Pro. Pay once — $4.99.").assertIsDisplayed()
        rule.onNodeWithText("See Pro").assertIsDisplayed()
        rule.onNodeWithText("Not now").assertIsDisplayed()
    }

    @Test fun importsCard_showsItsCopy_andNoPriceFallback() {
        show(ProPromptTrigger.IMPORTS_3, null)
        rule.onNodeWithText("Bringing recipes in? Unlock Spoonfile Pro").assertIsDisplayed()
        rule.onNodeWithText("Import a whole recipe file in one go, and keep unlimited recipes. Pay once.").assertIsDisplayed()
    }

    @Test fun milestoneCard_keepsItsOriginalCopy() {
        show(ProPromptTrigger.RECIPES_MILESTONE, null)
        rule.onNodeWithText("Growing recipe box? Unlock Spoonfile Pro").assertIsDisplayed()
    }

    @Test fun buttonsReportTheirTaps() {
        var seePro = 0
        var notNow = 0
        show(ProPromptTrigger.IMPORTS_3, null, onSeePro = { seePro++ }, onNotNow = { notNow++ })
        rule.onNodeWithText("See Pro").performClick()
        rule.onNodeWithText("Not now").performClick()
        assertEquals(1, seePro)
        assertEquals(1, notNow)
    }
}
