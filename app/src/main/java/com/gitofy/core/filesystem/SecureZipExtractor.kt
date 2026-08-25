package com.gitofy.core.filesystem

import com.gitofy.domain.model.GitOFYError
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlin.math.min

/**
 * Secure ZIP extraction with full protection.
 * PRD 8.3: ZIP extraction must protect against Zip Slip, path traversal,
 * malicious symbolic links, excessive decompression, extremely large files.
 * PRD 15.2: ZIP validation.
 * PRD 15.3: Secure extraction in application-controlled temporary directory.
 * PRD 44: Large ZIP handling — stream, don't load entire ZIP into RAM.
 */
class SecureZipExtractor {

    companion object {
        private const val MAX_TOTAL_UNCOMPRESSED_SIZE = 500L * 1024 * 1024 // 500 MB
        private const val MAX_SINGLE_FILE_SIZE = 200L * 1024 * 1024 // 200 MB
        private const val MAX_FILE_COUNT = 50_000
        private const val BUFFER_SIZE = 8192
    }

    data class ZipValidationResult(
        val isValid: Boolean,
        val fileCount: Int = 0,
        val totalUncompressedSize: Long = 0,
        val error: String? = null
    )

    data class ExtractionProgress(
        val currentFile: String,
        val filesExtracted: Int,
        val totalFiles: Int,
        val bytesExtracted: Long,
        val totalBytes: Long
    ) {
        val progress: Float
            get() = if (totalFiles > 0) filesExtracted.toFloat() / totalFiles else 0f
    }

    /**
     * Validate a ZIP file without extracting.
     * PRD 15.2: Check valid header, readability, file count, total size, path traversal, invalid entries, corrupted archive.
     */
    fun validateZip(zipFile: File): ZipValidationResult {
        try {
            if (!zipFile.exists()) {
                return ZipValidationResult(false, error = "File does not exist")
            }

            if (zipFile.length() == 0L) {
                return ZipValidationResult(false, error = "ZIP file is empty")
            }

            val zip = ZipFile(zipFile)
            zip.use { zf ->
                val entries = zf.entries()
                var fileCount = 0
                var totalSize = 0L

                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    fileCount++

                    if (fileCount > MAX_FILE_COUNT) {
                        return ZipValidationResult(
                            false,
                            error = "ZIP contains too many files (max $MAX_FILE_COUNT)"
                        )
                    }

                    // Check for path traversal
                    if (isUnsafePath(entry.name)) {
                        return ZipValidationResult(
                            false,
                            error = "Unsafe path detected: ${entry.name}"
                        )
                    }

                    if (!entry.isDirectory) {
                        totalSize += entry.size
                        if (entry.size > MAX_SINGLE_FILE_SIZE) {
                            return ZipValidationResult(
                                false,
                                error = "File too large: ${entry.name}"
                            )
                        }
                    }
                }

                if (totalSize > MAX_TOTAL_UNCOMPRESSED_SIZE) {
                    return ZipValidationResult(
                        false,
                        error = "Total uncompressed size too large"
                    )
                }

                return ZipValidationResult(
                    isValid = true,
                    fileCount = fileCount,
                    totalUncompressedSize = totalSize
                )
            }
        } catch (e: IOException) {
            return ZipValidationResult(false, error = "Corrupted or invalid ZIP: ${e.message}")
        }
    }

    /**
     * Securely extract a ZIP to a target directory.
     * PRD 15.3: Protect against "../" traversal, absolute paths, malicious links, resource exhaustion.
     * PRD 44: Stream file access, do not load entire ZIP into RAM.
     */
    fun extractZip(
        zipFile: File,
        targetDir: File,
        onProgress: ((ExtractionProgress) -> Unit)? = null
    ): Result<File> {
        return try {
            val validation = validateZip(zipFile)
            if (!validation.isValid) {
                return Result.failure(GitOFYError.ZipError(validation.error ?: "Invalid ZIP"))
            }

            targetDir.mkdirs()

            val zip = ZipFile(zipFile)
            zip.use { zf ->
                val entries = zf.entries()
                var filesExtracted = 0
                var bytesExtracted = 0L

                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()

                    // Skip directories, create them inline
                    if (entry.isDirectory) {
                        val dir = File(targetDir, entry.name)
                        dir.mkdirs()
                        continue
                    }

                    // Validate path safety
                    if (isUnsafePath(entry.name)) {
                        return Result.failure(
                            GitOFYError.ZipError("Unsafe path detected: ${entry.name}")
                        )
                    }

                    val outFile = File(targetDir, entry.name)

                    // Canonical path check — ensure within target
                    val targetCanonical = targetDir.canonicalPath
                    val outCanonical = outFile.canonicalPath
                    if (!outCanonical.startsWith(targetCanonical)) {
                        return Result.failure(
                            GitOFYError.ZipError("Path traversal attempt detected: ${entry.name}")
                        )
                    }

                    // Create parent directories
                    outFile.parentFile?.mkdirs()

                    // Stream extraction — don't load into memory
                    zf.getInputStream(entry).use { input ->
                        FileOutputStream(outFile).use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var bytesRead: Int
                            var fileSize = 0L
                            while (input.read(buffer).also { bytesRead = it } > 0) {
                                output.write(buffer, 0, bytesRead)
                                fileSize += bytesRead
                                bytesExtracted += bytesRead

                                if (fileSize > MAX_SINGLE_FILE_SIZE) {
                                    return Result.failure(
                                        GitOFYError.ZipError("File too large during extraction: ${entry.name}")
                                    )
                                }
                            }
                        }
                    }

                    filesExtracted++
                    onProgress?.invoke(
                        ExtractionProgress(
                            currentFile = entry.name,
                            filesExtracted = filesExtracted,
                            totalFiles = validation.fileCount,
                            bytesExtracted = bytesExtracted,
                            totalBytes = validation.totalUncompressedSize
                        )
                    )
                }
            }

            Result.success(targetDir)
        } catch (e: Exception) {
            // Clean up on failure
            targetDir.deleteRecursively()
            Result.failure(GitOFYError.ZipError("Extraction failed: ${e.message}"))
        }
    }

    /**
     * Check for unsafe paths — Zip Slip, absolute paths, backslash traversal.
     */
    private fun isUnsafePath(name: String): Boolean {
        if (name.contains("../") || name.contains("..\\") || name.contains("..")) return true
        if (name.startsWith("/")) return true
        if (name.startsWith("\\")) return true
        if (name.contains(":")) return true // Windows drive letters
        return false
    }

    /**
     * Detect project root from extracted ZIP contents.
     * PRD 15.4: Detect common project structures.
     */
    fun detectProjectRoot(extractedDir: File): File? {
        // Check if extractedDir itself is the project root
        if (isProjectRoot(extractedDir)) return extractedDir

        // Check single subdirectory
        val subDirs = extractedDir.listFiles { f -> f.isDirectory } ?: emptyArray()
        if (subDirs.size == 1 && isProjectRoot(subDirs[0])) {
            return subDirs[0]
        }

        // Check all subdirectories
        for (dir in subDirs) {
            if (isProjectRoot(dir)) return dir
        }

        return null
    }

    private fun isProjectRoot(dir: File): Boolean {
        val files = dir.listFiles() ?: return false
        val names = files.map { it.name.lowercase() }.toSet()
        // Android/Gradle project indicators
        return names.any { it == "build.gradle" || it == "build.gradle.kts" } &&
               names.any { it == "settings.gradle" || it == "settings.gradle.kts" }
    }
}
