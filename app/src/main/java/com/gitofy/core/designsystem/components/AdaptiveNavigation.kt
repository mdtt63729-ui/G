package com.gitofy.core.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold as AdaptiveNavSuite
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Adaptive Responsive Layout — PRD Addendum.
 * Layout adapts dynamically across mobile, foldable, and tablet screens.
 * Uses NavigationSuiteScaffold for responsive navigation:
 * - Mobile: Bottom NavigationBar
 * - Foldable/Tablet: NavigationRail
 * - Large tablet/desktop: Permanent drawer
 */
@Composable
fun AdaptiveNavigationScaffold(
    navigationItems: List<AdaptiveNavItem>,
    currentRoute: String,
    onNavigate: (String) -> Unit,
    topBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    AdaptiveNavSuite(
        navigationSuiteItems = {
            navigationItems.forEach { item ->
                item(
                    selected = currentRoute == item.route,
                    onClick = { onNavigate(item.route) },
                    icon = { Icon(item.icon, contentDescription = item.label) },
                    label = { Text(item.label) }
                )
            }
        }
    ) {
        Scaffold(
            topBar = topBar,
            floatingActionButton = floatingActionButton
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                content()
            }
        }
    }
}

data class AdaptiveNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)
