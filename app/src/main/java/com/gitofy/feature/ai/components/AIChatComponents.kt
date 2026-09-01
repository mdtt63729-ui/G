package com.gitofy.feature.ai.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gitofy.core.designsystem.tokens.Dimensions
import com.gitofy.feature.ai.AttachmentData
import com.gitofy.feature.ai.FileProcessor
import android.widget.Toast
import java.util.Calendar
import kotlinx.coroutines.delay

/**
 * GITO AI — Phase 5 chat building blocks.
 *
 * These are intentionally free of any ViewModel/state-management dependency
 * so the existing AI architecture, providers and view models are untouched —
 * this file only supplies the presentational layer described in the
 * Phase 5 modernization spec.
 */

// ---------------------------------------------------------------------------
// Markdown-ish rendering (headings, bold, inline code, fenced code blocks)
// ---------------------------------------------------------------------------

private sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Code(val language: String?, val code: String) : MdBlock
    data class Bullet(val items: List<String>) : MdBlock
}

private fun parseMarkdownBlocks(raw: String): List<MdBlock> {
    val lines = raw.split("\n")
    val blocks = mutableListOf<MdBlock>()
    var i = 0
    val paragraph = StringBuilder()
    val bullets = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraph.isNotBlank()) {
            blocks.add(MdBlock.Paragraph(paragraph.toString().trim()))
        }
        paragraph.clear()
    }
    fun flushBullets() {
        if (bullets.isNotEmpty()) {
            blocks.add(MdBlock.Bullet(bullets.toList()))
            bullets.clear()
        }
    }

    while (i < lines.size) {
        val line = lines[i]
        val fenceMatch = Regex("^```(\\w*)\\s*$").find(line.trim())
        if (fenceMatch != null) {
            flushParagraph(); flushBullets()
            val lang = fenceMatch.groupValues[1].ifBlank { null }
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && lines[i].trim() != "```") {
                codeLines.add(lines[i])
                i++
            }
            blocks.add(MdBlock.Code(lang, codeLines.joinToString("\n")))
            i++
            continue
        }
        val bulletMatch = Regex("^\\s*[-*]\\s+(.*)$").find(line)
        if (bulletMatch != null) {
            flushParagraph()
            bullets.add(bulletMatch.groupValues[1])
            i++
            continue
        }
        if (line.isBlank()) {
            flushParagraph(); flushBullets()
        } else {
            flushBullets()
            paragraph.append(if (paragraph.isEmpty()) line else "\n$line")
        }
        i++
    }
    flushParagraph(); flushBullets()
    return blocks
}

/** Renders **bold** and `inline code` spans within a single line of text. */
@Composable
private fun InlineMarkdownText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color
) {
    val annotated = remember(text) {
        buildAnnotatedString(text)
    }
    Text(text = annotated, style = style, color = color)
}

private fun buildAnnotatedString(text: String): AnnotatedString {
    val builder = AnnotatedString.Builder()
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end == -1) { builder.append(text.substring(i)); i = text.length }
                else {
                    builder.pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold))
                    builder.append(text.substring(i + 2, end))
                    builder.pop()
                    i = end + 2
                }
            }
            text.startsWith("`", i) -> {
                val end = text.indexOf("`", i + 1)
                if (end == -1) { builder.append(text.substring(i)); i = text.length }
                else {
                    builder.pushStyle(
                        androidx.compose.ui.text.SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color.Black.copy(alpha = 0.06f)
                        )
                    )
                    builder.append(text.substring(i + 1, end))
                    builder.pop()
                    i = end + 1
                }
            }
            else -> {
                val nextSpecial = listOf(text.indexOf("**", i), text.indexOf("`", i))
                    .filter { it != -1 }
                    .minOrNull() ?: text.length
                builder.append(text.substring(i, nextSpecial))
                i = nextSpecial
            }
        }
    }
    return builder.toAnnotatedString()
}

/**
 * Renders assistant markdown content: paragraphs, bullet lists and fenced
 * code blocks (with copy action + language label). Section 2 / 13.
 */
@Composable
fun MarkdownMessageContent(
    text: String,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Paragraph -> InlineMarkdownText(
                    text = block.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor
                )
                is MdBlock.Bullet -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    block.items.forEach { item ->
                        Row {
                            Text("•  ", color = contentColor, style = MaterialTheme.typography.bodyMedium)
                            InlineMarkdownText(item, MaterialTheme.typography.bodyMedium, contentColor)
                        }
                    }
                }
                is MdBlock.Code -> CodeBlock(language = block.language, code = block.code)
            }
        }
    }
}

/** Section 13 — specialized code container: monospace, scrollable, copyable, labeled. */
@Composable
fun CodeBlock(
    language: String?,
    code: String,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 0.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language?.uppercase() ?: "CODE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(code))
                        Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(Dimensions.minTouchTarget).semantics {
                        contentDescription = "Copy code"
                    }
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            ) {
                Text(
                    text = code,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Message bubbles (Section 2)
// ---------------------------------------------------------------------------

/**
 * PRD §13/§21: User message bubble with attachment cards above the text.
 *
 * Attachment cards appear above the user's text message, showing file icon,
 * file name, and actual file size. The attachment/message relationship
 * persists across the conversation.
 */
@Composable
fun UserMessageBubble(
    content: String,
    timestamp: String?,
    attachments: List<AttachmentData> = emptyList(),
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(max = 320.dp)) {
            // PRD §13: Attachment cards ABOVE the user message text
            if (attachments.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    attachments.forEach { att ->
                        AttachmentCard(attachment = att)
                    }
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp),
                tonalElevation = 0.dp
            ) {
                Text(
                    text = content,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (timestamp != null) {
                Text(
                    text = timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, end = 4.dp)
                )
            }
        }
    }
}

/**
 * PRD §13/§21: Attachment card showing file icon, name, and actual size.
 * Displayed above the user message bubble for the message that sent it.
 */
@Composable
fun AttachmentCard(
    attachment: AttachmentData,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.AttachFile,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    attachment.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                // PRD §12: Actual file size, not estimates
                Text(
                    FileProcessor.formatSize(attachment.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun AssistantMessageBubble(
    content: String,
    isStreaming: Boolean,
    onCopy: () -> Unit,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Column(modifier = Modifier.widthIn(max = 340.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    MarkdownMessageContent(
                        text = content,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                    if (isStreaming) {
                        Spacer(modifier = Modifier.height(4.dp))
                        StreamingDots()
                    }
                    if (!isStreaming && content.isNotBlank()) {
                        Row(
                            modifier = Modifier.padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            TextButton(onClick = onCopy, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy", style = MaterialTheme.typography.labelSmall)
                            }
                            if (onRetry != null) {
                                TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Retry", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Lightweight three-dot typing/streaming indicator — Section 3 / 7. */
@Composable
fun StreamingDots(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "streaming")
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = index * 150, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$index"
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Empty state (Section 6)
// ---------------------------------------------------------------------------

data class SuggestedPrompt(val label: String, val prompt: String)

// PRD §16: The oversized "Explain this repository" section and all
// suggested-prompt cards have been removed. The empty state is now
// just the Gito icon, "Gito", "Your AI coding assistant", and the
// input bar — no unnecessary large cards.
val defaultSuggestedPrompts: List<SuggestedPrompt> = emptyList()

/**
 * PRD §8: Clean, minimal empty state.
 * The large "Explain this repository" card and suggested prompts have been
 * removed. The empty state is now just the Gito logo + name + subtitle,
 * leaving the chat UI immediately usable.
 */
@Composable
fun AIEmptyState(
    onPromptSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var hour by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        }
    }
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Good night"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(42.dp))
        Text(
            text = greeting,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "What can we build today?",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

// ---------------------------------------------------------------------------
// Error state (Section 8)
// ---------------------------------------------------------------------------

data class AIErrorInfo(
    val message: String,
    val isRetryable: Boolean = true,
    val missingProviderConfig: Boolean = false
)

@Composable
fun AIErrorBanner(
    error: AIErrorInfo,
    onRetry: () -> Unit,
    onConfigureProvider: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = error.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Dismiss error",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (error.isRetryable) {
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
                if (error.missingProviderConfig) {
                    TextButton(onClick = onConfigureProvider) { Text("Configure Provider") }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Contextual AI chip (Section 12)
// ---------------------------------------------------------------------------

data class AIContextChipData(val label: String, val value: String)

/** A single removable context chip (repository, branch, file, PR, etc). */
@Composable
fun ContextChip(chip: AIContextChipData, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    InputChip(
        selected = false,
        onClick = onRemove,
        modifier = modifier.semantics { contentDescription = "Context: ${chip.label} ${chip.value}. Tap to remove." },
        label = { Text("${chip.label}: ${chip.value}", style = MaterialTheme.typography.labelMedium) },
        trailingIcon = {
            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(14.dp))
        }
    )
}

/** Horizontally scrollable row of removable context chips. */
@Composable
fun ContextChipRow(
    context: List<AIContextChipData>,
    onRemove: (AIContextChipData) -> Unit,
    modifier: Modifier = Modifier
) {
    if (context.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        context.forEach { chip ->
            ContextChip(chip = chip, onRemove = { onRemove(chip) })
        }
    }
}

// ---------------------------------------------------------------------------
// Provider state pill (Sections 9 / 10)
// ---------------------------------------------------------------------------

enum class ProviderConfigState { NOT_CONFIGURED, CONFIGURED, ACTIVE, ERROR }

@Composable
fun ProviderStatePill(state: ProviderConfigState, modifier: Modifier = Modifier) {
    val (label, containerColor, contentColor, icon) = when (state) {
        ProviderConfigState.NOT_CONFIGURED -> Quad(
            "Not configured",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Filled.Settings
        )
        ProviderConfigState.CONFIGURED -> Quad(
            "Configured",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            Icons.Filled.CheckCircle
        )
        ProviderConfigState.ACTIVE -> Quad(
            "Active",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            Icons.Filled.CheckCircle
        )
        ProviderConfigState.ERROR -> Quad(
            "Error",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            Icons.Filled.ErrorOutline
        )
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = contentColor)
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
