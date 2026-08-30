package com.gitofy.ai.feature

import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI Chat — PRD Section 27.
 * Gito supports: repository, build, workflow, code, logs, commits, PRs questions.
 * Conversation context must be scoped: Repository, Workflow, Job, File/Selection, General.
 */
@Singleton
class AIChatUseCase @Inject constructor(
    private val gateway: com.gitofy.ai.gateway.AIGateway
) {

    data class ChatRequest(
        val message: String,
        val scope: ChatScope,
        val scopeResourceId: String?
    )

    sealed class ChatScope(val displayName: String) {
        data object Repository : ChatScope("Current Repository")
        data object Workflow : ChatScope("Current Workflow")
        data object Job : ChatScope("Current Job")
        data object File : ChatScope("Current File/Selection")
        data object General : ChatScope("General")
    }

    suspend fun chat(request: ChatRequest, contextData: Map<String, String>): Result<com.gitofy.ai.gateway.AIGateway.GatewayResponse> {
        val gatewayRequest = com.gitofy.ai.gateway.AIGateway.GatewayRequest(
            taskType = com.gitofy.ai.model.AITaskType.GENERAL_QA,
            userPrompt = request.message,
            contextData = contextData,
            costBudget = com.gitofy.ai.gateway.AIGateway.CostBudget.FREE_FIRST
        )
        return gateway.process(gatewayRequest)
    }
}

/**
 * AI Build Failure Analysis — PRD Section 28.
 * Flow: Workflow Failed → Failed Job → Failed Step → Relevant Logs →
 * Recent Commits → Changed Files → Gradle Configuration → AI
 * Output: Root Cause, Evidence, Affected Files, Confidence, Recommended Fix, Potential Side Effects
 */
@Singleton
class AIBuildFailureAnalysisUseCase @Inject constructor(
    private val gateway: com.gitofy.ai.gateway.AIGateway,
    private val failureInspector: com.gitofy.feature.ci.CIFailureInspector
) {

    data class FailureAnalysisRequest(
        val repoOwner: String,
        val repoName: String,
        val workflowName: String,
        val jobName: String,
        val failedStepName: String,
        val logs: String,
        val recentCommits: List<String>,
        val changedFiles: List<String>,
        val gradleConfig: String?
    )

    suspend fun analyze(request: FailureAnalysisRequest): Result<com.gitofy.ai.model.AIDiagnosis> {
        // Step 1: Deterministic pattern matching (Priority — PRD Section 136)
        val deterministic = failureInspector.analyze(request.logs)

        // Step 2: Build AI context
        val contextData = mapOf(
            "workflow" to request.workflowName,
            "failed_job" to request.jobName,
            "failed_step" to request.failedStepName,
            "logs" to request.logs.take(5000),
            "recent_commits" to request.recentCommits.joinToString("\n"),
            "changed_files" to request.changedFiles.joinToString("\n"),
            "gradle_config" to (request.gradleConfig ?: "N/A")
        )

        // Step 3: AI analysis via gateway
        val gatewayResult = gateway.process(
            com.gitofy.ai.gateway.AIGateway.GatewayRequest(
                taskType = com.gitofy.ai.model.AITaskType.BUILD_FAILURE_ANALYSIS,
                userPrompt = "Analyze this build failure. Workflow: ${request.workflowName}, Job: ${request.jobName}, Step: ${request.failedStepName}",
                contextData = contextData,
                costBudget = com.gitofy.ai.gateway.AIGateway.CostBudget.FREE_FIRST
            )
        )

        return gatewayResult.map { response ->
            val confidence = when (deterministic.category) {
                com.gitofy.feature.ci.CIFailureInspector.FailureCategory.UNKNOWN -> com.gitofy.ai.model.AIConfidenceLevel.LOW
                com.gitofy.feature.ci.CIFailureInspector.FailureCategory.COMPILATION,
                com.gitofy.feature.ci.CIFailureInspector.FailureCategory.KOTLIN,
                com.gitofy.feature.ci.CIFailureInspector.FailureCategory.TEST -> com.gitofy.ai.model.AIConfidenceLevel.CONFIRMED
                else -> com.gitofy.ai.model.AIConfidenceLevel.HIGH
            }

            com.gitofy.ai.model.AIDiagnosis(
                rootCause = deterministic.category.name.lowercase().replace("_", " "),
                evidence = listOf(deterministic.relevantLog, response.content).filter { it.isNotBlank() },
                affectedFiles = request.changedFiles,
                confidence = confidence,
                recommendedFix = deterministic.suggestedAction,
                potentialSideEffects = emptyList(),
                isObserved = true,
                isInferred = confidence != com.gitofy.ai.model.AIConfidenceLevel.CONFIRMED,
                isSuggested = true
            )
        }
    }
}

/**
 * AI Code Review — PRD Section 30.
 * AI may review: uncommitted changes, commit diff, PR diff, selected files.
 * Output: Critical Issues, Warnings, Maintainability, Performance, Security, Testing, Suggestions
 */
@Singleton
class AICodeReviewUseCase @Inject constructor(
    private val gateway: com.gitofy.ai.gateway.AIGateway
) {

    data class ReviewRequest(
        val fileName: String,
        val fileContent: String,
        val diff: String?
    )

    data class ReviewResult(
        val criticalIssues: List<String>,
        val warnings: List<String>,
        val maintainability: List<String>,
        val performance: List<String>,
        val security: List<String>,
        val testing: List<String>,
        val suggestions: List<String>,
        val isAdvisory: Boolean = true
    )

    suspend fun review(request: ReviewRequest): Result<ReviewResult> {
        val contextData = mapOf(
            "file_name" to request.fileName,
            "file_content" to request.fileContent.take(8000),
            "diff" to (request.diff ?: "No diff provided")
        )

        val result = gateway.process(
            com.gitofy.ai.gateway.AIGateway.GatewayRequest(
                taskType = com.gitofy.ai.model.AITaskType.CODE_REVIEW,
                userPrompt = "Review this code file: ${request.fileName}",
                contextData = contextData
            )
        )

        return result.map { response ->
            ReviewResult(
                criticalIssues = emptyList(),
                warnings = emptyList(),
                maintainability = emptyList(),
                performance = emptyList(),
                security = emptyList(),
                testing = emptyList(),
                suggestions = listOf(response.content),
                isAdvisory = true
            )
        }
    }
}

/**
 * AI Patch Generation — PRD Section 31.
 * Pipeline: Problem → Context → AI Proposed Patch → Patch Validation → Diff Preview →
 * User Approval → Apply → Optional Test → Commit
 * AI must NEVER silently modify repository files.
 */
@Singleton
class AIPatchGenerationUseCase @Inject constructor(
    private val gateway: com.gitofy.ai.gateway.AIGateway,
    private val patchSafety: com.gitofy.ai.action.PatchSafety
) {

    data class PatchRequest(
        val problem: String,
        val fileName: String,
        val fileContent: String,
        val contextFiles: Map<String, String> = emptyMap()
    )

    data class PatchResult(
        val patch: String,
        val diffPreview: String,
        val isValid: Boolean,
        val validationErrors: List<String>,
        val requiresApproval: Boolean = true
    )

    suspend fun generate(request: PatchRequest): Result<PatchResult> {
        val contextData = mutableMapOf(
            "problem" to request.problem,
            "target_file" to request.fileName,
            "file_content" to request.fileContent.take(8000)
        )
        contextData.putAll(request.contextFiles.mapValues { it.value.take(4000) })

        val result = gateway.process(
            com.gitofy.ai.gateway.AIGateway.GatewayRequest(
                taskType = com.gitofy.ai.model.AITaskType.PATCH_GENERATION,
                userPrompt = "Generate a patch to fix: ${request.problem}",
                contextData = contextData
            )
        )

        return result.map { response ->
            val patch = response.content
            val validation = patchSafety.validatePatch(request.fileName, patch)
            val application = patchSafety.applyPatch(request.fileContent, patch)

            PatchResult(
                patch = patch,
                diffPreview = application.getOrNull()?.diffPreview ?: "",
                isValid = validation.isValid,
                validationErrors = validation.errors,
                requiresApproval = true
            )
        }
    }
}

/**
 * AI PR Generation — PRD Section 38.
 * AI may generate: PR Title, Summary, Changes, Testing, Potential Risks, Screenshots/Evidence
 * User can edit before creation.
 */
@Singleton
class AIPRGenerationUseCase @Inject constructor(
    private val gateway: com.gitofy.ai.gateway.AIGateway
) {

    data class PRGenerationRequest(
        val branchName: String,
        val targetBranch: String,
        val commits: List<String>,
        val changedFiles: List<String>,
        val diffSummary: String
    )

    data class PRContent(
        val title: String,
        val summary: String,
        val changes: String,
        val testing: String,
        val potentialRisks: String,
        val isEditable: Boolean = true
    )

    suspend fun generate(request: PRGenerationRequest): Result<PRContent> {
        val contextData = mapOf(
            "branch" to request.branchName,
            "target_branch" to request.targetBranch,
            "commits" to request.commits.joinToString("\n"),
            "changed_files" to request.changedFiles.joinToString("\n"),
            "diff_summary" to request.diffSummary.take(3000)
        )

        val result = gateway.process(
            com.gitofy.ai.gateway.AIGateway.GatewayRequest(
                taskType = com.gitofy.ai.model.AITaskType.PR_GENERATION,
                userPrompt = "Generate a pull request description for branch ${request.branchName} → ${request.targetBranch}",
                contextData = contextData
            )
        )

        return result.map { response ->
            PRContent(
                title = "AI-generated PR for ${request.branchName}",
                summary = response.content,
                changes = request.changedFiles.joinToString("\n"),
                testing = "Tests: Pending verification",
                potentialRisks = "See AI analysis",
                isEditable = true
            )
        }
    }
}

/**
 * AI Commit Generation — PRD Section 39.
 * AI may analyze staged changes and generate commit message.
 * User must approve the commit before creation.
 */
@Singleton
class AICommitGenerationUseCase @Inject constructor(
    private val gateway: com.gitofy.ai.gateway.AIGateway
) {

    suspend fun generate(stagedFiles: List<String>, diff: String): Result<String> {
        val contextData = mapOf(
            "staged_files" to stagedFiles.joinToString("\n"),
            "diff" to diff.take(3000)
        )

        val result = gateway.process(
            com.gitofy.ai.gateway.AIGateway.GatewayRequest(
                taskType = com.gitofy.ai.model.AITaskType.COMMIT_MESSAGE,
                userPrompt = "Generate a commit message for these changes",
                contextData = contextData
            )
        )

        return result.map { it.content }
    }
}

/**
 * AI Workflow Analysis & Generation — PRD Sections 40-41.
 * AI can inspect workflow YAML and identify issues.
 * AI may generate a GitHub Actions workflow from a request.
 * Pipeline: Generate YAML → Syntax validation → Security validation → Diff → User approval → Commit
 */
@Singleton
class AIWorkflowUseCase @Inject constructor(
    private val gateway: com.gitofy.ai.gateway.AIGateway,
    private val yamlValidator: com.gitofy.feature.ci.WorkflowYamlValidator
) {

    data class WorkflowAnalysis(
        val slowSteps: List<String>,
        val duplicateActions: List<String>,
        val missingCaching: Boolean,
        val incorrectPermissions: List<String>,
        val dependencyProblems: List<String>,
        val securityRisks: List<String>
    )

    suspend fun analyze(yamlContent: String): Result<WorkflowAnalysis> {
        val result = gateway.process(
            com.gitofy.ai.gateway.AIGateway.GatewayRequest(
                taskType = com.gitofy.ai.model.AITaskType.WORKFLOW_ANALYSIS,
                userPrompt = "Analyze this GitHub Actions workflow YAML for issues",
                contextData = mapOf("yaml" to yamlContent)
            )
        )

        return result.map { response ->
            WorkflowAnalysis(
                slowSteps = emptyList(),
                duplicateActions = emptyList(),
                missingCaching = !yamlContent.contains("cache:"),
                incorrectPermissions = if (yamlContent.contains("permissions: write-all")) listOf("Excessive permissions") else emptyList(),
                dependencyProblems = emptyList(),
                securityRisks = listOf(response.content)
            )
        }
    }

    suspend fun generate(description: String): Result<String> {
        val result = gateway.process(
            com.gitofy.ai.gateway.AIGateway.GatewayRequest(
                taskType = com.gitofy.ai.model.AITaskType.WORKFLOW_GENERATION,
                userPrompt = "Generate a GitHub Actions workflow YAML for: $description",
                contextData = emptyMap()
            )
        )

        return result.map { response ->
            // Validate generated YAML
            val validation = yamlValidator.validate(response.content)
            if (!validation.isValid) {
                "# WARNING: Generated YAML has validation errors\n# ${validation.errors.joinToString("; ")}\n\n${response.content}"
            } else {
                response.content
            }
        }
    }
}

/**
 * AI Vision System — PRD Sections 42-43.
 * Vision-capable providers may analyze: UI screenshots, error screenshots, workflow screenshots,
 * architecture diagrams, wireframes.
 * Screenshot-to-Compose: Generate Jetpack Compose, Material 3, responsive layout, accessibility semantics.
 * Generated code must be treated as a proposal and reviewed before being applied.
 */
@Singleton
class AIVisionUseCase @Inject constructor(
    private val gateway: com.gitofy.ai.gateway.AIGateway
) {

    data class VisionAnalysis(
        val description: String,
        val uiStructure: String,
        val composeSuggestion: String?,
        val isProposal: Boolean = true
    )

    suspend fun analyzeScreenshot(screenshot: ByteArray, question: String): Result<VisionAnalysis> {
        val result = gateway.process(
            com.gitofy.ai.gateway.AIGateway.GatewayRequest(
                taskType = com.gitofy.ai.model.AITaskType.VISION_UI_ANALYSIS,
                userPrompt = question,
                contextData = emptyMap(),
                attachments = listOf(screenshot),
                requireVision = true
            )
        )

        return result.map { response ->
            VisionAnalysis(
                description = response.content,
                uiStructure = "UI structure analysis: See AI response",
                composeSuggestion = if (question.contains("compose", ignoreCase = true)) response.content else null,
                isProposal = true
            )
        }
    }
}

/**
 * AI Documentation Generation — PRD Section 131.
 * AI may generate: README sections, release notes, commit messages, PR descriptions,
 * changelog entries, workflow documentation. All user-reviewable.
 */
@Singleton
class AIDocumentationUseCase @Inject constructor(
    private val gateway: com.gitofy.ai.gateway.AIGateway
) {

    suspend fun generateReadme(projectInfo: Map<String, String>): Result<String> {
        val result = gateway.process(
            com.gitofy.ai.gateway.AIGateway.GatewayRequest(
                taskType = com.gitofy.ai.model.AITaskType.DOCUMENTATION,
                userPrompt = "Generate a README.md section for this project",
                contextData = projectInfo
            )
        )
        return result.map { it.content }
    }

    suspend fun generateReleaseNotes(version: String, commits: List<String>, changes: List<String>): Result<String> {
        val result = gateway.process(
            com.gitofy.ai.gateway.AIGateway.GatewayRequest(
                taskType = com.gitofy.ai.model.AITaskType.DOCUMENTATION,
                userPrompt = "Generate release notes for version $version",
                contextData = mapOf("commits" to commits.joinToString("\n"), "changes" to changes.joinToString("\n"))
            )
        )
        return result.map { it.content }
    }
}

/**
 * AI Test Generation — PRD Section 132.
 * AI may generate: Kotlin unit tests, Compose UI tests, GitHub Actions test steps, Regression tests.
 * Tests must be shown as proposed changes.
 */
@Singleton
class AITestGenerationUseCase @Inject constructor(
    private val gateway: com.gitofy.ai.gateway.AIGateway
) {

    suspend fun generateUnitTests(fileName: String, fileContent: String): Result<String> {
        val result = gateway.process(
            com.gitofy.ai.gateway.AIGateway.GatewayRequest(
                taskType = com.gitofy.ai.model.AITaskType.TEST_GENERATION,
                userPrompt = "Generate Kotlin unit tests for: $fileName",
                contextData = mapOf("source_file" to fileContent.take(8000))
            )
        )
        return result.map { it.content }
    }
}

/**
 * AI + GitHub Actions Closed Loop — PRD Section 59.
 * Code Push → GitHub Actions → Build Failure → GITOFY Detects → AI Analyzes →
 * Fix Proposal → User Approval → Patch → Commit → Push → Workflow Trigger → Build Result
 */
@Singleton
class AIClosedLoopUseCase @Inject constructor(
    private val failureAnalysis: AIBuildFailureAnalysisUseCase,
    private val patchGeneration: AIPatchGenerationUseCase,
    private val branchStrategy: com.gitofy.ai.action.AIBranchStrategy
) {

    data class ClosedLoopState(
        val phase: Phase,
        val failureAnalysis: com.gitofy.ai.model.AIDiagnosis?,
        val patch: com.gitofy.ai.action.PatchSafety.PatchApplication?,
        val branchName: String?,
        val workflowResult: String?
    ) {
        enum class Phase {
            DETECT_FAILURE, AI_ANALYZE, FIX_PROPOSAL, USER_APPROVAL,
            PATCH_APPLIED, COMMITTED, PUSHED, WORKFLOW_TRIGGERED,
            WORKFLOW_SUCCESS, WORKFLOW_FAILED, STOPPED
        }
    }

    /**
     * AI Self-Verification — PRD Section 60.
     * AI must NOT claim "Fixed successfully" until verification confirms the result.
     * Instead: "Patch generated, verification pending" or "Patch applied, CI verification passed"
     */
    fun getVerificationStatus(phase: ClosedLoopState.Phase): String {
        return when (phase) {
            ClosedLoopState.Phase.PATCH_APPLIED -> "Patch applied. Verification pending."
            ClosedLoopState.Phase.WORKFLOW_TRIGGERED -> "Workflow triggered. Waiting for CI verification."
            ClosedLoopState.Phase.WORKFLOW_SUCCESS -> "Patch applied. CI verification passed."
            ClosedLoopState.Phase.WORKFLOW_FAILED -> "Patch applied but CI verification failed. Regression may exist."
            else -> "In progress: ${phase.name}"
        }
    }

    /**
     * AI Regression Detection — PRD Section 61.
     * When a build fails after an AI-generated change, identify whether the change is responsible.
     */
    fun detectRegression(
        changedFiles: List<String>,
        currentFailure: String,
        previousSuccess: Boolean
    ): String {
        return if (previousSuccess) {
            "Build was previously successful. The AI-generated change to ${changedFiles.joinToString()} may be responsible for this failure."
        } else {
            "Build was already failing before the AI-generated change. The change may or may not be related."
        }
    }
}
