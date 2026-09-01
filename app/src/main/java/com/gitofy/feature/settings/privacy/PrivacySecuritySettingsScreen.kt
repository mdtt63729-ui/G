package com.gitofy.feature.settings.privacy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gitofy.core.designsystem.components.GITOFYButton
import com.gitofy.core.designsystem.components.GITOFYButtonType
import com.gitofy.core.designsystem.components.GITOFYCard
import com.gitofy.core.designsystem.components.GITOFYTopAppBar
import com.gitofy.core.designsystem.components.SettingRow
import com.gitofy.core.designsystem.components.SettingRowDivider
import com.gitofy.core.designsystem.components.SettingSwitchRow
import com.gitofy.core.designsystem.theme.LocalSpacing
import com.gitofy.feature.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySecuritySettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val s = uiState.appSettings
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showClearCredentialsDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { GITOFYTopAppBar(title = "Privacy & Security", onBack = onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item {
                SectionHeader("Credential Status")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column(modifier = Modifier.padding(LocalSpacing.current.md)) {
                        val configuredCount = uiState.providerInstances.count { it.apiKeyHint.isNotBlank() }
                        Text("API Keys: $configuredCount configured", style = MaterialTheme.typography.bodyMedium)
                        Text("Keys are stored encrypted and never leave your device", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                SectionHeader("Data Collection")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column {
                        SettingSwitchRow(title = "Analytics", supportingText = "Send anonymous usage data", icon = Icons.Default.Analytics, checked = s.analyticsEnabled, onCheckedChange = viewModel::setAnalyticsEnabled)
                        SettingRowDivider()
                        SettingSwitchRow(title = "Crash Reporting", supportingText = "Send crash reports automatically", icon = Icons.Default.BugReport, checked = s.crashReportingEnabled, onCheckedChange = viewModel::setCrashReportingEnabled)
                    }
                }
            }

            item {
                SectionHeader("Local Data")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column {
                        SettingRow(
                            title = "Clear Cached Model Metadata",
                            supportingText = "Remove cached model lists from providers",
                            icon = Icons.Default.CleaningServices,
                            onClick = { showClearCacheDialog = true }
                        )
                        SettingRowDivider()
                        SettingRow(
                            title = "Clear Stored Credentials",
                            supportingText = "Remove all API keys — irreversible",
                            icon = Icons.Default.DeleteForever,
                            onClick = { showClearCredentialsDialog = true }
                        )
                    }
                }
            }
        }
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("Clear Cache") },
            text = { Text("This will remove all cached model metadata. You can reload them from the provider configuration page.") },
            confirmButton = { TextButton(onClick = { viewModel.clearCachedModels(); showClearCacheDialog = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { showClearCacheDialog = false }) { Text("Cancel") } }
        )
    }

    if (showClearCredentialsDialog) {
        AlertDialog(
            onDismissRequest = { showClearCredentialsDialog = false },
            title = { Text("Clear All Credentials") },
            text = { Text("This will permanently delete all stored API keys. This action cannot be undone.") },
            confirmButton = { TextButton(onClick = { viewModel.clearAllCredentials(); showClearCredentialsDialog = false }) { Text("Delete All", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showClearCredentialsDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = LocalSpacing.current.lg, vertical = 4.dp))
}
