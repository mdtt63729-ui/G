package com.gitofy.feature.releases

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitofy.domain.model.ReleaseSummary
import com.gitofy.domain.usecase.GetReleasesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReleaseDashboardUiState(
    val releases: List<ReleaseSummary> = emptyList(),
    val latestRelease: ReleaseSummary? = null,
    val draftReleases: List<ReleaseSummary> = emptyList(),
    val preReleases: List<ReleaseSummary> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ReleaseDashboardViewModel @Inject constructor(
    private val getReleasesUseCase: GetReleasesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReleaseDashboardUiState(isLoading = true))
    val uiState = _uiState.asStateFlow()

    fun load(owner: String, repo: String) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            getReleasesUseCase(owner, repo).fold(
                onSuccess = { releases ->
                    _uiState.update {
                        it.copy(
                            releases = releases,
                            latestRelease = releases.firstOrNull { r -> !r.isDraft && !r.isPreRelease },
                            draftReleases = releases.filter { r -> r.isDraft },
                            preReleases = releases.filter { r -> r.isPreRelease },
                            isLoading = false
                        )
                    }
                },
                onFailure = { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
            )
        }
    }
}
