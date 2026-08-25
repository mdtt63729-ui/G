package com.gitofy.feature.createproject

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gitofy.core.designsystem.components.GITOFYTopAppBar
import com.gitofy.core.designsystem.components.LabeledProgressBar
import com.gitofy.core.designsystem.theme.LocalSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadProgressScreen(
    operationId: String,
    onComplete: (String, String) -> Unit,
    onCancel: () -> Unit,
    viewModel: UploadProgressViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(operationId) {
        viewModel.startMonitoring(operationId)
    }

    LaunchedEffect(uiState.isComplete, uiState.owner, uiState.repo) {
        if (uiState.isComplete && uiState.owner.isNotEmpty() && uiState.repo.isNotEmpty()) {
            onComplete(uiState.owner, uiState.repo)
        }
    }

    Scaffold(
        topBar = {
            GITOFYTopAppBar(title = "Uploading", onBack = onCancel)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(LocalSpacing.current.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.md)
        ) {
            Text(
                text = "Uploading project",
                style = MaterialTheme.typography.titleMedium
            )

            LabeledProgressBar(
                label = uiState.currentStage,
                progress = uiState.progress
            )

            Text(
                text = "This may continue in the background.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (uiState.error != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = uiState.error!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(LocalSpacing.current.md)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (uiState.error != null) {
                GITOFYButton(
                    text = "Cancel",
                    onClick = onCancel,
                    type = com.gitofy.core.designsystem.components.GITOFYButtonType.Outlined,
                    fullWidth = true
                )
            }
        }
    }
}
