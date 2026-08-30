package com.gitofy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * PRD §37/§38: Gito Repair Job — persistent state for the auto-repair flow.
 */
@Entity(
    tableName = "gito_repair_jobs",
    indices = [Index("status"), Index("ownerLogin"), Index("repoName"), Index("updatedAt")]
)
data class GitoRepairJobEntity(
    @PrimaryKey val repairId: String = "",
    val ownerLogin: String = "",
    val repoName: String = "",
    val branch: String = "",
    val commitSha: String = "",
    val workflowId: String = "",
    val runId: Long = 0,
    val failedJobId: Long = 0,
    val failedJobName: String = "",
    val failedStepName: String = "",
    val status: String = "DETECTED",
    val attempt: Int = 0,
    val maxAttempts: Int = 3,
    val errorLog: String = "",
    val rootCause: String = "",
    val affectedFiles: String = "",
    val fixDescription: String = "",
    val commitMessage: String = "",
    val verificationRunId: Long = 0,
    val verificationStatus: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0
)

@Entity(
    tableName = "gito_repair_attempts",
    indices = [Index("repairId"), Index("attemptNumber")]
)
data class GitoRepairAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val repairId: String = "",
    val attemptNumber: Int = 1,
    val status: String = "",
    val errorLog: String = "",
    val rootCause: String = "",
    val modifiedFiles: String = "",
    val commitSha: String = "",
    val verificationRunId: Long = 0,
    val verificationResult: String = "",
    val errorMessage: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0
)
