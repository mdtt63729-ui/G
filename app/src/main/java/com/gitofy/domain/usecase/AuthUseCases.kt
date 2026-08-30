package com.gitofy.domain.usecase

import com.gitofy.core.security.SecureCredentialStorage
import com.gitofy.domain.model.AuthState
import com.gitofy.domain.model.User
import com.gitofy.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * PRD 7.1: Authentication use cases.
 * - Secure GitHub authentication.
 * - Validate authentication immediately.
 * - Detect invalid, revoked, expired credentials.
 * - Provide secure logout.
 * - Remove locally stored credentials on logout.
 */
class AuthenticateUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(token: String): Result<User> {
        return authRepository.authenticate(token)
    }
}

class ObserveAuthStateUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    operator fun invoke() = authRepository.observeAuthState()
}

class CheckStoredCredentialsUseCase @Inject constructor(
    private val secureStorage: SecureCredentialStorage
) {
    operator fun invoke(): Boolean = secureStorage.hasToken()
}

class SignOutUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    operator fun invoke() = authRepository.signOut()
}
