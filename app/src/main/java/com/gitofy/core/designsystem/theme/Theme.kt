package com.gitofy.core.designsystem.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
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
    dynamicColor: Boolean = false,
    amoledMode: Boolean = false,
    accentColorHex: String? = null,
    fontFamily: FontFamily = FontFamily.Default,
    content: @Composable () -> Unit
) {
    // GITOFY uses its own deterministic palette. Android wallpaper/dynamic
    // colors are deliberately not used because they can turn the UI teal,
    // washed-out, or otherwise inconsistent with the brand palette.
    val baseScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val view = LocalView.current
        if (darkTheme) dynamicDarkColorScheme(view.context) else dynamicLightColorScheme(view.context)
    } else if (darkTheme) {
        if (amoledMode) DarkColorsAmoled else DarkColors
    } else {
        LightColors
    }

    // PRD §6 — Accent color override.
    // FIX (dynamic color + broken text contrast): accentColorHex always has a
    // non-null default ("#5B32D6"), so this override used to fire even when
    // dynamic color was ON. That overwrote just `primary`/`onPrimary` while
    // leaving every other role (primaryContainer, onPrimaryContainer,
    // secondary, etc.) computed from the *dynamic* wallpaper palette — a
    // mismatched scheme where container/text pairs no longer agreed with
    // each other, which is what made text and colors look wrong. Dynamic
    // color must fully own the palette when enabled; the manual accent
    // override only makes sense when dynamic color is off.
    val usingDynamicColor = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = if (!usingDynamicColor && accentColorHex != null) {
        val accent = parseHexColor(accentColorHex)
        baseScheme.copy(
            primary = accent,
            onPrimary = if (accent.luminance() > 0.5f) Color(0xFF000000) else Color(0xFFFFFFFF)
        )
    } else baseScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // Keep system-window geometry stable across pause/resume and theme
            // recomposition. MainActivity owns the transparent edge-to-edge
            // window configuration; this block only updates icon contrast.
            val controller = WindowCompat.getInsetsController((view.context as Activity).window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    val typography = GITOFYTypography.copy(
        displayLarge = GITOFYTypography.displayLarge.copy(fontFamily = fontFamily),
        displayMedium = GITOFYTypography.displayMedium.copy(fontFamily = fontFamily),
        displaySmall = GITOFYTypography.displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = GITOFYTypography.headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = GITOFYTypography.headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = GITOFYTypography.headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = GITOFYTypography.titleLarge.copy(fontFamily = fontFamily),
        titleMedium = GITOFYTypography.titleMedium.copy(fontFamily = fontFamily),
        titleSmall = GITOFYTypography.titleSmall.copy(fontFamily = fontFamily),
        bodyLarge = GITOFYTypography.bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = GITOFYTypography.bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = GITOFYTypography.bodySmall.copy(fontFamily = fontFamily),
        labelLarge = GITOFYTypography.labelLarge.copy(fontFamily = fontFamily),
        labelMedium = GITOFYTypography.labelMedium.copy(fontFamily = fontFamily),
        labelSmall = GITOFYTypography.labelSmall.copy(fontFamily = fontFamily)
    )
    MaterialTheme(colorScheme = colorScheme, typography = typography, shapes = GITOFYShapes, content = content)
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
