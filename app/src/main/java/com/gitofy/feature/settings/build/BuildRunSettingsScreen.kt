package com.gitofy.feature.settings.build

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gitofy.core.designsystem.components.GITOFYCard
import com.gitofy.core.designsystem.components.GITOFYTopAppBar
import com.gitofy.core.designsystem.components.SettingRow
import com.gitofy.core.designsystem.components.SettingRowDivider
import com.gitofy.core.designsystem.theme.LocalSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildRunSettingsScreen(
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { GITOFYTopAppBar(title = "Build & Run", onBack = onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item {
                SectionHeader("Build Engine")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column {
                        SettingRow(
                            title = "Native libgit2",
                            supportingText = "Used for Git push operations",
                            icon = Icons.Default.Build,
                            onClick = { }
                        )
                        SettingRowDivider()
                        SettingRow(
                            title = "Auto error fixing",
                            supportingText = "AI-assisted build error correction",
                            icon = Icons.Default.Bolt,
                            onClick = { }
                        )
                    }
                }
            }

            item {
                SectionHeader("CI/CD")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column {
                        SettingRow(
                            title = "Background sync",
                            supportingText = "Sync repositories in background",
                            icon = Icons.Default.Cloud,
                            onClick = { }
                        )
                        SettingRowDivider()
                        SettingRow(
                            title = "Max agent iterations",
                            supportingText = "Maximum AI agent loop iterations",
                            icon = Icons.Default.Code,
                            onClick = { }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = LocalSpacing.current.lg, vertical = 4.dp)
    )
}
