package com.gitofy.data.git

import com.gitofy.core.logging.GITOFYLogger
import com.gitofy.domain.model.GitOFYError
import com.gitofy.domain.repository.GitRepository
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * JGit engine — isolated behind domain interface.
 * PRD 17: Never expose JGit types to Compose or domain UI models.
 * PRD 8.1: Never include credentials in Git remote URLs — use credentials provider instead.
 */
@Singleton
class JGitEngine @Inject constructor() : GitRepository {

    override suspend fun initialize(directory: String): Result<Unit> {
        return try {
            val dir = File(directory)
            Git.init().setDirectory(dir).call()
            GITOFYLogger.i("Git repository initialized at $directory")
            Result.success(Unit)
        } catch (e: GitAPIException) {
            GITOFYLogger.e("Git init failed", throwable = e)
            Result.failure(GitOFYError.GitError("Initialization failed: ${e.message ?: "Unknown error"}"))
        }
    }

    override suspend fun configureUser(directory: String, name: String, email: String): Result<Unit> {
        return try {
            val config = Git.open(File(directory)).repository.config
            config.setString("user", null, "name", name)
            config.setString("user", null, "email", email)
            config.save()
            Result.success(Unit)
        } catch (e: Exception) {
            GITOFYLogger.e("Git config failed", throwable = e)
            Result.failure(GitOFYError.GitError("Config failed: ${e.message ?: "Unknown error"}"))
        }
    }

    override suspend fun addAll(directory: String): Result<Unit> {
        return try {
            Git.open(File(directory)).add().addFilepattern(".").call()
            Result.success(Unit)
        } catch (e: GitAPIException) {
            GITOFYLogger.e("Git add failed", throwable = e)
            Result.failure(GitOFYError.GitError("Staging failed: ${e.message ?: "Unknown error"}"))
        }
    }

    override suspend fun commit(directory: String, message: String): Result<Unit> {
        return try {
            Git.open(File(directory)).commit().setMessage(message).call()
            GITOFYLogger.i("Committed: $message")
            Result.success(Unit)
        } catch (e: GitAPIException) {
            GITOFYLogger.e("Git commit failed", throwable = e)
            Result.failure(GitOFYError.GitError("Commit failed: ${e.message ?: "Unknown error"}"))
        }
    }

    override suspend fun setRemote(directory: String, remoteUrl: String): Result<Unit> {
        return try {
            val git = Git.open(File(directory))
            val config = git.repository.config
            // PRD 8.1: Never include credentials in Git remote URLs
            // Store clean URL without token
            val cleanUrl = remoteUrl
                .replace(Regex("https://[^@]+@"), "https://")
                .replace(Regex("http://[^@]+@"), "http://")
            config.setString("remote", "origin", "url", cleanUrl)
            config.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*")
            config.save()
            Result.success(Unit)
        } catch (e: Exception) {
            GITOFYLogger.e("Git setRemote failed", throwable = e)
            Result.failure(GitOFYError.GitError("Remote setup failed: ${e.message ?: "Unknown error"}"))
        }
    }

    override suspend fun push(directory: String, token: String, remoteUrl: String): Result<Unit> {
        return try {
            val git = Git.open(File(directory))
            // PRD 8.1: Use credentials provider, never embed token in URL
            val credentialsProvider = UsernamePasswordCredentialsProvider(token, "")

            git.push()
                .setRemote("origin")
                .setCredentialsProvider(credentialsProvider)
                .setPushAll()
                .call()

            GITOFYLogger.i("Pushed to remote successfully")
            Result.success(Unit)
        } catch (e: GitAPIException) {
            GITOFYLogger.e("Git push failed", throwable = e)
            Result.failure(GitOFYError.GitError("Push failed: ${e.message ?: "Unknown error"}"))
        }
    }

    override suspend fun verifyRemote(directory: String, remoteUrl: String): Result<Unit> {
        return try {
            val git = Git.open(File(directory))
            val config = git.repository.config
            val storedUrl = config.getString("remote", "origin", "url")
            if (storedUrl.isNullOrEmpty()) {
                Result.failure(GitOFYError.GitError("Remote not configured"))
            } else {
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(GitOFYError.GitError("Verification failed: ${e.message ?: "Unknown error"}"))
        }
    }

    override suspend fun getHeadCommitSha(directory: String): Result<String> {
        return try {
            val git = Git.open(File(directory))
            val head = git.repository.resolve("HEAD")
            if (head != null) {
                Result.success(head.name.substring(0, 7))
            } else {
                Result.success("")
            }
        } catch (e: Exception) {
            Result.success("")
        }
    }

    override fun cleanup(directory: String) {
        try {
            val dir = File(directory)
            dir.deleteRecursively()
            GITOFYLogger.i("Cleaned up temporary directory: $directory")
        } catch (e: Exception) {
            GITOFYLogger.w("Cleanup failed: ${e.message}")
        }
    }
}
