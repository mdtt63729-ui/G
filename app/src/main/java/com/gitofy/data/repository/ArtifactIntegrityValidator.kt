package com.gitofy.data.repository

import com.gitofy.core.logging.GITOFYLogger
import com.gitofy.domain.model.GitOFYError
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Artifact Integrity — PRD v3.0 Section 52.
 * - Verify download completion
 * - Validate file size
 * - Verify checksum if supplied/available
 * - Detect corrupted downloads
 * - Prevent partial file presentation as completed artifact
 *
 * Uses temporary extension during download and atomically moves after completion.
 */
@Singleton
class ArtifactIntegrityValidator @Inject constructor() {

    /**
     * Download a file atomically — write to .tmp, rename on success.
     * Prevents partial files from being presented as complete artifacts.
     */
    fun downloadAtomically(
        targetFile: File,
        outputStream: (FileOutputStream) -> Unit
    ): Result<File> {
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")

        return try {
            // Write to temp file
            FileOutputStream(tempFile).use { output ->
                outputStream(output)
            }

            // Verify temp file is not empty
            if (tempFile.length() == 0L) {
                tempFile.delete()
                return Result.failure(GitOFYError.ArtifactError("Downloaded file is empty"))
            }

            // Atomic rename
            if (!tempFile.renameTo(targetFile)) {
                // Fallback: copy then delete
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            GITOFYLogger.i("Artifact downloaded atomically: ${targetFile.name}")
            Result.success(targetFile)
        } catch (e: Exception) {
            tempFile.delete()
            GITOFYLogger.e("Artifact download failed: ${e.message}")
            Result.failure(GitOFYError.ArtifactError("Download failed: ${e.message}"))
        }
    }

    /**
     * Verify SHA-256 checksum of a downloaded file.
     */
    fun verifyChecksum(file: File, expectedSha256: String): Boolean {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } > 0) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            val match = actualHash.equals(expectedSha256, ignoreCase = true)
            if (!match) {
                GITOFYLogger.w("Checksum mismatch: expected=$expectedSha256 actual=$actualHash")
            }
            match
        } catch (e: Exception) {
            GITOFYLogger.e("Checksum verification failed: ${e.message}")
            false
        }
    }

    /**
     * Validate that a downloaded file has the expected size.
     */
    fun validateFileSize(file: File, expectedSize: Long): Boolean {
        val actual = file.length()
        if (actual != expectedSize && expectedSize > 0) {
            GITOFYLogger.w("File size mismatch: expected=$expectedSize actual=$actual")
            return false
        }
        return true
    }

    /**
     * Check if a file appears to be a valid ZIP archive (for APK artifacts).
     */
    fun isValidZip(file: File): Boolean {
        return try {
            java.util.zip.ZipFile(file).use { it.close() }
            true
        } catch (e: Exception) {
            false
        }
    }
}
