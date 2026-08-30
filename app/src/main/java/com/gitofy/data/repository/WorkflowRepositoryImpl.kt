package com.gitofy.data.repository

import com.gitofy.core.network.GitHubApiService
import com.gitofy.core.network.safeApiCall
import com.gitofy.core.notification.NotificationManager
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
    private val jobDao: JobDao,
    private val notificationManager: NotificationManager
) : WorkflowRepository {

    override fun observeWorkflows(owner: String, repo: String): Flow<List<WorkflowSummary>> =
        workflowDao.observeWorkflowsForRepo(owner, repo).map { it.map { entity ->
            WorkflowSummary(entity.id, entity.name, entity.path, entity.state)
        }}

    override suspend fun refreshWorkflows(owner: String, repo: String): Result<List<WorkflowSummary>> {
        val result = safeApiCall { apiService.listWorkflows(owner, repo) }
        return result.fold(
            onSuccess = { list ->
                val entities = list.workflows.map { it.toEntity(0, owner, repo) }
                workflowDao.clearForRepoScopes(owner, repo)
                workflowDao.upsertAll(entities)
                Result.success(list.workflows.map { it.toDomain() })
            },
            onFailure = { Result.failure(it) }
        )
    }

    override fun observeRuns(owner: String, repo: String): Flow<List<WorkflowRunSummary>> =
        workflowRunDao.observeRunsForRepo(owner, repo).map { it.map { entity -> entity.toDomain() } }

    override suspend fun refreshRuns(owner: String, repo: String): Result<List<WorkflowRunSummary>> {
        val result = safeApiCall { apiService.listWorkflowRuns(owner, repo) }
        return result.fold(
            onSuccess = { list ->
                val entities = list.workflowRuns.map { it.toEntity(0, owner, repo) }
                val previousById = entities.associate { entity ->
                    entity.id to workflowRunDao.getById(entity.id)
                }
                workflowRunDao.clearForRepoScopes(owner, repo)
                workflowRunDao.upsertAll(entities)
                entities.forEach { entity ->
                    notifyWorkflowTransition(
                        previous = previousById[entity.id],
                        currentStatus = entity.status,
                        currentConclusion = entity.conclusion,
                        workflowName = entity.name
                    )
                }
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
                val entity = run.toEntity(0, owner, repo)
                val previous = workflowRunDao.getById(run.id)
                workflowRunDao.upsertAll(listOf(entity))
                notifyWorkflowTransition(previous, entity.status, entity.conclusion, entity.name)
                Result.success(run.toDomain())
            },
            onFailure = { Result.failure(it) }
        )
    }

    // PRD §3.3/§4: Jobs now include their REAL steps from the GitHub API.
    override fun observeJobs(runId: Long): Flow<List<JobSummary>> =
        jobDao.observeJobs(runId).map { entities ->
            entities.map { JobSummary(
                it.id, it.name, it.status, it.conclusion,
                it.startedAt, it.completedAt, it.htmlUrl,
                it.stepsJson?.let { json -> parseStepsJson(json) } ?: emptyList()
            )}
        }

    override suspend fun refreshJobs(owner: String, repo: String, runId: Long): Result<List<JobSummary>> {
        val result = safeApiCall { apiService.listJobs(owner, repo, runId) }
        return result.map { jobList ->
            val entities = jobList.jobs.map { dto ->
                val entity = dto.toEntity()
                entity.copy(stepsJson = serializeSteps(dto.steps))
            }
            jobDao.upsertAll(entities)
            jobList.jobs.map { it.toDomain() }
        }
    }

    // PRD §9/§30: Download real job logs from GitHub
    override suspend fun getJobLogs(owner: String, repo: String, jobId: Long): Result<String> {
        return runCatching {
            val response = apiService.downloadJobLogs(owner, repo, jobId)
            if (response.isSuccessful) {
                response.body()?.string() ?: ""
            } else {
                throw RuntimeException("Failed to download job logs: ${response.code()}")
            }
        }
    }

    // PRD §9/§30: Download real workflow run logs from GitHub
    override suspend fun getRunLogs(owner: String, repo: String, runId: Long): Result<String> {
        return runCatching {
            val response = apiService.downloadRunLogs(owner, repo, runId)
            if (response.isSuccessful) {
                response.body()?.string() ?: ""
            } else {
                throw RuntimeException("Failed to download run logs: ${response.code()}")
            }
        }
    }

    // PRD §36: Get a single job by ID
    override suspend fun getJob(owner: String, repo: String, jobId: Long): Result<JobSummary> {
        return runCatching {
            val response = apiService.getJob(owner, repo, jobId)
            if (response.isSuccessful) {
                response.body()?.toDomain()
                    ?: throw RuntimeException("Empty job response")
            } else {
                throw RuntimeException("Failed to get job: ${response.code()}")
            }
        }
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

    // PRD §3.2: List runs filtered by workflow file ID
    override suspend fun refreshRunsByWorkflow(
        owner: String, repo: String, workflowId: String
    ): Result<List<WorkflowRunSummary>> {
        val result = safeApiCall { apiService.listWorkflowRunsByWorkflow(owner, repo, workflowId) }
        return result.fold(
            onSuccess = { list ->
                val entities = list.workflowRuns.map { it.toEntity(0, owner, repo) }
                val previousById = entities.associate { entity ->
                    entity.id to workflowRunDao.getById(entity.id)
                }
                workflowRunDao.upsertAll(entities)
                entities.forEach { entity ->
                    notifyWorkflowTransition(previousById[entity.id], entity.status, entity.conclusion, entity.name)
                }
                Result.success(list.workflowRuns.map { it.toDomain() })
            },
            onFailure = { Result.failure(it) }
        )
    }

    private fun notifyWorkflowTransition(
        previous: com.gitofy.data.local.entity.WorkflowRunEntity?,
        currentStatus: String?,
        currentConclusion: String?,
        workflowName: String
    ) {
        val wasActive = previous?.status == "queued" || previous?.status == "in_progress"
        if (!wasActive) return

        when {
            currentStatus == "completed" && currentConclusion == "success" ->
                notificationManager.showWorkflowComplete(workflowName, eventKey = "$workflowName:${previous?.id ?: 0}")
            currentStatus == "completed" && currentConclusion in setOf("failure", "timed_out") ->
                notificationManager.showWorkflowFailed(workflowName, eventKey = "$workflowName:${previous?.id ?: 0}")
        }
    }

    // --- Step serialization helpers ---

    private fun serializeSteps(steps: List<com.gitofy.data.remote.dto.Step>): String {
        return steps.joinToString("\n") { step ->
            "${step.number}\t${step.name}\t${step.status}\t${step.conclusion ?: ""}\t${step.startedAt ?: ""}\t${step.completedAt ?: ""}"
        }
    }

    private fun parseStepsJson(json: String): List<StepSummary> {
        return json.lines().mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val parts = line.split("\t")
            if (parts.size < 4) return@mapNotNull null
            StepSummary(
                name = parts[1],
                status = parts[2],
                conclusion = parts[3].ifBlank { null },
                number = parts[0].toIntOrNull() ?: 0,
                startedAt = parts.getOrNull(4)?.ifBlank { null },
                completedAt = parts.getOrNull(5)?.ifBlank { null }
            )
        }
    }
}
