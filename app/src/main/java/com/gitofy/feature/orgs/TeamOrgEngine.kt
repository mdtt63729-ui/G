package com.gitofy.feature.orgs

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Multiple GitHub Accounts — PRD v6.5 Section 94.
 * Support securely managing multiple authorized GitHub identities.
 * Each account must have isolated credential storage.
 */
@Singleton
class AccountManager @Inject constructor() {

    data class Account(
        val id: String,
        val login: String,
        val avatarUrl: String,
        val type: AccountType,
        val isActive: Boolean
    )

    enum class AccountType(val displayName: String) {
        PERSONAL("Personal"), WORK("Work"), CLIENT("Client"), ORGANIZATION("Organization")
    }

    private val accounts = mutableListOf<Account>()
    private var activeAccount: Account? = null

    fun getAccounts(): List<Account> = accounts.toList()

    fun getActiveAccount(): Account? = activeAccount

    fun addAccount(account: Account) {
        accounts.add(account)
        if (activeAccount == null) activeAccount = account
    }

    /**
     * Account Switching — PRD v6.5 Section 95.
     * Flow: Current Account → Account Selector → Select → Re-auth check → Workspace switch
     * Cached repository data must be scoped to the correct account.
     */
    fun switchAccount(accountId: String): Boolean {
        val account = accounts.find { it.id == accountId } ?: return false
        accounts.replaceAll { it.copy(isActive = it.id == accountId) }
        activeAccount = account
        return true
    }

    fun removeAccount(accountId: String) {
        accounts.removeAll { it.id == accountId }
        if (activeAccount?.id == accountId) {
            activeAccount = accounts.firstOrNull()
        }
    }
}

/**
 * Multi-Account Data Isolation — PRD v6.5 Section 104.
 * All Room entities containing GitHub resources must be scoped with accountId.
 */
@Singleton
class MultiAccountIsolation @Inject constructor() {

    /**
     * Generate scoped ID: accountId + repositoryId
     */
    fun scopedId(accountId: String, resourceId: String): String = "$accountId:$resourceId"

    /**
     * Verify that a resource belongs to the active account.
     */
    fun isResourceForAccount(scopedId: String, accountId: String): Boolean {
        return scopedId.startsWith("$accountId:")
    }
}

/**
 * Repository Health Score — PRD v6.5 Section 98.
 * Create a transparent heuristic based on observable metrics.
 * The score must explain its inputs.
 * Do not present it as an objective software-quality measurement.
 */
@Singleton
class RepositoryHealthScoreCalculator @Inject constructor() {

    fun calculate(
        repoName: String,
        ciSuccessRate: Float,
        openPRs: Int,
        openIssues: Int,
        recentCommits: Int,
        staleBranches: Int,
        recentReleases: Int
    ): com.gitofy.domain.model.RepositoryHealthScore {
        val ciScore = ciSuccessRate * 100
        val prScore = when {
            openPRs == 0 -> 100f
            openPRs <= 5 -> 80f
            openPRs <= 10 -> 60f
            else -> 40f
        }
        val issueScore = when {
            openIssues == 0 -> 100f
            openIssues <= 10 -> 80f
            openIssues <= 30 -> 60f
            else -> 40f
        }
        val releaseScore = when {
            recentReleases > 0 -> 100f
            else -> 50f
        }
        val overall = (ciScore * 0.4f + prScore * 0.2f + issueScore * 0.2f + releaseScore * 0.2f)

        return com.gitofy.domain.model.RepositoryHealthScore(
            repoId = 0,
            repoName = repoName,
            ciHealthScore = ciScore,
            prHealthScore = prScore,
            issueHealthScore = issueScore,
            releaseHealthScore = releaseScore,
            overallScore = overall,
            reasoning = "CI: ${ciScore.toInt()}%, PRs: ${prScore.toInt()}%, Issues: ${issueScore.toInt()}%, Releases: ${releaseScore.toInt()}%. " +
                "Weighted: CI(40%) + PR(20%) + Issues(20%) + Releases(20%)."
        )
    }
}

/**
 * Enterprise Security — PRD v6.5 Section 103.
 */
@Singleton
class EnterpriseSecurityConfig @Inject constructor() {

    data class EnterpriseConfig(
        val sessionTimeoutMinutes: Int = 30,
        val sensitiveScreenProtection: Boolean = true,
        val auditFriendlyHistory: Boolean = true,
        val explicitDestructiveConfirmations: Boolean = true
    )

    fun getConfig(): EnterpriseConfig = EnterpriseConfig()
}

/**
 * Team Permissions — PRD v6.5 Section 102.
 * The app must not invent permissions.
 * Display only permissions that can be reliably obtained from GitHub.
 */
@Singleton
class TeamPermissionChecker @Inject constructor() {

    enum class WritePermission { AVAILABLE, MISSING, UNKNOWN }

    fun checkWritePermission(userPermissions: String?): WritePermission {
        return when (userPermissions) {
            "admin", "write" -> WritePermission.AVAILABLE
            "read", "none" -> WritePermission.MISSING
            else -> WritePermission.UNKNOWN
        }
    }
}
