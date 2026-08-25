package com.gitofy.domain.model

/**
 * GITOFY typed domain errors.
 * PRD 32: Error Handling Architecture — every error must provide
 * technical cause, user-facing message, recovery action, retry eligibility.
 */
sealed class GitOFYError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    abstract val recoveryAction: String
    abstract val isRetryable: Boolean

    data class AuthenticationRequired(
        override val recoveryAction: String = "Check your GitHub authorization and try again.",
        override val isRetryable: Boolean = true
    ) : GitOFYError("Authentication required")

    data class AuthenticationExpired(
        override val recoveryAction: String = "Your session has expired. Please sign in again.",
        override val isRetryable: Boolean = false
    ) : GitOFYError("Authentication expired")

    data class AuthenticationRevoked(
        override val recoveryAction: String = "Your token has been revoked. Please sign in again.",
        override val isRetryable: Boolean = false
    ) : GitOFYError("Token revoked")

    data class InsufficientPermission(
        override val recoveryAction: String = "Additional GitHub permission is required.",
        override val isRetryable: Boolean = false
    ) : GitOFYError("Insufficient permissions")

    data class NetworkError(
        val detail: String,
        override val recoveryAction: String = "Check your internet connection and try again.",
        override val isRetryable: Boolean = true
    ) : GitOFYError(detail)

    data class NetworkTimeout(
        override val recoveryAction: String = "The request timed out. Please try again.",
        override val isRetryable: Boolean = true
    ) : GitOFYError("Network timeout")

    data class NoNetwork(
        override val recoveryAction: String = "You're offline. Cached information is available.",
        override val isRetryable: Boolean = false
    ) : GitOFYError("No network connection")

    data class GitHubApiError(
        val code: Int,
        val detail: String,
        override val recoveryAction: String = "Something went wrong. Please try again.",
        override val isRetryable: Boolean = code in 500..599
    ) : GitOFYError("GitHub API error ($code): $detail")

    data class GitHubServerError(
        val code: Int,
        override val recoveryAction: String = "GitHub server error. Please try again later.",
        override val isRetryable: Boolean = true
    ) : GitOFYError("GitHub server error ($code)")

    data class PermissionDenied(
        override val recoveryAction: String = "Check your GitHub token permissions.",
        override val isRetryable: Boolean = false
    ) : GitOFYError("Permission denied")

    data class ResourceNotFound(
        override val recoveryAction: String = "The requested resource was not found.",
        override val isRetryable: Boolean = false
    ) : GitOFYError("Resource not found")

    data class Conflict(
        override val recoveryAction: String = "A conflict occurred. Please refresh and try again.",
        override val isRetryable: Boolean = false
    ) : GitOFYError("Conflict")

    data class ValidationError(
        override val recoveryAction: String = "Please check your input and try again.",
        override val isRetryable: Boolean = false
    ) : GitOFYError("Validation error")

    data class RateLimited(
        override val recoveryAction: String = "GitHub API rate limit reached. Please try again later.",
        override val isRetryable: Boolean = true
    ) : GitOFYError("Rate limited")

    data class ZipError(
        val detail: String,
        override val recoveryAction: String = "The ZIP file could not be processed.",
        override val isRetryable: Boolean = false
    ) : GitOFYError(detail)

    data class GitError(
        val detail: String,
        override val recoveryAction: String = "Your local project remains untouched. Try again.",
        override val isRetryable: Boolean = true
    ) : GitOFYError(detail)

    data class StorageError(
        val detail: String,
        override val recoveryAction: String = "Free up storage space and try again.",
        override val isRetryable: Boolean = false
    ) : GitOFYError(detail)

    data class WorkflowError(
        val detail: String,
        override val recoveryAction: String = "View failed job logs to identify the problem.",
        override val isRetryable: Boolean = false
    ) : GitOFYError(detail)

    data class ArtifactError(
        val detail: String,
        override val recoveryAction: String = "Download failed. Please try again.",
        override val isRetryable: Boolean = true
    ) : GitOFYError(detail)

    data class UnknownError(
        val detail: String,
        override val recoveryAction: String = "Something went wrong. Please try again.",
        override val isRetryable: Boolean = false
    ) : GitOFYError(detail)
}
