package com.gitofy.feature.ci

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.gitofy.core.designsystem.components.GITOFYCard
import com.gitofy.core.designsystem.theme.LocalSpacing

/**
 * Matrix Build Visualization — PRD v4.5 Section 34.
 * For matrix workflows: OS, JDK, Result table with filtering.
 */
@Composable
fun MatrixBuildVisualization(matrixRuns: List<MatrixRun>) {
    var filter by remember { mutableStateOf(MatrixFilter.ALL) }

    val filtered = when (filter) {
        MatrixFilter.ALL -> matrixRuns
        MatrixFilter.PASSED -> matrixRuns.filter { it.result == MatrixResult.SUCCESS }
        MatrixFilter.FAILED -> matrixRuns.filter { it.result == MatrixResult.FAILURE }
    }

    Column(verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.xs)) {
            MatrixFilter.entries.forEach { f ->
                FilterChip(
                    selected = filter == f,
                    onClick = { filter = f },
                    label = { Text(f.displayName) }
                )
            }
        }
        filtered.forEach { run ->
            GITOFYCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(LocalSpacing.current.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(run.os, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(run.jdk, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    when (run.result) {
                        MatrixResult.SUCCESS -> Text("✓", color = MaterialTheme.colorScheme.primary)
                        MatrixResult.FAILURE -> Text("✕", color = MaterialTheme.colorScheme.error)
                        MatrixResult.RUNNING -> Text("●", color = MaterialTheme.colorScheme.tertiary)
                        MatrixResult.QUEUED -> Text("○", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

data class MatrixRun(val os: String, val jdk: String, val result: MatrixResult)
enum class MatrixResult { SUCCESS, FAILURE, RUNNING, QUEUED }
enum class MatrixFilter(val displayName: String) { ALL("All"), PASSED("Passed"), FAILED("Failed") }
