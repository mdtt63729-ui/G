package com.gitofy.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitofy.domain.model.RepoSummary
import com.gitofy.domain.usecase.GetRepositoriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Global Search — PRD v3.0 Section 63.
 * Search repositories, recent workflows, recent commits, and local operation history.
 * Does not download the entire GitHub account dataset.
 */
data class GlobalSearchUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val isLoading: Boolean = false
)

data class SearchResult(
    val title: String,
    val subtitle: String,
    val type: SearchResultType,
    val ownerLogin: String = "",
    val repoName: String = ""
)

enum class SearchResultType { REPOSITORY, WORKFLOW, COMMIT, OPERATION }

@HiltViewModel
class GlobalSearchViewModel @Inject constructor(
    private val getReposUseCase: GetRepositoriesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(GlobalSearchUiState())
    val uiState = _uiState.asStateFlow()

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        if (query.length >= 2) {
            search(query)
        } else {
            _uiState.update { it.copy(results = emptyList()) }
        }
    }

    private fun search(query: String) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            // Search from local cache only — no network request
            getReposUseCase().collect { repos ->
                val results = repos
                    .filter {
                        it.name.contains(query, ignoreCase = true) ||
                        it.fullName.contains(query, ignoreCase = true) ||
                        (it.description?.contains(query, ignoreCase = true) ?: false)
                    }
                    .take(20)
                    .map {
                        SearchResult(
                            title = it.name,
                            subtitle = it.fullName,
                            type = SearchResultType.REPOSITORY,
                            ownerLogin = it.ownerLogin,
                            repoName = it.name
                        )
                    }
                _uiState.update { it.copy(results = results, isLoading = false) }
            }
        }
    }
}
