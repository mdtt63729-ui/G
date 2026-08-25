package com.gitofy.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.gitofy.core.designsystem.theme.LocalSpacing
import kotlinx.coroutines.launch

/**
 * Rich Terminal & Log Inspector — PRD Addendum.
 * ANSI color-coded dark terminal view for GitHub Actions logs.
 * Real-time search filter and auto-scroll options for tracking build errors.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalLogInspector(
    logs: String,
    modifier: Modifier = Modifier,
    onSearch: String? = null
) {
    val lines = logs.lines()
    val filteredLines = if (onSearch.isNullOrEmpty()) {
        lines
    } else {
        lines.filter { it.contains(onSearch, ignoreCase = true) }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Auto-scroll to bottom for new logs
    LaunchedEffect(filteredLines.size) {
        if (filteredLines.isNotEmpty()) {
            listState.animateScrollToItem(filteredLines.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
    ) {
        // Terminal header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2D2D2D))
                .padding(horizontal = LocalSpacing.current.md, vertical = LocalSpacing.current.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Terminal — Logs",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF00FF41),
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${filteredLines.size} lines",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF888888),
                fontFamily = FontFamily.Monospace
            )
        }

        // Terminal body
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = LocalSpacing.current.md, vertical = LocalSpacing.current.sm)
        ) {
            items(filteredLines) { line ->
                Text(
                    text = parseAnsiColors(line),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFD4D4D4),
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
        }
    }
}

/**
 * Parse ANSI color codes and apply syntax highlighting.
 * Highlights: errors (red), warnings (yellow), success (green), info (blue).
 */
private fun parseAnsiColors(line: String): AnnotatedString {
    return buildAnnotatedString {
        val lower = line.lowercase()

        when {
            lower.contains("error") || lower.contains("failed") || lower.contains("exception") -> {
                withStyle(SpanStyle(color = Color(0xFFFF6B6B))) { append(line) }
            }
            lower.contains("warn") -> {
                withStyle(SpanStyle(color = Color(0xFFFFD93D))) { append(line) }
            }
            lower.contains("success") || lower.contains("passed") || lower.contains("completed") -> {
                withStyle(SpanStyle(color = Color(0xFF6BCB77))) { append(line) }
            }
            lower.contains("info") || lower.contains("log") -> {
                withStyle(SpanStyle(color = Color(0xFF4D96FF))) { append(line) }
            }
            else -> {
                append(line)
            }
        }
    }
}
