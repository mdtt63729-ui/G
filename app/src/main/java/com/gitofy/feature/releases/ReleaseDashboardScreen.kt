package com.gitofy.feature.releases

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
import com.gitofy.domain.model.ReleaseSummary

/**
 * Releases — PRD Phase 4 §13.
 *
 * Hierarchy: Version -> Release status -> Metadata (author, published
 * date) -> Release notes preview -> Assets.
 */
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

        val isEmpty = uiState.latestRelease == null && uiState.draftReleases.isEmpty() &&
            uiState.preReleases.isEmpty() && uiState.releases.isEmpty()

        if (isEmpty) {
            DeveloperEmptyState(
                icon = Icons.Default.NewReleases,
                title = "No releases yet",
                subtitle = "Publish your first release to see it here.",
                modifier = Modifier.fillMaxSize().padding(padding),
                actionText = "Create Release",
                onAction = onCreateRelease
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(LocalSpacing.current.lg),
            verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)
        ) {
            uiState.latestRelease?.let { latest ->
                item { SectionHeader("Latest Release") }
                item { ReleaseCard(latest, highlight = true) }
            }

            if (uiState.draftReleases.isNotEmpty()) {
                item { SectionHeader("Drafts") }
                items(uiState.draftReleases, key = { "draft-${it.id}" }) { ReleaseCard(it) }
            }

            if (uiState.preReleases.isNotEmpty()) {
                item { SectionHeader("Pre-releases") }
                items(uiState.preReleases, key = { "pre-${it.id}" }) { ReleaseCard(it) }
            }

            if (uiState.releases.isNotEmpty()) {
                item { SectionHeader("All Releases") }
                items(uiState.releases, key = { "all-${it.id}" }) { ReleaseCard(it) }
            }
        }
    }
}

@Composable
private fun ReleaseCard(release: ReleaseSummary, highlight: Boolean = false) {
    DeveloperCard {
        Column(modifier = Modifier.padding(LocalSpacing.current.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.NewReleases,
                    contentDescription = null,
                    tint = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(LocalSpacing.current.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(release.tagName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    release.name.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                when {
                    release.isDraft -> StatusBadge("Draft", StatusType.Neutral)
                    release.isPreRelease -> StatusBadge("Pre-release", StatusType.Warning)
                    else -> StatusBadge("Latest", StatusType.Success)
                }
            }
            Spacer(modifier = Modifier.height(LocalSpacing.current.xs))
            MetadataRow(text = "by ${release.authorLogin} · ${release.publishedAt ?: release.createdAt}")
            release.body?.takeIf { it.isNotBlank() }?.let {
                Spacer(modifier = Modifier.height(LocalSpacing.current.sm))
                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (release.assetCount > 0) {
                Spacer(modifier = Modifier.height(LocalSpacing.current.xs))
                MetadataRow(text = "${release.assetCount} assets", icon = Icons.Default.Archive)
            }
        }
    }
}
