package com.gitofy.data.repository

import com.gitofy.core.logging.GITOFYLogger
import com.gitofy.domain.model.JobType
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PRD §34: RepositoryOperationManager — Central component that wraps
 * JobManager and RepositorySyncEngine.
 *
 * Architecture (PRD §2):
 *   Operation Engine → Job Manager → Job Event Stream → Real-time State → UI
 *
 * PRD §22: Navigation Independence — operations run via this manager,
 * NOT tied to any screen's lifecycle. Job state persists in Room.
 *
 * PRD §21: Background Job Support — user can navigate away and the
 * job continues. Home shows an active job indicator.
 */
@Singleton
class RepositoryOperationManager @Inject constructor(
    private val jobManager: JobManager,
    private val syncEngine: RepositorySyncEngine
) {
    // PRD §7: Standard step definitions for each operation type
    private val createRepoSteps = listOf(
        "SELECTING_PROJECT" to "Selecting project",
        "READING_ZIP" to "Reading ZIP",
        "EXTRACTING_PROJECT" to "Extracting project",
        "VALIDATING_PROJECT" to "Validating project",
        "CREATING_REPOSITORY" to "Create GitHub repository",
        "UPLOADING_FILES" to "Upload files",
        "CREATING_COMMIT" to "Create initial commit",
        "PUSHING_CHANGES" to "Push changes",
        "VERIFYING_REMOTE" to "Verify remote state",
        "COMPLETED" to "Completed"
    )

    private val updateRepoSteps = listOf(
        "SELECTING_PROJECT" to "Selecting project",
        "READING_ZIP" to "Reading ZIP",
        "EXTRACTING_PROJECT" to "Extracting project",
        "VALIDATING_PROJECT" to "Validating project",
        "RESOLVING_REPOSITORY" to "Resolving repository",
        "READING_REMOTE_TREE" to "Reading remote tree",
        "COMPARING_FILES" to "Comparing files",
        "PREPARING_CHANGES" to "Preparing changes",
        "UPLOADING_FILES" to "Uploading files",
        "CREATING_COMMIT" to "Creating commit",
        "PUSHING_CHANGES" to "Pushing changes",
        "VERIFYING_REMOTE" to "Verifying remote state",
        "COMPLETED" to "Completed"
    )

    /**
     * PRD §29: Create Repository with real job tracking.
     * Returns the jobId for UI observation.
     */
    suspend fun createRepository(
        zipInputStream: InputStream,
        repoName: String,
        repoDescription: String,
        isPrivate: Boolean,
        commitMessage: String,
        operationDir: File
    ): String {
        // PRD §5: Create job
        val jobId = jobManager.createJob(
            operationType = JobType.CREATE_REPOSITORY,
            repoName = repoName
        )

        // Define steps
        jobManager.defineSteps(jobId, createRepoSteps)
        jobManager.startJob(jobId)

        // Execute the real sync operation while mirroring the engine's real
        // progress into the persistent Jobs data source. The screen lifecycle
        // is never involved, so leaving/re-entering Jobs does not interrupt it.
        val result = coroutineScope {
            val progressJob = launch { mirrorSyncProgress(jobId, JobType.CREATE_REPOSITORY) }
            try {
                syncEngine.createRepository(
                    zipInputStream = zipInputStream,
                    repoName = repoName,
                    repoDescription = repoDescription,
                    isPrivate = isPrivate,
                    commitMessage = commitMessage,
                    operationDir = operationDir
                )
            } finally {
                progressJob.cancel()
                progressJob.join()
            }
        }

        // Map sync result to job outcome
        when (result) {
            is RepositorySyncEngine.SyncResult.Created -> {
                jobManager.completeJob(jobId)
            }
            is RepositorySyncEngine.SyncResult.Failed -> {
                jobManager.failJob(jobId, result.error.message ?: "Unknown error")
            }
            else -> {
                jobManager.failJob(jobId, "Unexpected result for create operation")
            }
        }

        return jobId
    }

    /**
     * PRD §7-9: Update Repository with real job tracking.
     * Returns the jobId for UI observation.
     */
    suspend fun updateRepository(
        sourceZip: File,
        ownerLogin: String,
        repoName: String,
        commitMessage: String,
        operationDir: File,
        chatMessageId: String = ""
    ): Pair<String, RepositorySyncEngine.SyncResult> {
        // PRD §5: Create job
        val jobId = jobManager.createJob(
            operationType = JobType.UPDATE_REPOSITORY,
            ownerLogin = ownerLogin,
            repoName = repoName,
            chatMessageId = chatMessageId
        )

        // Define steps
        jobManager.defineSteps(jobId, updateRepoSteps)
        jobManager.startJob(jobId)

        // Execute the real sync operation while mirroring the engine's real
        // progress into the persistent Jobs data source.
        //
        // `sourceZip` is passed straight through as a File — it must already
        // exist on disk (written once by the caller/coordinator) and is
        // treated as immutable from here on. It is never re-wrapped in a
        // stream and copied back onto its own path.
        val result = coroutineScope {
            val progressJob = launch { mirrorSyncProgress(jobId, JobType.UPDATE_REPOSITORY) }
            try {
                syncEngine.updateRepository(
                    sourceZip = sourceZip,
                    ownerLogin = ownerLogin,
                    repoName = repoName,
                    commitMessage = commitMessage,
                    operationDir = operationDir
                )
            } finally {
                progressJob.cancel()
                progressJob.join()
            }
        }

        // Map sync result to job outcome
        when (result) {
            is RepositorySyncEngine.SyncResult.Updated -> jobManager.completeJob(jobId)
            is RepositorySyncEngine.SyncResult.NoChanges -> jobManager.completeJobNoChanges(jobId)
            is RepositorySyncEngine.SyncResult.Failed -> jobManager.failJob(jobId, result.error.message ?: "Unknown error")
            else -> jobManager.failJob(jobId, "Unexpected result for update operation")
        }

        return jobId to result
    }

    /**
     * PRD §30: Gito AI modification with real job tracking.
     */
    suspend fun aiRepositoryChange(
        ownerLogin: String,
        repoName: String,
        chatMessageId: String,
        modification: suspend (String) -> RepositorySyncEngine.SyncResult
    ): String {
        val jobId = jobManager.createJob(
            operationType = JobType.AI_REPOSITORY_CHANGE,
            ownerLogin = ownerLogin,
            repoName = repoName,
            chatMessageId = chatMessageId
        )

        val gitoSteps = listOf(
            "RESOLVE_REPOSITORY" to "Resolve repository",
            "INSPECT_REPOSITORY" to "Inspect repository",
            "LOCATE_TARGET" to "Locate target file",
            "APPLY_CHANGE" to "Upload replacement",
            "CREATE_COMMIT" to "Create commit",
            "PUSH_CHANGES" to "Push changes",
            "VERIFY_REMOTE" to "Verify remote state"
        )
        jobManager.defineSteps(jobId, gitoSteps)
        jobManager.startJob(jobId)

        val result = modification(jobId)

        when (result) {
            is RepositorySyncEngine.SyncResult.Updated -> jobManager.completeJob(jobId)
            is RepositorySyncEngine.SyncResult.Created -> jobManager.completeJob(jobId)
            is RepositorySyncEngine.SyncResult.NoChanges -> jobManager.completeJobNoChanges(jobId)
            is RepositorySyncEngine.SyncResult.Failed -> jobManager.failJob(jobId, result.error.message ?: "Unknown error")
        }

        return jobId
    }

    /**
     * Mirrors the RepositorySyncEngine's real StateFlow into ExecJob*.
     * The sync engine is the source of truth for the operation; JobManager is
     * the source of truth for the Jobs screen. No simulated progress is used.
     */
    private suspend fun mirrorSyncProgress(jobId: String, operationType: JobType) {
        var lastStep: String? = null
        syncEngine.progressFlow.collect { progress ->
            val step = when (progress.stage) {
                RepositorySyncEngine.SyncStage.PREPARING -> "SELECTING_PROJECT"
                RepositorySyncEngine.SyncStage.CHECKING_REPOSITORY -> if (operationType == JobType.CREATE_REPOSITORY) "CREATING_REPOSITORY" else "RESOLVING_REPOSITORY"
                RepositorySyncEngine.SyncStage.COMPARING -> "COMPARING_FILES"
                RepositorySyncEngine.SyncStage.PREPARING_CHANGES -> "PREPARING_CHANGES"
                RepositorySyncEngine.SyncStage.UPLOADING -> "UPLOADING_FILES"
                RepositorySyncEngine.SyncStage.CREATING_COMMIT -> "CREATING_COMMIT"
                RepositorySyncEngine.SyncStage.PUSHING -> "PUSHING_CHANGES"
                RepositorySyncEngine.SyncStage.VERIFYING -> "VERIFYING_REMOTE"
                RepositorySyncEngine.SyncStage.SUCCESS,
                RepositorySyncEngine.SyncStage.NO_CHANGES,
                RepositorySyncEngine.SyncStage.FAILED,
                RepositorySyncEngine.SyncStage.CANCELLED -> lastStep
            }

            if (step != null) {
                if (step != lastStep) {
                    lastStep?.let { jobManager.completeStep(jobId, it) }
                    jobManager.startStep(jobId, step)
                    lastStep = step
                }
                jobManager.updateProgress(
                    jobId = jobId,
                    stepName = step,
                    completedItems = progress.completedItems,
                    totalItems = progress.totalItems,
                    currentItem = progress.currentItem,
                    progressOverride = progress.progress
                )
            }
        }
    }

    /**
     * Expose the job manager for direct access (step updates, etc.)
     */
    fun getJobManager(): JobManager = jobManager
}
