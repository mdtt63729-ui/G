package com.gitofy.feature.branches

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
import com.gitofy.domain.model.BranchInfo

/**
 * Branches — PRD Phase 4 §1.
 *
 * Standardized list rows, status badges, repository metadata typography,
 * and consistent dividers/spacing via [DeveloperCard] + [MetadataRow].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BranchListScreen(
    owner: String,
    repo: String,
    onBack: () -> Unit,
    onCompare: (String, String) -> Unit,
    viewModel: BranchListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(owner, repo) { viewModel.load(owner, repo) }

    // The first branch returned by the API is conventionally the
    // repository's default branch when no explicit flag is available.
    val defaultBranchName = uiState.branches.firstOrNull()?.name

    Scaffold(topBar = { GITOFYTopAppBar(title = "Branches", onBack = onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchChange,
                modifier = Modifier.fillMaxWidth().padding(LocalSpacing.current.lg),
                placeholder = { Text("Search branches...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            val filtered = uiState.branches.filter { it.name.contains(uiState.searchQuery, ignoreCase = true) }

            when {
                uiState.isLoading && uiState.branches.isEmpty() -> {
                    LazyColumn(contentPadding = PaddingValues(LocalSpacing.current.lg)) {
                        items(6) { SkeletonListItem() }
                    }
                }
                filtered.isEmpty() && uiState.searchQuery.isNotEmpty() -> {
                    DeveloperEmptyState(
                        icon = Icons.Default.SearchOff,
                        title = "No matching branches",
                        subtitle = "Try a different search term.",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                filtered.isEmpty() -> {
                    DeveloperEmptyState(
                        icon = Icons.Default.CallSplit,
                        title = "No branches found",
                        subtitle = "This repository has no branches yet.",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = LocalSpacing.current.lg,
                            end = LocalSpacing.current.lg,
                            bottom = LocalSpacing.current.lg
                        ),
                        verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)
                    ) {
                        items(filtered, key = { it.name }) { branch ->
                            val isDefault = branch.name == defaultBranchName
                            BranchCard(
                                branch = branch,
                                isDefault = isDefault,
                                onDelete = { viewModel.showDeleteConfirm(branch) },
                                onClick = { onCompare(branch.name, defaultBranchName ?: branch.name) }
                            )
                        }
                    }
                }
            }
        }
    }

    uiState.showDeleteConfirm?.let { branch ->
        AlertDialog(
            onDismissRequest = { viewModel.hideDeleteConfirm() },
            title = { Text("Delete Branch") },
            text = { Text("Delete branch '${branch.name}'? This action cannot be undone.") },
            confirmButton = { TextButton(onClick = { viewModel.hideDeleteConfirm() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { viewModel.hideDeleteConfirm() }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun BranchCard(
    branch: BranchInfo,
    isDefault: Boolean,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    DeveloperCard(onClick = onClick) {
        Row(
            modifier = Modifier.padding(LocalSpacing.current.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CallSplit,
                contentDescription = null,
                tint = if (isDefault) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(LocalSpacing.current.md))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        branch.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isDefault) {
                        Spacer(modifier = Modifier.width(LocalSpacing.current.xs))
                        StatusBadge("Default", StatusType.Info)
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                MetadataRow(text = branch.commitSha.take(7), icon = Icons.Default.Commit)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete branch ${branch.name}", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
