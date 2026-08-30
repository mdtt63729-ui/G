package com.gitofy.feature.codebrowser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gitofy.core.designsystem.components.CodeContainer
import com.gitofy.core.designsystem.components.CodeLine
import com.gitofy.core.designsystem.components.DeveloperEmptyState
import com.gitofy.core.designsystem.components.GITOFYTopAppBar
import com.gitofy.core.designsystem.theme.LocalSpacing
import com.gitofy.domain.model.FileContent

/**
 * Code Browser — PRD Phase 4 §11.
 *
 * Breadcrumb navigation, directory/file hierarchy with per-type file
 * icons, and a monospace, horizontally-scrollable code preview.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeBrowserScreen(
    owner: String,
    repo: String,
    onBack: () -> Unit,
    viewModel: CodeBrowserViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(owner, repo) { viewModel.browse(owner, repo) }

    val selectedFile = uiState.selectedFile
    if (selectedFile != null) {
        FileViewerScreen(
            file = selectedFile,
            content = uiState.fileContent,
            onBack = { viewModel.closeFile() }
        )
        return
    }

    Scaffold(
        topBar = { GITOFYTopAppBar(title = repo, onBack = onBack) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Repository path + breadcrumb navigation
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                Text(
                    text = "$owner/$repo",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (uiState.breadcrumbs.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        uiState.breadcrumbs.forEachIndexed { index, crumb ->
                            if (index > 0) {
                                Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            TextButton(
                                onClick = {
                                    val newPath = uiState.breadcrumbs.take(index + 1).joinToString("/")
                                    viewModel.browse(owner, repo, newPath)
                                }
                            ) { Text(crumb, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }

            if (uiState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (!uiState.isLoading && uiState.files.isEmpty()) {
                DeveloperEmptyState(
                    icon = Icons.Default.FolderOff,
                    title = "This folder is empty",
                    subtitle = "There's nothing to show at this path.",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(LocalSpacing.current.lg),
                    verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.xs)
                ) {
                    items(uiState.files, key = { it.path }) { file ->
                        FileRow(file = file, onClick = {
                            if (file.isDirectory) {
                                viewModel.browse(owner, repo, file.path)
                            } else {
                                viewModel.openFile(file)
                            }
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun FileRow(file: FileContent, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            fileTypeIcon(file),
            contentDescription = null,
            tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(LocalSpacing.current.md))
        Text(
            file.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (!file.isDirectory) {
            Text(formatFileSize(file.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Maps a file to a representative type icon based on directory/extension. */
private fun fileTypeIcon(file: FileContent): ImageVector {
    if (file.isDirectory) return Icons.Default.Folder
    return when (file.name.substringAfterLast('.', "").lowercase()) {
        "kt", "kts", "java", "py", "js", "ts", "tsx", "jsx", "go", "rs", "c", "cpp", "h", "swift" -> Icons.Default.Code
        "md", "markdown", "txt" -> Icons.Default.Description
        "json", "yml", "yaml", "xml", "toml" -> Icons.Default.DataObject
        "png", "jpg", "jpeg", "gif", "svg", "webp" -> Icons.Default.Image
        "gradle" -> Icons.Default.Build
        else -> Icons.Default.InsertDriveFile
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileViewerScreen(file: FileContent, content: String?, onBack: () -> Unit) {
    Scaffold(
        topBar = { GITOFYTopAppBar(title = file.name, onBack = onBack) }
    ) { padding ->
        if (content == null) {
            DeveloperEmptyState(
                icon = Icons.Default.Description,
                title = "Can't preview this file",
                subtitle = "Binary file or unable to decode.",
                modifier = Modifier.fillMaxSize().padding(padding)
            )
        } else {
            val lines = content.lines()
            CodeContainer(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
            ) {
                Row {
                    // Line numbers
                    Column(modifier = Modifier.padding(end = LocalSpacing.current.md)) {
                        lines.indices.forEach { i ->
                            Text(
                                "${i + 1}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    // Content
                    Column {
                        lines.forEach { line ->
                            CodeLine(text = line, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> "${bytes / 1024}KB"
    else -> "${bytes / 1024 / 1024}MB"
}
