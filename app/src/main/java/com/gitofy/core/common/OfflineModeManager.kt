package com.gitofy.core.common

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PRD §82: Offline Mode.
 *
 * Offline হলে:
 * - "You're offline. Cached repository data is available." দেখাবে
 * - Last updated timestamp দেখাবে
 * - Offline অবস্থায় cached read allowed
 * - Write operation queue করা যেতে পারে, কিন্তু user-কে clearly জানাতে হবে: "Waiting for connection"
 */
@Singleton
class OfflineModeManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _isOnline = MutableStateFlow(true)
    val isOnline = _isOnline.asStateFlow()

    private val _lastOnlineTime = MutableStateFlow(System.currentTimeMillis())
    val lastOnlineTime = _lastOnlineTime.asStateFlow()

    fun checkConnectivity(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = cm?.activeNetwork
        val capabilities = cm?.getNetworkCapabilities(network)
        val online = capabilities != null && (
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        )
        _isOnline.value = online
        if (online) _lastOnlineTime.value = System.currentTimeMillis()
        return online
    }

    fun isOnline(): Boolean = _isOnline.value

    fun getLastOnlineTimestamp(): Long = _lastOnlineTime.value

    /**
     * PRD §82: Returns a user-facing message for offline state.
     */
    fun getOfflineMessage(): String? {
        return if (!_isOnline.value) {
            val lastOnline = _lastOnlineTime.value
            val ago = (System.currentTimeMillis() - lastOnline) / 1000
            val timeStr = when {
                ago < 60 -> "just now"
                ago < 3600 -> "${ago / 60} min ago"
                else -> "${ago / 3600} hr ago"
            }
            "You're offline. Cached repository data is available.\nLast updated: $timeStr"
        } else null
    }

    /**
     * PRD §82: Write operations should show "Waiting for connection".
     */
 fun getPendingWriteMessage(): String? {
        return if (!_isOnline.value) "Waiting for connection" else null
    }
}
