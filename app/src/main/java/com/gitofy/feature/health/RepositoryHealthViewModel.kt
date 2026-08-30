package com.gitofy.feature.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitofy.domain.model.RepositoryHealth
import com.gitofy.domain.model.HealthStatus
import com.gitofy.domain.usecase.GetRepositoryHealthUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RepositoryHealthUiState(
    val health: RepositoryHealth? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class RepositoryHealthViewModel @Inject constructor(
    private val getHealthUseCase: GetRepositoryHealthUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(RepositoryHealthUiState(isLoading = true))
    val uiState = _uiState.asStateFlow()

    fun load(owner: String, repo: String) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            getHealthUseCase(owner, repo).fold(
                onSuccess = { health -> _uiState.update { it.copy(health = health, isLoading = false) } },
                onFailure = { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
            )
        }
    }
}
