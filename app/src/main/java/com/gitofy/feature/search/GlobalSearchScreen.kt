package com.gitofy.feature.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gitofy.core.designsystem.components.GITOFYCard
import com.gitofy.core.designsystem.components.GITOFYTopAppBar
import com.gitofy.core.designsystem.theme.LocalSpacing

/**
 * Global Search Screen — PRD v3.0 Section 63.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(
    onBack: () -> Unit,
    onRepoClick: (String, String) -> Unit,
    viewModel: GlobalSearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            GITOFYTopAppBar(title = "Search", onBack = onBack)
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(LocalSpacing.current.lg),
                placeholder = { Text("Search repositories...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(LocalSpacing.current.lg),
                verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)
            ) {
                items(uiState.results, key = { it.title + it.subtitle }) { result ->
                    GITOFYCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            if (result.type == SearchResultType.REPOSITORY) {
                                onRepoClick(result.ownerLogin, result.repoName)
                            }
                        }
                    ) {
                        Column(modifier = Modifier.padding(LocalSpacing.current.lg)) {
                            Text(result.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                result.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
