package com.gitofy.feature.issues

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitofy.domain.model.IssueSummary
import com.gitofy.domain.usecase.GetIssuesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IssueListUiState(
    val issues: List<IssueSummary> = emptyList(),
    val filter: IssueFilter = IssueFilter.OPEN,
    val isLoading: Boolean = false,
    val error: String? = null
)

enum class IssueFilter(val apiValue: String, val displayName: String) {
    OPEN("open", "Open"), CLOSED("closed", "Closed"), ALL("all", "All")
}

@HiltViewModel
class IssueListViewModel @Inject constructor(
    private val getIssuesUseCase: GetIssuesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(IssueListUiState(isLoading = true))
    val uiState = _uiState.asStateFlow()

    fun load(owner: String, repo: String, filter: IssueFilter = IssueFilter.OPEN) {
        _uiState.update { it.copy(filter = filter, isLoading = true, error = null) }
        viewModelScope.launch {
            getIssuesUseCase(owner, repo, filter.apiValue).fold(
                onSuccess = { issues -> _uiState.update { it.copy(issues = issues, isLoading = false) } },
                onFailure = { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
            )
        }
    }
}
