package com.gitofy.feature.home

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
import coil.compose.AsyncImage
import com.gitofy.core.designsystem.components.*
import com.gitofy.core.designsystem.theme.LocalSpacing
import com.gitofy.domain.model.RepoSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToRepos: () -> Unit,
    onNavigateToCreate: () -> Unit,
    onNavigateToRepoDetails: (String, String) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            GITOFYTopAppBar(
                title = "GITOFY",
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            GITOFYFloatingActionButton(onClick = onNavigateToCreate)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Offline indicator
            if (uiState.isOffline) {
                OfflineBanner()
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(LocalSpacing.current.lg),
                verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.md)
            ) {
                // Quick actions
                item {
                    QuickActionsRow(
                        onCreate = onNavigateToCreate,
                        onRepos = onNavigateToRepos
                    )
                }

                // Recent repositories
                item {
                    Text(
                        text = "Recent Repositories",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = LocalSpacing.current.sm)
                    )
                }

                if (uiState.isLoading && uiState.recentRepos.isEmpty()) {
                    items(3) { SkeletonListItem() }
                } else if (uiState.recentRepos.isEmpty()) {
                    item {
                        EmptyStateView(
                            icon = Icons.Default.Cloud,
                            title = "No repositories yet",
                            subtitle = "Create your first repository from a ZIP project.",
                            actionText = "Create Project",
                            onAction = onNavigateToCreate
                        )
                    }
                } else {
                    items(uiState.recentRepos) { repo ->
                        RepositoryCard(repo = repo, onClick = {
                            onNavigateToRepoDetails(repo.ownerLogin, repo.name)
                        })
                    }
                }

                // Error
                if (uiState.error != null && uiState.recentRepos.isEmpty()) {
                    item {
                        ErrorBanner(
                            message = uiState.error!!,
                            onRetry = { viewModel.refresh() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = "You're offline. Cached information is available.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(LocalSpacing.current.md)
        )
    }
}

@Composable
private fun QuickActionsRow(
    onCreate: () -> Unit,
    onRepos: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.md)
    ) {
        GITOFYCard(
            modifier = Modifier.weight(1f),
            onClick = onCreate
        ) {
            Row(
                modifier = Modifier.padding(LocalSpacing.current.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(LocalSpacing.current.md))
                Text("Create", style = MaterialTheme.typography.titleSmall)
            }
        }

        GITOFYCard(
            modifier = Modifier.weight(1f),
            onClick = onRepos
        ) {
            Row(
                modifier = Modifier.padding(LocalSpacing.current.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Cloud,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(LocalSpacing.current.md))
                Text("Repos", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
fun RepositoryCard(
    repo: RepoSummary,
    onClick: () -> Unit
) {
    GITOFYCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(LocalSpacing.current.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = repo.ownerAvatar,
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(LocalSpacing.current.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = repo.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = repo.fullName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (repo.isPrivate) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "Private",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
