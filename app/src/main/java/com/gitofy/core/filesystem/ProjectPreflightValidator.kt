package com.gitofy.core.filesystem

import com.gitofy.core.security.SecretDetector
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Project Preflight Validation — PRD v3.0 Section 28.
 * Before creating the repository, check:
 * - Project root validity
 * - File count and size
 * - Storage requirements
 * - Existing .git directory
 * - Existing Git metadata
 * - Potential secrets
 * - Invalid filenames
 * - Unsupported filesystem paths
 *
 * If .git exists, provide explicit decision — never destructive by default.
 */
@Singleton
class ProjectPreflightValidator @Inject constructor(
    private val secretDetector: SecretDetector
) {

    data class PreflightResult(
        val isValid: Boolean,
        val fileCount: Int,
        val totalSize: Long,
        val hasExistingGit: Boolean,
        val secretFindings: SecretDetector.DetectionResult,
        val invalidFiles: List<String>,
        val warnings: List<String>,
        val errors: List<String>
    ) {
        val requiresGitDecision: Boolean get() = hasExistingGit
        val hasSecretWarnings: Boolean get() = secretFindings.hasSecrets
    }

    /**
     * Validate a project directory before repository creation.
     */
    fun validate(projectRoot: File): PreflightResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val invalidFiles = mutableListOf<String>()

        if (!projectRoot.exists() || !projectRoot.isDirectory) {
            return PreflightResult(
                isValid = false,
                fileCount = 0,
                totalSize = 0,
                hasExistingGit = false,
                secretFindings = SecretDetector.DetectionResult(false, emptyList()),
                invalidFiles = emptyList(),
                warnings = emptyList(),
                errors = listOf("Project root does not exist or is not a directory")
            )
        }

        // Count files and size
        var fileCount = 0
        var totalSize = 0L
        val maxFileSize = 100L * 1024 * 1024 // 100MB per file

        projectRoot.walkTopDown()
            .filter { it.isFile }
            .filter { !it.absolutePath.contains("/.git/") }
            .forEach { file ->
                fileCount++
                totalSize += file.length()

                // Check for invalid filenames
                val name = file.name
                if (name.contains("\n") || name.contains("\r")) {
                    invalidFiles.add(file.absolutePath)
                }
                if (name.length > 255) {
                    invalidFiles.add(file.absolutePath)
                }

                // Check file size
                if (file.length() > maxFileSize) {
                    warnings.add("Large file detected: ${file.name} (${file.length() / 1024 / 1024}MB)")
                }
            }

        // Check for existing .git directory
        val hasExistingGit = File(projectRoot, ".git").exists()
        if (hasExistingGit) {
            warnings.add("Existing .git directory detected. User decision required: use existing history or initialize new.")
        }

        // Check for potential secrets
        val secretResult = secretDetector.scan(projectRoot)
        if (secretResult.hasSecrets) {
            warnings.add("Potential secrets detected. Review before pushing to GitHub.")
        }

        // Validate project structure
        val files = projectRoot.listFiles()?.map { it.name.lowercase() } ?: emptyList()
        val hasGradle = files.any { it == "build.gradle" || it == "build.gradle.kts" }
        val hasSettings = files.any { it == "settings.gradle" || it == "settings.gradle.kts" }

        if (!hasGradle && !hasSettings) {
            warnings.add("No Gradle build files detected. Project may not be a standard Android/Gradle project.")
        }

        val isValid = errors.isEmpty()

        return PreflightResult(
            isValid = isValid,
            fileCount = fileCount,
            totalSize = totalSize,
            hasExistingGit = hasExistingGit,
            secretFindings = secretResult,
            invalidFiles = invalidFiles,
            warnings = warnings,
            errors = errors
        )
    }
}
