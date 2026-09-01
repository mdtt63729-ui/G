package com.gitofy.domain.usecase

import com.gitofy.core.network.GitHubApiService
import com.gitofy.domain.model.*
import com.gitofy.data.remote.dto.PullRequest
import com.gitofy.data.remote.dto.Issue
import com.gitofy.data.remote.dto.Release
import com.gitofy.data.remote.dto.ContentFile
import javax.inject.Inject

class GetPullRequestsUseCase @Inject constructor(private val api: GitHubApiService) {
    suspend operator fun invoke(owner: String, repo: String, state: String): Result<List<PullRequestSummary>> = runCatching {
        val response = api.listPullRequests(owner, repo, state)
        if (!response.isSuccessful) throw RuntimeException("Failed: ${'$'}{response.code()}")
        val body = response.body() ?: emptyList()
        body.map { pr ->
            PullRequestSummary(
                number = pr.number,
                title = pr.title,
                body = pr.body,
                state = pr.state,
                isDraft = pr.draft,
                isMerged = pr.merged,
                authorLogin = pr.user?.login ?: "",
                authorAvatar = pr.user?.avatarUrl ?: "",
                headBranch = pr.head?.ref ?: "",
                baseBranch = pr.base?.ref ?: "",
                createdAt = pr.createdAt,
                updatedAt = pr.updatedAt,
                labels = pr.labels.map { it.name },
                htmlUrl = pr.htmlUrl,
                mergeable = pr.mergeable
            )
        }
    }
}

class GetPullRequestDetailUseCase @Inject constructor(private val api: GitHubApiService) {
    suspend operator fun invoke(owner: String, repo: String, prNumber: Int): Result<PullRequestDetail> = runCatching {
        val response = api.getPullRequest(owner, repo, prNumber)
        if (!response.isSuccessful) throw RuntimeException("Failed: ${'$'}{response.code()}")
        val pr = response.body() ?: throw RuntimeException("Empty response")
        PullRequestDetail(
            number = pr.number,
            title = pr.title,
            body = pr.body,
            state = pr.state,
            isDraft = pr.draft,
            isMerged = pr.merged,
            authorLogin = pr.user?.login ?: "",
            authorAvatar = pr.user?.avatarUrl ?: "",
            headBranch = pr.head?.ref ?: "",
            baseBranch = pr.base?.ref ?: "",
            createdAt = pr.createdAt,
            updatedAt = pr.updatedAt,
            labels = pr.labels.map { it.name },
            htmlUrl = pr.htmlUrl,
            mergeable = pr.mergeable,
            mergeableState = pr.mergeableState,
            changedFiles = 0,
            additions = 0,
            deletions = 0,
            commits = 0
        )
    }
}

class GetPRReviewsUseCase @Inject constructor(private val api: GitHubApiService) {
    suspend operator fun invoke(owner: String, repo: String, prNumber: Int): Result<List<ReviewSummary>> = runCatching {
        val response = api.listReviews(owner, repo, prNumber)
        if (!response.isSuccessful) throw RuntimeException("Failed: ${'$'}{response.code()}")
        val body = response.body() ?: emptyList()
        body.map { ReviewSummary(it.id, it.user?.login ?: "", it.body, it.state, it.submittedAt) }
    }
}

class GetPRCommentsUseCase @Inject constructor(private val api: GitHubApiService) {
    suspend operator fun invoke(owner: String, repo: String, prNumber: Int): Result<List<PRCommentSummary>> = runCatching {
        val response = api.listPRComments(owner, repo, prNumber)
        if (!response.isSuccessful) throw RuntimeException("Failed: ${'$'}{response.code()}")
        val body = response.body() ?: emptyList()
        body.map { PRCommentSummary(it.id, it.body, it.user?.login ?: "", it.createdAt, it.updatedAt) }
    }
}

class GetPRDiffUseCase @Inject constructor(private val api: GitHubApiService) {
    suspend operator fun invoke(owner: String, repo: String, prNumber: Int): Result<List<DiffFile>> = runCatching {
        val response = api.getPullRequestFiles(owner, repo, prNumber)
        if (!response.isSuccessful) throw RuntimeException("Failed: ${'$'}{response.code()}")
        val body = response.body() ?: emptyList()
        body.map { diff ->
            val status = when (diff.status) {
                "added" -> DiffFileStatus.ADDED
                "removed" -> DiffFileStatus.REMOVED
                "modified" -> DiffFileStatus.MODIFIED
                "renamed" -> DiffFileStatus.RENAMED
                "copied" -> DiffFileStatus.COPIED
                "changed" -> DiffFileStatus.CHANGED
                else -> DiffFileStatus.UNCHANGED
            }
            DiffFile(diff.filename, status, diff.additions, diff.deletions, diff.changes, diff.patch ?: "", diff.previousFilename ?: "")
        }
    }
}

class GetIssuesUseCase @Inject constructor(private val api: GitHubApiService) {
    suspend operator fun invoke(owner: String, repo: String, state: String): Result<List<IssueSummary>> = runCatching {
        val response = api.listIssues(owner, repo, state)
        if (!response.isSuccessful) throw RuntimeException("Failed: ${'$'}{response.code()}")
        val body = response.body() ?: emptyList()
        body.map { issue ->
            IssueSummary(
                number = issue.number,
                title = issue.title,
                body = issue.body,
                state = issue.state,
                authorLogin = issue.user?.login ?: "",
                authorAvatar = issue.user?.avatarUrl ?: "",
                labels = issue.labels.map { it.name },
                assignees = emptyList(),
                milestone = null,
                createdAt = issue.createdAt,
                updatedAt = issue.updatedAt,
                htmlUrl = issue.htmlUrl,
                commentCount = 0
            )
        }
    }
}

class GetContentUseCase @Inject constructor(private val api: GitHubApiService) {
    suspend operator fun invoke(owner: String, repo: String, path: String, ref: String?): Result<FileContent> = runCatching {
        val response = api.getContent(owner, repo, path, ref)
        if (!response.isSuccessful) throw RuntimeException("Failed: ${'$'}{response.code()}")
        val body = response.body() ?: throw RuntimeException("Empty response")
        FileContent(
            name = body.name ?: path.substringAfterLast("/"),
            path = body.path ?: path,
            sha = body.sha,
            size = 0L,
            type = "file",
            content = body.content,
            encoding = body.encoding,
            htmlUrl = "",
            downloadUrl = null
        )
    }
}

class GetV4BranchesUseCase @Inject constructor(private val api: GitHubApiService) {
    suspend operator fun invoke(owner: String, repo: String): Result<List<BranchDetail>> = runCatching {
        val response = api.listBranches(owner, repo)
        if (!response.isSuccessful) throw RuntimeException("Failed: ${'$'}{response.code()}")
        val body = response.body() ?: emptyList()
        body.map { BranchDetail(it.name, it.commit?.sha ?: "", false, it.protected) }
    }
}
