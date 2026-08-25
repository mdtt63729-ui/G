package com.gitofy.feature.releases

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.gitofy.domain.model.ReleaseSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseDashboardScreen(
    owner: String,
    repo: String,
    onBack: () -> Unit,
    onCreateRelease: () -> Unit,
    viewModel: ReleaseDashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(owner, repo) { viewModel.load(owner, repo) }

    Scaffold(
        topBar = { GITOFYTopAppBar(title = "Releases", onBack = onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateRelease) {
                Icon(Icons.Default.Add, contentDescription = "Create Release")
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            LoadingView(modifier = Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(LocalSpacing.current.lg),
            verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)
        ) {
            uiState.latestRelease?.let { latest ->
                item {
                    Text("Latest Release", style = MaterialTheme.typography.titleMedium)
                    ReleaseCard(latest)
                }
            }

            if (uiState.draftReleases.isNotEmpty()) {
                item {
                    Text("Drafts", style = MaterialTheme.typography.titleSmall)
                }
                items(uiState.draftReleases, key = { it.id }) { ReleaseCard(it) }
            }

            if (uiState.preReleases.isNotEmpty()) {
                item {
                    Text("Pre-releases", style = MaterialTheme.typography.titleSmall)
                }
                items(uiState.preReleases, key = { it.id }) { ReleaseCard(it) }
            }

            if (uiState.releases.isNotEmpty()) {
                item { Text("All Releases", style = MaterialTheme.typography.titleSmall) }
                items(uiState.releases, key = { it.id }) { ReleaseCard(it) }
            }
        }
    }
}

@Composable
private fun ReleaseCard(release: ReleaseSummary) {
    GITOFYCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(LocalSpacing.current.lg), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.NewReleases, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(LocalSpacing.current.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(release.tagName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                release.name?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (release.isDraft) StatusBadge("Draft", StatusType.Neutral)
            if (release.isPreRelease) StatusBadge("Pre", StatusType.Warning)
            if (!release.isDraft && !release.isPreRelease) StatusBadge("Latest", StatusType.Success)
        }
    }
}
