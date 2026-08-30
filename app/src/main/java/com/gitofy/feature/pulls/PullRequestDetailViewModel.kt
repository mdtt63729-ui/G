package com.gitofy.feature.pulls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitofy.domain.model.PullRequestDetail
import com.gitofy.domain.model.ReviewSummary
import com.gitofy.domain.model.PRCommentSummary
import com.gitofy.domain.model.DiffFile
import com.gitofy.domain.usecase.GetPullRequestDetailUseCase
import com.gitofy.domain.usecase.GetPRReviewsUseCase
import com.gitofy.domain.usecase.GetPRCommentsUseCase
import com.gitofy.domain.usecase.GetPRDiffUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PullRequestDetailUiState(
    val pr: PullRequestDetail? = null,
    val reviews: List<ReviewSummary> = emptyList(),
    val comments: List<PRCommentSummary> = emptyList(),
    val diffFiles: List<DiffFile> = emptyList(),
    val selectedTab: PRTab = PRTab.CONVERSATION,
    val isLoading: Boolean = false,
    val error: String? = null,
    val showMergeConfirm: Boolean = false
)

enum class PRTab { CONVERSATION, FILES, COMMITS }

@HiltViewModel
class PullRequestDetailViewModel @Inject constructor(
    private val getPRDetailUseCase: GetPullRequestDetailUseCase,
    private val getReviewsUseCase: GetPRReviewsUseCase,
    private val getCommentsUseCase: GetPRCommentsUseCase,
    private val getDiffUseCase: GetPRDiffUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(PullRequestDetailUiState(isLoading = true))
    val uiState = _uiState.asStateFlow()

    fun load(owner: String, repo: String, prNumber: Int) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            getPRDetailUseCase(owner, repo, prNumber).fold(
                onSuccess = { pr -> _uiState.update { it.copy(pr = pr, isLoading = false) } },
                onFailure = { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
            )
        }
        viewModelScope.launch {
            getReviewsUseCase(owner, repo, prNumber).onSuccess { reviews ->
                _uiState.update { it.copy(reviews = reviews) }
            }
        }
        viewModelScope.launch {
            getCommentsUseCase(owner, repo, prNumber).onSuccess { comments ->
                _uiState.update { it.copy(comments = comments) }
            }
        }
        viewModelScope.launch {
            getDiffUseCase(owner, repo, prNumber).onSuccess { diff ->
                _uiState.update { it.copy(diffFiles = diff) }
            }
        }
    }

    fun selectTab(tab: PRTab) { _uiState.update { it.copy(selectedTab = tab) } }
    fun showMergeConfirm() { _uiState.update { it.copy(showMergeConfirm = true) } }
    fun hideMergeConfirm() { _uiState.update { it.copy(showMergeConfirm = false) } }
}
