package com.gitofy.core.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes for GITOFY.
 * Using string-based routes for Navigation Compose.
 */
object Routes {
    // Auth
    const val SPLASH = "splash"
    const val AUTH = "auth"

    // Main
    const val HOME = "home"
    const val REPOSITORIES = "repositories"
    const val SETTINGS = "settings"

    // Repository
    const val REPOSITORY_DETAILS = "repository/{owner}/{repo}"
    const val CREATE_PROJECT = "create_project"
    const val UPLOAD_PROGRESS = "upload_progress/{operationId}"

    // Workflow
    const val WORKFLOWS = "workflows/{owner}/{repo}"
    const val WORKFLOW_DETAILS = "workflow_details/{owner}/{repo}/{runId}"
    const val LOGS = "logs/{owner}/{repo}/{jobId}"

    // Artifacts
    const val ARTIFACTS = "artifacts/{owner}/{repo}/{runId}"

    fun repositoryDetails(owner: String, repo: String) = "repository/$owner/$repo"
    fun workflows(owner: String, repo: String) = "workflows/$owner/$repo"
    fun workflowDetails(owner: String, repo: String, runId: Long) = "workflow_details/$owner/$repo/$runId"
    fun logs(owner: String, repo: String, jobId: Long) = "logs/$owner/$repo/$jobId"
    fun artifacts(owner: String, repo: String, runId: Long) = "artifacts/$owner/$repo/$runId"
    fun uploadProgress(operationId: String) = "upload_progress/$operationId"
}

/**
 * Bottom navigation items.
 */
data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

object BottomNavItems {
    val items = listOf(
        BottomNavItem(Routes.HOME, "Home", androidx.compose.material.icons.Icons.Default.Home),
        BottomNavItem(Routes.REPOSITORIES, "Repos", androidx.compose.material.icons.Icons.Default.Cloud),
        BottomNavItem(Routes.SETTINGS, "Settings", androidx.compose.material.icons.Icons.Default.Settings)
    )
}
