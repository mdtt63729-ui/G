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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gitofy.core.designsystem.components.*
import com.gitofy.core.designsystem.theme.LocalSpacing

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
            // Status grid
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)) {
                StatusTile("Running", uiState.running, StatusType.Warning, Icons.Default.PlayArrow, Modifier.weight(1f))
                StatusTile("Queued", uiState.queued, StatusType.Info, Icons.Default.Schedule, Modifier.weight(1f))
                StatusTile("Failed", uiState.failed, StatusType.Error, Icons.Default.Error, Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)) {
                StatusTile("Success", uiState.successful, StatusType.Success, Icons.Default.CheckCircle, Modifier.weight(1f))
                StatusTile("Cancelled", uiState.cancelled, StatusType.Neutral, Icons.Default.Cancel, Modifier.weight(1f))
            }

            if (uiState.recentFailures.isNotEmpty()) {
                Text("Recent Failures", style = MaterialTheme.typography.titleSmall)
                uiState.recentFailures.forEach { failure ->
                    GITOFYCard(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(LocalSpacing.current.lg), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(LocalSpacing.current.sm))
                            Text(failure, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            if (uiState.slowestBuilds.isNotEmpty()) {
                Text("Slowest Builds", style = MaterialTheme.typography.titleSmall)
                uiState.slowestBuilds.forEach { build ->
                    GITOFYCard(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(LocalSpacing.current.lg), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(LocalSpacing.current.sm))
                            Text(build, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusTile(label: String, count: Int, type: StatusType, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    GITOFYCard(modifier = modifier) {
        Column(modifier = Modifier.padding(LocalSpacing.current.md), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Text("$count", style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}
