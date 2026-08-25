package com.gitofy.feature.operationcenter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitofy.data.local.dao.OperationDao
import com.gitofy.data.local.entity.OperationEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OperationCenterUiState(
    val activeOperations: List<OperationDisplay> = emptyList(),
    val recentOperations: List<OperationDisplay> = emptyList(),
    val isLoading: Boolean = false
)

data class OperationDisplay(
    val id: String,
    val type: String,
    val status: String,
    val progress: Float,
    val currentStage: String,
    val errorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long
)

@HiltViewModel
class OperationCenterViewModel @Inject constructor(
    private val operationDao: OperationDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(OperationCenterUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            operationDao.observeAll().collect { operations ->
                val active = operations
                    .filter { it.status in listOf("QUEUED", "RUNNING") }
                    .map { it.toDisplay() }
                val recent = operations
                    .filter { it.status !in listOf("QUEUED", "RUNNING") }
                    .sortedByDescending { it.updatedAt }
                    .take(20)
                    .map { it.toDisplay() }
                _uiState.update {
                    it.copy(activeOperations = active, recentOperations = recent, isLoading = false)
                }
            }
        }
    }

    fun clearFinished() {
        viewModelScope.launch {
            operationDao.clearFinished()
        }
    }

    private fun OperationEntity.toDisplay() = OperationDisplay(
        id = id,
        type = type,
        status = status,
        progress = progress,
        currentStage = currentStage,
        errorMessage = errorMessage,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
