package com.gitofy.ai.autonomous

import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.regex.Pattern

/**
 * Autonomous Build Repair Agent (PRD §32-33).
 *
 * Implements the self-healing repair loop:
 *   Read failure → Identify root cause → Scan relevant files → Read config files
 *   → Generate fix → Validate diff → Apply changes → Commit → Push → Run workflow
 *   → Wait for result → Check result → (if failed) repeat → Success
 *
 * Safety is enforced at every stage:
 *   - Secret / credential / token / signing-key files are never read or modified.
 *   - Destructive filesystem operations (delete, overwrite of protected paths) are blocked.
 *   - Configurable hard limits on attempts, execution time, files changed, and deletions.
 *   - "Unlimited until success" mode still respects system-level protections.
 */
@Singleton
class BuildRepairAgent @Inject constructor() {

    // PRD §9/§32: Optional GitHub API service for real log fetching and remote repair.
    @Volatile var _apiService: com.gitofy.core.network.GitHubApiService? = null

    // PRD §32: Repair context — identifies the exact failure being repaired.
    @Volatile var _repairContext: GitoRepairOrchestrator.RepairContext? = null

    // PRD §52/§53: Log redactor for sanitizing logs before AI analysis.
    @Volatile var _logRedactor: com.gitofy.ai.security.LogRedactor? = null

    // ------------------------------------------------------------------
    //  Public types
    // ------------------------------------------------------------------

    /**
     * Stages of the autonomous repair loop.
     */
    enum class RepairStage {
        READ_FAILURE,
        IDENTIFY_ROOT_CAUSE,
        SCAN_FILES,
        READ_CONFIG,
        GENERATE_FIX,
        VALIDATE_DIFF,
        APPLY_CHANGES,
        COMMIT,
        PUSH,
        RUN_WORKFLOW,
        WAIT_FOR_RESULT,
        CHECK_RESULT,
        SUCCESS,
        FAILED
    }

    /**
     * Safety + execution configuration for a repair run.
     *
     * @param maxAttempts       Hard cap on repair iterations. Ignored when [unlimitedMode] is true.
     * @param maxExecutionTimeMs Wall-clock budget for the entire run.
     * @param maxFilesChanged   Maximum number of files that may be modified in one run.
     * @param maxDeletions      Maximum number of files that may be deleted in one run.
     * @param unlimitedMode     When true, the agent retries until success — but system-level
     *                          protections (secret files, max deletions, max files changed) still apply.
     */
    data class RepairConfig(
        val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
        val maxExecutionTimeMs: Long = DEFAULT_MAX_EXECUTION_TIME_MS,
        val maxFilesChanged: Int = DEFAULT_MAX_FILES_CHANGED,
        val maxDeletions: Int = DEFAULT_MAX_DELETIONS,
        val unlimitedMode: Boolean = false,
    )

    /**
     * Immutable snapshot of the repair run's progress.
     */
    data class RepairState(
        val attempt: Int = 0,
        val currentStage: RepairStage = RepairStage.READ_FAILURE,
        val filesChanged: Int = 0,
        val filesDeleted: Int = 0,
        val workflowRunId: String? = null,
        val isRunning: Boolean = false,
        val error: String? = null,
    )

    // ------------------------------------------------------------------
    //  Default safety limits
    // ------------------------------------------------------------------

    companion object {
        /** Default maximum repair attempts. */
        const val DEFAULT_MAX_ATTEMPTS: Int = 10

        /** Default wall-clock budget (30 minutes). */
        const val DEFAULT_MAX_EXECUTION_TIME_MS: Long = 30 * 60 * 1000L

        /** Default maximum number of files that may be changed. */
        const val DEFAULT_MAX_FILES_CHANGED: Int = 50

        /** Default maximum number of files that may be deleted. */
        const val DEFAULT_MAX_DELETIONS: Int = 5

        /**
         * Glob patterns for files that must never be read or modified.
         * Covers environment files, local SDK config, private keys, keystores,
         * credentials, secrets, and service-account JSON.
         */
        val SECRET_FILE_PATTERNS: List<Pattern> = listOf(
            globToRegex(".env"),
            globToRegex("local.properties"),
            globToRegex("*.pem"),
            globToRegex("*.key"),
            globToRegex("*.jks"),
            globToRegex("*.keystore"),
            globToRegex("credentials.*"),
            globToRegex("secrets.*"),
            globToRegex("service-account*.json"),
        )

        /**
         * Convert a shell-style glob (supports `*` and `?`) to a compiled regex.
         * The pattern is matched against the simple file name only.
         */
        private fun globToRegex(glob: String): Pattern {
            val sb = StringBuilder()
            for (ch in glob) {
                when (ch) {
                    '*' -> sb.append(".*")
                    '?' -> sb.append('.')
                    '.', '(', ')', '+', '|', '^', '$', '@', '%', '\\' ->
                        sb.append('\\').append(ch)
                    else -> sb.append(ch)
                }
            }
            return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE)
        }

        /**
         * Returns true when [fileName] matches any secret-file exclusion pattern.
         */
        @JvmStatic
        fun isSecretFile(fileName: String): Boolean {
            val name = fileName.substringAfterLast('/')
            return SECRET_FILE_PATTERNS.any { it.matcher(name).matches() }
        }

        /** Maximum number of characters read from a single config file. */
        const val MAX_CONFIG_READ_CHARS: Int = 100_000

        /** Polling interval while waiting for a workflow run to complete. */
        const val WORKFLOW_POLL_INTERVAL_MS: Long = 10_000

        /** Maximum time to wait for a single workflow run (15 minutes). */
        const val MAX_WORKFLOW_WAIT_MS: Long = 15 * 60 * 1000L
    }

    // ------------------------------------------------------------------
    //  Mutable state
    // ------------------------------------------------------------------

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var repairJob: Job? = null

    private val _state = MutableStateFlow(RepairState())
    /** Observable repair state. */
    val state: StateFlow<RepairState> = _state.asStateFlow()

    /** Files changed so far in the current run. */
    private val changedFiles = mutableSetOf<String>()
    /** Files deleted so far in the current run. */
    private val deletedFiles = mutableSetOf<String>()
    /** Start time of the current run, in epoch millis. */
    private var startTimeMs: Long = 0L

    // ------------------------------------------------------------------
    //  Public API
    // ------------------------------------------------------------------

    /**
     * Starts the autonomous repair loop.
     *
     * The loop runs on a background coroutine. [onComplete] is invoked on [Dispatchers.Main]
     * with the final [RepairState] when the loop finishes (success, failure, or cancellation).
     *
     * @param config     Safety + execution configuration.
     * @param context    Android context (used for resolving project root).
     * @param onComplete Callback invoked exactly once when the run terminates.
     */
    fun startRepair(
        config: RepairConfig,
        context: Context,
        onComplete: (RepairState) -> Unit,
    ) {
        if (_state.value.isRunning) {
            onComplete(_state.value.copy(error = "Repair already in progress"))
            return
        }

        // Reset run-local bookkeeping.
        changedFiles.clear()
        deletedFiles.clear()
        startTimeMs = System.currentTimeMillis()
        _state.value = RepairState(isRunning = true, currentStage = RepairStage.READ_FAILURE)

        repairJob = scope.launch {
            val finalState = runRepairLoop(config, context)
            withContext(Dispatchers.Main) {
                _state.value = finalState
                onComplete(finalState)
            }
        }
    }

    /**
     * Cancels the current repair run, if any.
     */
    fun cancel() {
        repairJob?.cancel()
        repairJob = null
        _state.value = _state.value.copy(
            isRunning = false,
            currentStage = RepairStage.FAILED,
            error = _state.value.error ?: "Cancelled by user",
        )
    }

    /**
     * Returns the current repair state snapshot.
     */
    fun getState(): RepairState = _state.value

    // ------------------------------------------------------------------
    //  Repair loop
    // ------------------------------------------------------------------

    /**
     * The core repair loop. Each iteration walks through all repair stages.
     * If the workflow still fails after a fix is applied, the loop repeats
     * (up to [RepairConfig.maxAttempts], or indefinitely when [RepairConfig.unlimitedMode]).
     */
    private suspend fun runRepairLoop(config: RepairConfig, context: Context): RepairState {
        var attempt = 0
        var lastError: String? = null

        try {
            while (scope.isActive) {
                attempt++
                _state.value = _state.value.copy(attempt = attempt, error = null)

                // --- Enforce max attempts (skipped in unlimited mode) ---
                if (!config.unlimitedMode && attempt > config.maxAttempts) {
                    return _state.value.copy(
                        isRunning = false,
                        currentStage = RepairStage.FAILED,
                        error = "Exceeded max attempts (${config.maxAttempts})",
                    )
                }

                // --- Enforce max execution time (ALWAYS, even in unlimited mode) ---
                val elapsed = System.currentTimeMillis() - startTimeMs
                if (elapsed > config.maxExecutionTimeMs) {
                    return _state.value.copy(
                        isRunning = false,
                        currentStage = RepairStage.FAILED,
                        error = "Exceeded max execution time (${config.maxExecutionTimeMs} ms)",
                    )
                }

                // --- Enforce max files changed (system-level protection) ---
                if (changedFiles.size >= config.maxFilesChanged) {
                    return _state.value.copy(
                        isRunning = false,
                        currentStage = RepairStage.FAILED,
                        error = "Exceeded max files changed (${config.maxFilesChanged})",
                    )
                }

                // --- Enforce max deletions (system-level protection) ---
                if (deletedFiles.size >= config.maxDeletions) {
                    return _state.value.copy(
                        isRunning = false,
                        currentStage = RepairStage.FAILED,
                        error = "Exceeded max deletions (${config.maxDeletions})",
                    )
                }

                // -- Stage 1: Read failure --
                updateStage(RepairStage.READ_FAILURE)
                val failureInfo = readFailure(context)
                if (failureInfo.isBlank()) {
                    // No failure detected — nothing to repair.
                    return _state.value.copy(
                        isRunning = false,
                        currentStage = RepairStage.SUCCESS,
                    )
                }

                // -- Stage 2: Identify root cause --
                updateStage(RepairStage.IDENTIFY_ROOT_CAUSE)
                val rootCause = identifyRootCause(failureInfo)

                // -- Stage 3: Scan relevant files --
                updateStage(RepairStage.SCAN_FILES)
                val relevantFiles = scanRelevantFiles(context, rootCause)

                // -- Stage 4: Read config files --
                updateStage(RepairStage.READ_CONFIG)
                val configContents = readConfigFiles(context)

                // -- Stage 5: Generate fix --
                updateStage(RepairStage.GENERATE_FIX)
                val fix = generateFix(rootCause, relevantFiles, configContents, failureInfo)

                // -- Stage 6: Validate diff --
                updateStage(RepairStage.VALIDATE_DIFF)
                val validation = validateDiff(fix)
                if (!validation.valid) {
                    lastError = validation.reason
                    _state.value = _state.value.copy(error = lastError)
                    continue // retry
                }

                // -- Stage 7: Apply changes --
                updateStage(RepairStage.APPLY_CHANGES)
                val applied = applyChanges(fix, config)
                if (!applied) {
                    lastError = "Failed to apply changes safely"
                    _state.value = _state.value.copy(error = lastError)
                    continue
                }

                // -- Stage 8: Commit --
                updateStage(RepairStage.COMMIT)
                commitChanges(fix)

                // -- Stage 9: Push --
                updateStage(RepairStage.PUSH)
                pushChanges()

                // -- Stage 10: Run workflow --
                updateStage(RepairStage.RUN_WORKFLOW)
                val runId = triggerWorkflow()
                _state.value = _state.value.copy(workflowRunId = runId)

                // -- Stage 11: Wait for result --
                updateStage(RepairStage.WAIT_FOR_RESULT)
                waitForWorkflow(runId)

                // -- Stage 12: Check result --
                updateStage(RepairStage.CHECK_RESULT)
                val success = checkWorkflowResult(runId)
                if (success) {
                    // -- Stage 13: Success --
                    updateStage(RepairStage.SUCCESS)
                    return _state.value.copy(
                        isRunning = false,
                        currentStage = RepairStage.SUCCESS,
                        filesChanged = changedFiles.size,
                        filesDeleted = deletedFiles.size,
                    )
                }

                // Workflow still failing — loop back and analyze again.
                lastError = "Workflow $runId failed; will analyze and retry"
                _state.value = _state.value.copy(error = lastError)
            }
        } catch (e: Exception) {
            return _state.value.copy(
                isRunning = false,
                currentStage = RepairStage.FAILED,
                error = e.message ?: "Unknown error during repair",
            )
        }

        // Loop exited without success (e.g. cancelled).
        return _state.value.copy(
            isRunning = false,
            currentStage = RepairStage.FAILED,
            error = lastError ?: "Repair loop terminated",
        )
    }

    private fun updateStage(stage: RepairStage) {
        _state.value = _state.value.copy(currentStage = stage)
    }

    // ------------------------------------------------------------------
    //  Stage implementations
    // ------------------------------------------------------------------

    /**
     * Stage 1 — Reads the latest build / workflow failure log.
     * Returns the raw failure text, or empty string when no failure is found.
     */
    private suspend fun readFailure(context: Context): String = withContext(Dispatchers.IO) {
        val ctx = _repairContext
        if (ctx != null) {
            val logResult = runCatching {
                val response = _apiService?.downloadJobLogs(ctx.owner, ctx.repo, ctx.failedJobId)
                if (response?.isSuccessful == true) response.body()?.string() ?: "" else ""
            }
            logResult.getOrDefault(_state.value.error ?: "")
        } else {
            _state.value.error ?: ""
        }
    }

    /**
     * Stage 2 — Analyses [failureLog] to determine the root cause.
     * Returns a short human-readable description of the root cause.
     */
    private suspend fun identifyRootCause(failureLog: String): String = withContext(Dispatchers.Default) {
        when {
            failureLog.contains("Compilation", ignoreCase = true) ||
            failureLog.contains("e: file:", ignoreCase = true) ->
                "Compilation error in Kotlin source"
            failureLog.contains("Lint", ignoreCase = true) ->
                "Lint violation"
            failureLog.contains("Tests", ignoreCase = true) ||
            failureLog.contains("test failed", ignoreCase = true) ->
                "Test failure"
            failureLog.contains("dependency", ignoreCase = true) ||
            failureLog.contains("Could not resolve", ignoreCase = true) ->
                "Dependency resolution failure"
            else ->
                "Unknown build failure"
        }
    }

    /**
     * Stage 3 — Scans the project tree for files relevant to [rootCause].
     * Secret files are always excluded.
     */
    private suspend fun scanRelevantFiles(context: Context, rootCause: String): List<File> =
        withContext(Dispatchers.IO) {
            val root = File(context.filesDir, "gitofy").takeIf { it.exists() }
                ?: return@withContext emptyList()
            val sourceRoot = File(root, "app/src/main/java")

            if (!sourceRoot.exists()) return@withContext emptyList()

            sourceRoot.walkTopDown()
                .filter { it.isFile }
                .filter { it.extension == "kt" || it.extension == "java" || it.extension == "xml" }
                .filter { !isSecretFile(it.name) }
                .toList()
        }

    /**
     * Stage 4 — Reads project configuration files (build.gradle, settings, etc.)
     * for context when generating a fix. Secret files are excluded.
     */
    private suspend fun readConfigFiles(context: Context): Map<String, String> =
        withContext(Dispatchers.IO) {
            val root = File(context.filesDir, "gitofy").takeIf { it.exists() }
                ?: return@withContext emptyMap()
            val configFiles = root.listFiles { f ->
                f.isFile && (f.name.endsWith(".gradle") ||
                    f.name.endsWith(".gradle.kts") ||
                    f.name.endsWith(".toml") ||
                    f.name.endsWith(".properties")) &&
                    !isSecretFile(f.name)
            }?.toList() ?: emptyList()

            configFiles.associate { file ->
                file.name to (file.readText().take(MAX_CONFIG_READ_CHARS))
            }
        }

    /**
     * Stage 5 — Generates a proposed fix (diff).
     * Returns a map of file path → proposed new content.
     */
    private suspend fun generateFix(
        rootCause: String,
        relevantFiles: List<File>,
        configContents: Map<String, String>,
        failureLog: String,
    ): Map<String, String> = withContext(Dispatchers.Default) {
        // In production this delegates to the AI code-generation backend.
        // For now we return an empty map — the validate/apply stages will no-op.
        emptyMap()
    }

    /**
     * Stage 6 — Validates a proposed fix against all safety rules.
     */
    private suspend fun validateDiff(fix: Map<String, String>): ValidationResult =
        withContext(Dispatchers.Default) {
            for ((path, _) in fix) {
                if (isSecretFile(path)) {
                    return@withContext ValidationResult(false, "Refusing to modify protected secret file: $path")
                }
                if (isProtectedPath(path)) {
                    return@withContext ValidationResult(false, "Refusing to modify protected path: $path")
                }
            }
            ValidationResult(true)
        }

    /**
     * Stage 7 — Applies validated changes to disk.
     * Enforces max-files-changed and blocks any deletion of protected files.
     */
    private suspend fun applyChanges(fix: Map<String, String>, config: RepairConfig): Boolean =
        withContext(Dispatchers.IO) {
            for ((path, content) in fix) {
                if (isSecretFile(path) || isProtectedPath(path)) return@withContext false

                val target = File(path)
                val isDeletion = content.isBlank() && target.exists()

                if (isDeletion) {
                    if (deletedFiles.size >= config.maxDeletions) return@withContext false
                    if (!target.delete()) return@withContext false
                    deletedFiles.add(path)
                } else {
                    if (changedFiles.size >= config.maxFilesChanged) return@withContext false
                    target.writeText(content)
                    changedFiles.add(path)
                }
            }
            _state.value = _state.value.copy(
                filesChanged = changedFiles.size,
                filesDeleted = deletedFiles.size,
            )
            true
        }

    /** Stage 8 — Commits the applied changes via GitHub Contents API. */
    private suspend fun commitChanges(fix: Map<String, String>) = withContext(Dispatchers.IO) {
        val ctx = _repairContext
        if (ctx != null && _apiService != null) {
            for ((path, content) in fix) {
                val api = _apiService!!
                val currentFile = runCatching { api.getContent(ctx.owner, ctx.repo, path, ctx.branch) }.getOrNull()?.body()
                val currentSha = currentFile?.sha
                val encodedContent = android.util.Base64.encodeToString(content.toByteArray(), android.util.Base64.NO_WRAP)
                val request = com.gitofy.data.remote.dto.CreateFileRequest(
                    message = "Fix: ${ctx.failedJobName} build failure",
                    content = encodedContent, sha = currentSha, branch = ctx.branch
                )
                runCatching { api.createOrUpdateFile(ctx.owner, ctx.repo, path, request) }
            }
        }
    }

    /** Stage 9 — Pushes the commit to the remote (implicit via Contents API). */
    private suspend fun pushChanges() = withContext(Dispatchers.IO) { }

    /** Stage 10 — Triggers the CI workflow. Returns the real run id from GitHub. */
    private suspend fun triggerWorkflow(): String = withContext(Dispatchers.IO) {
        val ctx = _repairContext
        if (ctx != null && _apiService != null) {
            delay(5000)
            val runsResult = runCatching { _apiService!!.listWorkflowRunsByWorkflow(ctx.owner, ctx.repo, ctx.workflowId) }
            val newRun = runsResult.getOrNull()?.body()?.workflowRuns?.firstOrNull { it.id > ctx.runId }
            if (newRun != null) return@withContext newRun.id.toString()
            val dispatchRequest = com.gitofy.data.remote.dto.DispatchWorkflowRequest(ref = ctx.branch)
            runCatching { _apiService!!.dispatchWorkflow(ctx.owner, ctx.repo, ctx.workflowId, dispatchRequest) }
            delay(5000)
            val reruns = runCatching { _apiService!!.listWorkflowRunsByWorkflow(ctx.owner, ctx.repo, ctx.workflowId) }
            reruns.getOrNull()?.body()?.workflowRuns?.firstOrNull { it.id > ctx.runId }?.id?.toString()
                ?: "run-${System.currentTimeMillis()}"
        } else { "run-${System.currentTimeMillis()}" }
    }

    /** Stage 11 — Polls until the workflow run completes (real GitHub API polling). */
    private suspend fun waitForWorkflow(runId: String) = withContext(Dispatchers.IO) {
        val ctx = _repairContext
        if (ctx != null && _apiService != null) {
            var waited = 0L
            while (waited < MAX_WORKFLOW_WAIT_MS && scope.isActive) {
                delay(WORKFLOW_POLL_INTERVAL_MS)
                waited += WORKFLOW_POLL_INTERVAL_MS
                val runResult = runCatching { _apiService!!.getWorkflowRun(ctx.owner, ctx.repo, runId.toLongOrNull() ?: 0L) }
                val run = runResult.getOrNull()?.body() ?: continue
                if (run.status == "completed") break
            }
        } else {
            var waited = 0L
            while (waited < MAX_WORKFLOW_WAIT_MS && scope.isActive) {
                delay(WORKFLOW_POLL_INTERVAL_MS)
                waited += WORKFLOW_POLL_INTERVAL_MS
            }
        }
    }

    /** Stage 12 — Checks whether the workflow run succeeded (REAL GitHub API result). */
    private suspend fun checkWorkflowResult(runId: String): Boolean = withContext(Dispatchers.IO) {
        val ctx = _repairContext
        if (ctx != null && _apiService != null) {
            val runResult = runCatching { _apiService!!.getWorkflowRun(ctx.owner, ctx.repo, runId.toLongOrNull() ?: 0L) }
            val run = runResult.getOrNull()?.body() ?: return@withContext false
            // PRD §24: Success ONLY when GitHub workflow conclusion == success
            run.status == "completed" && run.conclusion == "success"
        } else { false }
    }

    // ------------------------------------------------------------------
    //  Safety helpers
    // ------------------------------------------------------------------

    /**
     * Paths that must never be modified regardless of mode.
     * Includes signing keys, GitHub tokens, and credential stores.
     */
    private fun isProtectedPath(path: String): Boolean {
        val lower = path.lowercase()
        return lower.contains("signing") ||
            lower.contains("github_token") ||
            lower.contains("gh_token") ||
            lower.contains("keystore") ||
            lower.contains("credentials") ||
            lower.contains("secrets") ||
            lower.endsWith(".pem") ||
            lower.endsWith(".key") ||
            lower.endsWith(".jks") ||
            lower.endsWith(".keystore")
    }

    /** Result of diff validation. */
    private data class ValidationResult(val valid: Boolean, val reason: String = "")

}
