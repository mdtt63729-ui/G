package com.gitofy.feature.repositories.update

import com.gitofy.core.designsystem.motion.gitofySlideFadeEnter
import com.gitofy.core.designsystem.motion.gitofySlideFadeExit

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gitofy.core.designsystem.components.GITOFYButton
import com.gitofy.core.designsystem.components.PremiumSuccessCheck
import com.gitofy.core.designsystem.components.GITOFYButtonType
import com.gitofy.data.repository.RepositorySyncEngine

/**
 * PRD §7-9: Update Repository screen.
 *
 * Flow: Android File Picker → Select ZIP → Show ZIP info →
 *       Update Repository button → Real sync operation →
 *       Success/Failure/NoChanges UI.
 *
 * PRD §55: Uses ACTION_OPEN_DOCUMENT with MIME wildcard to allow
 * arbitrary files, with specific support for application/zip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateRepositoryScreen(
    owner: String,
    repo: String,
    onBack: () -> Unit,
    onComplete: () -> Unit,
    viewModel: UpdateRepositoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // PRD §55: Android File Picker using ACTION_OPEN_DOCUMENT
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            // PRD §56: Get actual file size from ContentResolver
            val size = getFileSize(context, uri)
            val fileName = getFileName(context, uri) ?: "selected.zip"
            viewModel.onZipSelected(uri, fileName, size)
        }
    }

    // PRD §48: Back navigation safety during operation
    var showBackDialog by remember { mutableStateOf(false) }

    LaunchedEffect(owner, repo) {
        viewModel.init(owner, repo)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Update Repository", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.isSyncing) {
                            showBackDialog = true
                        } else {
                            onBack()
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
                    uiState.isNoChanges -> "no_changes"
                    uiState.isSyncing -> "syncing"
                    else -> "select"
                },
                transitionSpec = {
                    gitofySlideFadeEnter.togetherWith(gitofySlideFadeExit)
                },
                label = "update-state"
            ) { state ->
                when (state) {
                    "complete" -> UpdateSuccessContent(uiState, onComplete)
                    "failed" -> UpdateFailureContent(uiState, onRetry = { viewModel.retry() }, onBack = onBack)
                    "no_changes" -> NoChangesContent(uiState, onBack)
                    "syncing" -> SyncingContent(uiState, onCancel = { viewModel.cancel() })
                    else -> SelectZipContent(
                        uiState = uiState,
                        onPickZip = {
                            // PRD §55: Open file picker with */* MIME type
                            filePickerLauncher.launch(arrayOf("*/*"))
                        },
                        onRemoveZip = { viewModel.removeZip() },
                        onStartUpdate = { viewModel.startUpdate() }
                    )
                }
            }
        }
    }

    if (showBackDialog) {
        AlertDialog(
            onDismissRequest = { showBackDialog = false },
            title = { Text("Update in progress") },
            text = { Text("Are you sure you want to cancel? The repository may be left in an inconsistent state.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.cancel()
                    showBackDialog = false
                    onBack()
                }) { Text("Cancel update", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showBackDialog = false }) { Text("Continue update") }
            }
        )
    }
}

@Composable
private fun SelectZipContent(
    uiState: UpdateRepositoryUiState,
    onPickZip: () -> Unit,
    onRemoveZip: () -> Unit,
    onStartUpdate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Repository info header
        Text(
            text = uiState.repoName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "${uiState.ownerLogin}/${uiState.repoName}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.zipUri == null) {
            // No ZIP selected — show picker prompt
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Select a ZIP file to sync with this repository",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    GITOFYButton(
                        text = "Select ZIP",
                        onClick = onPickZip,
                        icon = Icons.Default.FolderOpen
                    )
                }
            }
        } else {
            // PRD §8: ZIP Selection UI — shows selected ZIP with actual byte size
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Archive,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            uiState.zipFileName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        // PRD §56: Show actual byte size
                        Text(
                            text = uiState.zipSizeBytes?.let { formatFileSize(it) } ?: "Unknown size",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "ZIP Archive",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    TextButton(onClick = onRemoveZip) {
                        Text("Remove")
                    }
                }
            }

            // Commit message field
            OutlinedTextField(
                value = uiState.commitMessage,
                onValueChange = { },
                label = { Text("Commit message") },
                modifier = Modifier.fillMaxWidth(),
                enabled = false,
                supportingText = {
                    Text("Update repository from ${uiState.zipFileName}")
                }
            )

            Spacer(modifier = Modifier.weight(1f))

            // PRD §9: Update button
            GITOFYButton(
                text = "Update Repository",
                onClick = onStartUpdate,
                icon = Icons.Default.Sync,
                fullWidth = true,
                type = GITOFYButtonType.Primary
            )
        }

        // Show validation error if any
        uiState.error?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun SyncingContent(uiState: UpdateRepositoryUiState, onCancel: () -> Unit) {
    // PRD FIX: uiState.progress was fed straight into LinearProgressIndicator
    // and the "%" text with no animationSpec at all. The sync engine emits
    // progress in coarse, uneven steps (per-file, per-stage), so the bar and
    // the number used to visually SNAP between values instead of gliding —
    // reported as "the percentage and progress line work with a lot of lag."
    // animateFloatAsState smooths every jump into a short glide so updates
    // read as continuous motion instead of stutter.
    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = uiState.progress,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 300,
            easing = androidx.compose.animation.core.LinearOutSlowInEasing
        ),
        label = "updateProgress"
    )
    val progress = animatedProgress
    val percent = (progress * 100).toInt().coerceIn(0, 100)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Repository info
        Text(
            text = uiState.repoName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = uiState.zipFileName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (uiState.zipSizeBytes != null) {
            Text(
                text = formatFileSize(uiState.zipSizeBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // PRD §24: Progress bar — single track, smooth interpolation, no jumps
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        Text(
            text = "$percent%",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        if (uiState.totalBytes > 0) {
            Text(
                text = "${formatFileSize(uiState.bytesUploaded)} / ${formatFileSize(uiState.totalBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        // PRD §27: Step timeline with state icons
        Text(
            text = uiState.currentStageDisplayName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // File progress (PRD §23)
        if (uiState.totalItems > 0) {
            Text(
                text = "${uiState.completedItems} / ${uiState.totalItems}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (uiState.currentItem.isNotBlank()) {
                AnimatedContent(
                    targetState = uiState.currentItem,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                    label = "update-current-file"
                ) { item ->
                    Text(
                        text = item,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // PRD §49: Cancel operation
        TextButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel update", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun UpdateSuccessContent(
    uiState: UpdateRepositoryUiState,
    onComplete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // Payment-like success confirmation.
        PremiumSuccessCheck(size = 80.dp, contentDescription = "Repository updated successfully")

        Text(
            "Successfully Updated",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Text(
            uiState.repoName,
            style = MaterialTheme.typography.titleMedium
        )

        // PRD §30: Change summary
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChangeSummaryRow("Added", uiState.addedCount)
                ChangeSummaryRow("Modified", uiState.modifiedCount)
                ChangeSummaryRow("Deleted", uiState.deletedCount)
                ChangeSummaryRow("Unchanged", uiState.unchangedCount)
            }
        }

        // Commit info
        if (uiState.commitSha.isNotBlank()) {
            Text(
                text = "Commit: ${uiState.commitSha.take(7)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        GITOFYButton(
            text = "Done",
            onClick = onComplete,
            fullWidth = true
        )
    }
}

@Composable
private fun NoChangesContent(
    uiState: UpdateRepositoryUiState,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // PRD §19: No changes — this is NOT a failure. The sync engine
        // already auto-detects this by comparing the SHA of every local file
        // against the remote SHA (see RepositorySyncEngine's diff step) — if
        // the uploaded ZIP is byte-identical to what's already on GitHub,
        // this state fires instead of running a no-op update.
        androidx.compose.animation.AnimatedVisibility(
            visible = true,
            enter = gitofySlideFadeEnter,
            exit = gitofySlideFadeExit
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Text(
            "Already Updated",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Your selected project is already up to date with this repository.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.weight(1f))

        GITOFYButton(
            text = "Done",
            onClick = onBack,
            fullWidth = true
        )
    }
}

@Composable
private fun UpdateFailureContent(
    uiState: UpdateRepositoryUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // PRD §28: Failure state with actual error — cross icon with a
        // smooth scale-in to match the success/no-changes states.
        androidx.compose.animation.AnimatedVisibility(
            visible = true,
            enter = gitofySlideFadeEnter,
            exit = gitofySlideFadeExit
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        Text(
            "Update Failed",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        // PRD §28: Show actual error reason, not just "Upload failed"
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Could not complete the update.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = uiState.error ?: "Unknown error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // PRD §29: Retry button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GITOFYButton(
                text = "Back",
                onClick = onBack,
                modifier = Modifier.weight(1f)
            )
            GITOFYButton(
                text = "Retry",
                onClick = onRetry,
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Refresh
            )
        }
    }
}

@Composable
private fun ChangeSummaryRow(label: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

// PRD §56: Get actual file size from ContentResolver
private fun getFileSize(context: Context, uri: Uri): Long? {
    return try {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (sizeIndex >= 0 && it.moveToFirst()) {
                val size = it.getLong(sizeIndex)
                if (size > 0) size else null
            } else null
        }
    } catch (e: Exception) {
        null
    }
}

private fun getFileName(context: Context, uri: Uri): String? {
    return try {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && it.moveToFirst()) it.getString(nameIndex) else null
        }
    } catch (e: Exception) {
        null
    }
}

private fun formatFileSize(bytes: Long): String {
    return Formatter.formatFileSize(null, bytes)
}
