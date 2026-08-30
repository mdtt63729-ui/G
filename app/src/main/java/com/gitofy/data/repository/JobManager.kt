package com.gitofy.data.repository

import com.gitofy.core.logging.GITOFYLogger
import com.gitofy.data.local.dao.ExecJobDao
import com.gitofy.data.local.dao.ExecJobEventDao
import com.gitofy.data.local.dao.ExecJobStepDao
import com.gitofy.data.local.entity.ExecJobEntity
import com.gitofy.data.local.entity.ExecJobEventEntity
import com.gitofy.data.local.entity.ExecJobStepEntity
import com.gitofy.domain.model.JobEvent
import com.gitofy.domain.model.JobEventType
import com.gitofy.domain.model.JobInfo
import com.gitofy.domain.model.JobStatus
import com.gitofy.domain.model.JobType
import com.gitofy.domain.model.StepStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PRD §34: JobManager — Centralized execution system.
 *
 * Responsibilities (PRD §34):
 * - createJob()
 * - startJob()
 * - emitEvent()
 * - updateProgress()
 * - completeStep()
 * - failJob()
 * - cancelJob()
 * - completeJob()
 *
 * PRD §3: Single Source of Truth — the JobManager is the only component
 * that creates and updates job state. UI never creates its own state.
 *
 * PRD §37: Uses Kotlin SharedFlow for real-time event streaming within
 * the same Android process. Architecture is future-ready for WebSocket
 * or server-sent events via JobEventSource abstraction.
 *
 * PRD §22: Navigation Independence — jobs are NOT tied to any screen's
 * lifecycle. They persist in Room and survive screen changes.
 */
@Singleton
class JobManager @Inject constructor(
    private val execJobDao: ExecJobDao,
    private val execJobStepDao: ExecJobStepDao,
    private val execJobEventDao: ExecJobEventDao
) {
    // PRD §37: Real-time event stream — SharedFlow for in-process events
    private val _eventFlow = MutableSharedFlow<JobEvent>(
        replay = 0,
        extraBufferCapacity = 64
    )
    val eventFlow: SharedFlow<JobEvent> = _eventFlow.asSharedFlow()

    // PRD §21: Background job support — in-memory map for active jobs
    private val activeJobs = ConcurrentHashMap<String, JobInfo>()

    /**
     * PRD §34: createJob — creates a new job record.
     * Returns the jobId for tracking.
     */
    suspend fun createJob(
        operationType: JobType,
        ownerLogin: String = "",
        repoName: String = "",
        chatMessageId: String = ""
    ): String {
        val jobId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val job = JobInfo(
            jobId = jobId,
            operationId = jobId,
            repository = if (ownerLogin.isNotEmpty() && repoName.isNotEmpty()) "$ownerLogin/$repoName" else repoName,
            ownerLogin = ownerLogin,
            repoName = repoName,
            operationType = operationType,
            status = JobStatus.QUEUED,
            startedAt = now,
            updatedAt = now,
            chatMessageId = chatMessageId
        )

        activeJobs[jobId] = job

        // Persist to Room
        execJobDao.upsert(job.toEntity())

        // Emit creation event
        emitEvent(JobEvent(
            jobId = jobId,
            type = JobEventType.JOB_CREATED,
            message = "Job created: ${operationType.name}"
        ))

        GITOFYLogger.i("JobManager: Created job $jobId for ${operationType.name}")
        return jobId
    }

    /**
     * PRD §34: startJob — transitions job from QUEUED to RUNNING.
     */
    suspend fun startJob(jobId: String) {
        val now = System.currentTimeMillis()
        val job = activeJobs[jobId] ?: return
        val updated = job.copy(status = JobStatus.RUNNING, startedAt = now, updatedAt = now)
        activeJobs[jobId] = updated
        execJobDao.upsert(updated.toEntity())

        emitEvent(JobEvent(
            jobId = jobId,
            timestamp = now,
            type = JobEventType.JOB_STARTED,
            message = "Job started"
        ))
    }

    /**
     * PRD §7-9: Define job steps — only real steps that will execute.
     */
    suspend fun defineSteps(jobId: String, steps: List<Pair<String, String>>) {
        steps.forEachIndexed { index, (name, displayName) ->
            val step = ExecJobStepEntity(
                jobId = jobId,
                stepName = name,
                displayName = displayName,
                stepOrder = index,
                status = "PENDING"
            )
            execJobStepDao.upsert(step)
        }
    }

    /**
     * PRD §34: startStep — marks a step as RUNNING.
     */
    suspend fun startStep(jobId: String, stepName: String) {
        val now = System.currentTimeMillis()
        val steps = execJobStepDao.getStepsForJob(jobId)
        val step = steps.find { it.stepName == stepName } ?: return

        execJobStepDao.upsert(step.copy(
            status = "RUNNING",
            startedAt = now
        ))

        // Update job's current step
        val job = activeJobs[jobId]
        if (job != null) {
            val updated = job.copy(currentStep = stepName, updatedAt = now)
            activeJobs[jobId] = updated
            execJobDao.upsert(updated.toEntity())
        }

        emitEvent(JobEvent(
            jobId = jobId,
            timestamp = now,
            type = JobEventType.STEP_STARTED,
            stage = stepName,
            message = step.displayName
        ))
    }

    /**
     * PRD §34: completeStep — marks a step as SUCCESS.
     */
    suspend fun completeStep(jobId: String, stepName: String) {
        val now = System.currentTimeMillis()
        val steps = execJobStepDao.getStepsForJob(jobId)
        val step = steps.find { it.stepName == stepName } ?: return

        execJobStepDao.upsert(step.copy(
            status = "SUCCESS",
            completedAt = now
        ))

        emitEvent(JobEvent(
            jobId = jobId,
            timestamp = now,
            type = JobEventType.STEP_COMPLETED,
            stage = stepName,
            message = step.displayName
        ))
    }

    /**
     * PRD §8: updateProgress — updates real progress from actual operation.
     * PRD §11: NO fake progress — only real counts from the backend.
     */
    suspend fun updateProgress(
        jobId: String,
        stepName: String,
        completedItems: Int,
        totalItems: Int,
        currentItem: String = ""
    ) {
        val now = System.currentTimeMillis()

        // Update step
        val steps = execJobStepDao.getStepsForJob(jobId)
        val step = steps.find { it.stepName == stepName } ?: return
        execJobStepDao.upsert(step.copy(
            completedItems = completedItems,
            totalItems = totalItems
        ))

        // PRD §11: Calculate real progress — no fake percentages
        val progress = if (totalItems > 0) {
            completedItems.toFloat() / totalItems.toFloat()
        } else {
            0f // PRD §12: Indeterminate — no percentage shown
        }

        // Update job
        val job = activeJobs[jobId]
        if (job != null) {
            val updated = job.copy(
                progress = progress,
                completedItems = completedItems,
                totalItems = totalItems,
                updatedAt = now
            )
            activeJobs[jobId] = updated
            execJobDao.upsert(updated.toEntity())
        }

        emitEvent(JobEvent(
            jobId = jobId,
            timestamp = now,
            type = JobEventType.STEP_PROGRESS,
            stage = stepName,
            progress = progress,
            completed = completedItems,
            total = totalItems,
            item = currentItem,
            message = if (totalItems > 0) "$completedItems / $totalItems" else ""
        ))
    }

    /**
     * PRD §14: FILE_STARTED event
     */
    suspend fun fileStarted(jobId: String, fileName: String) {
        emitEvent(JobEvent(
            jobId = jobId,
            type = JobEventType.FILE_STARTED,
            item = fileName,
            message = "Uploading: $fileName"
        ))
    }

    /**
     * PRD §14: FILE_COMPLETED event
     */
    suspend fun fileCompleted(jobId: String, fileName: String) {
        emitEvent(JobEvent(
            jobId = jobId,
            type = JobEventType.FILE_COMPLETED,
            item = fileName,
            message = "Uploaded: $fileName"
        ))
    }

    /**
     * PRD §27: COMMIT_CREATED event
     */
    suspend fun commitCreated(jobId: String, commitSha: String) {
        val job = activeJobs[jobId]
        if (job != null) {
            val updated = job.copy(commitSha = commitSha, updatedAt = System.currentTimeMillis())
            activeJobs[jobId] = updated
            execJobDao.upsert(updated.toEntity())
        }

        emitEvent(JobEvent(
            jobId = jobId,
            type = JobEventType.COMMIT_CREATED,
            message = "Commit created: ${commitSha.take(7)}"
        ))
    }

    /**
     * PRD §26: PUSH_STARTED / PUSH_COMPLETED events
     */
    suspend fun pushStarted(jobId: String) {
        emitEvent(JobEvent(
            jobId = jobId,
            type = JobEventType.PUSH_STARTED,
            message = "Pushing changes..."
        ))
    }

    suspend fun pushCompleted(jobId: String) {
        emitEvent(JobEvent(
            jobId = jobId,
            type = JobEventType.PUSH_COMPLETED,
            message = "Push completed"
        ))
    }

    /**
     * PRD §25: VERIFICATION_STARTED / VERIFICATION_COMPLETED events
     */
    suspend fun verificationStarted(jobId: String) {
        emitEvent(JobEvent(
            jobId = jobId,
            type = JobEventType.VERIFICATION_STARTED,
            message = "Verifying remote state..."
        ))
    }

    suspend fun verificationCompleted(jobId: String) {
        emitEvent(JobEvent(
            jobId = jobId,
            type = JobEventType.VERIFICATION_COMPLETED,
            message = "Verification completed"
        ))
    }

    /**
     * PRD §34: completeJob — marks job as COMPLETED.
     */
    suspend fun completeJob(jobId: String) {
        val now = System.currentTimeMillis()
        val job = activeJobs[jobId] ?: return
        val updated = job.copy(
            status = JobStatus.COMPLETED,
            progress = 1.0f,
            completedAt = now,
            updatedAt = now
        )
        activeJobs.remove(jobId)
        execJobDao.upsert(updated.toEntity())

        emitEvent(JobEvent(
            jobId = jobId,
            timestamp = now,
            type = JobEventType.JOB_COMPLETED,
            progress = 1.0f,
            message = "Job completed"
        ))

        GITOFYLogger.i("JobManager: Completed job $jobId")
    }

    /**
     * PRD §28: completeJobNoChanges — for NoChanges state.
     * Job is COMPLETED (not FAILED).
     */
    suspend fun completeJobNoChanges(jobId: String) {
        val now = System.currentTimeMillis()
        val job = activeJobs[jobId] ?: return
        val updated = job.copy(
            status = JobStatus.COMPLETED,
            progress = 1.0f,
            completedAt = now,
            updatedAt = now,
            error = null
        )
        activeJobs.remove(jobId)
        execJobDao.upsert(updated.toEntity())

        emitEvent(JobEvent(
            jobId = jobId,
            timestamp = now,
            type = JobEventType.JOB_COMPLETED,
            progress = 1.0f,
            message = "No changes detected — repository already up to date"
        ))
    }

    /**
     * PRD §34: failJob — marks job as FAILED with real error.
     */
    suspend fun failJob(jobId: String, error: String) {
        val now = System.currentTimeMillis()
        val job = activeJobs[jobId] ?: return
        val updated = job.copy(
            status = JobStatus.FAILED,
            error = error,
            completedAt = now,
            updatedAt = now
        )
        activeJobs.remove(jobId)
        execJobDao.upsert(updated.toEntity())

        emitEvent(JobEvent(
            jobId = jobId,
            timestamp = now,
            type = JobEventType.JOB_FAILED,
            error = error,
            message = error
        ))

        GITOFYLogger.e("JobManager: Failed job $jobId: $error")
    }

    /**
     * PRD §34: cancelJob — marks job as CANCELLED.
     */
    suspend fun cancelJob(jobId: String) {
        val now = System.currentTimeMillis()
        val job = activeJobs[jobId] ?: return
        val updated = job.copy(
            status = JobStatus.CANCELLED,
            completedAt = now,
            updatedAt = now
        )
        activeJobs.remove(jobId)
        execJobDao.upsert(updated.toEntity())

        emitEvent(JobEvent(
            jobId = jobId,
            timestamp = now,
            type = JobEventType.JOB_CANCELLED,
            message = "Job cancelled"
        ))
    }

    /**
     * PRD §34: emitEvent — internal event emission.
     */
    private suspend fun emitEvent(event: JobEvent) {
        // Persist event to Room
        execJobEventDao.upsert(event.toEntity())
        // Stream to listeners
        _eventFlow.emit(event)
    }

    /**
     * PRD §24: App restart recovery — get active jobs from Room.
     */
    suspend fun recoverActiveJobs(): List<JobInfo> {
        val entities = execJobDao.getActiveJobs()
        return entities.map { it.toDomain() }
    }

    /**
     * PRD §48: Job cleanup — delete old completed jobs (7 days retention).
     */
    suspend fun cleanupOldJobs() {
        val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
        execJobDao.deleteOldJobs(sevenDaysAgo)
        execJobEventDao.deleteOldEvents(sevenDaysAgo)
        GITOFYLogger.i("JobManager: Cleaned up jobs older than 7 days")
    }

    // Mapping helpers
    private fun JobInfo.toEntity() = ExecJobEntity(
        jobId = jobId,
        operationId = operationId,
        repository = repository,
        ownerLogin = ownerLogin,
        repoName = repoName,
        operationType = operationType.name,
        status = status.name,
        progress = progress,
        currentStep = currentStep,
        startedAt = startedAt,
        updatedAt = updatedAt,
        completedAt = completedAt,
        error = error,
        commitSha = commitSha,
        chatMessageId = chatMessageId,
        totalItems = totalItems,
        completedItems = completedItems
    )

    private fun ExecJobEntity.toDomain() = JobInfo(
        jobId = jobId,
        operationId = operationId,
        repository = repository,
        ownerLogin = ownerLogin,
        repoName = repoName,
        operationType = runCatching { JobType.valueOf(operationType) }.getOrDefault(JobType.CREATE_REPOSITORY),
        status = runCatching { JobStatus.valueOf(status) }.getOrDefault(JobStatus.QUEUED),
        progress = progress,
        currentStep = currentStep,
        startedAt = startedAt,
        updatedAt = updatedAt,
        completedAt = completedAt,
        error = error,
        commitSha = commitSha,
        chatMessageId = chatMessageId,
        totalItems = totalItems,
        completedItems = completedItems
    )

    private fun JobEvent.toEntity() = ExecJobEventEntity(
        jobId = jobId,
        timestamp = timestamp,
        type = type.name,
        stage = stage,
        status = status,
        progress = progress,
        message = message,
        item = item,
        completed = completed,
        total = total,
        error = error
    )
}
