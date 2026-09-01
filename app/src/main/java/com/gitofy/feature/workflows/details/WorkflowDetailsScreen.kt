package com.gitofy.feature.workflows.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gitofy.core.designsystem.components.*
import com.gitofy.core.designsystem.theme.LocalSpacing
import com.gitofy.domain.model.JobSummary
import com.gitofy.domain.model.WorkflowRunSummary
import com.gitofy.domain.model.WorkflowStatus
import java.time.Duration
import java.time.Instant

/**
 * PRD §42: Run detail screen with status, commit, branch, duration, jobs.
 * PRD §43: Run actions (cancel, rerun, rerun failed jobs, view artifacts).
 * PRD §46: Job live timer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowDetailsScreen(
    owner: String,
    repo: String,
    runId: Long,
    onBack: () -> Unit,
    onLogs: (Long) -> Unit,
    onArtifacts: () -> Unit,
    onGitoAiRepair: (String) -> Unit = {},
    viewModel: WorkflowDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(owner, repo, runId) {
        viewModel.load(owner, repo, runId)
    }

    Scaffold(
        topBar = {
            GITOFYTopAppBar(
                title = uiState.run?.displayTitle?.ifBlank { uiState.run?.name ?: "Run" } ?: "Run",
                onBack = onBack,
                actions = {
                    var showMenu by remember { mutableStateOf(false) }
                    val run = uiState.run
                    if (run != null) {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Actions")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            // PRD §43: Context-aware actions
                            if (run.status == WorkflowStatus.IN_PROGRESS || run.status == WorkflowStatus.QUEUED) {
                                DropdownMenuItem(
                                    text = { Text("Cancel workflow") },
                                    onClick = {
                                        showMenu = false
                                        viewModel.cancelRun(owner, repo, runId)
                                    }
                                )
                            }
                            if (run.status == WorkflowStatus.COMPLETED_SUCCESS || run.status == WorkflowStatus.COMPLETED_FAILURE) {
                                DropdownMenuItem(
                                    text = { Text("Re-run") },
                                    onClick = {
                                        showMenu = false
                                        viewModel.rerunRun(owner, repo, runId)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Re-run failed jobs") },
                                    onClick = {
                                        showMenu = false
                                        viewModel.rerunFailedJobs(owner, repo, runId)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("View artifacts") },
                                onClick = {
                                    showMenu = false
                                    onArtifacts()
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isInitialLoading -> {
                Column(modifier = Modifier.padding(padding)) {
                    LazyColumn { items(5) { SkeletonListItem() } }
                }
            }
            uiState.error != null && uiState.run == null -> {
                DeveloperErrorState(
                    message = uiState.error!!,
                    onRetry = { viewModel.load(owner, repo, runId) },
                    modifier = Modifier.padding(padding)
                )
            }
            uiState.run != null -> {
                val run = uiState.run!!
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(horizontal = LocalSpacing.current.lg, vertical = LocalSpacing.current.sm),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Status section
                    item {
                        RunStatusSection(run = run, ticker = uiState.ticker)
                    }

                    // Trigger info
                    item {
                        InfoCard(
                            commitSha = run.headSha,
                            branch = run.headBranch,
                            triggeredBy = run.actorLogin
                        )
                    }

                    // Duration
                    item {
                        DurationSection(run = run, ticker = uiState.ticker)
                    }

                    // Jobs section
                    if (uiState.jobs.isNotEmpty()) {
                        item {
                            Text(
                                "JOBS",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = LocalSpacing.current.md, bottom = LocalSpacing.current.xs)
                            )
                        }
                        items(uiState.jobs, key = { "job-${it.id}" }) { job ->
                            JobCard(
                                job = job,
                                ticker = uiState.ticker,
                                onClick = { onLogs(job.id) },
                                onGitoAiRepair = {
                                    val run = uiState.run
                                    val failedStepName = job.steps.firstOrNull { it.conclusion == "failure" }?.name ?: ""
                                    val route = buildGitoRepairRoute(
                                        owner, repo, runId, job.id,
                                        run?.name ?: "", run?.headBranch ?: "main",
                                        run?.headSha ?: "", job.name, failedStepName
                                    )
                                    onGitoAiRepair(route)
                                }
                            )
                        }
                    }
                }
            }
            else -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun RunStatusSection(run: WorkflowRunSummary, ticker: Long) {
    val (icon, color, text) = when (run.status) {
        WorkflowStatus.QUEUED -> Triple(Icons.Default.Schedule, MaterialTheme.colorScheme.onSurfaceVariant, "Queued")
        WorkflowStatus.IN_PROGRESS -> Triple(Icons.Default.PlayArrow, MaterialTheme.colorScheme.primary, "Running")
        WorkflowStatus.COMPLETED_SUCCESS -> Triple(Icons.Default.CheckCircle, MaterialTheme.colorScheme.primary, "Success")
        WorkflowStatus.COMPLETED_FAILURE -> Triple(Icons.Default.Cancel, MaterialTheme.colorScheme.error, "Failure")
        WorkflowStatus.CANCELLED -> Triple(Icons.Default.Block, MaterialTheme.colorScheme.onSurfaceVariant, "Cancelled")
        else -> Triple(Icons.Default.Help, MaterialTheme.colorScheme.onSurfaceVariant, run.status.name)
    }

    GITOFYCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(LocalSpacing.current.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color)
        }
    }
}

@Composable
private fun InfoCard(commitSha: String, branch: String, triggeredBy: String) {
    GITOFYCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(LocalSpacing.current.lg)) {
            InfoRow("Commit", commitSha.take(7))
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            InfoRow("Branch", branch)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            InfoRow("Triggered by", triggeredBy)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DurationSection(run: WorkflowRunSummary, ticker: Long) {
    // PRD §40: Duration calculation
    val duration = remember(run, ticker) {
        val startTime = run.runStartedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?: run.createdAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val endTime = if (run.status == WorkflowStatus.COMPLETED_SUCCESS || run.status == WorkflowStatus.COMPLETED_FAILURE) {
            run.updatedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
        } else {
            Instant.now()
        }
        if (startTime != null && endTime != null) {
            val d = Duration.between(startTime, endTime)
            "${d.toMinutes()}m ${d.seconds % 60}s"
        } else {
            "—"
        }
    }

    GITOFYCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(LocalSpacing.current.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(12.dp))
            Text("Duration", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.weight(1f))
            Text(duration, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun JobCard(
    job: JobSummary,
    ticker: Long,
    onClick: () -> Unit,
    onGitoAiRepair: () -> Unit
) {
    val isFailed = job.conclusion == "failure"
    val (icon, color, statusText) = when {
        job.status == "queued" -> Triple(Icons.Default.Schedule, MaterialTheme.colorScheme.onSurfaceVariant, "Queued")
        job.status == "in_progress" -> Triple(Icons.Default.PlayArrow, MaterialTheme.colorScheme.primary, "In progress")
        job.conclusion == "success" -> Triple(Icons.Default.CheckCircle, MaterialTheme.colorScheme.primary, "Success")
        job.conclusion == "failure" -> Triple(Icons.Default.Cancel, MaterialTheme.colorScheme.error, "Failure")
        job.conclusion == "cancelled" -> Triple(Icons.Default.Block, MaterialTheme.colorScheme.onSurfaceVariant, "Cancelled")
        job.conclusion == "skipped" -> Triple(Icons.Default.SkipNext, MaterialTheme.colorScheme.onSurfaceVariant, "Skipped")
        else -> Triple(Icons.Default.Help, MaterialTheme.colorScheme.onSurfaceVariant, job.status)
    }

    // PRD §46: Job live timer — safe against blank/null timestamps
    val duration = remember(job, ticker) {
        val start = job.startedAt?.takeIf { it.isNotBlank() }
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val end = if (job.status == "completed") {
            job.completedAt?.takeIf { it.isNotBlank() }
                ?.let { runCatching { Instant.parse(it) }.getOrNull() }
        } else {
            Instant.now()
        }
        if (start != null && end != null) {
            val d = Duration.between(start, end)
            "${d.toMinutes()}m ${d.seconds % 60}s"
        } else {
            "—"
        }
    }

    GITOFYCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(LocalSpacing.current.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    job.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(statusText, style = MaterialTheme.typography.labelSmall, color = color)
            }
            Text(duration, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // PRD §7: Gito AI icon — ONLY on failed jobs
            if (isFailed) {
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = onGitoAiRepair, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "Fix with Gito AI", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

private fun buildGitoRepairRoute(
    owner: String, repo: String, runId: Long, jobId: Long,
    workflowId: String, branch: String, commitSha: String,
    failedJobName: String, failedStepName: String
): String {
    val encWorkflowId = java.net.URLEncoder.encode(workflowId, "UTF-8")
    val encBranch = java.net.URLEncoder.encode(branch, "UTF-8")
    val encFailedJobName = java.net.URLEncoder.encode(failedJobName, "UTF-8")
    val encFailedStepName = java.net.URLEncoder.encode(failedStepName, "UTF-8")
    return "gito_repair/$owner/$repo/$runId/$jobId/$encWorkflowId/$encBranch/$commitSha/$encFailedJobName/$encFailedStepName"
}
