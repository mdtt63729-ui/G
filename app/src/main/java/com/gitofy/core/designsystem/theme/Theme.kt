package com.gitofy.core.designsystem.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    inversePrimary = LightInversePrimary,
    scrim = LightScrim,
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,
    inversePrimary = DarkInversePrimary,
    scrim = DarkScrim,
)

/**
 * Additional status colors not covered by M3 scheme.
 */
object GITOFYStatusColors {
    val success @Composable get() = if (isSystemInDarkTheme()) SuccessDark else SuccessLight
    val successContainer @Composable get() = if (isSystemInDarkTheme()) SuccessContainerDark else SuccessContainerLight
    val warning @Composable get() = if (isSystemInDarkTheme()) WarningDark else WarningLight
    val warningContainer @Composable get() = if (isSystemInDarkTheme()) WarningContainerDark else WarningContainerLight
    val info @Composable get() = if (isSystemInDarkTheme()) InfoDark else InfoLight
    val infoContainer @Composable get() = if (isSystemInDarkTheme()) InfoContainerDark else InfoContainerLight
    val neutral @Composable get() = if (isSystemInDarkTheme()) NeutralDark else NeutralLight
    val neutralContainer @Composable get() = if (isSystemInDarkTheme()) NeutralContainerDark else NeutralContainerLight
}

@Composable
fun GITOFYTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // PRD §6: No dynamic color — use exact GITOFY palette
    amoledMode: Boolean = false,
    accentColorHex: String? = null,
    content: @Composable () -> Unit
) {
    // GITOFY uses its own deterministic palette. Android wallpaper/dynamic
    // colors are deliberately not used because they can turn the UI teal,
    // washed-out, or otherwise inconsistent with the brand palette.
    val baseScheme = if (darkTheme) {
        if (amoledMode) DarkColorsAmoled else DarkColors
    } else {
        LightColors
    }

    // PRD §6 — Accent color override
    val colorScheme = if (accentColorHex != null) {
        val accent = parseHexColor(accentColorHex)
        baseScheme.copy(
            primary = accent,
            onPrimary = if (accent.luminance() > 0.5f) Color(0xFF000000) else Color(0xFFFFFFFF)
        )
    } else baseScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = GITOFYTypography,
        shapes = GITOFYShapes,
        content = content
    )
}

private fun parseHexColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        LightPrimary
    }
}

private fun Color.luminance(): Float {
    val r = red; val g = green; val b = blue
    return 0.299f * r + 0.587f * g + 0.114f * b
}

// PRD §6 — AMOLED / Pure Black dark scheme
private val DarkColorsAmoled = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    background = Color(0xFF000000),
    onBackground = DarkOnBackground,
    surface = Color(0xFF000000),
    onSurface = DarkOnSurface,
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,
    inversePrimary = DarkInversePrimary,
    scrim = DarkScrim,
)
