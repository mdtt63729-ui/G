package com.gitofy.data.repository

import com.gitofy.core.logging.GITOFYLogger
import com.gitofy.core.network.GitHubApiService
import com.gitofy.core.network.safeApiCall
import com.gitofy.data.local.dao.RepositoryDao
import com.gitofy.data.local.dao.SyncMetadataDao
import com.gitofy.data.mapper.toDomain
import com.gitofy.data.mapper.toEntity
import com.gitofy.domain.model.RepoSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sync Repository Implementation — PRD Addendum: Offline-First Data Router.
 *
 * Strategy:
 * 1. UI opens → Room cache displays instantly (zero skeleton lag)
 * 2. Background network sync fetches fresh data
 * 3. Room updates → UI reacts via StateFlow
 *
 * This eliminates perceived loading time for returning screens.
 */
@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val apiService: GitHubApiService,
    private val repositoryDao: RepositoryDao,
    private val syncMetadataDao: SyncMetadataDao
) {

    /**
     * Observe cached repositories — instant display from Room.
     * PRD Addendum: < 50ms perceived loading time for returning screens.
     */
    fun observeCachedRepositories(): Flow<List<RepoSummary>> =
        repositoryDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }

    /**
     * Background sync — fetches fresh data from network.
     * ETag interceptor handles 304 Not Modified automatically.
     */
    suspend fun syncRepositories(): Result<List<RepoSummary>> {
        val result = safeApiCall { apiService.listRepositories() }
        return result.fold(
            onSuccess = { repos ->
                val entities = repos.map { it.toEntity() }
                repositoryDao.upsertAll(entities)
                GITOFYLogger.d("Synced ${repos.size} repositories from network")
                Result.success(repos.map { it.toDomain() })
            },
            onFailure = { error ->
                GITOFYLogger.w("Repository sync failed, serving cache: ${error.message}")
                // Offline-first: failure is acceptable, cache is already displayed
                Result.failure(error)
            }
        )
    }

    /**
     * Check if data is stale (older than threshold).
     */
    suspend fun isDataStale(key: String, maxAgeMs: Long = 5 * 60 * 1000): Boolean {
        val metadata = syncMetadataDao.get(key)
        if (metadata == null) return true
        return System.currentTimeMillis() - metadata.lastSyncTime > maxAgeMs
    }
}
