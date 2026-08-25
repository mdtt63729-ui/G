package com.gitofy.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MergeRequest(
    @SerialName("commit_title") val commitTitle: String? = null,
    @SerialName("merge_method") val mergeMethod: String = "merge" // merge, squash, rebase
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
data class Commit(
    val sha: String = "",
    val commit: CommitInfo? = null,
    val author: Owner? = null,
    val committer: Owner? = null,
    val files: List<DiffEntry> = emptyList(),
    val stats: CommitStats? = null,
    val parents: List<CommitParent> = emptyList()
)

@Serializable
data class CommitInfo(
    val message: String = "",
    val author: CommitAuthor? = null,
    val committer: CommitAuthor? = null
)

@Serializable
data class CommitAuthor(
    val name: String = "",
    val email: String = "",
    val date: String = ""
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
