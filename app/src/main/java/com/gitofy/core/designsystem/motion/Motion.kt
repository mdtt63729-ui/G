package com.gitofy.core.designsystem.motion

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.gitofy.core.designsystem.tokens.Dimensions
import com.gitofy.core.designsystem.tokens.MotionTokens

/**
 * PRD §3 — Global Motion System.
 *
 * Centralized motion definitions so every screen uses the SAME animation
 * language. Feature screens should NEVER hand-roll animation specs — they
 * pull from these helpers.
 *
 * Motion categories defined here (PRD §3):
 *   - Screen Enter / Exit
 *   - Back Enter / Exit
 *   - Bottom Navigation
 *   - Card Enter / Exit
 *   - List Insert / Remove
 *   - Loading → Content
 *   - Press
 *   - Success / Error
 *
 * Principles (PRD §4):
 *   - Fast interaction, smooth easing
 *   - No visible frame drop
 *   - No excessive bounce
 *   - Springs use low-bouncy / no-bouncy overshoot
 */

// ---------------------------------------------------------------------------
// PRESS
// ---------------------------------------------------------------------------

/**
 * Applies the standard GITOFY "press" feedback: a subtle scale-down while
 * pressed that springs back on release. Used by [com.gitofy.core.designsystem.components.GITOFYCard]
 * and can be reused anywhere a surface needs the same tactile feel.
 *
 * Target scale is 1.00 → 0.985 for cards; pass [pressedScale] to override
 * (e.g. for smaller controls that can use a slightly stronger squeeze).
 */
@Composable
fun Modifier.gitofyPressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = Dimensions.cardPressedScale
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = MotionTokens.PressSpring,
        label = "gitofyPressScale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

// ---------------------------------------------------------------------------
// SCREEN ENTER / EXIT — Forward navigation
// ---------------------------------------------------------------------------

/**
 * PRD §4 — Forward navigation: current screen slightly left + fade,
 * new screen slides in from right → center + fade.
 */
val AnimatedContentTransitionScope<*>.gitofyForwardEnter
    get() = slideInHorizontally(
        animationSpec = tween(MotionTokens.DurationMedium, easing = MotionTokens.EmphasizedEasing),
        initialOffsetX = { it / 4 }
    ) + fadeIn(tween(MotionTokens.DurationMedium))

val AnimatedContentTransitionScope<*>.gitofyForwardExit
    get() = slideOutHorizontally(
        animationSpec = tween(MotionTokens.DurationMedium, easing = MotionTokens.EmphasizedEasing),
        targetOffsetX = { -it / 4 }
    ) + fadeOut(tween(MotionTokens.DurationFast))

// ---------------------------------------------------------------------------
// BACK ENTER / EXIT — Reverse navigation
// ---------------------------------------------------------------------------

/**
 * PRD §4 — Back navigation: current screen center → right + fade,
 * previous screen slides in from left → center + fade.
 */
val AnimatedContentTransitionScope<*>.gitofyBackEnter
    get() = slideInHorizontally(
        animationSpec = tween(MotionTokens.DurationMedium, easing = MotionTokens.EmphasizedEasing),
        initialOffsetX = { -it / 4 }
    ) + fadeIn(tween(MotionTokens.DurationMedium))

val AnimatedContentTransitionScope<*>.gitofyBackExit
    get() = slideOutHorizontally(
        animationSpec = tween(MotionTokens.DurationMedium, easing = MotionTokens.EmphasizedEasing),
        targetOffsetX = { it / 4 }
    ) + fadeOut(tween(MotionTokens.DurationFast))

// ---------------------------------------------------------------------------
// CARD ENTER / EXIT — PRD §3, §9
// ---------------------------------------------------------------------------

/** Card enter: fade + slight vertical movement (bottom → up). */
val gitofyCardEnter: EnterTransition
    get() = fadeIn(tween(MotionTokens.DurationMedium, easing = MotionTokens.EnterEasing)) +
        slideInVertically(
            animationSpec = tween(MotionTokens.DurationMedium, easing = MotionTokens.EnterEasing),
            initialOffsetY = { it / 6 }
        )

/** Card exit: fade out + slight shrink. */
val gitofyCardExit: ExitTransition
    get() = fadeOut(tween(MotionTokens.DurationFast)) +
        scaleOut(
            animationSpec = tween(MotionTokens.DurationFast, easing = MotionTokens.EmphasizedEasing),
            targetScale = 0.92f
        )

// ---------------------------------------------------------------------------
// LIST INSERT / REMOVE — PRD §3, §9
// ---------------------------------------------------------------------------

/** List item insert: slide in from bottom + fade. */
val gitofyListInsertEnter: EnterTransition
    get() = slideInVertically(
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        initialOffsetY = { it / 4 }
    ) + fadeIn(tween(MotionTokens.DurationFast))

/** List item remove: fade out + shrink. */
val gitofyListRemoveExit: ExitTransition
    get() = fadeOut(tween(MotionTokens.DurationFast)) +
        scaleOut(
            animationSpec = tween(MotionTokens.DurationFast),
            targetScale = 0.85f
        )

// ---------------------------------------------------------------------------
// LOADING → CONTENT — PRD §3, §8, §47
// ---------------------------------------------------------------------------

/** Content appears after loading with a smooth crossfade. */
val gitofyContentEnter: EnterTransition
    get() = fadeIn(tween(MotionTokens.DurationMedium, easing = MotionTokens.EnterEasing)) +
        scaleIn(
            animationSpec = tween(MotionTokens.DurationMedium, easing = MotionTokens.EnterEasing),
            initialScale = 0.96f
        )

/** Loading overlay exits with a fade when content is ready. */
val gitofyLoadingExit: ExitTransition
    get() = fadeOut(tween(MotionTokens.DurationMedium))

// ---------------------------------------------------------------------------
// BOTTOM NAVIGATION — PRD §3, §13, §14
// ---------------------------------------------------------------------------

/** Tab content switch: quick fade, no slide (keeps indicator the focus). */
val gitofyTabEnter: EnterTransition
    get() = fadeIn(tween(MotionTokens.DurationFast, easing = MotionTokens.EnterEasing))

val gitofyTabExit: ExitTransition
    get() = fadeOut(tween(MotionTokens.DurationFast))

// ---------------------------------------------------------------------------
// SUCCESS — PRD §3
// ---------------------------------------------------------------------------

/** Success state: scale-in with a gentle spring (no excessive bounce). */
val gitofySuccessEnter: EnterTransition
    get() = scaleIn(
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        initialScale = 0.5f
    ) + fadeIn(tween(MotionTokens.DurationFast))

// ---------------------------------------------------------------------------
// ERROR — PRD §3
// ---------------------------------------------------------------------------

/** Error state: slide in from top + fade (draws attention without jank). */
val gitofyErrorEnter: EnterTransition
    get() = slideInVertically(
        animationSpec = tween(MotionTokens.DurationMedium, easing = MotionTokens.EnterEasing),
        initialOffsetY = { -it / 4 }
    ) + fadeIn(tween(MotionTokens.DurationMedium))
