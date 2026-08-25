package com.gitofy.feature.pulls

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Merge
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
import com.gitofy.domain.model.PullRequestSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullRequestListScreen(
    owner: String,
    repo: String,
    onBack: () -> Unit,
    onPRClick: (Int) -> Unit,
    onCreatePR: () -> Unit,
    viewModel: PullRequestListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(owner, repo) {
        viewModel.load(owner, repo)
    }

    Scaffold(
        topBar = { GITOFYTopAppBar(title = "Pull Requests", onBack = onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreatePR) {
                Icon(Icons.Default.Add, contentDescription = "Create PR")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Filter chips
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg),
                horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)
            ) {
                PRFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = uiState.filter == filter,
                        onClick = { viewModel.load(owner, repo, filter) },
                        label = { Text(filter.displayName) }
                    )
                }
            }

            if (uiState.isLoading) {
                LoadingView(modifier = Modifier.fillMaxSize())
            } else if (uiState.error != null) {
                ErrorView(message = uiState.error!!, onRetry = { viewModel.load(owner, repo) })
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(LocalSpacing.current.lg),
                    verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)
                ) {
                    items(uiState.prs, key = { it.number }) { pr ->
                        PRCard(pr = pr, onClick = { onPRClick(pr.number) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PRCard(pr: PullRequestSummary, onClick: () -> Unit) {
    GITOFYCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(LocalSpacing.current.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (pr.isMerged) Icons.Default.Merge else Icons.Default.Add,
                contentDescription = null,
                tint = when {
                    pr.isMerged -> MaterialTheme.colorScheme.secondary
                    pr.state == "open" -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outline
                },
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(LocalSpacing.current.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    pr.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "#${pr.number} by ${pr.authorLogin} · ${pr.headBranch} → ${pr.baseBranch}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (pr.isDraft) {
                StatusBadge("Draft", StatusType.Neutral)
            } else if (pr.state == "open") {
                StatusBadge("Open", StatusType.Success)
            } else if (pr.isMerged) {
                StatusBadge("Merged", StatusType.Info)
            } else {
                StatusBadge("Closed", StatusType.Neutral)
            }
        }
    }
}
