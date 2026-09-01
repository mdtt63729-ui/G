package com.gitofy.core.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * PRD §18 — Skeleton Loading System
 *
 * A set of shimmer-based placeholder composables used to indicate loading states
 * throughout the GITOFY app. The shimmer is intentionally soft and low-CPU:
 * a single [rememberInfiniteTransition] drives a long, slow [tween] animation
 * that translates a subtle linear gradient across each placeholder surface.
 *
 * Design notes
 * ------------
 * - No fake progress percentages are shown; the shimmer is a pure indefinite loop.
 * - Shimmer colors are derived from the Material 3 surface/surfaceVariant roles
 *   so placeholders blend with both light and dark themes.
 * - Every public composable is [Modifier]-driven for sizing, allowing callers to
 *   constrain or stretch placeholders as needed.
 */
private object SkeletonDefaults {
    /** Duration of a single shimmer sweep, in ms. Long enough to feel calm. */
    const val SHIMMER_DURATION_MS: Int = 1300

    /** Half of a full sweep used as the offscreen lead/trail offset. */
    const val GRADIENT_SPAN: Float = 1000f

    /** Base elevation-like corner radius for cards. */
    val CardCornerRadius: Dp = 12.dp

    /** Base corner radius for pill/text placeholders. */
    val TextCornerRadius: Dp = 4.dp

    /** Default avatar diameter. */
    val AvatarSize: Dp = 40.dp
}

/**
 * The shared shimmer brush used by all skeleton components.
 *
 * It builds a [Brush.linearGradient] whose stops are shifted by [shimmerProgress]
 * (0f..1f), producing a continuous left-to-right sweep. The highlight band is
 * deliberately narrow and low-contrast to keep the animation easy on the eyes
 * and cheap on the CPU.
 *
 * @param shimmerProgress current sweep position, 0f..1f.
 * @param baseColor the resting placeholder color.
 * @param highlightColor the brighter shimmer band color.
 */
@Composable
private fun shimmerBrush(
    shimmerProgress: Float,
    baseColor: Color,
    highlightColor: Color,
): Brush {
    val span = SkeletonDefaults.GRADIENT_SPAN
    // Translate the gradient by progress; use modulo to keep it continuous.
    val start = shimmerProgress * span
    val end = start + span / 2f
    return Brush.linearGradient(
        colors = listOf(
            baseColor,
            highlightColor,
            baseColor,
        ),
        start = Offset(start, 0f),
        end = Offset(end, 0f),
        tileMode = TileMode.Mirror,
    )
}

/**
 * Core placeholder box that paints the shimmer surface.
 *
 * All public skeleton components delegate their painting to this composable so
 * the animation parameters stay centralized and consistent.
 *
 * @param modifier sizing/positioning modifier.
 * @param shape clip shape of the placeholder.
 * @param baseColor resting color of the placeholder.
 * @param highlightColor color of the moving shimmer band.
 */
@Composable
private fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(SkeletonDefaults.TextCornerRadius),
    baseColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    highlightColor: Color = MaterialTheme.colorScheme.surface,
) {
    val transition = rememberInfiniteTransition(label = "skeletonShimmer")
    val shimmerProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = SkeletonDefaults.SHIMMER_DURATION_MS,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerProgress",
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                shimmerBrush(
                    shimmerProgress = shimmerProgress,
                    baseColor = baseColor,
                    highlightColor = highlightColor,
                ),
            ),
    )
}

// -------------------------------------------------------------------------------------------------
// Public skeleton components
// -------------------------------------------------------------------------------------------------

/**
 * A single animated text-line placeholder.
 *
 * Useful for simulating one line of body text, a title, or a label while real
 * content is loading.
 *
 * @param width the width of the placeholder line. Pass [Dp.Unspecified] to fill
 *             the available width.
 * @param height the height of the placeholder line.
 * @param modifier additional layout modifiers.
 */
@Composable
fun SkeletonText(
    width: Dp = 120.dp,
    height: Dp = 12.dp,
    modifier: Modifier = Modifier,
) {
    val resolvedModifier = if (width == Dp.Unspecified) {
        modifier.fillMaxWidth()
    } else {
        modifier.width(width)
    }
    ShimmerBox(
        modifier = resolvedModifier.height(height),
        shape = RoundedCornerShape(SkeletonDefaults.TextCornerRadius),
    )
}

/**
 * A circular avatar placeholder.
 *
 * @param diameter diameter of the avatar circle.
 * @param modifier additional layout modifiers.
 */
@Composable
fun SkeletonAvatar(
    diameter: Dp = SkeletonDefaults.AvatarSize,
    modifier: Modifier = Modifier,
) {
    ShimmerBox(
        modifier = modifier.size(diameter),
        shape = CircleShape,
    )
}

/**
 * A generic card-shaped placeholder.
 *
 * Useful as a building block for richer skeletons, or on its own to indicate
 * that a card surface is loading.
 *
 * @param modifier sizing/positioning modifier. Defaults to filling the width
 *                 with a fixed height.
 * @param height height of the card.
 */
@Composable
fun SkeletonCard(
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
) {
    ShimmerBox(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        shape = RoundedCornerShape(SkeletonDefaults.CardCornerRadius),
    )
}

/**
 * A full list-item skeleton: a circular avatar followed by two text lines.
 *
 * Mirrors the common "avatar + primary text + secondary text" row used across
 * lists in the app (users, members, assignees, etc.).
 *
 * @param modifier additional layout modifiers.
 * @param avatarSize diameter of the avatar placeholder.
 */
@Composable
fun SkeletonListItem(
    modifier: Modifier = Modifier,
    avatarSize: Dp = SkeletonDefaults.AvatarSize,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SkeletonAvatar(diameter = avatarSize)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SkeletonText(width = 160.dp, height = 14.dp)
            SkeletonText(width = 100.dp, height = 12.dp)
        }
    }
}

/**
 * A repository card skeleton.
 *
 * Simulates the repository list-row layout: repo name, description line,
 * and a row of metadata pills (stars / forks / language). The shapes are
 * pure placeholders — no real counts or badges are rendered.
 *
 * @param modifier additional layout modifiers.
 */
@Composable
fun SkeletonRepository(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Repo name (primary title)
        SkeletonText(width = 200.dp, height = 16.dp)
        // Description line (full width)
        SkeletonText(width = Dp.Unspecified, height = 12.dp)
        // Shorter secondary description line
        SkeletonText(width = 220.dp, height = 12.dp)
        Spacer(modifier = Modifier.height(2.dp))
        // Metadata row: language dot + stars + forks pills
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonText(width = 60.dp, height = 10.dp)
            SkeletonText(width = 48.dp, height = 10.dp)
            SkeletonText(width = 48.dp, height = 10.dp)
        }
    }
}

/**
 * A workflow-run card skeleton.
 *
 * Mirrors the workflow run row: status icon, workflow name + branch, and a
 * trailing timestamp/time-ago placeholder.
 *
 * @param modifier additional layout modifiers.
 */
@Composable
fun SkeletonWorkflow(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Status icon placeholder (small circle)
        SkeletonAvatar(diameter = 24.dp)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SkeletonText(width = 180.dp, height = 14.dp)
            SkeletonText(width = 120.dp, height = 11.dp)
        }
        // Trailing time-ago placeholder
        SkeletonText(width = 56.dp, height = 10.dp)
    }
}

/**
 * A log-line skeleton.
 *
 * Simulates a monospaced log line with a short leading token and a longer
 * message body, as seen in the workflow/CI log viewer.
 *
 * @param modifier additional layout modifiers.
 * @param lineCount number of log lines to render.
 */
@Composable
fun SkeletonLog(
    modifier: Modifier = Modifier,
    lineCount: Int = 5,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(lineCount) { index ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Leading timestamp/token
                SkeletonText(
                    width = if (index % 2 == 0) 56.dp else 72.dp,
                    height = 10.dp,
                )
                // Message body — varied widths to feel natural
                SkeletonText(
                    width = when (index % 3) {
                        0 -> 220.dp
                        1 -> 160.dp
                        else -> Dp.Unspecified
                    },
                    height = 10.dp,
                )
            }
        }
    }
}

/**
 * An artifact card skeleton.
 *
 * Mirrors the artifact list-item: name line plus a size/download pill.
 *
 * @param modifier additional layout modifiers.
 */
@Composable
fun SkeletonArtifact(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Artifact icon placeholder (rounded square)
        ShimmerBox(
            modifier = Modifier.size(32.dp),
            shape = RoundedCornerShape(SkeletonDefaults.TextCornerRadius),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SkeletonText(width = 160.dp, height = 13.dp)
            SkeletonText(width = 80.dp, height = 10.dp)
        }
        // Download action placeholder
        SkeletonText(width = 48.dp, height = 10.dp)
    }
}

/**
 * A chat-message bubble skeleton.
 *
 * Renders a single message bubble with a leading avatar and a small column of
 * text lines. Caller can stack multiple of these to simulate a conversation.
 *
 * @param modifier additional layout modifiers.
 * @param avatarSize diameter of the sender avatar placeholder.
 */
@Composable
fun SkeletonChatMessage(
    modifier: Modifier = Modifier,
    avatarSize: Dp = SkeletonDefaults.AvatarSize,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SkeletonAvatar(diameter = avatarSize)
        // Bubble surface
        ShimmerBox(
            modifier = Modifier
                .weight(1f)
                .height(64.dp),
            shape = RoundedCornerShape(SkeletonDefaults.CardCornerRadius),
        )
    }
}
