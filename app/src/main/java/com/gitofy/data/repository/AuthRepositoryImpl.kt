package com.gitofy.data.repository

import com.gitofy.core.network.GitHubApiService
import com.gitofy.core.network.safeApiCall
import com.gitofy.core.security.SecureCredentialStorage
import com.gitofy.data.local.dao.UserDao
import com.gitofy.data.mapper.toDomain
import com.gitofy.data.mapper.toEntity
import com.gitofy.domain.model.AuthState
import com.gitofy.domain.model.GitOFYError
import com.gitofy.domain.model.User
import com.gitofy.domain.repository.AuthRepository
import com.gitofy.core.logging.GITOFYLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val apiService: GitHubApiService,
    private val secureStorage: SecureCredentialStorage,
    private val userDao: UserDao
) : AuthRepository {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    override fun observeAuthState(): Flow<AuthState> = _authState.asStateFlow()

    override suspend fun authenticate(token: String): Result<User> {
        _authState.value = AuthState.Authenticating

        // Save token first for the interceptor to use
        secureStorage.saveToken(token)

        val result = safeApiCall { apiService.getAuthenticatedUser() }

        return result.fold(
            onSuccess = { userDto ->
                // Validate permissions — check that we can access user data
                secureStorage.saveUserData(userDto.login, userDto.avatarUrl)
                userDao.upsert(userDto.toEntity())
                _authState.value = AuthState.Authenticated
                Result.success(userDto.toDomain())
            },
            onFailure = { error ->
                // Clear the invalid token
                secureStorage.clearToken()
                _authState.value = when (error) {
                    is GitOFYError.AuthenticationRequired -> AuthState.Invalid
                    is GitOFYError.PermissionDenied -> AuthState.InsufficientPermission
                    is GitOFYError.NoNetwork -> AuthState.NetworkError
                    else -> AuthState.Invalid
                }
                GITOFYLogger.w("Authentication failed: ${error.message}")
                Result.failure(error)
            }
        )
    }

    override suspend fun getCurrentUser(): Result<User> {
        val result = safeApiCall { apiService.getAuthenticatedUser() }
        return result.fold(
            onSuccess = { dto ->
                secureStorage.saveUserData(dto.login, dto.avatarUrl)
                userDao.upsert(dto.toEntity())
                Result.success(dto.toDomain())
            },
            onFailure = { error ->
                when (error) {
                    is GitOFYError.AuthenticationRequired -> _authState.value = AuthState.Invalid
                    is GitOFYError.PermissionDenied -> _authState.value = AuthState.InsufficientPermission
                    else -> {}
                }
                Result.failure(error)
            }
        )
    }

    override fun signOut() {
        // PRD 7.1: Remove locally stored credentials on logout
        secureStorage.clearAll()
        _authState.value = AuthState.SignedOut
        GITOFYLogger.i("User signed out, credentials cleared")
    }

    override fun hasStoredCredentials(): Boolean = secureStorage.hasToken()
}
