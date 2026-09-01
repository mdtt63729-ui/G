package com.gitofy.feature.repositories.details

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.gitofy.core.designsystem.components.*
import com.gitofy.core.designsystem.theme.LocalSpacing
import com.gitofy.domain.model.BranchInfo
import com.gitofy.domain.model.CommitInfo

/**
 * Repository Details.
 *
 * Hierarchy (PRD Phase 3 §3): Header -> Metadata -> Primary Actions ->
 * Branches -> Recent Activity/Commits -> Repository Health.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepositoryDetailsScreen(
    owner: String,
    repo: String,
    onBack: () -> Unit,
    onWorkflows: () -> Unit,
    onHealth: () -> Unit = {},
    onDeleted: () -> Unit = {},
    onUpdateRepository: () -> Unit = {},
    viewModel: RepositoryDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(owner, repo) {
        viewModel.load(owner, repo)
    }

    // Navigate back to the list once the repository has been deleted.
    // PRD §6: Show a success snackbar before navigating back, so the user
    // gets feedback that the delete succeeded.
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) {
            snackbarHostState.showSnackbar("Repository deleted")
            viewModel.consumeDeletedEvent()
            onDeleted()
        }
    }

    // Surface delete failures as a snackbar.
    LaunchedEffect(uiState.deleteError) {
        uiState.deleteError?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeDeleteError()
        }
    }

    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            GITOFYTopAppBar(title = repo, onBack = onBack)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val details = uiState.details
        if (details == null && uiState.isLoading) {
            LoadingIndicator(modifier = Modifier.padding(padding))
        } else if (details == null && uiState.error != null) {
            ErrorBanner(
                message = uiState.error!!,
                onRetry = { viewModel.load(owner, repo) },
                modifier = Modifier.padding(padding)
            )
        } else if (details != null) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(LocalSpacing.current.lg),
                verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.md)
            ) {
                // Header + metadata
                item {
                    RepositoryHeaderCard(
                        ownerAvatar = details.ownerAvatar,
                        fullName = details.fullName,
                        description = details.description,
                        isPrivate = details.isPrivate,
                        stars = details.stars,
                        forks = details.forks,
                        openIssues = details.openIssues,
                        defaultBranch = details.defaultBranch
                    )
                }

                // Primary action — single visually-dominant action per PRD §3
                item {
                    GITOFYButton(
                        text = "View Workflows",
                        onClick = onWorkflows,
                        icon = Icons.Default.PlayCircle,
                        fullWidth = true
                    )
                }

                // PRD §6: Update Repository button — opens file picker to
                // select a ZIP and sync it with this repository.
                item {
                    GITOFYButton(
                        text = "Update Repository",
                        onClick = onUpdateRepository,
                        icon = Icons.Default.Sync,
                        fullWidth = true
                    )
                }

                // Branches
                item {
                    Text("Branches", style = MaterialTheme.typography.titleMedium)
                }
                if (uiState.branches.isEmpty()) {
                    item {
                        InlineEmptyRow(
                            icon = Icons.Default.CallSplit,
                            text = "No branches found."
                        )
                    }
                } else {
                    items(uiState.branches.take(10), key = { it.name }) { branch ->
                        BranchRow(branch)
                    }
                }

                // Recent activity / commits
                item {
                    Spacer(modifier = Modifier.height(LocalSpacing.current.sm))
                    Text("Recent Activity", style = MaterialTheme.typography.titleMedium)
                }
                if (uiState.commits.isEmpty()) {
                    item {
                        InlineEmptyRow(
                            icon = Icons.Default.History,
                            text = "No recent commits found."
                        )
                    }
                } else {
                    items(uiState.commits.take(10), key = { it.sha }) { commit ->
                        CommitRow(commit)
                    }
                }

                // Repository Health — entry point at the bottom of the hierarchy
                item {
                    Spacer(modifier = Modifier.height(LocalSpacing.current.sm))
                    Text("Repository Health", style = MaterialTheme.typography.titleMedium)
                }
                item {
                    GITOFYCard(modifier = Modifier.fillMaxWidth(), onClick = onHealth) {
                        Row(
                            modifier = Modifier.padding(LocalSpacing.current.lg),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.HealthAndSafety,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(LocalSpacing.current.md))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("CI health, coverage & signals", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "View workflow success rate and repository signals",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Danger zone — delete repository
                item {
                    Spacer(modifier = Modifier.height(LocalSpacing.current.sm))
                    Text("Danger Zone", style = MaterialTheme.typography.titleMedium)
                }
                item {
                    GITOFYButton(
                        text = if (uiState.isDeleting) "Deleting…" else "Delete Repository",
                        onClick = { showDeleteDialog = true },
                        icon = Icons.Default.Delete,
                        type = GITOFYButtonType.Destructive,
                        fullWidth = true,
                        enabled = !uiState.isDeleting
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!uiState.isDeleting) showDeleteDialog = false
            },
            icon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Delete repository") },
            text = {
                Text(
                    "This will permanently delete \"$repo\" from GitHub. " +
                        "This action cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteRepository(owner, repo)
                        showDeleteDialog = false
                    },
                    enabled = !uiState.isDeleting
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                    enabled = !uiState.isDeleting
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun RepositoryHeaderCard(
    ownerAvatar: String,
    fullName: String,
    description: String?,
    isPrivate: Boolean,
    stars: Int,
    forks: Int,
    openIssues: Int,
    defaultBranch: String
) {
    GITOFYCard {
        Column(modifier = Modifier.padding(LocalSpacing.current.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = ownerAvatar,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.small)
                )
                Spacer(modifier = Modifier.width(LocalSpacing.current.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(fullName, style = MaterialTheme.typography.titleMedium)
                    description?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                StatusBadge(
                    text = if (isPrivate) "Private" else "Public",
                    statusType = if (isPrivate) StatusType.Neutral else StatusType.Info
                )
            }
            Spacer(modifier = Modifier.height(LocalSpacing.current.md))
            Row(horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.lg)) {
                InfoChip(Icons.Default.Star, stars.toString())
                InfoChip(Icons.Default.CallSplit, forks.toString())
                InfoChip(Icons.Default.BugReport, openIssues.toString())
                InfoChip(Icons.Default.AccountTree, defaultBranch)
            }
        }
    }
}

@Composable
private fun BranchRow(branch: BranchInfo) {
    GITOFYCard {
        Row(
            modifier = Modifier.padding(LocalSpacing.current.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.CallSplit, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(LocalSpacing.current.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(branch.name, style = MaterialTheme.typography.bodyMedium)
                Text(
                    branch.commitSha.take(7),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CommitRow(commit: CommitInfo) {
    GITOFYCard {
        Column(modifier = Modifier.padding(LocalSpacing.current.lg)) {
            Text(
                commit.message.lines().firstOrNull() ?: "",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(LocalSpacing.current.xs))
            Text(
                "${commit.authorName} · ${commit.date}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InlineEmptyRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = LocalSpacing.current.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(LocalSpacing.current.sm))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InfoChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
