package com.gitofy.feature.inbox

import androidx.compose.foundation.background
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gitofy.core.designsystem.components.GITOFYCard
import com.gitofy.core.designsystem.theme.LocalSpacing
import com.gitofy.data.remote.dto.GitHubNotification

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    viewModel: InboxViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedNotification by remember { mutableStateOf<GitHubNotification?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inbox", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { viewModel.markAllAsRead() }) {
                        Icon(Icons.Outlined.MarkEmailRead, contentDescription = "Mark all as read")
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Done, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = LocalSpacing.current.lg, vertical = LocalSpacing.current.xs),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InboxFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.activeFilter == filter,
                        onClick = { viewModel.loadNotifications(filter) },
                        label = {
                            Text(filter.name.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    )
                }
            }

            when {
                !state.tokenSupportsNotifications -> {
                    InboxUnavailableState()
                }
                state.isLoading && state.notifications.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.error != null && state.notifications.isEmpty() -> {
                    ErrorState(message = state.error!!) { viewModel.refresh() }
                }
                state.notifications.isEmpty() -> {
                    EmptyInboxState()
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = LocalSpacing.current.lg,
                            vertical = LocalSpacing.current.sm
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.filteredNotifications, key = { it.id }) { notification ->
                            InboxItemCard(
                                notification = notification,
                                onClick = {
                                    selectedNotification = notification
                                    if (notification.unread) viewModel.markAsRead(notification.id)
                                },
                                onMarkRead = { viewModel.markAsRead(notification.id) },
                                onMarkDone = { viewModel.markAsDone(notification.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    selectedNotification?.let { notification ->
        NotificationDetailDialog(
            notification = notification,
            onDismiss = { selectedNotification = null },
            onMarkDone = {
                viewModel.markAsDone(notification.id)
                selectedNotification = null
            }
        )
    }
}

@Composable
private fun InboxItemCard(
    notification: GitHubNotification,
    onClick: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkDone: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    GITOFYCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LocalSpacing.current.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar/icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notification.repository.fullName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = notification.subject.title ?: "Untitled",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (notification.unread) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                notification.reason?.let { reason ->
                    Text(
                        text = reason.replace("_", " ").replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // Unread indicator
            if (notification.unread) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            // Overflow menu
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Mark as read") },
                        onClick = {
                            showMenu = false
                            onMarkRead()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Mark as done") },
                        onClick = {
                            showMenu = false
                            onMarkDone()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationDetailDialog(
    notification: GitHubNotification,
    onDismiss: () -> Unit,
    onMarkDone: () -> Unit
) {
    val context = LocalContext.current
    val githubUrl = notification.subject.url ?: notification.subject.latestCommentUrl

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(notification.subject.title ?: "Notification") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(notification.repository.fullName, style = MaterialTheme.typography.labelMedium)
                notification.reason?.let {
                    Text(
                        it.replace("_", " ").replaceFirstChar { c -> c.uppercase() },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                notification.updatedAt?.let {
                    Text(
                        it.replace("T", " ").removeSuffix("Z"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (githubUrl == null) {
                    Text(
                        "No direct GitHub URL was provided by the notification thread.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            if (githubUrl != null) {
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl)))
                }) { Text("Open on GitHub") }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onMarkDone) { Text("Done") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}

@Composable
private fun EmptyInboxState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Notifications,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "No notifications",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "You're all caught up",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InboxUnavailableState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.Notifications,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Inbox unavailable for this token",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Your GitHub token may not have the 'notifications' scope. Visit GitHub Settings to manage your token.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}
