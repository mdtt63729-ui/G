package com.gitofy.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gitofy.core.logging.GITOFYLogger
import com.gitofy.data.local.dao.OperationDao
import com.gitofy.data.local.entity.OperationEntity
import com.gitofy.data.repository.RepositorySyncEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.File

/** Executes repository updates durably and mirrors every real engine progress event into Room. */
@HiltWorker
class RepositoryUpdateWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncEngine: RepositorySyncEngine,
    private val operationDao: OperationDao
) : CoroutineWorker(appContext, params) {
    companion object {
        const val KEY_OPERATION_ID = "operation_id"
        const val KEY_PROJECT_PATH = "project_path"
        const val KEY_OWNER_LOGIN = "owner_login"
        const val KEY_REPO_NAME = "repo_name"
        const val KEY_COMMIT_MESSAGE = "commit_message"

        /**
         * Stage 1 pre-flight check on the source ZIP, run before any pipeline
         * work begins. Pure/side-effect-free (only reads File metadata) so it
         * can be unit tested without Android or WorkManager infrastructure.
         *
         * Returns a user-facing diagnostic message, or null if the ZIP is
         * present and non-empty and the worker may proceed.
         */
        fun validateSourceZip(sourceZip: File): String? = when {
            !sourceZip.isFile -> "Source ZIP is missing"
            sourceZip.length() <= 0L -> "Source ZIP is empty"
            else -> null
        }
    }

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_OPERATION_ID) ?: return Result.failure()
        val path = inputData.getString(KEY_PROJECT_PATH) ?: return Result.failure()
        val owner = inputData.getString(KEY_OWNER_LOGIN) ?: return Result.failure()
        val repo = inputData.getString(KEY_REPO_NAME) ?: return Result.failure()
        val message = inputData.getString(KEY_COMMIT_MESSAGE) ?: "Update repository"
        val dir = File(path)

        try {
            operationDao.getById(id)?.let { operationDao.upsert(it.copy(status = "RUNNING", currentStage = "PREPARING", operationStartedAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis())) }
            syncEngine.resetProgress()

            // Stage 1 (PRD §5/§15): validate the source ZIP written by the
            // coordinator BEFORE handing it to the sync engine. Fail fast
            // with a specific diagnostic instead of a generic error.
            val sourceZip = File(dir, "source.zip")
            validateSourceZip(sourceZip)?.let { diagnostic -> return fail(id, diagnostic) }

            val result = coroutineScope {
                val mirror = launch {
                    syncEngine.progressFlow.collect { p ->
                        val existing = operationDao.getById(id) ?: return@collect
                        operationDao.upsert(existing.copy(
                            status = when (p.stage) {
                                RepositorySyncEngine.SyncStage.SUCCESS -> "COMPLETED"
                                RepositorySyncEngine.SyncStage.NO_CHANGES -> "NO_CHANGES"
                                RepositorySyncEngine.SyncStage.FAILED -> "FAILED"
                                RepositorySyncEngine.SyncStage.CANCELLED -> "CANCELLED"
                                else -> "RUNNING"
                            },
                            progress = p.progress.coerceIn(0f, 1f),
                            currentStage = p.stage.name,
                            currentFile = p.currentItem,
                            filesCompleted = p.completedItems,
                            totalFiles = p.totalItems,
                            bytesUploaded = p.bytesUploaded,
                            totalBytes = p.totalBytes,
                            errorMessage = p.error,
                            updatedAt = System.currentTimeMillis()
                        ))
                    }
                }
                // PRD §5/§6: pass the File directly — source.zip is never
                // re-opened as a stream/output here. RepositorySyncEngine
                // treats it as an immutable input from this point on.
                val value = syncEngine.updateRepository(sourceZip, owner, repo, message, dir)
                mirror.cancel()
                mirror.join()
                value
            }

            return when (result) {
                is RepositorySyncEngine.SyncResult.Updated -> {
                    updateTerminal(id, "COMPLETED", result.commitSha, null)
                    Result.success()
                }
                RepositorySyncEngine.SyncResult.NoChanges -> {
                    updateTerminal(id, "NO_CHANGES", "", null)
                    Result.success()
                }
                is RepositorySyncEngine.SyncResult.Failed -> {
                    updateTerminal(id, "FAILED", "", result.error.message)
                    Result.failure()
                }
                else -> fail(id, "Unexpected update result")
            }
        } catch (t: Throwable) {
            GITOFYLogger.e("RepositoryUpdateWorker failed", throwable = t)
            return fail(id, t.message ?: "Update failed")
        } finally {
            runCatching { dir.deleteRecursively() }
        }
    }

    private suspend fun updateTerminal(id: String, status: String, sha: String, error: String?) {
        val now = System.currentTimeMillis()
        operationDao.getById(id)?.let { operationDao.upsert(it.copy(status = status, currentStage = status, progress = if (status == "COMPLETED" || status == "NO_CHANGES") 1f else it.progress, commitSha = sha, errorMessage = error, operationCompletedAt = now, updatedAt = now)) }
    }

    private suspend fun fail(id: String, message: String): Result {
        updateTerminal(id, "FAILED", "", message)
        return Result.failure()
    }
}
