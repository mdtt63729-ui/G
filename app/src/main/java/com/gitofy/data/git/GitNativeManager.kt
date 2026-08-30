package com.gitofy.data.git

import android.content.Context
import com.gitofy.core.logging.GITOFYLogger
import com.gitofy.domain.model.GitOFYError
import com.gitofy.core.filesystem.SecureZipExtractor
import com.gitofy.domain.repository.GitRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Native Git engine backed by libgit2 through JNI.
 *
 * The Java/Kotlin layer owns lifecycle, secure credentials and Android storage.
 * Git object creation, indexing, pack building and push are performed by the
 * native libgit2 core. Credentials are supplied through libgit2's credential
 * callback and are never written to the remote URL.
 */
@Singleton
class GitNativeManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val zipExtractor: SecureZipExtractor
) : GitRepository {

    interface ProgressCallback {
        fun onProgress(
            uploadedBytes: Long,
            totalBytes: Long,
            filesCompleted: Int,
            totalFiles: Int,
            currentFile: String
        )

        fun onStage(stage: String)

        /** Return true when the owning Worker/request has been cancelled. */
        fun isCancelled(): Boolean = false
    }

    companion object {
        init {
            System.loadLibrary("native-git-bridge")
        }
    }

    private external fun nativePushDirectoryToGithub(
        repoUrl: String,
        token: String,
        directoryPath: String,
        branch: String,
        commitMessage: String,
        userName: String?,
        userEmail: String?,
        callback: ProgressCallback?
    ): String

    private external fun nativeSyncDirectoryToGithub(
        repoUrl: String,
        token: String,
        sourceDirectory: String,
        branch: String,
        commitMessage: String,
        userName: String?,
        userEmail: String?,
        workDirectory: String,
        callback: ProgressCallback?
    ): String

    private external fun nativeVersion(): String

    /**
     * PRD-compatible ZIP entry point. Android validates/extracts the SAF ZIP
     * safely, then the native libgit2 core performs Git object creation and push.
     */
    suspend fun pushZipToGithub(
        repoUrl: String,
        token: String,
        zipPath: String,
        branch: String,
        commitMessage: String = "GITOFY upload",
        userName: String? = null,
        userEmail: String? = null,
        callback: ProgressCallback? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val zipFile = File(zipPath)
        val validation = zipExtractor.validateZip(zipFile)
        if (!validation.isValid) {
            return@withContext Result.failure(
                GitOFYError.GitError(validation.error ?: "Invalid ZIP")
            )
        }
        val extractDir = File(context.filesDir, "gitofy_native_extract_${System.nanoTime()}")
        val extracted = zipExtractor.extractZip(zipFile, extractDir)
        if (extracted.isFailure) {
            return@withContext Result.failure(
                GitOFYError.GitError(extracted.exceptionOrNull()?.message ?: "ZIP extraction failed")
            )
        }
        val root = zipExtractor.detectProjectRoot(extractDir) ?: extractDir
        try {
            removeEmbeddedGitDirectories(root)
            pushDirectoryToGithub(
                repoUrl = repoUrl,
                token = token,
                directory = root,
                branch = branch,
                commitMessage = commitMessage,
                userName = userName,
                userEmail = userEmail,
                callback = callback
            )
        } finally {
            extractDir.deleteRecursively()
        }
    }

    suspend fun pushDirectoryToGithub(
        repoUrl: String,
        token: String,
        directory: File,
        branch: String,
        commitMessage: String,
        userName: String? = null,
        userEmail: String? = null,
        callback: ProgressCallback? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!directory.isDirectory) {
            return@withContext Result.failure(
                GitOFYError.GitError("Project directory does not exist")
            )
        }
        if (token.isBlank()) {
            return@withContext Result.failure(
                GitOFYError.GitError("GitHub authentication token is missing")
            )
        }
        if (repoUrl.isBlank() || branch.isBlank()) {
            return@withContext Result.failure(
                GitOFYError.GitError("GitHub repository URL or branch is missing")
            )
        }

        removeEmbeddedGitDirectories(directory)

        runCatching {
            val response = nativePushDirectoryToGithub(
                repoUrl,
                token,
                directory.absolutePath,
                branch,
                commitMessage,
                userName,
                userEmail,
                callback
            )
            if (response.startsWith("OK:")) {
                response.removePrefix("OK:")
            } else {
                throw GitOFYError.GitError(response.removePrefix("ERROR:"))
            }
        }.onFailure {
            GITOFYLogger.e("libgit2 push failed", throwable = it)
        }
    }

    private fun removeEmbeddedGitDirectories(root: File) {
        root.walkTopDown()
            .filter { it.isDirectory && it.name == ".git" }
            .toList()
            .forEach { it.deleteRecursively() }
    }


    suspend fun syncDirectoryToGithub(
        repoUrl: String, token: String, sourceDirectory: File, branch: String,
        commitMessage: String, userName: String? = null, userEmail: String? = null,
        callback: ProgressCallback? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!sourceDirectory.isDirectory) return@withContext Result.failure(GitOFYError.GitError("Source project directory does not exist"))
        if (token.isBlank()) return@withContext Result.failure(GitOFYError.GitError("GitHub authentication token is missing"))
        if (repoUrl.isBlank() || branch.isBlank()) return@withContext Result.failure(GitOFYError.GitError("GitHub repository URL or branch is missing"))
        val workDir = File(context.filesDir, "gitofy_native_update_${System.nanoTime()}")
        try {
            runCatching {
                val response = nativeSyncDirectoryToGithub(repoUrl, token, sourceDirectory.absolutePath, branch, commitMessage, userName, userEmail, workDir.absolutePath, callback)
                if (response.startsWith("OK:")) response.removePrefix("OK:")
                else throw GitOFYError.GitError(response.removePrefix("ERROR:"))
            }.onFailure { GITOFYLogger.e("libgit2 update sync failed", throwable = it) }
        } finally {
            workDir.deleteRecursively()
        }
    }

    fun libgit2Version(): String = runCatching { nativeVersion() }.getOrDefault("unknown")

    override suspend fun initialize(directory: String): Result<Unit> = withContext(Dispatchers.IO) {
        // Native push creates the repository itself. This method remains available
        // for the domain contract and intentionally does not run a second init.
        runCatching {
            File(directory).mkdirs()
            Unit
        }
    }

    override suspend fun configureUser(directory: String, name: String, email: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Native Git config is applied during push; use pushZipToGithub/pushDirectoryToGithub."))

    override suspend fun addAll(directory: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Native Git indexing is an atomic part of the push operation."))

    override suspend fun commit(directory: String, message: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Native Git commit is an atomic part of the push operation."))

    override suspend fun setRemote(directory: String, remoteUrl: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Native Git remote configuration is scoped to the push operation."))

    override suspend fun push(directory: String, token: String, remoteUrl: String): Result<Unit> =
        pushDirectoryToGithub(
            repoUrl = remoteUrl,
            token = token,
            directory = File(directory),
            branch = "main",
            commitMessage = "GITOFY upload"
        ).map { Unit }

    override suspend fun verifyRemote(directory: String, remoteUrl: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Remote verification is performed against the GitHub branch after push."))

    override suspend fun getHeadCommitSha(directory: String): Result<String> =
        Result.failure(UnsupportedOperationException("The native push returns the authoritative commit SHA."))

    override fun cleanup(directory: String) {
        runCatching { File(directory).deleteRecursively() }
    }
}
