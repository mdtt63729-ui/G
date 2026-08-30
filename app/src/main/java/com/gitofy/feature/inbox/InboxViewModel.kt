package com.gitofy.feature.inbox

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitofy.core.network.GitHubApiService
import com.gitofy.data.remote.dto.GitHubNotification
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class InboxFilter { UNREAD, PARTICIPATING, ALL }

data class InboxUiState(
    val notifications: List<GitHubNotification> = emptyList(),
    val filteredNotifications: List<GitHubNotification> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val activeFilter: InboxFilter = InboxFilter.UNREAD,
    val tokenSupportsNotifications: Boolean = true
)

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val apiService: GitHubApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(InboxUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadNotifications()
    }

    fun loadNotifications(filter: InboxFilter? = null) {
        val currentFilter = filter ?: _uiState.value.activeFilter
        _uiState.update { it.copy(isLoading = true, error = null, activeFilter = currentFilter) }

        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    when (currentFilter) {
                        InboxFilter.UNREAD -> apiService.listNotifications(all = false, participating = false)
                        InboxFilter.PARTICIPATING -> apiService.listNotifications(all = false, participating = true)
                        InboxFilter.ALL -> apiService.listNotifications(all = true, participating = false)
                    }
                }

                if (response.isSuccessful) {
                    val notifications = response.body() ?: emptyList()
                    _uiState.update {
                        it.copy(
                            notifications = notifications,
                            filteredNotifications = notifications,
                            isLoading = false,
                            isRefreshing = false,
                            error = null,
                            tokenSupportsNotifications = true
                        )
                    }
                } else {
                    // 404 or 403 likely means token doesn't support notifications scope
                    if (response.code() == 403 || response.code() == 404) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                tokenSupportsNotifications = false,
                                error = null
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                error = "Failed to load notifications (${response.code()})"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("InboxViewModel", "Failed to load notifications", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = e.message ?: "Network error"
                    )
                }
            }
        }
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        loadNotifications()
    }

    fun markAsRead(threadId: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    apiService.markThreadAsRead(threadId)
                }
                // Update local state
                _uiState.update { state ->
                    val updated = state.notifications.map { n ->
                        if (n.id == threadId) n.copy(unread = false) else n
                    }
                    state.copy(
                        notifications = updated,
                        filteredNotifications = updated
                    )
                }
            } catch (e: Exception) {
                Log.e("InboxViewModel", "Failed to mark as read", e)
            }
        }
    }

    fun markAsDone(threadId: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    apiService.markThreadAsDone(threadId)
                }
                // Remove from local state
                _uiState.update { state ->
                    val updated = state.notifications.filterNot { it.id == threadId }
                    state.copy(
                        notifications = updated,
                        filteredNotifications = updated
                    )
                }
            } catch (e: Exception) {
                Log.e("InboxViewModel", "Failed to mark as done", e)
            }
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    apiService.markAllNotificationsAsRead()
                }
                _uiState.update { state ->
                    val updated = state.notifications.map { it.copy(unread = false) }
                    state.copy(
                        notifications = updated,
                        filteredNotifications = updated
                    )
                }
            } catch (e: Exception) {
                Log.e("InboxViewModel", "Failed to mark all as read", e)
            }
        }
    }
}
