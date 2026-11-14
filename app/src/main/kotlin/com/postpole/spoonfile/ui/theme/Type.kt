package com.postpole.spoonfile.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Brand font is Roboto Flex (display/headline roles use opsz 57, wdth 100, GRAD 0).
 * TODO: bundle the Roboto Flex variable TTF in res/font (or add downloadable-font certs) and point this at it;
 * until then the platform default family is used so the type scale below is already correct.
 */
val SpoonfileFontFamily: FontFamily = FontFamily.Default

private fun style(
    size: Int,
    line: Int,
    weight: Int,
    tracking: Float = 0f,
    color: Color = Color.Unspecified,
) = TextStyle(
    fontFamily = SpoonfileFontFamily,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = tracking.sp,
    color = color,
)

val SpoonfileTypography = Typography(
    displayLarge = style(50, 58, 600, -1.0f), // "Recipes" oversized title
    displayMedium = style(40, 46, 600, -0.8f), // recipe title
    displaySmall = style(34, 38, 600, -0.6f), // "Spoonfile Pro"
    headlineLarge = style(31, 40, 500, -0.4f), // cook step text
    headlineMedium = style(28, 34, 600), // empty state
    headlineSmall = style(24, 32, 600), // sheet title (dialog titles: 500, see DialogTitle)
    titleLarge = style(22, 28, 500), // top app bar titles
    titleMedium = style(16, 24, 500, 0.15f),
    titleSmall = style(14, 20, 700), // group headers (tint with primary at call site)
    labelLarge = style(16, 21, 700), // buttons / FAB labels
    labelMedium = style(12, 20, 700), // chips / tags
    labelSmall = style(11, 16, 500, 0.5f),
    bodyLarge = style(16, 22, 400), // search, ingredients, steps
    bodyMedium = style(14, 20, 400), // supporting text, banners, dialog body
    bodySmall = style(13, 18, 400), // subtitles / metadata / tips
)

/** Extra, non-M3-role styles from the design handoff. */
object SpoonfileText {
    /** Cook step number: displayLarge at 64 sp, weight 700. */
    val cookStepNumber = style(64, 64, 700)

    /** Section headers / card titles: titleLarge emphasized. */
    val sectionTitle = style(22, 28, 600, -0.3f)

    /** Section labels: titleSmall override. */
    val sectionLabel = style(15, 20, 700, 0.1f)

    /** Dialog title: headlineSmall, weight 500. */
    val dialogTitle = style(24, 32, 500)

    /** Ingredient quantity column: bodyLarge, weight 700, primary colour at call site; min width 58 sp. */
    val ingredientQuantity = style(16, 22, 700)
    val ingredientQuantityMinWidth: TextUnit = 58.sp
}
