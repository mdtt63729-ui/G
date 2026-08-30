package com.gitofy.feature.branches

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitofy.domain.model.BranchInfo
import com.gitofy.domain.usecase.GetBranchesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BranchListUiState(
    val branches: List<BranchInfo> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val showDeleteConfirm: BranchInfo? = null
)

@HiltViewModel
class BranchListViewModel @Inject constructor(
    private val getBranchesUseCase: GetBranchesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(BranchListUiState(isLoading = true))
    val uiState = _uiState.asStateFlow()

    fun load(owner: String, repo: String) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            getBranchesUseCase(owner, repo).collect { branches ->
                _uiState.update { it.copy(branches = branches, isLoading = false) }
            }
        }
    }

    fun onSearchChange(query: String) { _uiState.update { it.copy(searchQuery = query) } }
    fun showDeleteConfirm(branch: BranchInfo) { _uiState.update { it.copy(showDeleteConfirm = branch) } }
    fun hideDeleteConfirm() { _uiState.update { it.copy(showDeleteConfirm = null) } }
}
