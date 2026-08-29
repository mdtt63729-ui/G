package com.gitofy.core.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
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
                AnimatedVisibility(
                    visible = showChrome,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(150))
                ) {
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
                AnimatedVisibility(
                    visible = showChrome,
                    enter = expandHorizontally(tween(200)) + fadeIn(tween(200)),
                    exit = shrinkHorizontally(tween(150)) + fadeOut(tween(150))
                ) {
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
    val itemCount = navigationItems.size
    val selectedIndex = navigationItems.indexOfFirst { it.route == currentRoute }
        .coerceAtLeast(0)

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
        ) {
            // PRD §4: Sliding pill indicator that physically slides between items
            if (itemCount > 0) {
                SlidingPill(
                    selectedIndex = selectedIndex,
                    itemCount = itemCount,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Navigation items on top
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                navigationItems.forEachIndexed { index, item ->
                    val isSelected = index == selectedIndex
                    // PRD FIX: this Column previously had no click handling at
                    // all on compact/phone width — only the Medium (rail) and
                    // Expanded (drawer) variants below wired onNavigate, so
                    // tapping "Inbox" (or any bottom-bar item) on a phone did
                    // nothing. indication = null since SlidingPill already
                    // provides the visual selected-state feedback.
                    val interactionSource = remember { MutableInteractionSource() }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = interactionSource,
                                indication = ripple(bounded = false, radius = 48.dp),
                                onClick = { onNavigate(item) }
                            ),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            item.icon,
                            contentDescription = item.label,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .graphicsLayer {
                                    val s = if (isSelected) 1.1f else 1.0f
                                    scaleX = s
                                    scaleY = s
                                }
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            item.label,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

/**
 * The sliding pill indicator. It occupies the full bar area and positions
 * itself based on the selected index using a fractional layout modifier.
 */
@Composable
private fun SlidingPill(
    selectedIndex: Int,
    itemCount: Int,
    modifier: Modifier = Modifier
) {
    if (itemCount <= 0) return
    val slotFraction = 1f / itemCount
    val pillFraction = slotFraction * 0.6f // pill is 60% of slot width

    // Animate the position with a spring for smooth sliding
    val animatedFraction by animateFloatAsState(
        targetValue = selectedIndex * slotFraction + (slotFraction - pillFraction) / 2f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "slidingPillPosition"
    )

    Box(
        modifier = modifier
            .layout { measurable, constraints ->
                val width = constraints.maxWidth
                val pillWidth = (width * pillFraction).toInt()
                val offsetX = (width * animatedFraction).toInt()
                val placeable = measurable.measure(
                    constraints.copy(
                        maxWidth = pillWidth,
                        minWidth = pillWidth
                    )
                )
                layout(width, constraints.maxHeight) {
                    val centerY = (constraints.maxHeight - placeable.height) / 2
                    placeable.place(offsetX, centerY)
                }
            }
            .padding(vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
        )
    }
}

data class AdaptiveNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)
