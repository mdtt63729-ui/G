package com.gitofy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * PRD §33: Job persistence — Room tables for jobs, job_steps, job_events.
 * These are separate from the existing OperationEntity (which is used by
 * WorkManager). The ExecJob* entities represent the real-time job system.
 */

@Entity(
    tableName = "exec_jobs",
    indices = [Index("status"), Index("operationType"), Index("updatedAt")]
)
data class ExecJobEntity(
    @PrimaryKey val jobId: String = "",
    val operationId: String = "",
    val repository: String = "",
    val ownerLogin: String = "",
    val repoName: String = "",
    val operationType: String = "",
    val status: String = "",
    val progress: Float = 0f,
    val currentStep: String = "",
    val startedAt: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0L,
    val error: String? = null,
    val commitSha: String = "",
    val chatMessageId: String = "",
    val totalItems: Int = 0,
    val completedItems: Int = 0
)

@Entity(
    tableName = "exec_job_steps",
    indices = [Index("jobId"), Index("stepOrder")]
)
data class ExecJobStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jobId: String = "",
    val stepName: String = "",
    val displayName: String = "",
    val stepOrder: Int = 0,
    val status: String = "PENDING",
    val startedAt: Long = 0L,
    val completedAt: Long = 0L,
    val completedItems: Int = 0,
    val totalItems: Int = 0,
    val error: String? = null
)

@Entity(
    tableName = "exec_job_events",
    indices = [Index("jobId"), Index("timestamp")]
)
data class ExecJobEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jobId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val type: String = "",
    val stage: String = "",
    val status: String = "",
    val progress: Float = 0f,
    val message: String = "",
    val item: String = "",
    val completed: Int = 0,
    val total: Int = 0,
    val error: String? = null
)
