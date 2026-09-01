package com.gitofy.core.security

object NativeSecurity {
    private var loaded = false

    init {
        loaded = runCatching {
            System.loadLibrary("gitofy-security")
            true
        }.getOrDefault(false)
    }

    fun environmentFlags(): Int = if (loaded) {
        runCatching { nativeEnvironmentFlags() }.getOrDefault(0)
    } else 0

    fun validateIdentity(packageName: String, appLabel: String): Boolean =
        if (loaded) runCatching { nativeValidateIdentity(packageName, appLabel) }.getOrDefault(false) else false

    private external fun nativeEnvironmentFlags(): Int
    private external fun nativeValidateIdentity(packageName: String, appLabel: String): Boolean
}
