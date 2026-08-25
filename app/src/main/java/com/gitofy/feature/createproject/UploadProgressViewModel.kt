package com.gitofy.feature.createproject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitofy.data.local.dao.OperationDao
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UploadProgressUiState(
    val progress: Float? = null,
    val currentStage: String = "Initializing...",
    val isComplete: Boolean = false,
    val error: String? = null,
    val owner: String = "",
    val repo: String = ""
)

@Inject
class UploadProgressViewModel @Inject constructor(
    private val operationDao: OperationDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(UploadProgressUiState())
    val uiState = _uiState.asStateFlow()

    fun startMonitoring(operationId: String) {
        viewModelScope.launch {
            operationDao.observeOperation(operationId).collect { operation ->
                if (operation != null) {
                    _uiState.update {
                        it.copy(
                            progress = operation.progress,
                            currentStage = operation.currentStage,
                            isComplete = operation.status == "COMPLETED",
                            error = operation.errorMessage
                        )
                    }
                }
            }
        }
    }
}
