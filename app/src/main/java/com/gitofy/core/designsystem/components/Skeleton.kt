package com.gitofy.core.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gitofy.core.designsystem.theme.LocalSpacing

/**
 * Skeleton loader for list items.
 */
@Composable
fun SkeletonListItem(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(LocalSpacing.current.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShimmerCircle(size = 48)
        Spacer(modifier = Modifier.width(LocalSpacing.current.lg))
        Column(modifier = Modifier.weight(1f)) {
            ShimmerLine(widthFraction = 0.7f, height = 16)
            Spacer(modifier = Modifier.height(LocalSpacing.current.sm))
            ShimmerLine(widthFraction = 0.4f, height = 12)
        }
    }
}

@Composable
private fun ShimmerCircle(size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    )
}

@Composable
private fun ShimmerLine(widthFraction: Float, height: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    )
}

/**
 * Inline error banner with optional retry action.
 */
@Composable
fun ErrorBanner(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(LocalSpacing.current.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (onRetry != null) {
            Spacer(modifier = Modifier.height(LocalSpacing.current.md))
            GITOFYButton(
                text = "Retry",
                onClick = onRetry,
                type = GITOFYButtonType.Outlined
            )
        }
    }
}

/**
 * Simple loading indicator.
 */
@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

/**
 * Progress bar with label.
 */
@Composable
fun LabeledProgressBar(
    label: String,
    progress: Float?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(LocalSpacing.current.lg)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(LocalSpacing.current.sm))
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
