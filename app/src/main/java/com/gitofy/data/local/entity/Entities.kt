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

@Entity(
    tableName = "branches",
    indices = [Index("repoId"), Index(value = ["ownerLogin", "repoName", "name"], unique = true)]
)
data class BranchEntity(
    @PrimaryKey val name: String = "",
    val repoId: Long = 0,
    val ownerLogin: String = "",
    val repoName: String = "",
    val commitSha: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "commits",
    indices = [Index("repoId"), Index(value = ["ownerLogin", "repoName"])]
)
data class CommitEntity(
    @PrimaryKey val sha: String = "",
    val repoId: Long = 0,
    val ownerLogin: String = "",
    val repoName: String = "",
    val message: String = "",
    val authorName: String = "",
    val authorAvatar: String = "",
    val date: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "workflows",
    indices = [Index("repoId"), Index(value = ["ownerLogin", "repoName"])]
)
data class WorkflowEntity(
    @PrimaryKey val id: Long = 0,
    val repoId: Long = 0,
    val ownerLogin: String = "",
    val repoName: String = "",
    val name: String = "",
    val path: String = "",
    val state: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "workflow_runs",
    indices = [Index("repoId"), Index("status"), Index(value = ["ownerLogin", "repoName"])]
)
data class WorkflowRunEntity(
    @PrimaryKey val id: Long = 0,
    val repoId: Long = 0,
    val ownerLogin: String = "",
    val repoName: String = "",
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
    val htmlUrl: String = "",
    // PRD §4: Tab-separated step cache so observeJobs can surface real steps
    val stepsJson: String? = null
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

@Entity(
    tableName = "operations",
    indices = [Index("status")]
)
data class OperationEntity(
    @PrimaryKey val id: String = "",
    val type: String = "",
    val status: String = "",
    val progress: Float = 0f,
    val currentStage: String = "",
    val repositoryId: Long = 0,
    val ownerLogin: String = "",
    val repoName: String = "",
    val branch: String = "",
    val workflowRunId: Long = 0,
    val artifactId: Long = 0,
    val currentFile: String = "",
    val attempt: Int = 0,
    val maxAttempts: Int = 0,
    val aiSessionId: String = "",
    val lastLog: String = "",
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    // PRD PHASE 4: Real progress tracking
    val bytesUploaded: Long = 0L,
    val totalBytes: Long = 0L,
    val filesCompleted: Int = 0,
    val totalFiles: Int = 0,
    val commitSha: String = "",
    val stageStartedAt: Long = 0L,
    val stageCompletedAt: Long = 0L,
    val operationStartedAt: Long = 0L,
    val operationCompletedAt: Long = 0L,
    // PRD PHASE 24: Step tracking as JSON
    val stepHistoryJson: String = ""
)

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val key: String = "",
    val lastSyncTime: Long = 0,
    val etag: String? = null,
    val cachedBody: String? = null
)
