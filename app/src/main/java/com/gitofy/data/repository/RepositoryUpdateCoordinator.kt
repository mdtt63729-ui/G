package com.gitofy.data.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.gitofy.data.local.dao.OperationDao
import com.gitofy.data.local.entity.OperationEntity
import com.gitofy.worker.RepositoryUpdateWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Durable update coordinator. The upload/update lifecycle is owned by WorkManager and Room. */
@Singleton
class RepositoryUpdateCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val operationDao: OperationDao
) {
    suspend fun startUpdate(
        zipInputStream: InputStream,
        ownerLogin: String,
        repoName: String,
        commitMessage: String
    ): String {
        val operationId = UUID.randomUUID().toString()
        val operationDir = File(context.filesDir, "gito_operations/$operationId")
        operationDir.mkdirs()
        val sourceZip = File(operationDir, "source.zip")

        // FIX: this copy used to run inline on whatever dispatcher the
        // caller's viewModelScope.launch defaults to — Dispatchers.Main —
        // meaning the actual file copy (blocking I/O) happened on the UI
        // thread. Besides risking ANRs on larger projects, this is also the
        // last point before the worker takes over, so we now verify the
        // copy landed intact before ever enqueueing the worker: if it
        // didn't, we fail immediately with a clear message instead of
        // letting the worker discover a 0-byte file later as a generic
        // "ZIP file is empty".
        val copiedBytes = withContext(Dispatchers.IO) {
            zipInputStream.use { input -> sourceZip.outputStream().use { output -> input.copyTo(output) } }
            sourceZip.length()
        }
        if (copiedBytes <= 0L) {
            operationDir.deleteRecursively()
            throw IllegalStateException("Could not read the selected ZIP file.")
        }

        val now = System.currentTimeMillis()
        operationDao.upsert(
            OperationEntity(
                id = operationId,
                type = "GIT_UPDATE",
                status = "QUEUED",
                currentStage = "QUEUED",
                ownerLogin = ownerLogin,
                repoName = repoName,
                createdAt = now,
                updatedAt = now
            )
        )

        val data = Data.Builder()
            .putString(RepositoryUpdateWorker.KEY_OPERATION_ID, operationId)
            .putString(RepositoryUpdateWorker.KEY_PROJECT_PATH, operationDir.absolutePath)
            .putString(RepositoryUpdateWorker.KEY_OWNER_LOGIN, ownerLogin)
            .putString(RepositoryUpdateWorker.KEY_REPO_NAME, repoName)
            .putString(RepositoryUpdateWorker.KEY_COMMIT_MESSAGE, commitMessage)
            .build()

        val request = OneTimeWorkRequestBuilder<RepositoryUpdateWorker>()
            .setInputData(data)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag("update_$operationId")
            .build()

        WorkManager.getInstance(context).enqueue(request)
        return operationId
    }

    fun observe(operationId: String): Flow<OperationEntity?> = operationDao.observeOperation(operationId)

    fun cancel(operationId: String) {
        WorkManager.getInstance(context).cancelAllWorkByTag("update_$operationId")
    }
}
