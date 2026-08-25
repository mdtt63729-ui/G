package com.gitofy.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure credential storage using Android Keystore + EncryptedSharedPreferences.
 * PRD 8.1: No plaintext credential storage anywhere.
 */
@Singleton
class SecureCredentialStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encryptedPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            "encrypted_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveToken(token: String) {
        encryptedPrefs.edit().putString(KEY_GITHUB_TOKEN, token).apply()
    }

    fun getToken(): String? {
        return encryptedPrefs.getString(KEY_GITHUB_TOKEN, null)
    }

    fun clearToken() {
        encryptedPrefs.edit().remove(KEY_GITHUB_TOKEN).apply()
    }

    fun hasToken(): Boolean {
        return encryptedPrefs.contains(KEY_GITHUB_TOKEN)
    }

    fun saveUserData(login: String, avatarUrl: String) {
        encryptedPrefs.edit()
            .putString(KEY_USER_LOGIN, login)
            .putString(KEY_USER_AVATAR, avatarUrl)
            .apply()
    }

    fun getUserLogin(): String? = encryptedPrefs.getString(KEY_USER_LOGIN, null)
    fun getUserAvatar(): String? = encryptedPrefs.getString(KEY_USER_AVATAR, null)

    fun clearAll() {
        encryptedPrefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_GITHUB_TOKEN = "github_token"
        private const val KEY_USER_LOGIN = "user_login"
        private const val KEY_USER_AVATAR = "user_avatar"
    }
}
