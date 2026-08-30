package com.gitofy.data.git

import com.gitofy.core.logging.GITOFYLogger
import com.gitofy.domain.model.GitOFYError
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight filesystem delta helper.
 *
 * Git object/diff work is owned by libgit2 now; this class intentionally does
 * not depend on JGit. It is kept as a small compatibility service for callers
 * that only need a safe list of project files before a native push.
 */
@Singleton
class GitDeltaEngine @Inject constructor() {

    fun calculateChangedFiles(directory: String): Result<List<String>> = runCatching {
        val root = File(directory)
        require(root.isDirectory) { "Project directory does not exist" }
        root.walkTopDown()
            .filter { it.isFile }
            .mapNotNull { file ->
                val relative = file.relativeTo(root).invariantSeparatorsPath
                if (isExcluded(relative)) null else relative
            }
            .sorted()
            .toList()
    }.onFailure {
        GITOFYLogger.e("Filesystem delta calculation failed", throwable = it)
    }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(GitOFYError.GitError("Delta calculation failed: ${it.message ?: "Unknown error"}")) }
    )

    fun generateDiffSummary(directory: String): Result<String> = runCatching {
        val files = calculateChangedFiles(directory).getOrThrow()
        buildString {
            appendLine("Native libgit2 project snapshot")
            appendLine("Files: ${files.size}")
            files.forEach { appendLine("A\t$it") }
        }
    }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(GitOFYError.GitError("Diff summary failed: ${it.message ?: "Unknown error"}")) }
    )

    private fun isExcluded(path: String): Boolean {
        val excluded = setOf(".git", ".gradle", "build", ".idea", ".cxx", "captures", ".kotlin")
        return excluded.any { path == it || path.startsWith("$it/") }
    }
}
