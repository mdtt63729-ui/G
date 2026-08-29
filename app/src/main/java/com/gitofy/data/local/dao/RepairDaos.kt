package com.gitofy.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.gitofy.data.local.entity.GitoRepairJobEntity
import com.gitofy.data.local.entity.GitoRepairAttemptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GitoRepairJobDao {
    @Upsert
    suspend fun upsert(job: GitoRepairJobEntity)

    @Query("SELECT * FROM gito_repair_jobs ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<GitoRepairJobEntity>>

    @Query("SELECT * FROM gito_repair_jobs WHERE repairId = :repairId LIMIT 1")
    fun observeRepair(repairId: String): Flow<GitoRepairJobEntity?>

    @Query("SELECT * FROM gito_repair_jobs WHERE repairId = :repairId LIMIT 1")
    suspend fun getById(repairId: String): GitoRepairJobEntity?

    @Query("SELECT * FROM gito_repair_jobs WHERE status IN ('DETECTED','COLLECTING_LOGS','ANALYZING','INSPECTING_REPOSITORY','PLANNING_FIX','MODIFYING','VALIDATING','COMMITTING','PUSHING','TRIGGERING_BUILD','VERIFYING') ORDER BY updatedAt DESC")
    fun observeActiveRepairs(): Flow<List<GitoRepairJobEntity>>

    @Query("SELECT * FROM gito_repair_jobs WHERE ownerLogin = :owner AND repoName = :repo AND status IN ('DETECTED','COLLECTING_LOGS','ANALYZING','INSPECTING_REPOSITORY','PLANNING_FIX','MODIFYING','VALIDATING','COMMITTING','PUSHING','TRIGGERING_BUILD','VERIFYING') ORDER BY updatedAt DESC LIMIT 1")
    fun observeActiveRepairForRepo(owner: String, repo: String): Flow<GitoRepairJobEntity?>

    @Query("DELETE FROM gito_repair_jobs WHERE repairId = :repairId")
    suspend fun delete(repairId: String)

    @Query("DELETE FROM gito_repair_jobs WHERE completedAt > 0 AND completedAt < :beforeTimestamp")
    suspend fun deleteOldCompleted(beforeTimestamp: Long)
}

@Dao
interface GitoRepairAttemptDao {
    @Upsert
    suspend fun upsert(attempt: GitoRepairAttemptEntity)

    @Query("SELECT * FROM gito_repair_attempts WHERE repairId = :repairId ORDER BY attemptNumber ASC")
    fun observeAttempts(repairId: String): Flow<List<GitoRepairAttemptEntity>>

    @Query("SELECT * FROM gito_repair_attempts WHERE repairId = :repairId ORDER BY attemptNumber ASC")
    suspend fun getAttempts(repairId: String): List<GitoRepairAttemptEntity>

    @Query("SELECT * FROM gito_repair_attempts WHERE repairId = :repairId ORDER BY attemptNumber DESC LIMIT 1")
    suspend fun getLatestAttempt(repairId: String): GitoRepairAttemptEntity?

    @Query("DELETE FROM gito_repair_attempts WHERE repairId = :repairId")
    suspend fun deleteForRepair(repairId: String)
}
