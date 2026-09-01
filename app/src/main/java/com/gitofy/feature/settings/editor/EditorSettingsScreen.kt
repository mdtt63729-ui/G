package com.gitofy.feature.settings.editor

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
fun EditorSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val s = uiState.appSettings

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { GITOFYTopAppBar(title = "Editor", onBack = onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item {
                SectionHeader("Font Size")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column(modifier = Modifier.padding(LocalSpacing.current.md)) {
                        Text("Font Size: ${s.editorFontSize}sp", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = s.editorFontSize.toFloat(),
                            onValueChange = { viewModel.setEditorFontSize(it.toInt()) },
                            valueRange = 8f..24f,
                            steps = 15
                        )
                    }
                }
            }

            item {
                SectionHeader("Display")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column {
                        SettingSwitchRow(title = "Line Numbers", icon = Icons.Default.FormatListNumbered, checked = s.editorLineNumbers, onCheckedChange = viewModel::setEditorLineNumbers)
                        SettingRowDivider()
                        SettingSwitchRow(title = "Word Wrap", icon = Icons.Default.WrapText, checked = s.editorWordWrap, onCheckedChange = viewModel::setEditorWordWrap)
                        SettingRowDivider()
                        SettingSwitchRow(title = "Syntax Highlighting", icon = Icons.Default.Code, checked = s.editorSyntaxHighlighting, onCheckedChange = viewModel::setEditorSyntaxHighlighting)
                        SettingRowDivider()
                        SettingSwitchRow(title = "Bracket Matching", icon = Icons.Default.DataObject, checked = s.editorBracketMatching, onCheckedChange = viewModel::setEditorBracketMatching)
                        SettingRowDivider()
                        SettingSwitchRow(title = "Highlight Current Line", icon = Icons.Default.Highlight, checked = s.editorHighlightCurrentLine, onCheckedChange = viewModel::setEditorHighlightCurrentLine)
                        SettingRowDivider()
                        SettingSwitchRow(title = "Minimap", icon = Icons.Default.Map, checked = s.editorMinimap, onCheckedChange = viewModel::setEditorMinimap)
                    }
                }
            }

            item {
                SectionHeader("Behavior")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column {
                        SettingSwitchRow(title = "Auto Indentation", icon = Icons.Default.FormatIndentIncrease, checked = s.editorAutoIndent, onCheckedChange = viewModel::setEditorAutoIndent)
                        SettingRowDivider()
                        SettingSwitchRow(title = "Auto Save", icon = Icons.Default.Save, checked = s.editorAutoSave, onCheckedChange = viewModel::setEditorAutoSave)
                    }
                }
            }

            item {
                SectionHeader("Indentation")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column {
                        SettingSwitchRow(title = "Use Spaces", supportingText = "Insert spaces instead of tabs", icon = Icons.Default.SpaceBar, checked = s.editorUseSpaces, onCheckedChange = viewModel::setEditorUseSpaces)
                        SettingRowDivider()
                        Column(modifier = Modifier.padding(LocalSpacing.current.md)) {
                            Text("Tab Size: ${s.editorTabSize}", style = MaterialTheme.typography.bodyMedium)
                            Slider(
                                value = s.editorTabSize.toFloat(),
                                onValueChange = { viewModel.setEditorTabSize(it.toInt()) },
                                valueRange = 1f..8f,
                                steps = 6
                            )
                        }
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
