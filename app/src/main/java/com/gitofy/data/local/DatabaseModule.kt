package com.gitofy.data.local

import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: android.content.Context
    ): GITOFYDatabase {
        return Room.databaseBuilder(
            context,
            GITOFYDatabase::class.java,
            GITOFYDatabase.DATABASE_NAME
        )
            .build()
    }

    @Provides fun provideUserDao(db: GITOFYDatabase) = db.userDao()
    @Provides fun provideRepositoryDao(db: GITOFYDatabase) = db.repositoryDao()
    @Provides fun provideBranchDao(db: GITOFYDatabase) = db.branchDao()
    @Provides fun provideCommitDao(db: GITOFYDatabase) = db.commitDao()
    @Provides fun provideWorkflowDao(db: GITOFYDatabase) = db.workflowDao()
    @Provides fun provideWorkflowRunDao(db: GITOFYDatabase) = db.workflowRunDao()
    @Provides fun provideJobDao(db: GITOFYDatabase) = db.jobDao()
    @Provides fun provideArtifactDao(db: GITOFYDatabase) = db.artifactDao()
    @Provides fun provideOperationDao(db: GITOFYDatabase) = db.operationDao()
    @Provides fun provideSyncMetadataDao(db: GITOFYDatabase) = db.syncMetadataDao()
    @Provides fun provideExecJobDao(db: GITOFYDatabase) = db.execJobDao()
    @Provides fun provideExecJobStepDao(db: GITOFYDatabase) = db.execJobStepDao()
    @Provides fun provideExecJobEventDao(db: GITOFYDatabase) = db.execJobEventDao()
    @Provides fun provideGitoRepairJobDao(db: GITOFYDatabase) = db.gitoRepairJobDao()
    @Provides fun provideGitoRepairAttemptDao(db: GITOFYDatabase) = db.gitoRepairAttemptDao()
}
