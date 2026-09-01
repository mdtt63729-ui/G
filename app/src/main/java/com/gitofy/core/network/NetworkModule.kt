package com.gitofy.core.network

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Network DI module.
 * PRD 12.1: Retrofit + OkHttp, centralized GitHub API client.
 * PRD 8.2: HTTPS only, TLS validation, explicit timeouts, sensitive header redaction.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "https://api.github.com/"

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        redactingLogger: RedactingLogger,
        eTagInterceptor: ETagInterceptor
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor(redactingLogger).apply {
            level = if (com.gitofy.BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.HEADERS
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(eTagInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    @com.gitofy.core.network.AiHttpClient
    fun provideAiHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(
        client: OkHttpClient,
        json: Json
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideGitHubApiService(retrofit: Retrofit): GitHubApiService {
        return retrofit.create(GitHubApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideAuthInterceptor(
        tokenProvider: com.gitofy.core.security.SecureCredentialStorage
    ): AuthInterceptor {
        return AuthInterceptor(tokenProvider = { tokenProvider.getToken() })
    }

    @Provides
    @Singleton
    fun provideRedactingLogger(): RedactingLogger = RedactingLogger()

    @Provides
    @Singleton
    fun provideETagInterceptor(
        syncMetadataDao: com.gitofy.data.local.dao.SyncMetadataDao
    ): ETagInterceptor = ETagInterceptor(syncMetadataDao)
}
