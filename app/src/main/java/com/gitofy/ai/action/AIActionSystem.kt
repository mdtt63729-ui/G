package com.gitofy.ai.action

import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI Action Types — PRD Section 33.
 * READ, ANALYZE, SUGGEST, GENERATE, MODIFY_LOCAL, COMMIT, PUSH,
 * TRIGGER_WORKFLOW, CREATE_PR, MERGE_PR, CANCEL_WORKFLOW
 *
 * Risk levels: LOW, MEDIUM, HIGH, CRITICAL
 */
enum class AIActionType(val riskLevel: RiskLevel) {
    READ(RiskLevel.LOW),
    ANALYZE(RiskLevel.LOW),
    SUGGEST(RiskLevel.LOW),
    GENERATE(RiskLevel.LOW),
    MODIFY_LOCAL(RiskLevel.MEDIUM),
    COMMIT(RiskLevel.MEDIUM),
    PUSH(RiskLevel.HIGH),
    TRIGGER_WORKFLOW(RiskLevel.HIGH),
    CREATE_PR(RiskLevel.HIGH),
    MERGE_PR(RiskLevel.CRITICAL),
    CANCEL_WORKFLOW(RiskLevel.MEDIUM);

    enum class RiskLevel { LOW, MEDIUM, HIGH, CRITICAL }
}

/**
 * AI Permission Policy — PRD Section 34.
 * Default:
 * READ → automatic, ANALYZE → automatic, SUGGEST → automatic, GENERATE → automatic
 * MODIFY_LOCAL → approval, COMMIT → approval, PUSH → approval
 * TRIGGER_WORKFLOW → approval, MERGE → explicit approval
 *
 * No AI action should bypass GitHub authorization.
 */
@Singleton
class AIPermissionPolicy @Inject constructor() {

    fun requiresApproval(actionType: AIActionType): Boolean {
        return when (actionType) {
            AIActionType.READ, AIActionType.ANALYZE, AIActionType.SUGGEST, AIActionType.GENERATE -> false
            AIActionType.MODIFY_LOCAL, AIActionType.COMMIT, AIActionType.PUSH,
            AIActionType.TRIGGER_WORKFLOW, AIActionType.CREATE_PR, AIActionType.CANCEL_WORKFLOW -> true
            AIActionType.MERGE_PR -> true // explicit approval required
        }
    }

    fun isAutoApproved(actionType: AIActionType): Boolean = !requiresApproval(actionType)

    data class PermissionCheck(
        val actionType: AIActionType,
        val requiresApproval: Boolean,
        val riskLevel: AIActionType.RiskLevel,
        val reason: String
    )

    fun check(actionType: AIActionType): PermissionCheck {
        val requiresApproval = requiresApproval(actionType)
        return PermissionCheck(
            actionType = actionType,
            requiresApproval = requiresApproval,
            riskLevel = actionType.riskLevel,
            reason = if (requiresApproval) "Action ${actionType.name} requires user approval" else "Action ${actionType.name} is auto-approved"
        )
    }
}

/**
 * AI Action Plan — PRD Section 36.
 * AI generates a multi-step action plan. User may approve individual actions.
 * Example: 1. Modify MainActivity.kt, 2. Update dependency, 3. Run tests, 4. Commit, 5. Push, 6. Trigger workflow
 */
data class AIActionPlan(
    val id: String,
    val description: String,
    val steps: List<ActionStep>,
    val overallRisk: AIActionType.RiskLevel,
    val createdAt: Long,
    var approvedStepIds: Set<String> = emptySet(),
    var status: PlanStatus = PlanStatus.PENDING
) {
    enum class PlanStatus { PENDING, PARTIALLY_APPROVED, FULLY_APPROVED, REJECTED, EXECUTING, COMPLETED, FAILED }
}

data class ActionStep(
    val id: String,
    val order: Int,
    val actionType: AIActionType,
    val description: String,
    val targetFile: String? = null,
    val targetBranch: String? = null,
    val patch: String? = null,
    val requiresApproval: Boolean
)

/**
 * AI Action Sandbox — PRD Section 35.
 * All AI-generated actions must pass through:
 * AI → Action Plan → Policy Validator → Permission Validator → User Approval → Execution → Verification
 */
@Singleton
class AIActionSandbox @Inject constructor(
    private val policy: AIPermissionPolicy,
    private val patchSafety: PatchSafety
) {

    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String>,
        val warnings: List<String>
    )

    fun validatePlan(plan: AIActionPlan): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        for (step in plan.steps) {
            // Policy validation
            val check = policy.check(step.actionType)
            if (check.requiresApproval && step.id !in plan.approvedStepIds) {
                warnings.add("Step ${step.order}: ${step.actionType.name} requires approval")
            }

            // Patch safety validation
            if (step.patch != null && step.targetFile != null) {
                val patchCheck = patchSafety.validatePatch(step.targetFile, step.patch)
                if (!patchCheck.isValid) {
                    errors.addAll(patchCheck.errors.map { "Step ${step.order}: $it" })
                }
            }
        }

        return ValidationResult(errors.isEmpty(), errors, warnings)
    }

    /**
     * Execute an approved action plan step by step.
     * Returns results for each step.
     */
    suspend fun executePlan(
        plan: AIActionPlan,
        executor: suspend (ActionStep) -> Result<String>
    ): List<Pair<ActionStep, Result<String>>> {
        val results = mutableListOf<Pair<ActionStep, Result<String>>>()

        for (step in plan.steps) {
            if (step.requiresApproval && step.id !in plan.approvedStepIds) {
                results.add(step to Result.failure(RuntimeException("Step not approved")))
                continue
            }
            val result = executor(step)
            results.add(step to result)
            if (result.isFailure && step.actionType.riskLevel != AIActionType.RiskLevel.LOW) {
                break // Stop on high-risk failures
            }
        }

        return results
    }
}

/**
 * Patch Safety — PRD Section 32.
 * Before applying a patch:
 * - Verify file exists/state
 * - Verify expected base content
 * - Detect concurrent changes
 * - Prevent accidental overwrite
 * - Show diff, Require approval
 * If base changed: "Source changed since patch generation. Regenerate patch."
 */
@Singleton
class PatchSafety @Inject constructor() {

    data class PatchValidation(
        val isValid: Boolean,
        val errors: List<String>,
        val warnings: List<String>,
        val baseChanged: Boolean
    )

    data class PatchApplication(
        val filePath: String,
        val originalContent: String,
        val patchedContent: String,
        val diffPreview: String
    )

    fun validatePatch(filePath: String, patch: String): PatchValidation {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // Basic patch validation
        if (patch.isEmpty()) {
            errors.add("Empty patch")
        }
        if (!patch.contains("@@") && !patch.contains("---") && !patch.contains("+++") && !patch.contains("+") && !patch.contains("-")) {
            warnings.add("Patch does not appear to be in unified diff format")
        }

        // Check for dangerous patterns
        if (patch.contains("rm -rf", ignoreCase = true)) {
            errors.add("Patch contains dangerous command: rm -rf")
        }
        if (patch.contains("sudo", ignoreCase = true)) {
            errors.add("Patch contains sudo command")
        }
        if (patch.contains("DROP TABLE", ignoreCase = true)) {
            errors.add("Patch contains SQL DROP TABLE")
        }

        return PatchValidation(errors.isEmpty(), errors, warnings, baseChanged = false)
    }

    /**
     * Apply a patch to original content safely.
     */
    fun applyPatch(originalContent: String, patch: String): Result<PatchApplication> {
        // Check if base content changed since patch generation
        // In production, this would compare SHA hashes

        val patchedContent = applyUnifiedDiff(originalContent, patch)
        val diff = generateDiffPreview(originalContent, patchedContent)

        return Result.success(
            PatchApplication(
                filePath = "", // Set by caller
                originalContent = originalContent,
                patchedContent = patchedContent,
                diffPreview = diff
            )
        )
    }

    private fun applyUnifiedDiff(original: String, patch: String): String {
        // Simplified patch application — production would use a proper diff/patch library
        val originalLines = original.lines().toMutableList()
        for (line in patch.lines()) {
            when {
                line.startsWith("+") && !line.startsWith("+++") -> originalLines.add(line.drop(1))
                line.startsWith("-") && !line.startsWith("---") -> originalLines.removeAt(originalLines.indexOf(line.drop(1)))
            }
        }
        return originalLines.joinToString("\n")
    }

    private fun generateDiffPreview(original: String, modified: String): String {
        val origLines = original.lines()
        val modLines = modified.lines()
        val maxLen = maxOf(origLines.size, modLines.size)
        val diff = StringBuilder()

        for (i in 0 until maxLen) {
            val orig = origLines.getOrNull(i) ?: ""
            val mod = modLines.getOrNull(i) ?: ""
            if (orig != mod) {
                if (orig.isNotEmpty()) diff.append("- $orig\n")
                if (mod.isNotEmpty()) diff.append("+ $mod\n")
            } else {
                diff.append("  $orig\n")
            }
        }
        return diff.toString()
    }
}

/**
 * AI Branch Strategy — PRD Section 85.
 * AI-generated changes should preferably occur on a dedicated branch:
 * main → ai/fix-build-issue
 * This allows: Review, CI, Revert, PR creation.
 * Direct modification of protected branches should be disabled by default.
 */
@Singleton
class AIBranchStrategy @Inject constructor() {

    fun generateBranchName(actionDescription: String): String {
        val sanitized = actionDescription.lowercase()
            .replace(Regex("[^a-z0-9\\s-]"), "")
            .replace(Regex("\\s+"), "-")
            .take(40)
        return "ai/$sanitized"
    }

    fun isProtectedBranch(branchName: String): Boolean {
        return branchName == "main" || branchName == "master" || branchName == "release/*" || branchName == "prod"
    }

    fun canModifyBranch(branchName: String): Boolean {
        return !isProtectedBranch(branchName)
    }
}

/**
 * AI Approval Expiration — PRD Section 129.
 * Approval must be tied to the exact action plan.
 * If the plan changes: previous approval invalid, user must review again.
 */
@Singleton
class AIApprovalManager @Inject constructor() {

    data class Approval(
        val planId: String,
        val planHash: String, // Hash of plan content for change detection
        val approvedStepIds: Set<String>,
        val timestamp: Long,
        val expiresAt: Long
    ) {
        companion object {
            val DEFAULT_EXPIRY_MS = 30 * 60 * 1000L // 30 minutes
        }
    }

    private val approvals = mutableMapOf<String, Approval>()

    fun requestApproval(plan: AIActionPlan): Approval {
        val hash = computePlanHash(plan)
        val approval = Approval(
            planId = plan.id,
            planHash = hash,
            approvedStepIds = plan.approvedStepIds,
            timestamp = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + Approval.DEFAULT_EXPIRY_MS
        )
        approvals[plan.id] = approval
        return approval
    }

    fun isApprovalValid(plan: AIActionPlan): Boolean {
        val approval = approvals[plan.id] ?: return false
        if (System.currentTimeMillis() > approval.expiresAt) return false
        if (approval.planHash != computePlanHash(plan)) return false // Plan changed
        return true
    }

    fun revokeApproval(planId: String) {
        approvals.remove(planId)
    }

    private fun computePlanHash(plan: AIActionPlan): String {
        val content = plan.steps.joinToString { "${it.id}:${it.actionType}:${it.description}" }
        return content.hashCode().toString(16)
    }
}

/**
 * AI Session Expiration — PRD Section 128.
 * Long-running AI sessions must expire.
 * Expired sessions must not retain authorization to execute actions.
 */
@Singleton
class AISessionManager @Inject constructor() {

    data class Session(
        val id: String,
        val repository: String,
        val branch: String,
        val startedAt: Long,
        val expiresAt: Long,
        val isActive: Boolean
    ) {
        companion object {
            val SESSION_TIMEOUT_MS = 60 * 60 * 1000L // 1 hour
        }
    }

    private var currentSession: Session? = null

    fun startSession(repository: String, branch: String): Session {
        val session = Session(
            id = java.util.UUID.randomUUID().toString(),
            repository = repository,
            branch = branch,
            startedAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + Session.SESSION_TIMEOUT_MS,
            isActive = true
        )
        currentSession = session
        return session
    }

    fun getSession(): Session? {
        val session = currentSession ?: return null
        if (System.currentTimeMillis() > session.expiresAt) {
            currentSession = null // Expired
            return null
        }
        return session
    }

    fun endSession() {
        currentSession = null
    }
}

/**
 * AI Agent Maximum Scope — PRD Section 127.
 * An AI task must have: Repository scope, Branch scope, File scope, Action scope, Time limit, Request limit.
 */
data class AIAgentScope(
    val repository: String,
    val branch: String,
    val allowedFiles: List<String>,
    val allowedActions: Set<AIActionType>,
    val timeLimitMs: Long,
    val requestLimit: Int,
    val forbiddenBranches: List<String> = listOf("main", "master"),
    val forbiddenResources: List<String> = listOf("secrets", "settings")
) {
    fun isBranchAllowed(branch: String): Boolean = branch !in forbiddenBranches
    fun isFileAllowed(filePath: String): Boolean = allowedFiles.isEmpty() || filePath in allowedFiles
    fun isActionAllowed(action: AIActionType): Boolean = action in allowedActions
}
