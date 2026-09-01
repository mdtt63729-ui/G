package com.gitofy.feature.ai

import android.content.Context
import androidx.lifecycle.ViewModel
import com.gitofy.ai.autonomous.GitoRepairOrchestrator
import com.gitofy.ai.autonomous.RepairAnalyzer
import com.gitofy.ai.security.LogRedactor
import com.gitofy.core.security.SecureCredentialStorage
import com.gitofy.data.local.dao.GitoRepairJobDao
import com.gitofy.data.local.dao.GitoRepairAttemptDao
import com.gitofy.domain.repository.WorkflowRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GitoRepairViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val orchestrator: GitoRepairOrchestrator,
    private val workflowRepository: WorkflowRepository,
    private val logRedactor: LogRedactor,
    private val secureStorage: SecureCredentialStorage,
    private val repairJobDao: GitoRepairJobDao,
    private val repairAttemptDao: GitoRepairAttemptDao
) : ViewModel() {

    val repairState: StateFlow<GitoRepairOrchestrator.RepairUiState> = orchestrator.state

    private val _uiState = MutableStateFlow(GitoRepairScreenUiState())
    val uiState: StateFlow<GitoRepairScreenUiState> = _uiState.asStateFlow()

    fun startRepair(owner: String, repo: String, runId: Long, failedJobId: Long,
                    workflowId: String, branch: String, commitSha: String,
                    failedJobName: String, failedStepName: String) {
        val repairId = "repair_${System.currentTimeMillis()}_${failedJobId}"
        val context = GitoRepairOrchestrator.RepairContext(
            repairId = repairId, owner = owner, repo = repo, branch = branch,
            commitSha = commitSha, workflowId = workflowId, runId = runId,
            failedJobId = failedJobId, failedJobName = failedJobName, failedStepName = failedStepName
        )
        orchestrator.startRepair(context, createRepairAnalyzer())
    }

    fun restoreState(repairId: String) {
        kotlinx.coroutines.GlobalScope.launch { orchestrator.restoreFromPersistentState(repairId) }
    }

    fun retry() {
        val ctx = repairState.value.context ?: return
        val newRepairId = "repair_${System.currentTimeMillis()}_${ctx.failedJobId}"
        orchestrator.startRepair(ctx.copy(repairId = newRepairId), createRepairAnalyzer())
    }

    fun cancelRepair() { orchestrator.cancel() }
    fun dismissError() { _uiState.update { it.copy(showError = false) } }

    private fun createRepairAnalyzer(): RepairAnalyzer = AiRepairAnalyzerImpl()

    override fun onCleared() {
        super.onCleared()
    }
}

data class GitoRepairScreenUiState(val showError: Boolean = false, val showFullLogs: Boolean = false)

private class AiRepairAnalyzerImpl : RepairAnalyzer {
    override suspend fun analyzeFailure(redactedLog: String, context: GitoRepairOrchestrator.RepairContext): GitoRepairOrchestrator.FailureAnalysis {
        val errorType = when {
            redactedLog.contains("Duplicate resources", ignoreCase = true) -> "DUPLICATE_RESOURCE"
            redactedLog.contains("Compilation", ignoreCase = true) || redactedLog.contains("e: file:", ignoreCase = true) -> "COMPILATION_ERROR"
            redactedLog.contains("HiltViewModel", ignoreCase = true) || redactedLog.contains("Hilt", ignoreCase = true) -> "HILT_INJECTION_ERROR"
            redactedLog.contains("Could not resolve", ignoreCase = true) -> "DEPENDENCY_RESOLUTION"
            else -> "UNKNOWN_BUILD_ERROR"
        }
        val rootCause = when (errorType) {
            "DUPLICATE_RESOURCE" -> {
                val regex = Regex("\\[([^]]+)]")
                "Duplicate resource: ${regex.find(redactedLog)?.groupValues?.getOrNull(1) ?: "unknown"}"
            }
            "COMPILATION_ERROR" -> {
                val errorLines = redactedLog.lines().filter { it.contains("e: file:", ignoreCase = true) }
                "Compilation error in ${errorLines.firstOrNull()?.take(100) ?: "source file"}"
            }
            "HILT_INJECTION_ERROR" -> "Hilt ViewModel injection error"
            "DEPENDENCY_RESOLUTION" -> "Dependency resolution failure"
            else -> "Build failure detected in logs"
        }
        val relevantPaths = when (errorType) {
            "DUPLICATE_RESOURCE" -> {
                val resRegex = Regex("res/[^\\s]+")
                val matches = resRegex.findAll(redactedLog).map { it.value }.distinct().toList()
                if (matches.isEmpty()) listOf("app/src/main/res/drawable/") else matches
            }
            "COMPILATION_ERROR" -> {
                val fileRegex = Regex("app/src/main/java/[^\\s:]+\\.kt")
                val matches = fileRegex.findAll(redactedLog).map { it.value }.distinct().toList()
                if (matches.isEmpty()) listOf("app/src/main/java/") else matches
            }
            "HILT_INJECTION_ERROR" -> listOf("app/src/main/java/com/gitofy/GITOFYApp.kt", "app/src/main/java/com/gitofy/MainActivity.kt")
            else -> listOf("app/build.gradle.kts", "app/src/main/AndroidManifest.xml")
        }
        return GitoRepairOrchestrator.FailureAnalysis(errorType = errorType, rootCause = rootCause, relevantFilePaths = relevantPaths)
    }

    override suspend fun planFix(analysis: GitoRepairOrchestrator.FailureAnalysis, repoFiles: Map<String, String>, redactedLog: String, context: GitoRepairOrchestrator.RepairContext): GitoRepairOrchestrator.FixPlan {
        val modifiedFiles = mutableMapOf<String, String>()
        when (analysis.errorType) {
            "DUPLICATE_RESOURCE" -> {
                val drawableFiles = repoFiles.filterKeys { it.contains("drawable/") }
                val duplicates = drawableFiles.keys.groupBy { it.substringAfterLast("/").substringBeforeLast(".") }.filter { it.value.size > 1 }
                for ((_, paths) in duplicates) {
                    for (path in paths.drop(1)) { modifiedFiles[path] = "__DELETE__" }
                }
            }
            "HILT_INJECTION_ERROR" -> {
                val appKt = repoFiles["app/src/main/java/com/gitofy/GITOFYApp.kt"]
                if (appKt != null && !appKt.contains("@HiltAndroidApp")) {
                    modifiedFiles["app/src/main/java/com/gitofy/GITOFYApp.kt"] = appKt.replace("class GITOFYApp", "@HiltAndroidApp\nclass GITOFYApp")
                }
            }
            else -> {}
        }
        return GitoRepairOrchestrator.FixPlan(description = analysis.rootCause, modifiedFiles = modifiedFiles, commitMessage = "Fix: ${analysis.rootCause.take(60)}")
    }
}
