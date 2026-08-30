package com.gitofy.core.navigation

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gitofy.core.designsystem.components.AdaptiveNavItem
import com.gitofy.core.designsystem.components.AdaptiveNavigationScaffold
import com.gitofy.core.designsystem.motion.gitofyBackEnter
import com.gitofy.core.designsystem.motion.gitofyBackExit
import com.gitofy.core.designsystem.motion.gitofyForwardEnter
import com.gitofy.core.designsystem.motion.gitofyForwardExit
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
import com.gitofy.feature.ai.GitoAiScreen
import com.gitofy.feature.agent.AgentScreen
import com.gitofy.feature.search.GlobalSearchScreen
import com.gitofy.feature.intelligence.CommandCenterScreen
import com.gitofy.feature.health.RepositoryHealthScreen
import com.gitofy.feature.repositories.update.UpdateRepositoryScreen
import com.gitofy.feature.jobs.JobsScreen
import com.gitofy.feature.ai.GitoRepairScreen

/**
 * PRD §4: Repository Navigation Fix.
 * PRD §14 / §23: Adaptive navigation — Home, Repositories, GITO AI, Settings
 * render behind a [AdaptiveNavigationScaffold] that swaps between a bottom
 * bar (compact width), a rail (medium width) and a permanent drawer
 * (expanded width). Every other destination (splash, auth, detail screens)
 * renders full-bleed with no navigation chrome.
 *
 * Navigation uses launchSingleTop, popUpTo, saveState, restoreState
 * to prevent duplicate destinations and preserve state.
 */
@Composable
fun GITOFYNavHost(
    navController: androidx.navigation.NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showNavigationChrome = currentRoute in listOf(
        Routes.HOME,
        Routes.INBOX
    )

    val navigationItems = BottomNavItems.items.map { item ->
        AdaptiveNavItem(route = item.route, label = item.label, icon = item.icon)
    }

    val navHost: @Composable () -> Unit = {
        NavHost(
            navController = navController,
            startDestination = Routes.SPLASH,
            enterTransition = { gitofyForwardEnter },
            exitTransition = { gitofyForwardExit },
            popEnterTransition = { gitofyBackEnter },
            popExitTransition = { gitofyBackExit }
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
                            // PAT validation is complete; go straight to Home.
                            // The old "Connecting with GitHub" hand-off screen was
                            // redundant and added an artificial launch delay.
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.AUTH) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    )
                }

                // Home
                composable(Routes.HOME) {
                    HomeScreen(
                        onNavigateToCreate = { navController.navigate(Routes.CREATE_PROJECT) },
                        onNavigateToRepoDetails = { owner, repo ->
                            navController.navigate(Routes.repositoryDetails(owner, repo))
                        },
                        onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                        onNavigateToSearch = { navController.navigate(Routes.SEARCH) },
                        onNavigateToAIAssistant = { navController.navigate(Routes.GITO_AI) },
                        onNavigateToCommandCenter = { navController.navigate(Routes.COMMAND_CENTER) },
                        onNavigateToJobs = { navController.navigate(Routes.JOBS) }
                    )
                }

                // Inbox — PRD §12/§14: Real GitHub notification inbox
                composable(Routes.INBOX) {
                    com.gitofy.feature.inbox.InboxScreen()
                }

                // Global Search — dedicated M3 interaction (Phase 3 §5)
                composable(Routes.SEARCH) {
                    GlobalSearchScreen(
                        onBack = { navController.popBackStack() },
                        onRepoClick = { owner, repo ->
                            navController.navigate(Routes.repositoryDetails(owner, repo))
                        }
                    )
                }

                // Developer Command Center (Phase 3 §4)
                composable(Routes.COMMAND_CENTER) {
                    CommandCenterScreen(
                        onBack = { navController.popBackStack() },
                        onAIAssistantClick = { navController.navigate(Routes.GITO_AI) }
                    )
                }

                // PRD §16: Jobs — real-time execution monitor
                composable(Routes.JOBS) {
                    JobsScreen(
                        onBack = { navController.popBackStack() }
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

                // GITO AI — PRD §24
                composable(Routes.GITO_AI) {
                    GitoAiScreen(
                        onBack = { navController.popBackStack() },
                        onOpenProviderSettings = { navController.navigate(Routes.SETTINGS) }
                    )
                }

                // Agent — Autonomous GitHub Coding Agent (PRD §1-68)
                composable(Routes.AGENT) {
                    AgentScreen(
                        onBack = { navController.popBackStack() },
                        onOpenProviderSettings = { navController.navigate(Routes.SETTINGS) }
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
                        onWorkflows = { navController.navigate(Routes.workflows(owner, repo)) },
                        onHealth = { navController.navigate(Routes.repositoryHealth(owner, repo)) },
                        onUpdateRepository = {
                            navController.navigate(Routes.updateRepository(owner, repo))
                        },
                        onDeleted = {
                            // Pop back to the previous screen (Home / list) after a successful delete.
                            navController.popBackStack()
                        }
                    )
                }

                // Repository Health (Phase 3 §3 — bottom of the details hierarchy)
                composable(
                    route = Routes.REPOSITORY_HEALTH,
                    arguments = listOf(
                        navArgument("owner") { type = NavType.StringType },
                        navArgument("repo") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val owner = backStackEntry.arguments?.getString("owner") ?: ""
                    val repo = backStackEntry.arguments?.getString("repo") ?: ""
                    RepositoryHealthScreen(
                        owner = owner,
                        repo = repo,
                        onBack = { navController.popBackStack() }
                    )
                }

                // PRD §7-9: Update Repository — ZIP file picker + sync flow
                composable(
                    route = Routes.UPDATE_REPOSITORY,
                    arguments = listOf(
                        navArgument("owner") { type = NavType.StringType },
                        navArgument("repo") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val owner = backStackEntry.arguments?.getString("owner") ?: ""
                    val repo = backStackEntry.arguments?.getString("repo") ?: ""
                    UpdateRepositoryScreen(
                        owner = owner,
                        repo = repo,
                        onBack = { navController.popBackStack() },
                        onComplete = {
                            navController.popBackStack()
                        }
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
                                popUpTo(Routes.HOME) { saveState = true }
                                launchSingleTop = true
                            }
                        },
                        onEdit = { navController.popBackStack() }
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
                        },
                        onGitoAiRepair = { route ->
                            navController.navigate(route)
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

                // Gito AI Repair — PRD §11/§40: Auto-repair flow from failed job
                composable(
                    route = Routes.GITO_REPAIR,
                    arguments = listOf(
                        navArgument("owner") { type = NavType.StringType },
                        navArgument("repo") { type = NavType.StringType },
                        navArgument("runId") { type = NavType.LongType },
                        navArgument("jobId") { type = NavType.LongType },
                        navArgument("workflowId") { type = NavType.StringType },
                        navArgument("branch") { type = NavType.StringType },
                        navArgument("commitSha") { type = NavType.StringType },
                        navArgument("failedJobName") { type = NavType.StringType },
                        navArgument("failedStepName") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val args = backStackEntry.arguments!!
                    GitoRepairScreen(
                        owner = args.getString("owner") ?: "",
                        repo = args.getString("repo") ?: "",
                        runId = args.getLong("runId"),
                        jobId = args.getLong("jobId"),
                        workflowId = args.getString("workflowId") ?: "",
                        branch = args.getString("branch") ?: "main",
                        commitSha = args.getString("commitSha") ?: "",
                        failedJobName = args.getString("failedJobName") ?: "",
                        failedStepName = args.getString("failedStepName") ?: "",
                        onBack = { navController.popBackStack() },
                        onViewLogs = { logJobId ->
                            navController.navigate(Routes.logs(args.getString("owner") ?: "", args.getString("repo") ?: "", logJobId))
                        }
                    )
                }

                // Settings
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onSignOut = {
                            navController.navigate(Routes.AUTH) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onNavigateToCategory = { route ->
                            navController.navigate(route)
                        }
                    )
                }

                // PRD §2 — Settings category pages
                composable(Routes.SETTINGS_APPEARANCE) {
                    com.gitofy.feature.settings.appearance.AppearanceSettingsScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Routes.SETTINGS_API_PROVIDERS) {
                    com.gitofy.feature.settings.apiproviders.ApiProvidersScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Routes.SETTINGS_MODELS) {
                    com.gitofy.feature.settings.models.ModelsSettingsScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Routes.SETTINGS_AGENT) {
                    com.gitofy.feature.settings.agent.AgentSettingsScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Routes.SETTINGS_EDITOR) {
                    com.gitofy.feature.settings.editor.EditorSettingsScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Routes.SETTINGS_WORKSPACE) {
                    com.gitofy.feature.settings.workspace.WorkspaceSettingsScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Routes.SETTINGS_GITHUB) {
                    com.gitofy.feature.settings.github.GitGitHubSettingsScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Routes.SETTINGS_BUILD) {
                    com.gitofy.feature.settings.build.BuildRunSettingsScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Routes.SETTINGS_NOTIFICATIONS) {
                    com.gitofy.feature.settings.notifications.NotificationsSettingsScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Routes.SETTINGS_PRIVACY) {
                    com.gitofy.feature.settings.privacy.PrivacySecuritySettingsScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Routes.SETTINGS_ADVANCED) {
                    com.gitofy.feature.settings.advanced.AdvancedSettingsScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Routes.SETTINGS_ABOUT) {
                    com.gitofy.feature.settings.about.AboutSettingsScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
        }
    }

    // PRD FIX: AdaptiveNavigationScaffold (and therefore navHost) is now
    // ALWAYS used — never swapped for a bare Box — so the NavHost stays at a
    // stable composition position across every navigation, including into
    // chrome-less routes like Settings and Create Project. showChrome only
    // toggles the nav bar/rail/drawer's visibility. This is what actually
    // fixes "Settings/Create open with no animation" and the general
    // lag/animation-reset feeling when returning to Home.
    // Normal back navigation works inside the app. At the root, require a
    // second back press within two seconds before finishing the Activity.
    var lastRootBackAt by remember { mutableLongStateOf(0L) }
    val activity = androidx.compose.ui.platform.LocalContext.current as? Activity
    BackHandler {
        val previous = navController.previousBackStackEntry
        if (previous != null) {
            navController.popBackStack()
        } else {
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastRootBackAt <= 2000L) {
                activity?.finish()
            } else {
                lastRootBackAt = now
                Toast.makeText(activity, "Press back again to exit", Toast.LENGTH_SHORT).show()
            }
        }
    }

    AdaptiveNavigationScaffold(
        navigationItems = navigationItems,
        currentRoute = currentRoute,
        showChrome = showNavigationChrome,
        onNavigate = { item ->
            // PRD §4: Same destination tap → no unnecessary navigation
            if (currentRoute != item.route) {
                navController.navigate(item.route) {
                    // Pop up to the start destination to avoid building a large stack
                    popUpTo(Routes.HOME) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        },
        content = navHost
    )
}

