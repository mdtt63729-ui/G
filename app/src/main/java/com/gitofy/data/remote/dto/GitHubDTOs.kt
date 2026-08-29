package com.gitofy.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubUser(
    val login: String = "",
    val id: Long = 0,
    @SerialName("node_id") val nodeId: String = "",
    @SerialName("avatar_url") val avatarUrl: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val name: String? = null,
    val company: String? = null,
    val blog: String? = null,
    val location: String? = null,
    val email: String? = null,
    val bio: String? = null,
    @SerialName("twitter_username") val twitterUsername: String? = null,
    @SerialName("public_repos") val publicRepos: Int = 0,
    @SerialName("public_gists") val publicGists: Int = 0,
    val followers: Int = 0,
    val following: Int = 0,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = ""
)

@Serializable
data class CreateRepoRequest(
    val name: String,
    val description: String? = null,
    val private: Boolean = false,
    @SerialName("auto_init") val autoInit: Boolean = false
)

@Serializable
data class Repository(
    val id: Long = 0,
    val name: String = "",
    @SerialName("full_name") val fullName: String = "",
    val owner: Owner? = null,
    val private: Boolean = false,
    @SerialName("html_url") val htmlUrl: String = "",
    val description: String? = null,
    val fork: Boolean = false,
    val url: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("pushed_at") val pushedAt: String = "",
    @SerialName("default_branch") val defaultBranch: String = "main",
    @SerialName("open_issues_count") val openIssuesCount: Int = 0,
    val watchers: Int = 0,
    @SerialName("stargazers_count") val stargazersCount: Int = 0,
    @SerialName("forks_count") val forksCount: Int = 0,
    val permissions: RepoPermissions? = null
) {
    val ownerLogin: String get() = owner?.login ?: fullName.substringBefore("/", "")
}

@Serializable
data class RepoPermissions(
    val admin: Boolean = false,
    val push: Boolean = false,
    val pull: Boolean = true
)

@Serializable
data class Owner(
    val login: String = "",
    val id: Long = 0,
    @SerialName("node_id") val nodeId: String = "",
    @SerialName("avatar_url") val avatarUrl: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val type: String = ""
)

@Serializable
data class Branch(
    val name: String = "",
    val commit: BranchCommit? = null,
    val protected: Boolean = false
)

@Serializable
data class BranchCommit(
    val sha: String = "",
    val url: String = ""
)

@Serializable
data class Commit(
    val sha: String = "",
    @SerialName("node_id") val nodeId: String = "",
    val commit: CommitData? = null,
    val url: String = "",
    val author: Owner? = null,
    val committer: Owner? = null
)

@Serializable
data class CommitData(
    val author: CommitAuthor? = null,
    val committer: CommitAuthor? = null,
    val message: String = "",
    val url: String = ""
)

@Serializable
data class CommitAuthor(
    val name: String = "",
    val email: String = "",
    val date: String = ""
)

@Serializable
data class WorkflowList(
    @SerialName("total_count") val totalCount: Int = 0,
    val workflows: List<Workflow> = emptyList()
)

@Serializable
data class Workflow(
    val id: Long = 0,
    @SerialName("node_id") val nodeId: String = "",
    val name: String = "",
    val path: String = "",
    val state: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    val url: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val badgeUrl: String? = null
)

@Serializable
data class WorkflowRunList(
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("workflow_runs") val workflowRuns: List<WorkflowRun> = emptyList()
)

@Serializable
data class WorkflowRun(
    val id: Long = 0,
    val name: String = "",
    @SerialName("head_branch") val headBranch: String = "",
    @SerialName("head_sha") val headSha: String = "",
    val status: String = "",
    val conclusion: String? = null,
    @SerialName("workflow_id") val workflowId: Long = 0,
    val url: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("run_started_at") val runStartedAt: String? = null,
    val actor: Owner? = null,
    @SerialName("run_attempt") val runAttempt: Int = 1,
    @SerialName("display_title") val displayTitle: String = ""
)

@Serializable
data class DispatchWorkflowRequest(
    val ref: String,
    val inputs: Map<String, String> = emptyMap()
)

@Serializable
data class JobList(
    @SerialName("total_count") val totalCount: Int = 0,
    val jobs: List<Job> = emptyList()
)

@Serializable
data class Job(
    val id: Long = 0,
    @SerialName("run_id") val runId: Long = 0,
    @SerialName("run_url") val runUrl: String = "",
    val name: String = "",
    val status: String = "",
    val conclusion: String? = null,
    @SerialName("started_at") val startedAt: String = "",
    @SerialName("completed_at") val completedAt: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val steps: List<Step> = emptyList()
)

@Serializable
data class Step(
    val name: String = "",
    val status: String = "",
    val conclusion: String? = null,
    val number: Int = 0,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null
)

@Serializable
data class ArtifactList(
    @SerialName("total_count") val totalCount: Int = 0,
    val artifacts: List<Artifact> = emptyList()
)

@Serializable
data class Artifact(
    val id: Long = 0,
    @SerialName("node_id") val nodeId: String = "",
    val name: String = "",
    @SerialName("size_in_bytes") val sizeInBytes: Long = 0,
    val url: String = "",
    @SerialName("archive_download_url") val archiveDownloadUrl: String = "",
    val expired: Boolean = false,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("expires_at") val expiresAt: String? = null
)

@Serializable
data class RateLimit(
    val rate: RateLimitInfo? = null,
    val resources: RateLimitResources? = null
)

@Serializable
data class RateLimitInfo(
    val limit: Int = 0,
    val remaining: Int = 0,
    val used: Int = 0,
    val reset: Long = 0
)

@Serializable
data class RateLimitResources(
    val core: RateLimitInfo? = null,
    val search: RateLimitInfo? = null,
    val graphql: RateLimitInfo? = null,
    @SerialName("integration_manifest") val integrationManifest: RateLimitInfo? = null
)
