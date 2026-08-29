package com.gitofy.domain.model

/**
 * Repository Creation Transaction Model — PRD v3.0 Section 32.
 * Repository creation must be treated as a recoverable multi-stage operation.
 *
 * VALIDATE → CREATE_GITHUB_REPOSITORY → PREPARE_LOCAL_GIT → INITIAL_COMMIT →
 * CONFIGURE_REMOTE → PUSH → VERIFY → SYNC_METADATA → SUCCESS
 *
 * Each stage is persistent and recoverable. On failure, the user can resume
 * from the last completed stage rather than starting over.
 */
enum class RepoCreationStage(val order: Int, val displayName: String) {
    VALIDATE(0, "Validating project"),
    CREATE_GITHUB_REPOSITORY(1, "Creating GitHub repository"),
    PREPARE_LOCAL_GIT(2, "Preparing local Git"),
    INITIAL_COMMIT(3, "Creating initial commit"),
    CONFIGURE_REMOTE(4, "Configuring remote"),
    PUSH(5, "Pushing to GitHub"),
    VERIFY(6, "Verifying repository"),
    SYNC_METADATA(7, "Syncing metadata"),
    SUCCESS(8, "Completed");

    companion object {
        fun fromOrder(order: Int): RepoCreationStage =
            entries.find { it.order == order } ?: VALIDATE

        fun canResumeFrom(stage: RepoCreationStage): Boolean = stage != SUCCESS
    }
}

/**
 * Transaction state — persisted in Room OperationEntity.
 */
data class RepoCreationTransaction(
    val operationId: String,
    val stage: RepoCreationStage,
    val repoName: String,
    val ownerLogin: String,
    val isComplete: Boolean,
    val error: String?,
    val canResume: Boolean
) {
    companion object {
        fun fromOperation(
            operationId: String,
            stageName: String,
            status: String,
            repoName: String,
            ownerLogin: String,
            errorMessage: String?
        ): RepoCreationTransaction {
            val stage = runCatching {
                RepoCreationStage.valueOf(stageName)
            }.getOrDefault(RepoCreationStage.VALIDATE)

            return RepoCreationTransaction(
                operationId = operationId,
                stage = stage,
                repoName = repoName,
                ownerLogin = ownerLogin,
                isComplete = status == "COMPLETED",
                error = errorMessage,
                canResume = status != "COMPLETED" && status != "CANCELLED"
            )
        }
    }
}
