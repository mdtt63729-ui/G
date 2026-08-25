package com.gitofy.feature.issues

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg),
                horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)
            ) {
                IssueFilter.entries.forEach { filter ->
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
                    items(uiState.issues, key = { it.number }) { issue ->
                        IssueCard(issue = issue, onClick = { onIssueClick(issue.number) })
                    }
                }
            }
        }
    }
}

@Composable
private fun IssueCard(issue: IssueSummary, onClick: () -> Unit) {
    GITOFYCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(LocalSpacing.current.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.BugReport,
                contentDescription = null,
                tint = if (issue.state == "open") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(LocalSpacing.current.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(issue.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "#${issue.number} by ${issue.authorLogin}${if (issue.commentCount > 0) " · ${issue.commentCount} comments" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusBadge(
                issue.state.replaceFirstChar { it.uppercase() },
                if (issue.state == "open") StatusType.Success else StatusType.Neutral
            )
        }
    }
}
