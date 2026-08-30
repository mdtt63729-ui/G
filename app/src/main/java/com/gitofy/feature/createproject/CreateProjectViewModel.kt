package com.gitofy.feature.createproject

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitofy.core.filesystem.SecureZipExtractor
import com.gitofy.core.security.SecureCredentialStorage
import com.gitofy.data.repository.RepositoryUploadCoordinator
import com.gitofy.domain.model.GitOFYError
import com.gitofy.domain.usecase.GetCurrentUserUseCase
import com.gitofy.domain.usecase.GetRepositoryDetailsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.File
import javax.inject.Inject

enum class CreateProjectStep { Project, Repository, Upload, Complete }
enum class RepositoryNameStatus { Idle, Invalid, Validating, Valid, Duplicate, Unknown }

data class CreateProjectUiState(
    val step: CreateProjectStep = CreateProjectStep.Project,
    val zipUri: Uri? = null,
    val zipFileName: String = "",
    val zipSizeBytes: Long? = null,
    val zipValidation: SecureZipExtractor.ZipValidationResult? = null,
    val repoName: String = "",
    val repoDescription: String = "",
    val isPrivate: Boolean = false,
    val commitMessage: String = "Initial commit",
    val repositoryNameStatus: RepositoryNameStatus = RepositoryNameStatus.Idle,
    val isValidatingZip: Boolean = false,
    val isCheckingRepoName: Boolean = false,
    val isProcessing: Boolean = false,
    val error: String? = null,
    val operationId: String? = null
) {
    val canContinueToRepository: Boolean
        get() = zipUri != null && zipValidation?.isValid == true && !isValidatingZip

    val canContinueToUpload: Boolean
        get() = repositoryNameStatus == RepositoryNameStatus.Valid &&
            repoName.isNotBlank() &&
            commitMessage.isNotBlank()

    val canStartUpload: Boolean
        get() = canContinueToUpload && !isProcessing
}

@HiltViewModel
class CreateProjectViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getRepositoryDetailsUseCase: GetRepositoryDetailsUseCase,
    private val zipExtractor: SecureZipExtractor,
    private val secureStorage: SecureCredentialStorage,
    private val uploadCoordinator: RepositoryUploadCoordinator
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateProjectUiState())
    val uiState = _uiState.asStateFlow()
    private var availabilityJob: Job? = null

    fun onStepRequested(step: CreateProjectStep) {
        val current = _uiState.value
        val allowed = when (step) {
            CreateProjectStep.Project -> true
            CreateProjectStep.Repository -> current.canContinueToRepository
            CreateProjectStep.Upload -> current.canContinueToUpload
            CreateProjectStep.Complete -> false
        }
        if (allowed) _uiState.update { it.copy(step = step, error = null) }
    }

    fun onZipSelected(uri: Uri, fileName: String, sizeBytes: Long?) {
        _uiState.update {
            it.copy(
                zipUri = uri,
                zipFileName = fileName,
                zipSizeBytes = sizeBytes,
                zipValidation = null,
                isValidatingZip = true,
                error = null,
                step = CreateProjectStep.Project,
                repoName = fileName.substringBeforeLast(".", fileName)
                    .replace(Regex("[^A-Za-z0-9._-]"), "-")
                    .lowercase(),
                repositoryNameStatus = RepositoryNameStatus.Idle
            )
        }

        viewModelScope.launch {
            val validation = runCatching {
                val temp = File(context.filesDir, "gitofy_zip_validation_${System.nanoTime()}.zip")
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        temp.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("Could not read the selected ZIP.")
                    zipExtractor.validateZip(temp)
                } finally {
                    temp.delete()
                }
            }.getOrElse {
                SecureZipExtractor.ZipValidationResult(false, error = "Could not read the selected ZIP.")
            }

            _uiState.update {
                it.copy(
                    zipValidation = validation,
                    isValidatingZip = false,
                    error = if (validation.isValid) null else validation.error
                )
            }
            if (validation.isValid) checkRepositoryName()
        }
    }

    fun onRepoNameChange(name: String) {
        _uiState.update {
            it.copy(
                repoName = name,
                repositoryNameStatus = RepositoryNameStatus.Idle,
                error = null
            )
        }
        checkRepositoryName()
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

    fun checkRepositoryName() {
        val name = _uiState.value.repoName.trim()
        availabilityJob?.cancel()

        if (name.isBlank()) {
            _uiState.update { it.copy(repositoryNameStatus = RepositoryNameStatus.Idle) }
            return
        }
        if (!name.matches(Regex("^[A-Za-z0-9._-]+$")) ||
            name.startsWith(".") || name.startsWith("-") || name.length > 100
        ) {
            _uiState.update { it.copy(repositoryNameStatus = RepositoryNameStatus.Invalid) }
            return
        }

        availabilityJob = viewModelScope.launch {
            delay(300)
            // Only the latest repository name is checked; this prevents one
            // network request per keystroke while preserving inline feedback.
            if (_uiState.value.repoName.trim() != name) return@launch
            _uiState.update { it.copy(repositoryNameStatus = RepositoryNameStatus.Validating, isCheckingRepoName = true) }
            val user = getCurrentUserUseCase()
            user.fold(
                onSuccess = { account ->
                    val result = getRepositoryDetailsUseCase(account.login, name)
                    result.fold(
                        onSuccess = {
                            _uiState.update {
                                it.copy(repositoryNameStatus = RepositoryNameStatus.Duplicate, isCheckingRepoName = false)
                            }
                        },
                        onFailure = { error ->
                            val status = if (error is GitOFYError.ResourceNotFound)
                                RepositoryNameStatus.Valid
                            else RepositoryNameStatus.Unknown
                            _uiState.update {
                                it.copy(repositoryNameStatus = status, isCheckingRepoName = false)
                            }
                        }
                    )
                },
                onFailure = {
                    _uiState.update {
                        it.copy(repositoryNameStatus = RepositoryNameStatus.Unknown, isCheckingRepoName = false)
                    }
                }
            )
        }
    }

    fun startUpload() {
        val state = _uiState.value
        if (!state.canStartUpload || state.zipUri == null) return

        if (!secureStorage.hasToken()) {
            _uiState.update { it.copy(error = "Please sign in to GitHub first.") }
            return
        }

        _uiState.update { it.copy(isProcessing = true, error = null, step = CreateProjectStep.Upload) }

        viewModelScope.launch {
            try {
                val operationId = context.contentResolver.openInputStream(state.zipUri)?.use { inputStream ->
                    uploadCoordinator.startUpload(
                        zipInputStream = inputStream,
                        repoName = state.repoName,
                        repoDescription = state.repoDescription,
                        isPrivate = state.isPrivate,
                        commitMessage = state.commitMessage
                    )
                } ?: throw IllegalStateException("Could not read the selected ZIP file.")

                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        operationId = operationId,
                        step = CreateProjectStep.Upload
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        step = CreateProjectStep.Repository,
                        error = "Could not start upload. Your project settings are still saved."
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
