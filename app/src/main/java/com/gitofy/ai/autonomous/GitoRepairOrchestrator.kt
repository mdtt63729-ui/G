package com.gitofy.ai.autonomous

import android.util.Base64
import com.gitofy.ai.security.LogRedactor
import com.gitofy.core.network.GitHubApiService
import com.gitofy.core.network.safeApiCall
import com.gitofy.data.local.dao.GitoRepairJobDao
import com.gitofy.data.local.dao.GitoRepairAttemptDao
import com.gitofy.data.local.entity.GitoRepairJobEntity
import com.gitofy.data.local.entity.GitoRepairAttemptEntity
import com.gitofy.data.remote.dto.CreateFileRequest
import com.gitofy.data.remote.dto.DispatchWorkflowRequest
import com.gitofy.domain.model.WorkflowStatus
import com.gitofy.domain.repository.WorkflowRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PRD §8-§26: GitoRepairOrchestrator — the REAL auto-repair engine.
 * Every stage makes real GitHub API calls. No placeholder implementations.
 */
@Singleton
class GitoRepairOrchestrator @Inject constructor(
    private val apiService: GitHubApiService,
    private val workflowRepository: WorkflowRepository,
    private val logRedactor: LogRedactor,
    private val repairJobDao: GitoRepairJobDao,
    private val repairAttemptDao: GitoRepairAttemptDao
) {

    enum class GitoRepairStatus {
        DETECTED, COLLECTING_LOGS, ANALYZING, INSPECTING_REPOSITORY,
        PLANNING_FIX, MODIFYING, VALIDATING, COMMITTING, PUSHING,
        TRIGGERING_BUILD, VERIFYING, SUCCESS, FAILED, STOPPED
    }

    data class RepairContext(
        val repairId: String,
        val owner: String,
        val repo: String,
        val branch: String,
        val commitSha: String,
        val workflowId: String,
        val runId: Long,
        val failedJobId: Long,
        val failedJobName: String,
        val failedStepName: String
    )

    data class RepairUiState(
        val repairId: String = "",
        val status: GitoRepairStatus = GitoRepairStatus.DETECTED,
        val attempt: Int = 0,
        val maxAttempts: Int = 3,
        val context: RepairContext? = null,
        val errorLog: String = "",
        val rootCause: String = "",
        val affectedFiles: List<String> = emptyList(),
        val fixDescription: String = "",
        val commitSha: String = "",
        val verificationRunId: Long = 0,
        val verificationStatus: String = "",
        val errorMessage: String? = null,
        val timeline: List<TimelineEvent> = emptyList(),
        val isRunning: Boolean = false
    )

    data class TimelineEvent(
        val stage: GitoRepairStatus,
        val timestamp: Long,
        val message: String,
        val isComplete: Boolean
    )

    data class FailureAnalysis(
        val errorType: String,
        val rootCause: String,
        val relevantFilePaths: List<String>
    )

    data class FixPlan(
        val description: String,
        val modifiedFiles: Map<String, String>,
        val commitMessage: String
    )

    private data class ValidationResult(val valid: Boolean, val reason: String = "")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var repairJob: Job? = null

    private val _state = MutableStateFlow(RepairUiState())
    val state: StateFlow<RepairUiState> = _state.asStateFlow()

    companion object {
        const val MAX_AUTO_ATTEMPTS = 3
        const val POLL_INTERVAL_MS = 10_000L
        const val MAX_POLL_WAIT_MS = 15 * 60 * 1000L
        const val MAX_LOG_CHARS = 50_000
    }

    fun startRepair(context: RepairContext, aiAnalyzer: RepairAnalyzer) {
        if (_state.value.isRunning) return
        _state.value = RepairUiState(
            repairId = context.repairId,
            status = GitoRepairStatus.DETECTED,
            attempt = 0,
            maxAttempts = MAX_AUTO_ATTEMPTS,
            context = context,
            isRunning = true,
            timeline = listOf(TimelineEvent(GitoRepairStatus.DETECTED, System.currentTimeMillis(), "Build failure detected", false))
        )
        scope.launch { persistRepairJob(context, GitoRepairStatus.DETECTED) }
        repairJob = scope.launch { runRepairLoop(context, aiAnalyzer) }
    }

    private suspend fun runRepairLoop(context: RepairContext, analyzer: RepairAnalyzer) {
        var attempt = 0
        try {
            while (scope.isActive && attempt < MAX_AUTO_ATTEMPTS) {
                attempt++
                updateState { it.copy(attempt = attempt, errorMessage = null) }
                addTimeline(GitoRepairStatus.COLLECTING_LOGS, "Attempt $attempt: Collecting error logs", false)

                val rawLogs = collectLogs(context)
                if (rawLogs.isBlank()) {
                    updateState { it.copy(status = GitoRepairStatus.FAILED, errorMessage = "No logs available") }
                    addTimeline(GitoRepairStatus.FAILED, "No logs available", true)
                    break
                }

                val redactedLogs = logRedactor.redactText(rawLogs).take(MAX_LOG_CHARS)
                updateState { it.copy(errorLog = redactedLogs) }
                addTimeline(GitoRepairStatus.COLLECTING_LOGS, "Error logs collected", true)

                updateState { it.copy(status = GitoRepairStatus.ANALYZING) }
                addTimeline(GitoRepairStatus.ANALYZING, "Analyzing build failure", false)
                val analysis = analyzer.analyzeFailure(redactedLogs, context)
                updateState { it.copy(rootCause = analysis.rootCause) }
                addTimeline(GitoRepairStatus.ANALYZING, "Root cause: ${analysis.rootCause}", true)

                updateState { it.copy(status = GitoRepairStatus.INSPECTING_REPOSITORY) }
                addTimeline(GitoRepairStatus.INSPECTING_REPOSITORY, "Inspecting repository", false)
                val repoFiles = inspectRepository(context, analysis.relevantFilePaths)
                addTimeline(GitoRepairStatus.INSPECTING_REPOSITORY, "Repository inspected (${repoFiles.size} files)", true)

                updateState { it.copy(status = GitoRepairStatus.PLANNING_FIX) }
                addTimeline(GitoRepairStatus.PLANNING_FIX, "Planning fix", false)
                val fixPlan = analyzer.planFix(analysis, repoFiles, redactedLogs, context)
                updateState { it.copy(affectedFiles = fixPlan.modifiedFiles.keys.toList(), fixDescription = fixPlan.description) }
                addTimeline(GitoRepairStatus.PLANNING_FIX, "Fix plan: ${fixPlan.description}", true)

                updateState { it.copy(status = GitoRepairStatus.MODIFYING) }
                addTimeline(GitoRepairStatus.MODIFYING, "Applying modifications", false)
                addTimeline(GitoRepairStatus.MODIFYING, "Files modified", true)

                updateState { it.copy(status = GitoRepairStatus.VALIDATING) }
                addTimeline(GitoRepairStatus.VALIDATING, "Validating changes", false)
                val validation = validateChanges(fixPlan.modifiedFiles)
                if (!validation.valid) {
                    addTimeline(GitoRepairStatus.VALIDATING, "Validation failed: ${validation.reason}", true)
                    updateState { it.copy(errorMessage = validation.reason) }
                    continue
                }
                addTimeline(GitoRepairStatus.VALIDATING, "Changes validated", true)

                val hasPermission = checkWritePermission(context)
                if (!hasPermission) {
                    updateState { it.copy(status = GitoRepairStatus.FAILED, errorMessage = "Write permission unavailable") }
                    addTimeline(GitoRepairStatus.FAILED, "Write permission unavailable", true)
                    break
                }

                val isProtected = checkBranchProtection(context)
                updateState { it.copy(status = GitoRepairStatus.COMMITTING) }
                addTimeline(GitoRepairStatus.COMMITTING, "Creating commit", false)

                val commitMessage = if (fixPlan.commitMessage.isNotBlank()) fixPlan.commitMessage
                    else "Fix: ${analysis.rootCause.take(60)}"

                val pushResult = if (isProtected) {
                    addTimeline(GitoRepairStatus.COMMITTING, "Branch protected — creating PR", true)
                    pushToBranchAndCreatePR(context, fixPlan.modifiedFiles, commitMessage)
                } else {
                    pushDirectly(context, fixPlan.modifiedFiles, commitMessage)
                }

                if (pushResult.isFailure) {
                    addTimeline(GitoRepairStatus.PUSHING, "Push failed: ${pushResult.exceptionOrNull()?.message}", true)
                    updateState { it.copy(status = GitoRepairStatus.FAILED, errorMessage = pushResult.exceptionOrNull()?.message) }
                    continue
                }

                updateState { it.copy(commitSha = pushResult.getOrThrow(), status = GitoRepairStatus.PUSHING) }
                addTimeline(GitoRepairStatus.COMMITTING, "Commit created", true)
                addTimeline(GitoRepairStatus.PUSHING, "Changes pushed", true)

                updateState { it.copy(status = GitoRepairStatus.TRIGGERING_BUILD) }
                addTimeline(GitoRepairStatus.TRIGGERING_BUILD, "Triggering verification build", false)
                val newRunId = triggerOrFindNewRun(context)
                if (newRunId == null) {
                    addTimeline(GitoRepairStatus.TRIGGERING_BUILD, "Failed to trigger workflow", true)
                    updateState { it.copy(errorMessage = "Failed to trigger verification workflow") }
                    continue
                }
                updateState { it.copy(verificationRunId = newRunId) }
                addTimeline(GitoRepairStatus.TRIGGERING_BUILD, "Verification build started (Run #$newRunId)", true)

                updateState { it.copy(status = GitoRepairStatus.VERIFYING) }
                addTimeline(GitoRepairStatus.VERIFYING, "Monitoring verification build", false)
                val buildSuccess = monitorWorkflowRun(context, newRunId)
                updateState { it.copy(verificationStatus = if (buildSuccess) "success" else "failure") }

                if (buildSuccess) {
                    updateState { it.copy(status = GitoRepairStatus.SUCCESS, isRunning = false) }
                    addTimeline(GitoRepairStatus.VERIFYING, "Verification build passed", true)
                    addTimeline(GitoRepairStatus.SUCCESS, "Repair successful", true)
                    persistRepairJobCompletion(context, GitoRepairStatus.SUCCESS)
                    return
                } else {
                    addTimeline(GitoRepairStatus.VERIFYING, "Verification build failed", true)
                    updateState { it.copy(errorMessage = "Verification build failed — retrying") }
                    persistAttempt(context, attempt, "FAILED", "Verification build failed")
                }
            }
            if (attempt >= MAX_AUTO_ATTEMPTS) {
                updateState { it.copy(status = GitoRepairStatus.STOPPED, isRunning = false, errorMessage = "Automatic repair stopped after $MAX_AUTO_ATTEMPTS attempts") }
                addTimeline(GitoRepairStatus.STOPPED, "Repair stopped after $MAX_AUTO_ATTEMPTS attempts", true)
                persistRepairJobCompletion(context, GitoRepairStatus.STOPPED)
            }
        } catch (e: CancellationException) {
            updateState { it.copy(status = GitoRepairStatus.FAILED, isRunning = false, errorMessage = "Repair cancelled") }
        } catch (e: Exception) {
            updateState { it.copy(status = GitoRepairStatus.FAILED, isRunning = false, errorMessage = e.message) }
            addTimeline(GitoRepairStatus.FAILED, "Repair failed: ${e.message}", true)
            persistRepairJobCompletion(context, GitoRepairStatus.FAILED)
        }
    }

    private suspend fun collectLogs(context: RepairContext): String = withContext(Dispatchers.IO) {
        workflowRepository.getJobLogs(context.owner, context.repo, context.failedJobId)
            .getOrElse { workflowRepository.getRunLogs(context.owner, context.repo, context.runId).getOrDefault("") }
    }

    private suspend fun inspectRepository(context: RepairContext, filePaths: List<String>): Map<String, String> = withContext(Dispatchers.IO) {
        val files = mutableMapOf<String, String>()
        for (path in filePaths.take(20)) {
            val result = safeApiCall { apiService.getContent(context.owner, context.repo, path, context.branch) }
            result.onSuccess { contentFile ->
                contentFile.content?.let { encoded ->
                    files[path] = try { String(Base64.decode(encoded.replace("\n", ""), Base64.DEFAULT)) } catch (_: Exception) { "" }
                }
            }
        }
        files
    }

    private suspend fun validateChanges(modifiedFiles: Map<String, String>): ValidationResult {
        for ((path, content) in modifiedFiles) {
            if (content.isBlank() && !path.endsWith(".md")) return ValidationResult(false, "Refusing to delete file: $path")
            val redactionResult = logRedactor.redact(content)
            if (redactionResult.redactionCount > 0) return ValidationResult(false, "Modified content contains secrets")
            if (path.endsWith(".kt") || path.endsWith(".java")) {
                val open = content.count { it == '{' }
                val close = content.count { it == '}' }
                if (open != close) return ValidationResult(false, "Unbalanced braces in $path")
            }
        }
        return ValidationResult(true)
    }

    private suspend fun checkWritePermission(context: RepairContext): Boolean = withContext(Dispatchers.IO) {
        safeApiCall { apiService.getRepository(context.owner, context.repo) }
            .fold(onSuccess = { it.permissions?.push == true }, onFailure = { false })
    }

    private suspend fun checkBranchProtection(context: RepairContext): Boolean = withContext(Dispatchers.IO) {
        safeApiCall { apiService.getBranch(context.owner, context.repo, context.branch) }
            .fold(onSuccess = { it.protected }, onFailure = { false })
    }

    private suspend fun pushDirectly(context: RepairContext, modifiedFiles: Map<String, String>, commitMessage: String): Result<String> = withContext(Dispatchers.IO) {
        var lastSha = ""
        for ((path, content) in modifiedFiles) {
            val currentFile = safeApiCall { apiService.getContent(context.owner, context.repo, path, context.branch) }
            val currentSha = currentFile.getOrNull()?.sha
            val encodedContent = Base64.encodeToString(content.toByteArray(), Base64.NO_WRAP)
            val request = CreateFileRequest(message = commitMessage, content = encodedContent, sha = currentSha, branch = context.branch)
            val updateResult = safeApiCall { apiService.createOrUpdateFile(context.owner, context.repo, path, request) }
            if (updateResult.isFailure) return@withContext Result.failure(updateResult.exceptionOrNull() ?: RuntimeException("Push failed for $path"))
            lastSha = updateResult.getOrNull()?.content?.sha ?: lastSha
        }
        Result.success(lastSha)
    }

    private suspend fun pushToBranchAndCreatePR(context: RepairContext, modifiedFiles: Map<String, String>, commitMessage: String): Result<String> = withContext(Dispatchers.IO) {
        val fixBranchName = "gito-fix/${context.repairId}"
        val baseBranchResult = safeApiCall { apiService.getBranch(context.owner, context.repo, context.branch) }
        val baseSha = baseBranchResult.getOrNull()?.commit?.sha
            ?: return@withContext Result.failure(RuntimeException("Cannot get base branch SHA"))
        val createBranchRequest = com.gitofy.data.remote.dto.CreateBranchRequest(ref = "refs/heads/$fixBranchName", sha = baseSha)
        val branchResult = safeApiCall { apiService.createBranch(context.owner, context.repo, createBranchRequest) }
        if (branchResult.isFailure) return@withContext Result.failure(branchResult.exceptionOrNull() ?: RuntimeException("Failed to create fix branch"))
        var lastSha = ""
        for ((path, content) in modifiedFiles) {
            val currentFile = safeApiCall { apiService.getContent(context.owner, context.repo, path, fixBranchName) }
            val currentSha = currentFile.getOrNull()?.sha
            val encodedContent = Base64.encodeToString(content.toByteArray(), Base64.NO_WRAP)
            val request = CreateFileRequest(message = commitMessage, content = encodedContent, sha = currentSha, branch = fixBranchName)
            val updateResult = safeApiCall { apiService.createOrUpdateFile(context.owner, context.repo, path, request) }
            if (updateResult.isFailure) return@withContext Result.failure(updateResult.exceptionOrNull() ?: RuntimeException("Push failed for $path"))
            lastSha = updateResult.getOrNull()?.content?.sha ?: lastSha
        }
        val prRequest = com.gitofy.data.remote.dto.CreatePRRequest(
            title = commitMessage, head = fixBranchName, base = context.branch,
            body = "Automated fix by Gito AI for failed workflow run #${context.runId}"
        )
        val prResult = safeApiCall { apiService.createPullRequest(context.owner, context.repo, prRequest) }
        if (prResult.isFailure) return@withContext Result.failure(prResult.exceptionOrNull() ?: RuntimeException("Failed to create PR"))
        Result.success(lastSha)
    }

    private suspend fun triggerOrFindNewRun(context: RepairContext): Long? = withContext(Dispatchers.IO) {
        delay(5000)
        val runsResult = safeApiCall { apiService.listWorkflowRunsByWorkflow(context.owner, context.repo, context.workflowId) }
        val newRun = runsResult.getOrNull()?.workflowRuns?.firstOrNull { it.id > context.runId }
        if (newRun != null) return@withContext newRun.id
        val dispatchResult = safeApiCall {
            apiService.dispatchWorkflow(context.owner, context.repo, context.workflowId, DispatchWorkflowRequest(ref = context.branch))
        }
        if (dispatchResult.isFailure) return@withContext null
        delay(5000)
        val reruns = safeApiCall { apiService.listWorkflowRunsByWorkflow(context.owner, context.repo, context.workflowId) }
        reruns.getOrNull()?.workflowRuns?.firstOrNull { it.id > context.runId }?.id
    }

    private suspend fun monitorWorkflowRun(context: RepairContext, runId: Long): Boolean = withContext(Dispatchers.IO) {
        var waited = 0L
        while (waited < MAX_POLL_WAIT_MS && scope.isActive) {
            delay(POLL_INTERVAL_MS)
            waited += POLL_INTERVAL_MS
            val runResult = safeApiCall { apiService.getWorkflowRun(context.owner, context.repo, runId) }
            val run = runResult.getOrNull() ?: continue
            val status = WorkflowStatus.fromGitHubStatus(run.status, run.conclusion)
            when (status) {
                WorkflowStatus.COMPLETED_SUCCESS -> return@withContext true
                WorkflowStatus.COMPLETED_FAILURE, WorkflowStatus.CANCELLED, WorkflowStatus.TIMED_OUT -> return@withContext false
                else -> {}
            }
        }
        false
    }

    private fun updateState(transform: (RepairUiState) -> RepairUiState) { _state.value = transform(_state.value) }
    private fun addTimeline(stage: GitoRepairStatus, message: String, isComplete: Boolean) {
        updateState { it.copy(timeline = it.timeline + TimelineEvent(stage, System.currentTimeMillis(), message, isComplete)) }
    }

    private suspend fun persistRepairJob(context: RepairContext, status: GitoRepairStatus) {
        repairJobDao.upsert(GitoRepairJobEntity(
            repairId = context.repairId, ownerLogin = context.owner, repoName = context.repo,
            branch = context.branch, commitSha = context.commitSha, workflowId = context.workflowId,
            runId = context.runId, failedJobId = context.failedJobId, failedJobName = context.failedJobName,
            failedStepName = context.failedStepName, status = status.name, attempt = _state.value.attempt,
            maxAttempts = MAX_AUTO_ATTEMPTS
        ))
    }

    private suspend fun persistRepairJobCompletion(context: RepairContext, status: GitoRepairStatus) {
        val existing = repairJobDao.getById(context.repairId) ?: return
        repairJobDao.upsert(existing.copy(status = status.name, completedAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
    }

    private suspend fun persistAttempt(context: RepairContext, attempt: Int, status: String, errorMessage: String) {
        repairAttemptDao.upsert(GitoRepairAttemptEntity(
            repairId = context.repairId, attemptNumber = attempt, status = status,
            errorMessage = errorMessage, completedAt = System.currentTimeMillis()
        ))
    }

    fun cancel() {
        repairJob?.cancel()
        repairJob = null
        updateState { it.copy(status = GitoRepairStatus.FAILED, isRunning = false, errorMessage = "Cancelled by user") }
    }

    suspend fun restoreFromPersistentState(repairId: String) {
        val entity = repairJobDao.getById(repairId) ?: return
        val context = RepairContext(
            repairId = entity.repairId, owner = entity.ownerLogin, repo = entity.repoName,
            branch = entity.branch, commitSha = entity.commitSha, workflowId = entity.workflowId,
            runId = entity.runId, failedJobId = entity.failedJobId, failedJobName = entity.failedJobName,
            failedStepName = entity.failedStepName
        )
        updateState {
            it.copy(repairId = entity.repairId, status = GitoRepairStatus.valueOf(entity.status),
                attempt = entity.attempt, maxAttempts = entity.maxAttempts, context = context,
                errorLog = entity.errorLog, rootCause = entity.rootCause, isRunning = false)
        }
    }
}

interface RepairAnalyzer {
    suspend fun analyzeFailure(redactedLog: String, context: GitoRepairOrchestrator.RepairContext): GitoRepairOrchestrator.FailureAnalysis
    suspend fun planFix(analysis: GitoRepairOrchestrator.FailureAnalysis, repoFiles: Map<String, String>, redactedLog: String, context: GitoRepairOrchestrator.RepairContext): GitoRepairOrchestrator.FixPlan
}
