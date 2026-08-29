package com.gitofy.core.designsystem.tokens

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * GITOFY dimension tokens.
 *
 * Centralizes sizing decisions (touch targets, icon sizes, component heights)
 * so individual screens never invent one-off dimensions. See PRD §29
 * (Accessibility) and §31 (Spacing System).
 */
object Dimensions {

    // Accessibility — every interactive element must satisfy this minimum.
    val minTouchTarget: Dp = 48.dp

    // Icon sizes
    val iconExtraSmall: Dp = 16.dp
    val iconSmall: Dp = 18.dp
    val iconMedium: Dp = 20.dp
    val iconLarge: Dp = 24.dp
    val iconExtraLarge: Dp = 32.dp
    val iconHero: Dp = 64.dp

    // Avatar sizes
    val avatarSmall: Dp = 32.dp
    val avatarMedium: Dp = 40.dp
    val avatarLarge: Dp = 56.dp

    // Component heights
    val buttonHeight: Dp = 48.dp
    val compactButtonHeight: Dp = 40.dp
    val textFieldHeight: Dp = 56.dp
    val listItemHeight: Dp = 64.dp
    val settingRowMinHeight: Dp = 64.dp
    val topAppBarHeight: Dp = 64.dp
    val navigationBarHeight: Dp = 80.dp
    val navigationRailWidth: Dp = 96.dp
    val navigationDrawerWidth: Dp = 280.dp

    // Border / outline widths
    val borderThin: Dp = 1.dp
    val borderMedium: Dp = 1.5.dp
    val borderThick: Dp = 2.dp

    // Card press interaction (see PRD §12, §28)
    const val cardPressedScale: Float = 0.985f
    const val defaultPressedScale: Float = 0.97f

    // Window size breakpoints (dp), mirroring Material 3 window size classes.
    val compactMaxWidth: Dp = 599.dp
    val mediumMaxWidth: Dp = 839.dp
    // Expanded = anything above mediumMaxWidth
}
