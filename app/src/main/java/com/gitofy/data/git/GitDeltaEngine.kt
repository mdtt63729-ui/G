package com.gitofy.data.git

import com.gitofy.core.logging.GITOFYLogger
import com.gitofy.domain.model.GitOFYError
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.AbstractTreeIterator
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * JGit Delta & Diff Engine — PRD Addendum: GitHub Integration Criteria.
 * Local Git diff calculation before pushing ZIP contents—uploading only modified files
 * to optimize network usage.
 */
@Singleton
class GitDeltaEngine @Inject constructor() {

    /**
     * Calculate which files have changed or are new compared to the last commit.
     * Returns the list of file paths that need to be uploaded.
     */
    fun calculateChangedFiles(directory: String): Result<List<String>> {
        return try {
            val git = Git.open(File(directory))
            val repository = git.repository

            // Get HEAD commit
            val headId = repository.resolve("HEAD")

            if (headId == null) {
                // No commits yet — all files are "new"
                val allFiles = File(directory).walkTopDown()
                    .filter { it.isFile }
                    .filter { !it.absolutePath.contains(".git") }
                    .map { it.absolutePath.removePrefix(File(directory).absolutePath) }
                    .toList()
                return Result.success(allFiles)
            }

            // Compare HEAD tree with working tree
            val changes = mutableListOf<String>()

            val walk = RevWalk(repository)
            val commit = walk.parseCommit(headId)
            val treeParser = prepareTreeParser(repository, commit.tree.id)

            val diffFormatter = DiffFormatter(ByteArrayOutputStream())
            diffFormatter.setRepository(repository)

            val diffs = diffFormatter.scan(treeParser, CanonicalTreeParser().also {
                val reader = repository.newObjectReader()
                val headTree = repository.resolve("HEAD^{tree}")
                it.reset(reader, headTree)
            })

            diffs.forEach { diff ->
                when (diff.changeType) {
                    DiffEntry.ChangeType.ADD -> changes.add(diff.newPath)
                    DiffEntry.ChangeType.MODIFY -> changes.add(diff.newPath)
                    DiffEntry.ChangeType.DELETE -> GITOFYLogger.d("Deleted: ${diff.oldPath}")
                    else -> {}
                }
            }

            walk.dispose()
            diffFormatter.close()

            GITOFYLogger.i("Delta engine found ${changes.size} changed files")
            Result.success(changes)
        } catch (e: Exception) {
            GITOFYLogger.e("Delta calculation failed", e)
            Result.failure(GitOFYError.GitError("Delta calculation failed: ${e.message}"))
        }
    }

    /**
     * Generate a diff summary string.
     */
    fun generateDiffSummary(directory: String): Result<String> {
        return try {
            val git = Git.open(File(directory))
            val repository = git.repository

            val outputStream = ByteArrayOutputStream()
            val diffFormatter = DiffFormatter(outputStream)
            diffFormatter.setRepository(repository)

            val headId = repository.resolve("HEAD")
            if (headId != null) {
                val walk = RevWalk(repository)
                val commit = walk.parseCommit(headId)
                val treeParser = prepareTreeParser(repository, commit.tree.id)

                val diffs = diffFormatter.scan(treeParser, CanonicalTreeParser().also {
                    val reader = repository.newObjectReader()
                    val headTree = repository.resolve("HEAD^{tree}")
                    it.reset(reader, headTree)
                })

                diffs.forEach { diff ->
                    diffFormatter.format(diff)
                }

                walk.dispose()
            }

            diffFormatter.close()
            Result.success(outputStream.toString())
        } catch (e: Exception) {
            GITOFYLogger.e("Diff summary failed", e)
            Result.failure(GitOFYError.GitError("Diff summary failed: ${e.message}"))
        }
    }

    private fun prepareTreeParser(repository: Repository, objectId: ObjectId): AbstractTreeIterator {
        val walk = RevWalk(repository)
        val tree = walk.parseTree(objectId)
        val treeParser = CanonicalTreeParser()
        val reader = repository.newObjectReader()
        treeParser.reset(reader, tree.id)
        walk.dispose()
        return treeParser
    }
}
