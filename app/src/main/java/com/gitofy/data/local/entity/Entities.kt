package com.gitofy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val login: String = "",
    val avatarUrl: String = "",
    val name: String? = null,
    val bio: String? = null,
    val publicRepos: Int = 0,
    val followers: Int = 0,
    val following: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "repositories",
    indices = [Index("ownerLogin"), Index("updatedAt")]
)
data class RepositoryEntity(
    @PrimaryKey val id: Long = 0,
    val name: String = "",
    val fullName: String = "",
    val ownerLogin: String = "",
    val ownerAvatar: String = "",
    val isPrivate: Boolean = false,
    val description: String? = null,
    val htmlUrl: String = "",
    val defaultBranch: String = "main",
    val stargazersCount: Int = 0,
    val forksCount: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "branches")
data class BranchEntity(
    @PrimaryKey val name: String = "",
    val repoId: Long = 0,
    val commitSha: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "commits")
data class CommitEntity(
    @PrimaryKey val sha: String = "",
    val repoId: Long = 0,
    val message: String = "",
    val authorName: String = "",
    val authorAvatar: String = "",
    val date: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "workflows")
data class WorkflowEntity(
    @PrimaryKey val id: Long = 0,
    val repoId: Long = 0,
    val name: String = "",
    val path: String = "",
    val state: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "workflow_runs",
    indices = [Index("repoId"), Index("status")]
)
data class WorkflowRunEntity(
    @PrimaryKey val id: Long = 0,
    val repoId: Long = 0,
    val name: String = "",
    val displayTitle: String = "",
    val headBranch: String = "",
    val headSha: String = "",
    val status: String = "",
    val conclusion: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
    val actorLogin: String = "",
    val updatedAtTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "jobs")
data class JobEntity(
    @PrimaryKey val id: Long = 0,
    val runId: Long = 0,
    val name: String = "",
    val status: String = "",
    val conclusion: String? = null,
    val startedAt: String = "",
    val completedAt: String = "",
    val htmlUrl: String = ""
)

@Entity(tableName = "artifacts")
data class ArtifactEntity(
    @PrimaryKey val id: Long = 0,
    val runId: Long = 0,
    val name: String = "",
    val sizeInBytes: Long = 0,
    val archiveDownloadUrl: String = "",
    val expired: Boolean = false,
    val createdAt: String = "",
    val expiresAt: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "operations")
data class OperationEntity(
    @PrimaryKey val id: String = "",
    val type: String = "",
    val status: String = "",
    val progress: Float = 0f,
    val currentStage: String = "",
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val key: String = "",
    val lastSyncTime: Long = 0,
    val etag: String? = null
)
