package com.gitofy.domain.usecase

import com.gitofy.core.network.GitHubApiService
import com.gitofy.domain.model.*
import com.gitofy.domain.model.GitOFYError
import javax.inject.Inject

// PRD v4.0 Use Cases

class GetPullRequestsUseCase @Inject constructor(private val api: GitHubApiService) {
    suspend operator fun invoke(owner: String, repo: String, state: String): Result<List<PullRequestSummary>> = runCatching {
        val response = api.listPullRequests(owner, repo, state)
        if (!response.isSuccessful) throw RuntimeException("Failed: ${response.code()}")
        response.body()?.map { it.toSummary() } ?: emptyList()
    }
}

class GetPullRequestDetailUseCase @Inject constructor(private val api: GitHubApiService) {
    suspend operator fun invoke(owner: String, repo: String, prNumber: Int): Result<PullRequestDetail> = runCatching {
        val response = api.getPullRequest(owner, repo, prNumber)
        if (!response.isSuccessful) throw RuntimeException("Failed: ${response.code()}")
        response.body()?.toDetail() ?: throw RuntimeException("Empty response")
    }
}

class GetPRReviewsUseCase @Inject constructor(private val api: GitHubApiService) {
    suspend operator fun invoke(owner: String, repo: String, prNumber: Int): Result<List<ReviewSummary>> = runCatching {
        val response = api.listReviews(owner, repo, prNumber)
        if (!response.isSuccessful) throw RuntimeException("Failed: ${response.code()}")
        response.body()?.map { ReviewSummary(it.id, it.user?.login ?: "", it.body, it.state, it.submittedAt) } ?: emptyList()
    }
}

class GetPRCommentsUseCase @Inject constructor(private val api: GitHubApiService) {
    suspend operator fun invoke(owner: String, repo: String, prNumber: Int): Result<List<PRCommentSummary>> = runCatching {
        val response = api.listPRComments(owner, repo, prNumber)
        if (!response.isSuccessful) throw RuntimeException("Failed: ${response.code()}")
        response.body()?.map { PRCommentSummary(it.id, it.body, it.user?.login ?: "", it.createdAt, it.updatedAt) } ?: emptyList()
    }
}

class GetPRDiffUseCase @Inject constructor(private val api: GitHubApiService) {
    suspend operator fun invoke(owner: String, repo: String, prNumber: Int): Result<List<DiffFile>> = runCatching {
        val response = api.getPRDiff(owner, repo, prNumber)
        if (!response.isSuccessful) throw RuntimeException("Failed: ${response.code()}")
        response.body()?.map {
            DiffFile(it.filename, DiffFileStatus.valueOf(it.status.uppercase()), it.additions, it.deletions, it.changes, it.patch, it.previousFilename)
        } ?: emptyList()
    }
}

class GetIssuesUseCase @Inject constructor(private val api: GitHubApiService) {
    suspend operator fun invoke(owner: String, repo: String, state: String): Result<List<IssueSummary>> = runCatching {
        val response = api.listIssues(owner, repo, state)
        if (!response.isSuccessful) throw RuntimeException("Failed: ${response.code()}")
        response.body()?.filter { it.pullRequest == null }?.map { it.toSummary() } ?: emptyList()
    }
}

class GetContentUseCase @Inject constructor(private val api: GitHubApiService) {
    suspend operator fun invoke(owner: String, repo: String, path: String): Result<List<FileContent>> = runCatching {
        val response = api.getContent(owner, repo, path)
        if (!response.isSuccessful) throw RuntimeException("Failed: ${response.code()}")
        response.body()?.map { it.toModel() } ?: emptyList()
    }
}

class GetBranchesUseCase @Inject constructor(private val api: GitHubApiService) {
    suspend operator fun invoke(owner: String, repo: String): Result<List<BranchDetail>> = runCatching {
        val response = api.listBranches(owner, repo)
        if (!response.isSuccessful) throw RuntimeException("Failed: ${response.code()}")
        response.body()?.map { BranchDetail(it.name, it.commit?.sha ?: "", it.name == response.body()?.firstOrNull { br -> br.name == "main" || br.name == "master" }?.name, false) } ?: emptyList()
    }
}

// Extension mappers
private fun PullRequestDTO.toSummary() = PullRequestSummary(
    number = number, title = title, body = body, state = state,
    isDraft = draft, isMerged = merged, authorLogin = user?.login ?: "",
    authorAvatar = user?.avatarUrl ?: "", headBranch = head?.ref ?: "",
    baseBranch = base?.ref ?: "", createdAt = createdAt, updatedAt = updatedAt,
    labels = labels.map { it.name }, htmlUrl = htmlUrl, mergeable = mergeable
)

private fun PullRequestDTO.toDetail() = PullRequestDetail(
    number = number, title = title, body = body, state = state,
    isDraft = draft, isMerged = merged, authorLogin = user?.login ?: "",
    authorAvatar = user?.avatarUrl ?: "", headBranch = head?.ref ?: "",
    baseBranch = base?.ref ?: "", createdAt = createdAt, updatedAt = updatedAt,
    labels = labels.map { it.name }, htmlUrl = htmlUrl, mergeable = mergeable,
    mergeableState = mergeableState, changedFiles = 0, additions = 0, deletions = 0, commits = 0
)

private fun IssueDTO.toSummary() = IssueSummary(
    number = number, title = title, body = body, state = state,
    authorLogin = user?.login ?: "", authorAvatar = user?.avatarUrl ?: "",
    labels = labels.map { it.name }, assignees = assignees.map { it.login },
    milestone = milestone?.title, createdAt = createdAt, updatedAt = updatedAt,
    htmlUrl = htmlUrl, commentCount = comments
)

private fun ContentFileDTO.toModel() = FileContent(
    name = name, path = path, sha = sha, size = size, type = type,
    content = content, encoding = encoding, htmlUrl = htmlUrl, downloadUrl = downloadUrl
)

// Type aliases for local DTO references
private typealias PullRequestDTO = com.gitofy.data.remote.dto.PullRequest
private typealias IssueDTO = com.gitofy.data.remote.dto.Issue
private typealias ContentFileDTO = com.gitofy.data.remote.dto.ContentFile
