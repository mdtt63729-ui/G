package com.gitofy.core.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gitofy.core.designsystem.motion.gitofyPressScale
import com.gitofy.core.designsystem.theme.LocalSpacing
import com.gitofy.core.designsystem.tokens.Dimensions
import com.gitofy.core.designsystem.tokens.Elevation
import com.gitofy.core.designsystem.tokens.MotionTokens

/**
 * GITOFY Button types.
 *
 * [Tonal] and [Destructive] are additive variants (PRD §11) layered onto the
 * original Primary/Outlined/Text set — existing call sites keep compiling
 * unchanged.
 */
enum class GITOFYButtonType { Primary, Outlined, Text, Tonal, Destructive }

/**
 * Premium Material 3 button with consistent styling.
 *
 * Guarantees a 48dp minimum touch target (PRD §29) and cross-fades between
 * its label/icon content and the loading indicator instead of abruptly
 * swapping them (PRD §11).
 */
@Composable
fun GITOFYButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    type: GITOFYButtonType = GITOFYButtonType.Primary,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    loading: Boolean = false,
    fullWidth: Boolean = false
) {
    val finalModifier = (if (fullWidth) modifier.fillMaxWidth() else modifier)
        .heightIn(min = Dimensions.buttonHeight)
    val showLoading = loading

    val content: @Composable RowScope.(Color) -> Unit = { indicatorColor ->
        AnimatedContentSwap(
            showLoading = showLoading,
            indicatorColor = indicatorColor
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(Dimensions.iconMedium))
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }

    when (type) {
        GITOFYButtonType.Primary -> {
            Button(
                onClick = onClick,
                modifier = finalModifier,
                enabled = enabled && !showLoading,
                shape = MaterialTheme.shapes.medium,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                content(MaterialTheme.colorScheme.onPrimary)
            }
        }

        GITOFYButtonType.Tonal -> {
            FilledTonalButton(
                onClick = onClick,
                modifier = finalModifier,
                enabled = enabled && !showLoading,
                shape = MaterialTheme.shapes.medium,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                content(MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }

        GITOFYButtonType.Destructive -> {
            Button(
                onClick = onClick,
                modifier = finalModifier,
                enabled = enabled && !showLoading,
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                content(MaterialTheme.colorScheme.onError)
            }
        }

        GITOFYButtonType.Outlined -> {
            OutlinedButton(
                onClick = onClick,
                modifier = finalModifier,
                enabled = enabled && !showLoading,
                shape = MaterialTheme.shapes.medium,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                content(MaterialTheme.colorScheme.primary)
            }
        }

        GITOFYButtonType.Text -> {
            TextButton(
                onClick = onClick,
                modifier = finalModifier,
                enabled = enabled && !showLoading,
                shape = MaterialTheme.shapes.medium
            ) {
                content(MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/**
 * Cross-fades between a button's normal content and a loading spinner so the
 * transition reads as smooth rather than an abrupt content swap (PRD §11).
 */
@Composable
private fun RowScope.AnimatedContentSwap(
    showLoading: Boolean,
    indicatorColor: Color,
    normalContent: @Composable RowScope.() -> Unit
) {
    Box(contentAlignment = Alignment.Center) {
        androidx.compose.animation.AnimatedVisibility(
            visible = !showLoading,
            enter = fadeIn(tween(MotionTokens.DurationFast)),
            exit = fadeOut(tween(MotionTokens.DurationFast))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) { normalContent() }
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = showLoading,
            enter = fadeIn(tween(MotionTokens.DurationFast)) + scaleIn(
                tween(MotionTokens.DurationFast),
                initialScale = 0.8f
            ),
            exit = fadeOut(tween(MotionTokens.DurationFast))
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = indicatorColor
            )
        }
    }
}

/**
 * Filled tonal button for secondary actions.
 */
@Composable
fun GITOFYTonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = Dimensions.compactButtonHeight),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Floating action button — reference screenshot: pill-shaped with light purple bg + dark purple icon+text.
 */
@Composable
fun GITOFYFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Add,
    contentDescription: String = "Add"
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.primary
    ) {
        Icon(icon, contentDescription = contentDescription)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Create", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
    }
}

/**
 * GITOFY top app bar.
 *
 * Supports the PRD §13 upgrades needed for most screens: a standard bar,
 * an optional center-aligned title, and an optional [scrollBehavior] so
 * callers can wire it into a collapsing/pinned scroll container. Existing
 * call sites (title + onBack + actions only) are unaffected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GITOFYTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    centerAligned: Boolean = false,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val navigationIcon: @Composable () -> Unit = {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        }
    }
    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
        actionIconContentColor = MaterialTheme.colorScheme.onSurface
    )

    if (centerAligned) {
        CenterAlignedTopAppBar(
            title = { Text(title, style = MaterialTheme.typography.titleLarge) },
            modifier = modifier,
            navigationIcon = navigationIcon,
            actions = actions,
            colors = colors,
            scrollBehavior = scrollBehavior
        )
    } else {
        TopAppBar(
            title = { Text(title, style = MaterialTheme.typography.titleLarge) },
            modifier = modifier,
            navigationIcon = navigationIcon,
            actions = actions,
            colors = colors,
            scrollBehavior = scrollBehavior
        )
    }
}

/**
 * Collapsing branded header for information-dense top-level screens
 * (PRD §13 — "Home: Large/standard branded header depending on available
 * width"). Pair with `TopAppBarDefaults.enterAlwaysScrollBehavior()` (or
 * similar) and a `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)`
 * on the scrolling content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GITOFYLargeTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    LargeTopAppBar(
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        modifier = modifier,
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.largeTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        ),
        scrollBehavior = scrollBehavior
    )
}

/**
 * Dedicated search interaction (PRD §13 — search gets its own affordance
 * rather than being forced into a generic toolbar). A focused text field
 * with back/clear actions, sized and colored like the rest of the top app
 * bar system so it slots into the same [Scaffold] `topBar` slot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GITOFYSearchTopAppBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    onClear: () -> Unit = { onQueryChange("") }
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimensions.topAppBarHeight),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = Elevation.level0
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LocalSpacing.current.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                }
            }
        }
    }
}

/**
 * Status badge for workflow/run status.
 */
@Composable
fun StatusBadge(
    text: String,
    statusType: StatusType,
    modifier: Modifier = Modifier
) {
    val colors = when (statusType) {
        StatusType.Success -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        StatusType.Error -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        StatusType.Warning -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        StatusType.Info -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        StatusType.Neutral -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = colors.first
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = colors.second,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

enum class StatusType { Success, Error, Warning, Info, Neutral }

/**
 * Empty state composable.
 */
@Composable
fun EmptyStateView(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(LocalSpacing.current.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(LocalSpacing.current.lg))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(LocalSpacing.current.sm))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionText != null && onAction != null) {
            Spacer(modifier = Modifier.height(LocalSpacing.current.xl))
            GITOFYButton(
                text = actionText,
                onClick = onAction,
                type = GITOFYButtonType.Outlined
            )
        }
    }
}

/**
 * GITOFY card surface variants (PRD §12).
 *
 * [Filled] matches the original GITOFYCard look (tonal surfaceVariant, flat)
 * so existing call sites are visually unchanged by default.
 */
enum class CardVariant { Filled, Elevated, Outlined, Interactive, Selectable }

/**
 * Standardized surface abstraction used across the app. Defaults to the
 * original Filled, flat appearance — passing [onClick] alone (as most
 * existing call sites do) now also gets a subtle press-scale + ripple
 * response (PRD §12, §28) without any call-site changes.
 */
@Composable
fun GITOFYCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    variant: CardVariant = if (onClick != null) CardVariant.Interactive else CardVariant.Filled,
    selected: Boolean = false,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    val shape = MaterialTheme.shapes.large

    val colors = when (variant) {
        CardVariant.Filled, CardVariant.Interactive -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
        CardVariant.Elevated -> CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
        CardVariant.Outlined -> CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
        CardVariant.Selectable -> CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        )
    }

    val elevation = when (variant) {
        CardVariant.Elevated -> CardDefaults.cardElevation(defaultElevation = Elevation.level1)
        else -> CardDefaults.cardElevation(defaultElevation = Elevation.level0)
    }

    val border: BorderStroke? = if (variant == CardVariant.Outlined) {
        BorderStroke(Dimensions.borderThin, MaterialTheme.colorScheme.outlineVariant)
    } else if (variant == CardVariant.Selectable && selected) {
        BorderStroke(Dimensions.borderMedium, MaterialTheme.colorScheme.primary)
    } else {
        null
    }

    val pressModifier = if (onClick != null) {
        modifier.gitofyPressScale(interactionSource)
    } else {
        modifier
    }

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = pressModifier,
            enabled = enabled,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
            interactionSource = interactionSource,
            content = content
        )
    } else {
        Card(
            modifier = modifier,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
            content = content
        )
    }
}

/**
 * Info row for details screens.
 */
@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = LocalSpacing.current.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(LocalSpacing.current.md))
        }
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}


@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun ErrorView(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        if (onRetry != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}
