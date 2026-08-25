package com.gitofy.feature.workflows

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitofy.domain.model.WorkflowRunSummary
import com.gitofy.domain.usecase.GetWorkflowRunsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WorkflowListUiState(
    val runs: List<WorkflowRunSummary> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class WorkflowListViewModel @Inject constructor(
    private val getRunsUseCase: GetWorkflowRunsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkflowListUiState())
    val uiState = _uiState.asStateFlow()

    fun load(owner: String, repo: String) {
        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            getRunsUseCase(owner, repo).collect { runs ->
                _uiState.update { it.copy(runs = runs, isLoading = false) }
            }
        }
        refresh(owner, repo)
    }

    fun refresh(owner: String, repo: String) {
        viewModelScope.launch {
            getRunsUseCase.refresh(owner, repo).fold(
                onFailure = { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                }
            )
        }
    }
}
