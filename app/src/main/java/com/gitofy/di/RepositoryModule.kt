package com.gitofy.di

import com.gitofy.data.repository.ArtifactRepositoryImpl
import com.gitofy.data.repository.AuthRepositoryImpl
import com.gitofy.data.repository.GitHubRepositoryImpl
import com.gitofy.data.repository.WorkflowRepositoryImpl
import com.gitofy.data.git.JGitEngine
import com.gitofy.domain.repository.ArtifactRepository
import com.gitofy.domain.repository.AuthRepository
import com.gitofy.domain.repository.GitHubRepository
import com.gitofy.domain.repository.GitRepository
import com.gitofy.domain.repository.WorkflowRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds @Singleton
    abstract fun bindGitHubRepository(impl: GitHubRepositoryImpl): GitHubRepository

    @Binds @Singleton
    abstract fun bindWorkflowRepository(impl: WorkflowRepositoryImpl): WorkflowRepository

    @Binds @Singleton
    abstract fun bindArtifactRepository(impl: ArtifactRepositoryImpl): ArtifactRepository

    @Binds @Singleton
    abstract fun bindGitRepository(impl: JGitEngine): GitRepository
}
