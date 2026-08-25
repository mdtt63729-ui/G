package com.gitofy.feature.repositories

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gitofy.core.designsystem.components.*
import com.gitofy.core.designsystem.theme.LocalSpacing
import com.gitofy.feature.home.RepositoryCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepositoryListScreen(
    onRepoClick: (String, String) -> Unit,
    onCreateClick: () -> Unit,
    viewModel: RepositoryListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            GITOFYTopAppBar(
                title = "Repositories",
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateClick,
                shape = MaterialTheme.shapes.large,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create")
            }
        }
    ) { padding ->
        if (uiState.isLoading && uiState.repositories.isEmpty()) {
            Column(modifier = Modifier.padding(padding)) {
                LazyColumn {
                    items(8) { SkeletonListItem() }
                }
            }
        } else if (uiState.repositories.isEmpty() && uiState.error == null) {
            EmptyStateView(
                icon = Icons.Default.Cloud,
                title = "No repositories found",
                subtitle = "Create a new repository from a ZIP project to get started.",
                actionText = "Create Project",
                onAction = onCreateClick,
                modifier = Modifier.padding(padding)
            )
        } else if (uiState.error != null && uiState.repositories.isEmpty()) {
            ErrorBanner(
                message = uiState.error!!,
                onRetry = { viewModel.refresh() },
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(LocalSpacing.current.lg),
                verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)
            ) {
                items(uiState.repositories, key = { it.id }) { repo ->
                    RepositoryCard(repo = repo) {
                        onRepoClick(repo.ownerLogin, repo.name)
                    }
                }
            }
        }
    }
}
