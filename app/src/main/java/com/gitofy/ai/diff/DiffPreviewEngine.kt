package com.gitofy.ai.diff

import javax.inject.Inject
import javax.inject.Singleton

/**
 * PRD §35, §77: AI Code Changes with diff preview.
 *
 * Generates structured diffs between two snapshots of a repository's files and
 * renders them as unified-diff text suitable for human review before they are
 * applied.
 */
@Singleton
class DiffPreviewEngine @Inject constructor() {

    /**
     * Type of change applied to a single file.
     */
    enum class ChangeType { ADD, MODIFY, DELETE, RENAME }

    /**
     * A single file's change within a diff.
     *
     * @property filePath Path the change applies to (post-rename for RENAME).
     * @property oldContent Original content, or empty for an ADD.
     * @property newContent New content, or empty for a DELETE.
     * @property changeType Categorization of the change.
     */
    data class DiffEntry(
        val filePath: String,
        val oldContent: String,
        val newContent: String,
        val changeType: ChangeType
    )

    /**
     * The outcome of comparing two file snapshots.
     *
     * @property entries Ordered list of per-file changes.
     * @property summary Human-readable one-line summary.
     * @property totalLinesAdded Count of added lines across all entries.
     * @property totalLinesRemoved Count of removed lines across all entries.
     */
    data class DiffPreview(
        val entries: List<DiffEntry>,
        val summary: String,
        val totalLinesAdded: Int,
        val totalLinesRemoved: Int
    )

    /**
     * Result of validating a [DiffPreview] before it is applied.
     *
     * @property isValid True when the diff has no blocking errors.
     * @property errors Human-readable validation problems.
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String>
    )

    /**
     * Compares [oldFiles] to [newFiles] and produces a [DiffPreview].
     *
     * Both maps are keyed by file path. A path present only in [oldFiles] is a
     * DELETE; a path present only in [newFiles] is an ADD; a path present in
     * both with differing content is a MODIFY. RENAMEs are detected when a
     * deleted file's content reappears verbatim under a new path.
     */
    fun generateDiff(oldFiles: Map<String, String>, newFiles: Map<String, String>): DiffPreview {
        val entries = mutableListOf<DiffEntry>()
        var linesAdded = 0
        var linesRemoved = 0

        val oldPaths = oldFiles.keys
        val newPaths = newFiles.keys

        val addedPaths = (newPaths - oldPaths).sorted()
        val removedPaths = (oldPaths - newPaths).sorted()
        val commonPaths = (oldPaths intersect newPaths).sorted()

        // Detect renames: a removed file whose content matches an added file.
        val addedByContent = addedPaths.groupBy { newFiles[it] }
        val renamedPairs = mutableMapOf<String, String>() // oldPath -> newPath
        val consumedAdded = mutableSetOf<String>()

        for (removedPath in removedPaths) {
            val content = oldFiles[removedPath].orEmpty()
            val match = addedByContent[content]?.firstOrNull { it !in consumedAdded }
            if (match != null) {
                renamedPairs[removedPath] = match
                consumedAdded.add(match)
            }
        }

        // RENAME entries.
        for ((oldPath, newPath) in renamedPairs.toSortedMap()) {
            val content = oldFiles[oldPath].orEmpty()
            entries.add(DiffEntry(newPath, content, newFiles[newPath].orEmpty(), ChangeType.RENAME))
        }

        // DELETE entries (excluding renamed-away files).
        for (path in removedPaths) {
            if (path in renamedPairs) continue
            val content = oldFiles[path].orEmpty()
            entries.add(DiffEntry(path, content, "", ChangeType.DELETE))
            linesRemoved += content.lineCount()
        }

        // ADD entries (excluding renamed-to files).
        for (path in addedPaths) {
            if (path in consumedAdded) continue
            val content = newFiles[path].orEmpty()
            entries.add(DiffEntry(path, "", content, ChangeType.ADD))
            linesAdded += content.lineCount()
        }

        // MODIFY entries.
        for (path in commonPaths) {
            val oldContent = oldFiles[path].orEmpty()
            val newContent = newFiles[path].orEmpty()
            if (oldContent == newContent) continue
            entries.add(DiffEntry(path, oldContent, newContent, ChangeType.MODIFY))
            val (added, removed) = lineDelta(oldContent, newContent)
            linesAdded += added
            linesRemoved += removed
        }

        entries.sortBy { it.filePath }

        val summary = buildSummary(entries, linesAdded, linesRemoved)
        return DiffPreview(entries, summary, linesAdded, linesRemoved)
    }

    /**
     * Renders [diff] as unified-diff text. Each entry is prefixed with a
     * `diff --git` header followed by `---`/`+++` file markers and the
     * line-by-line body.
     */
    fun formatDiff(diff: DiffPreview): String {
        if (diff.entries.isEmpty()) {
            return "// No changes detected."
        }

        val sb = StringBuilder()
        for (entry in diff.entries) {
            sb.appendLine("diff --git a/${entry.filePath} b/${entry.filePath}")
            when (entry.changeType) {
                ChangeType.ADD -> {
                    sb.appendLine("new file mode 100644")
                    sb.appendLine("--- /dev/null")
                    sb.appendLine("+++ b/${entry.filePath}")
                    appendBody(sb, entry.newContent, addedOnly = true, removedOnly = false)
                }

                ChangeType.DELETE -> {
                    sb.appendLine("deleted file mode 100644")
                    sb.appendLine("--- a/${entry.filePath}")
                    sb.appendLine("+++ /dev/null")
                    appendBody(sb, entry.oldContent, addedOnly = false, removedOnly = true)
                }

                ChangeType.RENAME -> {
                    sb.appendLine("rename from ${entry.filePath}")
                    sb.appendLine("rename to ${entry.filePath}")
                    sb.appendLine("--- a/${entry.filePath}")
                    sb.appendLine("+++ b/${entry.filePath}")
                    appendUnified(sb, entry.oldContent, entry.newContent)
                }

                ChangeType.MODIFY -> {
                    sb.appendLine("--- a/${entry.filePath}")
                    sb.appendLine("+++ b/${entry.filePath}")
                    appendUnified(sb, entry.oldContent, entry.newContent)
                }
            }
            sb.appendLine()
        }

        sb.appendLine(diff.summary)
        return sb.toString().trimEnd()
    }

    /**
     * Validates [diff] for structural correctness. Returns a [ValidationResult]
     * listing any problems. A diff is valid when every entry is self-consistent
     * (e.g. an ADD has empty old content, a DELETE has empty new content) and
     * the summary statistics are plausible.
     */
    fun validateDiff(diff: DiffPreview): ValidationResult {
        val errors = mutableListOf<String>()

        if (diff.entries.isEmpty()) {
            return ValidationResult(true, emptyList())
        }

        var computedAdded = 0
        var computedRemoved = 0

        for ((index, entry) in diff.entries.withIndex()) {
            val context = "entry #${index + 1} (${entry.filePath})"

            when (entry.changeType) {
                ChangeType.ADD -> {
                    if (entry.oldContent.isNotEmpty()) {
                        errors.add("$context: ADD must have empty oldContent")
                    }
                    computedAdded += entry.newContent.lineCount()
                }

                ChangeType.DELETE -> {
                    if (entry.newContent.isNotEmpty()) {
                        errors.add("$context: DELETE must have empty newContent")
                    }
                    computedRemoved += entry.oldContent.lineCount()
                }

                ChangeType.RENAME, ChangeType.MODIFY -> {
                    val (added, removed) = lineDelta(entry.oldContent, entry.newContent)
                    computedAdded += added
                    computedRemoved += removed
                }
            }

            if (entry.filePath.isBlank()) {
                errors.add("$context: filePath must not be blank")
            }
        }

        if (computedAdded != diff.totalLinesAdded) {
            errors.add(
                "totalLinesAdded mismatch: declared=${diff.totalLinesAdded}, actual=$computedAdded"
            )
        }
        if (computedRemoved != diff.totalLinesRemoved) {
            errors.add(
                "totalLinesRemoved mismatch: declared=${diff.totalLinesRemoved}, actual=$computedRemoved"
            )
        }

        return ValidationResult(errors.isEmpty(), errors)
    }

    private fun appendBody(sb: StringBuilder, content: String, addedOnly: Boolean, removedOnly: Boolean) {
        if (content.isEmpty()) return
        val prefix = when {
            addedOnly -> '+'
            removedOnly -> '-'
            else -> ' '
        }
        for (line in content.splitLines()) {
            sb.append(prefix).appendLine(line)
        }
    }

    private fun appendUnified(sb: StringBuilder, oldContent: String, newContent: String) {
        val oldLines = oldContent.splitLines()
        val newLines = newContent.splitLines()
        val hunks = computeHunks(oldLines, newLines)
        for (hunk in hunks) {
            sb.appendLine(hunk.header)
            hunk.lines.forEach { (mark, text) ->
                sb.append(mark).appendLine(text)
            }
        }
    }

    private fun computeHunks(oldLines: List<String>, newLines: List<String>): List<Hunk> {
        val hunks = mutableListOf<Hunk>()
        val matrix = Array(oldLines.size + 1) { IntArray(newLines.size + 1) }
        for (i in oldLines.indices.reversed()) {
            for (j in newLines.indices.reversed()) {
                matrix[i][j] = if (oldLines[i] == newLines[j]) {
                    matrix[i + 1][j + 1] + 1
                } else {
                    maxOf(matrix[i + 1][j], matrix[i][j + 1])
                }
            }
        }

        val ops = mutableListOf<Op>()
        var i = 0
        var j = 0
        while (i < oldLines.size && j < newLines.size) {
            if (oldLines[i] == newLines[j]) {
                ops.add(Op(' ', oldLines[i]))
                i++; j++
            } else if (matrix[i + 1][j] >= matrix[i][j + 1]) {
                ops.add(Op('-', oldLines[i]))
                i++
            } else {
                ops.add(Op('+', newLines[j]))
                j++
            }
        }
        while (i < oldLines.size) { ops.add(Op('-', oldLines[i])); i++ }
        while (j < newLines.size) { ops.add(Op('+', newLines[j])); j++ }

        if (ops.isEmpty()) return emptyList()

        // Group ops into hunks, wrapping change runs with a small context window.
        val context = 3
        var idx = 0
        while (idx < ops.size) {
            if (ops[idx].mark == ' ') {
                idx++
                continue
            }
            val changeStart = idx
            var changeEnd = idx
            while (changeEnd < ops.size && ops[changeEnd].mark != ' ') {
                changeEnd++
            }
            val hunkStart = maxOf(0, changeStart - context)
            val hunkEnd = minOf(ops.size, changeEnd + context)
            val header = buildHunkHeader(ops, hunkStart, hunkEnd, oldLines.size, newLines.size)
            hunks.add(Hunk(header, ops.subList(hunkStart, hunkEnd).toList()))
            idx = hunkEnd
        }

        return hunks
    }

    private fun buildHunkHeader(
        ops: List<Op>,
        start: Int,
        end: Int,
        oldTotal: Int,
        newTotal: Int
    ): String {
        var oldStart = 0
        var oldLen = 0
        var newStart = 0
        var newLen = 0
        var sawOld = false
        var sawNew = false
        for (k in 0 until start) {
            if (ops[k].mark != '+') oldStart++
            if (ops[k].mark != '-') newStart++
        }
        for (k in start until end) {
            if (ops[k].mark != '+') { oldLen++; sawOld = true }
            if (ops[k].mark != '-') { newLen++; sawNew = true }
        }
        if (oldLen == 0 && !sawOld) oldStart = 0
        if (newLen == 0 && !sawNew) newStart = 0
        val oldLineNum = if (oldLen == 0) oldStart else oldStart + 1
        val newLineNum = if (newLen == 0) newStart else newStart + 1
        return "@@ -$oldLineNum,$oldLen +$newLineNum,$newLen @@"
    }

    private fun buildSummary(entries: List<DiffEntry>, added: Int, removed: Int): String {
        val counts = entries.groupingBy { it.changeType }.eachCount()
        val parts = mutableListOf<String>()
        ChangeType.values().forEach { type ->
            counts[type]?.let { parts.add("${it} ${type.name.lowercase()}") }
        }
        val filePart = if (parts.isEmpty()) "no file changes" else parts.joinToString(", ")
        return "$filePart (+$added / -$removed lines)"
    }

    private fun lineDelta(oldContent: String, newContent: String): Pair<Int, Int> {
        val oldLines = oldContent.splitLines()
        val newLines = newContent.splitLines()
        val matrix = Array(oldLines.size + 1) { IntArray(newLines.size + 1) }
        for (i in oldLines.indices.reversed()) {
            for (j in newLines.indices.reversed()) {
                matrix[i][j] = if (oldLines[i] == newLines[j]) {
                    matrix[i + 1][j + 1] + 1
                } else {
                    maxOf(matrix[i + 1][j], matrix[i][j + 1])
                }
            }
        }
        val common = matrix[0][0]
        val removed = oldLines.size - common
        val added = newLines.size - common
        return added to removed
    }

    private fun String.lineCount(): Int = if (isEmpty()) 0 else splitLines().size

    private fun String.splitLines(): List<String> =
        this.split("\n", "\r\n", "\r")

    private data class Op(val mark: Char, val text: String)
    private data class Hunk(val header: String, val lines: List<Op>)
}
