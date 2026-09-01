package com.gitofy.core.security

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GitHub Permission Preflight (PRD §14).
 *
 * Inspects a GitHub token's effective scopes against the set of permissions GITOFY needs
 * for a given operation, so the UI can warn the user and offer a "Fix Permissions" path
 * *before* an API call fails with 403/404.
 *
 * Classic PAT/OAuth scopes are checked against GitHub's authenticated `/user` endpoint.
 * Fine-grained repository permissions remain UNKNOWN when they cannot be inferred globally;
 * the concrete repository operation remains the authoritative check for those tokens.
 */
@Singleton
class PermissionPreflight @Inject constructor() {

    // ------------------------------------------------------------------ enums

    /** Coarse bucket a GitHub fine-grained/classic permission falls into for GITOFY's UI. */
    enum class PermissionCategory {
        METADATA_READ,
        REPO_READ,
        REPO_ADMIN,
        CONTENTS_READ,
        CONTENTS_WRITE,
        WORKFLOWS_READ,
        WORKFLOWS_WRITE,
        ACTIONS_READ,
        ACTIONS_WRITE,
        ISSUES,
        PULL_REQUESTS,
        RELEASES,
        BRANCHES,
        TAGS,
        ORGANIZATIONS,
    }

    /** Outcome of a single category check. */
    enum class PermissionStatus {
        /** The token demonstrably holds this permission. */
        GRANTED,

        /** The token demonstrably lacks this permission. */
        MISSING,

        /** Preflight could not determine the state (stub API, no scopes header, etc.). */
        UNKNOWN,
    }

    // ------------------------------------------------------------- data class

    /** Result of checking one [PermissionCategory] for the active token. */
    data class PermissionResult(
        val category: PermissionCategory,
        val status: PermissionStatus,
        val displayName: String,
        /** Short human-readable note of what GITOFY operation needs this permission. */
        val requiredFor: String,
    )

    // ---------------------------------------------------------------- state

    /**
     * Last full snapshot produced by [checkPermissions].
     * Guarded by [lock]; read via [getMissingPermissions] and friends.
     */
    @Volatile
    private var lastResults: List<PermissionResult> = emptyList()

    private val lock = Any()

    // ---------------------------------------------------------------- API

    /**
     * Inspect a GitHub token using the authenticated /user endpoint. Classic PAT/OAuth
     * scopes are exposed by GitHub in X-OAuth-Scopes. Fine-grained tokens intentionally
     * remain UNKNOWN here because their repository-specific permissions cannot be inferred
     * globally from /user; callers should verify the concrete operation against the target repo.
     */
    fun checkPermissions(token: String): List<PermissionResult> {
        if (token.isBlank()) return cacheUnknownResults()

        val scopes = runCatching {
            val connection = (URL("https://api.github.com/user").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            }
            try {
                if (connection.responseCode !in 200..299) return@runCatching emptySet<String>()
                connection.getHeaderField("X-OAuth-Scopes")
                    ?.split(',')
                    ?.map { it.trim().lowercase() }
                    ?.filter { it.isNotEmpty() }
                    ?.toSet()
                    ?: emptySet()
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(emptySet())

        val hasClassicScopes = scopes.isNotEmpty()
        val results = PermissionCategory.entries.map { category ->
            PermissionResult(
                category = category,
                status = if (!hasClassicScopes) PermissionStatus.UNKNOWN else scopeStatus(category, scopes),
                displayName = displayNames.getValue(category),
                requiredFor = requiredForDescriptions.getValue(category)
            )
        }
        synchronized(lock) { lastResults = results }
        return results
    }

    private fun cacheUnknownResults(): List<PermissionResult> {
        val results = PermissionCategory.entries.map { category ->
            PermissionResult(category, PermissionStatus.UNKNOWN, displayNames.getValue(category), requiredForDescriptions.getValue(category))
        }
        synchronized(lock) { lastResults = results }
        return results
    }

    private fun scopeStatus(category: PermissionCategory, scopes: Set<String>): PermissionStatus {
        fun has(vararg values: String): Boolean = values.any { it.lowercase() in scopes }
        val granted = when (category) {
            PermissionCategory.METADATA_READ -> has("user", "read:user", "user:email", "repo", "public_repo")
            PermissionCategory.REPO_READ, PermissionCategory.CONTENTS_READ -> has("repo", "public_repo", "repo:status")
            PermissionCategory.REPO_ADMIN -> has("repo")
            PermissionCategory.CONTENTS_WRITE -> has("repo", "public_repo")
            PermissionCategory.WORKFLOWS_READ, PermissionCategory.WORKFLOWS_WRITE -> has("workflow", "repo")
            PermissionCategory.ACTIONS_READ, PermissionCategory.ACTIONS_WRITE -> has("repo", "workflow")
            PermissionCategory.ISSUES -> has("repo", "public_repo")
            PermissionCategory.PULL_REQUESTS -> has("repo", "public_repo")
            PermissionCategory.RELEASES -> has("repo", "public_repo")
            PermissionCategory.BRANCHES, PermissionCategory.TAGS -> has("repo", "public_repo")
            PermissionCategory.ORGANIZATIONS -> has("read:org", "write:org", "admin:org", "repo")
        }
        return if (granted) PermissionStatus.GRANTED else PermissionStatus.MISSING
    }

    /**
     * Check only [required] categories against the cached snapshot, refreshing first
     * if nothing is cached yet.
     *
     * @param required Categories the caller's operation needs.
     * @return Subset of results for [required]; missing categories are reported as
     *   [PermissionStatus.UNKNOWN].
     */
    fun checkRequiredPermissions(required: List<PermissionCategory>): List<PermissionResult> {
        val snapshot = synchronized(lock) {
            if (lastResults.isEmpty()) null else lastResults
        }
        val working = snapshot ?: checkPermissions("")

        return required.map { req ->
            working.firstOrNull { it.category == req }
                ?: PermissionResult(
                    category = req,
                    status = PermissionStatus.UNKNOWN,
                    displayName = displayNames.getValue(req),
                    requiredFor = requiredForDescriptions.getValue(req),
                )
        }
    }

    /**
     * Convenience accessor over the cached snapshot: every category whose status is
     * [PermissionStatus.MISSING] (definitively absent).
     *
     * Note: [PermissionStatus.UNKNOWN] is intentionally excluded — "we don't know yet"
     * should not be surfaced as a hard failure to the user.
     */
    fun getMissingPermissions(): List<PermissionResult> =
        synchronized(lock) { lastResults.filter { it.status == PermissionStatus.MISSING } }

    /**
     * Human guidance for the "Fix Permissions" action — explains what's missing and what
     * the user should grant. Built from [getMissingPermissions]; if nothing is definitively
     * missing but something is [PermissionStatus.UNKNOWN], the user is nudged to re-auth.
     */
    fun generateFixGuidance(): String {
        val missing = getMissingPermissions()
        val unknown = synchronized(lock) { lastResults.filter { it.status == PermissionStatus.UNKNOWN } }

        return buildString {
            appendLine("GitHub permission preflight needs attention.")
            appendLine()
            if (missing.isNotEmpty()) {
                appendLine("Missing permissions (${missing.size}):")
                missing.forEach { r ->
                    appendLine("  • ${r.displayName} — needed for ${r.requiredFor}")
                }
                appendLine()
                appendLine("Re-authorise your GitHub token with the scopes above, then retry.")
            } else if (unknown.isNotEmpty()) {
                appendLine("Could not verify ${unknown.size} permission(s).")
                appendLine("Re-authorise your GitHub token so GITOFY can confirm access, then retry.")
            } else {
                appendLine("All required GitHub permissions are present.")
            }
            appendLine()
            appendLine("Tip: fine-grained tokens should target the specific repository and select the")
            appendLine("matching repository permissions; classic tokens need the matching OAuth scopes.")
        }.trimEnd()
    }

    // ------------------------------------------------------------- catalog

    /** Display names surfaced in the UI for each [PermissionCategory]. */
    val displayNames: Map<PermissionCategory, String> = mapOf(
        PermissionCategory.METADATA_READ to "Read profile & metadata",
        PermissionCategory.REPO_READ to "Read repositories",
        PermissionCategory.REPO_ADMIN to "Administer repositories",
        PermissionCategory.CONTENTS_READ to "Read repository contents",
        PermissionCategory.CONTENTS_WRITE to "Write repository contents",
        PermissionCategory.WORKFLOWS_READ to "Read workflows",
        PermissionCategory.WORKFLOWS_WRITE to "Write workflows",
        PermissionCategory.ACTIONS_READ to "Read GitHub Actions",
        PermissionCategory.ACTIONS_WRITE to "Manage GitHub Actions",
        PermissionCategory.ISSUES to "Issues",
        PermissionCategory.PULL_REQUESTS to "Pull requests",
        PermissionCategory.RELEASES to "Releases",
        PermissionCategory.BRANCHES to "Branches",
        PermissionCategory.TAGS to "Tags",
        PermissionCategory.ORGANIZATIONS to "Organizations",
    )

    /** What each category enables inside GITOFY — shown next to the permission in the UI. */
    val requiredForDescriptions: Map<PermissionCategory, String> = mapOf(
        PermissionCategory.METADATA_READ to "Displaying your GitHub user profile",
        PermissionCategory.REPO_READ to "Listing and browsing your repositories",
        PermissionCategory.REPO_ADMIN to "Changing repository settings & visibility",
        PermissionCategory.CONTENTS_READ to "Reading files, branches and commit history",
        PermissionCategory.CONTENTS_WRITE to "Committing files and pushing changes",
        PermissionCategory.WORKFLOWS_READ to "Listing existing GitHub Actions workflows",
        PermissionCategory.WORKFLOWS_WRITE to "Adding or updating GitHub Actions workflows",
        PermissionCategory.ACTIONS_READ to "Viewing workflow runs and logs",
        PermissionCategory.ACTIONS_WRITE to "Triggering, re-running or cancelling workflow runs",
        PermissionCategory.ISSUES to "Reading and creating issues",
        PermissionCategory.PULL_REQUESTS to "Opening, reviewing and merging pull requests",
        PermissionCategory.RELEASES to "Publishing and managing releases",
        PermissionCategory.BRANCHES to "Creating, listing and protecting branches",
        PermissionCategory.TAGS to "Creating and listing tags",
        PermissionCategory.ORGANIZATIONS to "Listing org-owned repositories and membership",
    )

    // ----------------------------------------------------------- file probe

    /**
     * Optional local probe: read the token from a [file] (e.g. a credentials cache) and
     * run [checkPermissions]. Returns an empty list if the file is absent or unreadable
     * rather than throwing — callers treat absence as "not configured yet".
     */
    fun checkPermissionsFromFile(file: File): List<PermissionResult> {
        if (!file.exists() || !file.isFile || !file.canRead()) return emptyList()
        return runCatching { file.readText().trim() }
            .map { checkPermissions(it) }
            .getOrDefault(emptyList())
    }
}
