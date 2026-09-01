package com.gitofy.feature.repositories.update

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitofy.core.filesystem.SecureZipExtractor
import com.gitofy.core.notification.NotificationManager
import com.gitofy.data.local.dao.OperationDao
import com.gitofy.data.repository.RepositoryUpdateCoordinator
import com.gitofy.data.repository.RepositorySyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
    val error: String? = null,
    val operationId: String? = null,
    val isPreparing: Boolean = false,
    val cachedZipPath: String? = null
)

@HiltViewModel
class UpdateRepositoryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val updateCoordinator: RepositoryUpdateCoordinator,
    private val operationDao: OperationDao,
    private val zipExtractor: SecureZipExtractor,
    private val notificationManager: NotificationManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(UpdateRepositoryUiState())
    val uiState = _uiState.asStateFlow()


    fun init(owner: String, repo: String) {
        _uiState.update {
            it.copy(ownerLogin = owner, repoName = repo, commitMessage = "Update repository")
        }
        // Resume the latest durable update after navigation, rotation, or process recreation.
        viewModelScope.launch {
            operationDao.observeActive().collect { operations ->
                val active = operations.firstOrNull {
                    it.type == "GIT_UPDATE" && it.ownerLogin == owner && it.repoName == repo
                }
                if (active != null && _uiState.value.operationId != active.id) {
                    _uiState.update { it.copy(operationId = active.id) }
                    observeOperation(active.id)
                }
            }
        }
    }

    // PRD §8: ZIP selection with actual byte size and immediate validation.
    fun onZipSelected(uri: Uri, fileName: String, sizeBytes: Long?) {
        _uiState.update {
            it.copy(
                zipUri = uri,
                zipFileName = fileName,
                zipSizeBytes = sizeBytes,
                error = null,
                isPreparing = true,
                isComplete = false,
                isFailed = false,
                isNoChanges = false,
                isCancelled = false,
                commitMessage = "Update repository from $fileName"
            )
        }
        viewModelScope.launch {
            // FIX: The previous validation copied the ZIP to a temp file and
            // deleted it afterwards, but startUpdate() then re-opened the URI
            // from scratch. On some content providers (Google Drive, SAF,
            // certain file managers) the second openInputStream() returns an
            // empty or truncated stream — the persistable URI permission is
            // not always honored — which caused the worker to see a 0-byte
            // source.zip and fail with "ZIP file is empty".
            //
            // Now: copy the ZIP to a persistent cache file ONCE here, validate
            // it, and keep the path so startUpdate() can read from the local
            // file instead of re-opening the content URI.
            //
            // FIX (still showing "ZIP file is empty" for real, non-empty
            // files): this copy used to run on viewModelScope's default
            // Main dispatcher. Several document providers (cloud-backed
            // "Downloads"/"Drive" entries, some file-manager apps) return a
            // stream that reads as 0 bytes when opened and drained
            // immediately after the picker returns — the bytes aren't
            // actually available yet. Moving the copy to Dispatchers.IO and,
            // if the very first attempt reads 0 bytes, retrying the read
            // once after a short delay fixes exactly that: a genuinely
            // non-empty file no longer gets misreported as empty because of
            // a transient provider timing issue.
            val cachedZip = File(context.cacheDir, "gitofy_update_source_${System.nanoTime()}.zip")
            val validation = withContext(Dispatchers.IO) {
                runCatching {
                    try {
                        fun copyOnce(): Long {
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                cachedZip.outputStream().use { output -> input.copyTo(output) }
                            } ?: error("Could not read the selected ZIP file.")
                            return cachedZip.length()
                        }

                        var copiedBytes = copyOnce()
                        if (copiedBytes == 0L) {
                            // Transient provider read — give it one more try
                            // before concluding the source is truly empty.
                            delay(300)
                            copiedBytes = copyOnce()
                        }

                        // If the content provider reported 0 or null size, use the
                        // actual copied file size so the UI shows the real value
                        // instead of "Unknown size" or a wrong "0 B".
                        val realSize = copiedBytes
                        if (realSize > 0 && (sizeBytes == null || sizeBytes <= 0)) {
                            _uiState.update { it.copy(zipSizeBytes = realSize) }
                        }

                        zipExtractor.validateZip(cachedZip)
                    } finally {
                        // Keep the cached file for startUpdate; clean up only if
                        // validation failed.
                    }
                }.getOrElse { SecureZipExtractor.ZipValidationResult(false, error = it.message ?: "Could not validate ZIP") }
            }

            if (!validation.isValid) {
                cachedZip.delete()
                _uiState.update {
                    it.copy(isPreparing = false, error = validation.error)
                }
            } else {
                _uiState.update {
                    it.copy(isPreparing = false, cachedZipPath = cachedZip.absolutePath)
                }
            }
        }
    }

    fun removeZip() {
        _uiState.value.cachedZipPath?.let { runCatching { File(it).delete() } }
        _uiState.update {
            it.copy(
                zipUri = null,
                zipFileName = "",
                zipSizeBytes = null,
                cachedZipPath = null,
                error = null
            )
        }
    }

    // PRD §9-17: Start a durable WorkManager update.
    fun startUpdate() {
        val state = _uiState.value
        if (state.isSyncing || state.isPreparing || state.error != null) return

        // FIX: Use the locally-cached ZIP file (created during onZipSelected)
        // instead of re-opening the content URI. The content URI may not be
        // re-openable from a background coroutine / WorkManager context,
        // which caused the worker to see a 0-byte source.zip.
        val cachedPath = state.cachedZipPath
        val cachedFile = cachedPath?.let { File(it) }?.takeIf { it.exists() && it.length() > 0 }
        if (cachedFile == null) {
            _uiState.update { it.copy(isSyncing = false, isFailed = true, error = "Please select a valid ZIP file first.") }
            return
        }

        _uiState.update {
            it.copy(isSyncing = true, isComplete = false, isFailed = false, isNoChanges = false, isCancelled = false, error = null)
        }

        viewModelScope.launch {
            try {
                val operationId = cachedFile.inputStream().use { input ->
                    updateCoordinator.startUpdate(input, state.ownerLogin, state.repoName, state.commitMessage)
                }
                _uiState.update { it.copy(operationId = operationId) }
                observeOperation(operationId)
            } catch (t: Throwable) {
                _uiState.update { it.copy(isSyncing = false, isFailed = true, error = t.message ?: "Could not start update") }
            }
        }
    }

    private var operationMonitor: Job? = null
    private val notifiedTerminalOperations = mutableSetOf<String>()

    private fun observeOperation(operationId: String) {
        operationMonitor?.cancel()
        operationMonitor = viewModelScope.launch {
            operationDao.observeOperation(operationId).collect { op ->
                op ?: return@collect
                val terminal = op.status == "COMPLETED" || op.status == "FAILED" || op.status == "NO_CHANGES" || op.status == "CANCELLED"
                _uiState.update {
                    it.copy(
                        operationId = operationId,
                        isSyncing = !terminal,
                        isComplete = op.status == "COMPLETED",
                        isFailed = op.status == "FAILED",
                        isNoChanges = op.status == "NO_CHANGES",
                        isCancelled = op.status == "CANCELLED",
                        progress = op.progress,
                        currentStage = op.currentStage,
                        currentStageDisplayName = friendlyStageName(op.currentStage),
                        currentItem = op.currentFile,
                        completedItems = op.filesCompleted,
                        totalItems = op.totalFiles,
                        bytesUploaded = op.bytesUploaded,
                        totalBytes = op.totalBytes,
                        commitSha = op.commitSha,
                        error = op.errorMessage
                    )
                }
                if (notifiedTerminalOperations.add("$operationId:${op.status}")) {
                    if (op.status == "COMPLETED") {
                        notificationManager.showUpdateComplete(op.repoName, eventKey = operationId)
                    } else if (op.status == "FAILED") {
                        notificationManager.showUpdateFailed(op.repoName, op.errorMessage ?: "Update failed", eventKey = operationId)
                    }
                }
            }
        }
    }

    // PRD §29: Retry from failed state
    fun retry() {
        _uiState.update { it.copy(isFailed = false, isComplete = false, isNoChanges = false, isCancelled = false, error = null, progress = 0f) }
        startUpdate()
    }

    // PRD §49: Cancel durable operation.
    fun cancel() {
        _uiState.value.operationId?.let(updateCoordinator::cancel)
        _uiState.update { it.copy(isSyncing = false, isCancelled = true) }
    }

    // The worker persists `currentStage` as the SyncStage enum name (e.g. "UPLOADING")
    // or, on terminal transitions, as a status string (e.g. "COMPLETED"). We accept
    // the raw String here and resolve both shapes so the UI always gets a friendly label.
    private fun friendlyStageName(stage: String): String = when (stage) {
        RepositorySyncEngine.SyncStage.PREPARING.name -> "Preparing project"
        RepositorySyncEngine.SyncStage.CHECKING_REPOSITORY.name -> "Checking repository"
        RepositorySyncEngine.SyncStage.COMPARING.name -> "Comparing files"
        RepositorySyncEngine.SyncStage.PREPARING_CHANGES.name -> "Preparing changes"
        RepositorySyncEngine.SyncStage.UPLOADING.name -> "Uploading files"
        RepositorySyncEngine.SyncStage.CREATING_COMMIT.name -> "Creating commit"
        RepositorySyncEngine.SyncStage.PUSHING.name -> "Pushing"
        RepositorySyncEngine.SyncStage.VERIFYING.name -> "Verifying upload"
        RepositorySyncEngine.SyncStage.SUCCESS.name, "COMPLETED" -> "Completed"
        RepositorySyncEngine.SyncStage.NO_CHANGES.name -> "No changes detected"
        RepositorySyncEngine.SyncStage.FAILED.name -> "Update failed"
        RepositorySyncEngine.SyncStage.CANCELLED.name -> "Update cancelled"
        else -> stage.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    override fun onCleared() {
        operationMonitor?.cancel()
        super.onCleared()
    }
}
