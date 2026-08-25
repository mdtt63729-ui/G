package com.gitofy.feature.workflows

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
import com.gitofy.domain.model.WorkflowRunSummary
import com.gitofy.domain.model.WorkflowStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowListScreen(
    owner: String,
    repo: String,
    onBack: () -> Unit,
    onRunClick: (Long) -> Unit,
    viewModel: WorkflowListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(owner, repo) {
        viewModel.load(owner, repo)
    }

    Scaffold(
        topBar = {
            GITOFYTopAppBar(
                title = "Workflows",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { viewModel.refresh(owner, repo) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading && uiState.runs.isEmpty() -> {
                Column(modifier = Modifier.padding(padding)) {
                    LazyColumn { items(8) { SkeletonListItem() } }
                }
            }
            uiState.runs.isEmpty() && uiState.error != null -> {
                ErrorBanner(
                    message = uiState.error!!,
                    onRetry = { viewModel.load(owner, repo) },
                    modifier = Modifier.padding(padding)
                )
            }
            uiState.runs.isEmpty() -> {
                EmptyStateView(
                    icon = Icons.Default.PlayCircle,
                    title = "No workflow runs found",
                    subtitle = "Trigger a workflow to see runs here.",
                    modifier = Modifier.padding(padding)
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(LocalSpacing.current.lg),
                    verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)
                ) {
                    items(uiState.runs, key = { it.id }) { run ->
                        WorkflowRunCard(run = run, onClick = { onRunClick(run.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkflowRunCard(run: WorkflowRunSummary, onClick: () -> Unit) {
    val (statusType, statusText) = when (run.status) {
        WorkflowStatus.QUEUED -> StatusType.Info to "Queued"
        WorkflowStatus.IN_PROGRESS -> StatusType.Warning to "In Progress"
        WorkflowStatus.COMPLETED_SUCCESS -> StatusType.Success to "Success"
        WorkflowStatus.COMPLETED_FAILURE -> StatusType.Error to "Failure"
        WorkflowStatus.CANCELLED -> StatusType.Neutral to "Cancelled"
        WorkflowStatus.SKIPPED -> StatusType.Neutral to "Skipped"
        WorkflowStatus.TIMED_OUT -> StatusType.Error to "Timed Out"
        WorkflowStatus.UNKNOWN -> StatusType.Neutral to "Unknown"
    }

    GITOFYCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(modifier = Modifier.padding(LocalSpacing.current.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(text = statusText, statusType = statusType)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = run.headBranch,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(LocalSpacing.current.sm))
            Text(
                text = run.displayTitle,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(LocalSpacing.current.xs))
            Text(
                text = "by ${run.actorLogin} · ${run.createdAt}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
