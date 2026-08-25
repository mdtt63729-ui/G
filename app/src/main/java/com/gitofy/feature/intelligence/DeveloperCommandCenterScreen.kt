package com.gitofy.feature.intelligence

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
import com.gitofy.core.designsystem.components.*
import com.gitofy.core.designsystem.theme.LocalSpacing
import com.gitofy.domain.model.AttentionItem
import com.gitofy.domain.model.AttentionPriority

/**
 * Developer Command Center — PRD v7.0 Section 148.
 * Final home architecture with Attention, Project Health, Active CI, Release, AI Assistant.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperCommandCenterScreen(
    attentionItems: List<AttentionItem>,
    projectHealth: List<Pair<String, String>>, // repoName, statusIcon
    activeBuilds: List<Pair<String, String>>, // repoName, status
    releaseTag: String?,
    releaseReady: Boolean,
    onAttentionClick: (AttentionItem) -> Unit,
    onAIAssistantClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(LocalSpacing.current.lg),
        verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.md)
    ) {
        // Attention Center
        if (attentionItems.isNotEmpty()) {
            item {
                Text("ATTENTION", style = MaterialTheme.typography.titleMedium)
            }
            items(attentionItems.take(5)) { item ->
                AttentionCard(item, onClick = { onAttentionClick(item) })
            }
        }

        // Project Health
        if (projectHealth.isNotEmpty()) {
            item {
                Text("PROJECT HEALTH", style = MaterialTheme.typography.titleMedium)
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)
                ) {
                    projectHealth.forEach { (name, status) ->
                        GITOFYCard(modifier = Modifier.weight(1f)) {
                            Column(
                                modifier = Modifier.padding(LocalSpacing.current.md),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(name.take(8), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(status, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }

        // Active CI
        if (activeBuilds.isNotEmpty()) {
            item {
                Text("ACTIVE CI", style = MaterialTheme.typography.titleMedium)
            }
            items(activeBuilds.take(3)) { (repo, status) ->
                GITOFYCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(LocalSpacing.current.lg), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(LocalSpacing.current.sm))
                        Text(repo, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Release
        releaseTag?.let { tag ->
            item {
                Text("RELEASE", style = MaterialTheme.typography.titleMedium)
            }
            item {
                GITOFYCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(LocalSpacing.current.lg), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NewReleases, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(LocalSpacing.current.sm))
                        Text(tag, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        StatusBadge(if (releaseReady) "Ready" else "Not Ready", if (releaseReady) StatusType.Success else StatusType.Warning)
                    }
                }
            }
        }

        // AI Assistant
        item {
            GITOFYCard(modifier = Modifier.fillMaxWidth(), onClick = onAIAssistantClick) {
                Row(modifier = Modifier.padding(LocalSpacing.current.lg), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(LocalSpacing.current.md))
                    Text("Ask about your projects...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AttentionCard(item: AttentionItem, onClick: () -> Unit) {
    val (color, icon) = when (item.priority) {
        AttentionPriority.CRITICAL -> MaterialTheme.colorScheme.error to Icons.Default.Error
        AttentionPriority.HIGH -> MaterialTheme.colorScheme.tertiary to Icons.Default.Warning
        AttentionPriority.MEDIUM -> MaterialTheme.colorScheme.primary to Icons.Default.Notifications
        AttentionPriority.LOW -> MaterialTheme.colorScheme.onSurfaceVariant to Icons.Default.Info
    }
    GITOFYCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(modifier = Modifier.padding(LocalSpacing.current.lg), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(LocalSpacing.current.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.bodySmall)
                Text(item.repoName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
