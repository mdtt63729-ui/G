package com.gitofy.core.notification

import android.app.NotificationManager as AndroidNotificationManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backwards-compatible notification facade.
 *
 * NotificationManager owns the actual Android channel registration and posting
 * implementation. This class only preserves the enum-based API used by older
 * ViewModels so there is a single notification implementation in the app.
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationManager: NotificationManager
) {
    enum class NotificationChannel(
        val id: String,
        val displayName: String,
        val description: String,
        val importance: Int
    ) {
        OPERATIONS(
            NotificationManager.CHANNEL_OPERATIONS,
            "Operations",
            "Notifications for repository uploads and file operations.",
            AndroidNotificationManager.IMPORTANCE_DEFAULT
        ),
        WORKFLOWS(
            NotificationManager.CHANNEL_WORKFLOWS,
            "Workflows",
            "Notifications for GitHub Actions workflow runs.",
            AndroidNotificationManager.IMPORTANCE_DEFAULT
        ),
        AI_AGENT(
            NotificationManager.CHANNEL_AI_AGENT,
            "AI Agent",
            "Notifications for Gito activity.",
            AndroidNotificationManager.IMPORTANCE_LOW
        ),
        DOWNLOADS(
            NotificationManager.CHANNEL_DOWNLOADS,
            "Downloads",
            "Notifications for artifact downloads.",
            AndroidNotificationManager.IMPORTANCE_LOW
        )
    }

    enum class NotificationType(val defaultChannel: NotificationChannel) {
        upload_started(NotificationChannel.OPERATIONS),
        upload_completed(NotificationChannel.OPERATIONS),
        upload_failed(NotificationChannel.OPERATIONS),
        workflow_started(NotificationChannel.WORKFLOWS),
        workflow_completed(NotificationChannel.WORKFLOWS),
        workflow_failed(NotificationChannel.WORKFLOWS),
        ai_started(NotificationChannel.AI_AGENT),
        ai_completed(NotificationChannel.AI_AGENT),
        ai_stopped(NotificationChannel.AI_AGENT),
        artifact_ready(NotificationChannel.DOWNLOADS),
        artifact_download_complete(NotificationChannel.DOWNLOADS)
    }

    fun createChannels() {
        // The canonical manager registers all channels in its init block.
        // Keep this method for source compatibility with Application startup.
    }

    fun showNotification(
        channel: NotificationChannel,
        type: NotificationType,
        title: String,
        message: String
    ) {
        notificationManager.showTypedNotification(
            channelId = channel.id,
            eventKey = "${type.name}:${title}:${message}",
            title = title,
            text = message
        )
    }
}
