package com.gitofy.domain.model

/**
 * Error Categories — PRD v3.0 Section 73.
 * Minimum error categories for typed domain errors.
 */
enum class ErrorCategory {
    AUTHENTICATION,
    AUTHORIZATION,
    NETWORK,
    GITHUB_API,
    RATE_LIMIT,
    ZIP,
    FILESYSTEM,
    GIT,
    WORKFLOW,
    ARTIFACT,
    DATABASE,
    STORAGE,
    CANCELLATION,
    UNKNOWN
}

/**
 * Recovery actions — PRD v3.0 Section 72.
 * What the user can do when an error occurs.
 */
enum class RecoveryAction {
    RETRY,
    RE_AUTHENTICATE,
    UPDATE_PERMISSIONS,
    CHECK_NETWORK,
    FREE_STORAGE,
    FIX_INPUT,
    VIEW_LOGS,
    OPEN_GITHUB,
    CONTACT_SUPPORT,
    NONE
}

/**
 * Error Info — structured error metadata.
 * PRD v3.0 Section 72: Errors should contain:
 * Category, Code, UserMessage, TechnicalCause, Retryable, RecoveryAction
 */
data class ErrorInfo(
    val category: ErrorCategory,
    val code: String,
    val userMessage: String,
    val technicalCause: String,
    val retryable: Boolean,
    val recoveryAction: RecoveryAction
) {
    companion object {
        fun fromGitOFYError(error: GitOFYError): ErrorInfo {
            return when (error) {
                is GitOFYError.AuthenticationRequired -> ErrorInfo(
                    ErrorCategory.AUTHENTICATION, "AUTH_REQUIRED",
                    "Authentication required. Check your GitHub authorization and try again.",
                    error.message ?: "", true, RecoveryAction.RE_AUTHENTICATE
                )
                is GitOFYError.AuthenticationExpired -> ErrorInfo(
                    ErrorCategory.AUTHENTICATION, "AUTH_EXPIRED",
                    "Your session has expired. Please sign in again.",
                    error.message, false, RecoveryAction.RE_AUTHENTICATE
                )
                is GitOFYError.AuthenticationRevoked -> ErrorInfo(
                    ErrorCategory.AUTHENTICATION, "AUTH_REVOKED",
                    "Your token has been revoked. Please sign in again.",
                    error.message, false, RecoveryAction.RE_AUTHENTICATE
                )
                is GitOFYError.InsufficientPermission -> ErrorInfo(
                    ErrorCategory.AUTHORIZATION, "INSUFFICIENT_PERMISSION",
                    "Additional GitHub permission is required.",
                    error.message, false, RecoveryAction.UPDATE_PERMISSIONS
                )
                is GitOFYError.NetworkError -> ErrorInfo(
                    ErrorCategory.NETWORK, "NETWORK_ERROR",
                    "Network error. Check your internet connection.",
                    error.detail, true, RecoveryAction.CHECK_NETWORK
                )
                is GitOFYError.NetworkTimeout -> ErrorInfo(
                    ErrorCategory.NETWORK, "NETWORK_TIMEOUT",
                    "The request timed out. Please try again.",
                    error.message, true, RecoveryAction.RETRY
                )
                is GitOFYError.NoNetwork -> ErrorInfo(
                    ErrorCategory.NETWORK, "NO_NETWORK",
                    "You're offline. Cached information is available.",
                    error.message, false, RecoveryAction.CHECK_NETWORK
                )
                is GitOFYError.RateLimited -> ErrorInfo(
                    ErrorCategory.RATE_LIMIT, "RATE_LIMITED",
                    "GitHub API rate limit reached. Please try again later.",
                    error.message, true, RecoveryAction.NONE
                )
                is GitOFYError.GitHubApiError -> ErrorInfo(
                    ErrorCategory.GITHUB_API, "API_ERROR_${error.code}",
                    "GitHub API error: ${error.detail}",
                    error.detail, error.isRetryable, RecoveryAction.RETRY
                )
                is GitOFYError.GitHubServerError -> ErrorInfo(
                    ErrorCategory.GITHUB_API, "SERVER_ERROR_${error.code}",
                    "GitHub server error. Please try again later.",
                    error.message, true, RecoveryAction.RETRY
                )
                is GitOFYError.PermissionDenied -> ErrorInfo(
                    ErrorCategory.AUTHORIZATION, "PERMISSION_DENIED",
                    "Permission denied. Check your GitHub token permissions.",
                    error.message, false, RecoveryAction.UPDATE_PERMISSIONS
                )
                is GitOFYError.ResourceNotFound -> ErrorInfo(
                    ErrorCategory.GITHUB_API, "NOT_FOUND",
                    "The requested resource was not found.",
                    error.message, false, RecoveryAction.NONE
                )
                is GitOFYError.Conflict -> ErrorInfo(
                    ErrorCategory.GITHUB_API, "CONFLICT",
                    "A conflict occurred. Please refresh and try again.",
                    error.message, false, RecoveryAction.RETRY
                )
                is GitOFYError.ValidationError -> ErrorInfo(
                    ErrorCategory.GITHUB_API, "VALIDATION_ERROR",
                    "Please check your input and try again.",
                    error.message, false, RecoveryAction.FIX_INPUT
                )
                is GitOFYError.ZipError -> ErrorInfo(
                    ErrorCategory.ZIP, "ZIP_ERROR",
                    "The ZIP file could not be processed: ${error.detail}",
                    error.detail, false, RecoveryAction.FIX_INPUT
                )
                is GitOFYError.GitError -> ErrorInfo(
                    ErrorCategory.GIT, "GIT_ERROR",
                    "Git operation failed: ${error.detail}. Your local project remains untouched.",
                    error.detail, error.isRetryable, RecoveryAction.RETRY
                )
                is GitOFYError.StorageError -> ErrorInfo(
                    ErrorCategory.STORAGE, "STORAGE_ERROR",
                    "Storage error: ${error.detail}",
                    error.detail, false, RecoveryAction.FREE_STORAGE
                )
                is GitOFYError.WorkflowError -> ErrorInfo(
                    ErrorCategory.WORKFLOW, "WORKFLOW_ERROR",
                    "Workflow failed. View failed job logs to identify the problem.",
                    error.detail, false, RecoveryAction.VIEW_LOGS
                )
                is GitOFYError.ArtifactError -> ErrorInfo(
                    ErrorCategory.ARTIFACT, "ARTIFACT_ERROR",
                    "Download failed: ${error.detail}",
                    error.detail, error.isRetryable, RecoveryAction.RETRY
                )
                is GitOFYError.UnknownError -> ErrorInfo(
                    ErrorCategory.UNKNOWN, "UNKNOWN",
                    "Something went wrong: ${error.detail}",
                    error.detail, false, RecoveryAction.RETRY
                )
            }
        }
    }
}
