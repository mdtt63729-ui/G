package com.gitofy.feature.workflows.details

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gitofy.core.designsystem.components.*
import com.gitofy.core.designsystem.components.WorkflowVisualizer
import com.gitofy.core.designsystem.components.WorkflowStepNode
import com.gitofy.core.designsystem.components.NodeStatus
import com.gitofy.core.designsystem.theme.LocalSpacing
import com.gitofy.domain.model.JobSummary
import com.gitofy.domain.model.WorkflowStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowDetailsScreen(
    owner: String,
    repo: String,
    runId: Long,
    onBack: () -> Unit,
    onLogs: (Long) -> Unit,
    onArtifacts: () -> Unit,
    viewModel: WorkflowDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(owner, repo, runId) {
        viewModel.load(owner, repo, runId)
    }

    Scaffold(
        topBar = {
            GITOFYTopAppBar(
                title = "Workflow Details",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onArtifacts) {
                        Icon(Icons.Default.Download, contentDescription = "Artifacts")
                    }
                }
            )
        }
    ) { padding ->
        val run = uiState.run
        if (run == null && uiState.isLoading) {
            LoadingIndicator(modifier = Modifier.padding(padding))
        } else if (run != null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(LocalSpacing.current.lg),
                verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.md)
            ) {
                // Run header
                item {
                    GITOFYCard {
                        Column(modifier = Modifier.padding(LocalSpacing.current.lg)) {
                            Text(run.displayTitle, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(LocalSpacing.current.sm))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val (statusType, statusText) = when (run.status) {
                                    WorkflowStatus.QUEUED -> StatusType.Info to "Queued"
                                    WorkflowStatus.IN_PROGRESS -> StatusType.Warning to "In Progress"
                                    WorkflowStatus.COMPLETED_SUCCESS -> StatusType.Success to "Success"
                                    WorkflowStatus.COMPLETED_FAILURE -> StatusType.Error to "Failure"
                                    WorkflowStatus.CANCELLED -> StatusType.Neutral to "Cancelled"
                                    else -> StatusType.Neutral to "Unknown"
                                }
                                StatusBadge(statusText, statusType)
                                Spacer(modifier = Modifier.weight(1f))
                                Text(run.headBranch, style = MaterialTheme.typography.labelMedium)
                            }
                            Spacer(modifier = Modifier.height(LocalSpacing.current.xs))
                            Text(
                                "by ${run.actorLogin} · ${run.createdAt}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Jobs
                item {
                    Text("Jobs", style = MaterialTheme.typography.titleMedium)
                }

                items(uiState.jobs, key = { it.id }) { job ->
                    // PRD Addendum: Dynamic Workflow Visualizer
                    val steps = job.steps.map { step ->
                        WorkflowStepNode(
                            name = step.name,
                            status = when {
                                step.conclusion == "success" -> NodeStatus.SUCCESS
                                step.conclusion == "failure" -> NodeStatus.FAILED
                                step.status == "in_progress" -> NodeStatus.RUNNING
                                else -> NodeStatus.PENDING
                            }
                        )
                    }
                    GITOFYCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(LocalSpacing.current.lg)) {
                            Text(job.name, style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.height(LocalSpacing.current.sm))
                            WorkflowVisualizer(steps = steps)
                        }
                    }
                }
            }
        } else if (uiState.error != null) {
            ErrorBanner(
                message = uiState.error!!,
                onRetry = { viewModel.load(owner, repo, runId) },
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun JobCard(job: JobSummary, onLogs: () -> Unit) {
    GITOFYCard(modifier = Modifier.fillMaxWidth(), onClick = onLogs) {
        Column(modifier = Modifier.padding(LocalSpacing.current.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (statusType, statusText) = when {
                    job.status == "queued" -> StatusType.Info to "Queued"
                    job.status == "in_progress" -> StatusType.Warning to "Running"
                    job.conclusion == "success" -> StatusType.Success to "Success"
                    job.conclusion == "failure" -> StatusType.Error to "Failure"
                    job.conclusion == "skipped" -> StatusType.Neutral to "Skipped"
                    else -> StatusType.Neutral to job.status
                }
                StatusBadge(statusText, statusType)
                Spacer(modifier = Modifier.weight(1f))
                Icon(Icons.Default.Description, contentDescription = "Logs", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(LocalSpacing.current.sm))
            Text(job.name, style = MaterialTheme.typography.bodyMedium)

            // Steps
            if (job.steps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(LocalSpacing.current.sm))
                job.steps.forEach { step ->
                    Row(
                        modifier = Modifier.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val icon = when (step.conclusion) {
                            "success" -> Icons.Default.Check to MaterialTheme.colorScheme.tertiary
                            "failure" -> Icons.Default.Close to MaterialTheme.colorScheme.error
                            else -> Icons.Default.RadioButtonUnchecked to MaterialTheme.colorScheme.outline
                        }
                        Icon(icon.first, contentDescription = null, modifier = Modifier.size(14.dp), tint = icon.second)
                        Spacer(modifier = Modifier.width(LocalSpacing.current.sm))
                        Text(
                            step.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
