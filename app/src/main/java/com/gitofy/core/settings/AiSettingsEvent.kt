package com.gitofy.core.settings

/**
 * One-shot events emitted by the Settings system that the UI should react to
 * (snackbars, navigation, etc.).  These are NOT persistent state.
 */
sealed class AiSettingsEvent {
    data object SettingsReset : AiSettingsEvent()
    data object CacheCleared : AiSettingsEvent()
    data object CredentialsCleared : AiSettingsEvent()
    data class Error(val message: String) : AiSettingsEvent()
}
