package com.postpole.spoonfile.ui.recipes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.postpole.spoonfile.ui.theme.SpoonfileTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TodayPlanCardTest {
    @get:Rule val rule = createComposeRule()

    private fun items(n: Int) = (1..n).map { TodayPlanItem(entryId = it.toLong(), recipeId = 100L + it, title = "Dish $it") }

    private fun show(
        items: List<TodayPlanItem>,
        fontScale: Float = 1f,
        onOpenRecipe: (Long) -> Unit = {},
        onCook: (Long) -> Unit = {},
        onSeeWeek: () -> Unit = {},
    ) {
        rule.setContent {
            Themed(fontScale) { TodayPlanCard(items, onOpenRecipe, onCook, onSeeWeek) }
        }
    }

    @Composable
    private fun Themed(fontScale: Float, content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = fontScale)) {
            SpoonfileTheme(darkTheme = false) { content() }
        }
    }

    @Test
    fun fiveItems_showsThreeAndPlusTwoMore() {
        show(items(5))
        rule.onNodeWithText("Dish 1").assertIsDisplayed()
        rule.onNodeWithText("Dish 2").assertIsDisplayed()
        rule.onNodeWithText("Dish 3").assertIsDisplayed()
        rule.onNodeWithText("Dish 4").assertDoesNotExist()
        rule.onNodeWithText("+2 more").assertIsDisplayed()
    }

    @Test
    fun cookButton_hasDescriptionWithTitle_and48dp() {
        show(items(1))
        rule.onNodeWithContentDescription("Start cook mode for Dish 1")
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
    }

    @Test
    fun header_isHeading() {
        show(items(1))
        rule.onNodeWithText("Today's plan")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun fontScale2_longTitle_cookButtonVisible() {
        val long = "A very long recipe title that keeps going and going so that it has to wrap onto more than one line"
        show(listOf(TodayPlanItem(1, 101, long)), fontScale = 2f)
        rule.onNodeWithContentDescription("Start cook mode for $long").assertIsDisplayed()
        rule.onNodeWithText("See the week").assertIsDisplayed()
    }

    @Test
    fun tapRow_callsOpenRecipe_tapCook_callsCook_tapSeeWeek_callsWeek() {
        val opened = mutableListOf<Long>()
        val cooked = mutableListOf<Long>()
        var week = 0
        show(items(2), onOpenRecipe = { opened += it }, onCook = { cooked += it }, onSeeWeek = { week++ })

        rule.onNodeWithText("Dish 1").performClick()
        rule.onNodeWithContentDescription("Start cook mode for Dish 2").performClick()
        rule.onNodeWithText("See the week").performClick()

        assertEquals(listOf(101L), opened)
        assertEquals(listOf(102L), cooked)
        assertEquals(1, week)
    }
}
