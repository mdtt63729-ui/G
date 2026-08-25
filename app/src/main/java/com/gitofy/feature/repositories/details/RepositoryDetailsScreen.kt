package com.gitofy.feature.repositories.details

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.gitofy.core.designsystem.components.*
import com.gitofy.core.designsystem.theme.LocalSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepositoryDetailsScreen(
    owner: String,
    repo: String,
    onBack: () -> Unit,
    onWorkflows: () -> Unit,
    viewModel: RepositoryDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(owner, repo) {
        viewModel.load(owner, repo)
    }

    Scaffold(
        topBar = {
            GITOFYTopAppBar(title = repo, onBack = onBack)
        }
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
                // Header card
                item {
                    GITOFYCard {
                        Column(modifier = Modifier.padding(LocalSpacing.current.lg)) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                AsyncImage(
                                    model = details.ownerAvatar,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.width(LocalSpacing.current.md))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(details.fullName, style = MaterialTheme.typography.titleMedium)
                                    details.description?.let {
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(LocalSpacing.current.md))
                            Row(horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.lg)) {
                                InfoChip(Icons.Default.Star, details.stars.toString())
                                InfoChip(Icons.Default.CallSplit, details.forks.toString())
                                InfoChip(
                                    if (details.isPrivate) Icons.Default.Lock else Icons.Default.Public,
                                    if (details.isPrivate) "Private" else "Public"
                                )
                            }
                        }
                    }
                }

                // Workflows button
                item {
                    GITOFYButton(
                        text = "View Workflows",
                        onClick = onWorkflows,
                        icon = Icons.Default.PlayCircle,
                        fullWidth = true
                    )
                }

                // Branches
                item {
                    Text("Branches", style = MaterialTheme.typography.titleMedium)
                }
                items(uiState.branches.take(10), key = { it.name }) { branch ->
                    GITOFYCard {
                        Row(
                            modifier = Modifier.padding(LocalSpacing.current.lg),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
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

                // Recent commits
                item {
                    Spacer(modifier = Modifier.height(LocalSpacing.current.sm))
                    Text("Recent Commits", style = MaterialTheme.typography.titleMedium)
                }
                items(uiState.commits.take(10), key = { it.sha }) { commit ->
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
            }
        }
    }
}

@Composable
private fun InfoChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
