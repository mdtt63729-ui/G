package com.gitofy.ai.scanning

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PRD §34: AI Repository Scanning.
 *
 * Scans an Android project directory tree and categorizes its files so the AI
 * pipeline can reason about the repository structure without ingesting every
 * file's contents.
 */
@Singleton
class RepositoryScanner @Inject constructor() {

    /**
     * Represents a single entry (file or directory) discovered in the project tree.
     *
     * @property path Path relative to the scanned project root, using '/' separators.
     * @property size Size in bytes (0 for directories).
     * @property isDirectory True if this entry is a directory.
     */
    data class FileInfo(
        val path: String,
        val size: Long,
        val isDirectory: Boolean
    )

    /**
     * Captures the Gradle configuration that drives the build.
     *
     * @property buildGradleContent Contents of the root `build.gradle`/`build.gradle.kts`.
     * @property settingsGradleContent Contents of `settings.gradle`/`settings.gradle.kts`.
     * @property versionCatalogContent Contents of `gradle/libs.versions.toml`, if present.
     */
    data class GradleInfo(
        val buildGradleContent: String,
        val settingsGradleContent: String,
        val versionCatalogContent: String?
    )

    /**
     * Aggregated result of a repository scan.
     *
     * @property fileTree Flat list of every file and directory discovered.
     * @property gradleConfig Parsed Gradle configuration, or null if not found.
     * @property kotlinFiles Relative paths of all `.kt` files.
     * @property javaFiles Relative paths of all `.java` files.
     * @property xmlFiles Relative paths of all `.xml` files.
     * @property yamlFiles Relative paths of all `.yaml`/`.yml` files.
     * @property workflowFiles Relative paths of GitHub Actions workflow files.
     * @property manifests Relative paths of `AndroidManifest.xml` files.
     * @property proguardFiles Relative paths of ProGuard/R8 rule files.
     * @property versionCatalogs Relative paths of version catalog files.
     * @property resourceFiles Relative paths of Android resource files (layouts, drawables, values, etc.).
     * @property testFiles Relative paths of test source files.
     */
    data class ScanResult(
        val fileTree: List<FileInfo>,
        val gradleConfig: GradleInfo?,
        val kotlinFiles: List<String>,
        val javaFiles: List<String>,
        val xmlFiles: List<String>,
        val yamlFiles: List<String>,
        val workflowFiles: List<String>,
        val manifests: List<String>,
        val proguardFiles: List<String>,
        val versionCatalogs: List<String>,
        val resourceFiles: List<String>,
        val testFiles: List<String>
    )

    /**
     * Scans the directory at [projectPath] and returns a categorized [ScanResult].
     *
     * The walk is recursive and follows symlinks that resolve inside the project
     * root. Build outputs (e.g. `build/`, `.gradle/`) are skipped to keep the
     * result focused on source-controlled content.
     */
    fun scanRepository(projectPath: String): ScanResult {
        val root = File(projectPath)
        require(root.exists() && root.isDirectory) {
            "Project path does not exist or is not a directory: $projectPath"
        }

        val fileTree = mutableListOf<FileInfo>()
        val kotlinFiles = mutableListOf<String>()
        val javaFiles = mutableListOf<String>()
        val xmlFiles = mutableListOf<String>()
        val yamlFiles = mutableListOf<String>()
        val workflowFiles = mutableListOf<String>()
        val manifests = mutableListOf<String>()
        val proguardFiles = mutableListOf<String>()
        val versionCatalogs = mutableListOf<String>()
        val resourceFiles = mutableListOf<String>()
        val testFiles = mutableListOf<String>()

        val ignoredSegments = setOf(
            "build",
            ".gradle",
            ".idea",
            ".git",
            "captures",
            ".cxx",
            "generated"
        )

        root.walkTopDown().forEach { file ->
            val relative = file.relativeTo(root).path.replace(File.separatorChar, '/')
            if (relative.isEmpty()) {
                return@forEach
            }

            // Skip build output directories and their contents.
            val segments = relative.split('/')
            if (segments.any { it in ignoredSegments }) {
                return@forEach
            }

            val isDir = file.isDirectory
            fileTree.add(FileInfo(relative, if (isDir) 0L else file.length(), isDir))

            if (isDir) return@forEach

            val name = file.name
            val isTest = segments.any { it == "test" || it == "androidTest" }

            when {
                name.endsWith(".kt") -> {
                    kotlinFiles.add(relative)
                    if (isTest) testFiles.add(relative)
                }

                name.endsWith(".java") -> {
                    javaFiles.add(relative)
                    if (isTest) testFiles.add(relative)
                }

                name.endsWith(".xml") -> {
                    xmlFiles.add(relative)
                    if (name == "AndroidManifest.xml") {
                        manifests.add(relative)
                    } else if (segments.any { it in RESOURCE_DIR_NAMES }) {
                        resourceFiles.add(relative)
                    }
                }

                name.endsWith(".yaml") || name.endsWith(".yml") -> {
                    yamlFiles.add(relative)
                    if (segments.contains(".github") && segments.contains("workflows")) {
                        workflowFiles.add(relative)
                    }
                }

                name == "proguard-rules.pro" ||
                    name.endsWith(".pro") ||
                    name.startsWith("proguard") && name.endsWith(".pro") -> {
                    proguardFiles.add(relative)
                }

                name == "libs.versions.toml" -> {
                    versionCatalogs.add(relative)
                }
            }
        }

        val gradleConfig = readGradleInfo(root)

        return ScanResult(
            fileTree = fileTree.sortedBy { it.path },
            gradleConfig = gradleConfig,
            kotlinFiles = kotlinFiles.sorted(),
            javaFiles = javaFiles.sorted(),
            xmlFiles = xmlFiles.sorted(),
            yamlFiles = yamlFiles.sorted(),
            workflowFiles = workflowFiles.sorted(),
            manifests = manifests.sorted(),
            proguardFiles = proguardFiles.sorted(),
            versionCatalogs = versionCatalogs.sorted(),
            resourceFiles = resourceFiles.sorted(),
            testFiles = testFiles.sorted()
        )
    }

    /**
     * Builds a lightweight metadata summary of the repository suitable for
     * inclusion in an AI context window. The summary is compact by design —
     * it lists file counts and key configuration rather than file contents.
     */
    fun lightweightMetadataScan(scanResult: ScanResult): String {
        val sb = StringBuilder()
        sb.appendLine("# Repository Metadata")
        sb.appendLine()

        sb.appendLine("## Overview")
        sb.appendLine("- Total entries: ${scanResult.fileTree.size}")
        sb.appendLine(
            "- Directories: ${scanResult.fileTree.count { it.isDirectory }}"
        )
        sb.appendLine(
            "- Files: ${scanResult.fileTree.count { !it.isDirectory }}"
        )
        sb.appendLine()

        sb.appendLine("## Source Files")
        sb.appendLine("- Kotlin (.kt): ${scanResult.kotlinFiles.size}")
        sb.appendLine("- Java (.java): ${scanResult.javaFiles.size}")
        sb.appendLine("- Tests: ${scanResult.testFiles.size}")
        sb.appendLine()

        sb.appendLine("## Resources & Manifest")
        sb.appendLine("- XML files: ${scanResult.xmlFiles.size}")
        sb.appendLine("- Android manifests: ${scanResult.manifests.size}")
        sb.appendLine("- Resource files: ${scanResult.resourceFiles.size}")
        sb.appendLine()

        sb.appendLine("## Build Configuration")
        scanResult.gradleConfig?.let { gradle ->
            sb.appendLine("- build.gradle present: yes")
            sb.appendLine("- settings.gradle present: yes")
            sb.appendLine(
                "- Version catalog: ${if (gradle.versionCatalogContent != null) "present" else "absent"}"
            )
        } ?: sb.appendLine("- Gradle configuration: not detected")
        sb.appendLine("- Version catalog files: ${scanResult.versionCatalogs.size}")
        sb.appendLine("- ProGuard files: ${scanResult.proguardFiles.size}")
        sb.appendLine()

        sb.appendLine("## CI/CD")
        sb.appendLine("- YAML files: ${scanResult.yamlFiles.size}")
        sb.appendLine("- GitHub workflow files: ${scanResult.workflowFiles.size}")
        sb.appendLine()

        if (scanResult.kotlinFiles.isNotEmpty()) {
            sb.appendLine("## Kotlin Files")
            scanResult.kotlinFiles.take(MAX_LISTED_FILES).forEach { sb.appendLine("- $it") }
            if (scanResult.kotlinFiles.size > MAX_LISTED_FILES) {
                sb.appendLine(
                    "- ... and ${scanResult.kotlinFiles.size - MAX_LISTED_FILES} more"
                )
            }
            sb.appendLine()
        }

        if (scanResult.workflowFiles.isNotEmpty()) {
            sb.appendLine("## Workflows")
            scanResult.workflowFiles.forEach { sb.appendLine("- $it") }
            sb.appendLine()
        }

        if (scanResult.manifests.isNotEmpty()) {
            sb.appendLine("## Manifests")
            scanResult.manifests.forEach { sb.appendLine("- $it") }
            sb.appendLine()
        }

        return sb.toString().trimEnd()
    }

    private fun readGradleInfo(root: File): GradleInfo? {
        val buildGradle = findFirst(root, BUILD_GRADLE_NAMES)
        val settingsGradle = findFirst(root, SETTINGS_GRADLE_NAMES)
        val versionCatalog = findFirst(File(root, "gradle"), VERSION_CATALOG_NAMES)

        if (buildGradle == null && settingsGradle == null) {
            return null
        }

        return GradleInfo(
            buildGradleContent = buildGradle?.readText().orEmpty(),
            settingsGradleContent = settingsGradle?.readText().orEmpty(),
            versionCatalogContent = versionCatalog?.readText()
        )
    }

    private fun findFirst(root: File, candidates: List<String>): File? =
        candidates.map { File(root, it) }.firstOrNull { it.exists() && it.isFile }

    private companion object {
        val RESOURCE_DIR_NAMES = setOf(
            "layout", "layouts",
            "drawable", "drawables",
            "values", "anim", "animator",
            "menu", "mipmap", "color", "font", "xml"
        )

        val BUILD_GRADLE_NAMES = listOf(
            "build.gradle.kts", "build.gradle"
        )

        val SETTINGS_GRADLE_NAMES = listOf(
            "settings.gradle.kts", "settings.gradle"
        )

        val VERSION_CATALOG_NAMES = listOf("libs.versions.toml")

        const val MAX_LISTED_FILES = 100
    }
}
