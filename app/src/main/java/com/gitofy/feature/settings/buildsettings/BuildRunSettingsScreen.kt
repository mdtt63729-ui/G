package com.gitofy.feature.settings.buildsettings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gitofy.core.settings.AppSettingsViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Stable Build & Run settings destination. Kept deliberately self-contained
 * so the Settings route remains valid even when build tooling is unavailable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildRunSettingsScreen(
    onBack: () -> Unit,
    viewModel: AppSettingsViewModel = hiltViewModel()
) {
    // The root settings model exposes the persisted build preferences.
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Build & Run") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                BuildToggle(
                    title = "Automatic build",
                    description = "Start the configured build automatically after supported repository operations.",
                    checked = settings.autoBuild,
                    onCheckedChange = viewModel::setAutoBuild
                )
            }
            item {
                BuildToggle(
                    title = "Build notifications",
                    description = "Notify when a build finishes or fails.",
                    checked = settings.buildNotifications,
                    onCheckedChange = viewModel::setBuildNotifications
                )
            }
            item {
                BuildToggle(
                    title = "Automatic build retry",
                    description = "Retry a failed build when the operation supports retrying.",
                    checked = settings.autoBuildRetry,
                    onCheckedChange = viewModel::setAutoBuildRetryEnabled
                )
            }
            item {
                Text(
                    "Build variant: ${settings.buildVariant}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BuildToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
