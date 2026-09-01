package com.gitofy.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import com.gitofy.core.settings.AppSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

/**
 * Notification Architecture — PRD v3.0 Section 111.
 * Notifications must use appropriate Android notification channels.
 *
 * Channels:
 * - Operations (uploads, repo creation)
 * - Workflows (completion, failure)
 * - Downloads (artifact downloads)
 *
 * Users must be able to disable non-critical notifications.
 */
@Singleton
class NotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: AppSettingsRepository
) {
    @Volatile private var downloadsEnabled = true
    @Volatile private var workflowsEnabled = true
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        const val CHANNEL_OPERATIONS = "channel_operations"
        const val CHANNEL_WORKFLOWS = "channel_workflows"
        const val CHANNEL_AI_AGENT = "channel_ai_agent"
        const val CHANNEL_DOWNLOADS = "channel_downloads"

        const val NOTIF_UPLOAD_COMPLETE = 1001
        const val NOTIF_UPLOAD_FAILED = 1002
        const val NOTIF_UPDATE_COMPLETE = 1003
        const val NOTIF_UPDATE_FAILED = 1004
        const val NOTIF_WORKFLOW_COMPLETE = 2001
        const val NOTIF_WORKFLOW_FAILED = 2002
        const val NOTIF_DOWNLOAD_COMPLETE = 3001
        const val NOTIF_DOWNLOAD_FAILED = 3002

        private const val NOTIFICATION_ID_MIN = 10_000
    }

    init {
        createChannels()
        scope.launch {
            settingsRepository.settings.collect {
                downloadsEnabled = it.notifyDownloads
                workflowsEnabled = it.buildNotifications && (it.notifyBuildCompleted || it.notifyBuildFailed)
            }
        }
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_OPERATIONS,
                    "Operations",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Repository upload and creation notifications"
                }
            )

            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_WORKFLOWS,
                    "Workflows",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "GitHub Actions workflow completion and failure"
                }
            )

            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_AI_AGENT,
                    "AI Agent",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Gito AI and agent activity"
                }
            )

            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_DOWNLOADS,
                    "Downloads",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Artifact download progress and completion"
                }
            )
        }
    }

    fun showUploadComplete(repoName: String, eventKey: String = repoName) {
        showNotification(
            channelId = CHANNEL_OPERATIONS,
            id = stableNotificationId("upload_complete:$eventKey"),
            title = "Upload Complete",
            text = "Repository '$repoName' uploaded successfully"
        )
    }

    fun showUploadFailed(repoName: String, error: String, eventKey: String = repoName) {
        showNotification(
            channelId = CHANNEL_OPERATIONS,
            id = stableNotificationId("upload_failed:$eventKey"),
            title = "Upload Failed",
            text = "Failed to upload '$repoName': $error"
        )
    }

    fun showUpdateComplete(repoName: String, eventKey: String = repoName) {
        showNotification(
            channelId = CHANNEL_OPERATIONS,
            id = stableNotificationId("update_complete:$eventKey"),
            title = "Repository Updated",
            text = "Repository '$repoName' was updated successfully"
        )
    }

    fun showUpdateFailed(repoName: String, error: String, eventKey: String = repoName) {
        showNotification(
            channelId = CHANNEL_OPERATIONS,
            id = stableNotificationId("update_failed:$eventKey"),
            title = "Repository Update Failed",
            text = "Failed to update '$repoName': $error"
        )
    }

    fun showWorkflowComplete(workflowName: String, eventKey: String = workflowName) {
        if (!workflowsEnabled) return
        showNotification(
            channelId = CHANNEL_WORKFLOWS,
            id = stableNotificationId("workflow_complete:$eventKey"),
            title = "Workflow Completed",
            text = "Workflow '$workflowName' completed successfully"
        )
    }

    fun showWorkflowFailed(workflowName: String, eventKey: String = workflowName) {
        if (!workflowsEnabled) return
        showNotification(
            channelId = CHANNEL_WORKFLOWS,
            id = stableNotificationId("workflow_failed:$eventKey"),
            title = "Workflow Failed",
            text = "Workflow '$workflowName' failed. Tap to view logs."
        )
    }

    fun startDownload(artifactName: String): Int {
        val id = stableNotificationId("download:$artifactName")
        if (!downloadsEnabled) return id
        val compat = NotificationManagerCompat.from(context)
        if (!compat.areNotificationsEnabled()) return id
        val notification = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading $artifactName")
            .setContentText("Starting download…")
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, 0, true)
            .build()
        runCatching { compat.notify(id, notification) }
        return id
    }

    fun updateDownloadProgress(id: Int, artifactName: String, downloaded: Long, total: Long) {
        if (!downloadsEnabled) return
        val compat = NotificationManagerCompat.from(context)
        if (!compat.areNotificationsEnabled()) return
        val percent = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else 0
        val text = if (total > 0) "${formatBytes(downloaded)} / ${formatBytes(total)} • $percent%"
        else "${formatBytes(downloaded)} downloaded"
        val builder = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading $artifactName")
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
        if (total > 0) builder.setProgress(100, percent, false) else builder.setProgress(100, 0, true)
        runCatching { compat.notify(id, builder.build()) }
    }

    fun showDownloadComplete(artifactName: String, filePath: String? = null) {
        if (!downloadsEnabled) return
        val id = stableNotificationId("download:$artifactName")
        val compat = NotificationManagerCompat.from(context)
        if (!compat.areNotificationsEnabled()) return
        val builder = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download Complete")
            .setContentText("$artifactName downloaded")
            .setAutoCancel(true)
        filePath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                val uri: Uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                val mime = when {
                    file.name.endsWith(".apk", true) -> "application/vnd.android.package-archive"
                    file.name.endsWith(".zip", true) -> "application/zip"
                    else -> "application/octet-stream"
                }
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val pending = android.app.PendingIntent.getActivity(
                    context, id, intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) android.app.PendingIntent.FLAG_IMMUTABLE else 0)
                )
                builder.setContentIntent(pending)
            }
        }
        runCatching { compat.notify(id, builder.build()) }
    }

    fun showDownloadFailed(artifactName: String, error: String) {
        if (!downloadsEnabled) return
        showNotification(
            channelId = CHANNEL_DOWNLOADS,
            id = stableNotificationId("download_failed:$artifactName"),
            title = "Download Failed",
            text = "Failed to download '$artifactName': $error"
        )
    }

    /** Compatibility entry point used by NotificationHelper. */
    fun showTypedNotification(
        channelId: String,
        eventKey: String,
        title: String,
        text: String
    ) {
        showNotification(
            channelId = channelId,
            id = stableNotificationId(eventKey),
            title = title,
            text = text
        )
    }

    private fun showNotification(
        channelId: String,
        id: Int,
        title: String,
        text: String
    ) {
        val compat = NotificationManagerCompat.from(context)
        if (!compat.areNotificationsEnabled()) return

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()

        try {
            compat.notify(id, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS permission not granted.
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(java.util.Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(java.util.Locale.US, "%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format(java.util.Locale.US, "%.2f GB", gb)
    }

    private fun stableNotificationId(key: String): Int {
        val hash = key.hashCode() and 0x7fffffff
        return NOTIFICATION_ID_MIN + (hash % 1_000_000)
    }
}
