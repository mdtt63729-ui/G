package com.gitofy.feature.createproject

import com.gitofy.core.designsystem.motion.gitofySlideFadeEnter
import com.gitofy.core.designsystem.motion.gitofySlideFadeExit

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gitofy.core.designsystem.components.GITOFYButton
import com.gitofy.core.designsystem.components.PremiumSuccessCheck
import com.gitofy.core.designsystem.components.GITOFYButtonType
import com.gitofy.core.designsystem.motion.GITOFYStaggeredVisibility

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadProgressScreen(
    operationId: String,
    onComplete: (String, String) -> Unit,
    onEdit: () -> Unit,
    viewModel: UploadProgressViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(operationId) {
        viewModel.startMonitoring(operationId)
    }

    // PRD PHASE 21: Back button safety — confirmation dialog
    var showBackDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (uiState.isComplete) "Repository ready" else "Update Repository",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.isComplete || uiState.isFailed || uiState.isCancelled) {
                            onEdit()
                        } else {
                            showBackDialog = true
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedContent(
                targetState = when {
                    uiState.isComplete -> "complete"
                    uiState.isFailed -> "failed"
                    uiState.isCancelled -> "cancelled"
                    else -> "uploading"
                },
                transitionSpec = {
                    gitofySlideFadeEnter.togetherWith(gitofySlideFadeExit)
                },
                label = "upload-state"
            ) { state ->
                when (state) {
                    "complete" -> SuccessContent(uiState, onComplete)
                    "failed" -> FailureContent(uiState, onEdit)
                    "cancelled" -> CancelledContent(uiState, onEdit)
                    else -> UploadingContent(uiState, viewModel, operationId)
                }
            }
        }
    }

    // PRD PHASE 21: Back button safety dialog
    if (showBackDialog) {
        AlertDialog(
            onDismissRequest = { showBackDialog = false },
            title = { Text("Upload in progress") },
            text = { Text("You can leave this screen. The upload continues safely in the background.") },
            confirmButton = {
                TextButton(onClick = { showBackDialog = false; onEdit() }) {
                    Text("Leave")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackDialog = false }) {
                    Text("Stay")
                }
            }
        )
    }
}

// ===========================================================================
// PRD PHASE 3-5: Premium progress header — percentage, progress bar, data
// ===========================================================================

@Composable
private fun UploadingContent(
    state: UploadProgressUiState,
    viewModel: UploadProgressViewModel,
    operationId: String
) {
    // Smooth progress animation without target-chasing lag
    val animatedProgress by animateFloatAsState(
        targetValue = state.progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 140, easing = LinearEasing),
        label = "progressAnim"
    )

    // PRD PHASE 10: Centralized ticker for elapsed time
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.operationStartedAt) {
        while (true) {
            if (state.operationStartedAt > 0) {
                elapsedSeconds = ((System.currentTimeMillis() - state.operationStartedAt) / 1000).toInt()
            }
            kotlinx.coroutines.delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Repository name (PRD PHASE 27: Visual hierarchy #1)
        Text(
            text = state.repoName.ifBlank { state.projectName.ifBlank { "Your project" } },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Progress card (PRD PHASE 2-3)
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // PRD PHASE 3: Progress percentage as integer
                // PRD PHASE 5: Smooth animation
                Text(
                    text = "${state.progressPercent}%",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics {
                        contentDescription = "${state.progressPercent} percent complete"
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // PRD PHASE 5: Smooth progress bar
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Source bytes are meaningful during indexing. During the
                // native Git pack transfer, show the real network byte count
                // reported by libgit2 instead of relabelling source bytes.
                if (state.currentFile.startsWith("Pushing Git objects")) {
                    Text(
                        text = state.currentFile,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (state.totalBytes > 0) {
                    Text(
                        text = "Processed ${formatBytes(state.bytesUploaded)} / ${formatBytes(state.totalBytes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // PRD PHASE 12: Upload speed
                if (state.uploadSpeed > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${formatSpeed(state.uploadSpeed)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Current operation (PRD PHASE 7)
                Text(
                    text = state.currentStageDisplayName + "...",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Current file (PRD PHASE 7)
                if (state.currentFile.isNotBlank() && state.currentFile != "file_0_of_0") {
                    AnimatedContent(
                        targetState = state.currentFile,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                        label = "upload-current-file"
                    ) { file ->
                        Text(
                            text = file,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Elapsed time (PRD PHASE 10)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Elapsed: ${formatTime(elapsedSeconds)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // PRD PHASE 13: ETA
            if (state.etaSeconds > 0 && state.etaSeconds < 3600) {
                Text(
                    text = "About ${formatTime(state.etaSeconds.toInt())} remaining",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // PRD PHASE 14: File count
        if (state.totalFiles > 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${state.filesCompleted} / ${state.totalFiles} files",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // PRD PHASE 8: Step timeline
        Text(
            "STEPS",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                state.steps.forEach { step ->
                    StepRow(step)
                }
                // If no steps parsed, show default
                if (state.steps.isEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            state.currentStageDisplayName,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // PRD PHASE 20: Cancel button
        GITOFYButton(
            "Cancel",
            onClick = { viewModel.cancel(operationId) },
            type = GITOFYButtonType.Outlined,
            fullWidth = true
        )
    }
}

// ===========================================================================
// PRD PHASE 8: Step row with status icon + timer
// ===========================================================================

@Composable
private fun StepRow(step: UploadStepInfo) {
    // PRD PHASE 25: Step completion animation
    val scaleAnim by animateFloatAsState(
        targetValue = if (step.status == StepStatus.SUCCESS) 1f else 0.8f,
        animationSpec = tween(140),
        label = "stepIconScale"
    )

    // PRD PHASE 10: Step timer
    var nowMs by remember(step.startedAt, step.status, step.completedAt) {
        mutableLongStateOf(System.currentTimeMillis())
    }
    LaunchedEffect(step.startedAt, step.status, step.completedAt) {
        if (step.status == StepStatus.RUNNING) {
            while (true) {
                nowMs = System.currentTimeMillis()
                kotlinx.coroutines.delay(1000)
            }
        }
    }
    val stepDuration = if (step.completedAt > 0 && step.startedAt > 0) {
        ((step.completedAt - step.startedAt) / 1000).toInt()
    } else if (step.startedAt > 0 && step.status == StepStatus.RUNNING) {
        ((nowMs - step.startedAt) / 1000).toInt()
    } else 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status icon (PRD PHASE 8: ✓ ● ○ ✕ ⊘)
        val (icon, tint) = when (step.status) {
            StepStatus.SUCCESS -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.primary
            StepStatus.RUNNING -> Icons.Filled.RadioButtonChecked to MaterialTheme.colorScheme.primary
            StepStatus.FAILED -> Icons.Filled.Cancel to MaterialTheme.colorScheme.error
            StepStatus.CANCELLED -> Icons.Filled.DoNotDisturb to MaterialTheme.colorScheme.onSurfaceVariant
            StepStatus.PENDING -> Icons.Filled.RadioButtonUnchecked to MaterialTheme.colorScheme.outline
        }

        Icon(
            icon,
            contentDescription = step.status.name,
            tint = tint,
            modifier = Modifier
                .size(20.dp)
                .scale(if (step.status == StepStatus.SUCCESS) scaleAnim else 1f)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = step.displayName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (step.status == StepStatus.RUNNING) FontWeight.SemiBold else FontWeight.Normal,
            color = when (step.status) {
                StepStatus.FAILED -> MaterialTheme.colorScheme.error
                StepStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f)
        )

        // Timer (PRD PHASE 10)
        Text(
            text = if (step.status == StepStatus.PENDING) "—" else "${stepDuration}s",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ===========================================================================
// PRD PHASE 16: Success state
// ===========================================================================

@Composable
private fun SuccessContent(
    state: UploadProgressUiState,
    onComplete: (String, String) -> Unit
) {
    val totalDuration = if (state.operationCompletedAt > 0 && state.operationStartedAt > 0) {
        ((state.operationCompletedAt - state.operationStartedAt) / 1000).toInt()
    } else 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Payment-like success confirmation.
        GITOFYStaggeredVisibility(index = 0) {
            PremiumSuccessCheck(
                size = 88.dp,
                contentDescription = "Repository uploaded successfully"
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        GITOFYStaggeredVisibility(index = 1) {
            Text(
                "Repository Updated",
            style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        GITOFYStaggeredVisibility(index = 2) {
            Text("100%", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Details card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (state.totalFiles > 0) {
                    InfoRowCompact("Files uploaded", "${state.totalFiles}")
                }
                if (state.commitSha.isNotBlank()) {
                    InfoRowCompact("Commit", state.commitSha)
                }
                if (state.owner.isNotBlank() && state.repo.isNotBlank()) {
                    InfoRowCompact("Repository", "${state.owner}/${state.repo}")
                }
                if (totalDuration > 0) {
                    InfoRowCompact("Completed in", "${totalDuration}s")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Buttons (PRD PHASE 16)
        GITOFYButton(
            "View Repository",
            onClick = {
                if (state.owner.isNotBlank() && state.repo.isNotBlank()) {
                    onComplete(state.owner, state.repo)
                }
            },
            enabled = state.owner.isNotBlank() && state.repo.isNotBlank(),
            fullWidth = true,
            icon = Icons.Default.OpenInNew
        )

        Spacer(modifier = Modifier.height(12.dp))

        GITOFYButton(
            "Done",
            onClick = { onComplete(state.owner, state.repo) },
            type = GITOFYButtonType.Outlined,
            fullWidth = true
        )
    }
}

// ===========================================================================
// PRD PHASE 17: Failure state
// ===========================================================================

@Composable
private fun FailureContent(
    state: UploadProgressUiState,
    onEdit: () -> Unit
) {
    var showDetails by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Error header
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = "Upload failed",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Update Failed",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Step timeline with failed step
        if (state.steps.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    state.steps.forEach { step ->
                        StepRow(step)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Error message
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Upload failed",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    state.error ?: "Unable to update repository.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // PRD PHASE 18: Failed step detail
                if (showDetails) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Details",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        state.error ?: "No additional details available.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Buttons (PRD PHASE 17)
        GITOFYButton(
            "Retry",
            onClick = onEdit,
            type = GITOFYButtonType.Primary,
            fullWidth = true,
            icon = Icons.Default.Refresh
        )

        Spacer(modifier = Modifier.height(12.dp))

        GITOFYButton(
            "View Details",
            onClick = { showDetails = !showDetails },
            type = GITOFYButtonType.Outlined,
            fullWidth = true
        )
    }
}

// ===========================================================================
// PRD PHASE 20: Cancelled state
// ===========================================================================

@Composable
private fun CancelledContent(
    state: UploadProgressUiState,
    onEdit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.DoNotDisturb,
            contentDescription = "Upload cancelled",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Upload Cancelled",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "The repository update was cancelled.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        GITOFYButton(
            "Start Over",
            onClick = onEdit,
            type = GITOFYButtonType.Primary,
            fullWidth = true
        )
    }
}

// ===========================================================================
// Helper functions
// ===========================================================================

@Composable
private fun InfoRowCompact(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val mb = bytes / (1024.0 * 1024.0)
    val kb = bytes / 1024.0
    return when {
        mb >= 1 -> String.format("%.1f MB", mb)
        kb >= 1 -> String.format("%.1f KB", kb)
        else -> "$bytes B"
    }
}

private fun formatSpeed(bytesPerSec: Double): String {
    val mbps = bytesPerSec / (1024.0 * 1024.0)
    val kbps = bytesPerSec / 1024.0
    return when {
        mbps >= 1 -> String.format("%.1f MB/s", mbps)
        kbps >= 1 -> String.format("%.1f KB/s", kbps)
        else -> "${bytesPerSec.toInt()} B/s"
    }
}

private fun formatTime(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return if (mins > 0) String.format("%d:%02d", mins, secs) else "${secs}s"
}
