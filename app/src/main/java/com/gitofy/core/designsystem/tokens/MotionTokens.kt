package com.gitofy.core.designsystem.tokens

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec

/**
 * GITOFY motion system.
 *
 * A single source of truth for animation durations, easings and springs so
 * that motion feels consistent across the entire app. See PRD §26
 * (Motion Design System), §27 (Screen Transitions) and §28
 * (Micro-Interactions).
 *
 * Motion must always support comprehension rather than decoration:
 * pick the smallest duration tier that reads clearly and prefer these
 * tokens over ad-hoc `tween`/`spring` calls in feature code.
 */
object MotionTokens {

    // Durations (ms) ---------------------------------------------------

    /** Button feedback, chips, toggles, icon state changes. 150–200ms. */
    const val DurationFast = 180

    /** Dialogs, bottom sheets, expanding cards, content replacement. 250–300ms. */
    const val DurationMedium = 280

    /** Major screen transitions, complex dashboard changes. Use sparingly. 400–500ms. */
    const val DurationLong = 450

    /** Card / button press feedback specifically (PRD §28). */
    const val DurationPress = 150

    /** Shimmer loop duration for skeleton loading (PRD §25). */
    const val DurationShimmer = 1300

    // Easings ------------------------------------------------------------

    val EmphasizedEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val StandardEasing: Easing = FastOutSlowInEasing
    val EnterEasing: Easing = LinearOutSlowInEasing

    // Springs --------------------------------------------------------------

    /** Snappy spring for press/release feedback on cards and buttons. */
    val PressSpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )

    /** Restrained spring for expand/collapse and sheet motion; no overshoot. */
    val ExpandCollapseSpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
}
