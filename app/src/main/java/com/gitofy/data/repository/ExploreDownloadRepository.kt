package com.gitofy.data.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.gitofy.core.network.GitHubApiService
import com.gitofy.core.network.safeApiCall
import com.gitofy.core.security.SecureCredentialStorage
import com.gitofy.data.remote.dto.Release
import com.gitofy.data.remote.dto.ReleaseAsset
import com.gitofy.data.remote.dto.Repository
import com.gitofy.domain.model.GitOFYError
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Explore release/download implementation.
 *
 * Downloads are written to the user's public Downloads directory, not the
 * app-private files directory. APKs are returned as a content URI so the
 * Android package installer/file resolver can open them safely.
 */
@Singleton
class ExploreDownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: GitHubApiService,
    private val httpClient: OkHttpClient,
    private val secureStorage: SecureCredentialStorage
) {

    suspend fun loadLatestRelease(owner: String, repo: String): Result<Release?> =
        withContext(Dispatchers.IO) {
            safeApiCall { api.listReleases(owner, repo, page = 1, perPage = 20) }.map { releases ->
                releases
                    .asSequence()
                    .filterNot { it.draft }
                    .filterNot { it.preRelease }
                    .firstOrNull { release ->
                        release.assets.any { it.isApk() }
                    }
                    ?: releases.firstOrNull { !it.draft && !it.preRelease }
            }
        }

    suspend fun downloadApk(
        owner: String,
        repo: String,
        asset: ReleaseAsset,
        onProgress: suspend (Long, Long) -> Unit = { _, _ -> }
    ): Result<DownloadedFile> = downloadToDownloads(
        url = "https://api.github.com/repos/$owner/$repo/releases/assets/${asset.id}",
        displayName = sanitizeFileName(asset.name.ifBlank { "$repo.apk" }, ".apk"),
        mimeType = "application/vnd.android.package-archive",
        accept = "application/octet-stream",
        onProgress = onProgress
    )

    suspend fun downloadSourceZip(
        owner: String,
        repo: Repository,
        release: Release?,
        onProgress: suspend (Long, Long) -> Unit = { _, _ -> }
    ): Result<DownloadedFile> {
        val tag = release?.tagName?.takeIf { it.isNotBlank() }
        val displayName = sanitizeFileName(
            "${repo.name}${tag?.let { "-$it" } ?: "-${repo.defaultBranch}"}-source.zip",
            ".zip"
        )

        release?.zipballUrl?.takeIf { it.isNotBlank() }?.let { url ->
            return downloadToDownloads(
                url = url,
                displayName = displayName,
                mimeType = "application/zip",
                accept = "application/vnd.github+json",
                onProgress = onProgress
            )
        }

        // FIX: when there's no release, we guessed the ref as
        // repo.defaultBranch, which defaults to "main" if that field ever
        // comes back blank. Repos created before ~2020 (e.g. many popular
        // ones) default to "master" instead, so a blank/stale field turned
        // into a silent 404. Try the reported ref first, then fall back to
        // the other common ref names before giving up, so a wrong guess no
        // longer breaks the download outright.
        val candidateRefs = linkedSetOf(repo.defaultBranch, "master", "main").filter { it.isNotBlank() }
        var lastFailure: Result<DownloadedFile>? = null
        for (ref in candidateRefs) {
            val result = downloadToDownloads(
                url = "https://api.github.com/repos/$owner/${repo.name}/zipball/$ref",
                displayName = displayName,
                mimeType = "application/zip",
                accept = "application/vnd.github+json",
                onProgress = onProgress
            )
            if (result.isSuccess) return result
            lastFailure = result
        }
        return lastFailure ?: Result.failure(GitOFYError.ArtifactError("Could not determine the repository's branch"))
    }

    private suspend fun downloadToDownloads(
        url: String,
        displayName: String,
        mimeType: String,
        accept: String,
        onProgress: suspend (Long, Long) -> Unit
    ): Result<DownloadedFile> = withContext(Dispatchers.IO) {
        var pendingUri: Uri? = null
        var legacyFile: File? = null
        try {
            val requestBuilder = Request.Builder()
                .url(url)
                .header("Accept", accept)
                .header("X-GitHub-Api-Version", "2022-11-28")
            secureStorage.getToken()?.takeIf { it.isNotBlank() }?.let {
                requestBuilder.header("Authorization", "Bearer $it")
            }

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        GitOFYError.ArtifactError("Download failed: HTTP ${response.code}")
                    )
                }
                val body = response.body ?: return@withContext Result.failure(
                    GitOFYError.ArtifactError("Empty download response")
                )
                val total = body.contentLength()
                var downloaded = 0L
                var lastReported = 0L

                val output = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                        put(MediaStore.Downloads.MIME_TYPE, mimeType)
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/GITOFY")
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    pendingUri = resolver.insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        values
                    ) ?: return@withContext Result.failure(
                        GitOFYError.ArtifactError("Could not create Downloads file")
                    )
                    resolver.openOutputStream(pendingUri!!)
                        ?: return@withContext Result.failure(
                            GitOFYError.ArtifactError("Could not open Downloads output")
                        )
                } else {
                    val downloads = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS
                    )
                    val directory = File(downloads, "GITOFY").apply { mkdirs() }
                    legacyFile = uniqueFile(directory, displayName)
                    FileOutputStream(legacyFile!!)
                }

                output.use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            downloaded += read
                            if (downloaded - lastReported >= 128 * 1024 ||
                                (total > 0 && downloaded >= total)
                            ) {
                                onProgress(downloaded, total)
                                lastReported = downloaded
                            }
                        }
                    }
                    out.flush()
                }
                onProgress(downloaded, total)

                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.IS_PENDING, 0)
                    }
                    context.contentResolver.update(pendingUri!!, values, null, null)
                    pendingUri!!
                } else {
                    FileProvider.getUriForFile(
                        context,
                        context.packageName + ".fileprovider",
                        legacyFile!!
                    )
                }

                return@withContext Result.success(
                    DownloadedFile(
                        uri = uri,
                        displayName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                            displayName else legacyFile!!.name,
                        mimeType = mimeType
                    )
                )
            }
        } catch (t: Throwable) {
            pendingUri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
            legacyFile?.let { runCatching { it.delete() } }
            Result.failure(GitOFYError.ArtifactError(t.message ?: "Download failed"))
        }
    }

    private fun uniqueFile(directory: File, name: String): File {
        val base = File(directory, name)
        if (!base.exists()) return base
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var index = 1
        var candidate: File
        do {
            candidate = File(directory, "$stem ($index)$ext")
            index++
        } while (candidate.exists())
        return candidate
    }

    private fun sanitizeFileName(name: String, requiredExtension: String): String {
        val cleaned = name.replace(Regex("[^A-Za-z0-9._() -]"), "_")
            .trim()
            .ifBlank { "download" }
        return if (cleaned.lowercase(Locale.US).endsWith(requiredExtension)) cleaned
        else cleaned + requiredExtension
    }

    private fun ReleaseAsset.isApk(): Boolean = name.endsWith(".apk", ignoreCase = true)
}

data class DownloadedFile(
    val uri: Uri,
    val displayName: String,
    val mimeType: String
)
