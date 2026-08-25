package com.gitofy.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitofy.core.common.NetworkConnectivity
import com.gitofy.domain.model.RepoSummary
import com.gitofy.domain.model.User
import com.gitofy.domain.usecase.GetCurrentUserUseCase
import com.gitofy.domain.usecase.GetRepositoriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val user: User? = null,
    val recentRepos: List<RepoSummary> = emptyList(),
    val isLoading: Boolean = false,
    val isOffline: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getReposUseCase: GetRepositoriesUseCase,
    networkConnectivity: NetworkConnectivity
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

        // Observe cached repositories
        viewModelScope.launch {
            getReposUseCase().collect { repos ->
                _uiState.update { it.copy(recentRepos = repos.take(5), isLoading = false) }
            }
        }

        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = getReposUseCase.refresh()
            result.fold(
                onSuccess = { repos ->
                    _uiState.update {
                        it.copy(
                            recentRepos = repos.take(5),
                            isLoading = false,
                            error = null
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message
                        )
                    }
                }
            )
        }
    }
}
