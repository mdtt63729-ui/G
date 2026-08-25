package com.gitofy.core.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gitofy.feature.authentication.AuthenticationScreen
import com.gitofy.feature.home.HomeScreen
import com.gitofy.feature.repositories.RepositoryListScreen
import com.gitofy.feature.repositories.details.RepositoryDetailsScreen
import com.gitofy.feature.createproject.CreateProjectScreen
import com.gitofy.feature.createproject.UploadProgressScreen
import com.gitofy.feature.workflows.WorkflowListScreen
import com.gitofy.feature.workflows.details.WorkflowDetailsScreen
import com.gitofy.feature.workflows.logs.LogsScreen
import com.gitofy.feature.artifacts.ArtifactsScreen
import com.gitofy.feature.settings.SettingsScreen

@Composable
fun GITOFYNavHost(
    navController: androidx.navigation.NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in listOf(
        Routes.HOME,
        Routes.REPOSITORIES,
        Routes.SETTINGS
    )

    androidx.compose.foundation.layout.Column {
        androidx.compose.foundation.layout.Box(
            modifier = androidx.compose.ui.Modifier.weight(1f)
        ) {
            NavHost(
                navController = navController,
                startDestination = Routes.SPLASH,
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { it / 4 }) + fadeIn(tween(300))
                },
                exitTransition = {
                    fadeOut(tween(200))
                },
                popEnterTransition = {
                    fadeIn(tween(300))
                },
                popExitTransition = {
                    slideOutHorizontally(targetOffsetX = { it / 4 }) + fadeOut(tween(200))
                }
            ) {
                // Splash
                composable(Routes.SPLASH) {
                    com.gitofy.feature.splash.SplashScreen(
                        onNavigate = { route ->
                            navController.navigate(route) {
                                popUpTo(Routes.SPLASH) { inclusive = true }
                            }
                        }
                    )
                }

                // Authentication
                composable(Routes.AUTH) {
                    AuthenticationScreen(
                        onAuthenticated = {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.AUTH) { inclusive = true }
                            }
                        }
                    )
                }

                // Home
                composable(Routes.HOME) {
                    HomeScreen(
                        onNavigateToRepos = { navController.navigate(Routes.REPOSITORIES) },
                        onNavigateToCreate = { navController.navigate(Routes.CREATE_PROJECT) },
                        onNavigateToRepoDetails = { owner, repo ->
                            navController.navigate(Routes.repositoryDetails(owner, repo))
                        },
                        onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
                    )
                }

                // Repository List
                composable(Routes.REPOSITORIES) {
                    RepositoryListScreen(
                        onRepoClick = { owner, repo ->
                            navController.navigate(Routes.repositoryDetails(owner, repo))
                        },
                        onCreateClick = { navController.navigate(Routes.CREATE_PROJECT) }
                    )
                }

                // Repository Details
                composable(
                    route = Routes.REPOSITORY_DETAILS,
                    arguments = listOf(
                        navArgument("owner") { type = NavType.StringType },
                        navArgument("repo") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val owner = backStackEntry.arguments?.getString("owner") ?: ""
                    val repo = backStackEntry.arguments?.getString("repo") ?: ""
                    RepositoryDetailsScreen(
                        owner = owner,
                        repo = repo,
                        onBack = { navController.popBackStack() },
                        onWorkflows = { navController.navigate(Routes.workflows(owner, repo)) }
                    )
                }

                // Create Project
                composable(Routes.CREATE_PROJECT) {
                    CreateProjectScreen(
                        onBack = { navController.popBackStack() },
                        onUploadStarted = { operationId ->
                            navController.navigate(Routes.uploadProgress(operationId))
                        }
                    )
                }

                // Upload Progress
                composable(
                    route = Routes.UPLOAD_PROGRESS,
                    arguments = listOf(
                        navArgument("operationId") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val operationId = backStackEntry.arguments?.getString("operationId") ?: ""
                    UploadProgressScreen(
                        operationId = operationId,
                        onComplete = { owner, repo ->
                            navController.navigate(Routes.repositoryDetails(owner, repo)) {
                                popUpTo(Routes.HOME)
                            }
                        },
                        onCancel = { navController.popBackStack() }
                    )
                }

                // Workflows
                composable(
                    route = Routes.WORKFLOWS,
                    arguments = listOf(
                        navArgument("owner") { type = NavType.StringType },
                        navArgument("repo") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val owner = backStackEntry.arguments?.getString("owner") ?: ""
                    val repo = backStackEntry.arguments?.getString("repo") ?: ""
                    WorkflowListScreen(
                        owner = owner,
                        repo = repo,
                        onBack = { navController.popBackStack() },
                        onRunClick = { runId ->
                            navController.navigate(Routes.workflowDetails(owner, repo, runId))
                        }
                    )
                }

                // Workflow Details
                composable(
                    route = Routes.WORKFLOW_DETAILS,
                    arguments = listOf(
                        navArgument("owner") { type = NavType.StringType },
                        navArgument("repo") { type = NavType.StringType },
                        navArgument("runId") { type = NavType.LongType }
                    )
                ) { backStackEntry ->
                    val owner = backStackEntry.arguments?.getString("owner") ?: ""
                    val repo = backStackEntry.arguments?.getString("repo") ?: ""
                    val runId = backStackEntry.arguments?.getLong("runId") ?: 0L
                    WorkflowDetailsScreen(
                        owner = owner,
                        repo = repo,
                        runId = runId,
                        onBack = { navController.popBackStack() },
                        onLogs = { jobId ->
                            navController.navigate(Routes.logs(owner, repo, jobId))
                        },
                        onArtifacts = {
                            navController.navigate(Routes.artifacts(owner, repo, runId))
                        }
                    )
                }

                // Logs
                composable(
                    route = Routes.LOGS,
                    arguments = listOf(
                        navArgument("owner") { type = NavType.StringType },
                        navArgument("repo") { type = NavType.StringType },
                        navArgument("jobId") { type = NavType.LongType }
                    )
                ) { backStackEntry ->
                    val owner = backStackEntry.arguments?.getString("owner") ?: ""
                    val repo = backStackEntry.arguments?.getString("repo") ?: ""
                    val jobId = backStackEntry.arguments?.getLong("jobId") ?: 0L
                    LogsScreen(
                        owner = owner,
                        repo = repo,
                        jobId = jobId,
                        onBack = { navController.popBackStack() }
                    )
                }

                // Artifacts
                composable(
                    route = Routes.ARTIFACTS,
                    arguments = listOf(
                        navArgument("owner") { type = NavType.StringType },
                        navArgument("repo") { type = NavType.StringType },
                        navArgument("runId") { type = NavType.LongType }
                    )
                ) { backStackEntry ->
                    val owner = backStackEntry.arguments?.getString("owner") ?: ""
                    val repo = backStackEntry.arguments?.getString("repo") ?: ""
                    val runId = backStackEntry.arguments?.getLong("runId") ?: 0L
                    ArtifactsScreen(
                        owner = owner,
                        repo = repo,
                        runId = runId,
                        onBack = { navController.popBackStack() }
                    )
                }

                // Settings
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onSignOut = {
                            navController.navigate(Routes.AUTH) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                }
            }
        }

        if (showBottomBar) {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = androidx.compose.ui.unit.dp.times(3)
            ) {
                BottomNavItems.items.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = currentRoute == item.route,
                        onClick = {
                            if (currentRoute != item.route) {
                                navController.navigate(item.route) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
