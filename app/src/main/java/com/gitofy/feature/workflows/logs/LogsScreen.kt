package com.gitofy.feature.workflows.logs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gitofy.core.designsystem.components.ErrorBanner
import com.gitofy.core.designsystem.components.GITOFYTopAppBar
import com.gitofy.core.designsystem.components.LoadingIndicator
import com.gitofy.core.designsystem.components.TerminalLogInspector
import com.gitofy.core.designsystem.theme.LocalSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    owner: String,
    repo: String,
    jobId: Long,
    onBack: () -> Unit,
    viewModel: LogsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var searchText by remember { mutableStateOf("") }

    LaunchedEffect(owner, repo, jobId) {
        viewModel.loadLogs(owner, repo, jobId)
    }

    Scaffold(
        topBar = {
            GITOFYTopAppBar(title = "Logs", onBack = onBack)
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Search bar
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = LocalSpacing.current.lg, vertical = LocalSpacing.current.sm),
                placeholder = { Text("Search in logs...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )

            when {
                uiState.isLoading -> LoadingIndicator()
                uiState.error != null -> ErrorBanner(
                    message = uiState.error!!,
                    onRetry = { viewModel.loadLogs(owner, repo, jobId) }
                )
                uiState.logs.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Text("No logs available", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> {
                    TerminalLogInspector(
                        logs = uiState.logs,
                        onSearch = searchText,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
