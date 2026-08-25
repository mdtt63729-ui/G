package com.gitofy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.gitofy.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Upsert
    suspend fun upsert(user: UserEntity)

    @Query("SELECT * FROM users WHERE login = :login")
    fun observeUser(login: String): Flow<UserEntity?>

    @Query("SELECT * FROM users LIMIT 1")
    fun observeCurrentUser(): Flow<UserEntity?>

    @Query("DELETE FROM users")
    suspend fun clear()
}

@Dao
interface RepositoryDao {
    @Upsert
    suspend fun upsertAll(repos: List<RepositoryEntity>)

    @Query("SELECT * FROM repositories ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<RepositoryEntity>>

    @Query("SELECT * FROM repositories WHERE ownerLogin = :owner AND name = :name LIMIT 1")
    fun observeRepository(owner: String, name: String): Flow<RepositoryEntity?>

    @Query("SELECT * FROM repositories ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<RepositoryEntity>>

    @Query("DELETE FROM repositories")
    suspend fun clear()
}

@Dao
interface BranchDao {
    @Upsert
    suspend fun upsertAll(branches: List<BranchEntity>)

    @Query("SELECT * FROM branches WHERE repoId = :repoId")
    fun observeBranches(repoId: Long): Flow<List<BranchEntity>>

    @Query("DELETE FROM branches WHERE repoId = :repoId")
    suspend fun clearForRepo(repoId: Long)
}

@Dao
interface CommitDao {
    @Upsert
    suspend fun upsertAll(commits: List<CommitEntity>)

    @Query("SELECT * FROM commits WHERE repoId = :repoId ORDER BY date DESC")
    fun observeCommits(repoId: Long): Flow<List<CommitEntity>>

    @Query("DELETE FROM commits WHERE repoId = :repoId")
    suspend fun clearForRepo(repoId: Long)
}

@Dao
interface WorkflowDao {
    @Upsert
    suspend fun upsertAll(workflows: List<WorkflowEntity>)

    @Query("SELECT * FROM workflows WHERE repoId = :repoId")
    fun observeWorkflows(repoId: Long): Flow<List<WorkflowEntity>>

    @Query("DELETE FROM workflows WHERE repoId = :repoId")
    suspend fun clearForRepo(repoId: Long)
}

@Dao
interface WorkflowRunDao {
    @Upsert
    suspend fun upsertAll(runs: List<WorkflowRunEntity>)

    @Query("SELECT * FROM workflow_runs WHERE repoId = :repoId ORDER BY updatedAt DESC")
    fun observeRuns(repoId: Long): Flow<List<WorkflowRunEntity>>

    @Query("SELECT * FROM workflow_runs WHERE id = :runId LIMIT 1")
    fun observeRun(runId: Long): Flow<WorkflowRunEntity?>

    @Query("SELECT * FROM workflow_runs WHERE status IN ('queued', 'in_progress') ORDER BY updatedAt DESC")
    fun observeActiveRuns(): Flow<List<WorkflowRunEntity>>

    @Query("DELETE FROM workflow_runs WHERE repoId = :repoId")
    suspend fun clearForRepo(repoId: Long)
}

@Dao
interface JobDao {
    @Upsert
    suspend fun upsertAll(jobs: List<JobEntity>)

    @Query("SELECT * FROM jobs WHERE runId = :runId")
    fun observeJobs(runId: Long): Flow<List<JobEntity>>

    @Query("DELETE FROM jobs WHERE runId = :runId")
    suspend fun clearForRun(runId: Long)
}

@Dao
interface ArtifactDao {
    @Upsert
    suspend fun upsertAll(artifacts: List<ArtifactEntity>)

    @Query("SELECT * FROM artifacts WHERE runId = :runId")
    fun observeArtifacts(runId: Long): Flow<List<ArtifactEntity>>

    @Query("SELECT * FROM artifacts ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ArtifactEntity>>

    @Query("DELETE FROM artifacts WHERE runId = :runId")
    suspend fun clearForRun(runId: Long)
}

@Dao
interface OperationDao {
    @Upsert
    suspend fun upsert(operation: OperationEntity)

    @Query("SELECT * FROM operations WHERE id = :id")
    fun observeOperation(id: String): Flow<OperationEntity?>

    @Query("SELECT * FROM operations ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<OperationEntity>>

    @Query("SELECT * FROM operations WHERE status IN ('QUEUED', 'RUNNING') ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<OperationEntity>>

    @Query("DELETE FROM operations WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM operations WHERE status IN ('COMPLETED', 'CANCELLED', 'FAILED')")
    suspend fun clearFinished()
}

@Dao
interface SyncMetadataDao {
    @Upsert
    suspend fun upsert(metadata: SyncMetadataEntity)

    @Query("SELECT * FROM sync_metadata WHERE key = :key")
    suspend fun get(key: String): SyncMetadataEntity?

    @Query("DELETE FROM sync_metadata")
    suspend fun clear()
}
