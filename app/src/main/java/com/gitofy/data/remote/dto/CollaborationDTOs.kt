package com.gitofy.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// PRD v4.0 — Pull Request DTOs

@Serializable
data class PullRequestList(
    @SerialName("total_count") val totalCount: Int = 0,
    val items: List<PullRequest> = emptyList()
)

@Serializable
data class PullRequest(
    val id: Long = 0,
    val number: Int = 0,
    val title: String = "",
    val body: String? = null,
    val state: String = "",
    val draft: Boolean = false,
    val merged: Boolean = false,
    val mergedAt: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("closed_at") val closedAt: String? = null,
    val user: Owner? = null,
    val labels: List<Label> = emptyList(),
    @SerialName("head") val head: BranchRef? = null,
    @SerialName("base") val base: BranchRef? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("mergeable") val mergeable: Boolean? = null,
    @SerialName("mergeable_state") val mergeableState: String? = null,
    val draft1: Boolean = false
)

@Serializable
data class BranchRef(
    val ref: String = "",
    val sha: String = "",
    val label: String = "",
    val user: Owner? = null,
    val repo: Repository? = null
)

@Serializable
data class Label(
    val id: Long = 0,
    val name: String = "",
    val color: String = "",
    val description: String? = null
)

@Serializable
data class CreatePRRequest(
    val title: String,
    val body: String? = null,
    val head: String,
    val base: String,
    val draft: Boolean = false
)

@Serializable
data class Review(
    val id: Long = 0,
    val user: Owner? = null,
    val body: String? = null,
    val state: String = "",
    @SerialName("submitted_at") val submittedAt: String? = null,
    @SerialName("html_url") val htmlUrl: String = ""
)

@Serializable
data class CreateReviewRequest(
    val body: String? = null,
    val event: String = "COMMENT", // APPROVE, REQUEST_CHANGES, COMMENT
    val comments: List<ReviewComment> = emptyList()
)

@Serializable
data class ReviewComment(
    val path: String = "",
    val body: String = "",
    val line: Int? = null,
    val side: String = "RIGHT"
)

@Serializable
data class PRComment(
    val id: Long = 0,
    val body: String = "",
    val user: Owner? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = ""
)

@Serializable
data class CreateCommentRequest(
    val body: String
)

@Serializable
data class DiffEntry(
    val sha: String = "",
    val filename: String = "",
    val status: String = "", // added, removed, modified, renamed
    val additions: Int = 0,
    val deletions: Int = 0,
    val changes: Int = 0,
    val patch: String? = null,
    @SerialName("previous_filename") val previousFilename: String? = null
)

@Serializable
data class IssueList(
    @SerialName("total_count") val totalCount: Int = 0,
    val items: List<Issue> = emptyList()
)

@Serializable
data class Issue(
    val id: Long = 0,
    val number: Int = 0,
    val title: String = "",
    val body: String? = null,
    val state: String = "",
    val user: Owner? = null,
    val labels: List<Label> = emptyList(),
    val assignees: List<Owner> = emptyList(),
    val milestone: Milestone? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("closed_at") val closedAt: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val comments: Int = 0
)

@Serializable
data class Milestone(
    val id: Long = 0,
    val number: Int = 0,
    val title: String = "",
    val state: String = "",
    val description: String? = null,
    @SerialName("due_on") val dueOn: String? = null
)

@Serializable
data class CreateIssueRequest(
    val title: String,
    val body: String? = null,
    val labels: List<String> = emptyList(),
    val assignees: List<String> = emptyList()
)

@Serializable
data class ContentFile(
    val name: String = "",
    val path: String = "",
    val sha: String = "",
    val size: Long = 0,
    val type: String = "", // file, dir, symlink, submodule
    val content: String? = null,
    val encoding: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("download_url") val downloadUrl: String? = null
)

@Serializable
data class CreateFileRequest(
    val message: String,
    val content: String,
    val branch: String? = null,
    val sha: String? = null
)

// PRD fix: GitHub's PUT/POST contents API does NOT return a ContentFile directly.
// The real response shape is { "content": {...ContentFile...}, "commit": {...} }.
// Using ContentFile as the response type caused a runtime JSON deserialization
// crash ("Expected beginning of the string, but got {" at $.content) because
// ContentFile.content is a String (base64 file body from GET), not an object.
@Serializable
data class FileCommitResponse(
    val content: ContentFile? = null,
    val commit: CommitRef? = null
)

@Serializable
data class CommitRef(
    val sha: String = "",
    @SerialName("html_url") val htmlUrl: String = ""
)

// PRD §27: Code search result DTO for the GitHub search/code API
@Serializable
data class CodeSearchResultItem(
    val name: String = "",
    val path: String = "",
    val sha: String = "",
    val url: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("repository") val repository: CodeSearchRepo? = null
)

@Serializable
data class CodeSearchRepo(
    val name: String = "",
    @SerialName("full_name") val fullName: String = "",
    @SerialName("html_url") val htmlUrl: String = ""
)

@Serializable
data class CodeSearchResult(
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("incomplete_results") val incompleteResults: Boolean = false,
    val items: List<CodeSearchResultItem> = emptyList()
)

@Serializable
data class Release(
    val id: Long = 0,
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("target_commitish") val targetCommitish: String = "",
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    @SerialName("prerelease") val preRelease: Boolean = false,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("published_at") val publishedAt: String? = null,
    val author: Owner? = null,
    val assets: List<ReleaseAsset> = emptyList(),
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("tarball_url") val tarballUrl: String? = null,
    @SerialName("zipball_url") val zipballUrl: String? = null
)

@Serializable
data class ReleaseAsset(
    val id: Long = 0,
    val name: String = "",
    @SerialName("size_in_bytes") val sizeInBytes: Long = 0,
    @SerialName("download_count") val downloadCount: Int = 0,
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = ""
)

@Serializable
data class CreateReleaseRequest(
    @SerialName("tag_name") val tagName: String,
    @SerialName("target_commitish") val targetCommitish: String = "main",
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    @SerialName("prerelease") val preRelease: Boolean = false
)
