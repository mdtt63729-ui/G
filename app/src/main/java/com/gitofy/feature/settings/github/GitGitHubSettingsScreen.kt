package com.gitofy.feature.settings.github

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gitofy.core.designsystem.components.GITOFYCard
import com.gitofy.core.designsystem.components.GITOFYTopAppBar
import com.gitofy.core.designsystem.components.SettingRow
import com.gitofy.core.designsystem.components.SettingRowDivider
import com.gitofy.core.designsystem.components.SettingSwitchRow
import com.gitofy.core.designsystem.theme.LocalSpacing
import com.gitofy.core.security.PermissionPreflight
import com.gitofy.feature.settings.SettingsViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitGitHubSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    githubViewModel: GitHubSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val githubState by githubViewModel.uiState.collectAsStateWithLifecycle()
    val s = uiState.appSettings
    val context = LocalContext.current
    var showBranchDialog by remember { mutableStateOf(false) }
    var branchInput by remember(s.gitDefaultBranch) { mutableStateOf(s.gitDefaultBranch) }

    fun openGithub(path: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com$path")))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            GITOFYTopAppBar(title = "Git & GitHub", onBack = onBack)
        }
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    githubState.user?.name?.takeIf { it.isNotBlank() } ?: githubState.user?.login ?: uiState.userLogin ?: "Not signed in",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    githubState.user?.login?.let { "@$it" } ?: if (uiState.hasCredentials) "Token stored securely" else "No GitHub credentials",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (githubState.isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (githubState.isConnected) "Connected to GitHub" else "Not connected",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (githubState.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        githubState.error?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = githubViewModel::refresh, enabled = !githubState.isLoading) {
                                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Refresh")
                            }
                            OutlinedButton(onClick = { openGithub("/settings/profile") }) {
                                Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("GitHub Profile")
                            }
                        }
                    }
                }
            }

            item {
                SectionHeader("Account Details")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column {
                        DetailRow("Name", githubState.user?.name ?: "Not set")
                        SettingRowDivider()
                        DetailRow("Email", githubState.user?.email ?: "Private / unavailable")
                        SettingRowDivider()
                        DetailRow("Public repositories", githubState.user?.publicRepos?.toString() ?: "—")
                        SettingRowDivider()
                        DetailRow("Followers", githubState.user?.followers?.toString() ?: "—")
                        SettingRowDivider()
                        DetailRow("Following", githubState.user?.following?.toString() ?: "—")
                    }
                }
            }

            item {
                SectionHeader("Permissions & Access")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column {
                        if (githubState.permissions.isEmpty()) {
                            SettingRow(title = "Permission status", supportingText = "Refresh to inspect this token", icon = Icons.Default.Security)
                        } else {
                            githubState.permissions.forEachIndexed { index, permission ->
                                PermissionRow(permission)
                                if (index != githubState.permissions.lastIndex) SettingRowDivider()
                            }
                        }
                    }
                }
            }

            item {
                SectionHeader("Repository Defaults")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column {
                        SettingRow(
                            title = "Default Branch",
                            supportingText = s.gitDefaultBranch,
                            icon = Icons.Default.AccountTree,
                            onClick = { branchInput = s.gitDefaultBranch; showBranchDialog = true }
                        )
                        SettingRowDivider()
                        SettingSwitchRow(
                            title = "Use Repository Default Branch",
                            supportingText = "Respect the target repository's default branch when available",
                            icon = Icons.Default.Sync,
                            checked = s.gitUseRepositoryDefaultBranch,
                            onCheckedChange = viewModel::setGitUseRepositoryDefaultBranch
                        )
                        SettingRowDivider()
                        SettingSwitchRow(
                            title = "Include Forks",
                            supportingText = "Include forked repositories in repository lists",
                            icon = Icons.Default.CallSplit,
                            checked = s.gitIncludeForks,
                            onCheckedChange = viewModel::setGitIncludeForks
                        )
                    }
                }
            }

            item {
                SectionHeader("Git Operations")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column {
                        SettingSwitchRow(
                            title = "Confirm Destructive Operations",
                            supportingText = "Ask before delete, force-push, branch removal and other destructive actions",
                            icon = Icons.Default.Warning,
                            checked = s.gitConfirmDestructive,
                            onCheckedChange = viewModel::setGitConfirmDestructive
                        )
                        SettingRowDivider()
                        SettingSwitchRow(
                            title = "Auto Push After Commit",
                            supportingText = "Push a successful local commit automatically",
                            icon = Icons.Default.CloudUpload,
                            checked = s.gitAutoPush,
                            onCheckedChange = viewModel::setGitAutoPush
                        )
                        SettingRowDivider()
                        SettingSwitchRow(
                            title = "Fetch Before Repository Open",
                            supportingText = "Refresh remote branch state before opening a repository",
                            icon = Icons.Default.CloudDownload,
                            checked = s.gitFetchOnOpen,
                            onCheckedChange = viewModel::setGitFetchOnOpen
                        )
                    }
                }
            }

            item {
                SectionHeader("AI GitHub Changes")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column {
                        SettingSwitchRow(
                            title = "Create Branch for AI Changes",
                            supportingText = "Prefer an isolated branch before AI writes repository changes",
                            icon = Icons.Default.AccountTree,
                            checked = s.gitAiCreateBranch,
                            onCheckedChange = viewModel::setGitAiCreateBranch
                        )
                        SettingRowDivider()
                        SettingSwitchRow(
                            title = "Confirm Before AI Commit",
                            supportingText = "Require confirmation immediately before a remote commit",
                            icon = Icons.Default.EditNote,
                            checked = s.gitAiConfirmCommit,
                            onCheckedChange = viewModel::setGitAiConfirmCommit
                        )
                        SettingRowDivider()
                        SettingSwitchRow(
                            title = "Create Pull Request After AI Changes",
                            supportingText = "Offer a pull request instead of directly merging AI changes",
                            icon = Icons.Default.MergeType,
                            checked = s.gitAiCreatePullRequest,
                            onCheckedChange = viewModel::setGitAiCreatePullRequest
                        )
                        SettingRowDivider()
                        SettingSwitchRow(
                            title = "Confirm Pull Request Merge",
                            supportingText = "Never merge an AI-created pull request without confirmation",
                            icon = Icons.Default.Rule,
                            checked = s.gitConfirmMerge,
                            onCheckedChange = viewModel::setGitConfirmMerge
                        )
                    }
                }
            }

            item {
                SectionHeader("GitHub Actions & Releases")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column {
                        SettingSwitchRow(
                            title = "Allow AI to Trigger Actions",
                            supportingText = "Permit AI workflows to dispatch, rerun or cancel GitHub Actions when requested",
                            icon = Icons.Default.PlayArrow,
                            checked = s.gitAiActionsEnabled,
                            onCheckedChange = viewModel::setGitAiActionsEnabled
                        )
                        SettingRowDivider()
                        SettingSwitchRow(
                            title = "Show Release Operations",
                            supportingText = "Expose release and tag actions in GitHub workflows",
                            icon = Icons.Default.NewReleases,
                            checked = s.gitReleaseOperationsEnabled,
                            onCheckedChange = viewModel::setGitReleaseOperationsEnabled
                        )
                    }
                }
            }

            item {
                SectionHeader("Notifications")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column {
                        SettingSwitchRow(
                            title = "GitHub Notifications",
                            supportingText = "Show GitHub notification threads inside GITOFY Inbox",
                            icon = Icons.Default.Notifications,
                            checked = s.gitNotificationsEnabled,
                            onCheckedChange = viewModel::setGitNotificationsEnabled
                        )
                        SettingRowDivider()
                        SettingSwitchRow(
                            title = "Include Participating Threads",
                            supportingText = "Include notifications where you are participating",
                            icon = Icons.Default.People,
                            checked = s.gitParticipatingNotifications,
                            onCheckedChange = viewModel::setGitParticipatingNotifications
                        )
                    }
                }
            }

            item {
                SectionHeader("Organizations")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    if (githubState.organizations.isEmpty()) {
                        SettingRow(title = "No organizations returned", supportingText = "Refresh the connection to load organization membership", icon = Icons.Default.Business)
                    } else {
                        Column {
                            githubState.organizations.forEachIndexed { index, org ->
                                SettingRow(
                                    title = org.login,
                                    supportingText = org.description ?: "Organization · ${org.publicRepos} public repositories",
                                    icon = Icons.Default.Business
                                )
                                if (index != githubState.organizations.lastIndex) SettingRowDivider()
                            }
                        }
                    }
                }
            }

            item {
                SectionHeader("API & Diagnostics")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column {
                        val rate = githubState.rateLimit
                        DetailRow("Core API", if (rate != null) "${formatNumber(rate.remaining)} remaining / ${formatNumber(rate.limit)}" else "Unavailable")
                        SettingRowDivider()
                        DetailRow("Used", rate?.used?.toString() ?: "—")
                        SettingRowDivider()
                        DetailRow("Reset", rate?.reset?.let { "Unix ${it}" } ?: "—")
                        SettingRowDivider()
                        SettingRow(
                            title = "Open GitHub Settings",
                            supportingText = "Manage settings that GitHub does not expose safely through the app API",
                            icon = Icons.Default.OpenInNew,
                            onClick = { openGithub("/settings") }
                        )
                    }
                }
            }

            item {
                SectionHeader("GitHub Account Management")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column {
                        SettingRow(title = "Profile", supportingText = "Profile, name, bio and public profile settings", icon = Icons.Default.Person, onClick = { openGithub("/settings/profile") })
                        SettingRowDivider()
                        SettingRow(title = "Account", supportingText = "Account preferences and account management", icon = Icons.Default.ManageAccounts, onClick = { openGithub("/settings/account") })
                        SettingRowDivider()
                        SettingRow(title = "Password & Authentication", supportingText = "Password, passkeys and two-factor authentication", icon = Icons.Default.Lock, onClick = { openGithub("/settings/security") })
                        SettingRowDivider()
                        SettingRow(title = "SSH & GPG Keys", supportingText = "Manage SSH keys and signing keys", icon = Icons.Default.Key, onClick = { openGithub("/settings/keys") })
                        SettingRowDivider()
                        SettingRow(title = "Applications", supportingText = "OAuth apps, GitHub Apps and authorizations", icon = Icons.Default.Apps, onClick = { openGithub("/settings/applications") })
                        SettingRowDivider()
                        SettingRow(title = "Sessions", supportingText = "Review active GitHub sessions", icon = Icons.Default.Devices, onClick = { openGithub("/settings/sessions") })
                        SettingRowDivider()
                        SettingRow(title = "Notifications", supportingText = "GitHub email and web notification preferences", icon = Icons.Default.Notifications, onClick = { openGithub("/settings/notifications") })
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
                OutlinedTextField(
                    value = branchInput,
                    onValueChange = { branchInput = it },
                    label = { Text("Branch name") },
                    singleLine = true,
                    isError = branchInput.trim().isBlank() || branchInput.any { it.isWhitespace() }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = branchInput.trim().isNotBlank() && branchInput.none { it.isWhitespace() },
                    onClick = {
                        viewModel.setGitDefaultBranch(branchInput.trim())
                        showBranchDialog = false
                    }
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showBranchDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PermissionRow(permission: PermissionPreflight.PermissionResult) {
    val (icon, color) = when (permission.status) {
        PermissionPreflight.PermissionStatus.GRANTED -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
        PermissionPreflight.PermissionStatus.MISSING -> Icons.Default.Error to MaterialTheme.colorScheme.error
        PermissionPreflight.PermissionStatus.UNKNOWN -> Icons.Default.HelpOutline to MaterialTheme.colorScheme.onSurfaceVariant
    }
    SettingRow(
        title = permission.displayName,
        supportingText = permission.requiredFor,
        icon = icon,
        trailing = { Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp)) }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.md, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun formatNumber(value: Int): String = NumberFormat.getIntegerInstance(Locale.US).format(value)

@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = LocalSpacing.current.lg, vertical = 6.dp)
    )
}
