package com.gitofy.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.gitofy.core.designsystem.components.GITOFYButton
import com.gitofy.core.designsystem.components.GITOFYButtonType
import com.gitofy.core.designsystem.components.GITOFYCard
import com.gitofy.core.designsystem.components.SectionHeader
import com.gitofy.core.designsystem.theme.LocalSpacing
import com.gitofy.core.designsystem.motion.GITOFYStaggeredVisibility

/**
 * PRD §2 — Premium category-based Settings screen.
 *
 * Shows premium category cards (not individual settings).  Each category
 * opens its own dedicated settings page via [onNavigateToCategory].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onSignOut: () -> Unit,
    onNavigateToCategory: (String) -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSignOutDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = LocalSpacing.current.lg, vertical = LocalSpacing.current.sm)
            ) {
                Text(
                    "Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Account section
            item {
                SectionHeader("Account")
            }
            item {
                GITOFYCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = LocalSpacing.current.lg)
                ) {
                    Row(
                        modifier = Modifier.padding(LocalSpacing.current.lg),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!uiState.userAvatar.isNullOrEmpty()) {
                            AsyncImage(
                                model = uiState.userAvatar,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
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
                Box(modifier = Modifier.padding(horizontal = LocalSpacing.current.lg, vertical = LocalSpacing.current.sm)) {
                    GITOFYButton(
                        text = "Sign Out",
                        onClick = { showSignOutDialog = true },
                        type = GITOFYButtonType.Outlined,
                        icon = Icons.AutoMirrored.Filled.Logout,
                        fullWidth = true
                    )
                }
            }

            // PRD §2 — Premium category cards
            item {
                Spacer(modifier = Modifier.height(LocalSpacing.current.md))
                SectionHeader("Settings")
            }

            val categories = SettingsCategory.entries
            items(categories.size) { index ->
                val category = categories[index]
                GITOFYStaggeredVisibility(index = index) {
                    SettingsCategoryCard(
                        category = category,
                        onClick = { onNavigateToCategory(category.route) }
                    )
                }
            }
        }
    }

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

/**
 * PRD §2 — The 12 settings categories (Terminal removed per PRD §2).
 */
enum class SettingsCategory(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: String
) {
    APPEARANCE(
        "Appearance", "Theme, colors, animations and display",
        Icons.Default.Palette, "settings/appearance"
    ),
    API_PROVIDERS(
        "API Providers", "Manage Gemini, OpenRouter and other AI providers",
        Icons.Default.Cloud, "settings/api_providers"
    ),
    MODELS(
        "Models", "Default models and model behavior",
        Icons.Default.ModelTraining, "settings/models"
    ),
    AI_AGENT(
        "AI & Agent", "AI coding and agent preferences",
        Icons.Default.SmartToy, "settings/agent"
    ),
    EDITOR(
        "Editor", "Code editor preferences",
        Icons.Default.Code, "settings/editor"
    ),
    WORKSPACE(
        "Workspace & Project", "Project and workspace behavior",
        Icons.Default.Folder, "settings/workspace"
    ),
    GIT_GITHUB(
        "Git & GitHub", "Git and GitHub configuration",
        Icons.Default.Code, "settings/github"
    ),
    BUILD_RUN(
        "Build & Run", "Build, APK and execution preferences",
        Icons.Default.Build, "settings/build"
    ),
    NOTIFICATIONS(
        "Notifications", "Application notification preferences",
        Icons.Default.Notifications, "settings/notifications"
    ),
    PRIVACY_SECURITY(
        "Privacy & Security", "Keys, data and privacy controls",
        Icons.Default.Security, "settings/privacy"
    ),
    ADVANCED(
        "Advanced", "Developer and experimental options",
        Icons.Default.Tune, "settings/advanced"
    ),
    ABOUT(
        "About", "Version, licenses and application information",
        Icons.Default.Info, "settings/about"
    )
}

@Composable
private fun SettingsCategoryCard(
    category: SettingsCategory,
    onClick: () -> Unit
) {
    GITOFYCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LocalSpacing.current.lg, vertical = 2.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LocalSpacing.current.md, vertical = LocalSpacing.current.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        category.icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.width(LocalSpacing.current.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    category.title,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    category.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
