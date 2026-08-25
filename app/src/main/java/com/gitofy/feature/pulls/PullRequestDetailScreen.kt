package com.gitofy.feature.pulls

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
import com.gitofy.domain.model.DiffFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullRequestDetailScreen(
    owner: String,
    repo: String,
    prNumber: Int,
    onBack: () -> Unit,
    viewModel: PullRequestDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(owner, repo, prNumber) {
        viewModel.load(owner, repo, prNumber)
    }

    Scaffold(
        topBar = { GITOFYTopAppBar(title = "PR #$prNumber", onBack = onBack) }
    ) { padding ->
        if (uiState.isLoading) {
            LoadingView(modifier = Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }

        val pr = uiState.pr ?: return@Scaffold

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Header
            Column(modifier = Modifier.padding(LocalSpacing.current.lg)) {
                Text(pr.title, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${pr.headBranch} → ${pr.baseBranch} · ${pr.additions}+ ${pr.deletions}-",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(LocalSpacing.current.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.xs)) {
                    if (pr.isDraft) StatusBadge("Draft", StatusType.Neutral)
                    if (pr.isMerged) StatusBadge("Merged", StatusType.Info)
                    if (pr.state == "open" && !pr.isMerged) StatusBadge("Open", StatusType.Success)
                    StatusBadge("${pr.changedFiles} files", StatusType.Info)
                }
            }

            TabRow(selectedTabIndex = uiState.selectedTab.ordinal) {
                PRTab.entries.forEach { tab ->
                    Tab(
                        selected = uiState.selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = { Text(tab.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            when (uiState.selectedTab) {
                PRTab.CONVERSATION -> ConversationTab(uiState)
                PRTab.FILES -> FilesTab(uiState.diffFiles)
                PRTab.COMMITS -> CommitsTab(uiState)
            }
        }
    }

    if (uiState.showMergeConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.hideMergeConfirm() },
            title = { Text("Merge Pull Request") },
            text = { Text("Are you sure you want to merge PR #$prNumber? This action cannot be undone.") },
            confirmButton = { TextButton(onClick = { viewModel.hideMergeConfirm() }) { Text("Merge") } },
            dismissButton = { TextButton(onClick = { viewModel.hideMergeConfirm() }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ConversationTab(state: PullRequestDetailUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(LocalSpacing.current.lg),
        verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)
    ) {
        // Reviews
        if (state.reviews.isNotEmpty()) {
            item { Text("Reviews", style = MaterialTheme.typography.titleSmall) }
            items(state.reviews, key = { it.id }) { review ->
                GITOFYCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(LocalSpacing.current.lg)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(review.user, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            StatusBadge(review.state, when (review.state) {
                                "APPROVED" -> StatusType.Success
                                "CHANGES_REQUESTED" -> StatusType.Error
                                else -> StatusType.Neutral
                            })
                        }
                        review.body?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }

        // Comments
        if (state.comments.isNotEmpty()) {
            item { Text("Comments", style = MaterialTheme.typography.titleSmall) }
            items(state.comments, key = { it.id }) { comment ->
                GITOFYCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(LocalSpacing.current.lg)) {
                        Text(comment.author, style = MaterialTheme.typography.titleSmall)
                        Text(comment.body, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilesTab(diffFiles: List<DiffFile>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(LocalSpacing.current.lg),
        verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)
    ) {
        items(diffFiles, key = { it.filename }) { file ->
            DiffFileCard(file)
        }
    }
}

@Composable
private fun DiffFileCard(file: DiffFile) {
    var expanded by remember { mutableStateOf(false) }
    GITOFYCard(modifier = Modifier.fillMaxWidth(), onClick = { expanded = !expanded }) {
        Column(modifier = Modifier.padding(LocalSpacing.current.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when (file.status.name) {
                        "ADDED" -> Icons.Default.Add
                        "REMOVED" -> Icons.Default.Delete
                        "RENAMED" -> Icons.Default.DriveFileMove
                        else -> Icons.Default.Edit
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(LocalSpacing.current.sm))
                Text(file.filename, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("+${file.additions} -${file.deletions}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (expanded && file.patch != null) {
                Spacer(modifier = Modifier.height(LocalSpacing.current.sm))
                val lines = file.patch.split("\n")
                lines.forEach { line ->
                    val color = when {
                        line.startsWith("+") && !line.startsWith("+++") -> MaterialTheme.colorScheme.primary
                        line.startsWith("-") && !line.startsWith("---") -> MaterialTheme.colorScheme.error
                        line.startsWith("@@") -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Text(line, style = MaterialTheme.typography.bodySmall, color = color)
                }
            }
        }
    }
}

@Composable
private fun CommitsTab(state: PullRequestDetailUiState) {
    val pr = state.pr ?: return
    Column(modifier = Modifier.fillMaxSize().padding(LocalSpacing.current.lg)) {
        GITOFYCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(LocalSpacing.current.lg)) {
                Text("Changes", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(LocalSpacing.current.sm))
                Text("${pr.changedFiles} files changed")
                Text("+${pr.additions} additions")
                Text("-${pr.deletions} deletions")
                Text("${pr.commits} commits")
            }
        }
    }
}
