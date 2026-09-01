package com.gitofy.feature.repositories

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gitofy.core.designsystem.components.*
import com.gitofy.core.designsystem.theme.LocalSpacing

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
            GITOFYFloatingActionButton(onClick = onCreateClick)
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            RepositorySearchField(
                query = uiState.query,
                onQueryChange = viewModel::onQueryChange
            )

            when {
                uiState.isLoading && uiState.repositories.isEmpty() -> {
                    LazyColumnSkeleton()
                }

                uiState.error != null && uiState.repositories.isEmpty() -> {
                    ErrorBanner(
                        message = uiState.error!!,
                        onRetry = { viewModel.refresh() }
                    )
                }

                uiState.repositories.isEmpty() -> {
                    EmptyStateView(
                        icon = Icons.Default.Cloud,
                        title = "No repositories found",
                        subtitle = "Create a new repository from a ZIP project to get started.",
                        actionText = "Create Project",
                        onAction = onCreateClick
                    )
                }

                uiState.filteredRepositories.isEmpty() -> {
                    EmptyStateView(
                        icon = Icons.Default.SearchOff,
                        title = "No matches",
                        subtitle = "No repositories match \"${uiState.query}\"."
                    )
                }

                else -> {
                    PaginatedRepositoryList(
                        repositories = uiState.filteredRepositories,
                        isLoading = uiState.isLoadingMore && uiState.query.isBlank(),
                        onLoadMore = {
                            // Pagination only applies to the unfiltered, server-backed list.
                            if (uiState.query.isBlank()) viewModel.loadMore()
                        },
                        onRepoClick = onRepoClick
                    )
                }
            }
        }
    }
}

@Composable
private fun RepositorySearchField(
    query: String,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LocalSpacing.current.lg, vertical = LocalSpacing.current.sm),
        placeholder = { Text("Filter repositories...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
    )
}

@Composable
private fun LazyColumnSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = LocalSpacing.current.lg)
    ) {
        repeat(8) { SkeletonListItem() }
    }
}
