package com.gitofy.navigation

/**
 * Complete navigation routes for GITOFY v4.0 → v7.0.
 * All routes are defined here and referenced by the NavHost.
 */
object Routes {
    // v3.0 existing
    const val SPLASH = "splash"
    const val AUTH = "auth"
    const val HOME = "home"
    const val REPOS = "repos"
    const val CREATE_PROJECT = "create_project"
    const val WORKFLOWS = "workflows"
    const val WORKFLOW_DETAIL = "workflow_detail/{owner}/{repo}/{runId}"
    const val LOGS = "logs/{owner}/{repo}/{jobId}"
    const val ARTIFACTS = "artifacts/{owner}/{repo}"
    const val SETTINGS = "settings"
    const val OPERATIONS = "operations"
    const val SEARCH = "search"

    // v4.0 — Developer Operations
    const val PULL_REQUESTS = "pulls/{owner}/{repo}"
    const val PULL_REQUEST_DETAIL = "pr_detail/{owner}/{repo}/{prNumber}"
    const val ISSUES = "issues/{owner}/{repo}"
    const val ISSUE_DETAIL = "issue_detail/{owner}/{repo}/{issueNumber}"
    const val BRANCHES = "branches/{owner}/{repo}"
    const val COMMITS = "commits/{owner}/{repo}"
    const val CODE_BROWSER = "code/{owner}/{repo}"
    const val REPO_HEALTH = "repo_health/{owner}/{repo}"
    const val COMMIT_COMPOSER = "commit_composer/{owner}/{repo}"

    // v4.5 — CI Intelligence
    const val CI_CONTROL_CENTER = "ci_center/{owner}/{repo}"
    const val BUILD_COMPARISON = "build_compare/{owner}/{repo}/{runId1}/{runId2}"

    // v5.0 — AI Assistant
    const val AI_ASSISTANT = "ai_assistant"
    const val AI_ANALYSIS = "ai_analysis/{owner}/{repo}/{runId}"

    // v5.5 — Developer Workspace
    const val WORKSPACE = "workspace/{owner}/{repo}"
    const val CODE_EDITOR = "code_editor/{owner}/{repo}/{filePath}"

    // v6.0 — Release & Deployment
    const val RELEASES = "releases/{owner}/{repo}"
    const val CREATE_RELEASE = "create_release/{owner}/{repo}"

    // v6.5 — Team & Organization
    const val ORGANIZATIONS = "organizations"
    const val ACCOUNT_SWITCHER = "account_switcher"

    // v7.0 — Intelligence Dashboard
    const val COMMAND_CENTER = "command_center"
    const val ATTENTION_CENTER = "attention_center"
    const val ACTIVITY_TIMELINE = "activity_timeline"
    const val REPOSITORY_HEALTH_SCORE = "repo_score/{owner}/{repo}"
    const val CI_FLEET = "ci_fleet"

    // Helper functions for route construction
    fun pulls(owner: String, repo: String) = "pulls/$owner/$repo"
    fun prDetail(owner: String, repo: String, prNumber: Int) = "pr_detail/$owner/$repo/$prNumber"
    fun issues(owner: String, repo: String) = "issues/$owner/$repo"
    fun branches(owner: String, repo: String) = "branches/$owner/$repo"
    fun codeBrowser(owner: String, repo: String) = "code/$owner/$repo"
    fun repoHealth(owner: String, repo: String) = "repo_health/$owner/$repo"
    fun ciCenter(owner: String, repo: String) = "ci_center/$owner/$repo"
    fun releases(owner: String, repo: String) = "releases/$owner/$repo"
    fun createRelease(owner: String, repo: String) = "create_release/$owner/$repo"
    fun workspace(owner: String, repo: String) = "workspace/$owner/$repo"
}
