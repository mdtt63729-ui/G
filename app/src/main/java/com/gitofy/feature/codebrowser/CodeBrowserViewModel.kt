package com.gitofy.feature.codebrowser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitofy.domain.model.FileContent
import com.gitofy.domain.usecase.GetContentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CodeBrowserUiState(
    val currentPath: String = "",
    val breadcrumbs: List<String> = emptyList(),
    val files: List<FileContent> = emptyList(),
    val selectedFile: FileContent? = null,
    val fileContent: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class CodeBrowserViewModel @Inject constructor(
    private val getContentUseCase: GetContentUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CodeBrowserUiState(isLoading = true))
    val uiState = _uiState.asStateFlow()

    fun browse(owner: String, repo: String, path: String = "") {
        _uiState.update { it.copy(currentPath = path, isLoading = true, error = null) }
        viewModelScope.launch {
            getContentUseCase(owner, repo, if (path.isEmpty()) "" else path).fold(
                onSuccess = { files ->
                    val breadcrumbs = if (path.isEmpty()) emptyList() else path.split("/")
                    _uiState.update { it.copy(files = files, breadcrumbs = breadcrumbs, isLoading = false) }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
            )
        }
    }

    fun openFile(file: FileContent) {
        _uiState.update { it.copy(selectedFile = file, fileContent = file.decodedContent) }
    }

    fun closeFile() {
        _uiState.update { it.copy(selectedFile = null, fileContent = null) }
    }
}
