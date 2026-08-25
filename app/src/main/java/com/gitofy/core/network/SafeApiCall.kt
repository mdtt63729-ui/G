package com.gitofy.core.network

import com.gitofy.domain.model.GitOFYError
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Safe API call wrapper that maps HTTP errors to domain errors.
 * PRD 13: GitHub API Error Mapping.
 * PRD 32: Error Handling Architecture — typed domain errors.
 */
suspend fun <T> safeApiCall(
    apiCall: suspend () -> Response<T>
): Result<T> {
    return try {
        val response = apiCall()
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) {
                Result.success(body)
            } else {
                Result.failure(GitOFYError.UnknownError("Empty response body"))
            }
        } else {
            val error = when (response.code()) {
                401 -> GitOFYError.AuthenticationRequired
                403 -> {
                    // Check if rate limited
                    val remaining = response.headers()["x-ratelimit-remaining"]
                    if (remaining == "0") GitOFYError.RateLimited
                    else GitOFYError.PermissionDenied
                }
                404 -> GitOFYError.ResourceNotFound
                409 -> GitOFYError.Conflict
                422 -> GitOFYError.ValidationError
                429 -> GitOFYError.RateLimited
                in 500..599 -> GitOFYError.GitHubServerError(response.code())
                else -> GitOFYError.GitHubApiError(response.code(), response.message())
            }
            Result.failure(error)
        }
    } catch (e: SocketTimeoutException) {
        Result.failure(GitOFYError.NetworkTimeout)
    } catch (e: java.net.UnknownHostException) {
        Result.failure(GitOFYError.NoNetwork)
    } catch (e: IOException) {
        Result.failure(GitOFYError.NetworkError(e.message ?: "Network error"))
    } catch (e: Exception) {
        Result.failure(GitOFYError.UnknownError(e.message ?: "Unknown error"))
    }
}
