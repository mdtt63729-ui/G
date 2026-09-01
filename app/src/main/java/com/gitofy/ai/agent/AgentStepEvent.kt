package com.gitofy.ai.agent

/**
 * A category for a single agent progress step, used to group consecutive
 * steps into a single collapsible summary row in the chat UI (mirrors the
 * "Read 1 file · Used 1 tool" style step summaries in Claude Code / Copilot).
 */
enum class AgentStepKind {
    PLAN,
    READ,
    SEARCH,
    EDIT,
    CREATE_REPO,
    BRANCH,
    COMMIT,
    PULL_REQUEST,
    WORKFLOW,
    TOOL,
    ERROR
}

/**
 * One real, human-readable progress update emitted while [GitHubAiAgent]
 * works on a request. The chat UI renders a running list of these — grouped
 * by [kind] — as collapsible step chips instead of a single overwritten
 * status line, so the user can see exactly what the agent is doing.
 */
data class AgentStepEvent(
    val kind: AgentStepKind,
    val label: String,
    val detail: String? = null
)
