package com.gitofy.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.gitofy.data.local.entity.ExecJobEntity
import com.gitofy.data.local.entity.ExecJobStepEntity
import com.gitofy.data.local.entity.ExecJobEventEntity
import kotlinx.coroutines.flow.Flow

/**
 * PRD §35: Job Repository — provides reactive queries for jobs, steps, and events.
 */
@Dao
interface ExecJobDao {
    @Upsert
    suspend fun upsert(job: ExecJobEntity)

    @Query("SELECT * FROM exec_jobs ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ExecJobEntity>>

    @Query("SELECT * FROM exec_jobs WHERE jobId = :jobId LIMIT 1")
    fun observeJob(jobId: String): Flow<ExecJobEntity?>

    @Query("SELECT * FROM exec_jobs WHERE jobId = :jobId LIMIT 1")
    suspend fun getById(jobId: String): ExecJobEntity?

    @Query("SELECT * FROM exec_jobs WHERE status IN ('QUEUED', 'STARTING', 'RUNNING', 'CANCELLING') ORDER BY updatedAt DESC")
    fun observeActiveJobs(): Flow<List<ExecJobEntity>>

    @Query("SELECT * FROM exec_jobs WHERE status IN ('QUEUED', 'STARTING', 'RUNNING', 'CANCELLING') ORDER BY updatedAt DESC")
    suspend fun getActiveJobs(): List<ExecJobEntity>

    @Query("SELECT COUNT(*) FROM exec_jobs WHERE status IN ('QUEUED', 'STARTING', 'RUNNING', 'CANCELLING')")
    fun observeActiveJobCount(): Flow<Int>

    @Query("DELETE FROM exec_jobs WHERE jobId = :jobId")
    suspend fun delete(jobId: String)

    @Query("DELETE FROM exec_jobs WHERE status IN ('COMPLETED', 'CANCELLED', 'FAILED') AND completedAt < :beforeTimestamp")
    suspend fun deleteOldJobs(beforeTimestamp: Long)
}

@Dao
interface ExecJobStepDao {
    @Upsert
    suspend fun upsert(step: ExecJobStepEntity)

    @Query("SELECT * FROM exec_job_steps WHERE jobId = :jobId ORDER BY stepOrder ASC")
    fun observeStepsForJob(jobId: String): Flow<List<ExecJobStepEntity>>

    @Query("SELECT * FROM exec_job_steps WHERE jobId = :jobId ORDER BY stepOrder ASC")
    suspend fun getStepsForJob(jobId: String): List<ExecJobStepEntity>

    @Query("DELETE FROM exec_job_steps WHERE jobId = :jobId")
    suspend fun deleteForJob(jobId: String)
}

@Dao
interface ExecJobEventDao {
    @Upsert
    suspend fun upsert(event: ExecJobEventEntity)

    @Query("SELECT * FROM exec_job_events WHERE jobId = :jobId ORDER BY timestamp ASC")
    fun observeEventsForJob(jobId: String): Flow<List<ExecJobEventEntity>>

    @Query("SELECT * FROM exec_job_events WHERE jobId = :jobId ORDER BY timestamp ASC")
    suspend fun getEventsForJob(jobId: String): List<ExecJobEventEntity>

    @Query("DELETE FROM exec_job_events WHERE jobId = :jobId")
    suspend fun deleteForJob(jobId: String)

    @Query("DELETE FROM exec_job_events WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldEvents(beforeTimestamp: Long)
}
