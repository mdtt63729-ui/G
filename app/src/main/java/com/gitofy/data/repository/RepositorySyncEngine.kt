package com.gitofy.data.repository

import com.gitofy.core.filesystem.SecureZipExtractor
import com.gitofy.core.logging.GITOFYLogger
import com.gitofy.core.network.GitHubApiService
import com.gitofy.core.network.safeApiCall
import com.gitofy.core.security.SecureCredentialStorage
import com.gitofy.data.remote.dto.GitTreeEntry
import com.gitofy.data.git.GitNativeManager
import com.gitofy.data.git.WorkflowInjector
import com.gitofy.data.remote.dto.DispatchWorkflowRequest
import com.gitofy.domain.model.GitOFYError
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.io.BufferedInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PRD §33: Repository Sync Engine — the unified core that powers both
 * Create Repository and Update Repository flows.
 *
 * Architecture:
 *   GitHubApi → GitHubRepositoryDataSource → RepositorySyncEngine
 *                                        → CreateRepositoryUseCase
 *                                        → UpdateRepositoryUseCase
 *                                        → ViewModel → Compose UI
 *
 * PRD §34: No stubs, no fakes, no simulated success. Every operation
 * performs a real GitHub API mutation.
 *
 * PRD §17: Atomic Update Strategy — Prepare → Validate ALL → Apply →
 * Commit → Push → Verify. If any step fails, the operation FAILS and
 * the user sees the exact failed operation.
 *
 * PRD §19: "Nothing to Push" Fix — if diff is empty, return NoChanges
 * (not a failure).
 */
@Singleton
class RepositorySyncEngine @Inject constructor(
    private val apiService: GitHubApiService,
    private val zipExtractor: SecureZipExtractor,
    private val secureStorage: SecureCredentialStorage,
    private val gitNativeManager: GitNativeManager,
    private val workflowInjector: WorkflowInjector
) {

    // PRD §14: Default excluded directories — generated artifacts that
    // should not be synced to the remote repository.
    private val defaultExcludedDirs = setOf(
        ".git", ".gradle", "build", ".idea", ".cxx",
        "local.properties", "captures", ".kotlin", "node_modules", "dist", "out", "target",
        "coverage", ".cache", ".pytest_cache", "__pycache__", ".venv", "venv", "Pods", "DerivedData"
    )

    // PRD §15: Supported binary file extensions
    private val binaryExtensions = setOf(
        "png", "jpg", "jpeg", "webp", "gif", "svg", "ico",
        "pdf", "zip", "apk", "aab", "mp3", "mp4", "jar",
        "keystore", "jks", "bin", "dat", "db", "sqlite"
    )

    /**
     * PRD §33: Result of a sync operation — either Create or Update.
     */
    sealed class SyncResult {
        data class Created(
            val ownerLogin: String,
            val repoName: String,
            val filesPushed: Int,
            val commitSha: String
        ) : SyncResult()

        data class Updated(
            val ownerLogin: String,
            val repoName: String,
            val added: Int,
            val modified: Int,
            val deleted: Int,
            val unchanged: Int,
            val commitSha: String
        ) : SyncResult()

        data object NoChanges : SyncResult()

        data class Failed(val error: GitOFYError) : SyncResult()
    }

    /**
     * PRD §10-11: File change classification.
     */
    enum class ChangeType { ADDED, MODIFIED, DELETED, UNCHANGED }

    data class FileChange(
        val path: String,
        val changeType: ChangeType,
        val localSha: String = "",
        val remoteSha: String = ""
    )

    /**
     * PRD §65: Structured progress event — single source of truth.
     * UI never creates its own percentage; it only renders what the
     * engine emits.
     */
    data class SyncProgress(
        val stage: SyncStage,
        val progress: Float,
        val currentItem: String = "",
        val completedItems: Int = 0,
        val totalItems: Int = 0,
        val bytesUploaded: Long = 0L,
        val totalBytes: Long = 0L,
        val startedAt: Long = System.currentTimeMillis(),
        val completedAt: Long = 0L,
        val error: String? = null
    )

    enum class SyncStage {
        PREPARING, CHECKING_REPOSITORY, COMPARING, PREPARING_CHANGES,
        UPLOADING, CREATING_COMMIT, PUSHING, VERIFYING,
        SUCCESS, NO_CHANGES, FAILED, CANCELLED
    }

    private val _progressFlow = MutableStateFlow(
        SyncProgress(SyncStage.PREPARING, 0f)
    )
    val progressFlow: StateFlow<SyncProgress> = _progressFlow.asStateFlow()

    private fun emitProgress(
        stage: SyncStage,
        progress: Float,
        currentItem: String = "",
        completedItems: Int = 0,
        totalItems: Int = 0,
        bytesUploaded: Long = _progressFlow.value.bytesUploaded,
        totalBytes: Long = _progressFlow.value.totalBytes
    ) {
        val now = System.currentTimeMillis()
        _progressFlow.value = SyncProgress(
            stage = stage,
            progress = progress,
            currentItem = currentItem,
            completedItems = completedItems,
            totalItems = totalItems,
            bytesUploaded = bytesUploaded,
            totalBytes = totalBytes,
            startedAt = if (stage != _progressFlow.value.stage) now else _progressFlow.value.startedAt,
            completedAt = if (stage == SyncStage.SUCCESS || stage == SyncStage.FAILED || stage == SyncStage.NO_CHANGES || stage == SyncStage.CANCELLED) now else 0L
        )
    }

    private fun emitFailure(error: GitOFYError) {
        _progressFlow.value = SyncProgress(
            stage = SyncStage.FAILED,
            progress = _progressFlow.value.progress,
            error = error.message,
            startedAt = _progressFlow.value.startedAt,
            completedAt = System.currentTimeMillis()
        )
    }

    /**
     * PRD §3: Create Repository flow.
     *
     * Validate ZIP → Extract → Normalize root → Create GitHub repo →
     * Upload all files → Commit → Push → Verify.
     */
    suspend fun createRepository(
        zipInputStream: InputStream,
        repoName: String,
        repoDescription: String,
        isPrivate: Boolean,
        commitMessage: String,
        operationDir: File
    ): SyncResult {
        try {
            val operationJob = coroutineContext[Job]
            // Step 0: Prepare
            emitProgress(SyncStage.PREPARING, 0.05f)
            val sourceZip = File(operationDir, "source.zip")
            zipInputStream.use { input ->
                sourceZip.outputStream().use { output -> input.copyTo(output) }
            }
            GITOFYLogger.i("SyncEngine: ZIP copied to ${sourceZip.absolutePath}")

            // Validate ZIP (PRD §5)
            val validation = zipExtractor.validateZip(sourceZip)
            if (!validation.isValid) {
                emitFailure(GitOFYError.ZipError(validation.error ?: "Invalid ZIP"))
                return SyncResult.Failed(GitOFYError.ZipError(validation.error ?: "Invalid ZIP"))
            }

            // Extract ZIP (PRD §5 — secure extraction)
            val extractDir = File(operationDir, "extracted")
            val extractResult = zipExtractor.extractZip(sourceZip, extractDir)
            if (extractResult.isFailure) {
                val error = extractResult.exceptionOrNull() as? GitOFYError
                    ?: GitOFYError.ZipError("Extraction failed")
                emitFailure(error)
                return SyncResult.Failed(error)
            }

            // Normalize project root (PRD §4)
            val projectRoot = zipExtractor.detectProjectRoot(extractDir) ?: extractDir
            GITOFYLogger.i("SyncEngine: Project root = ${projectRoot.absolutePath}")

            // Always include the two GITOFY CI workflows in the exact project
            // snapshot that will be committed. This keeps newly created repos
            // immediately runnable through Debug + Release workflow dispatch.
            workflowInjector.injectWorkflows(projectRoot.absolutePath).getOrElse {
                val error = GitOFYError.GitError("Could not prepare GitHub Actions workflows: ${it.message}")
                emitFailure(error)
                return SyncResult.Failed(error)
            }

            emitProgress(SyncStage.PREPARING, 0.10f)

            // Step 1: Check repository / authenticate
            emitProgress(SyncStage.CHECKING_REPOSITORY, 0.15f)
            val token = secureStorage.getToken()
            if (token == null) {
                val error = GitOFYError.AuthenticationRequired()
                emitFailure(error)
                return SyncResult.Failed(error)
            }
            val userLogin = secureStorage.getUserLogin()
            if (userLogin == null) {
                val error = GitOFYError.AuthenticationRequired()
                emitFailure(error)
                return SyncResult.Failed(error)
            }

            // Create GitHub repository via API
            val createRepoRequest = com.gitofy.data.remote.dto.CreateRepoRequest(
                name = repoName,
                description = repoDescription.ifBlank { null },
                private = isPrivate,
                autoInit = false
            )
            val createRepoResult = safeApiCall { apiService.createRepository(createRepoRequest) }
            if (createRepoResult.isFailure) {
                val error = createRepoResult.exceptionOrNull() as? GitOFYError
                    ?: GitOFYError.GitHubApiError(0, "Repository creation failed")
                emitFailure(error)
                return SyncResult.Failed(error)
            }
            val createdRepo = createRepoResult.getOrNull()!!
            val ownerLogin = createdRepo.ownerLogin.ifBlank { userLogin }

            emitProgress(SyncStage.CHECKING_REPOSITORY, 0.20f)

            // Step 2: Collect all local files
            val localFiles = collectLocalFiles(projectRoot)
            val totalFiles = localFiles.size
            val totalBytes = localFiles.sumOf { it.second.length() }

            GITOFYLogger.i("SyncEngine: ${totalFiles} files to upload, ${totalBytes} bytes total")

            // Step 3: Native libgit2 upload — one index, one tree, one commit, one push.
            emitProgress(SyncStage.UPLOADING, 0.25f, totalItems = totalFiles, totalBytes = totalBytes, bytesUploaded = 0L)
            val commitSha = gitNativeManager.pushDirectoryToGithub(
                repoUrl = createdRepo.htmlUrl.ifBlank { "https://github.com/$ownerLogin/$repoName.git" },
                token = token,
                directory = projectRoot,
                branch = createdRepo.defaultBranch.ifBlank { "main" },
                commitMessage = commitMessage,
                userName = userLogin,
                userEmail = "$userLogin@users.noreply.github.com",
                callback = object : GitNativeManager.ProgressCallback {
                    override fun onProgress(uploadedBytes: Long, totalBytes: Long, filesCompleted: Int, totalFiles: Int, currentFile: String) {
                        emitProgress(
                            SyncStage.UPLOADING,
                            (0.25f + 0.55f * if (totalBytes > 0) uploadedBytes.toFloat() / totalBytes else 0f).coerceIn(0.25f, 0.80f),
                            currentItem = currentFile, completedItems = filesCompleted, totalItems = totalFiles,
                            bytesUploaded = uploadedBytes.coerceAtMost(totalBytes), totalBytes = totalBytes
                        )
                    }
                    override fun onStage(stage: String) { GITOFYLogger.i("libgit2 create: $stage") }
                    override fun isCancelled(): Boolean = operationJob?.isActive != true
                }
            ).getOrElse { error ->
                val mapped = error as? GitOFYError ?: GitOFYError.GitHubApiError(0, error.message ?: "Native Git push failed")
                emitFailure(mapped)
                return SyncResult.Failed(mapped)
            }

            emitProgress(
                SyncStage.CREATING_COMMIT, 0.90f,
                currentItem = "Commit ${commitSha.take(7)}", completedItems = totalFiles, totalItems = totalFiles,
                bytesUploaded = totalBytes, totalBytes = totalBytes
            )
            // Step 4: Verify remote
            emitProgress(
                SyncStage.VERIFYING, 0.95f,
                completedItems = totalFiles,
                totalItems = totalFiles,
                bytesUploaded = totalBytes,
                totalBytes = totalBytes
            )
            val verifyResult = safeApiCall { apiService.getBranch(ownerLogin, repoName, createdRepo.defaultBranch.ifBlank { "main" }) }
            val verifiedBranch = verifyResult.getOrElse { error ->
                val mapped = error as? GitOFYError ?: GitOFYError.GitError("Verification failed: ${error.message}")
                emitFailure(mapped)
                return SyncResult.Failed(mapped)
            }
            if (verifiedBranch.commit?.sha != commitSha) {
                val error = GitOFYError.GitError("Verification failed: remote branch does not point to uploaded commit")
                emitFailure(error)
                return SyncResult.Failed(error)
            }

            val workflowDispatch = dispatchRequiredWorkflows(
                ownerLogin, repoName, createdRepo.defaultBranch.ifBlank { "main" }
            )
            if (workflowDispatch.isFailure) {
                val error = GitOFYError.GitError(workflowDispatch.exceptionOrNull()?.message ?: "Could not start GitHub Actions workflows")
                emitFailure(error)
                return SyncResult.Failed(error)
            }

            // Success
            emitProgress(
                SyncStage.SUCCESS, 1.0f,
                completedItems = totalFiles,
                totalItems = totalFiles,
                bytesUploaded = totalBytes,
                totalBytes = totalBytes
            )
            GITOFYLogger.i("SyncEngine: Repository created successfully — $ownerLogin/$repoName")

            return SyncResult.Created(
                ownerLogin = ownerLogin,
                repoName = repoName,
                filesPushed = totalFiles,
                commitSha = commitSha
            )
        } catch (e: Exception) {
            GITOFYLogger.e("SyncEngine create failed", throwable = e)
            val error = GitOFYError.UnknownError(e.message ?: "Unknown error")
            emitFailure(error)
            return SyncResult.Failed(error)
        }
    }

    /**
     * PRD §10-13: Update Repository flow — full project sync.
     *
     * Load remote tree → Load local tree → Diff → Apply changes
     * (add/update/delete) → Verify.
     *
     * Final state: GitHub Repository == Selected ZIP Project
     * (excluding excluded files).
     */
    suspend fun updateRepository(
        sourceZip: File,
        ownerLogin: String,
        repoName: String,
        commitMessage: String,
        operationDir: File
    ): SyncResult {
        try {
            val operationJob = coroutineContext[Job]
            // Step 0: Prepare.
            //
            // IMPORTANT: `sourceZip` is an already-written, immutable input —
            // it must be a plain File reference from here on, NEVER an
            // InputStream that gets copied back onto operationDir/source.zip.
            // The historical "ZIP file is empty" bug was exactly that: this
            // method used to open operationDir/source.zip for OUTPUT while a
            // stream over that same path/file was still being read as INPUT,
            // which truncates the file to 0 bytes before it can be copied.
            // source.zip must never be opened as a destination again.
            emitProgress(SyncStage.PREPARING, 0.05f)
            GITOFYLogger.i("[RepositoryUpdate] Operation started")
            GITOFYLogger.i("[RepositoryUpdate] Source ZIP: ${sourceZip.absolutePath}")

            // Stage 1: Source ZIP presence/size (PRD §13/§15)
            if (!sourceZip.isFile) {
                val error = GitOFYError.ZipError("Source ZIP is missing")
                GITOFYLogger.e("[RepositoryUpdate] Update failed at stage=SOURCE_ZIP: Source ZIP is missing")
                emitFailure(error)
                return SyncResult.Failed(error)
            }
            val sourceZipSize = sourceZip.length()
            GITOFYLogger.i("[RepositoryUpdate] ZIP size: $sourceZipSize")
            if (sourceZipSize <= 0L) {
                val error = GitOFYError.ZipError("Source ZIP is empty")
                GITOFYLogger.e("[RepositoryUpdate] Update failed at stage=SOURCE_ZIP: Source ZIP is empty")
                emitFailure(error)
                return SyncResult.Failed(error)
            }

            // Stage 2: Validate ZIP structure (PRD §13/§15)
            GITOFYLogger.i("[RepositoryUpdate] ZIP validation started")
            val validation = zipExtractor.validateZip(sourceZip)
            if (!validation.isValid) {
                val error = GitOFYError.ZipError(
                    "Source ZIP is invalid or corrupted".let { base ->
                        validation.error?.let { detail -> "$base: $detail" } ?: base
                    }
                )
                GITOFYLogger.e("[RepositoryUpdate] Update failed at stage=ZIP_VALIDATION: ${validation.error}")
                emitFailure(error)
                return SyncResult.Failed(error)
            }
            GITOFYLogger.i("[RepositoryUpdate] ZIP validation successful")

            // Stage 3: Extract into a SEPARATE directory — never back into source.zip's path.
            GITOFYLogger.i("[RepositoryUpdate] Extraction started")
            val extractDir = File(operationDir, "extracted")
            val extractResult = zipExtractor.extractZip(sourceZip, extractDir)
            if (extractResult.isFailure) {
                val error = extractResult.exceptionOrNull() as? GitOFYError
                    ?: GitOFYError.ZipError("Could not extract repository ZIP")
                GITOFYLogger.e(
                    "[RepositoryUpdate] Update failed at stage=EXTRACTION",
                    throwable = extractResult.exceptionOrNull()
                )
                emitFailure(error)
                return SyncResult.Failed(error)
            }
            GITOFYLogger.i("[RepositoryUpdate] Extraction completed")

            // Normalize project root (PRD §4)
            val projectRoot = zipExtractor.detectProjectRoot(extractDir) ?: extractDir

            workflowInjector.injectWorkflows(projectRoot.absolutePath).getOrElse {
                val error = GitOFYError.GitError("Could not prepare GitHub Actions workflows: ${it.message}")
                emitFailure(error)
                return SyncResult.Failed(error)
            }

            emitProgress(SyncStage.PREPARING, 0.10f)

            // Step 1: Check repository / authenticate
            emitProgress(SyncStage.CHECKING_REPOSITORY, 0.15f)
            val token = secureStorage.getToken()
            if (token == null) {
                val error = GitOFYError.AuthenticationRequired()
                emitFailure(error)
                return SyncResult.Failed(error)
            }

            // Verify repository exists and user has write access
            val repoResult = safeApiCall { apiService.getRepository(ownerLogin, repoName) }
            if (repoResult.isFailure) {
                val error = repoResult.exceptionOrNull() as? GitOFYError
                    ?: GitOFYError.ResourceNotFound()
                emitFailure(error)
                return SyncResult.Failed(error)
            }
            val repoInfo = repoResult.getOrNull()!!
            val permissions = repoInfo.permissions
            if (permissions != null && !permissions.push && !permissions.admin) {
                val error = GitOFYError.PermissionDenied()
                emitFailure(error)
                return SyncResult.Failed(error)
            }

            val defaultBranch = repoInfo.defaultBranch.ifBlank { "main" }

            emitProgress(SyncStage.CHECKING_REPOSITORY, 0.20f)

            // Step 2: Load remote tree (PRD §10-11)
            emitProgress(SyncStage.COMPARING, 0.25f)
            val remoteSnapshot = loadRemoteTree(ownerLogin, repoName, defaultBranch)
            val remoteTree = remoteSnapshot.entries
            val remoteFiles = remoteTree.associate { it.path to it.sha }

            // Step 3: Load local tree
            val localFiles = collectLocalFiles(projectRoot)
            val localFileMap = mutableMapOf<String, File>()
            val localShaMap = mutableMapOf<String, String>()
            for ((path, file) in localFiles) {
                localFileMap[path] = file
                localShaMap[path] = computeGitBlobSha(file)
            }

            // Step 4: Compute diff (PRD §11-12)
            val changes = mutableListOf<FileChange>()

            // ADDED + MODIFIED: files in local tree
            for ((path, file) in localFiles) {
                val remoteSha = remoteFiles[path]
                val localSha = localShaMap[path]!!
                if (remoteSha == null) {
                    changes.add(FileChange(path, ChangeType.ADDED, localSha = localSha))
                } else if (remoteSha != localSha) {
                    changes.add(FileChange(path, ChangeType.MODIFIED, localSha = localSha, remoteSha = remoteSha))
                } else {
                    changes.add(FileChange(path, ChangeType.UNCHANGED, localSha = localSha, remoteSha = remoteSha))
                }
            }

            // DELETED: files in remote but not in local
            for ((path, sha) in remoteFiles) {
                if (!localFileMap.containsKey(path)) {
                    changes.add(FileChange(path, ChangeType.DELETED, remoteSha = sha))
                }
            }

            val addedCount = changes.count { it.changeType == ChangeType.ADDED }
            val modifiedCount = changes.count { it.changeType == ChangeType.MODIFIED }
            val deletedCount = changes.count { it.changeType == ChangeType.DELETED }
            val unchangedCount = changes.count { it.changeType == ChangeType.UNCHANGED }

            GITOFYLogger.i("SyncEngine: Diff — Added=$addedCount, Modified=$modifiedCount, Deleted=$deletedCount, Unchanged=$unchangedCount")

            emitProgress(SyncStage.COMPARING, 0.35f)

            // Step 5: Check for no changes (PRD §19 — "Nothing to Push" fix)
            val mutableChanges = changes.filter { it.changeType != ChangeType.UNCHANGED }
            if (mutableChanges.isEmpty()) {
                // PRD §19: This is NOT a failure — the project is already up to date.
                emitProgress(SyncStage.NO_CHANGES, 1.0f)
                GITOFYLogger.i("SyncEngine: No changes detected — repository already up to date")
                return SyncResult.NoChanges
            }

            // Step 6: Use the same native libgit2 pipeline for updates as for creates.
            // The native engine clones the current branch, replaces the working tree,
            // builds one tree/commit and performs exactly one push. This avoids N
            // REST blob uploads and keeps create/update semantics identical.
            emitProgress(
                SyncStage.PREPARING_CHANGES, 0.40f,
                totalItems = localFiles.size,
                totalBytes = localFiles.sumOf { it.second.length() },
                bytesUploaded = 0L
            )

            val uploadTotalBytes = localFiles.sumOf { it.second.length() }
            GITOFYLogger.i("[RepositoryUpdate] Git sync started")
            GITOFYLogger.i("[RepositoryUpdate] libgit2 sync started")
            val nativeResult = gitNativeManager.syncDirectoryToGithub(
                repoUrl = repoInfo.htmlUrl.ifBlank { "https://github.com/$ownerLogin/$repoName.git" },
                token = token,
                sourceDirectory = projectRoot,
                branch = defaultBranch,
                commitMessage = commitMessage,
                userName = secureStorage.getUserLogin(),
                userEmail = secureStorage.getUserLogin()?.let { "$it@users.noreply.github.com" },
                callback = object : GitNativeManager.ProgressCallback {
                    override fun onProgress(
                        uploadedBytes: Long, totalBytes: Long, filesCompleted: Int,
                        totalFiles: Int, currentFile: String
                    ) {
                        emitProgress(
                            SyncStage.UPLOADING,
                            (0.40f + 0.45f * if (totalBytes > 0) uploadedBytes.toFloat() / totalBytes else 0f).coerceIn(0.40f, 0.85f),
                            currentItem = currentFile,
                            completedItems = filesCompleted,
                            totalItems = totalFiles,
                            bytesUploaded = uploadedBytes.coerceAtMost(totalBytes),
                            totalBytes = totalBytes
                        )
                    }

                    override fun onStage(stage: String) {
                        GITOFYLogger.i("libgit2 update: $stage")
                    }

                    override fun isCancelled(): Boolean = operationJob?.isActive != true
                }
            ).getOrElse { error ->
                val mapped = error as? GitOFYError
                    ?: GitOFYError.GitHubApiError(0, error.message ?: "Failed to update repository")
                emitFailure(mapped)
                return SyncResult.Failed(mapped)
            }

            val commitResultSha = nativeResult
            if (commitResultSha == "NO_CHANGES") {
                emitProgress(SyncStage.NO_CHANGES, 1.0f, currentItem = "Repository already up to date")
                return SyncResult.NoChanges
            }
            GITOFYLogger.i("[RepositoryUpdate] Commit created")
            emitProgress(
                SyncStage.CREATING_COMMIT, 0.90f,
                currentItem = "Commit ${commitResultSha.take(7)}",
                completedItems = localFiles.size,
                totalItems = localFiles.size,
                bytesUploaded = uploadTotalBytes,
                totalBytes = uploadTotalBytes
            )
            GITOFYLogger.i("[RepositoryUpdate] Push started")
            // Step 7: Verify remote
            emitProgress(
                SyncStage.VERIFYING, 0.95f,
                completedItems = localFiles.size,
                totalItems = localFiles.size,
                bytesUploaded = uploadTotalBytes,
                totalBytes = uploadTotalBytes
            )
            val verifyResult = safeApiCall { apiService.getBranch(ownerLogin, repoName, defaultBranch) }
            val verifiedBranch = verifyResult.getOrElse { error ->
                val mapped = error as? GitOFYError ?: GitOFYError.GitError("Verification failed: ${error.message}")
                emitFailure(mapped)
                return SyncResult.Failed(mapped)
            }
            val latestCommitSha = commitResultSha
            if (verifiedBranch.commit?.sha != latestCommitSha) {
                val error = GitOFYError.GitError("Verification failed: remote branch does not point to synchronized commit")
                emitFailure(error)
                return SyncResult.Failed(error)
            }

            val workflowDispatch = dispatchRequiredWorkflows(ownerLogin, repoName, defaultBranch)
            if (workflowDispatch.isFailure) {
                val error = GitOFYError.GitError(workflowDispatch.exceptionOrNull()?.message ?: "Could not start GitHub Actions workflows")
                emitFailure(error)
                return SyncResult.Failed(error)
            }

            // Success
            emitProgress(
                SyncStage.SUCCESS, 1.0f,
                completedItems = localFiles.size,
                totalItems = localFiles.size,
                bytesUploaded = uploadTotalBytes,
                totalBytes = uploadTotalBytes
            )
            GITOFYLogger.i("SyncEngine: Repository updated — Added=$addedCount, Modified=$modifiedCount, Deleted=$deletedCount")
            GITOFYLogger.i("[RepositoryUpdate] Update completed")

            return SyncResult.Updated(
                ownerLogin = ownerLogin,
                repoName = repoName,
                added = addedCount,
                modified = modifiedCount,
                deleted = deletedCount,
                unchanged = unchangedCount,
                commitSha = latestCommitSha
            )
        } catch (e: Exception) {
            GITOFYLogger.e("SyncEngine update failed", throwable = e)
            GITOFYLogger.e("[RepositoryUpdate] Update failed", throwable = e)
            val error = e as? GitOFYError
                ?: GitOFYError.UnknownError(e.message ?: "Unknown error")
            emitFailure(error)
            return SyncResult.Failed(error)
        }
    }

    /** Dispatch both required CI workflows and fail loudly if GitHub rejects either. */
    private suspend fun dispatchRequiredWorkflows(owner: String, repo: String, branch: String): Result<Unit> {
        val workflows = listOf("build.yml" to "Debug", "release.yml" to "Release")
        for ((workflow, label) in workflows) {
            val response = runCatching {
                apiService.dispatchWorkflow(
                    owner = owner,
                    repo = repo,
                    workflowId = workflow,
                    request = DispatchWorkflowRequest(ref = branch)
                )
            }.getOrElse {
                return Result.failure(IllegalStateException("$label workflow could not be started: ${it.message}"))
            }
            if (!response.isSuccessful) {
                return Result.failure(IllegalStateException("$label workflow could not be started (HTTP ${response.code()})."))
            }
            GITOFYLogger.i("SyncEngine: $label workflow dispatched for $owner/$repo@$branch")
        }
        return Result.success(Unit)
    }

    /**
     * PRD §33: Load the remote file tree using the Git Trees API.
     * Returns a flat list of all blob (file) entries recursively.
     */
    private data class RemoteTreeSnapshot(
        val commitSha: String,
        val treeSha: String,
        val entries: List<GitTreeEntry>
    )

    private suspend fun loadRemoteTree(owner: String, repo: String, branch: String): RemoteTreeSnapshot {
        // First get the branch to find the commit SHA
        val branchResult = safeApiCall { apiService.getBranch(owner, repo, branch) }
        if (branchResult.isFailure) {
            val cause = branchResult.exceptionOrNull()
            GITOFYLogger.w("SyncEngine: Could not load branch $branch; aborting update", throwable = cause)
            throw (cause as? GitOFYError)
                ?: GitOFYError.GitHubApiError(0, "Could not load branch $branch")
        }

        val commitSha = branchResult.getOrNull()?.commit?.sha
            ?: throw GitOFYError.GitHubApiError(0, "GitHub returned no commit SHA for branch $branch")

        // Get the tree recursively
        val treeResult = safeApiCall { apiService.getGitTree(owner, repo, commitSha, recursive = 1) }
        if (treeResult.isFailure) {
            val cause = treeResult.exceptionOrNull()
            GITOFYLogger.w("SyncEngine: Could not load git tree; aborting update", throwable = cause)
            throw (cause as? GitOFYError)
                ?: GitOFYError.GitHubApiError(0, "Could not load the remote Git tree")
        }

        val tree = treeResult.getOrNull()!!
        if (tree.truncated) {
            throw GitOFYError.GitHubApiError(0, "GitHub returned a truncated repository tree; refusing to update because the remote state cannot be compared safely")
        }

        // Return only blob entries (files), not tree entries (directories)
        return RemoteTreeSnapshot(
            commitSha = commitSha,
            treeSha = tree.sha,
            entries = tree.tree.filter { it.type == "blob" }
        )
    }

    /**
     * Collect all files from the local project directory, excluding
     * generated artifacts (PRD §14).
     */
    private fun collectLocalFiles(projectRoot: File): List<Pair<String, File>> {
        val result = mutableListOf<Pair<String, File>>()
        val rootPath = projectRoot.absolutePath

        projectRoot.walkTopDown()
            .filter { it.isFile }
            .filter { file ->
                // PRD §14: Exclude generated directories
                val relativePath = file.absolutePath.removePrefix(rootPath).trimStart('/')
                !defaultExcludedDirs.any { dir ->
                    relativePath.startsWith("$dir/") || relativePath == dir
                }
            }
            .forEach { file ->
                val relativePath = file.absolutePath.removePrefix(rootPath).trimStart('/')
                result.add(relativePath to file)
            }

        return result.sortedBy { it.first }
    }

    /**
     * Compute the Git blob SHA for a file — used for diff comparison.
     * Git blob SHA = SHA-1 of "blob <size>\0<content>"
     */
    private fun computeGitBlobSha(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-1")
            digest.update("blob ${file.length()}\u0000".toByteArray())
            BufferedInputStream(file.inputStream(), 64 * 1024).use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Reset the progress flow for a new operation.
     */
    fun resetProgress() {
        _progressFlow.value = SyncProgress(SyncStage.PREPARING, 0f)
    }
}
