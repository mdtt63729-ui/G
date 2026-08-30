package com.gitofy.feature.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitofy.ai.agent.*
import com.gitofy.core.security.SecureCredentialStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AgentViewModel @Inject constructor(
    private val orchestrator: AgentOrchestrator,
    private val secureStorage: SecureCredentialStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow(AgentUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            orchestrator.events.collect { event ->
                _uiState.update { state ->
                    state.copy(
                        recentEvents = (listOf(event) + state.recentEvents).take(100),
                        session = state.session.copy(
                            events = state.session.events + event
                        )
                    )
                }
            }
        }
    }

    fun executeCommand(command: String, repositoryOwner: String, repositoryName: String) {
        _uiState.update { it.copy(isProcessing = true, error = null) }
        viewModelScope.launch {
            try {
                val sessionId = orchestrator.executeCommand(
                    command, repositoryOwner, repositoryName
                )
                val session = orchestrator.getSession(sessionId) ?: return@launch
                _uiState.update {
                    it.copy(
                        session = session,
                        planVisible = true,
                        currentTask = session.tasks.firstOrNull()
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Command failed") }
            } finally {
                _uiState.update { it.copy(isProcessing = false) }
            }
        }
    }

    fun cancelSession() {
        val sessionId = _uiState.value.session.id
        if (sessionId.isNotBlank()) {
            orchestrator.cancelSession(sessionId)
        }
        _uiState.update { it.copy(isProcessing = false) }
    }

    fun togglePlan() {
        _uiState.update { it.copy(planVisible = !it.planVisible) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
}
