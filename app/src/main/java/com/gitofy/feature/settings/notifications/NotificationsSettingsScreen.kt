package com.gitofy.feature.settings.notifications

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
import com.gitofy.core.designsystem.components.GITOFYCard
import com.gitofy.core.designsystem.components.GITOFYTopAppBar
import com.gitofy.core.designsystem.components.SettingRowDivider
import com.gitofy.core.designsystem.components.SettingSwitchRow
import com.gitofy.core.designsystem.theme.LocalSpacing
import com.gitofy.feature.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val s = uiState.appSettings

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { GITOFYTopAppBar(title = "Notifications", onBack = onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item {
                SectionHeader("Build")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column {
                        SettingSwitchRow(title = "Build Completed", icon = Icons.Default.CheckCircle, checked = s.notifyBuildCompleted, onCheckedChange = viewModel::setNotifyBuildCompleted)
                        SettingRowDivider()
                        SettingSwitchRow(title = "Build Failed", icon = Icons.Default.Error, checked = s.notifyBuildFailed, onCheckedChange = viewModel::setNotifyBuildFailed)
                    }
                }
            }

            item {
                SectionHeader("AI Tasks")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column {
                        SettingSwitchRow(title = "AI Task Completed", icon = Icons.Default.SmartToy, checked = s.notifyAITaskCompleted, onCheckedChange = viewModel::setNotifyAITaskCompleted)
                        SettingRowDivider()
                        SettingSwitchRow(title = "AI Task Failed", icon = Icons.Default.ErrorOutline, checked = s.notifyAITaskFailed, onCheckedChange = viewModel::setNotifyAITaskFailed)
                    }
                }
            }

            item {
                SectionHeader("Git Operations")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column {
                        SettingSwitchRow(title = "Git Operation Completed", icon = Icons.Default.Code, checked = s.notifyGitCompleted, onCheckedChange = viewModel::setNotifyGitCompleted)
                        SettingRowDivider()
                        SettingSwitchRow(title = "Git Operation Failed", icon = Icons.Default.Error, checked = s.notifyGitFailed, onCheckedChange = viewModel::setNotifyGitFailed)
                    }
                }
            }

            item {
                SectionHeader("Downloads")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    SettingSwitchRow(title = "Download Progress", supportingText = "Show real-time artifact download progress", icon = Icons.Default.Download, checked = s.notifyDownloads, onCheckedChange = viewModel::setNotifyDownloads)
                }
            }

            item {
                SectionHeader("Application")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    SettingSwitchRow(title = "Important Errors", supportingText = "Notify on application errors", icon = Icons.Default.Warning, checked = s.notifyAppErrors, onCheckedChange = viewModel::setNotifyAppErrors)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = LocalSpacing.current.lg, vertical = 4.dp))
}
