package com.gitofy.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitofy.core.settings.SearchHistoryRepository
import com.gitofy.domain.model.RepoSummary
import com.gitofy.domain.usecase.GetRepositoriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * PRD §17/§18: Global Search with history.
 * Search history persisted in DataStore, max 20, duplicates move to top.
 */
data class GlobalSearchUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val searchHistory: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
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
    private val getReposUseCase: GetRepositoriesUseCase,
    private val searchHistoryRepository: SearchHistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GlobalSearchUiState())
    val uiState = _uiState.asStateFlow()

    init {
        // Observe search history from DataStore
        viewModelScope.launch {
            searchHistoryRepository.history.collect { history ->
                _uiState.update { it.copy(searchHistory = history) }
            }
        }
        // Observe cached repos for local search
        viewModelScope.launch {
            getReposUseCase().collect { repos ->
                cachedRepos = repos
            }
        }
    }

    private var cachedRepos: List<RepoSummary> = emptyList()

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        if (query.length >= 2) {
            search(query)
        } else {
            _uiState.update { it.copy(results = emptyList(), isLoading = false) }
        }
    }

    fun performSearch(query: String) {
        if (query.isBlank()) return
        // Save to history
        viewModelScope.launch {
            searchHistoryRepository.addQuery(query)
        }
        search(query)
    }

    private fun search(query: String) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        val q = query.lowercase()
        val results = cachedRepos.filter { repo ->
            repo.name.lowercase().contains(q) ||
            repo.ownerLogin.lowercase().contains(q) ||
            repo.fullName.lowercase().contains(q)
        }.map { repo ->
            SearchResult(
                title = repo.name,
                subtitle = repo.fullName,
                type = SearchResultType.REPOSITORY,
                ownerLogin = repo.ownerLogin,
                repoName = repo.name
            )
        }
        _uiState.update { it.copy(results = results, isLoading = false) }
    }

    fun removeHistoryItem(query: String) {
        viewModelScope.launch {
            searchHistoryRepository.removeQuery(query)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            searchHistoryRepository.clearAll()
        }
    }
}
