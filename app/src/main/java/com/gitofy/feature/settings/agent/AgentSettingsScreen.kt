package com.gitofy.feature.settings.agent

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
fun AgentSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val s = uiState.appSettings

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { GITOFYTopAppBar(title = "AI & Agent", onBack = onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item {
                SectionHeader("Agent Mode")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    SettingSwitchRow(
                        title = "Agent Mode",
                        supportingText = "Enable autonomous AI agent",
                        icon = Icons.Default.SmartToy,
                        checked = s.agentMode,
                        onCheckedChange = viewModel::setAgentMode
                    )
                }
            }

            item {
                SectionHeader("Tool Execution")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column {
                        SettingSwitchRow(
                            title = "Automatic Tool Execution",
                            supportingText = "Let the agent run tools without asking",
                            icon = Icons.Default.AutoMode,
                            checked = s.autoToolExecution,
                            onCheckedChange = viewModel::setAutoToolExecution
                        )
                        SettingRowDivider()
                        SettingSwitchRow(
                            title = "Confirm Dangerous Actions",
                            supportingText = "Ask before destructive operations",
                            icon = Icons.Default.Warning,
                            checked = s.confirmDangerousActions,
                            onCheckedChange = viewModel::setConfirmDangerousActions
                        )
                    }
                }
            }

            item {
                SectionHeader("Limits")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column(modifier = Modifier.padding(LocalSpacing.current.md)) {
                        Text("Max Agent Iterations: ${s.maxAgentIterations}", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = s.maxAgentIterations.toFloat(),
                            onValueChange = { viewModel.setMaxAgentIterations(it.toInt()) },
                            valueRange = 1f..50f,
                            steps = 48
                        )
                    }
                }
            }

            item {
                SectionHeader("Automation")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column {
                        SettingSwitchRow(
                            title = "Automatic Error Fixing",
                            supportingText = "Auto-fix errors when detected",
                            icon = Icons.Default.Build,
                            checked = s.autoErrorFixing,
                            onCheckedChange = viewModel::setAutoErrorFixing
                        )
                        SettingRowDivider()
                        SettingSwitchRow(
                            title = "Automatic Build Retry",
                            supportingText = "Retry builds automatically on failure",
                            icon = Icons.Default.Refresh,
                            checked = s.autoBuildRetry,
                            onCheckedChange = viewModel::setAutoBuildRetry
                        )
                    }
                }
            }

            item {
                SectionHeader("Response Style")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column {
                        ResponseStyleRow("Concise", s.aiResponseStyle == "concise") { viewModel.setAiResponseStyle("concise") }
                        SettingRowDivider()
                        ResponseStyleRow("Detailed", s.aiResponseStyle == "detailed") { viewModel.setAiResponseStyle("detailed") }
                        SettingRowDivider()
                        ResponseStyleRow("Balanced", s.aiResponseStyle == "balanced") { viewModel.setAiResponseStyle("balanced") }
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

@Composable
private fun ResponseStyleRow(label: String, selected: Boolean, onClick: () -> Unit) {
    SettingRow(title = label, icon = if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked, onClick = onClick)
}
