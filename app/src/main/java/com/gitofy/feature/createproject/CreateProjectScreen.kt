package com.gitofy.feature.createproject

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gitofy.core.designsystem.components.*
import com.gitofy.core.designsystem.theme.LocalSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProjectScreen(
    onBack: () -> Unit,
    onUploadStarted: (String) -> Unit,
    viewModel: CreateProjectViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = it.lastPathSegment?.substringAfterLast("/") ?: "project.zip"
            viewModel.onZipSelected(it, fileName)
        }
    }

    Scaffold(
        topBar = {
            GITOFYTopAppBar(title = "Create New Project", onBack = onBack)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(LocalSpacing.current.lg),
            verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.md)
        ) {
            // ZIP selection
            Text("Select ZIP File", style = MaterialTheme.typography.titleMedium)

            GITOFYCard(
                onClick = { zipPickerLauncher.launch("application/zip") }
            ) {
                Row(
                    modifier = Modifier.padding(LocalSpacing.current.lg),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Icon(
                        if (uiState.zipUri == null) Icons.Default.CloudUpload else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (uiState.zipUri == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.width(LocalSpacing.current.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (uiState.zipUri == null) "Tap to select ZIP file" else uiState.zipFileName,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (uiState.zipUri != null) {
                            Text("ZIP selected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            HorizontalDivider()

            // Repository configuration
            Text("Configure Repository", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = uiState.repoName,
                onValueChange = viewModel::onRepoNameChange,
                label = { Text("Repository Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = !viewModel.validateRepoName() && uiState.repoName.isNotEmpty(),
                supportingText = if (!viewModel.validateRepoName() && uiState.repoName.isNotEmpty()) {
                    { Text("Invalid repository name") }
                } else null
            )

            OutlinedTextField(
                value = uiState.repoDescription,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

            // Visibility toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text("Private repository", modifier = Modifier.weight(1f))
                Switch(
                    checked = uiState.isPrivate,
                    onCheckedChange = viewModel::onVisibilityChange
                )
            }

            OutlinedTextField(
                value = uiState.commitMessage,
                onValueChange = viewModel::onCommitMessageChange,
                label = { Text("Commit Message") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Error
            uiState.error?.let { error ->
                ErrorBanner(message = error)
            }

            // Upload button
            GITOFYButton(
                text = "Upload",
                onClick = {
                    viewModel.startUpload(androidx.core.content.FileProvider.getCacheDir())
                    viewModel.uiState.value.operationId?.let { onUploadStarted(it) }
                },
                loading = uiState.isProcessing,
                fullWidth = true,
                enabled = uiState.zipUri != null && viewModel.validateRepoName()
            )
        }
    }
}
