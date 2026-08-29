package com.gitofy.domain.model

/**
 * Domain models — UI-agnostic representation of data.
 * PRD 19: Operation state model — sealed/domain state models.
 */

// Auth states — PRD 7.1
sealed class AuthState {
    data object Unknown : AuthState()
    data object Authenticating : AuthState()
    data object Authenticated : AuthState()
    data object Invalid : AuthState()
    data object Expired : AuthState()
    data object Revoked : AuthState()
    data object InsufficientPermission : AuthState()
    data object NetworkError : AuthState()
    data object SignedOut : AuthState()
}

// Operation states — PRD 19
sealed class OperationState {
    data object Idle : OperationState()
    data object Queued : OperationState()
    data object Running : OperationState()
    data class Progress(val progress: Float, val stage: String) : OperationState()
    data object Success : OperationState()
    data class Failed(val error: GitOFYError) : OperationState()
    data object Cancelled : OperationState()
    data object Retrying : OperationState()
}

// Workflow states — PRD 22.2
enum class WorkflowStatus {
    QUEUED, IN_PROGRESS, COMPLETED_SUCCESS, COMPLETED_FAILURE,
    CANCELLED, SKIPPED, TIMED_OUT, UNKNOWN;

    companion object {
        fun fromGitHubStatus(status: String, conclusion: String?): WorkflowStatus {
            return when {
                status == "queued" -> QUEUED
                status == "in_progress" -> IN_PROGRESS
                status == "completed" -> when (conclusion) {
                    "success" -> COMPLETED_SUCCESS
                    "failure" -> COMPLETED_FAILURE
                    "cancelled" -> CANCELLED
                    "skipped" -> SKIPPED
                    "timed_out" -> TIMED_OUT
                    else -> UNKNOWN
                }
                else -> UNKNOWN
            }
        }
    }
}

// Repository lifecycle — PRD 65
enum class RepositoryLifecycle {
    NOT_CREATED, CREATING, CREATED, INITIALIZING, PUSHING, VERIFIED, ACTIVE, FAILED
}

// Git push pipeline stages — PRD 18
enum class GitPushStage {
    VALIDATING, EXTRACTING, CREATING_REPOSITORY, INITIALIZING_GIT,
    CONFIGURING_GIT, STAGING, COMMITTING, CONFIGURING_REMOTE,
    PUSHING, VERIFYING, COMPLETED
}

// Domain models
data class User(
    val login: String,
    val avatarUrl: String,
    val name: String?,
    val bio: String?,
    val publicRepos: Int,
    val followers: Int,
    val following: Int
)

data class RepoSummary(
    val id: Long,
    val name: String,
    val fullName: String,
    val ownerLogin: String,
    val ownerAvatar: String,
    val isPrivate: Boolean,
    val description: String?,
    val htmlUrl: String,
    val defaultBranch: String,
    val stars: Int,
    val forks: Int,
    val updatedAt: String
)

data class RepoDetails(
    val id: Long,
    val name: String,
    val fullName: String,
    val ownerLogin: String,
    val ownerAvatar: String,
    val isPrivate: Boolean,
    val description: String?,
    val htmlUrl: String,
    val defaultBranch: String,
    val stars: Int,
    val forks: Int,
    val openIssues: Int
)

data class BranchInfo(
    val name: String,
    val commitSha: String
)

data class CommitInfo(
    val sha: String,
    val message: String,
    val authorName: String,
    val authorAvatar: String,
    val date: String
)

data class WorkflowSummary(
    val id: Long,
    val name: String,
    val path: String,
    val state: String
)

data class WorkflowRunSummary(
    val id: Long,
    val name: String,
    val displayTitle: String,
    val headBranch: String,
    val status: WorkflowStatus,
    val createdAt: String,
    val updatedAt: String,
    val actorLogin: String,
    val htmlUrl: String,
    val conclusion: String? = null,
    val headSha: String = "",
    val runStartedAt: String? = null
)

data class JobSummary(
    val id: Long,
    val name: String,
    val status: String,
    val conclusion: String?,
    val startedAt: String,
    val completedAt: String,
    val htmlUrl: String,
    val steps: List<StepSummary>
)

data class StepSummary(
    val name: String,
    val status: String,
    val conclusion: String?,
    val number: Int,
    val startedAt: String? = null,
    val completedAt: String? = null
)

data class ArtifactSummary(
    val id: Long,
    val name: String,
    val sizeInBytes: Long,
    val archiveDownloadUrl: String,
    val expired: Boolean,
    val createdAt: String,
    val expiresAt: String?
)
