package com.gitofy.feature.repositories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitofy.domain.model.RepoSummary
import com.gitofy.domain.usecase.GetRepositoriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RepositoryListUiState(
    val repositories: List<RepoSummary> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class RepositoryListViewModel @Inject constructor(
    private val getReposUseCase: GetRepositoriesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(RepositoryListUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getReposUseCase().collect { repos ->
                _uiState.update { it.copy(repositories = repos, isLoading = false) }
            }
        }
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            getReposUseCase.refresh().fold(
                onSuccess = { repos ->
                    _uiState.update { it.copy(repositories = repos, isLoading = false) }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                }
            )
        }
    }
}
