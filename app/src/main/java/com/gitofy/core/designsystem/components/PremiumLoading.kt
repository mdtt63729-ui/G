package com.gitofy.core.designsystem.components

import com.gitofy.core.designsystem.motion.gitofySlideFadeEnter
import com.gitofy.core.designsystem.motion.gitofySlideFadeExit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gitofy.R

/**
 * Premium loading system for the entire GITOFY app.
 *
 * Provides smooth, premium loading experiences:
 * - PremiumLoadingOverlay: Full-screen premium loading with animated dots
 * - PremiumButtonLoader: Button-internal loading animation
 * - ScreenTransitionLoading: Used between screen transitions
 */

@Composable
fun PremiumLoadingOverlay(
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    dotColor: Color = MaterialTheme.colorScheme.primary
) {
    AnimatedVisibility(
        visible = visible,
        enter = gitofySlideFadeEnter,
        exit = gitofySlideFadeExit,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            PremiumDotsLoader(dotColor = dotColor)
        }
    }
}

/**
 * Premium 3-dot bouncing loader with smooth wave animation.
 */
@Composable
fun PremiumDotsLoader(
    modifier: Modifier = Modifier,
    dotSize: Dp = 12.dp,
    dotSpacing: Dp = 8.dp,
    dotColor: Color = MaterialTheme.colorScheme.primary,
    animationDelay: Int = 150
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dotsLoader")

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dotSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val dotScale by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 1.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 600,
                        delayMillis = index * animationDelay,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_scale_$index"
            )
            val dotAlpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 600,
                        delayMillis = index * animationDelay,
                        easing = LinearEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_alpha_$index"
            )
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .scale(dotScale)
                    .alpha(dotAlpha)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
    }
}

/**
 * Premium button loading — smooth pulsing circle inside button.
 */
@Composable
fun PremiumButtonLoader(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onPrimary,
    circleSize: Dp = 22.dp,
    strokeWidth: Dp = 2.5.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "buttonLoader")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = modifier
            .size(circleSize)
            .scale(pulseScale),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.CircularProgressIndicator(
            modifier = Modifier.size(circleSize),
            strokeWidth = strokeWidth,
            color = color
        )
    }
}

/**
 * Full-screen premium loading with logo + dots.
 * Used for screen transitions and initial loads.
 */
@Composable
fun PremiumScreenLoading(
    modifier: Modifier = Modifier,
    visible: Boolean = true
) {
    AnimatedVisibility(
        visible = visible,
        enter = gitofySlideFadeEnter,
        exit = gitofySlideFadeExit,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Animated logo circle with pulse
                val infiniteTransition = rememberInfiniteTransition(label = "screenLoad")
                val logoScale by infiniteTransition.animateFloat(
                    initialValue = 0.92f,
                    targetValue = 1.08f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "logoScale"
                )
                val logoAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.7f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "logoAlpha"
                )
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .scale(logoScale)
                        .alpha(logoAlpha)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    // PRD §46: Use the actual GITOFY branding asset
                    // instead of a generic Star icon.
                    Image(
                        painter = painterResource(id = R.drawable.ic_gito_logo),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Premium dots below logo
                PremiumDotsLoader(
                    dotSize = 10.dp,
                    dotSpacing = 6.dp,
                    dotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Content fade-in transition for when loading completes.
 * Wraps content in a smooth fade + slide-up animation.
 */
@Composable
fun ContentFadeIn(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = gitofySlideFadeEnter,
        exit = gitofySlideFadeExit,
        modifier = modifier
    ) {
        content()
    }
}
