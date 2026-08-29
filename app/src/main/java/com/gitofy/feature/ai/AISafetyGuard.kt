package com.gitofy.feature.ai

import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI Safety Principle — PRD v5.0 Section 46.
 * AI must operate under: READ → ANALYZE → EXPLAIN → SUGGEST → USER APPROVES → EXECUTE
 * AI must never silently push code, merge PR, delete branch/repo, change workflow, trigger expensive workflow, modify release, expose secrets.
 */
@Singleton
class AISafetyGuard @Inject constructor() {

    enum class AIPermissionLevel { READ, ANALYZE, EXPLAIN, SUGGEST, PREVIEW, EXECUTE }

    data class SafetyCheck(
        val isAllowed: Boolean,
        val requiresApproval: Boolean,
        val reason: String
    )

    private val forbiddenActions = setOf(
        "PUSH_CODE", "MERGE_PR", "DELETE_BRANCH", "DELETE_REPOSITORY",
        "CHANGE_WORKFLOW", "TRIGGER_WORKFLOW", "MODIFY_RELEASE", "EXPOSE_SECRETS"
    )

    fun checkAction(action: String, permissionLevel: AIPermissionLevel): SafetyCheck {
        if (action in forbiddenActions) {
            return SafetyCheck(
                isAllowed = false,
                requiresApproval = true,
                reason = "Action '$action' is forbidden for AI direct execution. User must approve."
            )
        }
        return when (permissionLevel) {
            AIPermissionLevel.READ -> SafetyCheck(true, false, "Read operations are allowed")
            AIPermissionLevel.ANALYZE -> SafetyCheck(true, false, "Analysis operations are allowed")
            AIPermissionLevel.EXPLAIN -> SafetyCheck(true, false, "Explanation operations are allowed")
            AIPermissionLevel.SUGGEST -> SafetyCheck(true, false, "Suggestions are allowed (advisory only)")
            AIPermissionLevel.PREVIEW -> SafetyCheck(true, true, "Preview requires user review")
            AIPermissionLevel.EXECUTE -> SafetyCheck(false, true, "Execution requires explicit user approval")
        }
    }
}

/**
 * AI Action Approval — PRD v5.0 Section 59.
 * AI Suggestion → Preview Changes → Diff → User Approval → Execute
 * No direct execution from natural-language intent without confirmation.
 */
@Singleton
class AIActionApproval @Inject constructor() {

    data class PendingAction(
        val id: String,
        val type: String,
        val description: String,
        val previewDiff: String?,
        val requiresConfirmation: Boolean = true
    )

    private val pendingActions = mutableMapOf<String, PendingAction>()

    fun registerAction(action: PendingAction) {
        pendingActions[action.id] = action
    }

    fun getPendingAction(id: String): PendingAction? = pendingActions[id]

    fun approveAction(id: String): PendingAction? {
        val action = pendingActions.remove(id)
        return action // Only after approval can this be executed
    }

    fun rejectAction(id: String) {
        pendingActions.remove(id)
    }
}
