package com.gitofy.feature.ci

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gitofy.core.designsystem.components.GITOFYCard
import com.gitofy.core.designsystem.theme.LocalSpacing

/**
 * Build Timeline — PRD v4.5 Section 35.
 * Displays each step with its duration, identifies slow stages.
 */
@Composable
fun BuildTimelineComponent(steps: List<BuildStep>) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)
    ) {
        steps.forEach { step ->
            GITOFYCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(LocalSpacing.current.lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(step.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(formatDuration(step.durationMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (step.durationMs > 60_000) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("⚠ Slow", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

data class BuildStep(val name: String, val durationMs: Long)

private fun formatDuration(ms: Long): String = when {
    ms < 1000 -> "${ms}ms"
    ms < 60_000 -> "${ms / 1000}s"
    else -> "${ms / 60_000}m ${(ms % 60_000) / 1000}s"
}
