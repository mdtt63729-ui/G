package com.gitofy.core.designsystem.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.gitofy.core.designsystem.theme.LocalSpacing
import com.gitofy.core.designsystem.tokens.Dimensions
import androidx.compose.ui.unit.dp

/**
 * Section header used to group related settings/content (PRD §22, §33).
 * e.g. "Account", "AI", "Appearance", "Workflow".
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(
            horizontal = LocalSpacing.current.lg,
            vertical = 4.dp
        )
    )
}

/**
 * A single row inside a settings group: leading icon, title, supporting
 * text, and a trailing control or chevron (PRD §22).
 *
 * Guarantees a minimum touch target and is safe to place directly inside a
 * [GITOFYCard] to form a settings group.
 */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                } else Modifier
            )
            .padding(horizontal = LocalSpacing.current.md, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                },
                modifier = Modifier.size(Dimensions.iconMedium)
            )
            Spacer(modifier = Modifier.width(LocalSpacing.current.md))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                }
            )
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (trailing != null) {
            Spacer(modifier = Modifier.width(LocalSpacing.current.sm))
            trailing()
        }
    }
}

/**
 * Convenience [SettingRow] with a trailing [Switch] for boolean toggles
 * (e.g. Dynamic Color, Background Sync).
 */
@Composable
fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    SettingRow(
        title = title,
        supportingText = supportingText,
        icon = icon,
        enabled = enabled,
        onClick = { onCheckedChange(!checked) },
        modifier = modifier,
        trailing = {
            GITOFYBouncySwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                contentDescription = title
            )
        }
    )
}


/**
 * Premium compact switch used throughout GITOFY settings.
 * The thumb uses a spring with a small overshoot so ON/OFF changes feel
 * tactile and bouncy instead of relying on the platform's linear-looking
 * default transition.
 */
@Composable
fun GITOFYBouncySwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null
) {
    val hapticView = LocalView.current
    val hapticChange: (Boolean) -> Unit = { value ->
        GITOFYHaptics.toggle(hapticView)
        onCheckedChange(value)
    }
    val targetOffset: Dp = if (checked) 24.dp else 4.dp
    val thumbOffset by animateDpAsState(
        targetValue = targetOffset,
        animationSpec = spring(
            dampingRatio = 0.62f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "switchThumbOffset"
    )
    val thumbScale by animateFloatAsState(
        targetValue = if (checked) 1.08f else 1f,
        animationSpec = spring(
            dampingRatio = 0.58f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "switchThumbScale"
    )
    // FIX: the off-state track (surfaceVariant) and off-state thumb
    // (outline) used to sit too close in tone in every theme mode — outline
    // is deliberately a subtle, low-contrast color meant for thin borders,
    // not a filled shape, so the thumb nearly disappeared into the track
    // and it was hard to tell at a glance whether a switch was on or off.
    // The thumb now uses onSurfaceVariant (a real content color, high
    // contrast against surfaceVariant) and the track always carries a
    // visible outline stroke so its boundary reads clearly against
    // whatever surface it sits on, in light, dark, and dynamic color alike.
    val trackColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 180
        ),
        label = "switchTrackColor"
    )
    val trackBorderColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outline,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 180
        ),
        label = "switchTrackBorderColor"
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 180
        ),
        label = "switchThumbColor"
    )

    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(width = 52.dp, height = 32.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
            .background(
                if (enabled) trackColor else trackColor.copy(alpha = 0.38f)
            )
            .border(
                width = 1.5.dp,
                color = if (enabled) trackBorderColor else trackBorderColor.copy(alpha = 0.38f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
            )
            .clickable(
                enabled = enabled,
                role = Role.Switch,
                interactionSource = interactionSource,
                indication = null
            ) { onCheckedChange(!checked) }
            .semantics {
                role = Role.Switch
                contentDescription?.let { this.contentDescription = it }
            }
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .align(Alignment.CenterStart)
                .size(24.dp)
                .graphicsLayer {
                    scaleX = thumbScale
                    scaleY = thumbScale
                }
                .clip(CircleShape)
                .background(if (enabled) thumbColor else thumbColor.copy(alpha = 0.55f))
        )
    }
}

/**
 * Thin divider to separate rows within a settings group without wrapping
 * every row in its own card (PRD §18 — avoid a card-per-row).
 */
@Composable
fun SettingRowDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = LocalSpacing.current.lg),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}
