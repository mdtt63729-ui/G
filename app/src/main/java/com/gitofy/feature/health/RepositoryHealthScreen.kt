package com.gitofy.feature.health

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gitofy.core.designsystem.components.GITOFYCard
import com.gitofy.core.designsystem.components.GITOFYTopAppBar
import com.gitofy.core.designsystem.components.StatusBadge
import com.gitofy.core.designsystem.components.StatusType
import com.gitofy.core.designsystem.theme.LocalSpacing
import com.gitofy.domain.model.HealthStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepositoryHealthScreen(
    owner: String,
    repo: String,
    onBack: () -> Unit,
    viewModel: RepositoryHealthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(owner, repo) { viewModel.load(owner, repo) }

    Scaffold(topBar = { GITOFYTopAppBar(title = "Repository Health", onBack = onBack) }) { padding ->
        val health = uiState.health
        if (uiState.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else if (health != null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(LocalSpacing.current.lg),
                verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.md)
            ) {
                HealthCard("CI Health", health.ciHealth, health.failedWorkflows, "failed workflows")
                HealthCard("PR Health", health.prHealth, health.openPRs, "open PRs")
                HealthCard("Issue Health", health.issueHealth, health.openIssues, "open issues")
                GITOFYCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(LocalSpacing.current.lg)) {
                        Text("Activity", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Recent commits: ${health.recentCommits}")
                        Text("Stale branches: ${health.staleBranches}")
                        Text("Recent releases: ${health.recentReleases}")
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthCard(title: String, status: HealthStatus, count: Int, label: String) {
    GITOFYCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(LocalSpacing.current.lg), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text("$count $label", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(8.dp))
            StatusBadge(
                status.name.replace("_", " "),
                when (status) {
                    HealthStatus.HEALTHY -> StatusType.Success
                    HealthStatus.NEEDS_ATTENTION -> StatusType.Warning
                    HealthStatus.CRITICAL -> StatusType.Error
                    HealthStatus.UNKNOWN -> StatusType.Neutral
                }
            )
        }
    }
}
