package com.gitofy.core.filesystem

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gitignore Intelligence — PRD v3.0 Section 30.
 * Detects whether .gitignore exists and offers to generate Android-appropriate rules.
 *
 * If absent, generates ignore rules covering common build/cache files.
 * The user must be able to review before commit.
 */
@Singleton
class GitignoreGenerator @Inject constructor() {

    /**
     * Check if .gitignore exists in a project directory.
     */
    fun hasGitignore(projectDir: File): Boolean {
        return File(projectDir, ".gitignore").exists()
    }

    /**
     * Generate an Android-appropriate .gitignore file.
     * PRD v3.0 Section 30: Cover common build/cache files.
     */
    fun generateAndroidGitignore(projectDir: File): Result<File> {
        return try {
            val gitignore = File(projectDir, ".gitignore")
            gitignore.writeText(ANDROID_GITIGNORE_CONTENT)
            Result.success(gitignore)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get the content that would be written, for user review before commit.
     */
    fun getPreviewContent(): String = ANDROID_GITIGNORE_CONTENT

    companion object {
        const val ANDROID_GITIGNORE_CONTENT = """# Gradle
.gradle/
build/

# Local configuration
local.properties

# IntelliJ / Android Studio
.idea/
*.iml
*.iws
*.ipr
.DS_Store

# Keystore
*.jks
*.keystore
keystore.properties

# NDK
.cxx/

# Google Services
google-services.json

# Secrets
.env
credentials.json
service-account.json
*.pem
*.key

# Logs
*.log

# Crashlytics
crashlytics-build.properties
"""
    }
}
