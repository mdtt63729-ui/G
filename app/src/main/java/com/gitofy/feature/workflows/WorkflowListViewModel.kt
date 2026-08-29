package com.gitofy.feature.workflows

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitofy.domain.model.WorkflowRunSummary
import com.gitofy.domain.model.WorkflowSummary
import com.gitofy.domain.repository.WorkflowRepository
import com.gitofy.domain.usecase.GetWorkflowRunsUseCase
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
    val error: String? = null
)

@HiltViewModel
class WorkflowListViewModel @Inject constructor(
    private val getRunsUseCase: GetWorkflowRunsUseCase,
    private val workflowRepository: WorkflowRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkflowListUiState())
    val uiState = _uiState.asStateFlow()

    fun load(owner: String, repo: String) {
        _uiState.update { it.copy(isLoading = true, error = null) }

        // Observe workflow definitions
        viewModelScope.launch {
            workflowRepository.observeWorkflows(owner, repo).collect { workflows ->
                _uiState.update { it.copy(workflows = workflows, isLoading = false) }
            }
        }

        // Observe workflow runs
        viewModelScope.launch {
            getRunsUseCase(owner, repo).collect { runs ->
                _uiState.update { it.copy(runs = runs, isLoading = false) }
            }
        }

        refresh(owner, repo)
    }

    fun refresh(owner: String, repo: String) {
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            workflowRepository.refreshWorkflows(owner, repo).fold(
                onSuccess = {},
                onFailure = { error ->
                    _uiState.update { it.copy(isRefreshing = false, error = error.message) }
                }
            )
        }
        viewModelScope.launch {
            getRunsUseCase.refresh(owner, repo).fold(
                onSuccess = { _uiState.update { it.copy(isRefreshing = false) } },
                onFailure = { error ->
                    _uiState.update { it.copy(isRefreshing = false, error = error.message) }
                }
            )
        }
    }
}
