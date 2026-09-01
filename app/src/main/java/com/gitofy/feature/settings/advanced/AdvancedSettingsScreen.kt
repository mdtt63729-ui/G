package com.gitofy.feature.settings.advanced

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
fun AdvancedSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val s = uiState.appSettings
    var showResetDialog by remember { mutableStateOf(false) }
    var resetCredentialsToo by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { GITOFYTopAppBar(title = "Advanced", onBack = onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item {
                SectionHeader("Developer Options")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column {
                        SettingSwitchRow(title = "Debug Mode", supportingText = "Enable verbose logging", icon = Icons.Default.BugReport, checked = s.debugMode, onCheckedChange = viewModel::setDebugMode)
                        SettingRowDivider()
                        SettingSwitchRow(title = "Experimental Features", supportingText = "Enable unreleased features", icon = Icons.Default.Science, checked = s.experimentalFeatures, onCheckedChange = viewModel::setExperimentalFeatures)
                    }
                }
            }

            item {
                SectionHeader("Maintenance")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column {
                        SettingRow(
                            title = "Clear Cached Model Metadata",
                            supportingText = "Remove cached model lists",
                            icon = Icons.Default.CleaningServices,
                            onClick = { viewModel.clearCachedModels() }
                        )
                    }
                }
            }

            item {
                SectionHeader("Reset")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column(modifier = Modifier.padding(LocalSpacing.current.md)) {
                        Text("Reset all settings to their default values.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Checkbox(checked = resetCredentialsToo, onCheckedChange = { resetCredentialsToo = it })
                            Text("Also delete API keys", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        GITOFYButton(
                            text = "Reset All Settings",
                            onClick = { showResetDialog = true },
                            type = GITOFYButtonType.Destructive,
                            fullWidth = true,
                            icon = Icons.Default.RestartAlt
                        )
                    }
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Settings") },
            text = {
                Text(if (resetCredentialsToo)
                    "This will reset all settings to defaults AND delete all API keys. This cannot be undone."
                else
                    "This will reset all settings to defaults. API keys will be preserved.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetAllSettings(clearCredentials = resetCredentialsToo)
                    showResetDialog = false
                }) { Text("Reset", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = LocalSpacing.current.lg, vertical = 4.dp))
}
