package com.gitofy.feature.issues

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
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
import com.gitofy.domain.model.IssueSummary

/**
 * Issues — PRD Phase 4 §2.
 *
 * Material 3 filter chips with an animated selected state and a
 * 150–200ms filter transition, plus a clear issue-detail-ready hierarchy
 * for each row (number, title, state, labels, author, timestamp, assignee).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueListScreen(
    owner: String,
    repo: String,
    onBack: () -> Unit,
    onIssueClick: (Int) -> Unit,
    onCreateIssue: () -> Unit,
    viewModel: IssueListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(owner, repo) { viewModel.load(owner, repo) }

    Scaffold(
        topBar = { GITOFYTopAppBar(title = "Issues", onBack = onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateIssue) {
                Icon(Icons.Default.Add, contentDescription = "Create Issue")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg, vertical = LocalSpacing.current.sm),
                horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)
            ) {
                items(IssueFilter.entries) { filter ->
                    val selected = uiState.filter == filter
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.load(owner, repo, filter) },
                        label = { Text(filter.displayName) },
                        leadingIcon = if (selected) {
                            { Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(16.dp)) }
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
                    uiState.isLoading && uiState.issues.isEmpty() -> {
                        LazyColumn(contentPadding = PaddingValues(LocalSpacing.current.lg)) {
                            items(6) { SkeletonListItem() }
                        }
                    }
                    uiState.error != null -> DeveloperErrorState(
                        message = uiState.error!!,
                        onRetry = { viewModel.load(owner, repo) },
                        modifier = Modifier.fillMaxSize()
                    )
                    uiState.issues.isEmpty() -> DeveloperEmptyState(
                        icon = Icons.Default.BugReport,
                        title = "No issues found",
                        subtitle = "Nothing matches this filter right now.",
                        modifier = Modifier.fillMaxSize()
                    )
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(LocalSpacing.current.lg),
                            verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)
                        ) {
                            items(uiState.issues, key = { it.number }) { issue ->
                                IssueCard(issue = issue, onClick = { onIssueClick(issue.number) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IssueCard(issue: IssueSummary, onClick: () -> Unit) {
    val visual = issueStatusVisual(issue.state)
    DeveloperCard(onClick = onClick) {
        Column(modifier = Modifier.padding(LocalSpacing.current.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.BugReport,
                    contentDescription = null,
                    tint = if (issue.state.equals("open", true)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(LocalSpacing.current.md))
                Text(
                    issue.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(LocalSpacing.current.sm))
                IconStatusBadge(visual)
            }

            Spacer(modifier = Modifier.height(LocalSpacing.current.xs))
            MetadataRow(
                text = "#${issue.number} · ${issue.authorLogin}" +
                    (issue.assignees.firstOrNull()?.let { " · assigned to $it" } ?: "") +
                    (if (issue.commentCount > 0) " · ${issue.commentCount} comments" else "")
            )

            if (issue.labels.isNotEmpty()) {
                Spacer(modifier = Modifier.height(LocalSpacing.current.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.xs)) {
                    issue.labels.take(3).forEach { label ->
                        StatusBadge(label, StatusType.Neutral)
                    }
                    if (issue.labels.size > 3) {
                        StatusBadge("+${issue.labels.size - 3}", StatusType.Neutral)
                    }
                }
            }
        }
    }
}
