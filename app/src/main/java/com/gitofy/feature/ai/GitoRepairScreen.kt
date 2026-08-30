package com.gitofy.feature.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gitofy.ai.autonomous.GitoRepairOrchestrator
import com.gitofy.core.designsystem.components.GITOFYTopAppBar
import com.gitofy.core.designsystem.theme.LocalSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitoRepairScreen(
    owner: String, repo: String, runId: Long, jobId: Long,
    workflowId: String, branch: String, commitSha: String,
    failedJobName: String, failedStepName: String,
    onBack: () -> Unit, onViewLogs: (Long) -> Unit,
    viewModel: GitoRepairViewModel = hiltViewModel()
) {
    val repairState by viewModel.repairState.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(owner, repo, runId, jobId) {
        if (!repairState.isRunning && repairState.status == GitoRepairOrchestrator.GitoRepairStatus.DETECTED) {
            viewModel.startRepair(owner, repo, runId, jobId, workflowId, branch, commitSha, failedJobName, failedStepName)
        }
    }

    Scaffold(
        topBar = {
            GITOFYTopAppBar(title = "Gito AI", onBack = onBack, actions = {
                if (repairState.isRunning) {
                    IconButton(onClick = { viewModel.cancelRepair() }) { Icon(Icons.Default.Close, contentDescription = "Cancel repair") }
                }
            })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = LocalSpacing.current.lg, vertical = LocalSpacing.current.sm),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { FailureContextCard(owner, repo, branch, runId, failedJobName, failedStepName, repairState.errorLog.isNotEmpty()) }
            item { RepairStatusHeader(repairState.status, repairState.attempt, repairState.maxAttempts) }
            if (repairState.errorLog.isNotEmpty()) {
                item { BuildErrorCard(repairState.errorLog, { onViewLogs(jobId) }) }
            }
            if (repairState.rootCause.isNotEmpty()) {
                item { AiAnalysisCard(repairState.rootCause, repairState.affectedFiles) }
            }
            item { RepairTimelineCard(repairState.timeline) }
            if (repairState.status == GitoRepairOrchestrator.GitoRepairStatus.SUCCESS) {
                item { SuccessCard(repairState.commitSha, repairState.verificationRunId) }
            }
            if (repairState.status == GitoRepairOrchestrator.GitoRepairStatus.FAILED || repairState.status == GitoRepairOrchestrator.GitoRepairStatus.STOPPED) {
                item { FailureResultCard(repairState.status, repairState.errorMessage, { viewModel.retry() }, { onViewLogs(jobId) }) }
            }
        }
    }
}

@Composable
private fun FailureContextCard(owner: String, repo: String, branch: String, runId: Long, failedJobName: String, failedStepName: String, logAttached: Boolean) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Build, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Build Failure", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.weight(1f))
                Text("#$runId", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("$owner/$repo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Branch: $branch", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Job: $failedJobName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (failedStepName.isNotBlank()) Text("Step: $failedStepName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (logAttached) Icons.Default.AttachFile else Icons.Default.Description, contentDescription = null, tint = if (logAttached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (logAttached) "1 error log attached" else "Collecting logs...", style = MaterialTheme.typography.labelSmall, color = if (logAttached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RepairStatusHeader(status: GitoRepairOrchestrator.GitoRepairStatus, attempt: Int, maxAttempts: Int) {
    val (icon, color, text) = when (status) {
        GitoRepairOrchestrator.GitoRepairStatus.DETECTED -> Triple(Icons.Default.BugReport, MaterialTheme.colorScheme.error, "Build failure detected")
        GitoRepairOrchestrator.GitoRepairStatus.COLLECTING_LOGS -> Triple(Icons.Default.Download, MaterialTheme.colorScheme.primary, "Collecting error logs...")
        GitoRepairOrchestrator.GitoRepairStatus.ANALYZING -> Triple(Icons.Default.Psychology, MaterialTheme.colorScheme.primary, "Analyzing build failure...")
        GitoRepairOrchestrator.GitoRepairStatus.INSPECTING_REPOSITORY -> Triple(Icons.Default.FolderOpen, MaterialTheme.colorScheme.primary, "Inspecting repository...")
        GitoRepairOrchestrator.GitoRepairStatus.PLANNING_FIX -> Triple(Icons.Default.Build, MaterialTheme.colorScheme.primary, "Planning fix...")
        GitoRepairOrchestrator.GitoRepairStatus.MODIFYING -> Triple(Icons.Default.Edit, MaterialTheme.colorScheme.primary, "Applying modifications...")
        GitoRepairOrchestrator.GitoRepairStatus.VALIDATING -> Triple(Icons.Default.Verified, MaterialTheme.colorScheme.primary, "Validating changes...")
        GitoRepairOrchestrator.GitoRepairStatus.COMMITTING -> Triple(Icons.Default.Commit, MaterialTheme.colorScheme.primary, "Creating commit...")
        GitoRepairOrchestrator.GitoRepairStatus.PUSHING -> Triple(Icons.Default.CloudUpload, MaterialTheme.colorScheme.primary, "Pushing fix...")
        GitoRepairOrchestrator.GitoRepairStatus.TRIGGERING_BUILD -> Triple(Icons.Default.PlayArrow, MaterialTheme.colorScheme.primary, "Triggering verification build...")
        GitoRepairOrchestrator.GitoRepairStatus.VERIFYING -> Triple(Icons.Default.Timeline, MaterialTheme.colorScheme.primary, "Monitoring verification build...")
        GitoRepairOrchestrator.GitoRepairStatus.SUCCESS -> Triple(Icons.Default.CheckCircle, MaterialTheme.colorScheme.primary, "Repair successful")
        GitoRepairOrchestrator.GitoRepairStatus.FAILED -> Triple(Icons.Default.Error, MaterialTheme.colorScheme.error, "Repair failed")
        GitoRepairOrchestrator.GitoRepairStatus.STOPPED -> Triple(Icons.Default.Block, MaterialTheme.colorScheme.onSurfaceVariant, "Repair stopped")
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f))) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color)
                if (attempt > 0 && status != GitoRepairOrchestrator.GitoRepairStatus.SUCCESS)
                    Text("Attempt $attempt of $maxAttempts", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BuildErrorCard(errorLog: String, onViewFullLogs: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Build Error", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { clipboardManager.setText(AnnotatedString(errorLog)) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(4.dp)); Text("Copy")
                }
                TextButton(onClick = onViewFullLogs) { Text("View Full Logs") }
            }
            Spacer(modifier = Modifier.height(8.dp))
            val displayLog = if (errorLog.length > 2000) errorLog.take(2000) + "\n\n... (more chars)" else errorLog
            Text(displayLog, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 15, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AiAnalysisCard(rootCause: String, affectedFiles: List<String>) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("AI Analysis", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Root Cause:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(rootCause, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (affectedFiles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Affected Files:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                affectedFiles.forEach { Text("  - $it", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
            }
        }
    }
}

@Composable
private fun RepairTimelineCard(timeline: List<GitoRepairOrchestrator.TimelineEvent>) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Repair Timeline", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(12.dp))
            timeline.forEach { event ->
                val color = if (event.isComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                val icon = if (event.isComplete) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(event.message, style = MaterialTheme.typography.bodySmall, color = if (event.isComplete) MaterialTheme.colorScheme.onSurface else color, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SuccessCard(commitSha: String, verificationRunId: Long) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Build is fixed and the updated project has been pushed successfully.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            if (commitSha.isNotBlank()) { Spacer(modifier = Modifier.height(8.dp)); Text("Commit: ${commitSha.take(7)}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (verificationRunId > 0) Text("Verification Run: #$verificationRunId", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FailureResultCard(status: GitoRepairOrchestrator.GitoRepairStatus, errorMessage: String?, onRetry: () -> Unit, onViewLogs: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (status == GitoRepairOrchestrator.GitoRepairStatus.STOPPED) "Repair Stopped" else "Repair Failed", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
            }
            if (errorMessage != null) { Spacer(modifier = Modifier.height(8.dp)); Text(errorMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(modifier = Modifier.height(12.dp))
            Row {
                OutlinedButton(onClick = onViewLogs) { Text("View Logs") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}
