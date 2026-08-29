package com.gitofy.core.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Centralized auth header injection.
 * PRD 8.2: Authorization headers injected centrally.
 * PRD 8.1: Never include credentials in Git remote URLs.
 */
class AuthInterceptor(
    private val tokenProvider: () -> String?
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider()
        val request = if (token != null) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build()
        } else {
            chain.request().newBuilder()
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build()
        }
        return chain.proceed(request)
    }
}
