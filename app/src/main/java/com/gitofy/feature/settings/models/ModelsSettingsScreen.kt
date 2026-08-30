package com.gitofy.feature.settings.models

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
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
fun ModelsSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val s = uiState.appSettings
    var showModelInput by remember { mutableStateOf(false) }
    var modelInput by remember { mutableStateOf(s.defaultModelId) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { GITOFYTopAppBar(title = "Models", onBack = onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item {
                SectionHeader("Default Provider")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column(modifier = Modifier.padding(LocalSpacing.current.md)) {
                        Text("Provider: ${uiState.providerInstances.find { it.isDefault }?.displayName ?: "Not set"}", style = MaterialTheme.typography.bodyMedium)
                        Text("Configure in API Providers → Set Default", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                SectionHeader("Default Model")
                GITOFYCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg),
                    onClick = { showModelInput = true; modelInput = s.defaultModelId }
                ) {
                    SettingRow(
                        title = "Model ID",
                        supportingText = s.defaultModelId.ifBlank { "Not set" },
                        icon = Icons.Default.ModelTraining
                    )
                }
            }

            item {
                SectionHeader("Temperature")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column(modifier = Modifier.padding(LocalSpacing.current.md)) {
                        Text("Temperature: ${String.format("%.1f", s.modelTemperature)}", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = s.modelTemperature,
                            onValueChange = viewModel::setModelTemperature,
                            valueRange = 0f..2f,
                            steps = 19
                        )
                    }
                }
            }

            item {
                SectionHeader("Top-P")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column(modifier = Modifier.padding(LocalSpacing.current.md)) {
                        Text("Top-P: ${String.format("%.2f", s.modelTopP)}", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = s.modelTopP,
                            onValueChange = viewModel::setModelTopP,
                            valueRange = 0f..1f,
                            steps = 99
                        )
                    }
                }
            }

            item {
                SectionHeader("Max Output Tokens")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column(modifier = Modifier.padding(LocalSpacing.current.md)) {
                        Text("Max Tokens: ${s.modelMaxOutputTokens}", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = s.modelMaxOutputTokens.toFloat(),
                            onValueChange = { viewModel.setModelMaxTokens(it.toInt()) },
                            valueRange = 256f..8192f,
                            steps = 30
                        )
                    }
                }
            }

            item {
                SectionHeader("Context Window")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column(modifier = Modifier.padding(LocalSpacing.current.md)) {
                        Text("Context: ${s.modelContextWindow}", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = s.modelContextWindow.toFloat(),
                            onValueChange = { viewModel.setModelContext(it.toInt()) },
                            valueRange = 4096f..1_000_000f,
                            steps = 50
                        )
                    }
                }
            }
        }
    }

    if (showModelInput) {
        AlertDialog(
            onDismissRequest = { showModelInput = false },
            title = { Text("Custom Model ID") },
            text = {
                OutlinedTextField(
                    value = modelInput,
                    onValueChange = { modelInput = it },
                    label = { Text("Model ID") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setDefaultModel(modelInput.trim())
                    showModelInput = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showModelInput = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = LocalSpacing.current.lg, vertical = 4.dp)
    )
}
