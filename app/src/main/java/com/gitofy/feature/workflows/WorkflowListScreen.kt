package com.gitofy.feature.workflows

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gitofy.core.designsystem.components.*
import com.gitofy.core.designsystem.motion.gitofySlideFadeEnter
import com.gitofy.core.designsystem.motion.gitofySlideFadeExit
import com.gitofy.core.designsystem.theme.LocalSpacing
import com.gitofy.domain.model.WorkflowRunSummary
import com.gitofy.domain.model.WorkflowStatus
import com.gitofy.domain.model.WorkflowSummary

/**
 * PRD §30: Workflow definitions screen.
 * Shows real GitHub workflows, then recent runs.
 */
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
    var expandedWorkflowId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(owner, repo) {
        viewModel.load(owner, repo)
    }

    Scaffold(
        topBar = {
            GITOFYTopAppBar(
                title = "Workflows",
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = { viewModel.refresh(owner, repo) },
                        enabled = !uiState.isRefreshing
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            modifier = Modifier.rotate(
                                animateFloatAsState(
                                    targetValue = if (uiState.isRefreshing) 360f else 0f,
                                    animationSpec = tween(420, easing = LinearEasing),
                                    label = "refreshRotation"
                                ).value
                            )
                        )
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading && uiState.workflows.isEmpty() && uiState.runs.isEmpty() -> {
                Column(modifier = Modifier.padding(padding)) {
                    LazyColumn { items(5) { SkeletonListItem() } }
                }
            }
            uiState.error != null && uiState.workflows.isEmpty() && uiState.runs.isEmpty() -> {
                DeveloperErrorState(
                    message = uiState.error!!,
                    onRetry = { viewModel.refresh(owner, repo) },
                    modifier = Modifier.padding(padding)
                )
            }
            uiState.workflows.isEmpty() && uiState.runs.isEmpty() -> {
                DeveloperEmptyState(
                    icon = Icons.Default.CloudOff,
                    title = "No workflows found",
                    subtitle = "This repository has no GitHub Actions workflows or runs yet.",
                    modifier = Modifier.padding(padding)
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(horizontal = LocalSpacing.current.lg, vertical = LocalSpacing.current.sm),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Workflow definitions section
                    if (uiState.workflows.isNotEmpty()) {
                        item {
                            Text(
                                "Workflows",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = LocalSpacing.current.xs)
                            )
                        }
                        items(uiState.workflows, key = { it.id }) { workflow ->
                            WorkflowCard(
                                workflow = workflow,
                                expanded = expandedWorkflowId == workflow.id,
                                runs = uiState.runsByWorkflow[workflow.id].orEmpty(),
                                isLoadingRuns = uiState.loadingWorkflowId == workflow.id,
                                onClick = {
                                    val wasExpanded = expandedWorkflowId == workflow.id
                                    expandedWorkflowId = if (wasExpanded) null else workflow.id
                                    if (!wasExpanded) viewModel.loadRunsForWorkflow(owner, repo, workflow.id)
                                },
                                onRunClick = onRunClick
                            )
                        }
                    }

                    // Recent runs section
                    if (uiState.runs.isNotEmpty()) {
                        item {
                            Text(
                                "Recent Runs",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = LocalSpacing.current.sm)
                            )
                        }
                        items(uiState.runs, key = { it.id }) { run ->
                            WorkflowRunCard(run = run, onClick = { onRunClick(run.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkflowCard(
    workflow: WorkflowSummary,
    expanded: Boolean,
    runs: List<WorkflowRunSummary>,
    isLoadingRuns: Boolean,
    onClick: () -> Unit,
    onRunClick: (Long) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GITOFYCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(LocalSpacing.current.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(workflow.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(workflow.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (expanded) "Runs" else workflow.state,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse workflow" else "Show workflow runs"
                )
            }
        }
        AnimatedVisibility(visible = expanded, enter = gitofySlideFadeEnter, exit = gitofySlideFadeExit) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(start = 12.dp)) {
                if (isLoadingRuns) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else if (runs.isEmpty()) {
                    Text(
                        "No runs for this workflow yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                } else {
                    runs.forEach { run ->
                        WorkflowRunCard(run = run, onClick = { onRunClick(run.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkflowRunCard(run: WorkflowRunSummary, onClick: () -> Unit) {
    GITOFYCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LocalSpacing.current.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status icon
            val statusColor = when (run.status) {
                WorkflowStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primary
                WorkflowStatus.COMPLETED_SUCCESS -> MaterialTheme.colorScheme.primary
                WorkflowStatus.COMPLETED_FAILURE -> MaterialTheme.colorScheme.error
                WorkflowStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            val statusIcon = when (run.status) {
                WorkflowStatus.IN_PROGRESS -> Icons.Default.PlayArrow
                WorkflowStatus.COMPLETED_SUCCESS -> Icons.Default.CheckCircle
                WorkflowStatus.COMPLETED_FAILURE -> Icons.Default.Cancel
                WorkflowStatus.CANCELLED -> Icons.Default.Cancel
                else -> Icons.Default.Schedule
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    run.displayTitle.ifBlank { run.name },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${run.headBranch} · ${run.actorLogin}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    run.status.name + (run.conclusion?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor
                )
            }
        }
    }
}
