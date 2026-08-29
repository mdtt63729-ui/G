package com.gitofy.domain.usecase

import com.gitofy.core.network.GitHubApiService
import com.gitofy.domain.model.*
import javax.inject.Inject

// PRD v6.0 — Release use cases

class GetReleasesUseCase @Inject constructor(private val api: GitHubApiService) {
    suspend operator fun invoke(owner: String, repo: String): Result<List<ReleaseSummary>> = runCatching {
        val response = api.listReleases(owner, repo)
        if (!response.isSuccessful) throw RuntimeException("Failed: ${'$'}{response.code()}")
        val body = response.body() ?: emptyList()
        body.map { rel -> ReleaseSummary(
            id = rel.id,
            tagName = rel.tagName,
            name = rel.name ?: rel.tagName,
            body = rel.body,
            isDraft = rel.draft,
            isPreRelease = rel.preRelease,
            createdAt = rel.createdAt,
            publishedAt = rel.publishedAt,
            authorLogin = rel.author?.login ?: "",
            htmlUrl = rel.htmlUrl,
            assetCount = rel.assets.size
        ) }
    }
}

class CreateReleaseUseCase @Inject constructor(private val api: GitHubApiService) {
    suspend operator fun invoke(owner: String, repo: String, tag: String, title: String?, body: String?, isDraft: Boolean, isPreRelease: Boolean): Result<Long> = runCatching {
        val response = api.createRelease(owner, repo, com.gitofy.data.remote.dto.CreateReleaseRequest(tagName = tag, targetCommitish = "main", name = title, body = body, draft = isDraft, preRelease = isPreRelease))
        if (!response.isSuccessful) throw RuntimeException("Create failed: ${'$'}{response.code()}")
        response.body()?.id ?: throw RuntimeException("Empty response")
    }
}

class GetTagsUseCase @Inject constructor(private val api: GitHubApiService) {
    suspend operator fun invoke(owner: String, repo: String): Result<List<TagInfo>> = runCatching {
        val response = api.listTags(owner, repo)
        if (!response.isSuccessful) throw RuntimeException("Failed: ${'$'}{response.code()}")
        val body = response.body() ?: emptyList()
        body.map { TagInfo(it.name, it.commit?.sha ?: "", null) }
    }
}

// PRD v6.5 — Organization use cases

class GetOrganizationsUseCase @Inject constructor(private val api: GitHubApiService) {
    suspend operator fun invoke(): Result<List<OrganizationSummary>> = runCatching {
        val response = api.listOrganizations()
        if (!response.isSuccessful) throw RuntimeException("Failed: ${'$'}{response.code()}")
        val body = response.body() ?: emptyList()
        body.map { OrganizationSummary(it.login, it.avatarUrl, it.description, it.publicRepos) }
    }
}
