package com.gitofy.core.designsystem.components

import com.gitofy.core.designsystem.motion.gitofySlideFadeEnter
import com.gitofy.core.designsystem.motion.gitofySlideFadeExit

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gitofy.core.designsystem.theme.LocalSpacing
import com.gitofy.core.designsystem.tokens.MotionTokens
import com.gitofy.domain.model.WorkflowStatus

/**
 * Shared developer-screen components — PRD Phase 4 §15.
 *
 * These are used across Branches, Issues, Pull Requests, Workflows, CI
 * Control Center, Code Browser, Artifacts, Releases, and Repository Health
 * so that repeated patterns (status, metadata rows, section headers, commit
 * rows, code containers, empty/error states) render identically everywhere.
 */

// ---------------------------------------------------------------------
// SectionHeader
// ---------------------------------------------------------------------

/** Consistent section title used to separate groups of developer content. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = LocalSpacing.current.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

// ---------------------------------------------------------------------
// MetadataRow
// ---------------------------------------------------------------------

/**
 * A single line of secondary, technical metadata (author, timestamp, SHA,
 * branch names…) rendered in the app's secondary typography so it never
 * competes with primary content like a title or commit message.
 */
@Composable
fun MetadataRow(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    maxLines: Int = 1
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(LocalSpacing.current.xs))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------------------------------------------------------------------
// DeveloperCard
// ---------------------------------------------------------------------

/**
 * Thin, semantically-named wrapper over [GITOFYCard] for developer list
 * rows. Exists so screen code reads as "this is a developer row" and so
 * any future developer-specific styling only needs to change here.
 */
@Composable
fun DeveloperCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    GITOFYCard(modifier = modifier.fillMaxWidth(), onClick = onClick, content = content)
}

// ---------------------------------------------------------------------
// Status helpers — icon + text + semantic color (never color alone)
// ---------------------------------------------------------------------

data class DeveloperStatusVisual(
    val label: String,
    val icon: ImageVector,
    val statusType: StatusType
)

/** Maps a [WorkflowStatus] to its icon/label/color triple. */
fun WorkflowStatus.toStatusVisual(): DeveloperStatusVisual = when (this) {
    WorkflowStatus.QUEUED -> DeveloperStatusVisual("Queued", Icons.Default.HourglassEmpty, StatusType.Info)
    WorkflowStatus.IN_PROGRESS -> DeveloperStatusVisual("Running", Icons.Default.PlayCircle, StatusType.Warning)
    WorkflowStatus.COMPLETED_SUCCESS -> DeveloperStatusVisual("Success", Icons.Default.CheckCircle, StatusType.Success)
    WorkflowStatus.COMPLETED_FAILURE -> DeveloperStatusVisual("Failure", Icons.Default.Error, StatusType.Error)
    WorkflowStatus.CANCELLED -> DeveloperStatusVisual("Cancelled", Icons.Default.PauseCircle, StatusType.Neutral)
    WorkflowStatus.SKIPPED -> DeveloperStatusVisual("Skipped", Icons.Default.SkipNext, StatusType.Neutral)
    WorkflowStatus.TIMED_OUT -> DeveloperStatusVisual("Timed Out", Icons.Default.ErrorOutline, StatusType.Error)
    WorkflowStatus.UNKNOWN -> DeveloperStatusVisual("Unknown", Icons.Default.RadioButtonUnchecked, StatusType.Neutral)
}

/** Job/step-level status visual from GitHub's raw `status`/`conclusion` strings. */
fun jobStatusVisual(status: String, conclusion: String?): DeveloperStatusVisual = when {
    status == "queued" -> DeveloperStatusVisual("Queued", Icons.Default.HourglassEmpty, StatusType.Info)
    status == "in_progress" -> DeveloperStatusVisual("Running", Icons.Default.PlayCircle, StatusType.Warning)
    conclusion == "success" -> DeveloperStatusVisual("Success", Icons.Default.CheckCircle, StatusType.Success)
    conclusion == "failure" -> DeveloperStatusVisual("Failure", Icons.Default.Error, StatusType.Error)
    conclusion == "skipped" -> DeveloperStatusVisual("Skipped", Icons.Default.SkipNext, StatusType.Neutral)
    conclusion == "cancelled" -> DeveloperStatusVisual("Cancelled", Icons.Default.PauseCircle, StatusType.Neutral)
    else -> DeveloperStatusVisual(status.replaceFirstChar { it.uppercase() }, Icons.Default.RadioButtonUnchecked, StatusType.Neutral)
}

/** Issue open/closed visual. */
fun issueStatusVisual(state: String): DeveloperStatusVisual = if (state.equals("open", ignoreCase = true)) {
    DeveloperStatusVisual("Open", Icons.Default.RadioButtonUnchecked, StatusType.Success)
} else {
    DeveloperStatusVisual("Closed", Icons.Default.CheckCircle, StatusType.Neutral)
}

/** Pull request draft/open/merged/closed visual. */
fun pullRequestStatusVisual(state: String, isDraft: Boolean, isMerged: Boolean): DeveloperStatusVisual = when {
    isDraft -> DeveloperStatusVisual("Draft", Icons.Default.RadioButtonUnchecked, StatusType.Neutral)
    isMerged -> DeveloperStatusVisual("Merged", Icons.Default.CheckCircle, StatusType.Info)
    state == "open" -> DeveloperStatusVisual("Open", Icons.Default.RadioButtonUnchecked, StatusType.Success)
    else -> DeveloperStatusVisual("Closed", Icons.Default.ErrorOutline, StatusType.Neutral)
}

/** [StatusBadge] variant that always pairs its color with an icon and label. */
@Composable
fun WorkflowStatusBadge(status: WorkflowStatus, modifier: Modifier = Modifier) {
    val visual = status.toStatusVisual()
    IconStatusBadge(visual, modifier)
}

@Composable
fun IconStatusBadge(visual: DeveloperStatusVisual, modifier: Modifier = Modifier) {
    val colors = when (visual.statusType) {
        StatusType.Success -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        StatusType.Error -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        StatusType.Warning -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        StatusType.Info -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        StatusType.Neutral -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(modifier = modifier, shape = MaterialTheme.shapes.small, color = colors.first) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(visual.icon, contentDescription = null, modifier = Modifier.size(12.dp), tint = colors.second)
            Spacer(modifier = Modifier.width(4.dp))
            Text(visual.label, style = MaterialTheme.typography.labelSmall, color = colors.second)
        }
    }
}

// ---------------------------------------------------------------------
// CommitRow — message -> author+timestamp -> SHA hierarchy
// ---------------------------------------------------------------------

/**
 * Standard commit row. Hierarchy per PRD §6: commit message is primary,
 * author + timestamp secondary, short SHA tertiary/monospace.
 */
@Composable
fun CommitRow(
    message: String,
    authorName: String,
    timestamp: String,
    sha: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = message.substringBefore('\n'),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$authorName · $timestamp",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(LocalSpacing.current.sm))
            Text(
                text = sha.take(7),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------------
// RepositoryMetadata — small inline metadata strip (repo path, branch…)
// ---------------------------------------------------------------------

@Composable
fun RepositoryMetadata(
    owner: String,
    repo: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = "$owner/$repo",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

// ---------------------------------------------------------------------
// CodeContainer — monospace code/log/diff surface
// ---------------------------------------------------------------------

/**
 * Readable, horizontally-scrollable monospace surface for diffs, file
 * previews, and short code excerpts. Deliberately not a heavily elevated
 * / rounded card per line — a single flat container holds all the lines.
 */
@Composable
fun CodeContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Box(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = LocalSpacing.current.md, vertical = LocalSpacing.current.sm)
        ) {
            content()
        }
    }
}

/** A single monospace code/diff line, colored for add/remove/context/hunk. */
@Composable
fun CodeLine(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = color,
        maxLines = 1
    )
}

// ---------------------------------------------------------------------
// Developer empty / error states — same visual language, dev-flavored copy
// ---------------------------------------------------------------------

@Composable
fun DeveloperEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    EmptyStateView(
        icon = icon,
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        actionText = actionText,
        onAction = onAction
    )
}

@Composable
fun DeveloperErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    ErrorBanner(message = message, onRetry = onRetry, modifier = modifier)
}

// ---------------------------------------------------------------------
// Motion helpers
// ---------------------------------------------------------------------

/**
 * Wraps tab bodies (PR Conversation/Files/Commits, etc.) in a subtle
 * fade + horizontal-shift crossfade instead of an abrupt swap. PRD §16 —
 * "Tab transitions: use subtle horizontal/fade movement."
 */
@Composable
fun <T> DeveloperTabContent(
    targetState: T,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit
) {
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            gitofySlideFadeEnter.togetherWith(gitofySlideFadeExit)
        },
        label = "developerTabContent"
    ) { state ->
        content(state)
    }
}
