package com.gitofy.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gitofy.core.filesystem.SecureZipExtractor
import com.gitofy.core.logging.GITOFYLogger
import com.gitofy.data.local.dao.OperationDao
import com.gitofy.data.local.entity.OperationEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

/**
 * ZIP Extraction Worker — PRD 20.
 * Responsibilities: Read ZIP, Validate, Extract, Report progress, Clean up on failure.
 * PRD 8.3: Protect against Zip Slip, path traversal, malicious links, resource exhaustion.
 * PRD 44: Stream file access, don't load entire ZIP into RAM.
 */
@HiltWorker
class ZipExtractionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val zipExtractor: SecureZipExtractor,
    private val operationDao: OperationDao
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val operationId = inputData.getString(KEY_OPERATION_ID) ?: return Result.failure()
        val zipPath = inputData.getString(KEY_ZIP_PATH) ?: return Result.failure()

        updateOperation(operationId, "EXTRACTING", 0.1f)

        val zipFile = File(zipPath)
        if (!zipFile.exists()) {
            updateOperationError(operationId, "ZIP file not found")
            return Result.failure()
        }

        // Validate — PRD 15.2
        val validation = zipExtractor.validateZip(zipFile)
        if (!validation.isValid) {
            updateOperationError(operationId, validation.error ?: "Invalid ZIP")
            return Result.failure()
        }

        // Extract — PRD 15.3
        val targetDir = File(applicationContext.cacheDir, "extraction_$operationId")
        val result = zipExtractor.extractZip(zipFile, targetDir) { progress ->
            GITOFYLogger.d("Extracting: ${progress.filesExtracted}/${progress.totalFiles}")
            // Update progress periodically
        }

        return result.fold(
            onSuccess = { dir ->
                val projectRoot = zipExtractor.detectProjectRoot(dir)
                if (projectRoot == null) {
                    updateOperationError(operationId, "Could not detect project root")
                    zipExtractor.cleanup(dir.absolutePath)
                    return Result.failure()
                }
                updateOperation(operationId, "EXTRACTED", 0.3f)
                Result.success()
            },
            onFailure = { error ->
                updateOperationError(operationId, error.message ?: "Extraction failed")
                zipExtractor.cleanup(targetDir.absolutePath)
                Result.failure()
            }
        )
    }

    private suspend fun updateOperation(id: String, stage: String, progress: Float) {
        operationDao.upsert(
            OperationEntity(
                id = id,
                type = "ZIP_EXTRACTION",
                status = "RUNNING",
                progress = progress,
                currentStage = stage
            )
        )
    }

    private suspend fun updateOperationError(id: String, error: String) {
        operationDao.upsert(
            OperationEntity(
                id = id,
                type = "ZIP_EXTRACTION",
                status = "FAILED",
                progress = 0f,
                currentStage = "FAILED",
                errorMessage = error
            )
        )
        GITOFYLogger.e("ZIP extraction failed: $error")
    }

    companion object {
        const val KEY_OPERATION_ID = "operation_id"
        const val KEY_ZIP_PATH = "zip_path"
    }
}
