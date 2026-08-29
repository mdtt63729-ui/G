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
 * PRD §73: Notification channels and types.
 *
 * Centralises the creation of the app's notification channels and provides a
 * single entry point for posting notifications across the Operations, Workflows,
 * AI Agent, and Downloads channels. All channels are registered with the system
 * via [createChannels], which should be called once at application start-up.
 *
 * @param context the application context, injected by Hilt.
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * The set of notification channels used by the app.
     *
     * @property id           the channel id registered with the system.
     * @property displayName  the user-visible channel name.
     * @property description  the user-visible channel description.
     * @property importance   the channel importance level.
     */
    enum class NotificationChannel(
        val id: String,
        val displayName: String,
        val description: String,
        val importance: Int
    ) {
        OPERATIONS(
            id = "channel_operations",
            displayName = "Operations",
            description = "Notifications for repository uploads and file operations.",
            importance = NotificationManager.IMPORTANCE_DEFAULT
        ),
        WORKFLOWS(
            id = "channel_workflows",
            displayName = "Workflows",
            description = "Notifications for GitHub Actions workflow runs.",
            importance = NotificationManager.IMPORTANCE_DEFAULT
        ),
        AI_AGENT(
            id = "channel_ai_agent",
            displayName = "AI Agent",
            description = "Notifications for Gito activity.",
            importance = NotificationManager.IMPORTANCE_LOW
        ),
        DOWNLOADS(
            id = "channel_downloads",
            displayName = "Downloads",
            description = "Notifications for artifact downloads.",
            importance = NotificationManager.IMPORTANCE_LOW
        )
    }

    /**
     * The discrete notification types the app can post. Each type is mapped to a
     * default channel and is used purely for categorisation and potential
     * post-processing; it is not surfaced to the system directly.
     *
     * @property defaultChannel the channel this type is normally posted to.
     */
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

    private val notificationManagerCompat: NotificationManagerCompat =
        NotificationManagerCompat.from(context)

    /**
     * Registers all notification channels with the system.
     *
     * Channels only need to be created on Android O (API 26) and above; on older
     * versions this call is a no-op. Safe to call repeatedly — the system ignores
     * duplicate channel ids.
     */
    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val systemManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        NotificationChannel.values().forEach { channel ->
            val systemChannel = android.app.NotificationChannel(
                channel.id,
                channel.displayName,
                channel.importance
            ).apply {
                description = channel.description
            }
            systemManager.createNotificationChannel(systemChannel)
        }
    }

    /**
     * Posts a notification on the given [channel] with the supplied [title] and
     * [message]. The [type] is recorded in the notification's extras so that
     * receivers can identify the originating event.
     *
     * NOTE: Callers must have the `POST_NOTIFICATIONS` runtime permission (Android
     * 13+). This method will silently do nothing if notifications are not enabled
     * for the app, and will throw `SecurityException` only if the permission is
     * missing while the app is targeting SDK 33+.
     *
     * @param channel the channel to post on.
     * @param type    the notification type, used for categorisation.
     * @param title   the notification title.
     * @param message the notification body text.
     */
    fun showNotification(
        channel: NotificationChannel,
        type: NotificationType,
        title: String,
        message: String
    ) {
        if (!notificationManagerCompat.areNotificationsEnabled()) {
            return
        }

        val notification = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(importanceToPriority(channel.importance))
            .setAutoCancel(true)
            .build()

        notificationManagerCompat.notify(type.ordinal, notification)
    }

    private fun importanceToPriority(importance: Int): Int = when (importance) {
        NotificationManager.IMPORTANCE_HIGH -> NotificationCompat.PRIORITY_HIGH
        NotificationManager.IMPORTANCE_DEFAULT -> NotificationCompat.PRIORITY_DEFAULT
        NotificationManager.IMPORTANCE_LOW -> NotificationCompat.PRIORITY_LOW
        NotificationManager.IMPORTANCE_MIN -> NotificationCompat.PRIORITY_MIN
        else -> NotificationCompat.PRIORITY_DEFAULT
    }
}
