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
        const val CHANNEL_OPERATIONS = "operations"
        const val CHANNEL_WORKFLOWS = "workflows"
        const val CHANNEL_DOWNLOADS = "downloads"

        const val NOTIF_UPLOAD_COMPLETE = 1001
        const val NOTIF_UPLOAD_FAILED = 1002
        const val NOTIF_WORKFLOW_COMPLETE = 2001
        const val NOTIF_WORKFLOW_FAILED = 2002
        const val NOTIF_DOWNLOAD_COMPLETE = 3001
        const val NOTIF_DOWNLOAD_FAILED = 3002
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
                    CHANNEL_DOWNLOADS,
                    "Downloads",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Artifact download progress and completion"
                }
            )
        }
    }

    fun showUploadComplete(repoName: String) {
        showNotification(
            channelId = CHANNEL_OPERATIONS,
            id = NOTIF_UPLOAD_COMPLETE,
            title = "Upload Complete",
            text = "Repository '$repoName' uploaded successfully"
        )
    }

    fun showUploadFailed(repoName: String, error: String) {
        showNotification(
            channelId = CHANNEL_OPERATIONS,
            id = NOTIF_UPLOAD_FAILED,
            title = "Upload Failed",
            text = "Failed to upload '$repoName': $error"
        )
    }

    fun showWorkflowComplete(workflowName: String) {
        showNotification(
            channelId = CHANNEL_WORKFLOWS,
            id = NOTIF_WORKFLOW_COMPLETE,
            title = "Workflow Completed",
            text = "Workflow '$workflowName' completed successfully"
        )
    }

    fun showWorkflowFailed(workflowName: String) {
        showNotification(
            channelId = CHANNEL_WORKFLOWS,
            id = NOTIF_WORKFLOW_FAILED,
            title = "Workflow Failed",
            text = "Workflow '$workflowName' failed. Tap to view logs."
        )
    }

    fun showDownloadComplete(artifactName: String) {
        showNotification(
            channelId = CHANNEL_DOWNLOADS,
            id = NOTIF_DOWNLOAD_COMPLETE,
            title = "Download Complete",
            text = "Artifact '$artifactName' downloaded successfully"
        )
    }

    fun showDownloadFailed(artifactName: String, error: String) {
        showNotification(
            channelId = CHANNEL_DOWNLOADS,
            id = NOTIF_DOWNLOAD_FAILED,
            title = "Download Failed",
            text = "Failed to download '$artifactName': $error"
        )
    }

    private fun showNotification(
        channelId: String,
        id: Int,
        title: String,
        text: String
    ) {
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS permission not granted
        }
    }
}
