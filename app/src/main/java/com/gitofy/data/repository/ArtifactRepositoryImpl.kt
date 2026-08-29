package com.gitofy.data.repository

import com.gitofy.core.network.GitHubApiService
import com.gitofy.core.network.safeApiCall
import com.gitofy.core.security.SecureCredentialStorage
import com.gitofy.data.local.dao.ArtifactDao
import com.gitofy.data.mapper.toDomain
import com.gitofy.data.mapper.toEntity
import com.gitofy.domain.model.ArtifactSummary
import com.gitofy.domain.model.GitOFYError
import com.gitofy.domain.repository.ArtifactRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtifactRepositoryImpl @Inject constructor(
    private val apiService: GitHubApiService,
    private val artifactDao: ArtifactDao,
    private val secureStorage: SecureCredentialStorage
) : ArtifactRepository {

    override fun observeArtifacts(runId: Long): Flow<List<ArtifactSummary>> =
        artifactDao.observeArtifacts(runId).map { it.map { entity -> entity.toDomain() } }

    override suspend fun refreshArtifacts(
        owner: String, repo: String, runId: Long
    ): Result<List<ArtifactSummary>> {
        val result = safeApiCall { apiService.listArtifacts(owner, repo, runId) }
        return result.map { list ->
            val entities = list.artifacts.map { it.toEntity(runId) }
            artifactDao.upsertAll(entities)
            list.artifacts.map { it.toDomain() }
        }
    }

    override suspend fun downloadArtifact(
        owner: String, repo: String, artifactId: Long, artifactName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        // PRD FIX: this blocking OkHttp call ran on whatever dispatcher the
        // caller happened to be on (usually Dispatchers.Main from a
        // ViewModel), which throws NetworkOnMainThreadException. Wrapping the
        // whole function body in withContext(Dispatchers.IO) fixes that.
        try {
            // GitHub artifact downloads require following a redirect with auth
            val token = secureStorage.getToken()
                ?: return@withContext Result.failure(GitOFYError.AuthenticationRequired())

            // Use OkHttp directly for the redirect
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val request = okhttp3.Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/actions/artifacts/$artifactId/zip")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(GitOFYError.ArtifactError("Download failed: ${response.code}"))
                }

                // Save to app's files directory — PRD 26: use appropriate Android storage APIs
                val downloadsDir = File(
                    android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS
                    ), "GITOFY"
                )
                downloadsDir.mkdirs()
                val outFile = File(downloadsDir, "$artifactName.zip")

                response.body?.byteStream()?.use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: return@withContext Result.failure(GitOFYError.ArtifactError("Empty response body"))

                Result.success(outFile.absolutePath)
            }
        } catch (e: Exception) {
            Result.failure(GitOFYError.ArtifactError(e.message ?: "Download failed"))
        }
    }
}
