package com.gitofy.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import com.gitofy.core.logging.GITOFYLogger
import com.gitofy.domain.repository.WorkflowRepository
import com.gitofy.domain.repository.GitHubRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * Workflow Sync Worker — PRD 20.
 * Refresh active workflow states. Respect network constraints. Avoid unnecessary polling.
 * PRD 23: Adaptive polling — not aggressive.
 */
@HiltWorker
class WorkflowSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val workflowRepository: WorkflowRepository,
    private val gitHubRepository: GitHubRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            GITOFYLogger.d("WorkflowSync: refreshing repositories and workflows")
            val repositoriesResult = gitHubRepository.refreshRepositories()
            if (repositoriesResult.isFailure) return Result.retry()
            val repositories = gitHubRepository.observeRepositories().first()
            for (repository in repositories) {
                workflowRepository.refreshWorkflows(repository.ownerLogin, repository.name)
                workflowRepository.refreshRuns(repository.ownerLogin, repository.name)
            }
            Result.success()
        } catch (e: Exception) {
            GITOFYLogger.w("WorkflowSync failed: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        fun buildRequest(intervalMinutes: Long = 15) =
            PeriodicWorkRequestBuilder<WorkflowSyncWorker>(intervalMinutes, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
    }
}
