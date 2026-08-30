package com.gitofy.feature.jobs

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gitofy.core.designsystem.components.GITOFYCard
import com.gitofy.core.designsystem.theme.LocalSpacing
import com.gitofy.domain.model.JobStatus
import com.gitofy.domain.model.JobType
import com.gitofy.domain.model.StepStatus

/**
 * PRD §16: Jobs UI — dedicated section/page showing running, completed,
 * failed, and cancelled jobs.
 *
 * PRD §9: GitHub-style job display with step timeline:
 *   ✓ Completed step    → green check
 *   ● Active step        → animated ring
 *   ○ Pending step       → hollow circle
 *   × Failed step        → red cross
 *
 * PRD §42: Empty state — "No active jobs" when nothing is running.
 * PRD §50: No dummy data — all jobs are real.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobsScreen(
    onBack: () -> Unit,
    viewModel: JobsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Jobs", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.activeJobs.isEmpty() &&
            uiState.completedJobs.isEmpty() &&
            uiState.failedJobs.isEmpty() &&
            uiState.cancelledJobs.isEmpty()) {
            // PRD §42: Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.WorkOutline,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        "No active jobs",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Your repository operations will\nappear here in real time.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(LocalSpacing.current.lg),
                verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.md)
            ) {
                // PRD §17: Active jobs — highest priority
                if (uiState.activeJobs.isNotEmpty()) {
                    item {
                        SectionHeader("Running", uiState.activeJobs.size)
                    }
                    items(uiState.activeJobs, key = { it.jobId }) { job ->
                        JobCard(
                            job = job,
                            onClick = { viewModel.selectJob(job.jobId) }
                        )
                    }
                }

                // PRD §19: Failed jobs
                if (uiState.failedJobs.isNotEmpty()) {
                    item {
                        SectionHeader("Failed", uiState.failedJobs.size)
                    }
                    items(uiState.failedJobs, key = { it.jobId }) { job ->
                        JobCard(
                            job = job,
                            onClick = { viewModel.selectJob(job.jobId) }
                        )
                    }
                }

                // PRD §18: Completed jobs
                if (uiState.completedJobs.isNotEmpty()) {
                    item {
                        SectionHeader("Completed", uiState.completedJobs.size)
                    }
                    items(uiState.completedJobs, key = { it.jobId }) { job ->
                        JobCard(
                            job = job,
                            onClick = { viewModel.selectJob(job.jobId) }
                        )
                    }
                }

                // Cancelled jobs
                if (uiState.cancelledJobs.isNotEmpty()) {
                    item {
                        SectionHeader("Cancelled", uiState.cancelledJobs.size)
                    }
                    items(uiState.cancelledJobs, key = { it.jobId }) { job ->
                        JobCard(
                            job = job,
                            onClick = { viewModel.selectJob(job.jobId) }
                        )
                    }
                }
            }
        }
    }

    // PRD §20: Job Details dialog
    uiState.selectedJobId?.let { jobId ->
        val allJobs = uiState.activeJobs + uiState.completedJobs + uiState.failedJobs + uiState.cancelledJobs
        val selectedJob = allJobs.find { it.jobId == jobId }
        if (selectedJob != null) {
            JobDetailsDialog(
                job = selectedJob,
                steps = uiState.selectedJobSteps,
                onDismiss = { viewModel.selectJob(null) }
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun JobCard(
    job: JobUiModel,
    onClick: () -> Unit
) {
    GITOFYCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(LocalSpacing.current.lg),
            verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)
        ) {
            // Header: operation type + repository
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                StepStatusIcon(
                    status = when (job.status) {
                        JobStatus.RUNNING -> StepStatus.RUNNING
                        JobStatus.COMPLETED -> StepStatus.SUCCESS
                        JobStatus.FAILED -> StepStatus.FAILED
                        JobStatus.CANCELLED -> StepStatus.CANCELLED
                        else -> StepStatus.PENDING
                    },
                    size = 20.dp
                )
                Spacer(modifier = Modifier.width(LocalSpacing.current.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = operationDisplayName(job.operationType),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = job.repository,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        val clipboard = LocalClipboardManager.current
                        IconButton(
                            onClick = { clipboard.setText(AnnotatedString(job.repository)) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy repository",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                // Duration
                Text(
                    text = formatDuration(job.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // PRD §8: Progress bar — real progress only
            if (job.isActive && job.progress > 0f) {
                LinearProgressIndicator(
                    progress = { job.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${job.progressPercent}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (job.totalItems > 0) {
                        Text(
                            text = "${job.completedItems} / ${job.totalItems}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (job.isActive) {
                // PRD §12: Indeterminate state
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            // PRD §9: Step timeline (compact — show current + completed)
            if (job.steps.isNotEmpty()) {
                val visibleSteps = job.steps.take(5)
                visibleSteps.forEach { step ->
                    StepRow(step)
                }
                if (job.steps.size > 5) {
                    Text(
                        text = "+${job.steps.size - 5} more steps",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 28.dp)
                    )
                }
            }

            // PRD §19: Error display for failed jobs
            job.error?.let { error ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Error",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            val clipboard = LocalClipboardManager.current
                            IconButton(
                                onClick = { clipboard.setText(AnnotatedString(error)) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copy job error",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // PRD §27: Commit SHA for completed jobs
            if (job.commitSha.isNotBlank() && job.status == JobStatus.COMPLETED) {
                val clipboard = LocalClipboardManager.current
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Commit: ${job.commitSha.take(7)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { clipboard.setText(AnnotatedString(job.commitSha)) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy commit SHA",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepRow(step: JobStepUi) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        StepStatusIcon(status = step.status, size = 16.dp)
        Spacer(modifier = Modifier.width(LocalSpacing.current.sm))
        Text(
            text = step.displayName,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            color = when (step.status) {
                StepStatus.SUCCESS -> MaterialTheme.colorScheme.onSurface
                StepStatus.RUNNING -> MaterialTheme.colorScheme.primary
                StepStatus.FAILED -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.outline
            }
        )
        // PRD §8: File count for running steps
        if (step.status == StepStatus.RUNNING && step.totalItems > 0) {
            Text(
                text = "${step.completedItems}/${step.totalItems}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (step.status == StepStatus.SUCCESS && step.durationMs > 0) {
            Text(
                text = formatDuration(step.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun StepStatusIcon(status: StepStatus, size: androidx.compose.ui.unit.Dp) {
    when (status) {
        StepStatus.SUCCESS -> {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(size * 0.7f),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        StepStatus.RUNNING -> {
            // PRD §9: Animated ring for active step
            val infiniteTransition = rememberInfiniteTransition(label = "stepRing")
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "ringRotation"
            )
            CircularProgressIndicator(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
                strokeWidth = 2.dp
            )
        }
        StepStatus.FAILED -> {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(size * 0.7f),
                    tint = MaterialTheme.colorScheme.onError
                )
            }
        }
        StepStatus.CANCELLED -> {
            Text(
                "—",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.size(size)
            )
        }
        StepStatus.PENDING -> {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
        StepStatus.SKIPPED -> {
            Text(
                "—",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(size)
            )
        }
    }
}

@Composable
private fun JobDetailsDialog(
    job: JobUiModel,
    steps: List<JobStepUi>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(operationDisplayName(job.operationType))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val clipboard = LocalClipboardManager.current
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Job ID: ${job.jobId}", modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = { clipboard.setText(AnnotatedString(job.jobId)) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy job ID")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Repository: ${job.repository}", modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = { clipboard.setText(AnnotatedString(job.repository)) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy repository")
                    }
                }
                Text("Status: ${job.status.name}")
                Text("Started: ${formatTime(job.startedAt)}")
                if (job.durationMs > 0) {
                    Text("Duration: ${formatDuration(job.durationMs)}")
                }
                if (job.commitSha.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Commit: ${job.commitSha.take(7)}", modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { clipboard.setText(AnnotatedString(job.commitSha)) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy commit SHA")
                        }
                    }
                }
                job.error?.let { error ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text("Error: $error", color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { clipboard.setText(AnnotatedString(error)) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy job error")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("Steps", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                steps.forEach { step -> StepRow(step) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

// Helpers
private fun operationDisplayName(type: JobType): String = when (type) {
    JobType.CREATE_REPOSITORY -> "Create Repository"
    JobType.UPDATE_REPOSITORY -> "Update Repository"
    JobType.AI_REPOSITORY_CHANGE -> "Gito Change"
    JobType.CLONE -> "Clone"
    JobType.PULL -> "Pull"
    JobType.PUSH -> "Push"
    JobType.COMMIT -> "Commit"
    JobType.BRANCH -> "Branch"
    JobType.PULL_REQUEST -> "Pull Request"
    JobType.WORKFLOW -> "Workflow"
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return ""
    val seconds = ms / 1000.0
    return if (seconds < 60) "${String.format("%.1f", seconds)}s"
    else "${(seconds / 60).toInt()}m ${(seconds % 60).toInt()}s"
}

private fun formatTime(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}
