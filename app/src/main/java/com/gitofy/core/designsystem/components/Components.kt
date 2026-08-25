package com.gitofy.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gitofy.core.designsystem.theme.LocalSpacing

/**
 * GITOFY Button types.
 */
enum class GITOFYButtonType { Primary, Outlined, Text }

/**
 * Premium Material 3 button with consistent styling.
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
    val finalModifier = if (fullWidth) modifier.fillMaxWidth() else modifier
    val showLoading = loading

    when (type) {
        GITOFYButtonType.Primary -> {
            Button(
                onClick = onClick,
                modifier = finalModifier,
                enabled = enabled && !showLoading,
                shape = MaterialTheme.shapes.medium,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                if (showLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    if (icon != null) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(text, style = MaterialTheme.typography.labelLarge)
                }
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
                if (showLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    if (icon != null) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(text, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        GITOFYButtonType.Text -> {
            TextButton(
                onClick = onClick,
                modifier = finalModifier,
                enabled = enabled && !showLoading,
                shape = MaterialTheme.shapes.medium
            ) {
                if (showLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    if (icon != null) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(text, style = MaterialTheme.typography.labelLarge)
                }
            }
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
        modifier = modifier,
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
 * Floating action button.
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
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Icon(icon, contentDescription = contentDescription)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Create", style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * GITOFY top app bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GITOFYTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
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
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
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
 * Card wrapper for consistent surface styling.
 */
@Composable
fun GITOFYCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            content = content
        )
    } else {
        Card(
            modifier = modifier,
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
