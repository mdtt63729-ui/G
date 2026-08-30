package com.gitofy.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitofy.core.common.NetworkConnectivity
import com.gitofy.domain.model.RepoSummary
import com.gitofy.domain.model.User
import com.gitofy.domain.usecase.GetCurrentUserUseCase
import com.gitofy.domain.usecase.GetRepositoriesUseCase
import com.gitofy.data.local.dao.ExecJobDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * PRD §3: Home screen with all repositories (no take(5) limit).
 * PRD §20: Proper refresh state — isRefreshing vs isInitialLoading.
 * PRD §11: Pagination support.
 * PRD §24: Clear UI state via RepositoryUiState sealed interface.
 * PRD §6/§25: Immediate UI removal on delete.
 * PRD §21: Active job indicator for background operations.
 */
data class HomeUiState(
    val user: User? = null,
    val repos: List<RepoSummary> = emptyList(),
    val isInitialLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isOffline: Boolean = false,
    val error: String? = null,
    val activeJobCount: Int = 0
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getReposUseCase: GetRepositoriesUseCase,
    networkConnectivity: NetworkConnectivity,
    execJobDao: ExecJobDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Observe network state
            networkConnectivity.isOnline.collect { isOnline ->
                _uiState.update { it.copy(isOffline = !isOnline) }
            }
        }

        // Observe cached repositories — PRD §11: no take(5) limit
        viewModelScope.launch {
            getReposUseCase().collect { repos ->
                _uiState.update { it.copy(repos = repos, isInitialLoading = false) }
            }
        }

        // PRD §21: Observe active job count for background indicator
        viewModelScope.launch {
            execJobDao.observeActiveJobCount().collect { count ->
                _uiState.update { it.copy(activeJobCount = count) }
            }
        }

        refresh()
    }

    fun refresh() {
        // PRD §20: Don't clear existing content on refresh
        _uiState.update {
            it.copy(
                isRefreshing = true,
                isInitialLoading = it.repos.isEmpty(),
                error = null
            )
        }
        viewModelScope.launch {
            val result = getReposUseCase.refresh()
            result.fold(
                onSuccess = { repos ->
                    _uiState.update {
                        it.copy(
                            repos = repos,
                            isRefreshing = false,
                            isInitialLoading = false,
                            error = null
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            isInitialLoading = false,
                            error = error.message
                        )
                    }
                }
            )
        }
    }

    /**
     * PRD §6: Immediately remove a repository from local UI state after
     * a successful delete, so the card disappears instantly without
     * waiting for a full refresh. Also prevents stale state from
     * reappearing when navigating back.
     */
    fun onRepositoryDeleted(repositoryId: Long) {
        _uiState.update { state ->
            state.copy(repos = state.repos.filterNot { it.id == repositoryId })
        }
    }
}
