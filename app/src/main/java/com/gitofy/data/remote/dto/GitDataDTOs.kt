package com.gitofy.data.remote.dto

import kotlinx.serialization.Serializable

/** Git Data API payloads used for one-commit repository synchronization. */
@Serializable
data class CreateGitBlobRequest(
    val content: String,
    val encoding: String = "base64"
)

@Serializable
data class GitBlobResponse(
    val sha: String = ""
)

@Serializable
data class CreateGitTreeEntryRequest(
    val path: String,
    val mode: String = "100644",
    val type: String = "blob",
    val sha: String? = null
)

@Serializable
data class CreateGitTreeRequest(
    val base_tree: String? = null,
    val tree: List<CreateGitTreeEntryRequest>
)

@Serializable
data class GitTreeCreateResponse(
    val sha: String = ""
)

@Serializable
data class CreateGitCommitRequest(
    val message: String,
    val tree: String,
    val parents: List<String> = emptyList()
)

@Serializable
data class GitCommitResponse(
    val sha: String = ""
)

@Serializable
data class UpdateGitRefRequest(
    val sha: String,
    val force: Boolean = false
)
