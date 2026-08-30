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
        animationSpec = tween(280, easing = MotionTokens.EmphasizedEasing),
        initialOffsetX = offset
    ) + fadeIn(tween(240, easing = MotionTokens.EmphasizedEasing))

private fun smoothPageExit(offset: (Int) -> Int): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(240, easing = MotionTokens.EmphasizedEasing),
        targetOffsetX = offset
    ) + fadeOut(tween(190, easing = MotionTokens.EmphasizedEasing))

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
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        initialScale = 0.55f
    ) + fadeIn(tween(360, easing = MotionTokens.StandardEasing))


@Composable
fun GITOFYStaggeredVisibility(
    index: Int,
    visible: Boolean = true,
    enter: EnterTransition = gitofySlideFadeEnter,
    exit: ExitTransition = gitofySlideFadeExit,
    content: @Composable () -> Unit
) {
    val readyState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(visible, index) {
        if (visible) {
            kotlinx.coroutines.delay((index.coerceAtLeast(0) * 18L).coerceAtMost(120L))
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
