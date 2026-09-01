package com.gitofy.core.designsystem.motion

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.gitofy.core.designsystem.tokens.Dimensions
import com.gitofy.core.designsystem.tokens.MotionTokens

/** Single motion language for the whole app: restrained slide + fade with no
 * overshoot or bounce. Animations are intentionally short to preserve 60/120Hz
 * interaction responsiveness on mobile devices. */
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
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

private const val PAGE_SLIDE_FRACTION = 0.16f

private fun smoothPageEnter(offset: (Int) -> Int): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(340, easing = MotionTokens.EmphasizedEasing),
        initialOffsetX = offset
    ) + fadeIn(tween(300, easing = MotionTokens.EmphasizedEasing))

private fun smoothPageExit(offset: (Int) -> Int): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(300, easing = MotionTokens.EmphasizedEasing),
        targetOffsetX = offset
    ) + fadeOut(tween(220, easing = MotionTokens.EmphasizedEasing))

val AnimatedContentTransitionScope<*>.gitofyForwardEnter: EnterTransition
    get() = smoothPageEnter { (it * PAGE_SLIDE_FRACTION).toInt() }

val AnimatedContentTransitionScope<*>.gitofyForwardExit: ExitTransition
    get() = smoothPageExit { -(it * PAGE_SLIDE_FRACTION).toInt() }

val AnimatedContentTransitionScope<*>.gitofyBackEnter: EnterTransition
    get() = smoothPageEnter { -(it * PAGE_SLIDE_FRACTION).toInt() }

val AnimatedContentTransitionScope<*>.gitofyBackExit: ExitTransition
    get() = smoothPageExit { (it * PAGE_SLIDE_FRACTION).toInt() }

/** Component/text entrance: restrained, non-bouncy fade + vertical slide. */
val gitofySlideFadeEnter: EnterTransition
    get() = slideInVertically(
        animationSpec = tween(240, easing = MotionTokens.EmphasizedEasing),
        initialOffsetY = { it / 12 }
    ) + fadeIn(tween(210, easing = MotionTokens.EmphasizedEasing))

/** Buttery bouncy scale + fade entrance, reserved for a single hero element
 * (e.g. the app icon on the sign-in screen). Everywhere else in the app uses
 * the restrained [gitofySlideFadeEnter] by design — this springy variant is
 * intentionally used sparingly so the bounce reads as a deliberate accent. */
val gitofyBouncyIconEnter: EnterTransition
    get() = scaleIn(
        animationSpec = tween(720, easing = MotionTokens.EmphasizedEasing),
        initialScale = 0.86f
    ) + fadeIn(tween(620, easing = MotionTokens.EmphasizedEasing))

/**
 * PRD §21-22: scroll-aware Create FAB — reverse slide-in from the right with
 * a spring bounce when the user scrolls back down toward the FAB. Paired
 * with [gitofyFabHideExit]. Reused as-is everywhere the FAB needs this
 * exact scroll behavior so the motion stays consistent.
 */
val gitofyFabShowEnter: EnterTransition
    get() = slideInHorizontally(
        animationSpec = MotionTokens.FabBounceSpringInt,
        initialOffsetX = { fullWidth -> fullWidth }
    ) + fadeIn(animationSpec = tween(MotionTokens.DurationFast, easing = MotionTokens.EnterEasing))

/** PRD §21-22: slide right + bounce out of view when the user scrolls up/away. */
val gitofyFabHideExit: ExitTransition
    get() = slideOutHorizontally(
        animationSpec = MotionTokens.FabBounceSpringInt,
        targetOffsetX = { fullWidth -> fullWidth }
    ) + fadeOut(animationSpec = tween(MotionTokens.DurationFast, easing = MotionTokens.StandardEasing))


@Composable
fun GITOFYStaggeredVisibility(
    index: Int,
    visible: Boolean = true,
    enter: EnterTransition = gitofySlideFadeEnter,
    exit: ExitTransition = gitofySlideFadeExit,
    content: @Composable () -> Unit
) {
    // Save the completed entrance state so a pause/resume or configuration
    // recreation restores the screen in its final geometry instead of replaying
    // every entrance animation and visibly shifting all components.
    val readyState = rememberSaveable(index, visible) { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(visible, index) {
        if (visible) {
            kotlinx.coroutines.delay((index.coerceAtLeast(0) * 72L).coerceAtMost(420L))
            readyState.value = true
        } else {
            readyState.value = false
        }
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = readyState.value && visible,
        enter = enter,
        exit = exit
    ) { content() }
}

val gitofySlideFadeExit: ExitTransition
    get() = slideOutVertically(
        animationSpec = tween(220, easing = MotionTokens.EmphasizedEasing),
        targetOffsetY = { -it / 14 }
    ) + fadeOut(tween(190, easing = MotionTokens.EmphasizedEasing))

val gitofyCardEnter: EnterTransition get() = gitofySlideFadeEnter
val gitofyCardExit: ExitTransition get() = gitofySlideFadeExit
val gitofyListInsertEnter: EnterTransition get() = gitofySlideFadeEnter
val gitofyListRemoveExit: ExitTransition get() = gitofySlideFadeExit
val gitofyContentEnter: EnterTransition get() = gitofySlideFadeEnter
val gitofyLoadingExit: ExitTransition get() = gitofySlideFadeExit
val gitofyTabEnter: EnterTransition get() = gitofySlideFadeEnter
val gitofyTabExit: ExitTransition get() = gitofySlideFadeExit
val gitofySuccessEnter: EnterTransition get() = gitofySlideFadeEnter
val gitofyErrorEnter: EnterTransition get() = gitofySlideFadeEnter
