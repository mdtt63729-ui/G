package com.gitofy.feature.settings.github

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
import com.gitofy.core.designsystem.components.SettingRow
import com.gitofy.core.designsystem.components.SettingRowDivider
import com.gitofy.core.designsystem.components.SettingSwitchRow
import com.gitofy.core.designsystem.theme.LocalSpacing
import com.gitofy.feature.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitGitHubSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val s = uiState.appSettings
    var showBranchDialog by remember { mutableStateOf(false) }
    var branchInput by remember { mutableStateOf(s.gitDefaultBranch) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { GITOFYTopAppBar(title = "Git & GitHub", onBack = onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item {
                SectionHeader("GitHub Account")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column(modifier = Modifier.padding(LocalSpacing.current.md)) {
                        Text(uiState.userLogin ?: "Not signed in", style = MaterialTheme.typography.titleSmall)
                        Text(if (uiState.hasCredentials) "Token stored securely" else "No credentials", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                SectionHeader("Branch Behavior")
                GITOFYCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg),
                    onClick = { showBranchDialog = true; branchInput = s.gitDefaultBranch }
                ) {
                    SettingRow(title = "Default Branch", supportingText = s.gitDefaultBranch, icon = Icons.Default.Code)
                }
            }

            item {
                SectionHeader("Operations")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column {
                        SettingSwitchRow(title = "Confirm Destructive Operations", supportingText = "Ask before force push, delete branch, etc.", icon = Icons.Default.Warning, checked = s.gitConfirmDestructive, onCheckedChange = viewModel::setGitConfirmDestructive)
                        SettingRowDivider()
                        SettingSwitchRow(title = "Auto Push After Commit", supportingText = "Automatically push after committing", icon = Icons.Default.CloudUpload, checked = s.gitAutoPush, onCheckedChange = viewModel::setGitAutoPush)
                    }
                }
            }
        }
    }

    if (showBranchDialog) {
        AlertDialog(
            onDismissRequest = { showBranchDialog = false },
            title = { Text("Default Branch") },
            text = {
                OutlinedTextField(value = branchInput, onValueChange = { branchInput = it }, label = { Text("Branch name") }, singleLine = true)
            },
            confirmButton = { TextButton(onClick = { viewModel.setGitDefaultBranch(branchInput.trim()); showBranchDialog = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { showBranchDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = LocalSpacing.current.lg, vertical = 4.dp))
}
