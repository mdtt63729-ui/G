package com.gitofy.feature.createproject

import androidx.lifecycle.ViewModel
import android.content.Context
import androidx.work.WorkManager
import androidx.lifecycle.viewModelScope
import com.gitofy.data.local.dao.OperationDao
import com.gitofy.core.notification.NotificationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import javax.inject.Inject

// PRD PHASE 24: Upload state model — centralized sealed state
data class UploadStepInfo(
    val name: String,
    val displayName: String,
    val status: StepStatus,
    val startedAt: Long = 0L,
    val completedAt: Long = 0L
)

enum class StepStatus { PENDING, RUNNING, SUCCESS, FAILED, CANCELLED }

data class UploadProgressUiState(
    val projectName: String = "",
    val repoName: String = "",
    val ownerLogin: String = "",
    val progress: Float = 0f,
    val progressPercent: Int = 0,
    val currentStage: String = "Preparing",
    val currentStageDisplayName: String = "Preparing project",
    val isComplete: Boolean = false,
    val isFailed: Boolean = false,
    val isCancelled: Boolean = false,
    val isNoChanges: Boolean = false,
    val error: String? = null,
    val owner: String = "",
    val repo: String = "",
    // PRD PHASE 3: Progress header data
    val bytesUploaded: Long = 0L,
    val totalBytes: Long = 0L,
    val filesCompleted: Int = 0,
    val totalFiles: Int = 0,
    val currentFile: String = "",
    val commitSha: String = "",
    // PRD PHASE 8: Step timeline
    val steps: List<UploadStepInfo> = emptyList(),
    // PRD PHASE 10: Elapsed time
    val operationStartedAt: Long = 0L,
    val operationCompletedAt: Long = 0L,
    val stageStartedAt: Long = 0L,
    val uploadSpeed: Double = 0.0, // bytes/sec
    val etaSeconds: Long = -1L // -1 = unknown
)

@HiltViewModel
class UploadProgressViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val operationDao: OperationDao,
    private val notificationManager: NotificationManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(UploadProgressUiState())
    val uiState = _uiState.asStateFlow()
    private var monitorJob: Job? = null
    private var lastTerminalStatus: String? = null

    fun startMonitoring(operationId: String) {
        monitorJob?.cancel()
        monitorJob = viewModelScope.launch {
            operationDao.observeOperation(operationId).collectLatest { operation ->
                operation ?: return@collectLatest

                // PRD PHASE 8: Parse step history JSON
                val steps = parseStepHistory(operation.stepHistoryJson)

                // PRD PHASE 4: Calculate real progress percentage (integer, no jumps)
                val realProgress = if (operation.totalBytes > 0) {
                    operation.bytesUploaded.toFloat() / operation.totalBytes.toFloat()
                } else {
                    operation.progress
                }
                val percent = (realProgress * 100).toInt().coerceIn(0, 100)

                // PRD PHASE 12: Calculate upload speed (bytes/sec)
                val elapsedMs = if (operation.stageStartedAt > 0) {
                    System.currentTimeMillis() - operation.stageStartedAt
                } else 0L
                val networkPush = operation.currentFile.startsWith("Pushing Git objects")
                val speed = if (!networkPush && elapsedMs > 0 && operation.bytesUploaded > 0) {
                    operation.bytesUploaded.toDouble() / (elapsedMs / 1000.0)
                } else 0.0

                // Source-byte ETA is meaningful during indexing only. libgit2's
                // network callback has a different byte domain, so don't display
                // a misleading ETA during the Git pack transfer.
                val eta = if (!networkPush && speed > 0 && operation.totalBytes > operation.bytesUploaded) {
                    ((operation.totalBytes - operation.bytesUploaded).toDouble() / speed).toLong()
                } else -1L

                val terminalStatus = operation.status
                if (terminalStatus != lastTerminalStatus) {
                    when (terminalStatus) {
                        "COMPLETED" -> notificationManager.showUploadComplete(operation.repoName, eventKey = operation.id)
                        "FAILED" -> notificationManager.showUploadFailed(operation.repoName, operation.errorMessage ?: "Unknown error", eventKey = operation.id)
                    }
                    if (terminalStatus == "COMPLETED" || terminalStatus == "FAILED") {
                        lastTerminalStatus = terminalStatus
                    }
                }

                _uiState.update {
                    it.copy(
                        projectName = operation.repoName,
                        repoName = operation.repoName,
                        ownerLogin = operation.ownerLogin,
                        progress = realProgress,
                        progressPercent = percent,
                        currentStage = operation.currentStage,
                        currentStageDisplayName = friendlyStage(operation.currentStage),
                        isComplete = operation.status == "COMPLETED",
                        isFailed = operation.status == "FAILED",
                        isCancelled = operation.status == "CANCELLED",
                        isNoChanges = operation.status == "NO_CHANGES",
                        error = operation.errorMessage,
                        owner = operation.ownerLogin,
                        repo = operation.repoName,
                        bytesUploaded = operation.bytesUploaded,
                        totalBytes = operation.totalBytes,
                        filesCompleted = operation.filesCompleted,
                        totalFiles = operation.totalFiles,
                        currentFile = operation.currentFile,
                        commitSha = operation.commitSha,
                        steps = steps,
                        operationStartedAt = operation.operationStartedAt,
                        operationCompletedAt = operation.operationCompletedAt,
                        stageStartedAt = operation.stageStartedAt,
                        uploadSpeed = speed,
                        etaSeconds = eta
                    )
                }
            }
        }
    }

    fun cancel(operationId: String) {
        WorkManager.getInstance(context).cancelAllWorkByTag("upload_$operationId")
        viewModelScope.launch {
            operationDao.getById(operationId)?.let { operation ->
                operationDao.upsert(
                    operation.copy(
                        status = "CANCELLED",
                        currentStage = "CANCELLED",
                        operationCompletedAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
        monitorJob?.cancel()
    }

    private fun parseStepHistory(json: String): List<UploadStepInfo> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<UploadStepInfo>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    UploadStepInfo(
                        name = obj.getString("name"),
                        displayName = obj.getString("displayName"),
                        status = StepStatus.valueOf(obj.getString("status")),
                        startedAt = obj.optLong("startedAt", 0L),
                        completedAt = obj.optLong("completedAt", 0L)
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun friendlyStage(stage: String): String = when (stage.uppercase()) {
        "CREATED", "QUEUED", "PREPARING_PROJECT" -> "Preparing project"
        "CHECKING_REPOSITORY" -> "Checking repository"
        "COMPARING_FILES" -> "Comparing files"
        "PREPARING_CHANGES" -> "Preparing changes"
        "UPLOADING_FILES" -> "Uploading files"
        "CREATING_COMMIT" -> "Creating commit"
        "VERIFYING_UPLOAD" -> "Verifying upload"
        "COMPLETED" -> "Completed"
        "NO_CHANGES" -> "No changes detected"
        "FAILED" -> "Upload failed"
        "CANCELLED" -> "Upload cancelled"
        else -> stage.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
    }
}
