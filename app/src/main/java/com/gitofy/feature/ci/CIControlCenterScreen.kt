package com.gitofy.feature.ci

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gitofy.core.designsystem.components.*
import com.gitofy.core.designsystem.theme.LocalSpacing

/**
 * CI Control Center — PRD Phase 4 §10.
 *
 * High-level operational dashboard. Priority hierarchy is
 * Failed -> Running -> Queued -> Successful, with the failed state made
 * visually dominant (full-width, elevated, first) rather than an equal
 * grid tile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CIControlCenterScreen(
    owner: String,
    repo: String,
    onBack: () -> Unit,
    viewModel: CIControlCenterViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(owner, repo) { viewModel.load(owner, repo) }

    Scaffold(topBar = { GITOFYTopAppBar(title = "CI/CD Control Center", onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(LocalSpacing.current.lg),
            verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.md)
        ) {
            // Failed is the most important state — visually dominant, full width.
            FailedStatusTile(count = uiState.failed)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)) {
                StatusTile("Running", uiState.running, StatusType.Warning, Icons.Default.PlayArrow, Modifier.weight(1f))
                StatusTile("Queued", uiState.queued, StatusType.Info, Icons.Default.Schedule, Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)) {
                StatusTile("Successful", uiState.successful, StatusType.Success, Icons.Default.CheckCircle, Modifier.weight(1f))
                StatusTile("Cancelled", uiState.cancelled, StatusType.Neutral, Icons.Default.Cancel, Modifier.weight(1f))
            }

            if (uiState.recentFailures.isNotEmpty()) {
                SectionHeader("Recent Failures")
                uiState.recentFailures.forEach { failure ->
                    DeveloperCard {
                        Row(modifier = Modifier.padding(LocalSpacing.current.lg), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(LocalSpacing.current.sm))
                            Text(failure, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            if (uiState.slowestBuilds.isNotEmpty()) {
                SectionHeader("Slowest Builds")
                uiState.slowestBuilds.forEach { build ->
                    DeveloperCard {
                        Row(modifier = Modifier.padding(LocalSpacing.current.lg), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(LocalSpacing.current.sm))
                            Text(build, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (uiState.recentFailures.isEmpty() && uiState.slowestBuilds.isEmpty() &&
                uiState.running == 0 && uiState.queued == 0 && uiState.failed == 0 && uiState.successful == 0
            ) {
                DeveloperEmptyState(
                    icon = Icons.Default.Dashboard,
                    title = "No CI activity yet",
                    subtitle = "Workflow runs will show up here once triggered."
                )
            }
        }
    }
}

/** The dominant, always-first failed-state tile — PRD §10 priority hierarchy. */
@Composable
private fun FailedStatusTile(count: Int) {
    val emphasized = count > 0
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (emphasized) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        tonalElevation = if (emphasized) 3.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(LocalSpacing.current.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (emphasized) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(LocalSpacing.current.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "$count",
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (emphasized) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Failed",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (emphasized) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatusTile(label: String, count: Int, type: StatusType, icon: ImageVector, modifier: Modifier = Modifier) {
    DeveloperCard(modifier = modifier) {
        Column(modifier = Modifier.padding(LocalSpacing.current.md), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Text("$count", style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}
