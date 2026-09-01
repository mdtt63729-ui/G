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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val operationId: String? = null,
    val cachedZipPath: String? = null
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
        // Drop any previously cached ZIP before caching the newly picked one.
        _uiState.value.cachedZipPath?.let { runCatching { File(it).delete() } }

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
                repositoryNameStatus = RepositoryNameStatus.Idle,
                cachedZipPath = null
            )
        }

        // FIX: startUpload() used to re-open state.zipUri a second time via
        // contentResolver.openInputStream() right before enqueueing the
        // upload worker. On several content providers (Google Drive, SAF,
        // some file-manager apps) that second open returns an empty/null
        // stream even though the persistable URI permission was granted —
        // the same class of bug already fixed for the Update Repository
        // flow. The fix is identical here: copy the ZIP to an
        // application-controlled cache file ONCE, right here during
        // selection, on Dispatchers.IO (with a single retry if the first
        // read comes back 0 bytes, since some providers aren't ready
        // immediately after the picker returns), and keep that local file
        // around so startUpload() reads from disk instead of the URI.
        viewModelScope.launch {
            val cachedZip = File(context.filesDir, "gitofy_zip_validation_${System.nanoTime()}.zip")
            val validation = withContext(Dispatchers.IO) {
                runCatching {
                    fun copyOnce(): Long {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            cachedZip.outputStream().use { output -> input.copyTo(output) }
                        } ?: error("Could not read the selected ZIP.")
                        return cachedZip.length()
                    }

                    var copiedBytes = copyOnce()
                    if (copiedBytes == 0L) {
                        delay(300)
                        copiedBytes = copyOnce()
                    }

                    val realSize = copiedBytes
                    if (realSize > 0 && (sizeBytes == null || sizeBytes <= 0)) {
                        _uiState.update { it.copy(zipSizeBytes = realSize) }
                    }

                    zipExtractor.validateZip(cachedZip)
                }.getOrElse {
                    SecureZipExtractor.ZipValidationResult(false, error = it.message ?: "Could not read the selected ZIP.")
                }
            }

            if (!validation.isValid) cachedZip.delete()

            _uiState.update {
                it.copy(
                    zipValidation = validation,
                    isValidatingZip = false,
                    error = if (validation.isValid) null else validation.error,
                    cachedZipPath = if (validation.isValid) cachedZip.absolutePath else null
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

        // FIX: use the locally-cached ZIP from onZipSelected instead of
        // re-opening state.zipUri here. Re-opening the content URI a second
        // time is what caused uploads to silently fail and bounce back to
        // the Repository step on repeat.
        val cachedFile = state.cachedZipPath?.let { File(it) }?.takeIf { it.exists() && it.length() > 0 }
        if (cachedFile == null) {
            _uiState.update {
                it.copy(
                    step = CreateProjectStep.Project,
                    error = "The selected ZIP is no longer available. Please pick it again."
                )
            }
            return
        }

        _uiState.update { it.copy(isProcessing = true, error = null, step = CreateProjectStep.Upload) }

        viewModelScope.launch {
            try {
                val operationId = cachedFile.inputStream().use { inputStream ->
                    uploadCoordinator.startUpload(
                        zipInputStream = inputStream,
                        repoName = state.repoName,
                        repoDescription = state.repoDescription,
                        isPrivate = state.isPrivate,
                        commitMessage = state.commitMessage
                    )
                }

                // The coordinator already made its own durable copy under
                // gito_operations/; the validation cache is no longer needed.
                cachedFile.delete()

                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        operationId = operationId,
                        step = CreateProjectStep.Upload,
                        cachedZipPath = null
                    )
                }
            } catch (e: Exception) {
                // Do not hide the real startup failure. The previous generic
                // message made WorkManager/security/storage failures impossible
                // to diagnose from the device. Keep the cached ZIP intact so the
                // user can retry after the underlying issue is fixed.
                val reason = e.message?.trim().orEmpty().ifBlank { e.javaClass.simpleName }
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        step = CreateProjectStep.Repository,
                        error = "Could not start upload: $reason"
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        _uiState.value.cachedZipPath?.let { runCatching { File(it).delete() } }
        super.onCleared()
    }
}
