package com.gitofy.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox

object Routes {
    const val SPLASH = "splash"
    const val AUTH = "auth"
    const val HOME = "home"
    const val INBOX = "inbox"
    const val SETTINGS = "settings"
    const val SEARCH = "search"
    const val GITO_AI = "gito_ai"
    const val AGENT = "agent"
    const val REPOSITORIES = "repositories"
    const val REPOSITORY_DETAILS = "repository/{owner}/{repo}"
    const val CREATE_PROJECT = "create_project"
    const val UPLOAD_PROGRESS = "upload_progress/{operationId}"
    const val WORKFLOWS = "workflows/{owner}/{repo}"
    const val WORKFLOW_DETAILS = "workflow_details/{owner}/{repo}/{runId}"
    const val LOGS = "logs/{owner}/{repo}/{jobId}"
    const val ARTIFACTS = "artifacts/{owner}/{repo}/{runId}"
    const val OPERATIONS = "operations"
    const val COMMAND_CENTER = "command_center"
    const val REPOSITORY_HEALTH = "repository_health/{owner}/{repo}"
    const val UPDATE_REPOSITORY = "update_repository/{owner}/{repo}"
    const val JOBS = "jobs"
    const val GITO_REPAIR = "gito_repair/{owner}/{repo}/{runId}/{jobId}/{workflowId}/{branch}/{commitSha}/{failedJobName}/{failedStepName}"

    // PRD §2 — Settings category routes
    const val SETTINGS_APPEARANCE = "settings/appearance"
    const val SETTINGS_API_PROVIDERS = "settings/api_providers"
    const val SETTINGS_MODELS = "settings/models"
    const val SETTINGS_AGENT = "settings/agent"
    const val SETTINGS_EDITOR = "settings/editor"
    const val SETTINGS_WORKSPACE = "settings/workspace"
    const val SETTINGS_GITHUB = "settings/github"
    const val SETTINGS_BUILD = "settings/build"
    const val SETTINGS_NOTIFICATIONS = "settings/notifications"
    const val SETTINGS_PRIVACY = "settings/privacy"
    const val SETTINGS_ADVANCED = "settings/advanced"
    const val SETTINGS_ABOUT = "settings/about"

    fun gitoRepair(
        owner: String,
        repo: String,
        runId: Long,
        jobId: Long,
        workflowId: String,
        branch: String,
        commitSha: String,
        failedJobName: String,
        failedStepName: String
    ) = "gito_repair/$owner/$repo/$runId/$jobId/$workflowId/$branch/$commitSha/$failedJobName/$failedStepName"

    fun repositoryDetails(owner: String, repo: String) = "repository/$owner/$repo"
    fun repositoryHealth(owner: String, repo: String) = "repository_health/$owner/$repo"
    fun updateRepository(owner: String, repo: String) = "update_repository/$owner/$repo"
    fun workflows(owner: String, repo: String) = "workflows/$owner/$repo"
    fun workflowDetails(owner: String, repo: String, runId: Long) = "workflow_details/$owner/$repo/$runId"
    fun logs(owner: String, repo: String, jobId: Long) = "logs/$owner/$repo/$jobId"
    fun artifacts(owner: String, repo: String, runId: Long) = "artifacts/$owner/$repo/$runId"
    fun uploadProgress(operationId: String) = "upload_progress/$operationId"
}

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

/**
 * PRD §12: Bottom navigation — only Home and Inbox.
 * Settings is accessed via top-right icon on Home.
 * Search is accessed via top-right search icon on Home.
 * Repos are shown directly on Home screen.
 * GITO AI is accessed via Home quick action card.
 */
object BottomNavItems {
    val items = listOf(
        BottomNavItem(Routes.HOME, "Home", Icons.Filled.Home),
        BottomNavItem(Routes.INBOX, "Inbox", Icons.Filled.Inbox)
    )
}
