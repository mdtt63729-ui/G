package com.gitofy.feature.workflows

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitofy.domain.model.WorkflowRunSummary
import com.gitofy.domain.model.WorkflowStatus
import com.gitofy.domain.model.WorkflowSummary
import com.gitofy.domain.repository.WorkflowRepository
import com.gitofy.domain.usecase.GetWorkflowRunsUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * PRD §7/§29: Workflow definitions + runs as separate concepts.
 * Shows real GitHub workflows (not dummy).
 */
data class WorkflowListUiState(
    val workflows: List<WorkflowSummary> = emptyList(),
    val runs: List<WorkflowRunSummary> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val runsByWorkflow: Map<Long, List<WorkflowRunSummary>> = emptyMap(),
    val loadingWorkflowId: Long? = null
)

@HiltViewModel
class WorkflowListViewModel @Inject constructor(
    private val getRunsUseCase: GetWorkflowRunsUseCase,
    private val workflowRepository: WorkflowRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkflowListUiState())
    val uiState = _uiState.asStateFlow()
    private var pollingJob: Job? = null

    fun load(owner: String, repo: String) {
        pollingJob?.cancel()
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            workflowRepository.observeWorkflows(owner, repo).collect { workflows ->
                _uiState.update { it.copy(workflows = workflows, isLoading = false) }
            }
        }
        viewModelScope.launch {
            getRunsUseCase(owner, repo).collect { runs ->
                _uiState.update { it.copy(runs = runs, isLoading = false) }
            }
        }
        refresh(owner, repo)
        pollingJob = viewModelScope.launch {
            while (true) {
                delay(4000)
                val active = _uiState.value.runs.any { it.status == WorkflowStatus.QUEUED || it.status == WorkflowStatus.IN_PROGRESS }
                if (active) refresh(owner, repo)
            }
        }
    }

    fun loadRunsForWorkflow(owner: String, repo: String, workflowId: Long) {
        _uiState.update { it.copy(loadingWorkflowId = workflowId) }
        viewModelScope.launch {
            workflowRepository.refreshRunsByWorkflow(owner, repo, workflowId.toString()).fold(
                onSuccess = { runs ->
                    _uiState.update { it.copy(runsByWorkflow = it.runsByWorkflow + (workflowId to runs), loadingWorkflowId = null) }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(loadingWorkflowId = null, error = error.message) }
                }
            )
        }
    }

    fun refresh(owner: String, repo: String) {
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            workflowRepository.refreshWorkflows(owner, repo).fold(
                onSuccess = {},
                onFailure = { error -> _uiState.update { it.copy(error = error.message) } }
            )
        }
        viewModelScope.launch {
            getRunsUseCase.refresh(owner, repo).fold(
                onSuccess = { runs ->
                    _uiState.update { it.copy(isRefreshing = false, runs = runs) }
                },
                onFailure = { error -> _uiState.update { it.copy(isRefreshing = false, error = error.message) } }
            )
        }
    }

    override fun onCleared() {
        pollingJob?.cancel()
        super.onCleared()
    }

}
