package com.gitofy.feature.jobs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitofy.data.local.dao.ExecJobDao
import com.gitofy.data.local.dao.ExecJobEventDao
import com.gitofy.data.local.dao.ExecJobStepDao
import com.gitofy.data.local.entity.ExecJobStepEntity
import com.gitofy.domain.model.JobStatus
import com.gitofy.domain.model.JobType
import com.gitofy.domain.model.StepStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Reactive Jobs screen model.
 *
 * The database remains the source of truth for job state. A lightweight
 * one-second clock only invalidates the UI so elapsed durations continue to
 * move while a job is running, including after leaving and re-entering Jobs.
 */
data class JobStepUi(
    val stepName: String,
    val displayName: String,
    val status: StepStatus,
    val startedAt: Long,
    val completedAt: Long,
    val completedItems: Int,
    val totalItems: Int,
    val error: String?
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

data class JobUiModel(
    val jobId: String,
    val repository: String,
    val operationType: JobType,
    val status: JobStatus,
    val progress: Float,
    val currentStep: String,
    val startedAt: Long,
    val completedAt: Long,
    val durationMs: Long,
    val error: String?,
    val commitSha: String,
    val totalItems: Int,
    val completedItems: Int,
    val steps: List<JobStepUi>
) {
    val isActive: Boolean
        get() = status == JobStatus.RUNNING || status == JobStatus.QUEUED ||
                status == JobStatus.STARTING || status == JobStatus.CANCELLING

    val progressPercent: Int
        get() = (progress * 100).toInt().coerceIn(0, 100)
}

data class JobsUiState(
    val activeJobs: List<JobUiModel> = emptyList(),
    val completedJobs: List<JobUiModel> = emptyList(),
    val failedJobs: List<JobUiModel> = emptyList(),
    val cancelledJobs: List<JobUiModel> = emptyList(),
    val activeJobCount: Int = 0,
    val isLoading: Boolean = false,
    val selectedJobId: String? = null,
    val selectedJobSteps: List<JobStepUi> = emptyList()
)

@HiltViewModel
class JobsViewModel @Inject constructor(
    private val execJobDao: ExecJobDao,
    private val execJobStepDao: ExecJobStepDao,
    @Suppress("UNUSED_PARAMETER") private val execJobEventDao: ExecJobEventDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(JobsUiState())
    val uiState = _uiState.asStateFlow()

    private val clock: Flow<Long> = flow {
        while (currentCoroutineContext().isActive) {
            emit(System.currentTimeMillis())
            delay(1000L)
        }
    }

    init {
        // Room emits immediately when a job changes. The clock emits once per
        // second so a running job's elapsed timer also changes in real time
        // even when no database field is being modified at that exact moment.
        viewModelScope.launch {
            combine(execJobDao.observeAll(), clock) { jobEntities, now ->
                jobEntities to now
            }.collect { (jobEntities, now) ->
                val jobsWithSteps = jobEntities.map { job ->
                    val steps = execJobStepDao.getStepsForJob(job.jobId)
                    job.toUiModel(steps.map { it.toUi() }, now)
                }

                val active = jobsWithSteps.filter { it.isActive }
                val completed = jobsWithSteps.filter { it.status == JobStatus.COMPLETED }
                val failed = jobsWithSteps.filter { it.status == JobStatus.FAILED }
                val cancelled = jobsWithSteps.filter { it.status == JobStatus.CANCELLED }

                val selectedId = _uiState.value.selectedJobId
                val selectedSteps = if (selectedId != null) {
                    jobsWithSteps.firstOrNull { it.jobId == selectedId }?.steps ?: emptyList()
                } else {
                    emptyList()
                }

                _uiState.update {
                    it.copy(
                        activeJobs = active,
                        completedJobs = completed,
                        failedJobs = failed,
                        cancelledJobs = cancelled,
                        activeJobCount = active.size,
                        selectedJobSteps = selectedSteps
                    )
                }
            }
        }
    }

    fun selectJob(jobId: String?) {
        _uiState.update { it.copy(selectedJobId = jobId) }
        if (jobId != null) {
            viewModelScope.launch {
                val steps = execJobStepDao.getStepsForJob(jobId)
                _uiState.update { it.copy(selectedJobSteps = steps.map { step -> step.toUi() }) }
            }
        } else {
            _uiState.update { it.copy(selectedJobSteps = emptyList()) }
        }
    }

    fun observeActiveJobCount() = execJobDao.observeActiveJobCount()

    private fun com.gitofy.data.local.entity.ExecJobEntity.toUiModel(
        steps: List<JobStepUi>,
        now: Long
    ): JobUiModel {
        return JobUiModel(
            jobId = jobId,
            repository = if (repository.isNotEmpty()) repository else repoName,
            operationType = runCatching { JobType.valueOf(operationType) }.getOrDefault(JobType.CREATE_REPOSITORY),
            status = runCatching { JobStatus.valueOf(status) }.getOrDefault(JobStatus.QUEUED),
            progress = progress,
            currentStep = currentStep,
            startedAt = startedAt,
            completedAt = completedAt,
            durationMs = when {
                completedAt > 0 && startedAt > 0 -> (completedAt - startedAt).coerceAtLeast(0L)
                startedAt > 0 -> (now - startedAt).coerceAtLeast(0L)
                else -> 0L
            },
            error = error,
            commitSha = commitSha,
            totalItems = totalItems,
            completedItems = completedItems,
            steps = steps
        )
    }

    private fun ExecJobStepEntity.toUi(): JobStepUi {
        return JobStepUi(
            stepName = stepName,
            displayName = displayName,
            status = runCatching { StepStatus.valueOf(status) }.getOrDefault(StepStatus.PENDING),
            startedAt = startedAt,
            completedAt = completedAt,
            completedItems = completedItems,
            totalItems = totalItems,
            error = error
        )
    }
}
