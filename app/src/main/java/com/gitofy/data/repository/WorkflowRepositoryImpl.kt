package com.gitofy.data.repository

import com.gitofy.core.network.GitHubApiService
import com.gitofy.core.network.safeApiCall
import com.gitofy.data.local.dao.WorkflowDao
import com.gitofy.data.local.dao.WorkflowRunDao
import com.gitofy.data.local.dao.JobDao
import com.gitofy.data.mapper.toDomain
import com.gitofy.data.mapper.toEntity
import com.gitofy.data.remote.dto.DispatchWorkflowRequest
import com.gitofy.domain.model.*
import com.gitofy.domain.repository.WorkflowRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkflowRepositoryImpl @Inject constructor(
    private val apiService: GitHubApiService,
    private val workflowDao: WorkflowDao,
    private val workflowRunDao: WorkflowRunDao,
    private val jobDao: JobDao
) : WorkflowRepository {

    override fun observeWorkflows(owner: String, repo: String): Flow<List<WorkflowSummary>> =
        workflowDao.observeWorkflows(0).map { it.map { entity ->
            WorkflowSummary(entity.id, entity.name, entity.path, entity.state)
        }}

    override suspend fun refreshWorkflows(owner: String, repo: String): Result<List<WorkflowSummary>> {
        val result = safeApiCall { apiService.listWorkflows(owner, repo) }
        return result.fold(
            onSuccess = { list ->
                val entities = list.workflows.map { it.toEntity(0) }
                workflowDao.upsertAll(entities)
                Result.success(list.workflows.map { it.toDomain() })
            },
            onFailure = { Result.failure(it) }
        )
    }

    override fun observeRuns(owner: String, repo: String): Flow<List<WorkflowRunSummary>> =
        workflowRunDao.observeRuns(0).map { it.map { entity -> entity.toDomain() } }

    override suspend fun refreshRuns(owner: String, repo: String): Result<List<WorkflowRunSummary>> {
        val result = safeApiCall { apiService.listWorkflowRuns(owner, repo) }
        return result.fold(
            onSuccess = { list ->
                val entities = list.workflowRuns.map { it.toEntity(0) }
                workflowRunDao.upsertAll(entities)
                Result.success(list.workflowRuns.map { it.toDomain() })
            },
            onFailure = { Result.failure(it) }
        )
    }

    override fun observeRun(runId: Long): Flow<WorkflowRunSummary?> =
        workflowRunDao.observeRun(runId).map { it?.toDomain() }

    override suspend fun getRun(owner: String, repo: String, runId: Long): Result<WorkflowRunSummary> {
        val result = safeApiCall { apiService.getWorkflowRun(owner, repo, runId) }
        return result.fold(
            onSuccess = { run ->
                workflowRunDao.upsertAll(listOf(run.toEntity(0)))
                Result.success(run.toDomain())
            },
            onFailure = { Result.failure(it) }
        )
    }

    override fun observeJobs(runId: Long): Flow<List<JobSummary>> =
        jobDao.observeJobs(runId).map { entities ->
            entities.map { JobSummary(
                it.id, it.name, it.status, it.conclusion,
                it.startedAt, it.completedAt, it.htmlUrl, emptyList()
            )}
        }

    override suspend fun refreshJobs(owner: String, repo: String, runId: Long): Result<List<JobSummary>> {
        val result = safeApiCall { apiService.getWorkflowRunJobs(owner, repo, runId) }
        return result.fold(
            onSuccess = { jobList ->
                val entities = jobList.jobs.map { it.toEntity() }
                jobDao.upsertAll(entities)
                Result.success(jobList.jobs.map { it.toDomain() })
            },
            onFailure = { Result.failure(it) }
        )
    }

    override suspend fun dispatchWorkflow(
        owner: String, repo: String, workflowId: String, ref: String, inputs: Map<String, String>
    ): Result<Unit> {
        val request = DispatchWorkflowRequest(ref = ref, inputs = inputs)
        val result = safeApiCall { apiService.dispatchWorkflow(owner, repo, workflowId, request) }
        return result.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(it) }
        )
    }
}
