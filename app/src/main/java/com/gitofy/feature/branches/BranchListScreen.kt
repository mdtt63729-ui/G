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
import com.gitofy.domain.model.BranchDetail

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

    Scaffold(topBar = { GITOFYTopAppBar(title = "Branches", onBack = onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchChange,
                modifier = Modifier.fillMaxWidth().padding(LocalSpacing.current.lg),
                placeholder = { Text("Search branches...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )
            val filtered = uiState.branches.filter { it.name.contains(uiState.searchQuery, ignoreCase = true) }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(LocalSpacing.current.lg),
                verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)
            ) {
                items(filtered, key = { it.name }) { branch ->
                    BranchCard(branch, onDelete = { viewModel.showDeleteConfirm(branch) })
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
private fun BranchCard(branch: BranchDetail, onDelete: () -> Unit) {
    GITOFYCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(LocalSpacing.current.lg), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CallSplit, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(LocalSpacing.current.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(branch.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(branch.commitSha.take(7), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (branch.isDefault) StatusBadge("Default", StatusType.Info)
            if (branch.isProtected) StatusBadge("Protected", StatusType.Warning)
            if (!branch.isDefault) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
