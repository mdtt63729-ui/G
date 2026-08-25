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
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * GitHub REST API service.
 * PRD 12.2: Required API operations.
 */
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

    @GET("repos/{owner}/{repo}/actions/runs/{run_id}")
    suspend fun getWorkflowRun(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long
    ): Response<WorkflowRun>

    @GET("repos/{owner}/{repo}/actions/runs/{run_id}/jobs")
    suspend fun getWorkflowRunJobs(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long
    ): Response<JobList>

    @POST("repos/{owner}/{repo}/actions/workflows/{workflow_id}/dispatches")
    suspend fun dispatchWorkflow(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("workflow_id") workflowId: String,
        @Body request: DispatchWorkflowRequest
    ): Response<Unit>

    // Artifacts
    @GET("repos/{owner}/{repo}/actions/artifacts")
    suspend fun listArtifacts(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30
    ): Response<ArtifactList>

    @GET("repos/{owner}/{repo}/actions/runs/{run_id}/artifacts")
    suspend fun listRunArtifacts(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long
    ): Response<ArtifactList>

    // Rate Limits
    @GET("rate_limit")
    suspend fun getRateLimit(): Response<RateLimit>

    // PRD v4.0 — Pull Requests
    @GET("repos/{owner}/{repo}/pulls")
    suspend fun listPullRequests(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("state") state: String = "open",
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30
    ): Response<List<PullRequest>>

    @GET("repos/{owner}/{repo}/pulls/{pull_number}")
    suspend fun getPullRequest(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("pull_number") pullNumber: Int
    ): Response<PullRequest>

    @POST("repos/{owner}/{repo}/pulls")
    suspend fun createPullRequest(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: CreatePRRequest
    ): Response<PullRequest>

    @PATCH("repos/{owner}/{repo}/pulls/{pull_number}")
    suspend fun updatePullRequest(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("pull_number") pullNumber: Int,
        @Body request: CreatePRRequest
    ): Response<PullRequest>

    @PUT("repos/{owner}/{repo}/pulls/{pull_number}/merge")
    suspend fun mergePullRequest(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("pull_number") pullNumber: Int,
        @Body request: MergeRequest
    ): Response<Unit>

    @GET("repos/{owner}/{repo}/pulls/{pull_number}/reviews")
    suspend fun listReviews(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("pull_number") pullNumber: Int
    ): Response<List<Review>>

    @POST("repos/{owner}/{repo}/pulls/{pull_number}/reviews")
    suspend fun createReview(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("pull_number") pullNumber: Int,
        @Body request: CreateReviewRequest
    ): Response<Review>

    @GET("repos/{owner}/{repo}/pulls/{pull_number}/comments")
    suspend fun listPRComments(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("pull_number") pullNumber: Int
    ): Response<List<PRComment>>

    @POST("repos/{owner}/{repo}/pulls/{pull_number}/comments")
    suspend fun createPRComment(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("pull_number") pullNumber: Int,
        @Body request: CreateCommentRequest
    ): Response<PRComment>

    @GET("repos/{owner}/{repo}/pulls/{pull_number}/files")
    suspend fun getPRDiff(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("pull_number") pullNumber: Int
    ): Response<List<DiffEntry>>

    // Issues
    @GET("repos/{owner}/{repo}/issues")
    suspend fun listIssues(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("state") state: String = "open",
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30
    ): Response<List<Issue>>

    @GET("repos/{owner}/{repo}/issues/{issue_number}")
    suspend fun getIssue(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("issue_number") issueNumber: Int
    ): Response<Issue>

    @POST("repos/{owner}/{repo}/issues")
    suspend fun createIssue(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: CreateIssueRequest
    ): Response<Issue>

    @PATCH("repos/{owner}/{repo}/issues/{issue_number}")
    suspend fun updateIssue(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("issue_number") issueNumber: Int,
        @Body request: CreateIssueRequest
    ): Response<Issue>

    @POST("repos/{owner}/{repo}/issues/{issue_number}/comments")
    suspend fun createIssueComment(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("issue_number") issueNumber: Int,
        @Body request: CreateCommentRequest
    ): Response<PRComment>

    // Code Browser / File Operations
    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getContent(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Query("ref") ref: String? = null
    ): Response<List<ContentFile>>

    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun createOrUpdateFile(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Body request: CreateFileRequest
    ): Response<Unit>

    @DELETE("repos/{owner}/{repo}/contents/{path}")
    suspend fun deleteFile(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Query("message") message: String,
        @Query("sha") sha: String
    ): Response<Unit>

    // Branch Management
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

    @GET("repos/{owner}/{repo}/compare/{base}...{head}")
    suspend fun compareBranches(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("base") base: String,
        @Path("head") head: String
    ): Response<CompareResult>

    // Commit Explorer
    @GET("repos/{owner}/{repo}/commits/{sha}")
    suspend fun getCommit(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("sha") sha: String
    ): Response<Commit>

    // Workflow Controls (v4.5)
    @POST("repos/{owner}/{repo}/actions/runs/{run_id}/cancel")
    suspend fun cancelWorkflowRun(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long
    ): Response<Unit>

    @POST("repos/{owner}/{repo}/actions/runs/{run_id}/rerun")
    suspend fun rerunWorkflowRun(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long
    ): Response<Unit>

    @POST("repos/{owner}/{repo}/actions/runs/{run_id}/rerun-failed-jobs")
    suspend fun rerunFailedJobs(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long
    ): Response<Unit>

    // Releases (v6.0)
    @GET("repos/{owner}/{repo}/releases")
    suspend fun listReleases(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30
    ): Response<List<Release>>

    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<Release>

    @POST("repos/{owner}/{repo}/releases")
    suspend fun createRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: CreateReleaseRequest
    ): Response<Release>

    @GET("repos/{owner}/{repo}/tags")
    suspend fun listTags(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30
    ): Response<List<Tag>>

    // Organizations (v6.5)
    @GET("user/orgs")
    suspend fun listOrganizations(): Response<List<OrgDto>>
}
