package com.gitofy.data.git

import java.io.File

/**
 * Built-in GitHub Actions workflow templates for GITOFY (PRD §53).
 *
 * Each [WorkflowTemplate] is a ready-to-commit `.yml` file under `.github/workflows/`.
 * Templates intentionally avoid any hard-coded signing material — every secret is
 * referenced through GitHub Secrets (PRD §54), e.g. `${'$'}{{ secrets.ANDROID_KEYSTORE_BASE64 }}`.
 *
 * See [shouldInjectWorkflow] (PRD §52) for the rule GITOFY uses to decide whether a
 * template may be added to a repository that already contains workflow files.
 */
object WorkflowTemplates {

    /** A single built-in workflow that GITOFY can inject into a repository. */
    data class WorkflowTemplate(
        /** Human-readable name, e.g. "Android Debug". */
        val name: String,
        /** One-line summary shown in the template picker. */
        val description: String,
        /** Target filename inside `.github/workflows/`, including extension. */
        val fileName: String,
        /** Full GitHub Actions YAML content. */
        val content: String,
    )

    // ------------------------------------------------------------- catalog
    //
    // PRD §54: signing keys, store passwords, key aliases and key passwords are NEVER
    // inlined. They are always pulled from GitHub Secrets at job-start time.
    //
    // GitHub Actions uses `${{ ... }}` expressions. Inside a Kotlin triple-quoted string
    // `${...}` is a Kotlin template, so a literal dollar sign is emitted via `${'$'}` and
    // the braces that follow are plain text. Every `${{ expr }}` below is therefore written
    // as `${'$'}{{ expr }}`, which the compiler turns into the exact YAML text `${{ expr }}`.

    private val androidDebug = WorkflowTemplate(
        name = "Android Debug",
        description = "Build the debug APK (assembleDebug) on every push and pull request.",
        fileName = "android-debug.yml",
        content = """
            name: Android Debug

            on:
              push:
                branches: [ main, master, develop ]
              pull_request:
                branches: [ main, master ]

            concurrency:
              group: android-debug-${'$'}{{ github.ref }}
              cancel-in-progress: true

            jobs:
              build-debug:
                runs-on: ubuntu-latest
                steps:
                  - name: Checkout
                    uses: actions/checkout@v4

                  - name: Set up JDK
                    uses: actions/setup-java@v4
                    with:
                      distribution: temurin
                      java-version: '17'

                  - name: Set up Gradle
                    uses: gradle/actions/setup-gradle@v4

                  - name: Assemble debug
                    run: ./gradlew assembleDebug --stacktrace

                  - name: Upload debug APK
                    uses: actions/upload-artifact@v4
                    with:
                      name: debug-apk
                      path: app/build/outputs/apk/debug/*.apk
                      if-no-files-found: warn
        """.trimIndent(),
    )

    private val androidRelease = WorkflowTemplate(
        name = "Android Release",
        description = "Build and sign the release APK using GitHub Secrets for keystore material.",
        fileName = "android-release.yml",
        content = """
            name: Android Release

            on:
              push:
                tags: [ 'v*' ]
              workflow_dispatch:

            jobs:
              build-release:
                runs-on: ubuntu-latest
                steps:
                  - name: Checkout
                    uses: actions/checkout@v4

                  - name: Set up JDK
                    uses: actions/setup-java@v4
                    with:
                      distribution: temurin
                      java-version: '17'

                  - name: Set up Gradle
                    uses: gradle/actions/setup-gradle@v4

                  - name: Decode release keystore
                    env:
                      ANDROID_KEYSTORE_BASE64: ${'$'}{{ secrets.ANDROID_KEYSTORE_BASE64 }}
                    run: |
                      echo "${'$'}{{ secrets.ANDROID_KEYSTORE_BASE64 }}" | base64 -d > release.keystore

                  - name: Assemble release
                    env:
                      ANDROID_KEYSTORE_PATH: ${'$'}{{ github.workspace }}/release.keystore
                      ANDROID_KEYSTORE_PASSWORD: ${'$'}{{ secrets.ANDROID_KEYSTORE_PASSWORD }}
                      ANDROID_KEY_ALIAS: ${'$'}{{ secrets.ANDROID_KEY_ALIAS }}
                      ANDROID_KEY_PASSWORD: ${'$'}{{ secrets.ANDROID_KEY_PASSWORD }}
                    run: ./gradlew assembleRelease --stacktrace

                  - name: Upload release APK
                    uses: actions/upload-artifact@v4
                    with:
                      name: release-apk
                      path: app/build/outputs/apk/release/*.apk
                      if-no-files-found: warn
        """.trimIndent(),
    )

    private val unitTest = WorkflowTemplate(
        name = "Unit Test",
        description = "Run JVM unit tests (testDebugUnitTest) on every push and pull request.",
        fileName = "unit-test.yml",
        content = """
            name: Unit Tests

            on:
              push:
                branches: [ main, master, develop ]
              pull_request:
                branches: [ main, master ]

            concurrency:
              group: unit-tests-${'$'}{{ github.ref }}
              cancel-in-progress: true

            jobs:
              unit-test:
                runs-on: ubuntu-latest
                steps:
                  - name: Checkout
                    uses: actions/checkout@v4

                  - name: Set up JDK
                    uses: actions/setup-java@v4
                    with:
                      distribution: temurin
                      java-version: '17'

                  - name: Set up Gradle
                    uses: gradle/actions/setup-gradle@v4

                  - name: Run unit tests
                    run: ./gradlew testDebugUnitTest --stacktrace

                  - name: Upload test report
                    if: always()
                    uses: actions/upload-artifact@v4
                    with:
                      name: unit-test-report
                      path: app/build/reports/tests/testDebugUnitTest/
                      if-no-files-found: warn
        """.trimIndent(),
    )

    private val lint = WorkflowTemplate(
        name = "Lint",
        description = "Run Android Lint and upload the HTML report.",
        fileName = "lint.yml",
        content = """
            name: Lint

            on:
              push:
                branches: [ main, master, develop ]
              pull_request:
                branches: [ main, master ]

            concurrency:
              group: lint-${'$'}{{ github.ref }}
              cancel-in-progress: true

            jobs:
              lint:
                runs-on: ubuntu-latest
                steps:
                  - name: Checkout
                    uses: actions/checkout@v4

                  - name: Set up JDK
                    uses: actions/setup-java@v4
                    with:
                      distribution: temurin
                      java-version: '17'

                  - name: Set up Gradle
                    uses: gradle/actions/setup-gradle@v4

                  - name: Run lint
                    run: ./gradlew lintDebug --stacktrace

                  - name: Upload lint report
                    if: always()
                    uses: actions/upload-artifact@v4
                    with:
                      name: lint-report
                      path: app/build/reports/lint-results-debug.html
                      if-no-files-found: warn
        """.trimIndent(),
    )

    private val apkArtifact = WorkflowTemplate(
        name = "APK Artifact",
        description = "Build, sign and upload a release APK artifact on version tags.",
        fileName = "apk-artifact.yml",
        content = """
            name: APK Artifact

            on:
              push:
                tags: [ 'v*' ]
              workflow_dispatch:

            jobs:
              apk-artifact:
                runs-on: ubuntu-latest
                steps:
                  - name: Checkout
                    uses: actions/checkout@v4

                  - name: Set up JDK
                    uses: actions/setup-java@v4
                    with:
                      distribution: temurin
                      java-version: '17'

                  - name: Set up Gradle
                    uses: gradle/actions/setup-gradle@v4

                  - name: Decode release keystore
                    env:
                      ANDROID_KEYSTORE_BASE64: ${'$'}{{ secrets.ANDROID_KEYSTORE_BASE64 }}
                    run: |
                      echo "${'$'}{{ secrets.ANDROID_KEYSTORE_BASE64 }}" | base64 -d > release.keystore

                  - name: Assemble release APK
                    env:
                      ANDROID_KEYSTORE_PATH: ${'$'}{{ github.workspace }}/release.keystore
                      ANDROID_KEYSTORE_PASSWORD: ${'$'}{{ secrets.ANDROID_KEYSTORE_PASSWORD }}
                      ANDROID_KEY_ALIAS: ${'$'}{{ secrets.ANDROID_KEY_ALIAS }}
                      ANDROID_KEY_PASSWORD: ${'$'}{{ secrets.ANDROID_KEY_PASSWORD }}
                    run: ./gradlew assembleRelease --stacktrace

                  - name: Upload APK artifact
                    uses: actions/upload-artifact@v4
                    with:
                      name: apk-${'$'}{{ github.ref_name }}
                      path: app/build/outputs/apk/release/*.apk
                      if-no-files-found: warn
        """.trimIndent(),
    )

    private val aabArtifact = WorkflowTemplate(
        name = "AAB Artifact",
        description = "Build, sign and upload a release Android App Bundle for Play Store upload.",
        fileName = "aab-artifact.yml",
        content = """
            name: AAB Artifact

            on:
              push:
                tags: [ 'v*' ]
              workflow_dispatch:

            jobs:
              aab-artifact:
                runs-on: ubuntu-latest
                steps:
                  - name: Checkout
                    uses: actions/checkout@v4

                  - name: Set up JDK
                    uses: actions/setup-java@v4
                    with:
                      distribution: temurin
                      java-version: '17'

                  - name: Set up Gradle
                    uses: gradle/actions/setup-gradle@v4

                  - name: Decode release keystore
                    env:
                      ANDROID_KEYSTORE_BASE64: ${'$'}{{ secrets.ANDROID_KEYSTORE_BASE64 }}
                    run: |
                      echo "${'$'}{{ secrets.ANDROID_KEYSTORE_BASE64 }}" | base64 -d > release.keystore

                  - name: Assemble release AAB
                    env:
                      ANDROID_KEYSTORE_PATH: ${'$'}{{ github.workspace }}/release.keystore
                      ANDROID_KEYSTORE_PASSWORD: ${'$'}{{ secrets.ANDROID_KEYSTORE_PASSWORD }}
                      ANDROID_KEY_ALIAS: ${'$'}{{ secrets.ANDROID_KEY_ALIAS }}
                      ANDROID_KEY_PASSWORD: ${'$'}{{ secrets.ANDROID_KEY_PASSWORD }}
                    run: ./gradlew bundleRelease --stacktrace

                  - name: Upload AAB artifact
                    uses: actions/upload-artifact@v4
                    with:
                      name: aab-${'$'}{{ github.ref_name }}
                      path: app/build/outputs/bundle/release/*.aab
                      if-no-files-found: warn
        """.trimIndent(),
    )

    /** All built-in templates, in picker order. */
    private val templates: List<WorkflowTemplate> = listOf(
        androidDebug,
        androidRelease,
        unitTest,
        lint,
        apkArtifact,
        aabArtifact,
    )

    // ----------------------------------------------------------------- API

    /** Every built-in [WorkflowTemplate]. Returns a defensive copy. */
    fun getAllTemplates(): List<WorkflowTemplate> = templates.toList()

    /**
     * Look up a template by its [name] (case-insensitive).
     * @return the matching template, or null if none matches.
     */
    fun getTemplate(name: String): WorkflowTemplate? =
        templates.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /**
     * Scan a local checkout for existing GitHub Actions workflows (PRD §53).
     *
     * Looks for `.github/workflows/` `*.{yml,yaml}` under [projectPath]. Returns just the
     * file basenames, which is what [shouldInjectWorkflow] keys on.
     *
     * @param projectPath Repository root. A non-existent / non-directory path yields [].
     * @return Basenames like `["android-debug.yml", "lint.yaml"]`, sorted.
     */
    fun detectExistingWorkflows(projectPath: String): List<String> {
        val dir = File(projectPath).resolve(".github/workflows")
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.listFiles { f ->
            f.isFile && (f.extension.equals("yml", true) || f.extension.equals("yaml", true))
        }?.map { it.name }?.sorted() ?: emptyList()
    }

    /**
     * Decide whether [template] may be injected given the workflows already present in
     * the repository (PRD §52).
     *
     * Rule: do not overwrite. If a file with the template's [WorkflowTemplate.fileName]
     * already exists in [existingFiles], return false so GITOFY surfaces a "already
     * present" note instead of clobbering the user's customisations. Everything else
     * is injectable.
     *
     * @param existingFiles Basenames present under `.github/workflows/` (see [detectExistingWorkflows]).
     * @param template Candidate template.
     * @return true if the template's file is absent and may be added.
     */
    fun shouldInjectWorkflow(existingFiles: List<String>, template: WorkflowTemplate): Boolean {
        val normalized = existingFiles.map { it.trim() }
        return template.fileName !in normalized
    }
}
