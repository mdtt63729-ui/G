package com.gitofy.feature.home

import com.gitofy.core.designsystem.motion.GITOFYStaggeredVisibility

import android.Manifest
import com.gitofy.core.designsystem.motion.gitofySlideFadeEnter
import com.gitofy.core.designsystem.motion.gitofySlideFadeExit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.gitofy.core.designsystem.motion.gitofyFabShowEnter
import com.gitofy.core.designsystem.motion.gitofyFabHideExit
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.gitofy.core.designsystem.components.*
import com.gitofy.core.designsystem.components.PremiumScreenLoading
import com.gitofy.core.designsystem.theme.LocalSpacing
import com.gitofy.domain.model.RepoSummary

/**
 * Home — the developer dashboard (PRD §3, §5-8).
 *
 * PRD §3: Staggered entry animation (Header → Welcome → Quick Actions → Repo list).
 * PRD §5: Repository skeleton loading with shimmer matching real card dimensions.
 * PRD §7: 'Repos' quick action removed — only Create + Gito remain.
 * PRD §6: Delete immediately removes repository from UI.
 *
 * Hierarchy: Header → Greeting → Quick Actions → Recent Repositories →
 * Repository Cards → Developer Command Center.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToCreate: () -> Unit,
    onNavigateToRepoDetails: (String, String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSearch: () -> Unit = {},
    onNavigateToAIAssistant: () -> Unit = {},
    onNavigateToCommandCenter: () -> Unit = {},
    onNavigateToJobs: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        context.getSharedPreferences("gitofy_first_run", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("notification_permission_requested", true).apply()
    }
    var showTelegramSheet by rememberSaveable {
        mutableStateOf(!context.getSharedPreferences("gitofy_first_run", android.content.Context.MODE_PRIVATE)
            .getBoolean("telegram_prompt_seen", false))
    }
    var telegramReady by remember { mutableStateOf(false) }

    // PRD FIX: derive loading state before any effect that depends on it.
    // This also avoids a first-composition ordering bug where the effect could
    // observe an undeclared state during compilation.
    val showLoading = uiState.isInitialLoading && uiState.repos.isEmpty() && uiState.user == null

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(180)
        telegramReady = true
    }

    LaunchedEffect(showTelegramSheet, telegramReady, showLoading) {
        if (!showTelegramSheet && telegramReady && !showLoading && android.os.Build.VERSION.SDK_INT >= 33) {
            val prefs = context.getSharedPreferences("gitofy_first_run", android.content.Context.MODE_PRIVATE)
            if (!prefs.getBoolean("notification_permission_requested", false)) {
                kotlinx.coroutines.delay(250)
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // PRD §21-22: scroll-aware Create FAB. Slides out (right + bounce) when
    // the user scrolls up toward new content, and slides back in (reverse +
    // bounce) when they scroll back down toward it, or when the list is at
    // rest at the top.
    val homeListState = rememberLazyListState()
    var previousScrollIndex by remember { mutableStateOf(0) }
    var previousScrollOffset by remember { mutableStateOf(0) }
    var isFabVisible by remember { mutableStateOf(true) }
    LaunchedEffect(homeListState) {
        snapshotFlow { homeListState.firstVisibleItemIndex to homeListState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val scrolledDown = index > previousScrollIndex ||
                    (index == previousScrollIndex && offset > previousScrollOffset)
                val scrolledUp = index < previousScrollIndex ||
                    (index == previousScrollIndex && offset < previousScrollOffset)
                val atRest = index == 0 && offset < 8
                when {
                    atRest -> isFabVisible = true
                    scrolledDown -> isFabVisible = false // content moving up -> hide
                    scrolledUp -> isFabVisible = true     // content moving down -> show
                }
                previousScrollIndex = index
                previousScrollOffset = offset
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                // PRD §16: No oversized AppBar — integrated header
                GITOFYTopAppBar(
                    title = "GITOFY",
                    actions = {
                        IconButton(onClick = onNavigateToSearch) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        // PRD FIX: `rotate(if (isRefreshing) 360f else 0f)` had no
                        // animationSpec at all, so the icon jump-cut between 0°
                        // and 360° (visually identical angles) instead of
                        // actually spinning — refresh looked like it did
                        // nothing. A continuously-repeating rotation while
                        // isRefreshing is true gives real spin feedback.
                        val refreshRotation = remember { Animatable(0f) }
                        LaunchedEffect(uiState.isRefreshing) {
                            if (uiState.isRefreshing) {
                                refreshRotation.snapTo(0f)
                                while (true) {
                                    refreshRotation.animateTo(360f, animationSpec = androidx.compose.animation.core.tween(700, easing = LinearEasing))
                                    refreshRotation.snapTo(0f)
                                }
                            } else {
                                refreshRotation.stop()
                                refreshRotation.snapTo(0f)
                            }
                        }
                        IconButton(onClick = { viewModel.refresh() }, enabled = !uiState.isRefreshing) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                modifier = Modifier
                                    .rotate(refreshRotation.value)
                            )
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                )
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = isFabVisible,
                    enter = gitofyFabShowEnter,
                    exit = gitofyFabHideExit
                ) {
                    GITOFYFloatingActionButton(onClick = onNavigateToCreate)
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Offline state banner
                if (uiState.isOffline) {
                    OfflineBanner()
                }

                // PRD §21: Active job indicator — shows when jobs are running
                // in the background, regardless of which screen the user is on.
                if (uiState.activeJobCount > 0) {
                    ActiveJobIndicator(
                        count = uiState.activeJobCount,
                        onClick = onNavigateToJobs
                    )
                }

                LazyColumn(
                    state = homeListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = LocalSpacing.current.lg,
                        end = LocalSpacing.current.lg,
                        top = LocalSpacing.current.lg,
                        bottom = LocalSpacing.current.xxl
                    ),
                    verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.lg)
                ) {
                    // PRD §3: Greeting — subtle fade + slide
                    item {
                        GITOFYStaggeredVisibility(index = 0) {
                            GreetingHeader(userLogin = uiState.user?.login)
                        }
                    }

                    // PRD §7: Quick actions — only Create + Gito (Repos removed)
                    item {
                        GITOFYStaggeredVisibility(index = 1) {
                            QuickActionsRow(
                                onCreate = onNavigateToCreate,
                                onAIAssistant = onNavigateToAIAssistant
                            )
                        }
                    }

                    // PRD §6: 'View all' link removed — repositories are shown
                    // directly on Home. No separate repos navigation from here.
                    item {
                        GITOFYStaggeredVisibility(index = 2) {
                            Text(
                                text = "Recent Repositories",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }

                    // PRD §5: Repository cards / skeleton / empty / error
                    // PRD FIX: previously the skeleton only showed on the very
                    // first load (isInitialLoading, repos empty). Tapping the
                    // refresh button when repos were already loaded set
                    // isRefreshing = true but isInitialLoading stayed false —
                    // so the skeleton branch below was never reached and the
                    // refresh button appeared to do nothing visually except a
                    // static (non-animated) icon flip. Now the skeleton also
                    // shows while a manual refresh is in flight.
                    if ((uiState.isInitialLoading || uiState.isRefreshing) && uiState.repos.isEmpty()) {
                        // PRD §5: Skeleton cards matching real card structure exactly.
                        // Dimensions match RepositoryCard so there's no layout jump on load.
                        items(4) { SkeletonRepositoryCard() }
                    } else if (uiState.isRefreshing && uiState.repos.isNotEmpty()) {
                        // Refreshing with existing data — show skeletons for the
                        // refresh instead of a silent, invisible reload.
                        items(uiState.repos.size.coerceIn(1, 6)) { SkeletonRepositoryCard() }
                    } else if (uiState.repos.isEmpty() && uiState.error == null) {
                        item {
                            EmptyStateView(
                                icon = Icons.Default.Cloud,
                                title = "No repositories yet",
                                subtitle = "Create your first repository from a ZIP project.",
                                actionText = "Create Project",
                                onAction = onNavigateToCreate
                            )
                        }
                    } else if (uiState.error != null && uiState.repos.isEmpty()) {
                        item {
                            ErrorBanner(
                                message = uiState.error!!,
                                onRetry = { viewModel.refresh() }
                            )
                        }
                    } else {
                        items(uiState.repos, key = { it.id }) { repo ->
                            RepositoryCard(
                                repo = repo,
                                onClick = {
                                    onNavigateToRepoDetails(repo.ownerLogin, repo.name)
                                }
                            )
                        }
                    }

                    // Developer Command Center
                    item {
                        Spacer(modifier = Modifier.height(LocalSpacing.current.sm))
                        Text(
                            text = "Command Center",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    item {
                        GITOFYCard(modifier = Modifier.fillMaxWidth(), onClick = onNavigateToCommandCenter) {
                            Row(
                                modifier = Modifier.padding(LocalSpacing.current.lg),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Dashboard,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(LocalSpacing.current.md))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Everything's quiet", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "No attention items, active builds, or pending releases right now.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // PRD: Premium loading overlay — fades out smoothly when content loads
        PremiumScreenLoading(visible = showLoading)

        if (showTelegramSheet && telegramReady && !showLoading) {
            TelegramJoinSheet(
                onContinue = {
                    context.getSharedPreferences("gitofy_first_run", android.content.Context.MODE_PRIVATE)
                        .edit().putBoolean("telegram_prompt_seen", true).apply()
                    showTelegramSheet = false
                    openTelegram(context)
                },
                onDismiss = {
                    context.getSharedPreferences("gitofy_first_run", android.content.Context.MODE_PRIVATE)
                        .edit().putBoolean("telegram_prompt_seen", true).apply()
                    showTelegramSheet = false
                }
            )
        }
    }
}

@Composable
private fun GreetingHeader(userLogin: String?) {
    Column {
        Text(
            text = if (userLogin != null) "Welcome back, $userLogin" else "Welcome back",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "Here's what's happening across your projects.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OfflineBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(LocalSpacing.current.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(LocalSpacing.current.sm))
            Text(
                text = "You're offline. Cached information is available.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/**
 * PRD §21: Active job indicator — small banner shown on Home when
 * repository operations are running in the background.
 * "↻ 1 job running" — tap to navigate to Jobs screen.
 */
@Composable
private fun ActiveJobIndicator(
    count: Int,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "jobIndicator")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "indicatorRotation"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier.padding(LocalSpacing.current.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(rotation)
            )
            Spacer(modifier = Modifier.width(LocalSpacing.current.sm))
            Text(
                text = if (count == 1) "1 job running" else "$count jobs running",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "View",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * PRD §7: Quick actions — Create + Gito only.
 * 'Repos' button has been completely removed (UI + handler + nav logic).
 */
@Composable
private fun QuickActionsRow(
    onCreate: () -> Unit,
    onAIAssistant: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.md)
    ) {
        QuickActionCard(
            modifier = Modifier.weight(1f),
            iconAsset = com.gitofy.core.designsystem.components.GitoIconAsset.Vector(Icons.Default.Add),
            label = "Create",
            onClick = onCreate
        )
        QuickActionCard(
            modifier = Modifier.weight(1f),
            // PRD §20: the Gito icon now goes through the configurable asset
            // architecture — swap this single line for GitoIconAsset.Drawable(R.drawable.ic_gito)
            // or GitoIconAsset.Bitmap(...) to use a custom/bundled icon without
            // touching any layout code.
            iconAsset = com.gitofy.core.designsystem.components.GitoIconAsset.Vector(Icons.Default.AutoAwesome),
            label = "Gito",
            onClick = onAIAssistant
        )
    }
}

@Composable
private fun QuickActionCard(
    modifier: Modifier = Modifier,
    iconAsset: com.gitofy.core.designsystem.components.GitoIconAsset,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    GITOFYCard(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = LocalSpacing.current.lg, horizontal = LocalSpacing.current.sm),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // PRD §75: Icon in CIRCULAR purple container (reference screenshot)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                com.gitofy.core.designsystem.components.GitoConfigurableIcon(
                    asset = iconAsset,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.height(LocalSpacing.current.sm))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun RepositoryCard(
    repo: RepoSummary,
    onClick: () -> Unit
) {
    // PRD §18: Repository card with avatar, name, owner/repo, Public pill, three-dot overflow
    GITOFYCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(LocalSpacing.current.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Repository avatar — reference shows circular icon container
            AsyncImage(
                model = repo.ownerAvatar.ifBlank {
                    "https://github.com/${repo.ownerLogin}.png?size=200"
                },
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
            )
            Spacer(modifier = Modifier.width(LocalSpacing.current.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = repo.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = repo.fullName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(LocalSpacing.current.sm))
            // PRD §18: Public/Private pill
            StatusBadge(
                text = if (repo.isPrivate) "Private" else "Public",
                statusType = if (repo.isPrivate) StatusType.Neutral else StatusType.Info
            )
            Spacer(modifier = Modifier.width(LocalSpacing.current.xs))
            // PRD §18, §73: Three-dot overflow menu
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "More options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * PRD §5: Skeleton card that exactly mirrors the real RepositoryCard structure.
 * Same avatar size (40dp), same padding, same text line heights — so there's
 * zero layout jump when skeletons are replaced by real cards.
 */
@Composable
fun SkeletonRepositoryCard() {
    GITOFYCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(LocalSpacing.current.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar placeholder — exact 40dp circle
            SkeletonAvatar(diameter = 40.dp)
            Spacer(modifier = Modifier.width(LocalSpacing.current.md))
            Column(modifier = Modifier.weight(1f)) {
                // Repo name placeholder
                SkeletonText(width = 140.dp, height = 14.dp)
                Spacer(modifier = Modifier.height(4.dp))
                // Full name placeholder
                SkeletonText(width = 100.dp, height = 12.dp)
            }
            Spacer(modifier = Modifier.width(LocalSpacing.current.sm))
            // Visibility badge placeholder
            SkeletonText(width = 48.dp, height = 14.dp)
            Spacer(modifier = Modifier.width(LocalSpacing.current.xs))
            // Menu placeholder
            SkeletonText(width = 20.dp, height = 20.dp)
        }
    }
}
