package com.gitofy.domain.usecase

import com.gitofy.domain.repository.GitHubRepository
import com.gitofy.domain.repository.WorkflowRepository
import com.gitofy.domain.repository.ArtifactRepository
import com.gitofy.domain.model.*
import javax.inject.Inject

// Repository use cases
class GetRepositoriesUseCase @Inject constructor(
    private val repo: GitHubRepository
) {
    operator fun invoke() = repo.observeRepositories()
    suspend fun refresh(page: Int = 1) = repo.refreshRepositories(page)
}

class GetRepositoryDetailsUseCase @Inject constructor(
    private val repo: GitHubRepository
) {
    suspend operator fun invoke(owner: String, name: String) = repo.getRepository(owner, name)
}

class CreateRepositoryUseCase @Inject constructor(
    private val repo: GitHubRepository
) {
    suspend operator fun invoke(name: String, description: String?, isPrivate: Boolean) =
        repo.createRepository(name, description, isPrivate)
}

class GetBranchesUseCase @Inject constructor(
    private val repo: GitHubRepository
) {
    operator fun invoke(owner: String, name: String) = repo.observeBranches(owner, name)
    suspend fun refresh(owner: String, name: String) = repo.refreshBranches(owner, name)
}

class GetCommitsUseCase @Inject constructor(
    private val repo: GitHubRepository
) {
    operator fun invoke(owner: String, name: String) = repo.observeCommits(owner, name)
    suspend fun refresh(owner: String, name: String) = repo.refreshCommits(owner, name)
}

// Workflow use cases
class GetWorkflowsUseCase @Inject constructor(
    private val repo: WorkflowRepository
) {
    operator fun invoke(owner: String, name: String) = repo.observeWorkflows(owner, name)
    suspend fun refresh(owner: String, name: String) = repo.refreshWorkflows(owner, name)
}

class GetWorkflowRunsUseCase @Inject constructor(
    private val repo: WorkflowRepository
) {
    operator fun invoke(owner: String, name: String) = repo.observeRuns(owner, name)
    suspend fun refresh(owner: String, name: String) = repo.refreshRuns(owner, name)
}

class GetWorkflowRunUseCase @Inject constructor(
    private val repo: WorkflowRepository
) {
    operator fun invoke(runId: Long) = repo.observeRun(runId)
    suspend fun get(owner: String, name: String, runId: Long) = repo.getRun(owner, name, runId)
}

class GetJobsUseCase @Inject constructor(
    private val repo: WorkflowRepository
) {
    operator fun invoke(runId: Long) = repo.observeJobs(runId)
    suspend fun refresh(owner: String, name: String, runId: Long) = repo.refreshJobs(owner, name, runId)
}

class TriggerWorkflowUseCase @Inject constructor(
    private val repo: WorkflowRepository
) {
    suspend operator fun invoke(
        owner: String, name: String, workflowId: String, ref: String, inputs: Map<String, String>
    ) = repo.dispatchWorkflow(owner, name, workflowId, ref, inputs)
}

// Artifact use cases
class GetArtifactsUseCase @Inject constructor(
    private val repo: ArtifactRepository
) {
    operator fun invoke(runId: Long) = repo.observeArtifacts(runId)
    suspend fun refresh(owner: String, name: String, runId: Long) = repo.refreshArtifacts(owner, name, runId)
}

class DownloadArtifactUseCase @Inject constructor(
    private val repo: ArtifactRepository
) {
    suspend operator fun invoke(owner: String, name: String, artifactId: Long, artifactName: String) =
        repo.downloadArtifact(owner, name, artifactId, artifactName)
}
