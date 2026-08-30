package com.gitofy.core.network

import com.gitofy.domain.model.GitOFYError
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * PRD §2, §16: 304 Not Modified must never be shown as a user-facing error.
 *
 * Strategy (PRD §16 "Simpler robust fallback"):
 *   200 → return body normally
 *   304 → return Result.failure(GitOFYError.NotModified()) — caller uses cached DB data
 *   4xx/5xx → typed error as before
 *
 * The caller (repository layer) checks: if error is NotModified → use cached data silently.
 */
suspend fun <T> safeApiCall(
    apiCall: suspend () -> Response<T>
): Result<T> {
    return try {
        val response = apiCall()
        when {
            response.code() == 304 -> {
                // PRD §16: 304 → use cached response. Not a user-facing error.
                Result.failure(GitOFYError.NotModified())
            }
            response.isSuccessful -> {
                val body = response.body()
                if (body != null) {
                    Result.success(body)
                } else {
                    Result.failure(GitOFYError.UnknownError("Empty response body"))
                }
            }
            else -> {
                val error = when (response.code()) {
                    401 -> GitOFYError.AuthenticationRequired()
                    403 -> {
                        val remaining = response.headers()["x-ratelimit-remaining"]
                        if (remaining == "0") GitOFYError.RateLimited()
                        else GitOFYError.PermissionDenied()
                    }
                    404 -> GitOFYError.ResourceNotFound()
                    409 -> GitOFYError.Conflict()
                    422 -> GitOFYError.ValidationError()
                    429 -> GitOFYError.RateLimited()
                    in 500..599 -> GitOFYError.GitHubServerError(response.code())
                    else -> GitOFYError.GitHubApiError(response.code(), response.message())
                }
                Result.failure(error)
            }
        }
    } catch (e: SocketTimeoutException) {
        Result.failure(GitOFYError.NetworkTimeout())
    } catch (e: java.net.UnknownHostException) {
        Result.failure(GitOFYError.NoNetwork())
    } catch (e: IOException) {
        Result.failure(GitOFYError.NetworkError(e.message ?: "Network error"))
    } catch (e: Exception) {
        Result.failure(GitOFYError.UnknownError(e.message ?: "Unknown error"))
    }
}
