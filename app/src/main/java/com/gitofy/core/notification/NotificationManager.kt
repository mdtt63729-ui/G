package com.gitofy.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

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
    @ApplicationContext private val context: Context
) {
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
        showNotification(
            channelId = CHANNEL_WORKFLOWS,
            id = stableNotificationId("workflow_complete:$eventKey"),
            title = "Workflow Completed",
            text = "Workflow '$workflowName' completed successfully"
        )
    }

    fun showWorkflowFailed(workflowName: String, eventKey: String = workflowName) {
        showNotification(
            channelId = CHANNEL_WORKFLOWS,
            id = stableNotificationId("workflow_failed:$eventKey"),
            title = "Workflow Failed",
            text = "Workflow '$workflowName' failed. Tap to view logs."
        )
    }

    fun showDownloadComplete(artifactName: String) {
        showNotification(
            channelId = CHANNEL_DOWNLOADS,
            id = stableNotificationId("download_complete:$artifactName"),
            title = "Download Complete",
            text = "Artifact '$artifactName' downloaded successfully"
        )
    }

    fun showDownloadFailed(artifactName: String, error: String) {
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

    private fun stableNotificationId(key: String): Int {
        val hash = key.hashCode() and 0x7fffffff
        return NOTIFICATION_ID_MIN + (hash % 1_000_000)
    }
}
