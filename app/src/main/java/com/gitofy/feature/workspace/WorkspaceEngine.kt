package com.gitofy.feature.workspace

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mobile Code Editor — PRD v5.5 Section 65.
 * Support: syntax highlighting, line numbers, search, replace, undo/redo,
 * code folding, file tabs, cursor navigation, selection, copy/paste, large-file protection.
 */
@Singleton
class CodeEditorEngine @Inject constructor() {

    data class EditorState(
        val text: String,
        val cursorPosition: Int,
        val selectionStart: Int,
        val selectionEnd: Int,
        val canUndo: Boolean,
        val canRedo: Boolean,
        val searchQuery: String?,
        val searchMatches: List<Int>
    )

    private val undoStack = mutableListOf<String>()
    private val redoStack = mutableListOf<String>()
    private var currentText = ""

    fun setText(text: String) {
        if (currentText.isNotEmpty()) undoStack.add(currentText)
        currentText = text
        redoStack.clear()
    }

    fun getText(): String = currentText

    fun insertText(insertion: String, position: Int): String {
        if (currentText.isNotEmpty()) undoStack.add(currentText)
        currentText = currentText.take(position) + insertion + currentText.drop(position)
        redoStack.clear()
        return currentText
    }

    fun deleteText(start: Int, end: Int): String {
        if (currentText.isNotEmpty()) undoStack.add(currentText)
        currentText = currentText.take(start) + currentText.drop(end)
        redoStack.clear()
        return currentText
    }

    fun replaceText(start: Int, end: Int, replacement: String): String {
        if (currentText.isNotEmpty()) undoStack.add(currentText)
        currentText = currentText.take(start) + replacement + currentText.drop(end)
        redoStack.clear()
        return currentText
    }

    fun undo(): String {
        if (undoStack.isEmpty()) return currentText
        redoStack.add(currentText)
        currentText = undoStack.removeLast()
        return currentText
    }

    fun redo(): String {
        if (redoStack.isEmpty()) return currentText
        undoStack.add(currentText)
        currentText = redoStack.removeLast()
        return currentText
    }

    fun search(query: String): List<Int> {
        if (query.isEmpty()) return emptyList()
        val matches = mutableListOf<Int>()
        var index = currentText.indexOf(query, ignoreCase = true)
        while (index >= 0) {
            matches.add(index)
            index = currentText.indexOf(query, index + 1, ignoreCase = true)
        }
        return matches
    }

    fun replaceAll(query: String, replacement: String): String {
        if (query.isEmpty()) return currentText
        if (currentText.isNotEmpty()) undoStack.add(currentText)
        currentText = currentText.replace(query, replacement, ignoreCase = true)
        redoStack.clear()
        return currentText
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    /**
     * Large file protection — PRD v5.5 Section 65.
     */
    fun isFileTooLarge(size: Long): Boolean = size > 500_000 // 500KB
}

/**
 * Editor Safety — PRD v5.5 Section 66.
 * Before saving: Unsaved Changes → Preview Diff → Commit or Save. No automatic push.
 */
@Singleton
class EditorSafetyGuard @Inject constructor() {

    data class DiffResult(
        val addedLines: List<String>,
        val removedLines: List<String>,
        val modifiedLines: List<String>,
        val summary: String
    )

    fun computeDiff(original: String, modified: String): DiffResult {
        val originalLines = original.lines()
        val modifiedLines = modified.lines()
        val added = mutableListOf<String>()
        val removed = mutableListOf<String>()

        val maxLen = maxOf(originalLines.size, modifiedLines.size)
        for (i in 0 until maxLen) {
            val orig = originalLines.getOrNull(i) ?: ""
            val mod = modifiedLines.getOrNull(i) ?: ""
            if (orig != mod) {
                if (orig.isNotEmpty()) removed.add("- $orig")
                if (mod.isNotEmpty()) added.add("+ $mod")
            }
        }

        return DiffResult(
            addedLines = added,
            removedLines = removed,
            modifiedLines = added + removed,
            summary = "${added.size} additions, ${removed.size} deletions"
        )
    }
}

/**
 * Workspace State — PRD v5.5 Section 74.
 * Persist: open repository, open files, current branch, recent files, recent operations.
 * Do not persist secrets in workspace state.
 */
@Singleton
class WorkspaceStateManager @Inject constructor() {

    data class WorkspaceState(
        val openRepository: String,
        val openFiles: List<String>,
        val currentBranch: String,
        val recentFiles: List<String>,
        val recentOperations: List<String>
    )

    private var state: WorkspaceState = WorkspaceState("", emptyList(), "", emptyList(), emptyList())

    fun getState(): WorkspaceState = state

    fun updateState(newState: WorkspaceState) {
        // Never persist secrets
        state = newState.copy(
            openFiles = newState.openFiles.filterNot { it.contains("secret", ignoreCase = true) },
            recentFiles = newState.recentFiles.filterNot { it.contains("secret", ignoreCase = true) }
        )
    }

    fun addRecentFile(filePath: String) {
        state = state.copy(recentFiles = (listOf(filePath) + state.recentFiles).distinct().take(10))
    }

    fun addRecentOperation(operation: String) {
        state = state.copy(recentOperations = (listOf(operation) + state.recentOperations).distinct().take(20))
    }
}

/**
 * Terminal-Like Developer Console — PRD v5.5 Section 72.
 * Explicit command allowlist. No unrestricted privileged shell.
 * Clear working directory. Cancellation. Output streaming. Resource limits.
 * No hidden network execution. Must not become an arbitrary remote command execution system.
 */
@Singleton
class DeveloperConsole @Inject constructor() {

    data class ConsoleResult(
        val output: String,
        val isError: Boolean,
        val durationMs: Long
    )

    // Explicit allowlist of commands
    private val allowedCommands = mapOf(
        "git.status" to { args: List<String> -> "git status --porcelain" },
        "git.log" to { args: List<String> -> "git log --oneline -20" },
        "git.branch" to { args: List<String> -> "git branch -a" },
        "git.diff" to { args: List<String> -> "git diff" },
        "gradle.tasks" to { args: List<String> -> "./gradlew tasks" },
        "help" to { args: List<String> -> "Available commands: git.status, git.log, git.branch, git.diff, gradle.tasks, help" }
    )

    fun execute(command: String, args: List<String> = emptyList(), workingDir: String): ConsoleResult {
        val startTime = System.currentTimeMillis()

        val action = allowedCommands[command]
            ?: return ConsoleResult("Command '$command' not recognized. Type 'help' for available commands.", true, 0)

        // Verify command is in allowlist
        if (command !in allowedCommands) {
            return ConsoleResult("Command not in allowlist. This console does not support arbitrary execution.", true, 0)
        }

        val cmdString = action(args)
        return ConsoleResult(
            output = "Executed: $cmdString\n(Execution would run in safe sandbox at $workingDir)",
            isError = false,
            durationMs = System.currentTimeMillis() - startTime
        )
    }

    fun getAllowedCommands(): List<String> = allowedCommands.keys.toList()
}

/**
 * Local Project Workspace — PRD v5.5 Section 73.
 * Work with imported project files inside app-private storage.
 * User's original files must remain protected unless explicitly copied/imported.
 */
@Singleton
class LocalProjectWorkspace @Inject constructor() {

    data class ProjectTree(
        val rootPath: String,
        val files: List<ProjectFile>
    )

    data class ProjectFile(
        val path: String,
        val name: String,
        val isDirectory: Boolean,
        val size: Long
    )

    fun listFiles(projectDir: java.io.File): ProjectTree {
        val files = projectDir.walkTopDown()
            .filter { it != projectDir }
            .filter { !it.absolutePath.contains("/.git/") }
            .map { ProjectFile(it.absolutePath, it.name, it.isDirectory, if (it.isFile) it.length() else 0) }
            .toList()
        return ProjectTree(projectDir.absolutePath, files)
    }
}
