package com.gitofy.data.mapper

import com.gitofy.data.local.entity.*
import com.gitofy.data.remote.dto.*
import com.gitofy.domain.model.*

/**
 * Mappers — convert DTOs to domain models and Room entities.
 * PRD 9.1: Mappers in data layer.
 */

fun GitHubUser.toDomain(): User = User(
    login = login,
    avatarUrl = avatarUrl,
    name = name,
    bio = bio,
    publicRepos = publicRepos,
    followers = followers,
    following = following
)

fun GitHubUser.toEntity(): UserEntity = UserEntity(
    login = login,
    avatarUrl = avatarUrl,
    name = name,
    bio = bio,
    publicRepos = publicRepos,
    followers = followers,
    following = following
)

fun Repository.toDomain(): RepoSummary = RepoSummary(
    id = id,
    name = name,
    fullName = fullName,
    ownerLogin = ownerLogin,
    ownerAvatar = owner?.avatarUrl ?: "",
    isPrivate = private,
    description = description,
    htmlUrl = htmlUrl,
    defaultBranch = defaultBranch,
    stars = stargazersCount,
    forks = forksCount,
    updatedAt = updatedAt
)

fun Repository.toEntity(): RepositoryEntity = RepositoryEntity(
    id = id,
    name = name,
    fullName = fullName,
    ownerLogin = ownerLogin,
    ownerAvatar = owner?.avatarUrl ?: "",
    isPrivate = private,
    description = description,
    htmlUrl = htmlUrl,
    defaultBranch = defaultBranch,
    stargazersCount = stargazersCount,
    forksCount = forksCount
)

fun RepositoryEntity.toDomain(): RepoSummary = RepoSummary(
    id = id,
    name = name,
    fullName = fullName,
    ownerLogin = ownerLogin,
    ownerAvatar = ownerAvatar,
    isPrivate = isPrivate,
    description = description,
    htmlUrl = htmlUrl,
    defaultBranch = defaultBranch,
    stars = stargazersCount,
    forks = forksCount,
    updatedAt = ""
)

fun Repository.toDetails(): RepoDetails = RepoDetails(
    id = id,
    name = name,
    fullName = fullName,
    ownerLogin = ownerLogin,
    ownerAvatar = owner?.avatarUrl ?: "",
    isPrivate = private,
    description = description,
    htmlUrl = htmlUrl,
    defaultBranch = defaultBranch,
    stars = stargazersCount,
    forks = forksCount,
    openIssues = openIssuesCount
)

fun Branch.toDomain(): BranchInfo = BranchInfo(
    name = name,
    commitSha = commit?.sha ?: ""
)

fun Branch.toEntity(repoId: Long): BranchEntity = BranchEntity(
    name = name,
    repoId = repoId,
    commitSha = commit?.sha ?: ""
)

fun Commit.toDomain(): CommitInfo = CommitInfo(
    sha = sha,
    message = commit?.message ?: "",
    authorName = commit?.author?.name ?: (author?.login ?: ""),
    authorAvatar = author?.avatarUrl ?: "",
    date = commit?.author?.date ?: ""
)

fun Commit.toEntity(repoId: Long): CommitEntity = CommitEntity(
    sha = sha,
    repoId = repoId,
    message = commit?.message ?: "",
    authorName = commit?.author?.name ?: (author?.login ?: ""),
    authorAvatar = author?.avatarUrl ?: "",
    date = commit?.author?.date ?: ""
)

fun Workflow.toDomain(): WorkflowSummary = WorkflowSummary(
    id = id,
    name = name,
    path = path,
    state = state
)

fun Workflow.toEntity(repoId: Long): WorkflowEntity = WorkflowEntity(
    id = id,
    repoId = repoId,
    name = name,
    path = path,
    state = state
)

fun WorkflowRun.toDomain(): WorkflowRunSummary = WorkflowRunSummary(
    id = id,
    name = name,
    displayTitle = displayTitle.ifEmpty { name },
    headBranch = headBranch,
    status = WorkflowStatus.fromGitHubStatus(status, conclusion),
    createdAt = createdAt,
    updatedAt = updatedAt,
    actorLogin = actor?.login ?: "",
    htmlUrl = htmlUrl
)

fun WorkflowRun.toEntity(repoId: Long): WorkflowRunEntity = WorkflowRunEntity(
    id = id,
    repoId = repoId,
    name = name,
    displayTitle = displayTitle,
    headBranch = headBranch,
    headSha = headSha,
    status = status,
    conclusion = conclusion,
    createdAt = createdAt,
    updatedAt = updatedAt,
    actorLogin = actor?.login ?: ""
)

fun WorkflowRunEntity.toDomain(): WorkflowRunSummary = WorkflowRunSummary(
    id = id,
    name = name,
    displayTitle = displayTitle,
    headBranch = headBranch,
    status = WorkflowStatus.fromGitHubStatus(status, conclusion),
    createdAt = createdAt,
    updatedAt = updatedAt,
    actorLogin = actorLogin,
    htmlUrl = ""
)

fun Job.toDomain(): JobSummary = JobSummary(
    id = id,
    name = name,
    status = status,
    conclusion = conclusion,
    startedAt = startedAt,
    completedAt = completedAt,
    htmlUrl = htmlUrl,
    steps = steps.map { it.toDomain() }
)

fun Step.toDomain(): StepSummary = StepSummary(
    name = name,
    status = status,
    conclusion = conclusion,
    number = number
)

fun Job.toEntity(): JobEntity = JobEntity(
    id = id,
    runId = runId,
    name = name,
    status = status,
    conclusion = conclusion,
    startedAt = startedAt,
    completedAt = completedAt,
    htmlUrl = htmlUrl
)

fun Artifact.toDomain(): ArtifactSummary = ArtifactSummary(
    id = id,
    name = name,
    sizeInBytes = sizeInBytes,
    archiveDownloadUrl = archiveDownloadUrl,
    expired = expired,
    createdAt = createdAt,
    expiresAt = expiresAt
)

fun Artifact.toEntity(runId: Long): ArtifactEntity = ArtifactEntity(
    id = id,
    runId = runId,
    name = name,
    sizeInBytes = sizeInBytes,
    archiveDownloadUrl = archiveDownloadUrl,
    expired = expired,
    createdAt = createdAt,
    expiresAt = expiresAt
)

fun ArtifactEntity.toDomain(): ArtifactSummary = ArtifactSummary(
    id = id,
    name = name,
    sizeInBytes = sizeInBytes,
    archiveDownloadUrl = archiveDownloadUrl,
    expired = expired,
    createdAt = createdAt,
    expiresAt = expiresAt
)
