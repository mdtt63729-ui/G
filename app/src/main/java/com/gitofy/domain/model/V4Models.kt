package com.gitofy.domain.model

// PRD v4.0 — Domain models for PR, Issue, Code, Diff, Health

data class PullRequestSummary(
    val number: Int,
    val title: String,
    val body: String?,
    val state: String, // open, closed
    val isDraft: Boolean,
    val isMerged: Boolean,
    val authorLogin: String,
    val authorAvatar: String,
    val headBranch: String,
    val baseBranch: String,
    val createdAt: String,
    val updatedAt: String,
    val labels: List<String>,
    val htmlUrl: String,
    val mergeable: Boolean?
)

data class PullRequestDetail(
    val number: Int,
    val title: String,
    val body: String?,
    val state: String,
    val isDraft: Boolean,
    val isMerged: Boolean,
    val authorLogin: String,
    val authorAvatar: String,
    val headBranch: String,
    val baseBranch: String,
    val createdAt: String,
    val updatedAt: String,
    val labels: List<String>,
    val htmlUrl: String,
    val mergeable: Boolean?,
    val mergeableState: String?,
    val changedFiles: Int,
    val additions: Int,
    val deletions: Int,
    val commits: Int
)

data class ReviewSummary(
    val id: Long,
    val user: String,
    val body: String?,
    val state: String, // APPROVED, CHANGES_REQUESTED, COMMENTED, PENDING, DISMISSED
    val submittedAt: String?
)

data class PRCommentSummary(
    val id: Long,
    val body: String,
    val author: String,
    val createdAt: String,
    val updatedAt: String
)

data class DiffFile(
    val filename: String,
    val status: DiffFileStatus,
    val additions: Int,
    val deletions: Int,
    val changes: Int,
    val patch: String?,
    val previousFilename: String?
)

enum class DiffFileStatus { ADDED, REMOVED, MODIFIED, RENAMED, COPIED, CHANGED, UNCHANGED }

data class IssueSummary(
    val number: Int,
    val title: String,
    val body: String?,
    val state: String,
    val authorLogin: String,
    val authorAvatar: String,
    val labels: List<String>,
    val assignees: List<String>,
    val milestone: String?,
    val createdAt: String,
    val updatedAt: String,
    val htmlUrl: String,
    val commentCount: Int
)

data class BranchDetail(
    val name: String,
    val commitSha: String,
    val isDefault: Boolean,
    val isProtected: Boolean
)

data class CommitDetail(
    val sha: String,
    val message: String,
    val authorName: String,
    val authorAvatar: String,
    val authorEmail: String,
    val committerName: String,
    val date: String,
    val parentShas: List<String>,
    val changedFiles: Int,
    val additions: Int,
    val deletions: Int
)

data class FileContent(
    val name: String,
    val path: String,
    val sha: String,
    val size: Long,
    val type: String, // file, dir
    val content: String?,
    val encoding: String?,
    val htmlUrl: String,
    val downloadUrl: String?
) {
    val isDirectory: Boolean get() = type == "dir"
    val decodedContent: String? get() = if (encoding == "base64" && content != null) {
        try {
            android.util.Base64.decode(content, android.util.Base64.DEFAULT).toString(Charsets.UTF_8)
        } catch (e: Exception) { null }
    } else content
}

data class RepositoryHealth(
    val openPRs: Int,
    val openIssues: Int,
    val failedWorkflows: Int,
    val recentCommits: Int,
    val staleBranches: Int,
    val recentReleases: Int,
    val ciHealth: HealthStatus,
    val prHealth: HealthStatus,
    val issueHealth: HealthStatus
)

enum class HealthStatus { HEALTHY, NEEDS_ATTENTION, CRITICAL, UNKNOWN }

// PRD v6.0 — Release & Deployment models
data class ReleaseSummary(
    val id: Long,
    val tagName: String,
    val name: String,
    val body: String?,
    val isDraft: Boolean,
    val isPreRelease: Boolean,
    val createdAt: String,
    val publishedAt: String?,
    val authorLogin: String,
    val htmlUrl: String,
    val assetCount: Int
)

data class ReleaseAssetInfo(
    val id: Long,
    val name: String,
    val sizeInBytes: Long,
    val downloadCount: Int,
    val downloadUrl: String,
    val createdAt: String
)

data class TagInfo(
    val name: String,
    val commitSha: String,
    val releaseId: Long?
)

data class DeploymentInfo(
    val id: Long,
    val environment: String,
    val status: String,
    val commitSha: String,
    val createdAt: String,
    val updatedAt: String
)

// PRD v6.5 — Team & Organization models
data class OrganizationSummary(
    val login: String,
    val avatarUrl: String,
    val description: String?,
    val publicRepos: Int
)

data class AccountInfo(
    val id: String,
    val login: String,
    val avatarUrl: String,
    val type: String // personal, work, client
)

data class TeamSummary(
    val name: String,
    val slug: String,
    val description: String?,
    val membersCount: Int,
    val reposCount: Int
)

data class RepositoryHealthScore(
    val repoId: Long,
    val repoName: String,
    val ciHealthScore: Float,
    val prHealthScore: Float,
    val issueHealthScore: Float,
    val releaseHealthScore: Float,
    val overallScore: Float,
    val reasoning: String
)

// PRD v7.0 — Intelligence models
data class AttentionItem(
    val priority: AttentionPriority,
    val title: String,
    val description: String,
    val repoName: String,
    val actionType: String,
    val targetUrl: String?
)

enum class AttentionPriority { CRITICAL, HIGH, MEDIUM, LOW }

data class CIFailureTrend(
    val failureRate: Float,
    val averageDuration: Long,
    val medianDuration: Long,
    val frequentFailureCategories: List<String>,
    val longestJobs: List<String>
)

data class ReleaseReadiness(
    val isReady: Boolean,
    val ciPassing: Boolean,
    val requiredPRsMerged: Boolean,
    val artifactAvailable: Boolean,
    val artifactVerified: Boolean,
    val noBlockingFailures: Boolean,
    val releaseNotesPrepared: Boolean,
    val blockingIssues: List<String>
)

data class AIAnalysis(
    val id: String,
    val type: AIAnalysisType,
    val rootCause: String?,
    val evidence: String?,
    val confidence: AIConfidence,
    val recommendedAction: String?,
    val isObserved: Boolean,
    val isInferred: Boolean,
    val isSuggested: Boolean
)

enum class AIAnalysisType {
    BUILD_FAILURE, LOG_SUMMARY, PR_SUMMARY, COMMIT_MESSAGE,
    PR_DESCRIPTION, CODE_EXPLANATION, CODE_REVIEW, SECURITY,
    DEPENDENCY, WORKFLOW_OPTIMIZATION, REPOSITORY_QA
}

enum class AIConfidence { HIGH, MEDIUM, LOW, INSUFFICIENT }

data class DeveloperRecommendation(
    val type: RecommendationType,
    val title: String,
    val description: String,
    val repoName: String?,
    val actionUrl: String?
)

enum class RecommendationType {
    RETRY_WORKFLOW, REVIEW_PR, UPDATE_DEPENDENCY, INVESTIGATE_FLAKY_TEST,
    OPTIMIZE_WORKFLOW, PREPARE_RELEASE, CLEAN_STALE_BRANCHES
}

data class GlobalActivityItem(
    val timestamp: Long,
    val type: String,
    val description: String,
    val repoName: String?,
    val status: String
)
