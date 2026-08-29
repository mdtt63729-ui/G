package com.gitofy.data.repository

import com.gitofy.core.network.GitHubApiService
import com.gitofy.core.network.safeApiCall
import com.gitofy.data.local.dao.RepositoryDao
import com.gitofy.data.local.dao.BranchDao
import com.gitofy.data.local.dao.CommitDao
import com.gitofy.data.mapper.toDomain
import com.gitofy.data.mapper.toEntity
import com.gitofy.data.mapper.toDetails
import com.gitofy.data.remote.dto.CreateRepoRequest
import com.gitofy.domain.model.*
import com.gitofy.domain.repository.GitHubRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubRepositoryImpl @Inject constructor(
    private val apiService: GitHubApiService,
    private val repositoryDao: RepositoryDao,
    private val branchDao: BranchDao,
    private val commitDao: CommitDao
) : GitHubRepository {

    override fun observeRepositories(): Flow<List<RepoSummary>> =
        repositoryDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun refreshRepositories(page: Int): Result<List<RepoSummary>> {
        val result = safeApiCall { apiService.listRepositories(page = page) }
        return result.fold(
            onSuccess = { repos ->
                val entities = repos.map { it.toEntity() }
                repositoryDao.upsertAll(entities)
                Result.success(repos.map { it.toDomain() })
            },
            onFailure = { Result.failure(it) }
        )
    }

    override suspend fun getRepository(owner: String, repo: String): Result<RepoDetails> {
        val result = safeApiCall { apiService.getRepository(owner, repo) }
        return result.fold(
            onSuccess = { Result.success(it.toDetails()) },
            onFailure = { Result.failure(it) }
        )
    }

    override suspend fun createRepository(
        name: String, description: String?, isPrivate: Boolean
    ): Result<RepoSummary> {
        val request = CreateRepoRequest(
            name = name,
            description = description,
            private = isPrivate,
            autoInit = false
        )
        val result = safeApiCall { apiService.createRepository(request) }
        return result.fold(
            onSuccess = { repo ->
                repositoryDao.upsertAll(listOf(repo.toEntity()))
                Result.success(repo.toDomain())
            },
            onFailure = { Result.failure(it) }
        )
    }

    override suspend fun deleteRepository(owner: String, repo: String): Result<Unit> {
        val result = safeApiCall { apiService.deleteRepository(owner, repo) }
        return result.fold(
            onSuccess = {
                // Remove all locally-cached data tied to this repository so the
                // list/details screens reflect the deletion immediately on offline reads.
                repositoryDao.deleteById(owner, repo)
                branchDao.clearForRepoScopes(owner, repo)
                commitDao.clearForRepoScopes(owner, repo)
                Result.success(Unit)
            },
            onFailure = { Result.failure(it) }
        )
    }

    override fun observeBranches(owner: String, repo: String): Flow<List<BranchInfo>> =
        branchDao.observeBranchesForRepo(owner, repo).map { entities ->
            entities.map { BranchInfo(it.name, it.commitSha) }
        }

    override suspend fun refreshBranches(owner: String, repo: String): Result<List<BranchInfo>> {
        val result = safeApiCall { apiService.listBranches(owner, repo) }
        return result.fold(
            onSuccess = { branches ->
                val entities = branches.map { it.toEntity(0, owner, repo) }
                branchDao.clearForRepoScopes(owner, repo)
                branchDao.upsertAll(entities)
                Result.success(branches.map { it.toDomain() })
            },
            onFailure = { Result.failure(it) }
        )
    }

    override fun observeCommits(owner: String, repo: String): Flow<List<CommitInfo>> =
        commitDao.observeCommitsForRepo(owner, repo).map { entities ->
            entities.map {
                CommitInfo(it.sha, it.message, it.authorName, it.authorAvatar, it.date)
            }
        }

    override suspend fun refreshCommits(owner: String, repo: String): Result<List<CommitInfo>> {
        val result = safeApiCall { apiService.listCommits(owner, repo) }
        return result.fold(
            onSuccess = { commits ->
                val entities = commits.map { it.toEntity(0, owner, repo) }
                commitDao.clearForRepoScopes(owner, repo)
                commitDao.upsertAll(entities)
                Result.success(commits.map { it.toDomain() })
            },
            onFailure = { Result.failure(it) }
        )
    }
}
