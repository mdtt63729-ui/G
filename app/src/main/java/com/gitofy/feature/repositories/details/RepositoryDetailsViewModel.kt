package com.gitofy.feature.repositories.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitofy.domain.model.BranchInfo
import com.gitofy.domain.model.CommitInfo
import com.gitofy.domain.model.RepoDetails
import com.gitofy.domain.usecase.GetBranchesUseCase
import com.gitofy.domain.usecase.GetCommitsUseCase
import com.gitofy.domain.usecase.GetRepositoryDetailsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RepositoryDetailsUiState(
    val details: RepoDetails? = null,
    val branches: List<BranchInfo> = emptyList(),
    val commits: List<CommitInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class RepositoryDetailsViewModel @Inject constructor(
    private val getDetailsUseCase: GetRepositoryDetailsUseCase,
    private val getBranchesUseCase: GetBranchesUseCase,
    private val getCommitsUseCase: GetCommitsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(RepositoryDetailsUiState())
    val uiState = _uiState.asStateFlow()

    fun load(owner: String, repo: String) {
        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            getDetailsUseCase(owner, repo).fold(
                onSuccess = { details ->
                    _uiState.update { it.copy(details = details, isLoading = false) }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                }
            )
        }

        viewModelScope.launch {
            getBranchesUseCase(owner, repo).collect { branches ->
                _uiState.update { it.copy(branches = branches) }
            }
        }

        viewModelScope.launch {
            getCommitsUseCase(owner, repo).collect { commits ->
                _uiState.update { it.copy(commits = commits) }
            }
        }

        // Fetch fresh data
        viewModelScope.launch {
            getBranchesUseCase.refresh(owner, repo)
        }
        viewModelScope.launch {
            getCommitsUseCase.refresh(owner, repo)
        }
    }
}
