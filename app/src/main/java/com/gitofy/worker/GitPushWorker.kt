package com.gitofy.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gitofy.core.logging.GITOFYLogger
import com.gitofy.core.security.SecureCredentialStorage
import com.gitofy.data.local.dao.OperationDao
import com.gitofy.data.local.entity.OperationEntity
import com.gitofy.domain.repository.GitRepository
import com.gitofy.domain.repository.GitHubRepository
import com.gitofy.data.git.WorkflowInjector
import com.gitofy.data.git.GitDeltaEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

/**
 * Git Push Worker — PRD 18, 20.
 * Pipeline: VALIDATING → EXTRACTING → CREATING_REPOSITORY → INITIALIZING_GIT →
 * CONFIGURING_GIT → STAGING → COMMITTING → CONFIGURING_REMOTE → PUSHING → VERIFYING → COMPLETED.
 * PRD 65: Repository Lifecycle — distinguish repo created but push failed.
 * PRD 66: Partial Failure Recovery — offer "Retry Push" not "Create Repository Again".
 */
@HiltWorker
class GitPushWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val gitRepository: GitRepository,
    private val gitHubRepository: GitHubRepository,
    private val secureStorage: SecureCredentialStorage,
    private val operationDao: OperationDao,
    private val workflowInjector: WorkflowInjector,
    private val gitDeltaEngine: GitDeltaEngine
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val operationId = inputData.getString(KEY_OPERATION_ID) ?: return Result.failure()
        val projectPath = inputData.getString(KEY_PROJECT_PATH) ?: return Result.failure()
        val repoName = inputData.getString(KEY_REPO_NAME) ?: return Result.failure()
        val repoDescription = inputData.getString(KEY_REPO_DESCRIPTION) ?: ""
        val isPrivate = inputData.getBoolean(KEY_IS_PRIVATE, false)
        val commitMessage = inputData.getString(KEY_COMMIT_MESSAGE) ?: "Initial commit"

        try {
            // Stage 1: Creating repository
            updateStage(operationId, "CREATING_REPOSITORY", 0.2f)

            val token = secureStorage.getToken()
                ?: run {
                    updateError(operationId, "Authentication required")
                    return Result.failure()
                }

            val createResult = gitHubRepository.createRepository(repoName, repoDescription, isPrivate)
            val createdRepo = createResult.getOrElse { error ->
                // PRD 65: Repository creation failed
                updateError(operationId, "Repository creation failed: ${error.message}")
                return Result.failure()
            }

            // PRD 65: Repository created successfully — now Git operations

            // Stage 2: Initializing Git
            updateStage(operationId, "INITIALIZING_GIT", 0.4f)
            gitRepository.initialize(projectPath).getOrElse { error ->
                // PRD 66: Repo created but Git init failed — can retry push
                updateError(operationId, "Git init failed: ${error.message}")
                return Result.failure()
            }

            // PRD Addendum: Automated Workflow Injection — inject CI/CD files before commit
            workflowInjector.injectWorkflows(projectPath)
            GITOFYLogger.i("Injected .github/workflows/ CI/CD pipelines")

            // PRD Addendum: JGit Delta — calculate changed files before staging
            gitDeltaEngine.calculateChangedFiles(projectPath).onSuccess { files ->
                GITOFYLogger.i("Delta engine: ${files.size} files to stage")
            }

            // Stage 3: Configuring Git
            updateStage(operationId, "CONFIGURING_GIT", 0.5f)
            val userLogin = secureStorage.getUserLogin() ?: repoName
            gitRepository.configureUser(projectPath, userLogin, "$userLogin@users.noreply.github.com")
                .getOrElse { error ->
                    updateError(operationId, "Git config failed: ${error.message}")
                    return Result.failure()
                }

            // Stage 4: Staging
            updateStage(operationId, "STAGING", 0.6f)
            gitRepository.addAll(projectPath).getOrElse { error ->
                updateError(operationId, "Staging failed: ${error.message}")
                return Result.failure()
            }

            // Stage 5: Committing
            updateStage(operationId, "COMMITTING", 0.7f)
            gitRepository.commit(projectPath, commitMessage).getOrElse { error ->
                updateError(operationId, "Commit failed: ${error.message}")
                return Result.failure()
            }

            // Stage 6: Configuring remote
            updateStage(operationId, "CONFIGURING_REMOTE", 0.8f)
            val remoteUrl = "https://github.com/${createdRepo.ownerLogin}/${createdRepo.name}.git"
            gitRepository.setRemote(projectPath, remoteUrl).getOrElse { error ->
                updateError(operationId, "Remote setup failed: ${error.message}")
                return Result.failure()
            }

            // Stage 7: Pushing — PRD 8.1: Use credentials provider, never embed token in URL
            updateStage(operationId, "PUSHING", 0.85f)
            gitRepository.push(projectPath, token, remoteUrl).getOrElse { error ->
                // PRD 66: Repo created but push failed — offer "Retry Push"
                updateError(operationId, "Push failed: ${error.message}")
                return Result.failure()
            }

            // Stage 8: Verifying
            updateStage(operationId, "VERIFYING", 0.95f)
            gitRepository.verifyRemote(projectPath, remoteUrl).getOrElse { error ->
                updateError(operationId, "Verification failed: ${error.message}")
                return Result.failure()
            }

            // Completed
            updateStage(operationId, "COMPLETED", 1.0f)
            operationDao.upsert(
                OperationEntity(
                    id = operationId,
                    type = "GIT_PUSH",
                    status = "COMPLETED",
                    progress = 1.0f,
                    currentStage = "COMPLETED"
                )
            )

            // Cleanup
            gitRepository.cleanup(projectPath)

            return Result.success()
        } catch (e: Exception) {
            GITOFYLogger.e("GitPushWorker failed", e)
            updateError(operationId, e.message ?: "Unknown error")
            return Result.failure()
        }
    }

    private suspend fun updateStage(id: String, stage: String, progress: Float) {
        GITOFYLogger.i("GitPush: $stage ($progress)")
        operationDao.upsert(
            OperationEntity(
                id = id,
                type = "GIT_PUSH",
                status = "RUNNING",
                progress = progress,
                currentStage = stage
            )
        )
    }

    private suspend fun updateError(id: String, error: String) {
        GITOFYLogger.e("GitPush error: $error")
        operationDao.upsert(
            OperationEntity(
                id = id,
                type = "GIT_PUSH",
                status = "FAILED",
                progress = 0f,
                currentStage = "FAILED",
                errorMessage = error
            )
        )
    }

    companion object {
        const val KEY_OPERATION_ID = "operation_id"
        const val KEY_PROJECT_PATH = "project_path"
        const val KEY_REPO_NAME = "repo_name"
        const val KEY_REPO_DESCRIPTION = "repo_description"
        const val KEY_IS_PRIVATE = "is_private"
        const val KEY_COMMIT_MESSAGE = "commit_message"
    }
}
