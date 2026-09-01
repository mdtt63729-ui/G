package com.gitofy.core.network

import com.gitofy.data.remote.dto.CreateRepoRequest
import com.gitofy.data.remote.dto.GitHubUser
import com.gitofy.data.remote.dto.RateLimit
import com.gitofy.data.remote.dto.Repository
import com.gitofy.data.remote.dto.Branch
import com.gitofy.data.remote.dto.Commit
import com.gitofy.data.remote.dto.Workflow
import com.gitofy.data.remote.dto.WorkflowRun
import com.gitofy.data.remote.dto.WorkflowRunList
import com.gitofy.data.remote.dto.WorkflowList
import com.gitofy.data.remote.dto.Job
import com.gitofy.data.remote.dto.JobList
import com.gitofy.data.remote.dto.Artifact
import com.gitofy.data.remote.dto.ArtifactList
import com.gitofy.data.remote.dto.DispatchWorkflowRequest
import com.gitofy.data.remote.dto.PullRequest
import com.gitofy.data.remote.dto.PullRequestList
import com.gitofy.data.remote.dto.CreatePRRequest
import com.gitofy.data.remote.dto.MergeRequest
import com.gitofy.data.remote.dto.Review
import com.gitofy.data.remote.dto.CreateReviewRequest
import com.gitofy.data.remote.dto.PRComment
import com.gitofy.data.remote.dto.CreateCommentRequest
import com.gitofy.data.remote.dto.DiffEntry
import com.gitofy.data.remote.dto.Issue
import com.gitofy.data.remote.dto.IssueList
import com.gitofy.data.remote.dto.CreateIssueRequest
import com.gitofy.data.remote.dto.ContentFile
import com.gitofy.data.remote.dto.CreateFileRequest
import com.gitofy.data.remote.dto.FileCommitResponse
import com.gitofy.data.remote.dto.CreateBranchRequest
import com.gitofy.data.remote.dto.CompareResult
import com.gitofy.data.remote.dto.CodeSearchResult
import com.gitofy.data.remote.dto.RepositorySearchResult
import com.gitofy.data.remote.dto.Release
import com.gitofy.data.remote.dto.CreateReleaseRequest
import com.gitofy.data.remote.dto.Tag
import com.gitofy.data.remote.dto.OrgDto
import com.gitofy.data.remote.dto.GitTreeResponse
import com.gitofy.data.remote.dto.CreateGitBlobRequest
import com.gitofy.data.remote.dto.GitBlobResponse
import com.gitofy.data.remote.dto.CreateGitTreeRequest
import com.gitofy.data.remote.dto.GitTreeCreateResponse
import com.gitofy.data.remote.dto.CreateGitCommitRequest
import com.gitofy.data.remote.dto.GitCommitResponse
import com.gitofy.data.remote.dto.UpdateGitRefRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Streaming
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface GitHubApiService {

    // Account
    @GET("user")
    suspend fun getAuthenticatedUser(): Response<GitHubUser>

    // Repositories
    @GET("user/repos")
    suspend fun listRepositories(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30,
        @Query("sort") sort: String = "updated"
    ): Response<List<Repository>>

    @POST("user/repos")
    suspend fun createRepository(@Body request: CreateRepoRequest): Response<Repository>

    @DELETE("repos/{owner}/{repo}")
    suspend fun deleteRepository(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<Unit>

    @GET("repos/{owner}/{repo}")
    suspend fun getRepository(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<Repository>

    @GET("repos/{owner}/{repo}/branches")
    suspend fun listBranches(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30
    ): Response<List<Branch>>

    @GET("repos/{owner}/{repo}/commits")
    suspend fun listCommits(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30
    ): Response<List<Commit>>

    // Workflows
    @GET("repos/{owner}/{repo}/actions/workflows")
    suspend fun listWorkflows(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<WorkflowList>

    @GET("repos/{owner}/{repo}/actions/runs")
    suspend fun listWorkflowRuns(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30
    ): Response<WorkflowRunList>

    @GET("repos/{owner}/{repo}/actions/runs/{runId}")
    suspend fun getWorkflowRun(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("runId") runId: Long
    ): Response<WorkflowRun>

    @POST("repos/{owner}/{repo}/actions/workflows/{workflowId}/dispatches")
    suspend fun dispatchWorkflow(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("workflowId") workflowId: String,
        @Body request: DispatchWorkflowRequest
    ): Response<Unit>

    @GET("repos/{owner}/{repo}/actions/runs/{runId}/jobs")
    suspend fun listJobs(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("runId") runId: Long
    ): Response<JobList>

    // PRD §9/§30: Download job logs (returns a redirect to a zip; OkHttp follows)
    @Streaming
    @GET("repos/{owner}/{repo}/actions/jobs/{jobId}/logs")
    suspend fun downloadJobLogs(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("jobId") jobId: Long
    ): Response<okhttp3.ResponseBody>

    // PRD §9/§30: Download entire workflow run logs
    @GET("repos/{owner}/{repo}/actions/runs/{runId}/logs")
    suspend fun downloadRunLogs(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("runId") runId: Long
    ): Response<okhttp3.ResponseBody>

    // PRD §3.2/§27: List workflow runs filtered by workflow_id
    @GET("repos/{owner}/{repo}/actions/workflows/{workflowId}/runs")
    suspend fun listWorkflowRunsByWorkflow(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("workflowId") workflowId: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30
    ): Response<WorkflowRunList>

    // PRD §36: Get a single job by ID (for repair targeting)
    @GET("repos/{owner}/{repo}/actions/jobs/{jobId}")
    suspend fun getJob(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("jobId") jobId: Long
    ): Response<Job>

    // Artifacts
    @GET("repos/{owner}/{repo}/actions/runs/{runId}/artifacts")
    suspend fun listArtifacts(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("runId") runId: Long
    ): Response<ArtifactList>

    // Pull Requests
    @GET("repos/{owner}/{repo}/pulls")
    suspend fun listPullRequests(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("state") state: String = "open"
    ): Response<List<PullRequest>>

    @GET("repos/{owner}/{repo}/pulls/{prNumber}")
    suspend fun getPullRequest(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("prNumber") prNumber: Int
    ): Response<PullRequest>

    @POST("repos/{owner}/{repo}/pulls")
    suspend fun createPullRequest(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: CreatePRRequest
    ): Response<PullRequest>

    @PATCH("repos/{owner}/{repo}/pulls/{prNumber}")
    suspend fun updatePullRequest(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("prNumber") prNumber: Int,
        @Body request: CreatePRRequest
    ): Response<PullRequest>

    @PUT("repos/{owner}/{repo}/pulls/{prNumber}/merge")
    suspend fun mergePullRequest(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("prNumber") prNumber: Int,
        @Body request: MergeRequest
    ): Response<Unit>

    // Reviews
    @GET("repos/{owner}/{repo}/pulls/{prNumber}/reviews")
    suspend fun listReviews(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("prNumber") prNumber: Int
    ): Response<List<Review>>

    @POST("repos/{owner}/{repo}/pulls/{prNumber}/reviews")
    suspend fun createReview(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("prNumber") prNumber: Int,
        @Body request: CreateReviewRequest
    ): Response<Review>

    // PR Comments
    @GET("repos/{owner}/{repo}/issues/{prNumber}/comments")
    suspend fun listPRComments(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("prNumber") prNumber: Int
    ): Response<List<PRComment>>

    @POST("repos/{owner}/{repo}/issues/{prNumber}/comments")
    suspend fun createComment(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("prNumber") prNumber: Int,
        @Body request: CreateCommentRequest
    ): Response<PRComment>

    // PR Diff/Files
    @GET("repos/{owner}/{repo}/pulls/{prNumber}/files")
    suspend fun getPullRequestFiles(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("prNumber") prNumber: Int
    ): Response<List<DiffEntry>>

    // Issues
    @GET("repos/{owner}/{repo}/issues")
    suspend fun listIssues(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("state") state: String = "open"
    ): Response<List<Issue>>

    @GET("repos/{owner}/{repo}/issues/{issueNumber}")
    suspend fun getIssue(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("issueNumber") issueNumber: Int
    ): Response<Issue>

    @POST("repos/{owner}/{repo}/issues")
    suspend fun createIssue(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: CreateIssueRequest
    ): Response<Issue>

    @PATCH("repos/{owner}/{repo}/issues/{issueNumber}")
    suspend fun updateIssue(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("issueNumber") issueNumber: Int,
        @Body request: CreateIssueRequest
    ): Response<Issue>

    @POST("repos/{owner}/{repo}/issues/{issueNumber}/comments")
    suspend fun createIssueComment(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("issueNumber") issueNumber: Int,
        @Body request: CreateCommentRequest
    ): Response<PRComment>

    // Repository discovery/search

    @GET("search/repositories")
    suspend fun searchRepositories(
        @Query("q") query: String,
        @Query("sort") sort: String? = null,
        @Query("order") order: String? = null,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30
    ): Response<RepositorySearchResult>

    // Code Search — PRD §27
    @GET("search/code")
    suspend fun searchCode(
        @Query("q") query: String
    ): Response<CodeSearchResult>

    // Content/File operations
    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getContent(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Query("ref") ref: String? = null
    ): Response<ContentFile>

    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun createOrUpdateFile(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Body request: CreateFileRequest
    ): Response<FileCommitResponse>

    @DELETE("repos/{owner}/{repo}/contents/{path}")
    suspend fun deleteFile(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Body request: CreateFileRequest
    ): Response<Unit>

    // Branches
    @GET("repos/{owner}/{repo}/branches/{branch}")
    suspend fun getBranch(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch") branch: String
    ): Response<Branch>

    @POST("repos/{owner}/{repo}/git/refs")
    suspend fun createBranch(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: CreateBranchRequest
    ): Response<Unit>

    @DELETE("repos/{owner}/{repo}/git/refs/heads/{branch}")
    suspend fun deleteBranch(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch") branch: String
    ): Response<Unit>

    // Compare
    @GET("repos/{owner}/{repo}/compare/{base}...{head}")
    suspend fun compareCommits(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("base") base: String,
        @Path("head") head: String
    ): Response<CompareResult>

    // Git Trees — PRD §33: Used by RepositorySyncEngine to load the remote
    // file tree for diff comparison during update operations.
    @GET("repos/{owner}/{repo}/git/trees/{treeSha}")
    suspend fun getGitTree(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("treeSha") treeSha: String,
        @Query("recursive") recursive: Int = 1
    ): Response<GitTreeResponse>

    // Git Data API — create blobs/tree/commit and update the branch ref as one
    // logical repository update. This prevents one GitHub Actions run per file.
    @POST("repos/{owner}/{repo}/git/blobs")
    suspend fun createGitBlob(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: CreateGitBlobRequest
    ): Response<GitBlobResponse>

    @POST("repos/{owner}/{repo}/git/trees")
    suspend fun createGitTree(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: CreateGitTreeRequest
    ): Response<GitTreeCreateResponse>

    @POST("repos/{owner}/{repo}/git/commits")
    suspend fun createGitCommit(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: CreateGitCommitRequest
    ): Response<GitCommitResponse>

    @PATCH("repos/{owner}/{repo}/git/refs/heads/{branch}")
    suspend fun updateGitBranchRef(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch") branch: String,
        @Body request: UpdateGitRefRequest
    ): Response<Unit>

    // Releases
    @GET("repos/{owner}/{repo}/releases")
    suspend fun listReleases(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20
    ): Response<List<Release>>

    @GET("repos/{owner}/{repo}/releases/{releaseId}")
    suspend fun getRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("releaseId") releaseId: Long
    ): Response<Release>

    @POST("repos/{owner}/{repo}/releases")
    suspend fun createRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: CreateReleaseRequest
    ): Response<Release>

    // Tags
    @GET("repos/{owner}/{repo}/tags")
    suspend fun listTags(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<List<Tag>>

    // Organizations
    @GET("user/orgs")
    suspend fun listOrganizations(): Response<List<OrgDto>>

    // Workflow Control
    @POST("repos/{owner}/{repo}/actions/runs/{runId}/cancel")
    suspend fun cancelWorkflowRun(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("runId") runId: Long
    ): Response<Unit>

    @POST("repos/{owner}/{repo}/actions/runs/{runId}/rerun")
    suspend fun rerunWorkflowRun(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("runId") runId: Long
    ): Response<Unit>

    @POST("repos/{owner}/{repo}/actions/runs/{runId}/rerun-failed-jobs")
    suspend fun rerunFailedJobs(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("runId") runId: Long
    ): Response<Unit>

    // Rate Limit
    @GET("rate_limit")
    suspend fun getRateLimit(): Response<RateLimit>

    // Notifications (PRD §86)
    @GET("notifications")
    suspend fun listNotifications(
        @Query("all") all: Boolean = false,
        @Query("participating") participating: Boolean = false,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50
    ): Response<List<com.gitofy.data.remote.dto.GitHubNotification>>

    @PUT("notifications")
    suspend fun markAllNotificationsAsRead(): Response<Unit>

    @PATCH("notifications/threads/{threadId}")
    suspend fun markThreadAsRead(
        @Path("threadId") threadId: String
    ): Response<Unit>

    @DELETE("notifications/threads/{threadId}")
    suspend fun markThreadAsDone(
        @Path("threadId") threadId: String
    ): Response<Unit>

    @GET("notifications/threads/{threadId}")
    suspend fun getThread(
        @Path("threadId") threadId: String
    ): Response<com.gitofy.data.remote.dto.GitHubNotification>

    @GET("notifications/threads/{threadId}/subscription")
    suspend fun getThreadSubscription(
        @Path("threadId") threadId: String
    ): Response<com.gitofy.data.remote.dto.ThreadSubscription>

    @PUT("notifications/threads/{threadId}/subscription")
    suspend fun setThreadSubscription(
        @Path("threadId") threadId: String,
        @Body body: com.gitofy.data.remote.dto.SetSubscriptionRequest
    ): Response<com.gitofy.data.remote.dto.ThreadSubscription>

    @DELETE("notifications/threads/{threadId}/subscription")
    suspend fun deleteThreadSubscription(
        @Path("threadId") threadId: String
    ): Response<Unit>
}
