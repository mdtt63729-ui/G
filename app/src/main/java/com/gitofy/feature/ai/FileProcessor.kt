package com.gitofy.feature.ai

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * PRD §10-19: File Processing Pipeline
 *
 * Handles reading actual file metadata (name, size, MIME type) from a content
 * URI using Android's ContentResolver, and extracts text content from
 * text-based files for AI context.
 *
 * Pipeline: Content URI → metadata → actual size → read file → validate →
 * process/parse → AI context
 */
data class FileMetadata(
    val uri: String,
    val fileName: String,
    val mimeType: String?,
    val sizeBytes: Long
)

/**
 * Result of attempting to read a file's content for AI context.
 */
sealed interface FileContentResult {
    data class TextContent(val fileName: String, val content: String) : FileContentResult
    data class BinaryContent(val fileName: String, val mimeType: String?, val sizeBytes: Long) : FileContentResult
    data class Error(val fileName: String, val message: String) : FileContentResult
}

object FileProcessor {

    /** Maximum file size for inline text content (4 MB). Larger files are chunked. */
    const val MAX_INLINE_SIZE_BYTES = 4L * 1024 * 1024

    /** Maximum text content length to send inline in a single prompt. */
    const val MAX_TEXT_LENGTH = 100_000

    /** Extensions that can be read as text. */
    private val TEXT_EXTENSIONS = setOf(
        "kt", "java", "py", "js", "ts", "tsx", "jsx", "xml", "json", "yaml", "yml",
        "md", "txt", "csv", "html", "css", "sql", "gradle", "properties", "env",
        "sh", "c", "cpp", "h", "hpp", "toml", "swift", "go", "rs", "rb", "php",
        "dart", "vue", "svelte", "gitignore", "dockerfile", "makefile",
    )

    /** MIME prefixes that indicate text-based content. */
    private val TEXT_MIME_PREFIXES = setOf("text/", "application/json", "application/xml",
        "application/javascript", "application/x-yaml", "application/x-sh")

    /**
     * PRD §12: Read actual file metadata (name, size, MIME) from a content URI.
     * Uses ContentResolver — not estimates.
     */
    fun readMetadata(context: Context, uri: Uri): FileMetadata {
        val resolver = context.contentResolver

        var fileName: String? = null
        var size: Long = 0L

        // Try querying the content provider for display name and size
        resolver.query(uri, null, null, null, null)?.use { cursor: Cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex)
                }
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }

        // Fallback: derive name from URI path segment
        if (fileName.isNullOrBlank()) {
            fileName = uri.lastPathSegment ?: "file"
        }

        // Fallback: if size is still 0, try opening the stream
        if (size <= 0) {
            try {
                size = resolver.openAssetFileDescriptor(uri, "r")?.length ?: 0L
            } catch (_: Exception) {
                // Leave at 0 if we truly can't determine it
            }
        }

        val mimeType = resolver.getType(uri)

        return FileMetadata(
            uri = uri.toString(),
            fileName = fileName ?: "file",
            mimeType = mimeType,
            sizeBytes = size
        )
    }

    /**
     * PRD §12: Format file size in human-readable form (B, KB, MB, GB).
     */
    fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }

    /**
     * PRD §16-18: Read and process a file for AI context.
     *
     * Text/code files: content is extracted directly.
     * Binary files: returns a BinaryContent result so the caller can decide
     *   how to handle it (e.g. send file name + size as context without content).
     * Large files: text is truncated to MAX_TEXT_LENGTH.
     */
    fun processFile(context: Context, metadata: FileMetadata): FileContentResult {
        return try {
            if (isTextFile(metadata)) {
                if (metadata.sizeBytes > MAX_INLINE_SIZE_BYTES * 4) {
                    // PRD §22: Large file — read only the first chunk
                    val content = readTextChunk(context, Uri.parse(metadata.uri), MAX_TEXT_LENGTH)
                    FileContentResult.TextContent(metadata.fileName, content)
                } else {
                    val content = readFullText(context, Uri.parse(metadata.uri))
                    if (content.length > MAX_TEXT_LENGTH) {
                        FileContentResult.TextContent(
                            metadata.fileName,
                            content.take(MAX_TEXT_LENGTH) + "\n\n... [file truncated, showing first ${MAX_TEXT_LENGTH} chars]"
                        )
                    } else {
                        FileContentResult.TextContent(metadata.fileName, content)
                    }
                }
            } else {
                // PRD §18: Binary file — we can't parse it locally
                FileContentResult.BinaryContent(
                    metadata.fileName,
                    metadata.mimeType,
                    metadata.sizeBytes
                )
            }
        } catch (e: Exception) {
            FileContentResult.Error(metadata.fileName, e.message ?: "Unable to read file")
        }
    }

    /**
     * Determines whether a file can be read as text based on extension and MIME type.
     */
    private fun isTextFile(metadata: FileMetadata): Boolean {
        val extension = metadata.fileName.substringAfterLast('.', "").lowercase()
        if (extension in TEXT_EXTENSIONS) return true

        val mime = metadata.mimeType ?: return false
        return TEXT_MIME_PREFIXES.any { mime.startsWith(it) }
    }

    private fun readFullText(context: Context, uri: Uri): String {
        val resolver = context.contentResolver
        return resolver.openInputStream(uri)?.use { input ->
            BufferedReader(InputStreamReader(input)).readText()
        } ?: throw Exception("Unable to open file stream")
    }

    private fun readTextChunk(context: Context, uri: Uri, maxChars: Int): String {
        val resolver = context.contentResolver
        return resolver.openInputStream(uri)?.use { input ->
            val reader = BufferedReader(InputStreamReader(input))
            val sb = StringBuilder()
            val buffer = CharArray(8192)
            var read: Int
            while (reader.read(buffer).also { read = it } != -1) {
                sb.append(buffer, 0, read)
                if (sb.length >= maxChars) break
            }
            sb.toString()
        } ?: throw Exception("Unable to open file stream")
    }
}
