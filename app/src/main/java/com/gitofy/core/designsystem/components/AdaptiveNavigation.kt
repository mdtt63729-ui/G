package com.gitofy.core.designsystem.components

import com.gitofy.core.designsystem.motion.gitofySlideFadeEnter
import com.gitofy.core.designsystem.motion.gitofySlideFadeExit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import com.gitofy.core.designsystem.theme.LocalSpacing
import com.gitofy.core.designsystem.tokens.Dimensions

/**
 * Window-width buckets used to drive navigation shape (PRD §14, §30).
 *
 * Deliberately implemented against [LocalConfiguration] rather than the
 * `material3-window-size-class` artifact so no new dependency is required.
 */
enum class GitofyWindowSizeClass { Compact, Medium, Expanded }

@Composable
fun rememberGitofyWindowSizeClass(): GitofyWindowSizeClass {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    return remember(screenWidthDp) {
        when {
            screenWidthDp < Dimensions.compactMaxWidth -> GitofyWindowSizeClass.Compact
            screenWidthDp < Dimensions.mediumMaxWidth -> GitofyWindowSizeClass.Medium
            else -> GitofyWindowSizeClass.Expanded
        }
    }
}

/**
 * Adaptive navigation shell (PRD §14): a bottom [NavigationBar] on compact
 * width, a [NavigationRail] on medium width, and a permanent navigation
 * drawer on expanded width.
 *
 * PRD §4: Home ↔ Inbox transition smooth — the selected indicator pill
 * physically slides between items using an animated offset rather than
 * instantly appearing on the new item.
 */
@Composable
fun AdaptiveNavigationScaffold(
    navigationItems: List<AdaptiveNavItem>,
    currentRoute: String?,
    onNavigate: (AdaptiveNavItem) -> Unit,
    modifier: Modifier = Modifier,
    // PRD FIX: `content` (the NavHost) must stay mounted at a single, stable
    // position in the composition tree at all times. Previously the caller
    // decided whether to wrap `content` in this scaffold at all — swapping
    // between "wrapped" and "bare Box" the moment a chrome-less route (e.g.
    // Settings, Create Project) was opened. That swap destroyed the NavHost's
    // AnimatedContent transition state mid-navigation, so the destination
    // screen popped in with no enter animation. Now this scaffold is ALWAYS
    // used, and `showChrome` only toggles the visibility of the surrounding
    // nav bar/rail/drawer via AnimatedVisibility — `content` never moves.
    showChrome: Boolean = true,
    windowSizeClass: GitofyWindowSizeClass = rememberGitofyWindowSizeClass(),
    content: @Composable () -> Unit
) {
    // FIX: this scaffold no longer paints its own opaque background behind
    // the nav bar/rail/drawer. The caller (GITOFYNavHost) now sits inside a
    // single themed Surface that already covers the whole screen, so the
    // area around the floating pill/rail/drawer is genuinely transparent —
    // it shows that real themed background directly, rather than a second,
    // possibly-mismatched opaque rectangle painted here. The pill/rail/
    // drawer itself keeps its own explicit container color below, so it
    // still reads as a distinct floating surface.
    when (windowSizeClass) {
        GitofyWindowSizeClass.Compact -> {
            Column(modifier = modifier.fillMaxSize()) {
                // content() is always the first, stable child of this Column —
                // its slot position never changes regardless of showChrome.
                Box(modifier = Modifier.weight(1f)) { content() }
                // PRD §13/§4: Bottom navigation with sliding pill indicator.
                // AnimatedVisibility keeps this composable mounted (animating
                // size/alpha) instead of abruptly adding/removing it from the
                // tree, so toggling chrome never resets sibling animation state.
                // Navigation chrome is route-owned. When a full-screen route
                // opens, remove the app navigation immediately so it can never
                // remain as a ghost/faded bar underneath Settings, Search,
                // details, editors, or other chrome-less screens.
                if (showChrome) {
                    SlidingIndicatorNavigationBar(
                        navigationItems = navigationItems,
                        currentRoute = currentRoute,
                        onNavigate = onNavigate
                    )
                }
            }
        }

        GitofyWindowSizeClass.Medium -> {
            Row(modifier = modifier.fillMaxSize()) {
                if (showChrome) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Spacer(modifier = Modifier.height(LocalSpacing.current.xl))
                        navigationItems.forEach { item ->
                            NavigationRailItem(
                                selected = currentRoute == item.route,
                                onClick = { onNavigate(item) },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) }
                            )
                        }
                    }
                }
                Box(modifier = Modifier.weight(1f)) { content() }
            }
        }

        GitofyWindowSizeClass.Expanded -> {
            // PermanentNavigationDrawer always wraps content — only the
            // drawer's width animates to 0 when chrome is hidden, so content
            // never leaves this stable position either.
            val drawerWidth by animateDpAsState(
                targetValue = if (showChrome) Dimensions.navigationDrawerWidth else 0.dp,
                animationSpec = tween(200),
                label = "drawerWidth"
            )
            PermanentNavigationDrawer(
                modifier = modifier,
                drawerContent = {
                    if (drawerWidth > 0.dp) {
                        PermanentDrawerSheet(modifier = Modifier.width(drawerWidth)) {
                            Spacer(modifier = Modifier.height(LocalSpacing.current.xl))
                            navigationItems.forEach { item ->
                                NavigationDrawerItem(
                                    selected = currentRoute == item.route,
                                    onClick = { onNavigate(item) },
                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                    label = { Text(item.label) },
                                    colors = NavigationDrawerItemDefaults.colors(),
                                    modifier = Modifier.padding(
                                        horizontal = LocalSpacing.current.md,
                                        vertical = LocalSpacing.current.xs
                                    )
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.width(0.dp))
                    }
                }
            ) {
                content()
            }
        }
    }
}

/**
 * PRD §4: Bottom navigation bar with a sliding pill indicator.
 *
 * Instead of the default M3 NavigationBar (which instantly snaps the
 * indicator to the selected item), this custom bar animates the indicator
 * pill's horizontal position with a spring so it physically slides between
 * Home and Inbox. This makes the Home ↔ Inbox transition feel smooth and
 * premium.
 */
@Composable
private fun SlidingIndicatorNavigationBar(
    navigationItems: List<AdaptiveNavItem>,
    currentRoute: String?,
    onNavigate: (AdaptiveNavItem) -> Unit
) {
    if (navigationItems.isEmpty()) return

    val selectedIndex = navigationItems.indexOfFirst { it.route == currentRoute }
        .coerceAtLeast(0)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .height(64.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        // Keep every destination in a fixed-width slot. The selected pill
        // moves independently, so changing the selected label can never
        // change the position of another item.
        val slotWidth = maxWidth / navigationItems.size
        val activeWidth = (slotWidth - 12.dp).coerceIn(88.dp, slotWidth)
        // FIX: button-to-button motion made more premium. The old spring
        // (dampingRatio 0.9, stiffness 520) was fast but slightly mechanical
        // — it arrived almost instantly with barely any settle. A lower
        // damping ratio with moderate stiffness gives the pill a touch of
        // natural overshoot-and-settle as it glides to the new tab, which
        // reads as smoother and more tactile rather than a hard snap.
        val activeOffset by animateDpAsState(
            targetValue = slotWidth * selectedIndex + (slotWidth - activeWidth) / 2,
            animationSpec = spring(
                dampingRatio = 0.68f,
                stiffness = 340f
            ),
            label = "active-capsule-offset"
        )

        // Selected indicator sits behind the content.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset(activeOffset.roundToPx(), 0) }
                .width(activeWidth)
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
        )

        Row(modifier = Modifier.fillMaxSize()) {
            navigationItems.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                val interactionSource = remember { MutableInteractionSource() }
                val pressed by interactionSource.collectIsPressedAsState()

                // FIX: a touch of bouncy overshoot on selection (instead of
                // a plain no-bounce ease) plus a gentle press-down scale
                // makes tapping between tabs feel tactile and premium
                // rather than purely mechanical.
                val iconScale by animateFloatAsState(
                    targetValue = when {
                        pressed -> 0.88f
                        selected -> 1.12f
                        else -> 1f
                    },
                    animationSpec = spring(
                        dampingRatio = if (selected) 0.5f else Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "navigation-icon-scale-${item.route}"
                )
                val contentSpacing by animateDpAsState(
                    targetValue = if (selected) 7.dp else 0.dp,
                    animationSpec = spring(
                        dampingRatio = 0.75f,
                        stiffness = 380f
                    ),
                    label = "navigation-content-spacing-${item.route}"
                )

                Box(
                    modifier = Modifier
                        .width(slotWidth)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onNavigate(item) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // The selected icon + label are laid out as one compact
                    // horizontal unit inside the pill. This fixes the old
                    // layout where the label was independently centered and
                    // visually dropped underneath/over the icon.
                    Row(
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = iconScale
                                scaleY = iconScale
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = if (selected) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )

                        // The label is still part of the fixed slot, but only
                        // occupies visual space when selected. Using a Spacer
                        // rather than an offset keeps icon/text alignment stable.
                        if (selected) {
                            Spacer(modifier = Modifier.width(contentSpacing))
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                modifier = Modifier.graphicsLayer {
                                    alpha = 1f
                                    scaleX = 0.96f + (0.04f * iconScale)
                                    scaleY = 0.96f + (0.04f * iconScale)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

data class AdaptiveNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)
