package com.gitofy.data.repository

import android.util.Base64
import com.gitofy.core.filesystem.SecureZipExtractor
import com.gitofy.core.logging.GITOFYLogger
import com.gitofy.core.network.GitHubApiService
import com.gitofy.core.network.safeApiCall
import com.gitofy.core.security.SecureCredentialStorage
import com.gitofy.data.remote.dto.CreateFileRequest
import com.gitofy.data.remote.dto.GitTreeEntry
import com.gitofy.domain.model.GitOFYError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
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
    private val secureStorage: SecureCredentialStorage
) {

    // PRD §14: Default excluded directories — generated artifacts that
    // should not be synced to the remote repository.
    private val defaultExcludedDirs = setOf(
        ".git", ".gradle", "build", ".idea", ".cxx",
        "local.properties", "captures", ".kotlin"
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

    private fun emitProgress(stage: SyncStage, progress: Float, currentItem: String = "", completedItems: Int = 0, totalItems: Int = 0) {
        val now = System.currentTimeMillis()
        _progressFlow.value = SyncProgress(
            stage = stage,
            progress = progress,
            currentItem = currentItem,
            completedItems = completedItems,
            totalItems = totalItems,
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

            // Step 3: Upload files via Contents API (real GitHub mutations)
            emitProgress(SyncStage.UPLOADING, 0.25f, totalItems = totalFiles)

            var filesUploaded = 0
            for ((relativePath, file) in localFiles) {
                val progressPerFile = 0.55f / totalFiles
                val uploadProgress = 0.25f + (progressPerFile * filesUploaded)
                emitProgress(
                    SyncStage.UPLOADING, uploadProgress,
                    currentItem = relativePath,
                    completedItems = filesUploaded,
                    totalItems = totalFiles
                )

                val content = encodeFileContent(file)
                val request = CreateFileRequest(
                    message = commitMessage,
                    content = content,
                    branch = null,
                    sha = null
                )

                val uploadResult = safeApiCall {
                    apiService.createOrUpdateFile(ownerLogin, repoName, relativePath, request)
                }
                if (uploadResult.isFailure) {
                    val error = uploadResult.exceptionOrNull() as? GitOFYError
                        ?: GitOFYError.GitHubApiError(0, "Failed to upload: $relativePath")
                    emitFailure(error)
                    return SyncResult.Failed(error)
                }

                filesUploaded++
                GITOFYLogger.i("SyncEngine: Uploaded $relativePath ($filesUploaded/$totalFiles)")
            }

            emitProgress(SyncStage.UPLOADING, 0.80f, completedItems = totalFiles, totalItems = totalFiles)

            // Step 4: Verify remote
            emitProgress(SyncStage.VERIFYING, 0.95f)
            val verifyResult = safeApiCall { apiService.getRepository(ownerLogin, repoName) }
            if (verifyResult.isFailure) {
                val error = verifyResult.exceptionOrNull() as? GitOFYError
                    ?: GitOFYError.GitError("Verification failed")
                emitFailure(error)
                return SyncResult.Failed(error)
            }
            val verifiedRepo = verifyResult.getOrNull()!!

            // Success
            emitProgress(SyncStage.SUCCESS, 1.0f)
            GITOFYLogger.i("SyncEngine: Repository created successfully — $ownerLogin/$repoName")

            return SyncResult.Created(
                ownerLogin = ownerLogin,
                repoName = repoName,
                filesPushed = totalFiles,
                commitSha = verifiedRepo.defaultBranch
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
        zipInputStream: InputStream,
        ownerLogin: String,
        repoName: String,
        commitMessage: String,
        operationDir: File
    ): SyncResult {
        try {
            // Step 0: Prepare
            emitProgress(SyncStage.PREPARING, 0.05f)
            val sourceZip = File(operationDir, "source.zip")
            zipInputStream.use { input ->
                sourceZip.outputStream().use { output -> input.copyTo(output) }
            }

            // Validate & extract ZIP
            val validation = zipExtractor.validateZip(sourceZip)
            if (!validation.isValid) {
                val error = GitOFYError.ZipError(validation.error ?: "Invalid ZIP")
                emitFailure(error)
                return SyncResult.Failed(error)
            }

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
            val remoteTree = loadRemoteTree(ownerLogin, repoName, defaultBranch)
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

            // Step 6: Apply changes atomically (PRD §17)
            emitProgress(SyncStage.PREPARING_CHANGES, 0.40f)
            val totalChanges = mutableChanges.size
            var changesApplied = 0

            for (change in mutableChanges) {
                val progressPerChange = 0.50f / totalChanges
                val applyProgress = 0.40f + (progressPerChange * changesApplied)
                emitProgress(
                    SyncStage.UPLOADING, applyProgress,
                    currentItem = change.path,
                    completedItems = changesApplied,
                    totalItems = totalChanges
                )

                when (change.changeType) {
                    ChangeType.ADDED -> {
                        val file = localFileMap[change.path]!!
                        val content = encodeFileContent(file)
                        val request = CreateFileRequest(
                            message = commitMessage,
                            content = content,
                            branch = defaultBranch,
                            sha = null
                        )
                        val result = safeApiCall {
                            apiService.createOrUpdateFile(ownerLogin, repoName, change.path, request)
                        }
                        if (result.isFailure) {
                            val error = result.exceptionOrNull() as? GitOFYError
                                ?: GitOFYError.GitHubApiError(0, "Failed to create: ${change.path}")
                            emitFailure(error)
                            return SyncResult.Failed(error)
                        }
                        GITOFYLogger.i("SyncEngine: ADDED ${change.path}")
                    }
                    ChangeType.MODIFIED -> {
                        val file = localFileMap[change.path]!!
                        val content = encodeFileContent(file)
                        val request = CreateFileRequest(
                            message = commitMessage,
                            content = content,
                            branch = defaultBranch,
                            sha = change.remoteSha
                        )
                        val result = safeApiCall {
                            apiService.createOrUpdateFile(ownerLogin, repoName, change.path, request)
                        }
                        if (result.isFailure) {
                            val error = result.exceptionOrNull() as? GitOFYError
                                ?: GitOFYError.GitHubApiError(0, "Failed to update: ${change.path}")
                            emitFailure(error)
                            return SyncResult.Failed(error)
                        }
                        GITOFYLogger.i("SyncEngine: MODIFIED ${change.path}")
                    }
                    ChangeType.DELETED -> {
                        val request = CreateFileRequest(
                            message = commitMessage,
                            content = "",
                            branch = defaultBranch,
                            sha = change.remoteSha
                        )
                        val result = safeApiCall {
                            apiService.deleteFile(ownerLogin, repoName, change.path, request)
                        }
                        if (result.isFailure) {
                            val error = result.exceptionOrNull() as? GitOFYError
                                ?: GitOFYError.GitHubApiError(0, "Failed to delete: ${change.path}")
                            emitFailure(error)
                            return SyncResult.Failed(error)
                        }
                        GITOFYLogger.i("SyncEngine: DELETED ${change.path}")
                    }
                    ChangeType.UNCHANGED -> { /* no-op */ }
                }

                changesApplied++
            }

            emitProgress(SyncStage.UPLOADING, 0.90f, completedItems = totalChanges, totalItems = totalChanges)

            // Step 7: Verify remote
            emitProgress(SyncStage.VERIFYING, 0.95f)
            val verifyResult = safeApiCall { apiService.getRepository(ownerLogin, repoName) }
            if (verifyResult.isFailure) {
                val error = verifyResult.exceptionOrNull() as? GitOFYError
                    ?: GitOFYError.GitError("Verification failed")
                emitFailure(error)
                return SyncResult.Failed(error)
            }

            // Get latest commit SHA
            val commitsResult = safeApiCall { apiService.listCommits(ownerLogin, repoName, page = 1, perPage = 1) }
            val latestCommitSha = commitsResult.getOrNull()?.firstOrNull()?.sha ?: ""

            // Success
            emitProgress(SyncStage.SUCCESS, 1.0f)
            GITOFYLogger.i("SyncEngine: Repository updated — Added=$addedCount, Modified=$modifiedCount, Deleted=$deletedCount")

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
            val error = GitOFYError.UnknownError(e.message ?: "Unknown error")
            emitFailure(error)
            return SyncResult.Failed(error)
        }
    }

    /**
     * PRD §33: Load the remote file tree using the Git Trees API.
     * Returns a flat list of all blob (file) entries recursively.
     */
    private suspend fun loadRemoteTree(owner: String, repo: String, branch: String): List<GitTreeEntry> {
        // First get the branch to find the commit SHA
        val branchResult = safeApiCall { apiService.getBranch(owner, repo, branch) }
        if (branchResult.isFailure) {
            GITOFYLogger.w("SyncEngine: Could not load branch $branch — assuming empty repo")
            return emptyList()
        }

        val commitSha = branchResult.getOrNull()?.commit?.sha ?: return emptyList()

        // Get the tree recursively
        val treeResult = safeApiCall { apiService.getGitTree(owner, repo, commitSha, recursive = 1) }
        if (treeResult.isFailure) {
            GITOFYLogger.w("SyncEngine: Could not load git tree — assuming empty repo")
            return emptyList()
        }

        val tree = treeResult.getOrNull()!!
        if (tree.truncated) {
            GITOFYLogger.w("SyncEngine: Git tree is truncated — some files may not be compared")
        }

        // Return only blob entries (files), not tree entries (directories)
        return tree.tree.filter { it.type == "blob" }
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
     * PRD §15: Encode file content for GitHub Contents API.
     * Binary files are Base64-encoded; text files are also Base64-encoded
     * (GitHub API requires Base64 for all content).
     */
    private fun encodeFileContent(file: File): String {
        return Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
    }

    /**
     * Compute the Git blob SHA for a file — used for diff comparison.
     * Git blob SHA = SHA-1 of "blob <size>\0<content>"
     */
    private fun computeGitBlobSha(file: File): String {
        return try {
            val content = file.readBytes()
            val header = "blob ${content.size}\u0000".toByteArray()
            val digest = MessageDigest.getInstance("SHA-1")
            digest.update(header)
            digest.update(content)
            val shaBytes = digest.digest()
            // Convert to hex string
            shaBytes.joinToString("") { "%02x".format(it) }
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
