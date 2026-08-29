package com.gitofy.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * PRD §86: GitHub notification DTOs for the Inbox feature.
 * GitHub notifications API returns threads with subject, repository, and reason.
 */
@Serializable
data class GitHubNotification(
    val id: String,
    @SerialName("unread") val unread: Boolean,
    @SerialName("reason") val reason: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("last_read_at") val lastReadAt: String? = null,
    val subject: NotificationSubject,
    val repository: NotificationRepository
)

@Serializable
data class NotificationSubject(
    val title: String? = null,
    val type: String? = null,
    val url: String? = null,
    @SerialName("latest_comment_url") val latestCommentUrl: String? = null
)

@Serializable
data class NotificationRepository(
    val id: Long,
    val name: String,
    @SerialName("full_name") val fullName: String,
    val owner: NotificationRepositoryOwner,
    val private: Boolean
)

@Serializable
data class NotificationRepositoryOwner(
    val login: String,
    @SerialName("avatar_url") val avatarUrl: String? = null
)

@Serializable
data class ThreadSubscription(
    val subscribed: Boolean,
    val ignored: Boolean,
    val reason: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val url: String? = null
)

@Serializable
data class SetSubscriptionRequest(
    val subscribed: Boolean,
    val ignored: Boolean
)
