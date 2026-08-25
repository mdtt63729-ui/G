package com.gitofy.feature.workflows.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitofy.domain.model.JobSummary
import com.gitofy.domain.model.WorkflowRunSummary
import com.gitofy.domain.usecase.GetJobsUseCase
import com.gitofy.domain.usecase.GetWorkflowRunUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WorkflowDetailsUiState(
    val run: WorkflowRunSummary? = null,
    val jobs: List<JobSummary> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class WorkflowDetailsViewModel @Inject constructor(
    private val getRunUseCase: GetWorkflowRunUseCase,
    private val getJobsUseCase: GetJobsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkflowDetailsUiState())
    val uiState = _uiState.asStateFlow()

    fun load(owner: String, repo: String, runId: Long) {
        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            getRunUseCase(runId).collect { run ->
                _uiState.update { it.copy(run = run, isLoading = false) }
            }
        }
        viewModelScope.launch {
            getJobsUseCase(runId).collect { jobs ->
                _uiState.update { it.copy(jobs = jobs) }
            }
        }

        viewModelScope.launch {
            getRunUseCase.get(owner, repo, runId)
        }
        viewModelScope.launch {
            getJobsUseCase.refresh(owner, repo, runId)
        }
    }
}
