package com.gitofy.ai.agent

// Agent execution mode (PRD §34)
enum class AgentExecutionMode { AUTO, SAFE, CONFIRM }

// Agent session state (PRD §44)
enum class AgentSessionStatus { IDLE, RUNNING, COMPLETED, FAILED, CANCELLED }

// Task state (PRD §44)
enum class TaskStatus { PENDING, RUNNING, COMPLETED, FAILED, BLOCKED, SKIPPED }

// Event types (PRD §10)
enum class AgentEventType {
    READ_FILE, SEARCH_FILE, SEARCH_CODE, READ_SYMBOL, ANALYZE_CODE,
    ANALYZE_DEPENDENCY, CREATE_FILE, EDIT_FILE, DELETE_FILE, MOVE_FILE,
    RUN_COMMAND, RUN_BUILD, RUN_TEST, GIT_DIFF, GIT_STATUS,
    CREATE_BRANCH, COMMIT, PUSH, CREATE_PR,
    RUN_WORKFLOW, CHECK_WORKFLOW, READ_JOB, READ_LOG,
    DOWNLOAD_ARTIFACT, ANALYZE_ERROR, APPLY_FIX, RETRY,
    SUCCESS, FAILURE
}

// Error categories (PRD §32)
enum class AgentErrorCategory {
    AUTH_ERROR, NETWORK_ERROR, PERMISSION_ERROR, API_ERROR,
    BUILD_ERROR, SYNTAX_ERROR, DEPENDENCY_ERROR, WORKFLOW_ERROR,
    GIT_ERROR, DOWNLOAD_ERROR, UNKNOWN_ERROR
}

/**
 * PRD §38: Agent Progress States.
 *
 * Every agent operation transitions through these states.
 * The UI shows a compact progress indicator for each.
 */
enum class AgentProgressState {
    IDLE,
    ANALYZING,
    READING,
    PLANNING,
    EDITING,
    VALIDATING,
    COMMITTING,
    PUSHING,
    VERIFYING,
    SUCCESS,
    FAILED
}

/**
 * PRD §35: Repository permission level.
 */
enum class RepositoryPermission { READ, WRITE, ADMIN }

data class AgentTask(
    val id: String,
    val sessionId: String,
    val title: String,
    val description: String,
    val status: TaskStatus = TaskStatus.PENDING,
    val order: Int = 0,
    val startedAt: Long = 0L,
    val completedAt: Long = 0L,
    val errorMessage: String? = null
)

data class AgentEvent(
    val id: String,
    val sessionId: String,
    val taskId: String? = null,
    val timestamp: Long,
    val type: AgentEventType,
    val title: String,
    val description: String = "",
    val status: String = "INFO",
    val filePath: String? = null,
    val toolName: String? = null,
    val duration: Long = 0L,
    val metadata: Map<String, String> = emptyMap()
)

data class AgentSession(
    val id: String,
    val repositoryOwner: String = "",
    val repositoryName: String = "",
    val branch: String = "",
    val command: String = "",
    val tasks: List<AgentTask> = emptyList(),
    val events: List<AgentEvent> = emptyList(),
    val status: AgentSessionStatus = AgentSessionStatus.IDLE,
    val executionMode: AgentExecutionMode = AgentExecutionMode.SAFE,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class AgentUiState(
    val session: AgentSession = AgentSession(id = ""),
    val isProcessing: Boolean = false,
    val currentTask: AgentTask? = null,
    val recentEvents: List<AgentEvent> = emptyList(),
    val planVisible: Boolean = false,
    val error: String? = null
)
