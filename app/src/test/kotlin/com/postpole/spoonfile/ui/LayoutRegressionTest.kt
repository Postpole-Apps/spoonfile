package com.postpole.spoonfile.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.postpole.spoonfile.R
import com.postpole.spoonfile.importer.ocr.LineRole
import com.postpole.spoonfile.ui.importer.RoleGroup
import com.postpole.spoonfile.ui.importer.labelRes
import com.postpole.spoonfile.ui.recipes.AddRecipeSheetContent
import com.postpole.spoonfile.ui.recipes.CategoryTile
import com.postpole.spoonfile.ui.theme.SpoonfilePolygons
import com.postpole.spoonfile.ui.theme.SpoonfileTheme
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Layout regression checks for the pre-closed-testing UI fixes: category tile labels, scan role
 * labels and the add-recipe sheet footer. They run on the JVM (Robolectric) and assert on the
 * measured text layout, so a wrapped, split or clipped label fails the build.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE) // real font metrics; the legacy mode measures every glyph as ~1px
class LayoutRegressionTest {
    @get:Rule val rule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun layoutOf(node: SemanticsNode): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.first()
    }

    private fun assertShownInFull(text: String) {
        val node = rule.onNodeWithText(text).fetchSemanticsNode()
        val layout = layoutOf(node)
        assertEquals("'$text' must stay on one line", 1, layout.lineCount)
        // softWrap=false lays text out unbounded, so compare the real text width with the space it was given.
        assertFalse("'$text' must not overflow vertically", layout.didOverflowHeight)
        assertTrue(
            "'$text' (${layout.multiParagraph.maxIntrinsicWidth}px) must fit its ${layout.layoutInput.constraints.maxWidth}px slot",
            layout.multiParagraph.maxIntrinsicWidth <= layout.layoutInput.constraints.maxWidth,
        )
        assertEquals("'$text' must not be truncated", text.length, layout.getLineEnd(0, visibleEnd = true))
    }

    private fun withFontScale(scale: Float, content: @Composable () -> Unit) {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = scale)) {
                SpoonfileTheme(darkTheme = false) { content() }
            }
        }
    }

    // 1. Category tiles

    private fun breakfastTile(scale: Float) {
        withFontScale(scale) {
            CategoryTile(label = "Breakfast", shape = SpoonfilePolygons.categoryTiles[0], selected = false, onClick = {})
        }
        assertShownInFull("Breakfast")
    }

    @Test fun categoryTile_breakfastIsOneUnsplitLine_at1x() = breakfastTile(1.0f)

    @Test fun categoryTile_breakfastIsOneUnsplitLine_at1_3x() = breakfastTile(1.3f)

    @Test fun categoryTile_longLabelEllipsizesOnOneLine_neverWraps() {
        withFontScale(1.0f) {
            CategoryTile(label = "Slow cooker weeknight dinners", shape = SpoonfilePolygons.categoryTiles[0], selected = true, onClick = {})
        }
        val layout = layoutOf(rule.onNodeWithText("Slow cooker weeknight dinners").fetchSemanticsNode())
        assertEquals(1, layout.lineCount)
    }

    // 3. Scan role labels

    private fun roleLabels(scale: Float) {
        withFontScale(scale) {
            Box(Modifier.size(width = 360.dp, height = 300.dp)) { RoleGroup(LineRole.INGREDIENT) {} }
        }
        LineRole.entries.forEach { assertShownInFull(context.getString(it.labelRes())) }
    }

    @Test fun roleGroup_everyLabelShowsInFull_at1x() = roleLabels(1.0f)

    @Test fun roleGroup_everyLabelShowsInFull_at1_3x() = roleLabels(1.3f)

    @Test fun roleGroup_labelsFitInsideTheirContainer() {
        withFontScale(1.3f) {
            Box(Modifier.size(width = 360.dp, height = 300.dp)) { RoleGroup(LineRole.TITLE) {} }
        }
        val rootRight = rule.onRoot().fetchSemanticsNode().boundsInRoot.right
        LineRole.entries.forEach {
            val bounds = rule.onNodeWithText(context.getString(it.labelRes())).fetchSemanticsNode().boundsInRoot
            assertTrue("${it.name} label starts inside the screen", bounds.left >= 0f)
            assertTrue("${it.name} label ends inside the screen", bounds.right <= rootRight)
        }
    }

    // 4. Add-recipe sheet

    @Test fun addSheet_footerSitsAboveNavigationBar() {
        val inset = 48.dp
        rule.setContent {
            SpoonfileTheme(darkTheme = false) {
                Box(Modifier.size(width = 360.dp, height = 900.dp), contentAlignment = Alignment.BottomStart) {
                    AddRecipeSheetContent(onAction = {}, insets = WindowInsets(bottom = inset))
                }
            }
        }
        val footer = rule.onNodeWithText(context.getString(R.string.add_privacy)).fetchSemanticsNode()
        val rootHeight = rule.onRoot().fetchSemanticsNode().boundsInRoot.bottom
        val insetPx = with(rule.density) { inset.toPx() }
        assertTrue(
            "footer bottom ${footer.boundsInRoot.bottom} must be above ${rootHeight - insetPx}",
            footer.boundsInRoot.bottom <= rootHeight - insetPx,
        )
    }

    @Test fun addSheet_defaultInsetsAreTheNavigationBars() {
        // Smoke check that the default parameter composes (no inset on the JVM, so it just renders).
        rule.setContent { SpoonfileTheme(darkTheme = false) { AddRecipeSheetContent(onAction = {}) } }
        rule.onNodeWithText(context.getString(R.string.add_privacy)).assertExists()
    }

    @Test fun addSheetCopy_isTheSameSentenceInAllFourLocales() {
        val expected = mapOf(
            "en" to "Your data is stored on your device",
            "de" to "Deine Daten werden auf deinem Gerät gespeichert",
            "fr" to "Vos données sont stockées sur votre appareil",
            "es" to "Tus datos se almacenan en tu dispositivo",
        )
        expected.forEach { (tag, text) ->
            val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
            assertEquals(tag, text, context.createConfigurationContext(config).getString(R.string.add_privacy))
        }
    }
}
