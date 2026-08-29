package com.gitofy.data.git

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Automated Workflow Injection — PRD Addendum: CI/CD Workflow Specification.
 * During the repository creation process, GITOFY must auto-populate the
 * .github/workflows/ directory with both pipeline definitions (Debug + Release)
 * prior to executing the initial JGit commit and push.
 */
@Singleton
class WorkflowInjector @Inject constructor() {

    /**
     * Inject CI/CD workflow files into a project directory before commit.
     * This ensures the GitHub Actions pipelines are available immediately
     * after the first push.
     */
    fun injectWorkflows(projectDirectory: String): Result<List<File>> {
        return try {
            val workflowsDir = File(projectDirectory, ".github/workflows")
            workflowsDir.mkdirs()

            val debugWorkflow = File(workflowsDir, "build.yml")
            debugWorkflow.writeText(DEBUG_BUILD_WORKFLOW)

            val releaseWorkflow = File(workflowsDir, "release.yml")
            releaseWorkflow.writeText(RELEASE_BUILD_WORKFLOW)

            listOf(debugWorkflow, releaseWorkflow)
            Result.success(listOf(debugWorkflow, releaseWorkflow))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        const val DEBUG_BUILD_WORKFLOW = """name: Build GITOFY

on:
  push:
    branches: [ main, master ]
  pull_request:
    branches: [ main, master ]
  workflow_dispatch:

jobs:
  debug-build:
    name: Debug APK Build
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

      - name: Build Debug APK
        run: ./gradlew assembleDebug

      - name: Run Unit Tests
        run: ./gradlew testDebugUnitTest

      - name: Run Lint
        run: ./gradlew lintDebug

      - name: Upload Debug APK Artifact
        uses: actions/upload-artifact@v4
        with:
          name: gitofy-debug-apk
          path: app/build/outputs/apk/debug/*.apk
          retention-days: 30
"""

        val RELEASE_BUILD_WORKFLOW = """name: Release GITOFY (Secret-Free Signed)

on:
  workflow_dispatch:
  push:
    tags:
      - 'v*'

jobs:
  release-build:
    name: Secret-Free Signed Release APK
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

      - name: Build Release APK (Unsigned)
        run: ./gradlew assembleRelease

      - name: Generate Dynamic Keystore
        run: |
          keytool -genkeypair \\
            -alias gitofy-release \\
            -keyalg RSA \\
            -keysize 2048 \\
            -validity 10000 \\
            -keystore ${'$'}{{ github.workspace }}/gitofy-release.jks \\
            -storepass gitofy123 \\
            -keypass gitofy123 \\
            -dname "CN=GITOFY, OU=Mobile, O=GITOFY, L=Kolkata, ST=West Bengal, C=IN"

      - name: Sign Release APK
        run: |
          ${'$'}ANDROID_HOME/build-tools/35.0.0/apksigner sign \\
            --ks ${'$'}{{ github.workspace }}/gitofy-release.jks \\
            --ks-key-alias gitofy-release \\
            --ks-pass pass:gitofy123 \\
            --key-pass pass:gitofy123 \\
            --out app/build/outputs/apk/release/app-release-signed.apk \\
            app/build/outputs/apk/release/app-release-unsigned.apk

      - name: Verify Signed APK
        run: |
          ${'$'}ANDROID_HOME/build-tools/35.0.0/apksigner verify --verbose app/build/outputs/apk/release/app-release-signed.apk

      - name: Upload Signed Release APK
        uses: actions/upload-artifact@v4
        with:
          name: gitofy-release-signed-apk
          path: app/build/outputs/apk/release/app-release-signed.apk
          retention-days: 90

      - name: Create GitHub Release
        if: startsWith(github.ref, 'refs/tags/')
        uses: softprops/action-gh-release@v2
        with:
          files: app/build/outputs/apk/release/app-release-signed.apk
          generate_release_notes: true
"""
    }
}
