package com.gitofy.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.gitofy.data.local.dao.*
import com.gitofy.data.local.entity.*

@Database(
    entities = [
        UserEntity::class,
        RepositoryEntity::class,
        BranchEntity::class,
        CommitEntity::class,
        WorkflowEntity::class,
        WorkflowRunEntity::class,
        JobEntity::class,
        ArtifactEntity::class,
        OperationEntity::class,
        SyncMetadataEntity::class,
        ExecJobEntity::class,
        ExecJobStepEntity::class,
        ExecJobEventEntity::class,
        GitoRepairJobEntity::class,
        GitoRepairAttemptEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class GITOFYDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun repositoryDao(): RepositoryDao
    abstract fun branchDao(): BranchDao
    abstract fun commitDao(): CommitDao
    abstract fun workflowDao(): WorkflowDao
    abstract fun workflowRunDao(): WorkflowRunDao
    abstract fun jobDao(): JobDao
    abstract fun artifactDao(): ArtifactDao
    abstract fun operationDao(): OperationDao
    abstract fun syncMetadataDao(): SyncMetadataDao
    abstract fun execJobDao(): ExecJobDao
    abstract fun execJobStepDao(): ExecJobStepDao
    abstract fun execJobEventDao(): ExecJobEventDao
    abstract fun gitoRepairJobDao(): GitoRepairJobDao
    abstract fun gitoRepairAttemptDao(): GitoRepairAttemptDao

    companion object {
        const val DATABASE_NAME = "gitofy.db"
    }
}
