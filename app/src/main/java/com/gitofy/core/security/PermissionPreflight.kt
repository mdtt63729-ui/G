package com.gitofy.core.security

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GitHub Permission Preflight (PRD §14).
 *
 * Inspects a GitHub token's effective scopes against the set of permissions GITOFY needs
 * for a given operation, so the UI can warn the user and offer a "Fix Permissions" path
 * *before* an API call fails with 403/404.
 *
 * The [checkPermissions] entry point is currently a deterministic stub (see STUB note) — it
 * will be wired to the real GitHub `/user` + `X-OAuth-Scopes` response once networking lands.
 * Until then every category resolves to [PermissionStatus.UNKNOWN], which the UI treats as
 * "ask the user to (re)authorise" rather than "denied".
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
     * Inspect [token] and return the permission status for every [PermissionCategory].
     *
     * STUB: until the GitHub API client is available this returns every category as
     * [PermissionStatus.UNKNOWN]. Replace the body with a real
     * `GET https://api.github.com/user` (classic, `X-OAuth-Scopes` header) or fine-grained
     * token introspection call; keep the side effect of caching into [lastResults].
     *
     * @param token GitHub personal access / OAuth token. Treated as opaque — never logged.
     * @return one [PermissionResult] per category, never empty.
     */
    fun checkPermissions(token: String): List<PermissionResult> {
        @Suppress("UNUSED_PARAMETER")
        val effective: String = token

        val results = PermissionCategory.entries.map { category ->
            PermissionResult(
                category = category,
                // Real implementation: map scopes → GRANTED / MISSING.
                status = PermissionStatus.UNKNOWN,
                displayName = displayNames.getValue(category),
                requiredFor = requiredForDescriptions.getValue(category),
            )
        }

        synchronized(lock) { lastResults = results }
        return results
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
