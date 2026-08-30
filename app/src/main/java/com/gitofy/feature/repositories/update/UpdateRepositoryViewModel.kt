package com.gitofy.feature.repositories.update

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitofy.core.filesystem.SecureZipExtractor
import com.gitofy.core.notification.NotificationManager
import com.gitofy.data.repository.RepositoryOperationManager
import com.gitofy.data.repository.RepositorySyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * PRD §63: Update state model — sealed states matching the PRD.
 */
data class UpdateRepositoryUiState(
    val ownerLogin: String = "",
    val repoName: String = "",
    val zipUri: Uri? = null,
    val zipFileName: String = "",
    val zipSizeBytes: Long? = null,
    val commitMessage: String = "",

    // Sync state
    val isSyncing: Boolean = false,
    val isComplete: Boolean = false,
    val isFailed: Boolean = false,
    val isNoChanges: Boolean = false,
    val isCancelled: Boolean = false,

    // PRD §64-65: Progress from the engine (single source of truth)
    val progress: Float = 0f,
    val currentStage: String = "",
    val currentStageDisplayName: String = "Preparing",
    val currentItem: String = "",
    val completedItems: Int = 0,
    val totalItems: Int = 0,
    val bytesUploaded: Long = 0L,
    val totalBytes: Long = 0L,

    // PRD §30: Result data
    val addedCount: Int = 0,
    val modifiedCount: Int = 0,
    val deletedCount: Int = 0,
    val unchangedCount: Int = 0,
    val commitSha: String = "",

    // PRD §28: Actual error reason
    val error: String? = null
)

@HiltViewModel
class UpdateRepositoryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val operationManager: RepositoryOperationManager,
    private val syncEngine: RepositorySyncEngine,
    private val zipExtractor: SecureZipExtractor,
    private val notificationManager: NotificationManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(UpdateRepositoryUiState())
    val uiState = _uiState.asStateFlow()

    private var syncJob: Job? = null
    private var operationDir: File? = null

    fun init(owner: String, repo: String) {
        _uiState.update {
            it.copy(
                ownerLogin = owner,
                repoName = repo,
                commitMessage = "Update repository"
            )
        }
    }

    // PRD §8: ZIP selection with actual byte size
    fun onZipSelected(uri: Uri, fileName: String, sizeBytes: Long?) {
        _uiState.update {
            it.copy(
                zipUri = uri,
                zipFileName = fileName,
                zipSizeBytes = sizeBytes,
                error = null,
                commitMessage = "Update repository from $fileName"
            )
        }
    }

    fun removeZip() {
        _uiState.update {
            it.copy(
                zipUri = null,
                zipFileName = "",
                zipSizeBytes = null,
                error = null
            )
        }
    }

    // PRD §9-17: Start the real update operation
    fun startUpdate() {
        val state = _uiState.value
        if (state.zipUri == null || state.isSyncing) return

        _uiState.update {
            it.copy(isSyncing = true, isComplete = false, isFailed = false, isNoChanges = false, error = null)
        }

        syncEngine.resetProgress()

        // Create operation directory
        val opDir = File(context.filesDir, "gito_update_${System.nanoTime()}")
        opDir.mkdirs()
        operationDir = opDir

        syncJob = viewModelScope.launch {
            // Monitor progress from the engine (PRD §64: single source of truth)
            val progressJob = launch {
                syncEngine.progressFlow.collect { progress ->
                    _uiState.update {
                        it.copy(
                            progress = progress.progress,
                            currentStage = progress.stage.name,
                            currentStageDisplayName = friendlyStageName(progress.stage),
                            currentItem = progress.currentItem,
                            completedItems = progress.completedItems,
                            totalItems = progress.totalItems,
                            bytesUploaded = progress.bytesUploaded,
                            totalBytes = progress.totalBytes,
                            error = progress.error
                        )
                    }
                }
            }

            // Execute the real sync operation via RepositoryOperationManager
            // (wraps JobManager for real-time job tracking + RepositorySyncEngine)
            val operationResult = context.contentResolver.openInputStream(state.zipUri)?.use { inputStream ->
                operationManager.updateRepository(
                    zipInputStream = inputStream,
                    ownerLogin = state.ownerLogin,
                    repoName = state.repoName,
                    commitMessage = state.commitMessage,
                    operationDir = opDir
                )
            } ?: ("" to RepositorySyncEngine.SyncResult.Failed(
                com.gitofy.domain.model.GitOFYError.ZipError("Could not read the selected ZIP file.")
            ))

            progressJob.cancel()
            val jobId = operationResult.first
            val result = operationResult.second

            // The RepositoryOperationManager has already updated the job state
            // via JobManager. Read the final sync progress state.
            val finalProgress = syncEngine.progressFlow.value
            when (result) {
                is RepositorySyncEngine.SyncResult.Updated -> {
                    notificationManager.showUpdateComplete(state.repoName, eventKey = "$jobId:${state.repoName}")
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            isComplete = true,
                            addedCount = result.added,
                            modifiedCount = result.modified,
                            deletedCount = result.deleted,
                            unchangedCount = result.unchanged,
                            commitSha = result.commitSha,
                            progress = 1f,
                            currentStageDisplayName = "Completed",
                            completedItems = finalProgress.completedItems,
                            totalItems = finalProgress.totalItems,
                            bytesUploaded = finalProgress.totalBytes,
                            totalBytes = finalProgress.totalBytes
                        )
                    }
                }
                is RepositorySyncEngine.SyncResult.NoChanges -> {
                    _uiState.update {
                        it.copy(isSyncing = false, isNoChanges = true, progress = 1f)
                    }
                }
                is RepositorySyncEngine.SyncResult.Failed -> {
                    notificationManager.showUpdateFailed(state.repoName, result.error.message ?: "Unknown error", eventKey = "$jobId:${state.repoName}")
                    _uiState.update {
                        it.copy(isSyncing = false, isFailed = true, error = result.error.message)
                    }
                }
                else -> {
                    _uiState.update {
                        it.copy(isSyncing = false, isFailed = true, error = "Unexpected update result")
                    }
                }
            }
            cleanup()
        }
    }

    // PRD §29: Retry from failed state
    fun retry() {
        _uiState.update {
            it.copy(isFailed = false, isComplete = false, isNoChanges = false, error = null, progress = 0f)
        }
        startUpdate()
    }

    // PRD §49: Cancel operation
    fun cancel() {
        syncJob?.cancel()
        _uiState.update {
            it.copy(isSyncing = false, isCancelled = true)
        }
        cleanup()
    }

    private fun cleanup() {
        operationDir?.deleteRecursively()
        operationDir = null
    }

    private fun friendlyStageName(stage: RepositorySyncEngine.SyncStage): String = when (stage) {
        RepositorySyncEngine.SyncStage.PREPARING -> "Preparing project"
        RepositorySyncEngine.SyncStage.CHECKING_REPOSITORY -> "Checking repository"
        RepositorySyncEngine.SyncStage.COMPARING -> "Comparing files"
        RepositorySyncEngine.SyncStage.PREPARING_CHANGES -> "Preparing changes"
        RepositorySyncEngine.SyncStage.UPLOADING -> "Uploading files"
        RepositorySyncEngine.SyncStage.CREATING_COMMIT -> "Creating commit"
        RepositorySyncEngine.SyncStage.PUSHING -> "Pushing"
        RepositorySyncEngine.SyncStage.VERIFYING -> "Verifying upload"
        RepositorySyncEngine.SyncStage.SUCCESS -> "Completed"
        RepositorySyncEngine.SyncStage.NO_CHANGES -> "No changes detected"
        RepositorySyncEngine.SyncStage.FAILED -> "Update failed"
        RepositorySyncEngine.SyncStage.CANCELLED -> "Update cancelled"
    }

    override fun onCleared() {
        super.onCleared()
        cleanup()
    }
}
