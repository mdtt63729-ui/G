package com.gitofy.feature.workflows.logs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gitofy.core.designsystem.components.GITOFYTopAppBar
import com.gitofy.core.designsystem.motion.gitofySlideFadeEnter
import com.gitofy.core.designsystem.motion.gitofySlideFadeExit
import com.gitofy.core.designsystem.theme.LocalSpacing
import com.gitofy.domain.model.JobSummary
import com.gitofy.domain.model.StepSummary
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    owner: String,
    repo: String,
    jobId: Long,
    onBack: () -> Unit,
    viewModel: LogsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var searchText by remember { mutableStateOf("") }
    var expandedStep by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(owner, repo, jobId) {
        viewModel.loadLogs(owner, repo, jobId)
    }

    Scaffold(
        topBar = {
            GITOFYTopAppBar(title = uiState.job?.name ?: "Logs", onBack = onBack)
        }
    ) { padding ->
        when {
            uiState.isLoading && uiState.job == null -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.job == null && uiState.error != null -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Icon(Icons.Default.ErrorOutline, null, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(uiState.error!!, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = { viewModel.loadLogs(owner, repo, jobId) }) { Text("Retry") }
                    }
                }
            }
            else -> {
                val job = uiState.job!!
                Column(Modifier.fillMaxSize().padding(padding)) {
                    JobHeader(job = job, isRefreshing = uiState.isRefreshing)

                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg, vertical = 8.dp),
                        placeholder = { Text("Search in logs...") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true
                    )

                    if (uiState.logUnavailableWhileRunning && uiState.logs.isBlank()) {
                        Text(
                            "Waiting for GitHub Actions logs…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = LocalSpacing.current.lg, vertical = 4.dp)
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = LocalSpacing.current.lg, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Text(
                                "STEPS",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        if (job.steps.isEmpty()) {
                            item {
                                StepLogCard(
                                    step = StepSummary("Job log", job.status, job.conclusion, 1),
                                    isExpanded = expandedStep == 0,
                                    onClick = { expandedStep = if (expandedStep == 0) null else 0 },
                                    logText = filterLog(uiState.logs, searchText)
                                )
                            }
                        } else {
                            items(job.steps, key = { it.number }) { step ->
                                val expanded = expandedStep == step.number
                                StepLogCard(
                                    step = step,
                                    isExpanded = expanded,
                                    onClick = { expandedStep = if (expanded) null else step.number },
                                    logText = filterLog(extractStepLog(uiState.logs, job.steps, step), searchText)
                                )
                            }
                        }

                        if (uiState.logs.isNotBlank()) {
                            item {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "RAW JOB LOG",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                                CompactTerminalLog(
                                    logs = filterLog(uiState.logs, searchText),
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 300.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JobHeader(job: JobSummary, isRefreshing: Boolean) {
    val running = job.status == "queued" || job.status == "in_progress"
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = LocalSpacing.current.lg, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusIcon(status = job.status, conclusion = job.conclusion)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(job.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        job.conclusion == "success" -> "Completed successfully"
                        job.conclusion == "failure" -> "Failed"
                        job.status == "in_progress" -> "Running"
                        job.status == "queued" -> "Queued"
                        else -> job.status.replace('_', ' ').replaceFirstChar { it.uppercase() }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (running || isRefreshing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun StepLogCard(
    step: StepSummary,
    isExpanded: Boolean,
    onClick: () -> Unit,
    logText: String
) {
    val running = step.status == "in_progress" || step.status == "queued"
    val success = step.conclusion == "success"
    val failed = step.conclusion == "failure"

    GITOFYStepCard(onClick = onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StatusIcon(step.status, step.conclusion)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(step.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    statusLabel(step),
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        success -> MaterialTheme.colorScheme.primary
                        failed -> MaterialTheme.colorScheme.error
                        running -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Text(durationText(step), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = gitofySlideFadeEnter,
            exit = gitofySlideFadeExit
        ) {
            Column {
                Spacer(Modifier.height(8.dp))
                CompactTerminalLog(
                    logs = if (logText.isBlank()) "No matching log lines for this step yet." else logText,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 240.dp)
                )
            }
        }
    }
}

@Composable
private fun GITOFYStepCard(onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        tonalElevation = 1.dp
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
private fun StatusIcon(status: String, conclusion: String?) {
    val running = status == "in_progress" || status == "queued"
    val success = conclusion == "success"
    val failed = conclusion == "failure"
    val color = when {
        success -> Color(0xFF2E7D32)
        failed -> MaterialTheme.colorScheme.error
        running -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(Modifier.size(34.dp).clip(CircleShape).background(color.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
        when {
            success -> Icon(Icons.Default.Check, null, tint = color, modifier = Modifier.size(19.dp))
            failed -> Icon(Icons.Default.Close, null, tint = color, modifier = Modifier.size(19.dp))
            running -> {
                val transition = rememberInfiniteTransition(label = "step-running")
                val alpha by transition.animateFloat(0.35f, 1f, infiniteRepeatable(tween(650, easing = LinearEasing), RepeatMode.Reverse), label = "step-alpha")
                Icon(Icons.Default.PlayArrow, null, tint = color.copy(alpha = alpha), modifier = Modifier.size(19.dp))
            }
            else -> Icon(Icons.Default.Schedule, null, tint = color, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun CompactTerminalLog(logs: String, modifier: Modifier = Modifier) {
    val lines = logs.lines()
    val vertical = rememberScrollState()
    val horizontal = rememberScrollState()
    Column(
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF171717)).padding(10.dp).verticalScroll(vertical).horizontalScroll(horizontal)
    ) {
        lines.forEach { line ->
            Text(
                line,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFD7D7D7),
                softWrap = false
            )
        }
    }
}

private fun statusLabel(step: StepSummary): String = when {
    step.conclusion == "success" -> "Completed"
    step.conclusion == "failure" -> "Failed"
    step.conclusion == "cancelled" -> "Cancelled"
    step.status == "in_progress" -> "Working…"
    step.status == "queued" -> "Waiting…"
    else -> step.status.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

private fun durationText(step: StepSummary): String {
    val start = step.startedAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return ""
    val end = step.completedAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.now()
    val d = Duration.between(start, end)
    return "${d.toMinutes()}m ${d.seconds % 60}s"
}

private fun filterLog(logs: String, search: String): String = if (search.isBlank()) logs else logs.lines().filter { it.contains(search, ignoreCase = true) }.joinToString("\n")

/**
 * GitHub Actions job logs are a single stream. The API does not expose a
 * separate log endpoint per step, so we split the stream using GitHub's
 * ##[group]/##[endgroup] markers and step-name/Run markers where possible.
 * If no marker can be matched, the step falls back to the complete raw job log
 * rather than inventing or changing log text.
 */
private fun extractStepLog(logs: String, steps: List<StepSummary>, target: StepSummary): String {
    if (logs.isBlank()) return ""
    val lines = logs.lines()
    val groups = mutableListOf<String>()
    var current = StringBuilder()
    var depth = 0
    for (line in lines) {
        val group = line.contains("##[group]", ignoreCase = true)
        val end = line.contains("##[endgroup]", ignoreCase = true)
        if (group && depth == 0 && current.isNotEmpty()) {
            groups += current.toString().trimEnd()
            current = StringBuilder()
        }
        current.append(line).append('\n')
        if (group) depth++
        if (end) depth = (depth - 1).coerceAtLeast(0)
    }
    if (current.isNotEmpty()) groups += current.toString().trimEnd()

    val index = steps.indexOfFirst { it.number == target.number }
    val grouped = groups.filter { it.isNotBlank() }
    if (index in grouped.indices) return grouped[index]

    val nameMatches = lines.filter { line ->
        line.contains(target.name, ignoreCase = true) ||
            line.contains("Run ${target.name}", ignoreCase = true)
    }
    return if (nameMatches.isNotEmpty()) nameMatches.joinToString("\n") else logs
}
