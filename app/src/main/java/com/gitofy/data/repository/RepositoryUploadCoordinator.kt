package com.gitofy.data.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.gitofy.core.logging.GITOFYLogger
import com.gitofy.data.local.dao.OperationDao
import com.gitofy.data.local.entity.OperationEntity
import com.gitofy.worker.GitPushWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PRD §8: RepositoryUploadCoordinator.
 *
 * Responsibilities:
 * - Create operation record
 * - Copy ZIP from URI to application-controlled temp file (PRD §6)
 * - Create WorkRequest with constraints
 * - Enqueue Worker
 * - Return operation ID
 *
 * WorkManager ensures operation survives process death, app close,
 * configuration change, and screen recreation (PRD §8).
 */
@Singleton
class RepositoryUploadCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val operationDao: OperationDao
) {
    /**
     * Start a repository upload operation.
     * PRD §6: ZIP is first copied to filesDir/gito_operations/{operationId}/source.zip
     * PRD §8: Worker is enqueued with NetworkType.CONNECTED constraint
     */
    suspend fun startUpload(
        zipInputStream: InputStream,
        repoName: String,
        repoDescription: String,
        isPrivate: Boolean,
        commitMessage: String
    ): String {
        val operationId = UUID.randomUUID().toString()

        // PRD §6: Copy ZIP to application-controlled temp file
        val operationDir = File(context.filesDir, "gito_operations/$operationId")
        operationDir.mkdirs()
        val sourceZip = File(operationDir, "source.zip")

        zipInputStream.use { input ->
            sourceZip.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        GITOFYLogger.i("UploadCoordinator: ZIP copied to ${sourceZip.absolutePath}")

        // Create operation record
        operationDao.upsert(
            OperationEntity(
                id = operationId,
                type = "GIT_PUSH",
                status = "CREATED",
                progress = 0f,
                currentStage = "CREATED",
                repoName = repoName,
                ownerLogin = "",
                errorMessage = null
            )
        )

        // PRD §8: Create WorkRequest with constraints
        val inputData = Data.Builder()
            .putString(GitPushWorker.KEY_OPERATION_ID, operationId)
            .putString(GitPushWorker.KEY_PROJECT_PATH, operationDir.absolutePath)
            .putString(GitPushWorker.KEY_REPO_NAME, repoName)
            .putString(GitPushWorker.KEY_REPO_DESCRIPTION, repoDescription)
            .putBoolean(GitPushWorker.KEY_IS_PRIVATE, isPrivate)
            .putString(GitPushWorker.KEY_COMMIT_MESSAGE, commitMessage)
            .build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<GitPushWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .addTag("upload_$operationId")
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)

        GITOFYLogger.i("UploadCoordinator: Worker enqueued for operation $operationId")
        return operationId
    }

    fun observeOperation(operationId: String): Flow<OperationEntity?> {
        return operationDao.observeOperation(operationId)
    }
}
