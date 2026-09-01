package com.gitofy.feature.operationcenter

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

/**
 * Operation Center — PRD v3.0 Section 55.
 * Centralized operation history/state screen.
 * Displays active and recent operations with status, type, timing, and retry actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationCenterScreen(
    onBack: () -> Unit,
    viewModel: OperationCenterViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            GITOFYTopAppBar(
                title = "Operations",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { viewModel.clearFinished() }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear finished")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(LocalSpacing.current.lg),
            verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)
        ) {
            // Active operations
            if (uiState.activeOperations.isNotEmpty()) {
                item {
                    Text("Active", style = MaterialTheme.typography.titleMedium)
                }
                items(uiState.activeOperations, key = { it.id }) { op ->
                    OperationCard(op)
                }
            }

            // Recent operations
            if (uiState.recentOperations.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(LocalSpacing.current.md))
                    Text("History", style = MaterialTheme.typography.titleMedium)
                }
                items(uiState.recentOperations, key = { it.id }) { op ->
                    OperationCard(op)
                }
            }

            if (uiState.activeOperations.isEmpty() && uiState.recentOperations.isEmpty()) {
                item {
                    EmptyStateView(
                        icon = Icons.Default.History,
                        title = "No operations yet",
                        subtitle = "Operations will appear here when you create repositories, trigger workflows, or download artifacts.",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun OperationCard(op: OperationDisplay) {
    val (statusType, statusText) = when (op.status) {
        "QUEUED" -> StatusType.Info to "Queued"
        "RUNNING" -> StatusType.Warning to "Running"
        "COMPLETED" -> StatusType.Success to "Success"
        "FAILED" -> StatusType.Error to "Failed"
        "CANCELLED" -> StatusType.Neutral to "Cancelled"
        else -> StatusType.Neutral to op.status
    }

    val typeLabel = when (op.type) {
        "GIT_PUSH" -> "Repository Upload"
        "ZIP_EXTRACTION" -> "ZIP Extraction"
        "WORKFLOW_SYNC" -> "Workflow Sync"
        "ARTIFACT_DOWNLOAD" -> "Artifact Download"
        else -> op.type
    }

    GITOFYCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(LocalSpacing.current.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when (op.type) {
                        "GIT_PUSH" -> Icons.Default.CloudUpload
                        "ZIP_EXTRACTION" -> Icons.Default.Archive
                        "ARTIFACT_DOWNLOAD" -> Icons.Default.Download
                        else -> Icons.Default.Sync
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(LocalSpacing.current.md))
                Text(typeLabel, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                StatusBadge(statusText, statusType)
            }

            Spacer(modifier = Modifier.height(LocalSpacing.current.sm))

            if (op.status == "RUNNING") {
                Text(
                    op.currentStage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(LocalSpacing.current.xs))
                LinearProgressIndicator(
                    progress = { op.progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (op.errorMessage != null) {
                Spacer(modifier = Modifier.height(LocalSpacing.current.sm))
                Text(
                    op.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
