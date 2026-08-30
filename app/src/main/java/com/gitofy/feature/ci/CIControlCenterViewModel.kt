package com.gitofy.feature.ci

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitofy.domain.model.WorkflowStatus
import com.gitofy.domain.usecase.GetWorkflowRunsUseCase
import com.gitofy.domain.usecase.CancelWorkflowRunUseCase
import com.gitofy.domain.usecase.RerunWorkflowRunUseCase
import com.gitofy.domain.usecase.RerunFailedJobsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CIControlCenterUiState(
    val running: Int = 0,
    val queued: Int = 0,
    val failed: Int = 0,
    val successful: Int = 0,
    val cancelled: Int = 0,
    val recentFailures: List<String> = emptyList(),
    val slowestBuilds: List<String> = emptyList(),
    val recentArtifacts: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class CIControlCenterViewModel @Inject constructor(
    private val getRunsUseCase: GetWorkflowRunsUseCase,
    private val cancelRunUseCase: CancelWorkflowRunUseCase,
    private val rerunRunUseCase: RerunWorkflowRunUseCase,
    private val rerunFailedJobsUseCase: RerunFailedJobsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CIControlCenterUiState(isLoading = true))
    val uiState = _uiState.asStateFlow()

    fun load(owner: String, repo: String) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            getRunsUseCase(owner, repo).collect { runs ->
                _uiState.update {
                    it.copy(
                        running = runs.count { r -> r.status == WorkflowStatus.IN_PROGRESS },
                        queued = runs.count { r -> r.status == WorkflowStatus.QUEUED },
                        failed = runs.count { r -> r.status == WorkflowStatus.COMPLETED_FAILURE },
                        successful = runs.count { r -> r.status == WorkflowStatus.COMPLETED_SUCCESS },
                        cancelled = runs.count { r -> r.status == WorkflowStatus.CANCELLED },
                        recentFailures = runs.filter { r -> r.status == WorkflowStatus.COMPLETED_FAILURE }.take(5).map { r -> r.name },
                        slowestBuilds = runs.sortedByDescending { r -> r.updatedAt }.take(5).map { r -> r.name },
                        isLoading = false
                    )
                }
            }
        }
    }

    fun cancelRun(owner: String, repo: String, runId: Long) {
        viewModelScope.launch { cancelRunUseCase(owner, repo, runId) }
    }

    fun rerunRun(owner: String, repo: String, runId: Long) {
        viewModelScope.launch { rerunRunUseCase(owner, repo, runId) }
    }

    fun rerunFailedJobs(owner: String, repo: String, runId: Long) {
        viewModelScope.launch { rerunFailedJobsUseCase(owner, repo, runId) }
    }
}
