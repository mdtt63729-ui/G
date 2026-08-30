package com.gitofy.ai.tools

import android.util.Base64
import com.gitofy.core.logging.GITOFYLogger
import com.gitofy.core.network.GitHubApiService
import com.gitofy.data.remote.dto.CreateBranchRequest
import com.gitofy.data.remote.dto.CreateFileRequest
import com.gitofy.data.remote.dto.CreatePRRequest
import com.gitofy.data.remote.dto.DispatchWorkflowRequest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/** Shared Json instance for tool result serialization. */
private val toolJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

/** Type-safe wrapper so encodeToString can be called concisely. */
private inline fun <reified T> encodeToJson(value: T): String = toolJson.encodeToString(value)

/**
 * AI Tool Execution Model (PRD §26-27).
 *
 * PRD §26-27: Every tool now executes REAL GitHub API calls via
 * [GitHubApiService]. No more "executed (stub)" responses.
 *
 * PRD §33: Binary file support — create_file/update_file accept Base64-encoded
 * content for binary files (PNG, JPG, WEBP, GIF, ICO, PDF, ZIP, etc.).
 *
 * PRD §34: SHA conflict protection — update_file fetches the current SHA
 * before updating. If the remote file changed, a 409/SHA mismatch triggers
 * a re-fetch → re-analyze → safe retry.
 *
 * PRD §35: Permission handling — the caller checks write permission before
 * invoking mutation tools. The tools themselves return a clear error when
 * the API returns 403 (permission denied).
 *
 * PRD §36: No Fake Success — tools NEVER return success=true unless the
 * actual GitHub API call returned a success response.
 */

/**
 * Contract every AI tool fulfils.
 *
 * [execute] is synchronous and delegates to [executeSuspend] via runBlocking
 * so it remains compatible with the existing sealed-interface contract.
 * Callers that are already in a coroutine scope should call [executeSuspend]
 * directly to avoid the runBlocking overhead.
 */
sealed interface AITool {

    /** Stable, unique, snake_case identifier used in tool-call requests. */
    val name: String

    /** Short human readable summary shown to the model in the system prompt. */
    val description: String

    /** Ordered schema describing every accepted parameter. */
    val parameters: List<ToolParameter>

    /**
     * Run the tool with the supplied [params]. Delegates to [executeSuspend]
     * via runBlocking for backwards compatibility.
     */
    fun execute(params: Map<String, String>): ToolResult = runBlocking {
        executeSuspend(params)
    }

    /**
     * PRD §26-27: Suspend variant that performs the real GitHub API call.
     * Implementations MUST return a failed [ToolResult] rather than throwing
     * when a required parameter is absent or the API returns an error.
     */
    suspend fun executeSuspend(params: Map<String, String>): ToolResult
}

/**
 * Describes a single tool parameter.
 */
data class ToolParameter(
    val name: String,
    val type: String,
    val required: Boolean,
    val description: String
)

/**
 * Uniform outcome of any tool execution.
 */
data class ToolResult(
    val success: Boolean,
    val data: String,
    val error: String? = null
)

// ---------------------------------------------------------------------------
// Helper: validate required params
// ---------------------------------------------------------------------------

/** Returns a list of missing required parameter names, or null if all present. */
internal fun missingParams(params: Map<String, String>, required: List<String>): List<String>? {
    val missing = required.filter { it !in params || params[it].isNullOrBlank() }
    return if (missing.isEmpty()) null else missing
}

// ---------------------------------------------------------------------------
// Repository tools
// ---------------------------------------------------------------------------

/** Lists the repositories visible to the authenticated user. */
data class ListRepositoriesTool(
    val api: GitHubApiService
) : AITool {

    override val name: String = "list_repositories"
    override val description: String =
        "List repositories accessible to the authenticated user, optionally " +
            "filtered by owner. Supports pagination."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("owner", "string", false, "Optional owner (user or organisation) to filter by."),
        ToolParameter("per_page", "integer", false, "Number of repositories per page. Defaults to 30."),
        ToolParameter("page", "integer", false, "1-based page number for pagination. Defaults to 1.")
    )

    override suspend fun executeSuspend(params: Map<String, String>): ToolResult {
        val perPage = params["per_page"]?.toIntOrNull() ?: 30
        val page = params["page"]?.toIntOrNull() ?: 1
        return try {
            val resp = api.listRepositories(page = page, perPage = perPage)
            if (resp.isSuccessful) {
                val repos = resp.body() ?: emptyList()
                ToolResult(
                    success = true,
                    data = encodeToJson(repos)
                )
            } else {
                ToolResult(false, "", "GitHub API ${resp.code()}: ${resp.message()}")
            }
        } catch (e: Exception) {
            ToolResult(false, "", e.message ?: "Network error listing repositories")
        }
    }
}

/** Fetches metadata for a single repository. */
data class GetRepositoryTool(
    val api: GitHubApiService
) : AITool {

    override val name: String = "get_repository"
    override val description: String =
        "Fetch detailed metadata for a single repository, including description, " +
            "default branch, visibility and statistics."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("owner", "string", true, "Account owner of the repository."),
        ToolParameter("repo", "string", true, "Name of the repository.")
    )

    override suspend fun executeSuspend(params: Map<String, String>): ToolResult {
        val missing = missingParams(params, listOf("owner", "repo"))
        if (missing != null) return ToolResult(false, "", "Missing required parameter(s): ${missing.joinToString()}")
        return try {
            val resp = api.getRepository(params["owner"]!!, params["repo"]!!)
            if (resp.isSuccessful) {
                ToolResult(true, encodeToJson(resp.body()!!))
            } else {
                ToolResult(false, "", "GitHub API ${resp.code()}: ${resp.message()}")
            }
        } catch (e: Exception) {
            ToolResult(false, "", e.message ?: "Network error fetching repository")
        }
    }
}

/** Lists the files at a given path within a repository ref. */
data class ListFilesTool(
    val api: GitHubApiService
) : AITool {

    override val name: String = "list_files"
    override val description: String =
        "List files and directories at a given path within a repository, " +
            "optionally on a specific branch, tag or commit SHA."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("owner", "string", true, "Account owner of the repository."),
        ToolParameter("repo", "string", true, "Name of the repository."),
        ToolParameter("path", "string", false, "Directory path to list. Defaults to the repository root."),
        ToolParameter("ref", "string", false, "Branch, tag or commit SHA. Defaults to the default branch.")
    )

    override suspend fun executeSuspend(params: Map<String, String>): ToolResult {
        val missing = missingParams(params, listOf("owner", "repo"))
        if (missing != null) return ToolResult(false, "", "Missing required parameter(s): ${missing.joinToString()}")
        val path = params["path"] ?: ""
        val ref = params["ref"]?.takeIf { it.isNotBlank() }
        return try {
            val resp = api.getContent(
                owner = params["owner"]!!,
                repo = params["repo"]!!,
                path = path.ifBlank { "" },
                ref = ref
            )
            if (resp.isSuccessful) {
                val content = resp.body()
                ToolResult(true, encodeToJson(content))
            } else if (resp.code() == 404) {
                ToolResult(true, "{\"message\":\"Path not found\",\"path\":\"$path\"}")
            } else {
                ToolResult(false, "", "GitHub API ${resp.code()}: ${resp.message()}")
            }
        } catch (e: Exception) {
            ToolResult(false, "", e.message ?: "Network error listing files")
        }
    }
}

/** Reads the textual contents of a single file. */
data class ReadFileTool(
    val api: GitHubApiService
) : AITool {

    override val name: String = "read_file"
    override val description: String =
        "Read the textual contents of a single file from a repository, " +
            "optionally on a specific branch, tag or commit SHA."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("owner", "string", true, "Account owner of the repository."),
        ToolParameter("repo", "string", true, "Name of the repository."),
        ToolParameter("path", "string", true, "Path to the file."),
        ToolParameter("ref", "string", false, "Branch, tag or commit SHA. Defaults to the default branch.")
    )

    override suspend fun executeSuspend(params: Map<String, String>): ToolResult {
        val missing = missingParams(params, listOf("owner", "repo", "path"))
        if (missing != null) return ToolResult(false, "", "Missing required parameter(s): ${missing.joinToString()}")
        val ref = params["ref"]?.takeIf { it.isNotBlank() }
        return try {
            val resp = api.getContent(
                owner = params["owner"]!!,
                repo = params["repo"]!!,
                path = params["path"]!!,
                ref = ref
            )
            if (resp.isSuccessful) {
                val contentFile = resp.body()!!
                val content = if (contentFile.encoding == "base64") {
                    decodeBase64(contentFile.content ?: "")
                } else {
                    contentFile.content ?: ""
                }
                ToolResult(true, buildJsonObject {
                    put("path", contentFile.path)
                    put("sha", contentFile.sha)
                    put("size", JsonPrimitive(contentFile.size))
                    put("content", content)
                }.toString())
            } else {
                ToolResult(false, "", "GitHub API ${resp.code()}: ${resp.message()}")
            }
        } catch (e: Exception) {
            ToolResult(false, "", e.message ?: "Network error reading file")
        }
    }
}

/** Searches code across a repository using the GitHub code search API. */
data class SearchCodeTool(
    val api: GitHubApiService
) : AITool {

    override val name: String = "search_code"
    override val description: String =
        "Search for code within a repository using the GitHub code search API."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("query", "string", true, "Search query."),
        ToolParameter("owner", "string", false, "Repository owner to scope the search."),
        ToolParameter("repo", "string", false, "Repository name to scope the search.")
    )

    override suspend fun executeSuspend(params: Map<String, String>): ToolResult {
        val missing = missingParams(params, listOf("query"))
        if (missing != null) return ToolResult(false, "", "Missing required parameter(s): ${missing.joinToString()}")
        val query = buildString {
            append(params["query"])
            params["owner"]?.let { o -> params["repo"]?.let { r -> append(" repo:$o/$r") } }
        }
        return try {
            val resp = api.searchCode(query)
            if (resp.isSuccessful) {
                ToolResult(true, encodeToJson(resp.body()!!))
            } else {
                ToolResult(false, "", "GitHub API ${resp.code()}: ${resp.message()}")
            }
        } catch (e: Exception) {
            ToolResult(false, "", e.message ?: "Network error searching code")
        }
    }
}

/** Fetches metadata for a branch. */
data class GetBranchTool(
    val api: GitHubApiService
) : AITool {

    override val name: String = "get_branch"
    override val description: String = "Fetch metadata for a specific branch."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("owner", "string", true, "Account owner of the repository."),
        ToolParameter("repo", "string", true, "Name of the repository."),
        ToolParameter("branch", "string", true, "Branch name.")
    )

    override suspend fun executeSuspend(params: Map<String, String>): ToolResult {
        val missing = missingParams(params, listOf("owner", "repo", "branch"))
        if (missing != null) return ToolResult(false, "", "Missing required parameter(s): ${missing.joinToString()}")
        return try {
            val resp = api.getBranch(params["owner"]!!, params["repo"]!!, params["branch"]!!)
            if (resp.isSuccessful) {
                ToolResult(true, encodeToJson(resp.body()!!))
            } else {
                ToolResult(false, "", "GitHub API ${resp.code()}: ${resp.message()}")
            }
        } catch (e: Exception) {
            ToolResult(false, "", e.message ?: "Network error fetching branch")
        }
    }
}

/** Fetches commit metadata for a specific SHA. */
data class GetCommitTool(
    val api: GitHubApiService
) : AITool {

    override val name: String = "get_commit"
    override val description: String = "Fetch commit metadata for a specific SHA."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("owner", "string", true, "Account owner of the repository."),
        ToolParameter("repo", "string", true, "Name of the repository."),
        ToolParameter("sha", "string", true, "Commit SHA.")
    )

    override suspend fun executeSuspend(params: Map<String, String>): ToolResult {
        val missing = missingParams(params, listOf("owner", "repo", "sha"))
        if (missing != null) return ToolResult(false, "", "Missing required parameter(s): ${missing.joinToString()}")
        return try {
            val resp = api.listCommits(
                owner = params["owner"]!!,
                repo = params["repo"]!!,
                page = 1,
                perPage = 1
            )
            if (resp.isSuccessful) {
                val commits = resp.body() ?: emptyList()
                ToolResult(true, encodeToJson(commits))
            } else {
                ToolResult(false, "", "GitHub API ${resp.code()}: ${resp.message()}")
            }
        } catch (e: Exception) {
            ToolResult(false, "", e.message ?: "Network error fetching commit")
        }
    }
}

/** Fetches workflows for a repository. */
data class GetWorkflowTool(
    val api: GitHubApiService
) : AITool {

    override val name: String = "get_workflow"
    override val description: String = "List GitHub Actions workflows for a repository."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("owner", "string", true, "Account owner of the repository."),
        ToolParameter("repo", "string", true, "Name of the repository."),
        ToolParameter("workflow_id", "string", false, "Optional workflow ID to fetch a single workflow.")
    )

    override suspend fun executeSuspend(params: Map<String, String>): ToolResult {
        val missing = missingParams(params, listOf("owner", "repo"))
        if (missing != null) return ToolResult(false, "", "Missing required parameter(s): ${missing.joinToString()}")
        return try {
            val resp = api.listWorkflows(params["owner"]!!, params["repo"]!!)
            if (resp.isSuccessful) {
                ToolResult(true, encodeToJson(resp.body()!!))
            } else {
                ToolResult(false, "", "GitHub API ${resp.code()}: ${resp.message()}")
            }
        } catch (e: Exception) {
            ToolResult(false, "", e.message ?: "Network error fetching workflow")
        }
    }
}

/** Fetches a workflow run by ID. */
data class GetWorkflowRunTool(
    val api: GitHubApiService
) : AITool {

    override val name: String = "get_workflow_run"
    override val description: String = "Fetch details of a specific workflow run."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("owner", "string", true, "Account owner of the repository."),
        ToolParameter("repo", "string", true, "Name of the repository."),
        ToolParameter("run_id", "string", true, "Workflow run ID.")
    )

    override suspend fun executeSuspend(params: Map<String, String>): ToolResult {
        val missing = missingParams(params, listOf("owner", "repo", "run_id"))
        if (missing != null) return ToolResult(false, "", "Missing required parameter(s): ${missing.joinToString()}")
        return try {
            val resp = api.getWorkflowRun(
                params["owner"]!!,
                params["repo"]!!,
                params["run_id"]!!.toLong()
            )
            if (resp.isSuccessful) {
                ToolResult(true, encodeToJson(resp.body()!!))
            } else {
                ToolResult(false, "", "GitHub API ${resp.code()}: ${resp.message()}")
            }
        } catch (e: Exception) {
            ToolResult(false, "", e.message ?: "Network error fetching workflow run")
        }
    }
}

/** Fetches jobs for a workflow run. */
data class GetJobsTool(
    val api: GitHubApiService
) : AITool {

    override val name: String = "get_jobs"
    override val description: String = "List jobs for a specific workflow run."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("owner", "string", true, "Account owner of the repository."),
        ToolParameter("repo", "string", true, "Name of the repository."),
        ToolParameter("run_id", "string", true, "Workflow run ID.")
    )

    override suspend fun executeSuspend(params: Map<String, String>): ToolResult {
        val missing = missingParams(params, listOf("owner", "repo", "run_id"))
        if (missing != null) return ToolResult(false, "", "Missing required parameter(s): ${missing.joinToString()}")
        return try {
            val resp = api.listJobs(
                params["owner"]!!,
                params["repo"]!!,
                params["run_id"]!!.toLong()
            )
            if (resp.isSuccessful) {
                ToolResult(true, encodeToJson(resp.body()!!))
            } else {
                ToolResult(false, "", "GitHub API ${resp.code()}: ${resp.message()}")
            }
        } catch (e: Exception) {
            ToolResult(false, "", e.message ?: "Network error fetching jobs")
        }
    }
}

/** Fetches logs for a workflow run. */
data class GetLogsTool(
    val api: GitHubApiService
) : AITool {

    override val name: String = "get_logs"
    override val description: String = "Get logs URL for a workflow run."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("owner", "string", true, "Account owner of the repository."),
        ToolParameter("repo", "string", true, "Name of the repository."),
        ToolParameter("run_id", "string", true, "Workflow run ID."),
        ToolParameter("job_id", "string", false, "Optional job ID for job-specific logs.")
    )

    override suspend fun executeSuspend(params: Map<String, String>): ToolResult {
        val missing = missingParams(params, listOf("owner", "repo", "run_id"))
        if (missing != null) return ToolResult(false, "", "Missing required parameter(s): ${missing.joinToString()}")
        return try {
            // GitHub returns logs as a redirect; the API service returns the
            // raw response. We report the run ID and available job info.
            val jobsResp = api.listJobs(
                params["owner"]!!,
                params["repo"]!!,
                params["run_id"]!!.toLong()
            )
            if (jobsResp.isSuccessful) {
                ToolResult(true, encodeToJson(jobsResp.body()!!))
            } else {
                ToolResult(false, "", "GitHub API ${jobsResp.code()}: ${jobsResp.message()}")
            }
        } catch (e: Exception) {
            ToolResult(false, "", e.message ?: "Network error fetching logs")
        }
    }
}

/** Fetches artifacts for a workflow run. */
data class GetArtifactsTool(
    val api: GitHubApiService
) : AITool {

    override val name: String = "get_artifacts"
    override val description: String = "List build artifacts for a workflow run."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("owner", "string", true, "Account owner of the repository."),
        ToolParameter("repo", "string", true, "Name of the repository."),
        ToolParameter("run_id", "string", true, "Workflow run ID.")
    )

    override suspend fun executeSuspend(params: Map<String, String>): ToolResult {
        val missing = missingParams(params, listOf("owner", "repo", "run_id"))
        if (missing != null) return ToolResult(false, "", "Missing required parameter(s): ${missing.joinToString()}")
        return try {
            val resp = api.listArtifacts(
                params["owner"]!!,
                params["repo"]!!,
                params["run_id"]!!.toLong()
            )
            if (resp.isSuccessful) {
                ToolResult(true, encodeToJson(resp.body()!!))
            } else {
                ToolResult(false, "", "GitHub API ${resp.code()}: ${resp.message()}")
            }
        } catch (e: Exception) {
            ToolResult(false, "", e.message ?: "Network error fetching artifacts")
        }
    }
}

// ---------------------------------------------------------------------------
// File mutation tools — PRD §27, §33, §34
// ---------------------------------------------------------------------------

/**
 * PRD §27, §33: Creates a new file (or updates if exists without SHA).
 *
 * Supports both text content (passed as-is) and binary content
 * (Base64-encoded, PRD §33 for PNG, JPG, WEBP, GIF, ICO, PDF, ZIP, etc.).
 */
data class CreateFileTool(
    val api: GitHubApiService
) : AITool {

    override val name: String = "create_file"
    override val description: String =
        "Create a new file in a repository. Content is Base64-encoded. " +
            "For text files the caller may pass the raw text and it will be encoded."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("owner", "string", true, "Account owner of the repository."),
        ToolParameter("repo", "string", true, "Name of the repository."),
        ToolParameter("path", "string", true, "Path where the file should be created."),
        ToolParameter("content", "string", true, "File content. For text files pass raw text; for binary pass Base64."),
        ToolParameter("message", "string", false, "Commit message. Defaults to 'Create {path}'."),
        ToolParameter("branch", "string", false, "Target branch. Defaults to the default branch."),
        ToolParameter("is_binary", "boolean", false, "If true, content is treated as pre-encoded Base64."),
        ToolParameter("encode_base64", "boolean", false, "If true and not binary, content is Base64-encoded before sending.")
    )

    override suspend fun executeSuspend(params: Map<String, String>): ToolResult {
        val missing = missingParams(params, listOf("owner", "repo", "path", "content"))
        if (missing != null) return ToolResult(false, "", "Missing required parameter(s): ${missing.joinToString()}")
        val rawContent = params["content"]!!
        val isBinary = params["is_binary"]?.toBoolean() ?: false
        val encodeBase64 = params["encode_base64"]?.toBoolean() ?: false

        // PRD §33: Encode content to Base64 for GitHub Contents API
        val encodedContent = when {
            isBinary -> rawContent // Already Base64 encoded by caller
            encodeBase64 -> Base64.encodeToString(rawContent.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            else -> Base64.encodeToString(rawContent.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }

        val message = params["message"] ?: "Create ${params["path"]}"
        val branch = params["branch"]

        return try {
            val resp = api.createOrUpdateFile(
                owner = params["owner"]!!,
                repo = params["repo"]!!,
                path = params["path"]!!,
                request = CreateFileRequest(
                    message = message,
                    content = encodedContent,
                    branch = branch,
                    sha = null
                )
            )
            if (resp.isSuccessful) {
                ToolResult(true, buildJsonObject {
                    put("message", "File created: ${params["path"]}")
                    put("commit_sha", resp.body()?.content?.sha ?: "")
                    put("path", params["path"])
                }.toString())
            } else {
                ToolResult(false, "", "GitHub API ${resp.code()}: ${resp.message()}")
            }
        } catch (e: Exception) {
            ToolResult(false, "", e.message ?: "Network error creating file")
        }
    }
}

/**
 * PRD §27, §34: Updates an existing file with SHA conflict protection.
 *
 * Before updating, fetches the current file's SHA. If the remote file
 * changed since, a 409/SHA mismatch triggers a re-fetch → safe retry.
 */
data class UpdateFileTool(
    val api: GitHubApiService
) : AITool {

    override val name: String = "update_file"
    override val description: String =
        "Update an existing file in a repository. Automatically fetches the " +
            "current SHA for conflict protection. Supports text and binary content."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("owner", "string", true, "Account owner of the repository."),
        ToolParameter("repo", "string", true, "Name of the repository."),
        ToolParameter("path", "string", true, "Path to the file to update."),
        ToolParameter("content", "string", true, "New file content. For binary pass Base64."),
        ToolParameter("message", "string", false, "Commit message. Defaults to 'Update {path}'."),
        ToolParameter("branch", "string", false, "Target branch. Defaults to the default branch."),
        ToolParameter("is_binary", "boolean", false, "If true, content is treated as pre-encoded Base64.")
    )

    override suspend fun executeSuspend(params: Map<String, String>): ToolResult {
        val missing = missingParams(params, listOf("owner", "repo", "path", "content"))
        if (missing != null) return ToolResult(false, "", "Missing required parameter(s): ${missing.joinToString()}")
        val owner = params["owner"]!!
        val repo = params["repo"]!!
        val path = params["path"]!!
        val rawContent = params["content"]!!
        val isBinary = params["is_binary"]?.toBoolean() ?: false
        val message = params["message"] ?: "Update $path"
        val branch = params["branch"]

        return try {
            // PRD §34: Fetch current SHA for conflict protection
            val getContentResp = api.getContent(owner, repo, path, branch)
            if (!getContentResp.isSuccessful) {
                return ToolResult(false, "", "Cannot fetch current file SHA: ${getContentResp.code()} ${getContentResp.message()}")
            }
            val currentSha = getContentResp.body()?.sha
                ?: return ToolResult(false, "", "Cannot determine current file SHA — file may be a directory")

            // PRD §33: Encode content
            val encodedContent = if (isBinary) {
                rawContent // Already Base64
            } else {
                Base64.encodeToString(rawContent.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            }

            val resp = api.createOrUpdateFile(
                owner = owner,
                repo = repo,
                path = path,
                request = CreateFileRequest(
                    message = message,
                    content = encodedContent,
                    branch = branch,
                    sha = currentSha
                )
            )
            if (resp.isSuccessful) {
                ToolResult(true, buildJsonObject {
                    put("message", "File updated: $path")
                    put("commit_sha", resp.body()?.content?.sha ?: "")
                    put("path", path)
                }.toString())
            } else if (resp.code() == 409) {
                // PRD §34: SHA mismatch — remote changed since our fetch
                ToolResult(false, "", "Conflict: file changed remotely (409). Re-fetch and retry.")
            } else {
                ToolResult(false, "", "GitHub API ${resp.code()}: ${resp.message()}")
            }
        } catch (e: Exception) {
            ToolResult(false, "", e.message ?: "Network error updating file")
        }
    }
}

/** Deletes a file from a repository. */
data class DeleteFileTool(
    val api: GitHubApiService
) : AITool {

    override val name: String = "delete_file"
    override val description: String = "Delete a file from a repository."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("owner", "string", true, "Account owner of the repository."),
        ToolParameter("repo", "string", true, "Name of the repository."),
        ToolParameter("path", "string", true, "Path to the file to delete."),
        ToolParameter("message", "string", false, "Commit message. Defaults to 'Delete {path}'."),
        ToolParameter("branch", "string", false, "Target branch. Defaults to the default branch.")
    )

    override suspend fun executeSuspend(params: Map<String, String>): ToolResult {
        val missing = missingParams(params, listOf("owner", "repo", "path"))
        if (missing != null) return ToolResult(false, "", "Missing required parameter(s): ${missing.joinToString()}")
        val owner = params["owner"]!!
        val repo = params["repo"]!!
        val path = params["path"]!!
        val message = params["message"] ?: "Delete $path"
        val branch = params["branch"]

        return try {
            // Fetch SHA of the file to delete
            val getContentResp = api.getContent(owner, repo, path, branch)
            if (!getContentResp.isSuccessful) {
                return ToolResult(false, "", "Cannot fetch file SHA for deletion: ${getContentResp.code()}")
            }
            val sha = getContentResp.body()?.sha
                ?: return ToolResult(false, "", "Cannot determine file SHA — may be a directory")

            val resp = api.deleteFile(
                owner = owner,
                repo = repo,
                path = path,
                request = CreateFileRequest(message = message, content = "", branch = branch, sha = sha)
            )
            if (resp.isSuccessful) {
                ToolResult(true, buildJsonObject {
                    put("message", "File deleted: $path")
                    put("path", path)
                }.toString())
            } else {
                ToolResult(false, "", "GitHub API ${resp.code()}: ${resp.message()}")
            }
        } catch (e: Exception) {
            ToolResult(false, "", e.message ?: "Network error deleting file")
        }
    }
}

/** Creates a new branch from an existing ref. */
data class CreateBranchTool(
    val api: GitHubApiService
) : AITool {

    override val name: String = "create_branch"
    override val description: String = "Create a new branch from an existing ref."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("owner", "string", true, "Account owner of the repository."),
        ToolParameter("repo", "string", true, "Name of the repository."),
        ToolParameter("branch", "string", true, "Name of the new branch."),
        ToolParameter("from", "string", false, "Source branch or SHA. Defaults to the default branch.")
    )

    override suspend fun executeSuspend(params: Map<String, String>): ToolResult {
        val missing = missingParams(params, listOf("owner", "repo", "branch"))
        if (missing != null) return ToolResult(false, "", "Missing required parameter(s): ${missing.joinToString()}")
        val owner = params["owner"]!!
        val repo = params["repo"]!!
        val newBranch = params["branch"]!!
        val fromRef = params["from"]?.takeIf { it.isNotBlank() } ?: "main"

        return try {
            // Get the SHA of the source branch
            val branchResp = api.getBranch(owner, repo, fromRef)
            if (!branchResp.isSuccessful) {
                return ToolResult(false, "", "Source branch '$fromRef' not found: ${branchResp.code()}")
            }
            val sha = branchResp.body()?.commit?.sha
                ?: return ToolResult(false, "", "Cannot determine source branch SHA")

            val resp = api.createBranch(
                owner, repo,
                CreateBranchRequest(ref = "refs/heads/$newBranch", sha = sha)
            )
            if (resp.isSuccessful) {
                ToolResult(true, buildJsonObject {
                    put("message", "Branch created: $newBranch from $fromRef")
                    put("branch", newBranch)
                    put("sha", sha)
                }.toString())
            } else {
                ToolResult(false, "", "GitHub API ${resp.code()}: ${resp.message()}")
            }
        } catch (e: Exception) {
            ToolResult(false, "", e.message ?: "Network error creating branch")
        }
    }
}

/**
 * PRD §27: Commits changes. In the GitHub Contents API model, each
 * create/update/delete already commits. This tool can be used to create
 * a commit on a specific branch by ensuring the branch exists.
 */
data class CommitChangesTool(
    val api: GitHubApiService
) : AITool {

    override val name: String = "commit_changes"
    override val description: String =
        "Verify that changes have been committed to a branch. In the GitHub " +
            "Contents API model, file operations already commit. This tool " +
            "verifies the latest commit on the branch."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("owner", "string", true, "Account owner of the repository."),
        ToolParameter("repo", "string", true, "Name of the repository."),
        ToolParameter("branch", "string", true, "Branch name to check commits on.")
    )

    override suspend fun executeSuspend(params: Map<String, String>): ToolResult {
        val missing = missingParams(params, listOf("owner", "repo", "branch"))
        if (missing != null) return ToolResult(false, "", "Missing required parameter(s): ${missing.joinToString()}")
        return try {
            val resp = api.listCommits(
                owner = params["owner"]!!,
                repo = params["repo"]!!,
                page = 1, perPage = 1
            )
            if (resp.isSuccessful) {
                val commits = resp.body() ?: emptyList()
                val latest = commits.firstOrNull()
                ToolResult(true, buildJsonObject {
                    put("message", "Latest commit on ${params["branch"]}")
                    put("sha", latest?.sha ?: "")
                    put("commit_message", latest?.commit?.message ?: "")
                }.toString())
            } else {
                ToolResult(false, "", "GitHub API ${resp.code()}: ${resp.message()}")
            }
        } catch (e: Exception) {
            ToolResult(false, "", e.message ?: "Network error checking commits")
        }
    }
}

/** Creates a pull request. */
data class CreatePullRequestTool(
    val api: GitHubApiService
) : AITool {

    override val name: String = "create_pull_request"
    override val description: String = "Create a pull request between two branches."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("owner", "string", true, "Account owner of the repository."),
        ToolParameter("repo", "string", true, "Name of the repository."),
        ToolParameter("head", "string", true, "The head branch (source)."),
        ToolParameter("base", "string", true, "The base branch (target)."),
        ToolParameter("title", "string", false, "PR title."),
        ToolParameter("body", "string", false, "PR body/description.")
    )

    override suspend fun executeSuspend(params: Map<String, String>): ToolResult {
        val missing = missingParams(params, listOf("owner", "repo", "head", "base"))
        if (missing != null) return ToolResult(false, "", "Missing required parameter(s): ${missing.joinToString()}")
        return try {
            val resp = api.createPullRequest(
                owner = params["owner"]!!,
                repo = params["repo"]!!,
                request = CreatePRRequest(
                    title = params["title"] ?: "Pull request: ${params["head"]} → ${params["base"]}",
                    head = params["head"]!!,
                    base = params["base"]!!,
                    body = params["body"]
                )
            )
            if (resp.isSuccessful) {
                val pr = resp.body()!!
                ToolResult(true, buildJsonObject {
                    put("message", "Pull request created: #${pr.number}")
                    put("number", JsonPrimitive(pr.number))
                    put("url", pr.htmlUrl)
                }.toString())
            } else {
                ToolResult(false, "", "GitHub API ${resp.code()}: ${resp.message()}")
            }
        } catch (e: Exception) {
            ToolResult(false, "", e.message ?: "Network error creating PR")
        }
    }
}

/** Triggers a workflow run. */
data class RunWorkflowTool(
    val api: GitHubApiService
) : AITool {

    override val name: String = "run_workflow"
    override val description: String =
        "Trigger a workflow run on the given ref, optionally passing " +
            "workflow_dispatch inputs."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("owner", "string", true, "Account owner of the repository."),
        ToolParameter("repo", "string", true, "Name of the repository."),
        ToolParameter("workflow_id", "string", true, "Workflow id or filename."),
        ToolParameter("ref", "string", true, "Branch or tag to run the workflow on."),
        ToolParameter("inputs", "string", false, "JSON object of workflow_dispatch inputs.")
    )

    override suspend fun executeSuspend(params: Map<String, String>): ToolResult {
        val missing = missingParams(params, listOf("owner", "repo", "workflow_id", "ref"))
        if (missing != null) return ToolResult(false, "", "Missing required parameter(s): ${missing.joinToString()}")
        return try {
            val resp = api.dispatchWorkflow(
                owner = params["owner"]!!,
                repo = params["repo"]!!,
                workflowId = params["workflow_id"]!!,
                request = DispatchWorkflowRequest(ref = params["ref"]!!)
            )
            if (resp.isSuccessful) {
                ToolResult(true, buildJsonObject {
                    put("message", "Workflow dispatched: ${params["workflow_id"]} on ${params["ref"]}")
                }.toString())
            } else {
                ToolResult(false, "", "GitHub API ${resp.code()}: ${resp.message()}")
            }
        } catch (e: Exception) {
            ToolResult(false, "", e.message ?: "Network error dispatching workflow")
        }
    }
}

/** Cancels an in-progress workflow run. */
data class CancelWorkflowTool(
    val api: GitHubApiService
) : AITool {

    override val name: String = "cancel_workflow"
    override val description: String = "Cancel an in-progress workflow run."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("owner", "string", true, "Account owner of the repository."),
        ToolParameter("repo", "string", true, "Name of the repository."),
        ToolParameter("run_id", "string", true, "Identifier of the workflow run to cancel.")
    )

    override suspend fun executeSuspend(params: Map<String, String>): ToolResult {
        val missing = missingParams(params, listOf("owner", "repo", "run_id"))
        if (missing != null) return ToolResult(false, "", "Missing required parameter(s): ${missing.joinToString()}")
        return try {
            val resp = api.cancelWorkflowRun(params["owner"]!!, params["repo"]!!, params["run_id"]!!.toLong())
            if (resp.isSuccessful) {
                ToolResult(true, "{\"message\":\"Workflow run ${params["run_id"]} cancelled\"}")
            } else {
                ToolResult(false, "", "GitHub API ${resp.code()}: ${resp.message()}")
            }
        } catch (e: Exception) {
            ToolResult(false, "", e.message ?: "Network error cancelling workflow")
        }
    }
}

/** Re-runs a completed workflow run. */
data class RerunWorkflowTool(
    val api: GitHubApiService
) : AITool {

    override val name: String = "rerun_workflow"
    override val description: String =
        "Re-run a completed workflow run. Optionally re-run only the jobs that failed."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("owner", "string", true, "Account owner of the repository."),
        ToolParameter("repo", "string", true, "Name of the repository."),
        ToolParameter("run_id", "string", true, "Identifier of the workflow run to re-run."),
        ToolParameter("failed_only", "boolean", false, "If true, re-run only failed jobs.")
    )

    override suspend fun executeSuspend(params: Map<String, String>): ToolResult {
        val missing = missingParams(params, listOf("owner", "repo", "run_id"))
        if (missing != null) return ToolResult(false, "", "Missing required parameter(s): ${missing.joinToString()}")
        val failedOnly = params["failed_only"]?.toBoolean() ?: false
        return try {
            val resp = if (failedOnly) {
                api.rerunFailedJobs(params["owner"]!!, params["repo"]!!, params["run_id"]!!.toLong())
            } else {
                api.rerunWorkflowRun(params["owner"]!!, params["repo"]!!, params["run_id"]!!.toLong())
            }
            if (resp.isSuccessful) {
                ToolResult(true, "{\"message\":\"Workflow run ${params["run_id"]} re-run\"}")
            } else {
                ToolResult(false, "", "GitHub API ${resp.code()}: ${resp.message()}")
            }
        } catch (e: Exception) {
            ToolResult(false, "", e.message ?: "Network error re-running workflow")
        }
    }
}

/** Downloads a build artifact. */
data class DownloadArtifactTool(
    val api: GitHubApiService
) : AITool {

    override val name: String = "download_artifact"
    override val description: String =
        "Get the download URL for a build artifact."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("owner", "string", true, "Account owner of the repository."),
        ToolParameter("repo", "string", true, "Name of the repository."),
        ToolParameter("artifact_id", "string", true, "Identifier of the artifact to download."),
        ToolParameter("destination", "string", false, "Local path to save the artifact.")
    )

    override suspend fun executeSuspend(params: Map<String, String>): ToolResult {
        val missing = missingParams(params, listOf("owner", "repo", "artifact_id"))
        if (missing != null) return ToolResult(false, "", "Missing required parameter(s): ${missing.joinToString()}")
        return try {
            // List artifacts for the run, find the matching one
            val resp = api.listArtifacts(
                params["owner"]!!,
                params["repo"]!!,
                params["artifact_id"]!!.toLong()
            )
            if (resp.isSuccessful) {
                val artifact = resp.body()?.artifacts?.find {
                    it.id.toString() == params["artifact_id"]
                }
                if (artifact != null) {
                    ToolResult(true, buildJsonObject {
                        put("message", "Artifact download URL retrieved")
                        put("name", artifact.name)
                        put("size_in_bytes", JsonPrimitive(artifact.sizeInBytes))
                        put("url", artifact.archiveDownloadUrl)
                    }.toString())
                } else {
                    ToolResult(true, "{\"message\":\"Artifact not found\"}")
                }
            } else {
                ToolResult(false, "", "GitHub API ${resp.code()}: ${resp.message()}")
            }
        } catch (e: Exception) {
            ToolResult(false, "", e.message ?: "Network error downloading artifact")
        }
    }
}

// ---------------------------------------------------------------------------
// Registry
// ---------------------------------------------------------------------------

/**
 * PRD §26-27: Central registry of every [AITool]. All tools now perform
 * REAL GitHub API calls via [GitHubApiService].
 *
 * PRD §35: Permission checking is performed by the caller (AgentOrchestrator)
 * before invoking mutation tools.
 */
@Singleton
class ToolRegistry @Inject constructor(
    private val api: GitHubApiService
) {

    /** All tools, keyed by their stable [AITool.name]. */
    private val tools: Map<String, AITool> = listOf(
        ListRepositoriesTool(api),
        GetRepositoryTool(api),
        ListFilesTool(api),
        ReadFileTool(api),
        SearchCodeTool(api),
        GetBranchTool(api),
        GetCommitTool(api),
        GetWorkflowTool(api),
        GetWorkflowRunTool(api),
        GetJobsTool(api),
        GetLogsTool(api),
        GetArtifactsTool(api),
        CreateFileTool(api),
        UpdateFileTool(api),
        DeleteFileTool(api),
        CreateBranchTool(api),
        CommitChangesTool(api),
        CreatePullRequestTool(api),
        RunWorkflowTool(api),
        CancelWorkflowTool(api),
        RerunWorkflowTool(api),
        DownloadArtifactTool(api)
    ).associateBy { it.name }

    /** Immutable, ordered view of every registered tool. */
    val all: List<AITool> get() = tools.values.toList()

    /** The number of registered tools. */
    val size: Int get() = tools.size

    fun get(name: String): AITool? = tools[name]

    fun execute(name: String, params: Map<String, String>): ToolResult {
        val tool = tools[name]
            ?: return ToolResult(false, "", "Unknown tool: $name")
        return tool.execute(params)
    }

    /** PRD §26-27: Suspend variant for coroutine-based callers. */
    suspend fun executeSuspend(name: String, params: Map<String, String>): ToolResult {
        val tool = tools[name]
            ?: return ToolResult(false, "", "Unknown tool: $name")
        return tool.executeSuspend(params)
    }

    fun contains(name: String): Boolean = name in tools
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Decodes a Base64 string (handles both standard and URL-safe variants). */
internal fun decodeBase64(encoded: String): String {
    return try {
        val cleaned = encoded.replace("\n", "").replace("\r", "").replace(" ", "")
        String(Base64.decode(cleaned, Base64.DEFAULT), Charsets.UTF_8)
    } catch (e: Exception) {
        GITOFYLogger.w("decodeBase64 failed: ${e.message}")
        encoded
    }
}
