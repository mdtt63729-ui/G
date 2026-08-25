package com.gitofy.feature.artifacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitofy.domain.model.ArtifactSummary
import com.gitofy.domain.usecase.DownloadArtifactUseCase
import com.gitofy.domain.usecase.GetArtifactsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArtifactsUiState(
    val artifacts: List<ArtifactSummary> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val downloadingId: Long? = null,
    val downloadMessage: String? = null
)

@HiltViewModel
class ArtifactsViewModel @Inject constructor(
    private val getArtifactsUseCase: GetArtifactsUseCase,
    private val downloadUseCase: DownloadArtifactUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArtifactsUiState())
    val uiState = _uiState.asStateFlow()

    fun load(owner: String, repo: String, runId: Long) {
        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            getArtifactsUseCase(runId).collect { artifacts ->
                _uiState.update { it.copy(artifacts = artifacts, isLoading = false) }
            }
        }
        refresh(owner, repo, runId)
    }

    fun refresh(owner: String, repo: String, runId: Long) {
        viewModelScope.launch {
            getArtifactsUseCase.refresh(owner, repo, runId).fold(
                onFailure = { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                }
            )
        }
    }

    fun download(owner: String, repo: String, artifact: ArtifactSummary) {
        _uiState.update { it.copy(downloadingId = artifact.id, downloadMessage = null) }
        viewModelScope.launch {
            downloadUseCase(owner, repo, artifact.id, artifact.name).fold(
                onSuccess = { path ->
                    _uiState.update {
                        it.copy(
                            downloadingId = null,
                            downloadMessage = "Downloaded to: $path"
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            downloadingId = null,
                            downloadMessage = "Download failed: ${error.message}"
                        )
                    }
                }
            )
        }
    }
}
