package com.gitofy.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.gitofy.core.designsystem.components.GITOFYButton
import com.gitofy.core.designsystem.components.GITOFYButtonType
import com.gitofy.core.designsystem.components.GITOFYTopAppBar
import com.gitofy.core.designsystem.theme.LocalSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onSignOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSignOutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { GITOFYTopAppBar(title = "Settings") }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(LocalSpacing.current.lg),
            verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.md)
        ) {
            // Account section
            item {
                Text("Account", style = MaterialTheme.typography.titleMedium)
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large
                ) {
                    Row(
                        modifier = Modifier.padding(LocalSpacing.current.lg),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!uiState.userAvatar.isNullOrEmpty()) {
                            AsyncImage(
                                model = uiState.userAvatar,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.width(LocalSpacing.current.md))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                uiState.userLogin ?: "Not signed in",
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "GitHub Account",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item {
                GITOFYButton(
                    text = "Sign Out",
                    onClick = { showSignOutDialog = true },
                    type = GITOFYButtonType.Outlined,
                    icon = Icons.Default.Logout,
                    fullWidth = true
                )
            }

            // Appearance
            item {
                Spacer(modifier = Modifier.height(LocalSpacing.current.md))
                Text("Appearance", style = MaterialTheme.typography.titleMedium)
            }
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Column {
                        ListItem(
                            headlineContent = { Text("Dynamic Color") },
                            trailingContent = {
                                Switch(
                                    checked = uiState.dynamicColor,
                                    onCheckedChange = viewModel::setDynamicColor
                                )
                            }
                        )
                        HorizontalDivider()
                        ListItem(
                            headlineContent = { Text("Theme") },
                            supportingContent = {
                                Row {
                                    FilterChip(
                                        selected = uiState.themeMode == ThemeMode.LIGHT,
                                        onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) },
                                        label = { Text("Light") }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    FilterChip(
                                        selected = uiState.themeMode == ThemeMode.DARK,
                                        onClick = { viewModel.setThemeMode(ThemeMode.DARK) },
                                        label = { Text("Dark") }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    FilterChip(
                                        selected = uiState.themeMode == ThemeMode.SYSTEM,
                                        onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
                                        label = { Text("System") }
                                    )
                                }
                            }
                        )
                    }
                }
            }

            // Workflow
            item {
                Spacer(modifier = Modifier.height(LocalSpacing.current.md))
                Text("Workflow", style = MaterialTheme.typography.titleMedium)
            }
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    ListItem(
                        headlineContent = { Text("Background Sync") },
                        trailingContent = {
                            Switch(
                                checked = uiState.backgroundSync,
                                onCheckedChange = viewModel::setBackgroundSync
                            )
                        }
                    )
                }
            }

            // Security
            item {
                Spacer(modifier = Modifier.height(LocalSpacing.current.md))
                Text("Security", style = MaterialTheme.typography.titleMedium)
            }
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    ListItem(
                        leadingContent = {
                            Icon(Icons.Default.Security, contentDescription = null)
                        },
                        headlineContent = { Text("Credential Status") },
                        supportingContent = {
                            Text(
                                if (uiState.hasCredentials) "Token stored securely" else "No credentials",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }

            // About
            item {
                Spacer(modifier = Modifier.height(LocalSpacing.current.md))
                Text("About", style = MaterialTheme.typography.titleMedium)
            }
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Column(modifier = Modifier.padding(LocalSpacing.current.lg)) {
                        Text("GITOFY", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Version 2.0.0",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(LocalSpacing.current.sm))
                        Text(
                            "GITOFY is a native Android app for managing GitHub repositories and CI/CD workflows from your phone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Sign out dialog
    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign Out") },
            text = { Text("Are you sure you want to sign out? All local credentials will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.signOut()
                    showSignOutDialog = false
                    onSignOut()
                }) { Text("Sign Out") }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) { Text("Cancel") }
            }
        )
    }
}
