package com.postpole.spoonfile.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * App font. Target is Roboto Flex (display/headline roles on opsz 57, wdth 100, GRAD 0).
 * TODO: bundle the Roboto Flex variable TTF in res/font (or add downloadable fonts) and point this
 * at it; until then the system sans-serif is used so the type scale below still applies.
 */
val SpoonfileFontFamily: FontFamily = FontFamily.SansSerif

private fun style(
    size: Float,
    line: Float,
    weight: Int,
    tracking: Float = 0f,
) = TextStyle(
    fontFamily = SpoonfileFontFamily,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = tracking.sp,
)

val SpoonfileTypography = Typography(
    // "Recipes" oversized title; cook step number uses CookStepNumber below.
    displayLarge = style(50f, 58f, 600, -1.0f),
    displayMedium = style(40f, 46f, 600, -0.8f), // recipe title
    displaySmall = style(34f, 38f, 600, -0.6f), // "Spoonfile Pro"
    headlineLarge = style(31f, 40f, 500, -0.4f), // cook step text
    headlineMedium = style(28f, 34f, 600), // empty state
    headlineSmall = style(24f, 32f, 600), // sheet title (dialog titles use DialogTitle, weight 500)
    titleLarge = style(22f, 28f, 500), // top app bar titles
    titleSmall = style(15f, 20f, 700, 0.1f), // section labels
    bodyLarge = style(16f, 22f, 400),
    bodyMedium = style(14.5f, 20.5f, 400),
    bodySmall = style(13.5f, 18f, 400),
    labelLarge = style(16.5f, 21f, 700), // buttons / FAB labels
    labelMedium = style(12.5f, 20f, 700), // chips / tags
)

/** Emphasized titleLarge: section headers / card titles. */
val SectionTitle = style(22f, 28f, 600, -0.3f)

/** Cook step number (64 sp, bold). */
val CookStepNumber = style(64f, 64f, 700)

/** Dialog title: headlineSmall at weight 500. */
val DialogTitle = style(24f, 32f, 500)

/** Group headers (Settings, Shopping): titleSmall 14/20 bold; apply primary colour at the call site. */
val GroupHeader = style(14f, 20f, 700)

/** Ingredient quantity column: bodyLarge bold; primary colour, min width 58 sp at the call site. */
val IngredientQuantity = style(16f, 22f, 700)
val IngredientQuantityMinWidth: TextUnit = 58.sp
