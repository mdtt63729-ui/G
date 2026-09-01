package com.gitofy.domain.model

/**
 * PRD §2-6: Real-time Jobs & Execution Monitor — Domain Models.
 *
 * Every job represents a REAL operation (Create, Update, Gito modification).
 * No hardcoded/fake/simulated job data is allowed (PRD §50).
 *
 * Flow: User Action → Real Operation → Job Created → Real-time Job Events →
 *       UI updates → Job completed/failed → Final verification.
 */

// PRD §6: Real job types
enum class JobType {
    CREATE_REPOSITORY,
    UPDATE_REPOSITORY,
    AI_REPOSITORY_CHANGE,
    // Future-ready types (PRD §6)
    CLONE,
    PULL,
    PUSH,
    COMMIT,
    BRANCH,
    PULL_REQUEST,
    WORKFLOW
}

// PRD §4: Job lifecycle
enum class JobStatus {
    QUEUED,
    STARTING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLING,
    CANCELLED
}

// PRD §14: Event types — each maps to a real operation event
enum class JobEventType {
    JOB_CREATED,
    JOB_STARTED,
    STEP_STARTED,
    STEP_PROGRESS,
    STEP_COMPLETED,
    FILE_STARTED,
    FILE_COMPLETED,
    COMMIT_CREATED,
    PUSH_STARTED,
    PUSH_COMPLETED,
    VERIFICATION_STARTED,
    VERIFICATION_COMPLETED,
    JOB_COMPLETED,
    JOB_FAILED,
    JOB_CANCELLED
}

// PRD §9: Step status icons
enum class StepStatus { PENDING, RUNNING, SUCCESS, FAILED, CANCELLED, SKIPPED }

// PRD §5: Job object — unique ID, operation type, repository, status, progress
data class JobInfo(
    val jobId: String,
    val operationId: String = "",
    val repository: String = "",
    val ownerLogin: String = "",
    val repoName: String = "",
    val operationType: JobType,
    val status: JobStatus = JobStatus.QUEUED,
    val progress: Float = 0f,
    val currentStep: String = "",
    val startedAt: Long = 0L,
    val updatedAt: Long = 0L,
    val completedAt: Long = 0L,
    val error: String? = null,
    val commitSha: String = "",
    val chatMessageId: String = "", // PRD §32: Job ↔ Chat linking
    val totalItems: Int = 0,
    val completedItems: Int = 0
) {
    val isActive: Boolean
        get() = status == JobStatus.QUEUED || status == JobStatus.STARTING ||
                status == JobStatus.RUNNING || status == JobStatus.CANCELLING

    val durationMs: Long
        get() = when {
            completedAt > 0 && startedAt > 0 -> completedAt - startedAt
            startedAt > 0 -> System.currentTimeMillis() - startedAt
            else -> 0L
        }
}

// PRD §7-9: Job step — only real steps that actually execute
data class JobStep(
    val jobId: String,
    val stepName: String,
    val displayName: String,
    val status: StepStatus = StepStatus.PENDING,
    val startedAt: Long = 0L,
    val completedAt: Long = 0L,
    val completedItems: Int = 0,
    val totalItems: Int = 0,
    val error: String? = null
) {
    val durationMs: Long
        get() = when {
            completedAt > 0 && startedAt > 0 -> completedAt - startedAt
            startedAt > 0 -> System.currentTimeMillis() - startedAt
            else -> 0L
        }

    val isIndeterminate: Boolean
        get() = status == StepStatus.RUNNING && totalItems == 0
}

// PRD §13: Real-time job event model
data class JobEvent(
    val jobId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: JobEventType,
    val stage: String = "",
    val status: String = "",
    val progress: Float = 0f,
    val message: String = "",
    val item: String = "",
    val completed: Int = 0,
    val total: Int = 0,
    val error: String? = null
)
