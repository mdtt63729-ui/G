package com.gitofy.ai.context

import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PRD §28: AI Context Scope selector.
 *
 * Manages the current "context scope" that the AI agent operates within. A scope
 * narrows what the assistant can see and act on — e.g. restricted to a single
 * repository, a workflow run, a job, a file, a text selection, or a build
 * failure. The scope is mutable through the builder-style setters below and can
 * be read atomically via [getContext].
 */
@Singleton
class AIContextScopeManager @Inject constructor() {

    /**
     * The broad category of context the assistant should consider.
     *
     * @property displayName a human-readable label suitable for UI surfaces.
     */
    enum class ContextScope(val displayName: String) {
        GENERAL("General"),
        REPOSITORY("Repository"),
        WORKFLOW("Workflow"),
        JOB("Job"),
        FILE("File"),
        SELECTION("Selection"),
        BUILD_FAILURE("Build Failure")
    }

    /**
     * Captured information about a failed build, used to give the assistant
     * targeted context for debugging.
     *
     * @property workflowName the name of the workflow that contained the failure.
     * @property failedJob    the name of the job that failed.
     * @property failedStep    the name of the step that failed.
     * @property logSnippet    a short extract of the relevant log output.
     */
    data class BuildFailureInfo(
        val workflowName: String,
        val failedJob: String,
        val failedStep: String,
        val logSnippet: String
    )

    /**
     * The full context payload handed to the assistant.
     *
     * Any field that is not relevant to the active [scope] is `null`.
     *
     * @property scope             the active context scope.
     * @property repositoryOwner   the owner of the scoped repository (GitHub login), if any.
     * @property repositoryName    the name of the scoped repository, if any.
     * @property workflowRunId     the id of the scoped workflow run, if any.
     * @property jobId             the id of the scoped job, if any.
     * @property filePath          the path of the scoped file, if any.
     * @property buildFailureInfo  build-failure details, if any.
     */
    data class AIContext(
        val scope: ContextScope,
        val repositoryOwner: String? = null,
        val repositoryName: String? = null,
        val workflowRunId: Long? = null,
        val jobId: Long? = null,
        val filePath: String? = null,
        val buildFailureInfo: BuildFailureInfo? = null
    )

    private val current: AtomicReference<AIContext> =
        AtomicReference(AIContext(scope = ContextScope.GENERAL))

    /**
     * Sets the active [scope], preserving any previously-supplied context fields
     * (repository, workflow, job, file, build failure) so that switching scope
     * does not silently discard finer-grained context the caller may still want.
     *
     * @param scope the new context scope.
     */
    fun setScope(scope: ContextScope) {
        current.updateAndGet { it.copy(scope = scope) }
    }

    /**
     * Returns the currently active scope.
     *
     * @return the active [ContextScope].
     */
    fun getScope(): ContextScope = current.get().scope

    /**
     * Records the scoped repository and switches the active scope to
     * [ContextScope.REPOSITORY].
     *
     * @param owner the repository owner (GitHub login).
     * @param name  the repository name.
     */
    fun setRepository(owner: String, name: String) {
        current.updateAndGet {
            it.copy(
                scope = ContextScope.REPOSITORY,
                repositoryOwner = owner,
                repositoryName = name
            )
        }
    }

    /**
     * Records the scoped workflow run id and switches the active scope to
     * [ContextScope.WORKFLOW].
     *
     * @param runId the workflow run id.
     */
    fun setWorkflowRun(runId: Long) {
        current.updateAndGet {
            it.copy(
                scope = ContextScope.WORKFLOW,
                workflowRunId = runId
            )
        }
    }

    /**
     * Records the scoped job id and switches the active scope to
     * [ContextScope.JOB].
     *
     * @param jobId the job id.
     */
    fun setJob(jobId: Long) {
        current.updateAndGet {
            it.copy(
                scope = ContextScope.JOB,
                jobId = jobId
            )
        }
    }

    /**
     * Records the scoped file path and switches the active scope to
     * [ContextScope.FILE].
     *
     * @param path the absolute or repository-relative file path.
     */
    fun setFile(path: String) {
        current.updateAndGet {
            it.copy(
                scope = ContextScope.FILE,
                filePath = path
            )
        }
    }

    /**
     * Records build-failure details and switches the active scope to
     * [ContextScope.BUILD_FAILURE].
     *
     * @param info the build-failure information to attach.
     */
    fun setBuildFailure(info: BuildFailureInfo) {
        current.updateAndGet {
            it.copy(
                scope = ContextScope.BUILD_FAILURE,
                buildFailureInfo = info
            )
        }
    }

    /**
     * Returns a snapshot of the full active context.
     *
     * @return the current [AIContext].
     */
    fun getContext(): AIContext = current.get()

    /**
     * Resets the context to a fresh [ContextScope.GENERAL] state with no
     * repository, workflow, job, file, or build-failure information attached.
     */
    fun clear() {
        current.set(AIContext(scope = ContextScope.GENERAL))
    }
}
