package com.gitofy.core.designsystem.tokens

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * GITOFY elevation tokens.
 *
 * PRD §9 — prefer tonal/surface hierarchy first, and reach for elevation
 * only when a surface genuinely needs physical separation. Do not make
 * every card elevated.
 */
object Elevation {

    /** Background / resting content surface. No shadow. */
    val level0: Dp = 0.dp

    /** Interactive cards that sit above the background. */
    val level1: Dp = 1.dp

    /** Floating controls (e.g. FAB at rest). */
    val level2: Dp = 3.dp

    /** Dialogs, bottom sheets, menus and other temporary surfaces. */
    val level3: Dp = 6.dp

    /** Reserved for rare, high-priority floating surfaces above level3. */
    val level4: Dp = 8.dp

    /** Pressed elevation for an otherwise-flat interactive card. */
    val pressed: Dp = 1.dp

    /** Elevation for a floating control while pressed/dragged. */
    val floatingPressed: Dp = 6.dp
}
