package com.gitofy.feature.pulls

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
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
import com.gitofy.core.designsystem.tokens.MotionTokens
import com.gitofy.domain.model.DiffFile

/**
 * Pull Request Detail — PRD Phase 4 §3/§4/§5.
 *
 * PR Header -> State/Metadata -> Actions -> Tabs (Conversation / Files /
 * Commits), with an animated M3 tab indicator and a subtle cross-fade
 * between tab bodies rather than a hard screen replacement.
 */
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
        if (uiState.isLoading && uiState.pr == null) {
            LoadingView(modifier = Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }

        val pr = uiState.pr
        if (pr == null) {
            if (uiState.error != null) {
                DeveloperErrorState(
                    message = uiState.error!!,
                    onRetry = { viewModel.load(owner, repo, prNumber) },
                    modifier = Modifier.fillMaxSize().padding(padding)
                )
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // PR Header -> State/Metadata
            Column(modifier = Modifier.padding(LocalSpacing.current.lg)) {
                Text(pr.title, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(LocalSpacing.current.xs))
                MetadataRow(text = "${pr.headBranch} → ${pr.baseBranch} · +${pr.additions} -${pr.deletions}")
                Spacer(modifier = Modifier.height(LocalSpacing.current.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.xs)) {
                    IconStatusBadge(pullRequestStatusVisual(pr.state, pr.isDraft, pr.isMerged))
                    StatusBadge("${pr.changedFiles} files", StatusType.Info)
                }
            }

            // Tabs with animated indicator movement
            TabRow(selectedTabIndex = uiState.selectedTab.ordinal) {
                PRTab.entries.forEach { tab ->
                    Tab(
                        selected = uiState.selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = { Text(tab.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            DeveloperTabContent(targetState = uiState.selectedTab, modifier = Modifier.weight(1f)) { tab ->
                when (tab) {
                    PRTab.CONVERSATION -> ConversationTab(uiState)
                    PRTab.FILES -> FilesTab(uiState.diffFiles)
                    PRTab.COMMITS -> CommitsTab(uiState)
                }
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
    if (state.reviews.isEmpty() && state.comments.isEmpty()) {
        DeveloperEmptyState(
            icon = Icons.Default.Forum,
            title = "No activity yet",
            subtitle = "Reviews and comments will show up here.",
            modifier = Modifier.fillMaxSize()
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(LocalSpacing.current.lg),
        verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)
    ) {
        // Reviews
        if (state.reviews.isNotEmpty()) {
            item { SectionHeader("Reviews") }
            items(state.reviews, key = { "review-${it.id}" }) { review ->
                DeveloperCard {
                    Column(modifier = Modifier.padding(LocalSpacing.current.lg)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(review.user, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            StatusBadge(
                                review.state,
                                when (review.state) {
                                    "APPROVED" -> StatusType.Success
                                    "CHANGES_REQUESTED" -> StatusType.Error
                                    else -> StatusType.Neutral
                                }
                            )
                        }
                        review.submittedAt?.let {
                            Spacer(modifier = Modifier.height(2.dp))
                            MetadataRow(text = it)
                        }
                        review.body?.let {
                            Spacer(modifier = Modifier.height(LocalSpacing.current.xs))
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // Comments — avatar/author/timestamp/body/actions hierarchy (PRD §4)
        if (state.comments.isNotEmpty()) {
            item { SectionHeader("Comments") }
            items(state.comments, key = { "comment-${it.id}" }) { comment ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = LocalSpacing.current.xs)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(LocalSpacing.current.sm))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(comment.author, style = MaterialTheme.typography.titleSmall)
                            MetadataRow(text = comment.createdAt)
                        }
                    }
                    Spacer(modifier = Modifier.height(LocalSpacing.current.xs))
                    Text(
                        comment.body,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 36.dp)
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
private fun FilesTab(diffFiles: List<DiffFile>) {
    if (diffFiles.isEmpty()) {
        DeveloperEmptyState(
            icon = Icons.Default.Description,
            title = "No file changes",
            subtitle = "This pull request doesn't change any files.",
            modifier = Modifier.fillMaxSize()
        )
        return
    }
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
    DeveloperCard(onClick = { expanded = !expanded }) {
        Column(
            modifier = Modifier
                .padding(LocalSpacing.current.lg)
                .animateContentSize(tween(MotionTokens.DurationMedium))
        ) {
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
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = LocalSpacing.current.xs).size(18.dp)
                )
            }
            if (expanded && file.patch != null) {
                Spacer(modifier = Modifier.height(LocalSpacing.current.sm))
                CodeContainer {
                    Column {
                        file.patch.split("\n").forEach { line ->
                            val color = when {
                                line.startsWith("+") && !line.startsWith("+++") -> MaterialTheme.colorScheme.primary
                                line.startsWith("-") && !line.startsWith("---") -> MaterialTheme.colorScheme.error
                                line.startsWith("@@") -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                            CodeLine(text = line, color = color)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommitsTab(state: PullRequestDetailUiState) {
    val pr = state.pr ?: return
    Column(
        modifier = Modifier.fillMaxSize().padding(LocalSpacing.current.lg),
        verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)
    ) {
        SectionHeader("Changes")
        DeveloperCard {
            Column(modifier = Modifier.padding(LocalSpacing.current.lg)) {
                InfoRow(label = "Commits", value = "${pr.commits}", icon = Icons.Default.Commit)
                InfoRow(label = "Files changed", value = "${pr.changedFiles}", icon = Icons.Default.Description)
                InfoRow(label = "Additions", value = "+${pr.additions}", icon = Icons.Default.Add)
                InfoRow(label = "Deletions", value = "-${pr.deletions}", icon = Icons.Default.Remove)
            }
        }
    }
}
