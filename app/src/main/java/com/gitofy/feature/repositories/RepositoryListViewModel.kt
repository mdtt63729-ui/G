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
    val query: String = "",
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null
) {
    /** Client-side filtered view used by the screen — does not affect the underlying cache. */
    val filteredRepositories: List<RepoSummary>
        get() = if (query.isBlank()) {
            repositories
        } else {
            repositories.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.fullName.contains(query, ignoreCase = true) ||
                    (it.description?.contains(query, ignoreCase = true) ?: false)
            }
        }
}

@HiltViewModel
class RepositoryListViewModel @Inject constructor(
    private val getReposUseCase: GetRepositoriesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(RepositoryListUiState())
    val uiState = _uiState.asStateFlow()

    private var currentPage = 1

    init {
        viewModelScope.launch {
            getReposUseCase().collect { repos ->
                _uiState.update { it.copy(repositories = repos, isLoading = false) }
            }
        }
        refresh()
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun refresh() {
        currentPage = 1
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            getReposUseCase.refresh(currentPage).fold(
                onSuccess = { repos ->
                    _uiState.update { it.copy(repositories = repos, isLoading = false) }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                }
            )
        }
    }

    /** PRD Phase 3 §2 — pagination, using the existing paginated refresh already in the data layer. */
    fun loadMore() {
        if (_uiState.value.isLoadingMore || _uiState.value.isLoading) return
        val nextPage = currentPage + 1
        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            getReposUseCase.refresh(nextPage).fold(
                onSuccess = { repos ->
                    currentPage = nextPage
                    _uiState.update { it.copy(repositories = repos, isLoadingMore = false) }
                },
                onFailure = {
                    // Keep the current page on failure; don't surface a fresh-load error for a background page fetch.
                    _uiState.update { it.copy(isLoadingMore = false) }
                }
            )
        }
    }
}
