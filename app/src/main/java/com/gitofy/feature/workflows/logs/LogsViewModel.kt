package com.gitofy.feature.workflows.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitofy.domain.model.JobSummary
import com.gitofy.domain.repository.WorkflowRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LogsUiState(
    val job: JobSummary? = null,
    val logs: String = "",
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val logUnavailableWhileRunning: Boolean = false
) {
    val isRunning: Boolean
        get() = job?.status == "queued" || job?.status == "in_progress"
}

@HiltViewModel
class LogsViewModel @Inject constructor(
    private val workflowRepository: WorkflowRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LogsUiState())
    val uiState = _uiState.asStateFlow()
    private var pollingJob: Job? = null

    fun loadLogs(owner: String, repo: String, jobId: Long) {
        pollingJob?.cancel()
        _uiState.value = LogsUiState(isLoading = true)

        viewModelScope.launch {
            val jobResult = workflowRepository.getJob(owner, repo, jobId)
            jobResult.fold(
                onSuccess = { job ->
                    _uiState.update { it.copy(job = job, isLoading = false, error = null) }
                    fetchLogs(owner, repo, jobId, initial = true)
                    if (job.status == "queued" || job.status == "in_progress") {
                        startPolling(owner, repo, jobId)
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isLoading = false, error = error.message ?: "Failed to load job")
                    }
                }
            )
        }
    }

    private fun startPolling(owner: String, repo: String, jobId: Long) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                delay(2000)
                val jobResult = workflowRepository.getJob(owner, repo, jobId)
                jobResult.onSuccess { job ->
                    _uiState.update { it.copy(job = job, error = null) }
                    fetchLogs(owner, repo, jobId, initial = false)
                    if (job.status != "queued" && job.status != "in_progress") {
                        pollingJob?.cancel()
                    }
                }
            }
        }
    }

    private suspend fun fetchLogs(owner: String, repo: String, jobId: Long, initial: Boolean) {
        if (initial) _uiState.update { it.copy(isRefreshing = true) }
        workflowRepository.getJobLogs(owner, repo, jobId).fold(
            onSuccess = { body ->
                _uiState.update {
                    it.copy(
                        logs = body,
                        isRefreshing = false,
                        error = null,
                        logUnavailableWhileRunning = false
                    )
                }
            },
            onFailure = { error ->
                val running = _uiState.value.isRunning
                if (running) {
                    // GitHub can return 404 until a running job has produced an
                    // accessible log stream. This is not a user-facing error and
                    // must never turn into a distracting Retry screen.
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            logUnavailableWhileRunning = true,
                            error = null
                        )
                    }
                } else if (_uiState.value.logs.isBlank()) {
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            error = error.message ?: "Logs are not available for this job.",
                            logUnavailableWhileRunning = false
                        )
                    }
                }
            }
        )
    }

    fun refresh(owner: String, repo: String, jobId: Long) {
        viewModelScope.launch {
            val job = workflowRepository.getJob(owner, repo, jobId).getOrNull() ?: return@launch
            _uiState.update { it.copy(job = job, isRefreshing = true) }
            fetchLogs(owner, repo, jobId, initial = false)
        }
    }

    override fun onCleared() {
        pollingJob?.cancel()
        super.onCleared()
    }
}
