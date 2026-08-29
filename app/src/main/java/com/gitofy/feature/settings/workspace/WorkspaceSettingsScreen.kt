package com.gitofy.feature.settings.workspace

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
fun WorkspaceSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val s = uiState.appSettings

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { GITOFYTopAppBar(title = "Workspace & Project", onBack = onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item {
                SectionHeader("Project Behavior")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column {
                        SettingSwitchRow(title = "Open Last Project", supportingText = "Automatically open the last project on launch", icon = Icons.Default.FolderOpen, checked = s.openLastProject, onCheckedChange = viewModel::setOpenLastProject)
                        SettingRowDivider()
                        SettingSwitchRow(title = "Restore Workspace Layout", supportingText = "Restore tabs and panels from last session", icon = Icons.Default.ViewModule, checked = s.restoreWorkspaceLayout, onCheckedChange = viewModel::setRestoreWorkspaceLayout)
                    }
                }
            }

            item {
                SectionHeader("File Handling")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column {
                        SettingSwitchRow(title = "Auto-save", supportingText = "Save changes automatically", icon = Icons.Default.Save, checked = s.workspaceAutoSave, onCheckedChange = viewModel::setWorkspaceAutoSave)
                        SettingRowDivider()
                        SettingSwitchRow(title = "Confirm Before Delete", supportingText = "Ask before deleting files or projects", icon = Icons.Default.Delete, checked = s.confirmBeforeDelete, onCheckedChange = viewModel::setConfirmBeforeDelete)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = LocalSpacing.current.lg, vertical = 4.dp))
}
