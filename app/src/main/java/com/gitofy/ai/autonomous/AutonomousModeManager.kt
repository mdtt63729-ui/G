package com.gitofy.ai.autonomous

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the AI approval-mode lifecycle for autonomous operations (PRD §68-69).
 *
 * The approval mode controls at which points the autonomous build-repair agent
 * pauses to ask the user for confirmation before proceeding. Modes form a
 * decreasing-strictness hierarchy:
 *
 *   ASK_BEFORE_CHANGES      → ask before every action (safest)
 *   ASK_BEFORE_COMMIT       → auto-apply changes; ask before commit/push/workflow
 *   ASK_BEFORE_PUSH         → auto-commit; ask before push/workflow
 *   ASK_BEFORE_WORKFLOW_RUN → auto-push; ask before triggering the workflow
 *   FULLY_AUTONOMOUS        → no prompts; agent runs end-to-end
 */
@Singleton
class AutonomousModeManager @Inject constructor() {

    // ------------------------------------------------------------------
    //  Public types
    // ------------------------------------------------------------------

    /**
     * The five approval-mode levels.
     */
    enum class ApprovalMode {
        /** Ask before applying any file changes. (Safest — default.) */
        ASK_BEFORE_CHANGES,

        /** Auto-apply changes; ask before committing. */
        ASK_BEFORE_COMMIT,

        /** Auto-commit; ask before pushing to remote. */
        ASK_BEFORE_PUSH,

        /** Auto-push; ask before triggering the CI workflow. */
        ASK_BEFORE_WORKFLOW_RUN,

        /** No human approval required at any stage. */
        FULLY_AUTONOMOUS,
    }

    /**
     * Actions that may require user approval, ordered by increasing permissiveness.
     */
    enum class AutonomousAction {
        APPLY_CHANGES,
        COMMIT,
        PUSH,
        RUN_WORKFLOW,
    }

    /**
     * Snapshot of an autonomous session's progress.
     *
     * @param attempt         Current attempt number (1-indexed).
     * @param maxAttempts     Maximum attempts configured for this session.
     * @param currentAction   Human-readable description of the current action.
     * @param changesSummary  Short summary of files changed so far.
     * @param workflowRunId   ID of the latest workflow run, if any.
     * @param isRunning       Whether the session is currently active.
     * @param approvalMode    The approval mode governing this session.
     */
    data class AutonomousSession(
        val attempt: Int = 0,
        val maxAttempts: Int = BuildRepairAgent.DEFAULT_MAX_ATTEMPTS,
        val currentAction: String = "",
        val changesSummary: String = "",
        val workflowRunId: String? = null,
        val isRunning: Boolean = false,
        val approvalMode: ApprovalMode = ApprovalMode.ASK_BEFORE_CHANGES,
    )

    // ------------------------------------------------------------------
    //  Mutable state
    // ------------------------------------------------------------------

    private val _approvalMode = MutableStateFlow(ApprovalMode.ASK_BEFORE_CHANGES)
    /** Observable current approval mode. */
    val approvalMode: StateFlow<ApprovalMode> = _approvalMode.asStateFlow()

    private val _session = MutableStateFlow(AutonomousSession())
    /** Observable current session. */
    val session: StateFlow<AutonomousSession> = _session.asStateFlow()

    // ------------------------------------------------------------------
    //  Approval-mode API
    // ------------------------------------------------------------------

    /**
     * Sets the active approval mode.
     */
    fun setApprovalMode(mode: ApprovalMode) {
        _approvalMode.value = mode
        if (_session.value.isRunning) {
            _session.value = _session.value.copy(approvalMode = mode)
        }
    }

    /**
     * Returns the current approval mode.
     */
    fun getApprovalMode(): ApprovalMode = _approvalMode.value

    /**
     * Returns true when the agent should pause and ask the user before [action].
     *
     * The logic encodes the hierarchy:
     *  - ASK_BEFORE_CHANGES asks before every action.
     *  - ASK_BEFORE_COMMIT asks before COMMIT and later.
     *  - ASK_BEFORE_PUSH asks before PUSH and later.
     *  - ASK_BEFORE_WORKFLOW_RUN asks before RUN_WORKFLOW only.
     *  - FULLY_AUTONOMOUS never asks.
     */
    fun shouldAskBefore(action: AutonomousAction): Boolean {
        val mode = _approvalMode.value
        val actionOrdinal = action.ordinal
        val threshold = when (mode) {
            ApprovalMode.ASK_BEFORE_CHANGES      -> AutonomousAction.APPLY_CHANGES.ordinal
            ApprovalMode.ASK_BEFORE_COMMIT       -> AutonomousAction.COMMIT.ordinal
            ApprovalMode.ASK_BEFORE_PUSH         -> AutonomousAction.PUSH.ordinal
            ApprovalMode.ASK_BEFORE_WORKFLOW_RUN -> AutonomousAction.RUN_WORKFLOW.ordinal
            ApprovalMode.FULLY_AUTONOMOUS        -> return false
        }
        return actionOrdinal >= threshold
    }

    // ------------------------------------------------------------------
    //  Session lifecycle
    // ------------------------------------------------------------------

    /**
     * Starts a new autonomous session from the given [RepairConfig].
     * Any existing session is stopped first.
     */
    fun startSession(config: BuildRepairAgent.RepairConfig) {
        stopSession()
        _session.value = AutonomousSession(
            attempt = 0,
            maxAttempts = if (config.unlimitedMode) Int.MAX_VALUE else config.maxAttempts,
            isRunning = true,
            approvalMode = _approvalMode.value,
        )
    }

    /**
     * Stops the current session, if any.
     */
    fun stopSession() {
        _session.value = _session.value.copy(isRunning = false)
    }

    /**
     * Returns the current session snapshot.
     */
    fun getSession(): AutonomousSession = _session.value

    // ------------------------------------------------------------------
    //  Session mutators (used by [BuildRepairAgent] to report progress)
    // ------------------------------------------------------------------

    /**
     * Updates the current action description and attempt number.
     */
    fun reportAction(attempt: Int, action: String) {
        _session.value = _session.value.copy(attempt = attempt, currentAction = action)
    }

    /**
     * Updates the changes summary for the current session.
     */
    fun reportChanges(summary: String) {
        _session.value = _session.value.copy(changesSummary = summary)
    }

    /**
     * Updates the latest workflow run id for the current session.
     */
    fun reportWorkflow(runId: String?) {
        _session.value = _session.value.copy(workflowRunId = runId)
    }
}
