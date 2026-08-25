package com.gitofy.domain.usecase

import com.gitofy.core.network.GitHubApiService
import com.gitofy.domain.model.*
import javax.inject.Inject

// PRD v6.0 — Release use cases

class GetReleasesUseCase @Inject constructor(private val api: GitHubApiService) {
    suspend operator fun invoke(owner: String, repo: String): Result<List<ReleaseSummary>> = runCatching {
        val response = api.listReleases(owner, repo)
        if (!response.isSuccessful) throw RuntimeException("Failed: ${response.code()}")
        response.body()?.map { it.toSummary() } ?: emptyList()
    }
}

class CreateReleaseUseCase @Inject constructor(private val api: GitHubApiService) {
    suspend operator fun invoke(owner: String, repo: String, tag: String, title: String?, body: String?, isDraft: Boolean, isPreRelease: Boolean): Result<Long> = runCatching {
        val response = api.createRelease(owner, repo, com.gitofy.data.remote.dto.CreateReleaseRequest(tag, "main", title, body, isDraft, isPreRelease))
        if (!response.isSuccessful) throw RuntimeException("Create failed: ${response.code()}")
        response.body()?.id ?: throw RuntimeException("Empty response")
    }
}

class GetTagsUseCase @Inject constructor(private val api: GitHubApiService) {
    suspend operator fun invoke(owner: String, repo: String): Result<List<TagInfo>> = runCatching {
        val response = api.listTags(owner, repo)
        if (!response.isSuccessful) throw RuntimeException("Failed: ${response.code()}")
        response.body()?.map { TagInfo(it.name, it.commit?.sha ?: "", null) } ?: emptyList()
    }
}

// PRD v6.5 — Organization use cases

class GetOrganizationsUseCase @Inject constructor(private val api: GitHubApiService) {
    suspend operator fun invoke(): Result<List<OrganizationSummary>> = runCatching {
        val response = api.listOrganizations()
        if (!response.isSuccessful) throw RuntimeException("Failed: ${response.code()}")
        response.body()?.map { OrganizationSummary(it.login, it.avatarUrl, it.description, it.publicRepos) } ?: emptyList()
    }
}

// Mappers
private fun com.gitofy.data.remote.dto.Release.toSummary() = ReleaseSummary(
    id = id, tagName = tagName, name = name ?: "", body = body,
    isDraft = draft, isPreRelease = preRelease,
    createdAt = createdAt, publishedAt = publishedAt,
    authorLogin = author?.login ?: "", htmlUrl = htmlUrl,
    assetCount = assets.size
)
