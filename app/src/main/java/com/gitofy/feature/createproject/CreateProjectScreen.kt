package com.gitofy.feature.createproject

import com.gitofy.core.designsystem.motion.gitofySlideFadeEnter
import com.gitofy.core.designsystem.motion.gitofySlideFadeExit

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gitofy.core.designsystem.components.*
import com.gitofy.core.designsystem.theme.LocalSpacing
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProjectScreen(
    onBack: () -> Unit,
    onUploadStarted: (String) -> Unit,
    viewModel: CreateProjectViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        // FIX: contentResolver.query() is provider-controlled — some file
        // managers / cloud providers (Google Drive, Samsung My Files, etc.)
        // throw (IllegalArgumentException, SecurityException, or a raw
        // RuntimeException from a misbehaving DocumentsProvider) instead of
        // returning a cursor. That used to crash the app the instant a ZIP
        // was picked. Treat any failure here as "name unknown" and keep going.
        val fileName = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    val name = (if (nameIndex >= 0) cursor.getString(nameIndex) else null) ?: "project.zip"
                    val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null
                    name to size
                } else "project.zip" to null
            } ?: ("project.zip" to null)
        }.getOrElse { "project.zip" to null }

        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Some providers do not expose persistable permissions. The
            // immediate flow still works with the granted URI permission.
        }
        viewModel.onZipSelected(uri, fileName.first, fileName.second)
    }

    LaunchedEffect(uiState.operationId) {
        uiState.operationId?.let(onUploadStarted)
    }

    Scaffold(
        topBar = {
            GITOFYTopAppBar(
                title = "Create project",
                onBack = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.md)
        ) {
            CreateProjectStepper(
                currentStep = uiState.step,
                onStepClick = viewModel::onStepRequested
            )

            AnimatedContent(
                targetState = uiState.step,
                transitionSpec = {
                    gitofySlideFadeEnter.togetherWith(gitofySlideFadeExit)
                },
                label = "create-project-step"
            ) { step ->
                when (step) {
                    CreateProjectStep.Project -> ProjectSelectionStep(
                        state = uiState,
                        onSelect = { zipPickerLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed")) },
                        onReplace = { zipPickerLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed")) },
                        onContinue = { viewModel.onStepRequested(CreateProjectStep.Repository) }
                    )

                    CreateProjectStep.Repository -> RepositoryConfigurationStep(
                        state = uiState,
                        onNameChange = viewModel::onRepoNameChange,
                        onDescriptionChange = viewModel::onDescriptionChange,
                        onPrivateChange = viewModel::onVisibilityChange,
                        onCommitChange = viewModel::onCommitMessageChange,
                        onBack = { viewModel.onStepRequested(CreateProjectStep.Project) },
                        onContinue = { viewModel.onStepRequested(CreateProjectStep.Upload) }
                    )

                    CreateProjectStep.Upload -> UploadReviewStep(
                        state = uiState,
                        onBack = { viewModel.onStepRequested(CreateProjectStep.Repository) },
                        onUpload = viewModel::startUpload
                    )

                    CreateProjectStep.Complete -> Unit
                }
            }

            uiState.error?.let {
                ErrorBanner(message = it)
            }
        }
    }
}

@Composable
private fun CreateProjectStepper(
    currentStep: CreateProjectStep,
    onStepClick: (CreateProjectStep) -> Unit
) {
    val steps = listOf(
        CreateProjectStep.Project to "Project",
        CreateProjectStep.Repository to "Repository",
        CreateProjectStep.Upload to "Upload"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, (step, label) ->
            val selected = currentStep == step
            val completed = currentStep.ordinal > step.ordinal
            if (index > 0) HorizontalDivider(modifier = Modifier.width(12.dp))
            AssistChip(
                onClick = { onStepClick(step) },
                enabled = completed || selected,
                modifier = Modifier.semantics {
                    contentDescription = "$label step"
                    stateDescription = when {
                        selected -> "Current step"
                        completed -> "Completed"
                        else -> "Not available yet"
                    }
                },
                label = { Text(if (completed) "✓ $label" else label) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (selected)
                        MaterialTheme.colorScheme.primary
                    else if (completed)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerLow,
                    labelColor = if (selected)
                        MaterialTheme.colorScheme.onPrimary
                    else if (completed)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = AssistChipDefaults.assistChipBorder(
                    enabled = true,
                    borderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
        }
    }
}

@Composable
private fun ProjectSelectionStep(
    state: CreateProjectUiState,
    onSelect: () -> Unit,
    onReplace: () -> Unit,
    onContinue: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("1. Choose your project", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Select a ZIP project. GITOFY validates the archive before you configure the repository.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        GITOFYCard(
            onClick = if (state.zipUri == null) onSelect else onReplace,
            variant = if (state.zipUri != null) CardVariant.Selectable else CardVariant.Interactive,
            selected = state.zipUri != null
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (state.zipUri == null) Icons.Default.FolderZip else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (state.zipUri == null)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (state.zipUri == null) "Select ZIP project" else state.zipFileName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (state.zipUri != null) {
                        Text(
                            buildString {
                                append(
                                    state.zipSizeBytes?.let(::formatBytes) ?: "Size unavailable"
                                )
                                state.zipValidation?.let {
                                    if (it.isValid) append(" • ${it.fileCount} files validated")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "ZIP only • secure archive validation",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (state.isValidatingZip) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("Validating archive…", style = MaterialTheme.typography.bodySmall)
        }

        state.zipValidation?.takeIf { !it.isValid }?.let {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.large
            ) {
                Text(
                    it.error ?: "The selected ZIP could not be validated.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        GITOFYButton(
            text = "Continue",
            onClick = onContinue,
            enabled = state.canContinueToRepository,
            fullWidth = true,
            icon = Icons.Default.Description
        )
    }
}

@Composable
private fun RepositoryConfigurationStep(
    state: CreateProjectUiState,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onPrivateChange: (Boolean) -> Unit,
    onCommitChange: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val status = state.repositoryNameStatus
    val supporting = when (status) {
        RepositoryNameStatus.Invalid -> "Use letters, numbers, dots, hyphens, or underscores."
        RepositoryNameStatus.Validating -> "Checking GitHub…"
        RepositoryNameStatus.Valid -> "Repository name is available."
        RepositoryNameStatus.Duplicate -> "A repository with this name already exists."
        RepositoryNameStatus.Unknown -> "Availability could not be verified. Check your connection."
        RepositoryNameStatus.Idle -> "This name will be used for the GitHub repository."
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("2. Configure repository", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = state.repoName,
            onValueChange = onNameChange,
            label = { Text("Repository name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.isCheckingRepoName,
            isError = status == RepositoryNameStatus.Invalid || status == RepositoryNameStatus.Duplicate,
            supportingText = { Text(supporting) },
            trailingIcon = {
                when (status) {
                    RepositoryNameStatus.Valid -> Icon(Icons.Default.CheckCircle, "Available", tint = MaterialTheme.colorScheme.primary)
                    RepositoryNameStatus.Validating -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else -> Unit
                }
            }
        )

        OutlinedTextField(
            value = state.repoDescription,
            onValueChange = onDescriptionChange,
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
            supportingText = { Text("Optional. Keep it concise and developer-friendly.") }
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .heightIn(min = 64.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Lock, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Private repository", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (state.isPrivate) "Only you and collaborators can access it."
                        else "The repository will be publicly visible.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.isPrivate,
                    onCheckedChange = onPrivateChange
                )
            }
        }

        OutlinedTextField(
            value = state.commitMessage,
            onValueChange = onCommitChange,
            label = { Text("Initial commit message") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = state.commitMessage.isBlank(),
            supportingText = { Text("Used for the first commit created from this project.") }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GITOFYButton("Back", onBack, Modifier.weight(1f), GITOFYButtonType.Outlined)
            GITOFYButton(
                "Review upload",
                onContinue,
                Modifier.weight(1f),
                enabled = state.canContinueToUpload,
                icon = Icons.Default.CloudUpload
            )
        }
    }
}

@Composable
private fun UploadReviewStep(
    state: CreateProjectUiState,
    onBack: () -> Unit,
    onUpload: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("3. Upload", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Review the destination, then start the real upload. Progress is reported from the background operation—never simulated.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        GITOFYCard(variant = CardVariant.Outlined) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoRow("Project", state.zipFileName, icon = Icons.Default.FolderZip)
                InfoRow("Repository", state.repoName, icon = Icons.Default.Description)
                InfoRow("Visibility", if (state.isPrivate) "Private" else "Public", icon = Icons.Default.Lock)
                InfoRow("Commit", state.commitMessage)
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                "The upload continues through WorkManager and can survive screen recreation.",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GITOFYButton("Back", onBack, Modifier.weight(1f), GITOFYButtonType.Outlined)
            GITOFYButton(
                "Create repository",
                onUpload,
                Modifier.weight(1f),
                icon = Icons.Default.CloudUpload
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unit])
}
