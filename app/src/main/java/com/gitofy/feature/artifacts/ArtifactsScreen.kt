package com.gitofy.feature.artifacts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gitofy.core.designsystem.components.*
import com.gitofy.core.designsystem.theme.LocalSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtifactsScreen(
    owner: String,
    repo: String,
    runId: Long,
    onBack: () -> Unit,
    viewModel: ArtifactsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(owner, repo, runId) {
        viewModel.load(owner, repo, runId)
    }

    Scaffold(
        topBar = {
            GITOFYTopAppBar(title = "Artifacts", onBack = onBack)
        }
    ) { padding ->
        when {
            uiState.isLoading && uiState.artifacts.isEmpty() -> {
                Column(modifier = Modifier.padding(padding)) {
                    LazyColumn { items(5) { SkeletonListItem() } }
                }
            }
            uiState.artifacts.isEmpty() && uiState.error != null -> {
                ErrorBanner(
                    message = uiState.error!!,
                    onRetry = { viewModel.load(owner, repo, runId) },
                    modifier = Modifier.padding(padding)
                )
            }
            uiState.artifacts.isEmpty() -> {
                EmptyStateView(
                    icon = Icons.Default.Download,
                    title = "No artifacts",
                    subtitle = "This workflow has no downloadable artifacts.",
                    modifier = Modifier.padding(padding)
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(LocalSpacing.current.lg),
                    verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)
                ) {
                    uiState.downloadMessage?.let { msg ->
                        item {
                            Surface(
                                color = if (msg.startsWith("Downloaded")) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    msg,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(LocalSpacing.current.md)
                                )
                            }
                        }
                    }

                    items(uiState.artifacts, key = { it.id }) { artifact ->
                        ArtifactCard(
                            artifact = artifact,
                            isDownloading = uiState.downloadingId == artifact.id,
                            onDownload = { viewModel.download(owner, repo, artifact) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtifactCard(
    artifact: com.gitofy.domain.model.ArtifactSummary,
    isDownloading: Boolean,
    onDownload: () -> Unit
) {
    GITOFYCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(LocalSpacing.current.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Archive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(LocalSpacing.current.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    artifact.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${formatSize(artifact.sizeInBytes)} · ${artifact.createdAt}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (artifact.expired) {
                StatusBadge("Expired", StatusType.Neutral)
            } else {
                GITOFYButton(
                    text = "Download",
                    onClick = onDownload,
                    loading = isDownloading,
                    type = GITOFYButtonType.Outlined
                )
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }
}
