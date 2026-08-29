package com.gitofy.domain.usecase

import com.gitofy.core.network.GitHubApiService
import javax.inject.Inject

class CancelWorkflowRunUseCase @Inject constructor(private val api: GitHubApiService) {
    suspend operator fun invoke(owner: String, repo: String, runId: Long): Result<Unit> = runCatching {
        val response = api.cancelWorkflowRun(owner, repo, runId)
        if (!response.isSuccessful) throw RuntimeException("Cancel failed: ${response.code()}")
    }
}

class RerunWorkflowRunUseCase @Inject constructor(private val api: GitHubApiService) {
    suspend operator fun invoke(owner: String, repo: String, runId: Long): Result<Unit> = runCatching {
        val response = api.rerunWorkflowRun(owner, repo, runId)
        if (!response.isSuccessful) throw RuntimeException("Rerun failed: ${response.code()}")
    }
}

class RerunFailedJobsUseCase @Inject constructor(private val api: GitHubApiService) {
    suspend operator fun invoke(owner: String, repo: String, runId: Long): Result<Unit> = runCatching {
        val response = api.rerunFailedJobs(owner, repo, runId)
        if (!response.isSuccessful) throw RuntimeException("Rerun failed jobs failed: ${response.code()}")
    }
}
