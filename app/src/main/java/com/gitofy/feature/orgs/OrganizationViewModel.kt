package com.gitofy.feature.orgs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitofy.domain.model.OrganizationSummary
import com.gitofy.domain.usecase.GetOrganizationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OrganizationUiState(
    val organizations: List<OrganizationSummary> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class OrganizationViewModel @Inject constructor(
    private val getOrgsUseCase: GetOrganizationsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrganizationUiState(isLoading = true))
    val uiState = _uiState.asStateFlow()

    fun load() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            getOrgsUseCase().fold(
                onSuccess = { orgs -> _uiState.update { it.copy(organizations = orgs, isLoading = false) } },
                onFailure = { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
            )
        }
    }
}
