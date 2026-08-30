package com.gitofy.core.config

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Feature Flag Architecture — PRD v3.0 Section 107.
 * Existing and future features may use feature flags.
 * Flags must have safe defaults.
 */
@Singleton
class FeatureFlags @Inject constructor() {

    enum class Flag(val defaultEnabled: Boolean) {
        WORKFLOW_DISPATCH(true),
        ARTIFACT_DOWNLOAD(true),
        WORKFLOW_EDITOR(true),
        BACKGROUND_SYNC(true),
        RELEASE_MANAGEMENT(false),
        SECRET_SCANNING(true),
        ADVANCED_LOGS(true),
        GLOBAL_SEARCH(true),
        OPERATION_CENTER(true),
        IN_APP_UPDATER(true)
    }

    private val flags = Flag.entries.associate { it to it.defaultEnabled }.toMutableMap()

    fun isEnabled(flag: Flag): Boolean = flags[flag] ?: flag.defaultEnabled

    fun setEnabled(flag: Flag, enabled: Boolean) {
        flags[flag] = enabled
    }

    fun reset() {
        flags.clear()
        Flag.entries.forEach { flags[it] = it.defaultEnabled }
    }
}
