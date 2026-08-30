package com.gitofy.feature.health

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gitofy.core.designsystem.components.DeveloperCard
import com.gitofy.core.designsystem.components.DeveloperEmptyState
import com.gitofy.core.designsystem.components.GITOFYTopAppBar
import com.gitofy.core.designsystem.components.SectionHeader
import com.gitofy.core.designsystem.components.StatusBadge
import com.gitofy.core.designsystem.components.StatusType
import com.gitofy.core.designsystem.theme.LocalSpacing
import com.gitofy.core.designsystem.tokens.Dimensions
import com.gitofy.domain.model.HealthStatus

/**
 * Repository Health — PRD Phase 4 §14.
 *
 * At-a-glance developer dashboard: CI/PR/Issue health cards plus an
 * activity summary. Single column on compact widths, a responsive grid
 * on medium/expanded widths (PRD §17).
 */
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
            BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
                val isExpanded = maxWidth >= Dimensions.mediumMaxWidth
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(LocalSpacing.current.lg),
                    verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.md)
                ) {
                    SectionHeader("Health Overview")
                    if (isExpanded) {
                        Row(horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.md)) {
                            HealthCard("CI Health", health.ciHealth, health.failedWorkflows, "failed workflows", Modifier.weight(1f))
                            HealthCard("PR Health", health.prHealth, health.openPRs, "open PRs", Modifier.weight(1f))
                            HealthCard("Issue Health", health.issueHealth, health.openIssues, "open issues", Modifier.weight(1f))
                        }
                    } else {
                        HealthCard("CI Health", health.ciHealth, health.failedWorkflows, "failed workflows")
                        HealthCard("PR Health", health.prHealth, health.openPRs, "open PRs")
                        HealthCard("Issue Health", health.issueHealth, health.openIssues, "open issues")
                    }

                    SectionHeader("Activity")
                    DeveloperCard {
                        Column(modifier = Modifier.padding(LocalSpacing.current.lg)) {
                            ActivityRow("Recent commits", health.recentCommits)
                            ActivityRow("Stale branches", health.staleBranches)
                            ActivityRow("Recent releases", health.recentReleases)
                        }
                    }
                }
            }
        } else if (uiState.error != null) {
            DeveloperEmptyState(
                icon = Icons.Default.MonitorHeart,
                title = "Couldn't load health data",
                subtitle = uiState.error!!,
                modifier = Modifier.fillMaxSize().padding(padding)
            )
        }
    }
}

@Composable
private fun ActivityRow(label: String, value: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("$value", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun HealthCard(title: String, status: HealthStatus, count: Int, label: String, modifier: Modifier = Modifier) {
    DeveloperCard(modifier = modifier) {
        Row(modifier = Modifier.padding(LocalSpacing.current.lg), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text("$count $label", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
