package com.gitofy.feature.pulls

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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

/**
 * Pull Requests — PRD Phase 4 §3.
 *
 * Standardized status badges, animated M3 filter chips, and clear
 * branch/review metadata for each row.
 */
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
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg, vertical = LocalSpacing.current.sm),
                horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)
            ) {
                items(PRFilter.entries) { filter ->
                    val selected = uiState.filter == filter
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.load(owner, repo, filter) },
                        label = { Text(filter.displayName) },
                        leadingIcon = if (selected) {
                            { Icon(Icons.Default.Merge, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            DeveloperTabContent(targetState = uiState.filter, modifier = Modifier.weight(1f)) {
                when {
                    uiState.isLoading && uiState.prs.isEmpty() -> {
                        LazyColumn(contentPadding = PaddingValues(LocalSpacing.current.lg)) {
                            items(6) { SkeletonListItem() }
                        }
                    }
                    uiState.error != null -> DeveloperErrorState(
                        message = uiState.error!!,
                        onRetry = { viewModel.load(owner, repo) },
                        modifier = Modifier.fillMaxSize()
                    )
                    uiState.prs.isEmpty() -> DeveloperEmptyState(
                        icon = Icons.Default.Merge,
                        title = "No pull requests",
                        subtitle = "Nothing matches this filter right now.",
                        modifier = Modifier.fillMaxSize()
                    )
                    else -> {
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
    }
}

@Composable
private fun PRCard(pr: PullRequestSummary, onClick: () -> Unit) {
    val visual = pullRequestStatusVisual(pr.state, pr.isDraft, pr.isMerged)
    DeveloperCard(onClick = onClick) {
        Column(modifier = Modifier.padding(LocalSpacing.current.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                Text(
                    pr.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(LocalSpacing.current.sm))
                IconStatusBadge(visual)
            }
            Spacer(modifier = Modifier.height(LocalSpacing.current.xs))
            MetadataRow(text = "#${pr.number} · ${pr.authorLogin} · ${pr.headBranch} → ${pr.baseBranch}")
            if (pr.labels.isNotEmpty()) {
                Spacer(modifier = Modifier.height(LocalSpacing.current.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.xs)) {
                    pr.labels.take(3).forEach { label -> StatusBadge(label, StatusType.Neutral) }
                    if (pr.labels.size > 3) StatusBadge("+${pr.labels.size - 3}", StatusType.Neutral)
                }
            }
        }
    }
}
