package com.gitofy.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gitofy.core.logging.GITOFYLogger
import com.gitofy.domain.repository.ArtifactRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Artifact Download Worker — PRD 20.
 * Download artifact, report progress, store securely, notify user.
 * PRD 72: Artifact security — associate with repo/workflow/run/artifact ID.
 */
@HiltWorker
class ArtifactDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val artifactRepository: ArtifactRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val owner = inputData.getString(KEY_OWNER) ?: return Result.failure()
        val repo = inputData.getString(KEY_REPO) ?: return Result.failure()
        val artifactId = inputData.getLong(KEY_ARTIFACT_ID, -1)
        val artifactName = inputData.getString(KEY_ARTIFACT_NAME) ?: return Result.failure()

        if (artifactId < 0) return Result.failure()

        return try {
            GITOFYLogger.i("Downloading artifact: $artifactName")
            val result = artifactRepository.downloadArtifact(owner, repo, artifactId, artifactName)
            result.fold(
                onSuccess = { path ->
                    GITOFYLogger.i("Artifact downloaded to: $path")
                    Result.success()
                },
                onFailure = { error ->
                    GITOFYLogger.e("Artifact download failed: ${error.message}")
                    Result.retry()
                }
            )
        } catch (e: Exception) {
            GITOFYLogger.e("Artifact download error", throwable = e)
            Result.retry()
        }
    }

    companion object {
        const val KEY_OWNER = "owner"
        const val KEY_REPO = "repo"
        const val KEY_ARTIFACT_ID = "artifact_id"
        const val KEY_ARTIFACT_NAME = "artifact_name"
    }
}
