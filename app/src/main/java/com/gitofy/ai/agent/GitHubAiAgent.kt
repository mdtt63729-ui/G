package com.gitofy.ai.agent

import com.gitofy.ai.gateway.AIGateway
import com.gitofy.ai.tools.ToolRegistry
import com.gitofy.ai.tools.ToolResult
import com.gitofy.core.logging.GITOFYLogger
import com.gitofy.core.network.GitHubApiService
import com.gitofy.core.security.SecureCredentialStorage
import com.gitofy.data.git.GitNativeManager
import com.gitofy.data.remote.dto.DispatchWorkflowRequest
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Natural-language GitHub coding agent used by the Gito chat.
 *
 * The AI is not given a fake "success" channel. It must request a concrete
 * registered GitHub tool, the tool executes against the authenticated GitHub
 * API, and the result is fed back to the model until the task is complete.
 *
 * GitHub authentication is intentionally inherited from the app's central
 * AuthInterceptor, so the PAT entered on the authentication screen is the
 * credential used for repository reads and writes. AI provider credentials
 * remain separate and are read by AIGateway/provider implementations.
 */
@Singleton
class GitHubAiAgent @Inject constructor(
    private val aiGateway: AIGateway,
    private val githubApi: GitHubApiService,
    private val toolRegistry: ToolRegistry,
    private val secureStorage: SecureCredentialStorage,
    private val gitNativeManager: GitNativeManager
) {

    data class AgentResult(
        val message: String,
        val owner: String? = null,
        val repo: String? = null,
        val changedFiles: List<String> = emptyList()
    )

    private data class Decision(
        val type: String,
        val tool: String? = null,
        val params: Map<String, String> = emptyMap(),
        val message: String? = null
    )

    private val writeTools = setOf(
        "create_file", "update_file", "delete_file", "create_branch",
        "create_pull_request", "run_workflow", "cancel_workflow", "rerun_workflow",
        "delete_repository", "delete_branch", "merge_pull_request"
    )

    /**
     * PRD §7: irreversible/high-impact tools. These require the model to have
     * set params["confirm"] = "CONFIRM" — which the tool implementations
     * themselves also enforce as defense in depth — and the system prompt
     * instructs the model to only do so after the user's OWN message in this
     * round explicitly confirms the specific action.
     */
    private val destructiveTools = setOf("delete_repository", "delete_branch", "merge_pull_request")

    /** Tools that don't operate on an existing owner/repo (e.g. the repo doesn't exist yet). */
    private val repoOptionalTools = setOf("list_repositories", "create_repository")

    suspend fun execute(
        command: String,
        provider: String,
        model: String,
        zipPath: String? = null,
        onProgress: (AgentStepEvent) -> Unit = {}
    ): Result<AgentResult> {
        if (!secureStorage.hasToken()) {
            return Result.failure(IllegalStateException("Connect your GitHub account first."))
        }

        val repositories = try {
            val response = githubApi.listRepositories(page = 1, perPage = 100)
            if (!response.isSuccessful) {
                return Result.failure(IllegalStateException("GitHub rejected the repository request (${response.code()})."))
            }
            response.body().orEmpty()
        } catch (e: Exception) {
            GITOFYLogger.w("GitHubAiAgent repository discovery failed: ${e.message}")
            return Result.failure(IllegalStateException("Unable to load your GitHub repositories."))
        }

        val repoCatalog = repositories.joinToString("\n") { repo ->
            "- ${repo.fullName.ifBlank { "${repo.ownerLogin}/${repo.name}" }} (default branch: ${repo.defaultBranch})"
        }

        // A ZIP attachment means the user supplied a complete project snapshot.
        // Do not make the model manually emit hundreds of file mutations: resolve
        // the requested repository, then let libgit2 atomically mirror the project
        // (including deletions) and verify the resulting branch. The selected model
        // still controls all ordinary repository/code decisions through the agent
        // loop below.
        if (!zipPath.isNullOrBlank()) {
            val target = resolveRepositoryTarget(command, repositories)
                ?: return Result.failure(IllegalStateException("Tell me the GitHub repository name or owner/repository that should receive this ZIP."))
            val permission = githubApi.getRepository(target.ownerLogin, target.name)
            if (!permission.isSuccessful) {
                return Result.failure(IllegalStateException("I could not open ${target.ownerLogin}/${target.name} (${permission.code()})."))
            }
            val permissions = permission.body()?.permissions
            if (permissions?.push != true && permissions?.admin != true) {
                return Result.failure(IllegalStateException("You have read-only access to ${target.ownerLogin}/${target.name}."))
            }
            val branch = permission.body()?.defaultBranch?.ifBlank { "main" } ?: "main"
            onProgress(AgentStepEvent(AgentStepKind.PLAN, "Uploading the complete project to ${target.ownerLogin}/${target.name}…"))
            val result = gitNativeManager.syncZipToGithub(
                repoUrl = target.htmlUrl.ifBlank { "https://github.com/${target.ownerLogin}/${target.name}.git" },
                token = secureStorage.getToken().orEmpty(),
                zipPath = zipPath,
                branch = branch,
                commitMessage = "Gito: update ${target.name}",
                userName = secureStorage.getUserLogin(),
                userEmail = secureStorage.getUserLogin()?.let { "$it@users.noreply.github.com" },
                callback = object : GitNativeManager.ProgressCallback {
                    override fun onProgress(uploadedBytes: Long, totalBytes: Long, filesCompleted: Int, totalFiles: Int, currentFile: String) {
                        onProgress(AgentStepEvent(AgentStepKind.EDIT, if (currentFile.isBlank()) "Syncing project…" else currentFile))
                    }
                    override fun onStage(stage: String) { onProgress(AgentStepEvent(AgentStepKind.PLAN, stage)) }
                }
            ).getOrElse { error ->
                return Result.failure(IllegalStateException(error.message ?: "GitHub project sync failed"))
            }

            onProgress(AgentStepEvent(AgentStepKind.WORKFLOW, "Starting Debug and Release workflows…"))
            val workflowErrors = mutableListOf<String>()
            for ((workflow, label) in listOf("build.yml" to "Debug", "release.yml" to "Release")) {
                val response = runCatching {
                    githubApi.dispatchWorkflow(
                        target.ownerLogin, target.name, workflow,
                        DispatchWorkflowRequest(ref = branch)
                    )
                }.getOrNull()
                if (response == null || !response.isSuccessful) {
                    workflowErrors += "$label workflow (HTTP ${response?.code() ?: -1})"
                }
            }
            if (workflowErrors.isNotEmpty()) {
                return Result.failure(IllegalStateException("Project uploaded, but these workflows could not be started: ${workflowErrors.joinToString()}."))
            }

            val message = if (result == "NO_CHANGES") {
                "The project was already up to date. Debug and Release workflows were started."
            } else {
                "The complete project was uploaded and synchronized to ${target.ownerLogin}/${target.name}. Debug and Release workflows were started. Commit ${result.take(7)}."
            }
            return Result.success(AgentResult(message, target.ownerLogin, target.name, listOf("<complete project snapshot>")))
        }

        var transcript = ""
        var activeOwner: String? = null
        var activeRepo: String? = null
        val changedFiles = linkedSetOf<String>()

        for (round in 0 until MAX_ROUNDS) {
            onProgress(AgentStepEvent(AgentStepKind.PLAN, if (round == 0) "Planning GitHub changes…" else "Working on GitHub changes…"))

            val prompt = buildPrompt(
                command = command,
                repoCatalog = repoCatalog,
                transcript = transcript,
                activeOwner = activeOwner,
                activeRepo = activeRepo
            )

            val response = aiGateway.process(
                AIGateway.GatewayRequest(
                    taskType = com.gitofy.ai.model.AITaskType.GENERAL_QA,
                    userPrompt = prompt,
                    contextData = emptyMap(),
                    requireToolCalling = false,
                    requireAgentActions = true,
                    costBudget = AIGateway.CostBudget.USER_SELECTED,
                    userPreferences = AIGateway.UserPreferences(
                        preferredProvider = provider,
                        preferredModel = model,
                        routingMode = AIGateway.RoutingMode.USER_SELECTED
                    )
                )
            ).getOrElse { error -> return Result.failure(error) }

            val decision = parseDecision(response.content)
                ?: return Result.failure(IllegalStateException("The AI returned an invalid agent action. Please retry."))

            when (decision.type) {
                "final" -> {
                    val message = decision.message?.trim().orEmpty()
                    if (message.isBlank()) {
                        return Result.failure(IllegalStateException("The AI completed without a final result."))
                    }
                    if (changedFiles.isNotEmpty() && !activeOwner.isNullOrBlank() && !activeRepo.isNullOrBlank()) {
                        onProgress(AgentStepEvent(AgentStepKind.WORKFLOW, "Starting Debug and Release workflows…"))
                        val branch = githubApi.getRepository(activeOwner, activeRepo).body()?.defaultBranch?.ifBlank { "main" } ?: "main"
                        val workflowErrors = mutableListOf<String>()
                        for ((workflow, label) in listOf("build.yml" to "Debug", "release.yml" to "Release")) {
                            val workflowResponse = runCatching {
                                githubApi.dispatchWorkflow(
                                    activeOwner, activeRepo, workflow,
                                    DispatchWorkflowRequest(ref = branch)
                                )
                            }.getOrNull()
                            if (workflowResponse == null || !workflowResponse.isSuccessful) {
                                workflowErrors += "$label workflow (HTTP ${workflowResponse?.code() ?: -1})"
                            }
                        }
                        if (workflowErrors.isNotEmpty()) {
                            return Result.failure(IllegalStateException("Repository changes were applied, but these workflows could not be started: ${workflowErrors.joinToString()}."))
                        }
                    }
                    return Result.success(AgentResult(message, activeOwner, activeRepo, changedFiles.toList()))
                }

                "clarify" -> {
                    val message = decision.message?.trim().orEmpty()
                    return Result.failure(IllegalStateException(message.ifBlank { "I need more information before changing the repository." }))
                }

                "tool" -> {
                    val toolName = decision.tool?.trim().orEmpty()
                    if (!toolRegistry.contains(toolName)) {
                        return Result.failure(IllegalStateException("Unsupported GitHub action: $toolName"))
                    }

                    val params = decision.params.toMutableMap()
                    val owner = params["owner"]?.takeIf { it.isNotBlank() } ?: activeOwner
                    val repo = params["repo"]?.takeIf { it.isNotBlank() } ?: activeRepo

                    if (owner != null) params["owner"] = owner
                    if (repo != null) params["repo"] = repo

                    if (toolName !in repoOptionalTools && (params["owner"].isNullOrBlank() || params["repo"].isNullOrBlank())) {
                        return Result.failure(IllegalStateException("I couldn't determine which repository you want to change."))
                    }

                    if (!toolRegistry.contains(toolName)) {
                        return Result.failure(IllegalStateException("Unsupported GitHub action: $toolName"))
                    }

                    if (toolName in writeTools) {
                        val writeOwner = params["owner"]!!
                        val writeRepo = params["repo"]!!
                        val permission = githubApi.getRepository(writeOwner, writeRepo)
                        if (!permission.isSuccessful) {
                            return Result.failure(IllegalStateException("I could not verify write access to $writeOwner/$writeRepo (${permission.code()})."))
                        }
                        val permissions = permission.body()?.permissions
                        if (permissions?.push != true && permissions?.admin != true) {
                            return Result.failure(IllegalStateException("You have read-only access to $writeOwner/$writeRepo, so I cannot modify it."))
                        }
                        if (toolName in setOf("create_file", "update_file", "delete_file") && params["branch"].isNullOrBlank()) {
                            params["branch"] = permission.body()?.defaultBranch?.takeIf { it.isNotBlank() } ?: "main"
                        }
                    }

                    onProgress(toolStepEvent(toolName, params))
                    val result = toolRegistry.executeSuspend(toolName, params)
                    if (!result.success) {
                        transcript += "\nTOOL $toolName FAILED: ${safeToolData(result.error ?: "Unknown error")}"
                        continue
                    }

                    if (toolName in setOf("create_file", "update_file", "delete_file")) {
                        params["path"]?.let(changedFiles::add)
                        verifyFileMutation(toolName, params)
                    }

                    if (toolName == "create_repository") {
                        // The repo didn't exist when this round started, so
                        // owner/repo above are still null — adopt the ones
                        // GitHub just assigned as the active repository.
                        runCatching { JSONObject(result.data) }.getOrNull()?.let { created ->
                            created.optString("owner").takeIf { it.isNotBlank() }?.let { activeOwner = it }
                            created.optString("repo").takeIf { it.isNotBlank() }?.let { activeRepo = it }
                        }
                    } else {
                        params["owner"]?.let { activeOwner = it }
                        params["repo"]?.let { activeRepo = it }
                    }
                    transcript += "\nTOOL $toolName RESULT:\n${safeToolData(result.data)}"
                }

                else -> return Result.failure(IllegalStateException("The AI returned an unknown agent decision."))
            }
        }

        return Result.failure(IllegalStateException("The task needs more steps than the safe agent limit allows. No further changes were made after the last verified action."))
    }

    private suspend fun verifyFileMutation(tool: String, params: Map<String, String>) {
        val owner = params["owner"] ?: return
        val repo = params["repo"] ?: return
        val path = params["path"] ?: return
        val branch = params["branch"]
        val response = githubApi.getContent(owner, repo, path, branch)
        if (tool == "delete_file" && response.code() == 404) return
        if (!response.isSuccessful && tool != "delete_file") {
            throw IllegalStateException("GitHub accepted the change but remote verification failed for $path (${response.code()}).")
        }
        if (tool == "delete_file" && response.isSuccessful) {
            throw IllegalStateException("GitHub reported deletion success but $path is still present on the remote.")
        }
    }

    private fun resolveRepositoryTarget(
        command: String,
        repositories: List<com.gitofy.data.remote.dto.Repository>
    ): com.gitofy.data.remote.dto.Repository? {
        val normalized = command.lowercase()
        repositories.firstOrNull { repo ->
            val fullName = repo.fullName.ifBlank { "${repo.ownerLogin}/${repo.name}" }.lowercase()
            fullName.isNotBlank() && normalized.contains(fullName)
        }?.let { return it }

        val ownerRepo = Regex("(?:github\\.com/)?([a-zA-Z0-9_.-]+)/([a-zA-Z0-9_.-]+)")
            .find(command)
        if (ownerRepo != null) {
            val owner = ownerRepo.groupValues[1]
            val name = ownerRepo.groupValues[2].removeSuffix(".git")
            repositories.firstOrNull {
                it.ownerLogin.equals(owner, true) && it.name.equals(name, true)
            }?.let { return it }
        }

        val repoNameMatch = repositories.firstOrNull {
            Regex("(?i)(?:repo(?:sitory)?\\s*(?:name)?\\s*[:=]\\s*)?\\b${Regex.escape(it.name)}\\b")
                .containsMatchIn(command)
        }
        return repoNameMatch
    }

    private fun buildPrompt(
        command: String,
        repoCatalog: String,
        transcript: String,
        activeOwner: String?,
        activeRepo: String?
    ): String = buildString {
        appendLine("You are Gito, the GitHub coding agent inside GITOFY.")
        appendLine("Execute the user's request against their real GitHub repositories using only the registered tools.")
        appendLine("The GitHub account is already authenticated by the Android app. Never ask for a GitHub token in chat.")
        appendLine("The selected AI provider key is separate from GitHub authentication.")
        appendLine("You may inspect files before modifying them. For code changes, read the relevant existing files first.")
        appendLine("When modifying a file, provide the COMPLETE new file content in the content parameter. Never provide a patch fragment.")
        appendLine("Prefer the repository default branch unless the user explicitly asks for another branch or a pull request.")
        appendLine("Do not claim a change happened until the tool reports success.")
        appendLine("Do not invent file contents, paths, repository names, commits, or test results.")
        appendLine("For destructive deletion, only call delete_file when the user explicitly requested deletion/removal.")
        appendLine("DESTRUCTIVE ACTIONS (delete_repository, delete_branch, merge_pull_request) are irreversible or high-impact.")
        appendLine("Never pass confirm=\"CONFIRM\" to a destructive tool on the same round the user first asked for it.")
        appendLine("Instead, respond with type \"clarify\": state exactly what will happen (repository/branch/PR name) and ask the user to explicitly confirm in their own words.")
        appendLine("Only pass confirm=\"CONFIRM\" once the user's most recent message unambiguously confirms that specific action (e.g. \"yes, delete it\", \"confirm\", \"go ahead and merge it\").")
        appendLine("After a successful mutation, continue inspecting/verifying when needed, then return a concise final result.")
        appendLine("You operate in COPILOT MODE: any user message can be a repository action. If the user mentions a file, path, bug, feature, or code change, use the tools to make it happen directly on their repository.")
        appendLine("When the user asks a general question that does NOT require repository changes, respond conversationally without calling tools.")
        appendLine("Always identify the target repository from the user's message. If they mention a repo name, use it. If no repo is mentioned, use the first repository in the catalog that matches.")
        appendLine("If the user asks to create/make a brand-new repository or project, call create_repository (do NOT pass owner/repo — it doesn't exist yet). After it succeeds the newly created repository becomes the active repository, so you can immediately call create_file to add starter files to it in the same turn.")
        appendLine("For multi-file changes, call create_file or update_file once per file. You may make as many tool calls as needed.")
        appendLine()
        appendLine("AVAILABLE TOOLS:")
        for (tool in toolRegistry.all) {
            val parameters = tool.parameters.joinToString(", ") { p ->
                "${p.name}:${p.type}${if (p.required) "!" else ""}"
            }
            appendLine("- ${tool.name}($parameters): ${tool.description}")
        }
        appendLine()
        appendLine("USER'S GITHUB REPOSITORIES:")
        appendLine(repoCatalog.ifBlank { "(No repositories were returned.)" })
        appendLine("ACTIVE REPOSITORY: ${activeOwner?.let { "$it/${activeRepo.orEmpty()}" } ?: "none"}")
        appendLine()
        appendLine("USER REQUEST:")
        appendLine(command)
        appendLine()
        appendLine("PREVIOUS TOOL RESULTS:")
        appendLine(transcript.ifBlank { "(none)" })
        appendLine()
        appendLine("Return EXACTLY one JSON object and no markdown:")
        appendLine("{\"type\":\"tool\",\"tool\":\"read_file\",\"params\":{\"owner\":\"...\",\"repo\":\"...\",\"path\":\"...\"}}")
        appendLine("or {\"type\":\"final\",\"message\":\"...\"}")
        appendLine("or {\"type\":\"clarify\",\"message\":\"...\"}")
    }

    private fun parseDecision(raw: String): Decision? {
        val json = extractJsonObject(raw) ?: return null
        return try {
            val type = json.optString("type").trim()
            when (type) {
                "final", "clarify" -> Decision(type = type, message = json.optString("message"))
                "tool" -> {
                    val tool = json.optString("tool").takeIf { it.isNotBlank() }
                    val paramsJson = json.optJSONObject("params") ?: JSONObject()
                    val params = mutableMapOf<String, String>()
                    paramsJson.keys().forEach { key ->
                        params[key] = when (val value = paramsJson.opt(key)) {
                            JSONObject.NULL -> ""
                            is JSONObject, is JSONArray -> value.toString()
                            else -> value?.toString() ?: ""
                        }
                    }
                    Decision(type = type, tool = tool, params = params)
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractJsonObject(raw: String): JSONObject? {
        val text = raw.trim().removePrefix("```").removeSuffix("```").trim()
        var start = -1
        var depth = 0
        var inString = false
        var escaped = false
        for (i in text.indices) {
            val c = text[i]
            if (inString) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == '"') inString = false
                continue
            }
            if (c == '"') {
                inString = true
                continue
            }
            if (c == '{') {
                if (start < 0) start = i
                depth++
            } else if (c == '}') {
                depth--
                if (start >= 0 && depth == 0) {
                    return runCatching { JSONObject(text.substring(start, i + 1)) }.getOrNull()
                }
            }
        }
        return null
    }

    private fun safeToolData(data: String): String {
        val normalized = data.replace(Regex("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[^\\s,}]+"), "$1[REDACTED]")
        return if (normalized.length <= MAX_RESULT_CHARS) normalized else normalized.take(MAX_RESULT_CHARS) + "\n[tool output truncated]"
    }

    private fun toolStepEvent(tool: String, params: Map<String, String>): AgentStepEvent = when (tool) {
        "list_repositories" -> AgentStepEvent(AgentStepKind.READ, "Loading repositories…")
        "list_files" -> AgentStepEvent(AgentStepKind.READ, "Inspecting repository structure…")
        "read_file" -> AgentStepEvent(AgentStepKind.READ, "Read ${params["path"].orEmpty()}", params["path"])
        "search_code" -> AgentStepEvent(AgentStepKind.SEARCH, "Searching repository code…", params["query"])
        "get_repository", "get_branch", "get_commit" -> AgentStepEvent(AgentStepKind.READ, "Checking repository…")
        "create_repository" -> AgentStepEvent(AgentStepKind.CREATE_REPO, "Creating repository ${params["name"].orEmpty()}…")
        "create_file" -> AgentStepEvent(AgentStepKind.EDIT, "Created ${params["path"].orEmpty()}", params["path"])
        "update_file" -> AgentStepEvent(AgentStepKind.EDIT, "Edited ${params["path"].orEmpty()}", params["path"])
        "delete_file" -> AgentStepEvent(AgentStepKind.EDIT, "Deleted ${params["path"].orEmpty()}", params["path"])
        "create_branch" -> AgentStepEvent(AgentStepKind.BRANCH, "Creating branch ${params["branch"].orEmpty()}…")
        "commit_changes" -> AgentStepEvent(AgentStepKind.COMMIT, "Committing changes…")
        "create_pull_request" -> AgentStepEvent(AgentStepKind.PULL_REQUEST, "Creating pull request…")
        "run_workflow" -> AgentStepEvent(AgentStepKind.WORKFLOW, "Starting GitHub Actions…")
        "cancel_workflow" -> AgentStepEvent(AgentStepKind.WORKFLOW, "Cancelling workflow run…")
        "rerun_workflow" -> AgentStepEvent(AgentStepKind.WORKFLOW, "Re-running workflow…")
        else -> AgentStepEvent(AgentStepKind.TOOL, "Used $tool")
    }

    companion object {
        private const val MAX_ROUNDS = 12
        private const val MAX_RESULT_CHARS = 14000
    }
}
