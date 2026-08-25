package com.gitofy.domain.usecase

import com.gitofy.core.network.GitHubApiService
import com.gitofy.domain.model.*
import javax.inject.Inject

class GetRepositoryHealthUseCase @Inject constructor(private val api: GitHubApiService) {
    suspend operator fun invoke(owner: String, repo: String): Result<RepositoryHealth> = runCatching {
        val prs = api.listPullRequests(owner, repo, "open")
        val issues = api.listIssues(owner, repo, "open")
        val workflows = api.listWorkflowRuns(owner, repo)

        val openPRs = prs.body()?.size ?: 0
        val openIssues = issues.body()?.filter { it.pullRequest == null }?.size ?: 0
        val failedWorkflows = workflows.body()?.workflowRuns?.count { it.conclusion == "failure" } ?: 0

        RepositoryHealth(
            openPRs = openPRs,
            openIssues = openIssues,
            failedWorkflows = failedWorkflows,
            recentCommits = 0,
            staleBranches = 0,
            recentReleases = 0,
            ciHealth = when {
                failedWorkflows == 0 -> HealthStatus.HEALTHY
                failedWorkflows <= 2 -> HealthStatus.NEEDS_ATTENTION
                else -> HealthStatus.CRITICAL
            },
            prHealth = when {
                openPRs == 0 -> HealthStatus.HEALTHY
                openPRs <= 5 -> HealthStatus.NEEDS_ATTENTION
                else -> HealthStatus.CRITICAL
            },
            issueHealth = when {
                openIssues == 0 -> HealthStatus.HEALTHY
                openIssues <= 10 -> HealthStatus.NEEDS_ATTENTION
                else -> HealthStatus.CRITICAL
            }
        )
    }
}
