package com.gitofy.domain.usecase

import com.gitofy.domain.repository.AuthRepository
import com.gitofy.domain.model.User
import javax.inject.Inject

class GetCurrentUserUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke() = authRepository.getCurrentUser()
}
