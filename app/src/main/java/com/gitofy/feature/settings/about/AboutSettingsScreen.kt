package com.gitofy.feature.settings.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gitofy.core.designsystem.components.GITOFYCard
import com.gitofy.core.designsystem.components.GITOFYTopAppBar
import com.gitofy.core.designsystem.components.SettingRow
import com.gitofy.core.designsystem.components.SettingRowDivider
import com.gitofy.core.designsystem.theme.LocalSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSettingsScreen(
    onBack: () -> Unit
) {
    var document by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { GITOFYTopAppBar(title = "About", onBack = onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item {
                SectionHeader("Application")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column(modifier = Modifier.padding(LocalSpacing.current.lg)) {
                        Text("GITOFY", style = MaterialTheme.typography.titleMedium)
                        Text("Version 4.1.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(LocalSpacing.current.sm))
                        Text(
                            "GITOFY is a native Android app for managing GitHub repositories and CI/CD workflows from your phone, with integrated AI assistance.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                SectionHeader("Links")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column {
                        SettingRow(title = "Privacy Policy", icon = Icons.Default.PrivacyTip, onClick = { document = "privacy" })
                        SettingRowDivider()
                        SettingRow(title = "Terms of Service", icon = Icons.Default.Description, onClick = { document = "terms" })
                        SettingRowDivider()
                        SettingRow(title = "Open Source Licenses", icon = Icons.Default.Code, onClick = { document = "licenses" })
                    }
                }
            }
        }
    }
    document?.let { selected ->
        val (title, body) = when (selected) {
            "privacy" -> "Privacy Policy" to "GITOFY stores credentials using Android secure storage and does not intentionally sell personal data. Network requests are made only to services required by enabled features."
            "terms" -> "Terms of Service" to "Use GITOFY only with repositories and credentials you are authorized to access. You are responsible for actions performed against connected GitHub accounts and repositories."
            else -> "Open Source Licenses" to "GITOFY uses open-source Android, Kotlin, Jetpack Compose, Retrofit, OkHttp, Coil, JGit, Hilt, Room and related libraries. Their respective licenses remain with their authors."
        }
        AlertDialog(
            onDismissRequest = { document = null },
            title = { Text(title) },
            text = { Text(body) },
            confirmButton = {
                TextButton(onClick = { document = null }) { Text("Close") }
            }
        )
    }

}

@Composable
private fun SectionHeader(title: String) {
    Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = LocalSpacing.current.lg, vertical = 4.dp))
}
