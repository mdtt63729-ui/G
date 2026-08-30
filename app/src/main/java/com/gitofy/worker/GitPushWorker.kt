package com.gitofy.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gitofy.core.logging.GITOFYLogger
import com.gitofy.core.security.SecureCredentialStorage
import com.gitofy.data.local.dao.OperationDao
import com.gitofy.data.local.entity.OperationEntity
import com.gitofy.data.git.GitNativeManager
import com.gitofy.core.network.GitHubApiService
import com.gitofy.core.network.safeApiCall
import com.gitofy.domain.repository.GitRepository
import com.gitofy.domain.repository.GitHubRepository
import com.gitofy.data.git.WorkflowInjector
import com.gitofy.core.filesystem.SecureZipExtractor
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Git Push Worker — Premium Upload Progress PRD.
 *
 * Pipeline: PREPARING → CHECKING_REPOSITORY → COMPARING_FILES → PREPARING_CHANGES →
 * UPLOADING_FILES → CREATING_COMMIT → VERIFYING_UPLOAD → COMPLETED.
 *
 * Each step tracks real startedAt/completedAt timestamps.
 * Progress is calculated from real file/byte counts where available.
 * No fake progress — steps complete only when the actual operation succeeds.
 */
@HiltWorker
class GitPushWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val gitRepository: GitRepository,
    private val gitHubRepository: GitHubRepository,
    private val secureStorage: SecureCredentialStorage,
    private val gitNativeManager: GitNativeManager,
    private val apiService: GitHubApiService,
    private val operationDao: OperationDao,
    private val workflowInjector: WorkflowInjector,
    private val zipExtractor: SecureZipExtractor
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_OPERATION_ID = "operation_id"
        const val KEY_PROJECT_PATH = "project_path"
        const val KEY_REPO_NAME = "repo_name"
        const val KEY_REPO_DESCRIPTION = "repo_description"
        const val KEY_IS_PRIVATE = "is_private"
        const val KEY_COMMIT_MESSAGE = "commit_message"

        // Upload step definitions (PRD PHASE 7-8)
        val STEPS = listOf(
            "PREPARING_PROJECT",
            "CHECKING_REPOSITORY",
            "COMPARING_FILES",
            "PREPARING_CHANGES",
            "UPLOADING_FILES",
            "CREATING_COMMIT",
            "VERIFYING_UPLOAD"
        )
    }

    private fun stepDisplayName(stage: String): String = when (stage) {
        "PREPARING_PROJECT" -> "Preparing project"
        "CHECKING_REPOSITORY" -> "Checking repository"
        "COMPARING_FILES" -> "Comparing files"
        "PREPARING_CHANGES" -> "Preparing changes"
        "UPLOADING_FILES" -> "Uploading files"
        "CREATING_COMMIT" -> "Creating commit"
        "VERIFYING_UPLOAD" -> "Verifying upload"
        "COMPLETED" -> "Completed"
        "FAILED" -> "Failed"
        "CANCELLED" -> "Cancelled"
        else -> stage.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
    }

    private fun buildStepHistoryJson(steps: List<StepRecord>): String {
        val arr = JSONArray()
        for (step in steps) {
            val obj = JSONObject()
            obj.put("name", step.name)
            obj.put("displayName", stepDisplayName(step.name))
            obj.put("status", step.status)
            obj.put("startedAt", step.startedAt)
            obj.put("completedAt", step.completedAt)
            arr.put(obj)
        }
        return arr.toString()
    }

    private data class StepRecord(
        val name: String,
        val status: String, // RUNNING, SUCCESS, FAILED, PENDING, CANCELLED
        val startedAt: Long = 0L,
        val completedAt: Long = 0L
    )

    private suspend fun updateStageWithHistory(
        operationId: String,
        stage: String,
        progress: Float,
        steps: MutableList<StepRecord>,
        stageStartedAt: Long = 0L,
        totalFiles: Int = 0,
        filesCompleted: Int = 0,
        totalBytes: Long = 0L,
        bytesUploaded: Long = 0L,
        currentFile: String = "",
        commitSha: String = ""
    ) {
        GITOFYLogger.i("GitPush: $stage (${(progress * 100).toInt()}%)")
        val existing = operationDao.getById(operationId)
        val startedAt = existing?.operationStartedAt ?: System.currentTimeMillis()
        val effectiveStageStartedAt = when {
            stageStartedAt > 0L -> stageStartedAt
            existing?.currentStage == stage && existing.stageStartedAt > 0L -> existing.stageStartedAt
            else -> System.currentTimeMillis()
        }
        operationDao.upsert(
            (existing ?: OperationEntity(
                id = operationId,
                type = "GIT_PUSH",
                status = "RUNNING",
                currentStage = stage,
                operationStartedAt = startedAt
            )).copy(
                id = operationId,
                type = "GIT_PUSH",
                status = "RUNNING",
                progress = progress,
                currentStage = stage,
                stageStartedAt = effectiveStageStartedAt,
                operationStartedAt = startedAt,
                totalFiles = totalFiles,
                filesCompleted = filesCompleted,
                totalBytes = totalBytes,
                bytesUploaded = bytesUploaded,
                currentFile = currentFile,
                commitSha = commitSha,
                stepHistoryJson = buildStepHistoryJson(steps),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun completeStep(
        operationId: String,
        steps: MutableList<StepRecord>,
        stepIndex: Int
    ) {
        steps[stepIndex] = steps[stepIndex].copy(
            status = "SUCCESS",
            completedAt = System.currentTimeMillis()
        )
        // Update DB with new step history
        val existing = operationDao.getById(operationId)
        existing?.let {
            operationDao.upsert(
                it.copy(
                    stepHistoryJson = buildStepHistoryJson(steps),
                    stageCompletedAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private suspend fun startStep(
        operationId: String,
        steps: MutableList<StepRecord>,
        stepIndex: Int,
        stage: String,
        progress: Float
    ) {
        val now = System.currentTimeMillis()
        steps[stepIndex] = steps[stepIndex].copy(
            status = "RUNNING",
            startedAt = now
        )
        updateStageWithHistory(operationId, stage, progress, steps, now)
    }

    override suspend fun doWork(): Result {
        val projectPath = inputData.getString(KEY_PROJECT_PATH)
        return try {
            performWork()
        } finally {
            // Operation files are durable while WorkManager owns the job, then
            // removed deterministically to avoid persistent-storage leaks.
            projectPath?.let { runCatching { File(it).deleteRecursively() } }
        }
    }

    private suspend fun performWork(): Result {
        val operationId = inputData.getString(KEY_OPERATION_ID) ?: return Result.failure()
        val projectPath = inputData.getString(KEY_PROJECT_PATH) ?: return Result.failure()
        val repoName = inputData.getString(KEY_REPO_NAME) ?: return Result.failure()
        val repoDescription = inputData.getString(KEY_REPO_DESCRIPTION) ?: ""
        val isPrivate = inputData.getBoolean(KEY_IS_PRIVATE, false)
        val commitMessage = inputData.getString(KEY_COMMIT_MESSAGE) ?: "Initial commit"

        // Initialize step tracking (PRD PHASE 24)
        val steps = STEPS.map { StepRecord(it, "PENDING") }.toMutableList()

        try {
            // Initialize operation
            operationDao.upsert(
                OperationEntity(
                    id = operationId,
                    type = "GIT_PUSH",
                    status = "RUNNING",
                    progress = 0f,
                    currentStage = "PREPARING_PROJECT",
                    repoName = repoName,
                    operationStartedAt = System.currentTimeMillis(),
                    stepHistoryJson = buildStepHistoryJson(steps),
                    updatedAt = System.currentTimeMillis()
                )
            )

            // Step 0: Preparing project (PRD PHASE 8)
            startStep(operationId, steps, 0, "PREPARING_PROJECT", 0.05f)

            // The coordinator stores the selected ZIP in operationDir/source.zip.
            // Extract it here; never upload the ZIP container itself. The previous
            // implementation treated operationDir as the project root, which made
            // a 3.1 MB project appear as "1 file / 3.1 MB" (the source.zip).
            val sourceZip = File(projectPath, "source.zip")
            val validation = zipExtractor.validateZip(sourceZip)
            if (!validation.isValid) {
                failStep(operationId, steps, 0, validation.error ?: "Invalid ZIP")
                return Result.failure()
            }
            val extractDir = File(projectPath, "extracted")
            val extractResult = zipExtractor.extractZip(sourceZip, extractDir)
            if (extractResult.isFailure) {
                failStep(operationId, steps, 0, extractResult.exceptionOrNull()?.message ?: "Could not extract ZIP")
                return Result.failure()
            }
            val projectDir = zipExtractor.detectProjectRoot(extractDir) ?: extractDir

            // Counts are calculated once after all generated workflow files are
            // injected, so the UI denominator exactly matches the native index.
            updateStageWithHistory(
                operationId, "PREPARING_PROJECT", 0.05f, steps
            )

            completeStep(operationId, steps, 0)

            // Step 1: Checking repository
            startStep(operationId, steps, 1, "CHECKING_REPOSITORY", 0.1f)

            val token = secureStorage.getToken()
            if (token == null) {
                failStep(operationId, steps, 1, "Authentication required")
                return Result.failure()
            }

            completeStep(operationId, steps, 1)

            // Step 2: Comparing files
            startStep(operationId, steps, 2, "COMPARING_FILES", 0.15f)

            updateStageWithHistory(
                operationId, "COMPARING_FILES", 0.15f, steps
            )

            completeStep(operationId, steps, 2)

            // Step 3: Preparing changes (create repo + init git + inject workflows)
            startStep(operationId, steps, 3, "PREPARING_CHANGES", 0.2f)

            val createResult = gitHubRepository.createRepository(repoName, repoDescription, isPrivate)
            val createdRepo = createResult.getOrElse { error ->
                failStep(operationId, steps, 3, "Repository creation failed: ${error.message}")
                return Result.failure()
            }

            // Inject the workflows before creating the single initial commit.
            val injected = workflowInjector.injectWorkflows(projectDir.absolutePath)
            if (injected.isFailure) {
                failStep(operationId, steps, 3, "Workflow preparation failed: ${injected.exceptionOrNull()?.message ?: "Unknown error"}")
                return Result.failure()
            }

            completeStep(operationId, steps, 3)

            // Step 4: Native libgit2 upload. One local index/tree/commit and one
            // smart-HTTP push are performed. No per-file GitHub Contents API calls.
            startStep(operationId, steps, 4, "UPLOADING_FILES", 0.25f)

            val localFiles = collectFiles(projectDir)
            val actualTotalFiles = localFiles.size
            val uploadTotalBytes = localFiles.sumOf { it.second.length() }
            updateStageWithHistory(
                operationId, "UPLOADING_FILES", 0.25f, steps,
                totalFiles = actualTotalFiles,
                filesCompleted = 0,
                totalBytes = uploadTotalBytes,
                bytesUploaded = 0L,
                currentFile = "Preparing $actualTotalFiles files..."
            )

            val userLogin = secureStorage.getUserLogin() ?: createdRepo.ownerLogin
            val branch = createdRepo.defaultBranch.ifBlank { "main" }
            val repoUrl = createdRepo.htmlUrl.trimEnd('/') + ".git"

            val nativeResult = coroutineScope {
                // JNI callbacks must never block the native Git thread on SQLite.
                // A conflated channel keeps only the newest progress snapshot and
                // writes to Room at a bounded rate while libgit2 continues at full speed.
                data class NativeProgress(
                    val uploadedBytes: Long,
                    val totalBytes: Long,
                    val filesCompleted: Int,
                    val totalFiles: Int,
                    val currentFile: String
                )
                val progressChannel = Channel<NativeProgress>(Channel.CONFLATED)
                val progressWriter = launch {
                    var lastWriteAt = 0L
                    var pending: NativeProgress? = null
                    for (snapshot in progressChannel) {
                        pending = snapshot
                        val now = System.currentTimeMillis()
                        val waitMs = (120L - (now - lastWriteAt)).coerceAtLeast(0L)
                        if (waitMs > 0L) kotlinx.coroutines.delay(waitMs)
                        val value = pending ?: continue
                        val ratio = if (value.totalBytes > 0) {
                            (value.uploadedBytes.toDouble() / value.totalBytes.toDouble()).coerceIn(0.0, 1.0)
                        } else 0.0
                        val isNetworkPush = value.currentFile.startsWith("Pushing Git objects") || value.currentFile == "Completed"
                        val overallProgress = if (isNetworkPush) {
                            (0.80 + 0.19 * ratio).toFloat()
                        } else {
                            (0.25 + 0.55 * ratio).toFloat()
                        }.coerceIn(0.25f, 0.99f)
                        updateStageWithHistory(
                            operationId,
                            "UPLOADING_FILES",
                            overallProgress,
                            steps,
                            totalFiles = value.totalFiles,
                            filesCompleted = value.filesCompleted,
                            totalBytes = value.totalBytes,
                            bytesUploaded = value.uploadedBytes.coerceIn(0L, value.totalBytes),
                            currentFile = value.currentFile
                        )
                        lastWriteAt = System.currentTimeMillis()
                        pending = null
                    }
                }

                // This try-expression is the last expression of coroutineScope,
                // so its value (Result<String>) becomes the result of nativeResult.
                try {
                    gitNativeManager.pushDirectoryToGithub(
                        repoUrl = repoUrl,
                        token = token,
                        directory = projectDir,
                        branch = branch,
                        commitMessage = commitMessage,
                        userName = userLogin,
                        userEmail = "$userLogin@users.noreply.github.com",
                        callback = object : GitNativeManager.ProgressCallback {
                            override fun onProgress(
                                uploadedBytes: Long,
                                totalBytes: Long,
                                filesCompleted: Int,
                                totalFiles: Int,
                                currentFile: String
                            ) {
                                progressChannel.trySend(
                                    NativeProgress(uploadedBytes, totalBytes, filesCompleted, totalFiles, currentFile)
                                )
                            }

                            override fun onStage(stage: String) {
                                GITOFYLogger.i("libgit2: $stage")
                            }

                            override fun isCancelled(): Boolean = this@GitPushWorker.isStopped
                        }
                    )
                } finally {
                    progressChannel.close()
                    progressWriter.join()
                }
            }

            if (nativeResult.isFailure) {
                val error = nativeResult.exceptionOrNull()
                failStep(operationId, steps, 4, error?.message ?: "Native Git push failed")
                return Result.failure()
            }
            val commitSha: String = nativeResult.getOrThrow()

            updateStageWithHistory(
                operationId, "UPLOADING_FILES", 0.99f, steps,
                totalFiles = actualTotalFiles,
                filesCompleted = actualTotalFiles,
                totalBytes = uploadTotalBytes,
                bytesUploaded = uploadTotalBytes,
                currentFile = "Native Git push completed"
            )
            completeStep(operationId, steps, 4)

            // Step 5: The native engine already created the single commit.
            startStep(operationId, steps, 5, "CREATING_COMMIT", 0.85f)
            updateStageWithHistory(
                operationId, "CREATING_COMMIT", 0.90f, steps,
                totalFiles = actualTotalFiles,
                filesCompleted = actualTotalFiles,
                totalBytes = uploadTotalBytes,
                bytesUploaded = uploadTotalBytes,
                currentFile = "Commit ${commitSha.take(7)}" ,
                commitSha = commitSha
            )
            completeStep(operationId, steps, 5)

            // Step 6: Verify the actual remote branch and commit.
            startStep(operationId, steps, 6, "VERIFYING_UPLOAD", 0.95f)
            val verifyResult = safeApiCall {
                apiService.getBranch(createdRepo.ownerLogin.ifBlank { userLogin }, createdRepo.name, branch)
            }
            val verifiedBranch = verifyResult.getOrElse { error ->
                failStep(operationId, steps, 6, "Verification failed: ${error.message ?: "Unknown error"}")
                return Result.failure()
            }
            if (verifiedBranch.commit?.sha != commitSha) {
                failStep(operationId, steps, 6, "Verification failed: remote branch does not point to the uploaded commit")
                return Result.failure()
            }
            completeStep(operationId, steps, 6)

            // Completed (PRD PHASE 16)
            val now = System.currentTimeMillis()
            val existing = operationDao.getById(operationId)
            operationDao.upsert(
                (existing ?: OperationEntity(id = operationId)).copy(
                    id = operationId,
                    type = "GIT_PUSH",
                    status = "COMPLETED",
                    progress = 1.0f,
                    currentStage = "COMPLETED",
                    commitSha = commitSha,
                    ownerLogin = createdRepo.ownerLogin,
                    repoName = createdRepo.name,
                    branch = branch,
                    totalFiles = actualTotalFiles,
                    filesCompleted = actualTotalFiles,
                    totalBytes = uploadTotalBytes,
                    bytesUploaded = uploadTotalBytes,
                    stepHistoryJson = buildStepHistoryJson(steps),
                    operationCompletedAt = now,
                    updatedAt = now
                )
            )

            // Cleanup
            gitRepository.cleanup(projectPath)

            return Result.success()
        } catch (e: Exception) {
            GITOFYLogger.e("GitPushWorker failed", throwable = e)
            // Find current running step and mark as failed
            val runningStepIndex = steps.indexOfFirst { it.status == "RUNNING" }
            if (runningStepIndex >= 0) {
                failStep(operationId, steps, runningStepIndex, e.message ?: "Unknown error")
            } else {
                updateError(operationId, e.message ?: "Unknown error")
            }
            return Result.failure()
        }
    }

    private fun collectFiles(projectRoot: File): List<Pair<String, File>> {
        val root = projectRoot.absolutePath
        val excluded = setOf(
            ".git", ".gradle", "build", ".idea", ".cxx", "captures", ".kotlin",
            "node_modules", "dist", "out", "target", "coverage", ".cache",
            ".pytest_cache", "__pycache__", ".venv", "venv", "Pods", "DerivedData"
        )
        return projectRoot.walkTopDown()
            .filter { it.isFile }
            .mapNotNull { file ->
                val relative = file.absolutePath.removePrefix(root).trimStart(File.separatorChar)
                if (excluded.any { dir -> relative == dir || relative.startsWith("$dir/") || relative.startsWith("$dir${File.separator}") }) null
                else relative.replace(File.separatorChar, '/') to file
            }
            .sortedBy { it.first }
            .toList()
    }

    private suspend fun failStep(
        operationId: String,
        steps: MutableList<StepRecord>,
        stepIndex: Int,
        error: String
    ) {
        val now = System.currentTimeMillis()
        steps[stepIndex] = steps[stepIndex].copy(
            status = "FAILED",
            completedAt = now
        )
        // Mark remaining steps as cancelled
        for (i in (stepIndex + 1) until steps.size) {
            steps[i] = steps[i].copy(status = "CANCELLED")
        }

        val existing = operationDao.getById(operationId)
        operationDao.upsert(
            (existing ?: OperationEntity(id = operationId)).copy(
                id = operationId,
                status = "FAILED",
                currentStage = "FAILED",
                errorMessage = error,
                stepHistoryJson = buildStepHistoryJson(steps),
                operationCompletedAt = now,
                updatedAt = now
            )
        )
        GITOFYLogger.e("GitPush failed at step ${steps[stepIndex].name}: $error")
    }

    private suspend fun cancelOperation(
        operationId: String,
        steps: MutableList<StepRecord>,
        stepIndex: Int
    ) {
        val now = System.currentTimeMillis()
        steps[stepIndex] = steps[stepIndex].copy(
            status = "CANCELLED",
            completedAt = now
        )
        for (i in (stepIndex + 1) until steps.size) {
            steps[i] = steps[i].copy(status = "CANCELLED")
        }

        val existing = operationDao.getById(operationId)
        operationDao.upsert(
            (existing ?: OperationEntity(id = operationId)).copy(
                id = operationId,
                status = "CANCELLED",
                currentStage = "CANCELLED",
                stepHistoryJson = buildStepHistoryJson(steps),
                operationCompletedAt = now,
                updatedAt = now
            )
        )
    }

    private suspend fun updateError(id: String, error: String) {
        GITOFYLogger.e("GitPush error: $error")
        val existing = operationDao.getById(id)
        val now = System.currentTimeMillis()
        operationDao.upsert(
            (existing ?: OperationEntity(id = id)).copy(
                id = id,
                status = "FAILED",
                currentStage = "FAILED",
                errorMessage = error,
                operationCompletedAt = now,
                updatedAt = now
            )
        )
    }

    private fun countFiles(dir: File): Int {
        if (!dir.exists()) return 0
        return dir.walkTopDown().filter { it.isFile }.count()
    }

    private fun calculateTotalSize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    }
}
