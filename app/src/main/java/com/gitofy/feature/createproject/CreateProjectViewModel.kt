package com.gitofy.feature.createproject

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitofy.core.filesystem.SecureZipExtractor
import com.gitofy.core.security.SecureCredentialStorage
import com.gitofy.domain.usecase.CreateRepositoryUseCase
import com.gitofy.domain.model.GitOFYError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class CreateProjectUiState(
    val zipUri: Uri? = null,
    val zipFileName: String = "",
    val zipValidation: SecureZipExtractor.ZipValidationResult? = null,
    val repoName: String = "",
    val repoDescription: String = "",
    val isPrivate: Boolean = false,
    val commitMessage: String = "Initial commit",
    val detectedProjectRoot: String = "",
    val isProcessing: Boolean = false,
    val error: String? = null,
    val operationId: String? = null
)

@HiltViewModel
class CreateProjectViewModel @Inject constructor(
    private val createRepoUseCase: CreateRepositoryUseCase,
    private val zipExtractor: SecureZipExtractor,
    private val secureStorage: SecureCredentialStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateProjectUiState())
    val uiState = _uiState.asStateFlow()

    fun onZipSelected(uri: Uri, fileName: String) {
        _uiState.update {
            it.copy(
                zipUri = uri,
                zipFileName = fileName,
                zipValidation = null,
                error = null,
                repoName = fileName.substringBeforeLast(".", fileName)
                    .replace(Regex("[^A-Za-z0-9._-]"), "-")
                    .lowercase()
            )
        }
    }

    fun onRepoNameChange(name: String) {
        _uiState.update { it.copy(repoName = name, error = null) }
    }

    fun onDescriptionChange(desc: String) {
        _uiState.update { it.copy(repoDescription = desc) }
    }

    fun onVisibilityChange(isPrivate: Boolean) {
        _uiState.update { it.copy(isPrivate = isPrivate) }
    }

    fun onCommitMessageChange(msg: String) {
        _uiState.update { it.copy(commitMessage = msg) }
    }

    fun validateRepoName(): Boolean {
        val name = _uiState.value.repoName
        if (name.isBlank()) return false
        if (!name.matches(Regex("^[A-Za-z0-9._-]+$"))) return false
        if (name.startsWith(".") || name.startsWith("-")) return false
        if (name.length > 100) return false
        return true
    }

    fun startUpload(cacheDir: File) {
        val state = _uiState.value
        if (state.zipUri == null || !validateRepoName()) {
            _uiState.update { it.copy(error = "Please select a ZIP and enter a valid repository name") }
            return
        }

        val operationId = UUID.randomUUID().toString()
        _uiState.update { it.copy(isProcessing = true, operationId = operationId, error = null) }

        // The actual upload is handled by WorkManager (GitPushWorker)
        // We just emit the operation ID for navigation
        _uiState.update { it.copy(isProcessing = false) }
    }
}
