/* Intentional code-editor palette: these colors are syntax/terminal tokens, not app theme surfaces. */
package com.gitofy.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gitofy.core.designsystem.theme.LocalSpacing

/**
 * In-App Workflow YAML Editor — PRD Addendum.
 * Dedicated YAML code editor with syntax highlighting and validation
 * for .github/workflows/android_build.yml
 */
@Composable
fun YamlWorkflowEditor(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Workflow YAML"
) {
    val validation = validateYaml(text)

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Label + validation indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            if (validation.isValid) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Valid YAML",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    "Valid",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            } else {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = "Invalid YAML",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    "Issues found",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(modifier = Modifier.height(LocalSpacing.current.sm))

        // Editor
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp, max = 400.dp),
            color = Color(0xFF1E1E1E),
            shape = MaterialTheme.shapes.medium
        ) {
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(LocalSpacing.current.md)
                    .verticalScroll(rememberScrollState()),
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = Color(0xFFD4D4D4),
                    lineHeight = 18.sp
                ),

            )
        }

        // Validation errors
        if (!validation.isValid && validation.errors.isNotEmpty()) {
            Spacer(modifier = Modifier.height(LocalSpacing.current.sm))
            validation.errors.take(3).forEach { error ->
                Text(
                    text = "• $error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * YAML syntax highlighter using Compose AnnotatedString.
 */
private fun highlightYamlSyntax(text: String): AnnotatedString {
    return buildAnnotatedString {
        val lines = text.lines()
        lines.forEachIndexed { index, line ->
            val trimmed = line.trimStart()

            when {
                // Comments
                trimmed.startsWith("#") -> {
                    withStyle(SpanStyle(color = Color(0xFF6A9955))) { append(line) }
                }
                // Keys (before colon)
                trimmed.contains(":") && !trimmed.startsWith("-") -> {
                    val colonIndex = trimmed.indexOf(":")
                    val indent = line.length - trimmed.length
                    append(line.substring(0, indent))
                    withStyle(SpanStyle(color = Color(0xFF569CD6), fontWeight = FontWeight.Bold)) {
                        append(trimmed.substring(0, colonIndex))
                    }
                    withStyle(SpanStyle(color = Color(0xFFD4D4D4))) {
                        append(trimmed.substring(colonIndex))
                    }
                }
                // List items
                trimmed.startsWith("-") -> {
                    withStyle(SpanStyle(color = Color(0xFFCE9178))) { append(line) }
                }
                else -> {
                    withStyle(SpanStyle(color = Color(0xFFD4D4D4))) { append(line) }
                }
            }
            if (index < lines.lastIndex) append("\n")
        }
    }
}

data class YamlValidationResult(
    val isValid: Boolean,
    val errors: List<String>
)

/**
 * Basic YAML validation for GitHub workflow files.
 */
fun validateYaml(text: String): YamlValidationResult {
    val errors = mutableListOf<String>()

    if (text.isBlank()) {
        return YamlValidationResult(false, listOf("YAML is empty"))
    }

    // Check for required GitHub workflow fields
    if (!text.contains("name:")) {
        errors.add("Missing 'name' field")
    }
    if (!text.contains("on:")) {
        errors.add("Missing 'on' field (trigger)")
    }
    if (!text.contains("jobs:")) {
        errors.add("Missing 'jobs' field")
    }

    // Basic indentation check
    val lines = text.lines()
    lines.forEachIndexed { index, line ->
        if (line.contains("\t")) {
            errors.add("Tab character at line ${index + 1} (use spaces)")
        }
    }

    return YamlValidationResult(errors.isEmpty(), errors)
}
