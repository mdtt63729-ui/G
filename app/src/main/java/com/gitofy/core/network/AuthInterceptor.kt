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
        val original = chain.request()
        // FIX: this used to force Accept to "application/vnd.github+json" on
        // every request, silently overwriting a caller's own Accept header.
        // GitHub's release-asset download endpoint only returns raw binary
        // bytes when Accept is "application/octet-stream" — with the old
        // unconditional overwrite, every APK download from Explore got back
        // a JSON metadata body instead of the APK, producing a corrupt file.
        // Now we only default the Accept header when the caller didn't set
        // one, instead of clobbering an explicit choice.
        val builder = original.newBuilder()
            .header("X-GitHub-Api-Version", "2022-11-28")
        if (original.header("Accept") == null) {
            builder.header("Accept", "application/vnd.github+json")
        }
        if (token != null) {
            builder.header("Authorization", "Bearer $token")
        }
        return chain.proceed(builder.build())
    }
}
