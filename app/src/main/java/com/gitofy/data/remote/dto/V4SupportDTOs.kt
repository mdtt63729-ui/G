package com.gitofy.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MergeRequest(
    @SerialName("commit_title") val commitTitle: String? = null,
    @SerialName("merge_method") val mergeMethod: String = "merge"
)

@Serializable
data class CreateBranchRequest(
    val ref: String,
    val sha: String
)

@Serializable
data class CompareResult(
    val status: String = "",
    @SerialName("ahead_by") val aheadBy: Int = 0,
    @SerialName("behind_by") val behindBy: Int = 0,
    @SerialName("total_commits") val totalCommits: Int = 0,
    val commits: List<Commit> = emptyList(),
    val files: List<DiffEntry> = emptyList()
)

@Serializable
data class CommitParent(
    val sha: String = ""
)

@Serializable
data class CommitStats(
    val additions: Int = 0,
    val deletions: Int = 0,
    val total: Int = 0
)

@Serializable
data class Tag(
    val name: String = "",
    val commit: TagCommit? = null
)

@Serializable
data class TagCommit(
    val sha: String = "",
    val url: String = ""
)

@Serializable
data class OrgDto(
    val id: Long = 0,
    val login: String = "",
    @SerialName("avatar_url") val avatarUrl: String = "",
    val description: String? = null,
    @SerialName("public_repos") val publicRepos: Int = 0
)

// PRD §33: Git Trees API — used by RepositorySyncEngine to load the remote
// file tree for diff comparison during update operations.
@Serializable
data class GitTreeResponse(
    val sha: String = "",
    val url: String = "",
    val tree: List<GitTreeEntry> = emptyList(),
    val truncated: Boolean = false
)

@Serializable
data class GitTreeEntry(
    val path: String = "",
    val mode: String = "",
    val type: String = "", // "blob", "tree", "commit"
    val sha: String = "",
    val size: Long = 0,
    val url: String = ""
)
