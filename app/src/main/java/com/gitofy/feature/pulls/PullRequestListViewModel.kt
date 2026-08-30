package com.gitofy.feature.pulls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitofy.domain.model.PullRequestSummary
import com.gitofy.domain.usecase.GetPullRequestsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PullRequestListUiState(
    val prs: List<PullRequestSummary> = emptyList(),
    val filter: PRFilter = PRFilter.OPEN,
    val isLoading: Boolean = false,
    val error: String? = null
)

enum class PRFilter(val apiValue: String, val displayName: String) {
    OPEN("open", "Open"),
    CLOSED("closed", "Closed"),
    ALL("all", "All")
}

@HiltViewModel
class PullRequestListViewModel @Inject constructor(
    private val getPullRequestsUseCase: GetPullRequestsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(PullRequestListUiState(isLoading = true))
    val uiState = _uiState.asStateFlow()

    fun load(owner: String, repo: String, filter: PRFilter = PRFilter.OPEN) {
        _uiState.update { it.copy(filter = filter, isLoading = true, error = null) }
        viewModelScope.launch {
            getPullRequestsUseCase(owner, repo, filter.apiValue).fold(
                onSuccess = { prs ->
                    val filtered = when (filter) {
                        PRFilter.OPEN -> prs.filter { it.state == "open" && !it.isMerged }
                        PRFilter.CLOSED -> prs.filter { it.state == "closed" }
                        PRFilter.ALL -> prs
                    }
                    _uiState.update { it.copy(prs = filtered, isLoading = false) }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
            )
        }
    }
}
