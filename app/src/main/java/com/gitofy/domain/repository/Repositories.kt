package com.gitofy.domain.repository

import com.gitofy.domain.model.GitOFYError
import com.gitofy.domain.model.User
import com.gitofy.domain.model.RepoSummary
import com.gitofy.domain.model.RepoDetails
import com.gitofy.domain.model.BranchInfo
import com.gitofy.domain.model.CommitInfo
import com.gitofy.domain.model.WorkflowSummary
import com.gitofy.domain.model.WorkflowRunSummary
import com.gitofy.domain.model.JobSummary
import com.gitofy.domain.model.ArtifactSummary
import com.gitofy.domain.model.AuthState

/**
 * Domain interfaces — PRD 9.1: Repository interfaces in domain layer.
 * No implementation details leak through.
 */
interface AuthRepository {
    fun observeAuthState(): kotlinx.coroutines.flow.Flow<AuthState>
    suspend fun authenticate(token: String): Result<User>
    suspend fun getCurrentUser(): Result<User>
    fun signOut()
    fun hasStoredCredentials(): Boolean
}

interface GitHubRepository {
    fun observeRepositories(): kotlinx.coroutines.flow.Flow<List<RepoSummary>>
    suspend fun refreshRepositories(page: Int = 1): Result<List<RepoSummary>>
    suspend fun getRepository(owner: String, repo: String): Result<RepoDetails>
    suspend fun createRepository(name: String, description: String?, isPrivate: Boolean): Result<RepoSummary>
    fun observeBranches(owner: String, repo: String): kotlinx.coroutines.flow.Flow<List<BranchInfo>>
    suspend fun refreshBranches(owner: String, repo: String): Result<List<BranchInfo>>
    fun observeCommits(owner: String, repo: String): kotlinx.coroutines.flow.Flow<List<CommitInfo>>
    suspend fun refreshCommits(owner: String, repo: String): Result<List<CommitInfo>>
}

interface WorkflowRepository {
    fun observeWorkflows(owner: String, repo: String): kotlinx.coroutines.flow.Flow<List<WorkflowSummary>>
    suspend fun refreshWorkflows(owner: String, repo: String): Result<List<WorkflowSummary>>
    fun observeRuns(owner: String, repo: String): kotlinx.coroutines.flow.Flow<List<WorkflowRunSummary>>
    suspend fun refreshRuns(owner: String, repo: String): Result<List<WorkflowRunSummary>>
    fun observeRun(runId: Long): kotlinx.coroutines.flow.Flow<WorkflowRunSummary?>
    suspend fun getRun(owner: String, repo: String, runId: Long): Result<WorkflowRunSummary>
    fun observeJobs(runId: Long): kotlinx.coroutines.flow.Flow<List<JobSummary>>
    suspend fun refreshJobs(owner: String, repo: String, runId: Long): Result<List<JobSummary>>
    suspend fun dispatchWorkflow(owner: String, repo: String, workflowId: String, ref: String, inputs: Map<String, String>): Result<Unit>
}

interface ArtifactRepository {
    fun observeArtifacts(runId: Long): kotlinx.coroutines.flow.Flow<List<ArtifactSummary>>
    suspend fun refreshArtifacts(owner: String, repo: String, runId: Long): Result<List<ArtifactSummary>>
    suspend fun downloadArtifact(owner: String, repo: String, artifactId: Long, artifactName: String): Result<String>
}

interface GitRepository {
    suspend fun initialize(directory: String): Result<Unit>
    suspend fun configureUser(directory: String, name: String, email: String): Result<Unit>
    suspend fun addAll(directory: String): Result<Unit>
    suspend fun commit(directory: String, message: String): Result<Unit>
    suspend fun setRemote(directory: String, remoteUrl: String): Result<Unit>
    suspend fun push(directory: String, token: String, remoteUrl: String): Result<Unit>
    suspend fun verifyRemote(directory: String, remoteUrl: String): Result<Unit>
    fun cleanup(directory: String)
}
