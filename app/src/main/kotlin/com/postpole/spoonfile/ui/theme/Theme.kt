package com.postpole.spoonfile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Spoonfile, seed #8E6BD1 (Direction 4 Expressive Pastel). No Material You dynamic colour.

val SpoonfileLightColors = lightColorScheme(
    primary = Color(0xFF6B4BA8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEBDCFF),
    onPrimaryContainer = Color(0xFF260059),
    inversePrimary = Color(0xFFD3BBFF),
    secondary = Color(0xFF8C4A6E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD8EA),
    onSecondaryContainer = Color(0xFF3A0727),
    tertiary = Color(0xFF8A5100),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCC0),
    onTertiaryContainer = Color(0xFF2D1600),
    background = Color(0xFFFDF7FF),
    onBackground = Color(0xFF1D1A22),
    surface = Color(0xFFFDF7FF),
    onSurface = Color(0xFF1D1A22),
    surfaceVariant = Color(0xFFE7E0EB),
    onSurfaceVariant = Color(0xFF4A4453),
    surfaceTint = Color(0xFF6B4BA8),
    inverseSurface = Color(0xFF322F35),
    inverseOnSurface = Color(0xFFF5EFF7),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF7B7485),
    outlineVariant = Color(0xFFCBC3D4),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFEF7FF),
    surfaceDim = Color(0xFFDED8E0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F1FB),
    surfaceContainer = Color(0xFFF2EBF6),
    surfaceContainerHigh = Color(0xFFECE5F0),
    surfaceContainerHighest = Color(0xFFE6DFEA),
)

val SpoonfileDarkColors = darkColorScheme(
    primary = Color(0xFFD3BBFF),
    onPrimary = Color(0xFF3C1878),
    primaryContainer = Color(0xFF54338F),
    onPrimaryContainer = Color(0xFFEBDDFF),
    inversePrimary = Color(0xFF6C4CA9),
    secondary = Color(0xFFFFAFD7),
    onSecondary = Color(0xFF541B3D),
    secondaryContainer = Color(0xFF6F3154),
    onSecondaryContainer = Color(0xFFFFD8E9),
    tertiary = Color(0xFFFFB86E),
    onTertiary = Color(0xFF492900),
    tertiaryContainer = Color(0xFF693C00),
    onTertiaryContainer = Color(0xFFFFDCBD),
    background = Color(0xFF151218),
    onSurface = Color(0xFFE7E0E8),
    surface = Color(0xFF151218),
    onBackground = Color(0xFFE7E0E8),
    surfaceVariant = Color(0xFF49454E),
    onSurfaceVariant = Color(0xFFCBC4CF),
    surfaceTint = Color(0xFFD3BBFF),
    inverseSurface = Color(0xFFE7E0E8),
    inverseOnSurface = Color(0xFF322F35),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF948F99),
    outlineVariant = Color(0xFF49454E),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF3B383E),
    surfaceDim = Color(0xFF151218),
    surfaceContainerLowest = Color(0xFF0F0D13),
    surfaceContainerLow = Color(0xFF1D1B20),
    surfaceContainer = Color(0xFF211F24),
    surfaceContainerHigh = Color(0xFF2C292F),
    surfaceContainerHighest = Color(0xFF36343A),
)

// Cook mode (app-level, not an M3 role)
val CookBackgroundLight = Color(0xFFEBDCFF) // primaryContainer
val CookTextLight = Color(0xFF260059) // onPrimaryContainer
val CookBackgroundDark = Color(0xFF000000) // pure black: screen stays on (OLED)
val CookTextDark = Color(0xFFEBDDFF)

@Composable
fun SpoonfileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) SpoonfileDarkColors else SpoonfileLightColors,
        typography = SpoonfileTypography,
        shapes = SpoonfileShapes,
        content = content,
    )
}
