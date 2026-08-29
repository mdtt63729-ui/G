package com.gitofy.feature.workflows.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitofy.core.notification.NotificationHelper
import com.gitofy.domain.model.JobSummary
import com.gitofy.domain.model.WorkflowRunSummary
import com.gitofy.domain.model.WorkflowStatus
import com.gitofy.domain.usecase.CancelWorkflowRunUseCase
import com.gitofy.domain.usecase.GetJobsUseCase
import com.gitofy.domain.usecase.GetWorkflowRunUseCase
import com.gitofy.domain.usecase.RerunFailedJobsUseCase
import com.gitofy.domain.usecase.RerunWorkflowRunUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * PRD §41: Real-time workflow monitoring with lifecycle-aware polling.
 * PRD §40: Duration calculation for completed and running runs.
 * PRD §100: Single ticker for all job timers.
 * PRD §73: Notifies the user (build success/failure) when a watched run completes.
 */
data class WorkflowDetailsUiState(
    val run: WorkflowRunSummary? = null,
    val jobs: List<JobSummary> = emptyList(),
    val isInitialLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val ticker: Long = 0  // Single ticker for all live timers
)

@HiltViewModel
class WorkflowDetailsViewModel @Inject constructor(
    private val getRunUseCase: GetWorkflowRunUseCase,
    private val getJobsUseCase: GetJobsUseCase,
    private val cancelRunUseCase: CancelWorkflowRunUseCase,
    private val rerunUseCase: RerunWorkflowRunUseCase,
    private val rerunFailedUseCase: RerunFailedJobsUseCase,
    private val notificationHelper: NotificationHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkflowDetailsUiState())
    val uiState = _uiState.asStateFlow()

    private var pollingJob: kotlinx.coroutines.Job? = null
    private var tickerJob: kotlinx.coroutines.Job? = null
    private var wasActive = false
    private var notifiedRunId: Long? = null

    fun load(owner: String, repo: String, runId: Long) {
        _uiState.update { it.copy(isInitialLoading = true, error = null) }

        viewModelScope.launch {
            try {
                getRunUseCase(runId).collect { run ->
                    _uiState.update { it.copy(run = run, isInitialLoading = false) }
                    // Start/stop polling based on run status
                    val isActive = run?.status == WorkflowStatus.QUEUED || run?.status == WorkflowStatus.IN_PROGRESS
                    if (isActive) {
                        startPolling(owner, repo, runId)
                        startTicker()
                    } else {
                        stopPolling()
                        stopTicker()
                        // PRD §73: Fire a notification exactly once when a run we were
                        // watching transitions from active (queued/in_progress) into a
                        // completed state — success or failure both notify.
                        if (wasActive && run != null && notifiedRunId != runId) {
                            notifiedRunId = runId
                            when (run.status) {
                                WorkflowStatus.COMPLETED_SUCCESS -> notificationHelper.showNotification(
                                    channel = NotificationHelper.NotificationChannel.WORKFLOWS,
                                    type = NotificationHelper.NotificationType.workflow_completed,
                                    title = "Build succeeded",
                                    message = "${run.name} (${run.headBranch}) completed successfully"
                                )
                                WorkflowStatus.COMPLETED_FAILURE, WorkflowStatus.TIMED_OUT -> notificationHelper.showNotification(
                                    channel = NotificationHelper.NotificationChannel.WORKFLOWS,
                                    type = NotificationHelper.NotificationType.workflow_failed,
                                    title = "Build failed",
                                    message = "${run.name} (${run.headBranch}) failed. Tap to view logs."
                                )
                                else -> { /* cancelled/skipped/unknown — no notification */ }
                            }
                        }
                    }
                    wasActive = isActive
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isInitialLoading = false, error = e.message ?: "Failed to load run") }
            }
        }

        viewModelScope.launch {
            try {
                getJobsUseCase(runId).collect { jobs ->
                    _uiState.update { it.copy(jobs = jobs) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(jobs = emptyList()) }
            }
        }

        refresh(owner, repo, runId)
    }

    private fun startPolling(owner: String, repo: String, runId: Long) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            var delayMs = 3000L
            while (true) {
                delay(delayMs)
                getRunUseCase.get(owner, repo, runId).fold(
                    onSuccess = {
                        // Reset to 3s on success
                        delayMs = if (it.status == WorkflowStatus.QUEUED) 5000L else 3000L
                    },
                    onFailure = {
                        // Exponential backoff on failure, max 30s
                        delayMs = (delayMs * 2).coerceAtMost(30000L)
                    }
                )
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    /**
     * PRD §100: Single ticker for all job timers.
     * Updates every 1 second for running job duration calculation.
     */
    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _uiState.update { it.copy(ticker = it.ticker + 1) }
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    fun refresh(owner: String, repo: String, runId: Long) {
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            getRunUseCase.get(owner, repo, runId).fold(
                onSuccess = { _uiState.update { it.copy(isRefreshing = false) } },
                onFailure = { _uiState.update { it.copy(isRefreshing = false, error = it.error) } }
            )
        }
        viewModelScope.launch {
            getJobsUseCase.refresh(owner, repo, runId)
        }
    }

    fun cancelRun(owner: String, repo: String, runId: Long) {
        viewModelScope.launch {
            cancelRunUseCase(owner, repo, runId).fold(
                onSuccess = { refresh(owner, repo, runId) },
                onFailure = { _uiState.update { it.copy(error = it.error) } }
            )
        }
    }

    fun rerunRun(owner: String, repo: String, runId: Long) {
        viewModelScope.launch {
            rerunUseCase(owner, repo, runId).fold(
                onSuccess = { refresh(owner, repo, runId) },
                onFailure = { _uiState.update { it.copy(error = it.error) } }
            )
        }
    }

    fun rerunFailedJobs(owner: String, repo: String, runId: Long) {
        viewModelScope.launch {
            rerunFailedUseCase(owner, repo, runId).fold(
                onSuccess = { refresh(owner, repo, runId) },
                onFailure = { _uiState.update { it.copy(error = it.error) } }
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
        stopTicker()
    }
}
