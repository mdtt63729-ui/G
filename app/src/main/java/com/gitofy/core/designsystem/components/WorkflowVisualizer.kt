package com.gitofy.core.designsystem.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gitofy.core.designsystem.theme.LocalSpacing

/**
 * Dynamic Workflow Visualizer — PRD Addendum: Custom Compose canvas node graph.
 * Renders step-by-step pipeline execution with live node status colors.
 * Green = Success, Red = Failed, Animated Pulse = Running.
 */
@Composable
fun WorkflowVisualizer(
    steps: List<WorkflowStepNode>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        items(steps.size) { index ->
            val step = steps[index]
            val isLast = index == steps.lastIndex
            WorkflowNodeRow(step = step, isLast = isLast)
        }
    }
}

data class WorkflowStepNode(
    val name: String,
    val status: NodeStatus,
    val duration: String? = null
)

enum class NodeStatus { SUCCESS, FAILED, RUNNING, PENDING }

@Composable
private fun WorkflowNodeRow(
    step: WorkflowStepNode,
    isLast: Boolean
) {
    val colors = when (step.status) {
        NodeStatus.SUCCESS -> MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.onTertiaryContainer
        NodeStatus.FAILED -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onErrorContainer
        NodeStatus.RUNNING -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimaryContainer
        NodeStatus.PENDING -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    // Animated pulse for running steps
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val nodeColor = if (step.status == NodeStatus.RUNNING) {
        colors.first.copy(alpha = pulseAlpha)
    } else {
        colors.first
    }

    val outlineColor = MaterialTheme.colorScheme.outline

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Node circle + connector line
        Canvas(
            modifier = Modifier.size(48.dp)
        ) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val radius = this.size.minDimension / 4f

            // Connector line to next node
            if (!isLast) {
                drawLine(
                    color = outlineColor,
                    start = Offset(center.x, center.y + radius),
                    end = Offset(center.x, this.size.height),
                    strokeWidth = 2f
                )
            }

            // Node circle
            drawCircle(
                color = nodeColor,
                radius = radius,
                center = center
            )

            // Inner ring for running
            if (step.status == NodeStatus.RUNNING) {
                drawCircle(
                    color = colors.first,
                    radius = radius * 0.6f,
                    center = center,
                    style = Stroke(width = 3f)
                )
            }
        }

        Spacer(modifier = Modifier.width(LocalSpacing.current.md))

        // Step info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = step.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val statusText = when (step.status) {
                NodeStatus.SUCCESS -> "Success"
                NodeStatus.FAILED -> "Failed"
                NodeStatus.RUNNING -> "Running..."
                NodeStatus.PENDING -> "Pending"
            }
            Row {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.second
                )
                step.duration?.let {
                    Text(
                        text = " · $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
