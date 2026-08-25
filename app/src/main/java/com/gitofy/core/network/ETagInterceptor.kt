package com.gitofy.core.network

import com.gitofy.core.logging.GITOFYLogger
import com.gitofy.data.local.dao.SyncMetadataDao
import com.gitofy.data.local.entity.SyncMetadataEntity
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * ETag Interceptor — PRD Addendum: Conditional HTTP Caching.
 * Adds If-None-Match header for REST fallback calls to handle 304 Not Modified responses.
 * Preserves GitHub API rate limits by reusing cached ETags.
 */
class ETagInterceptor(
    private val syncMetadataDao: SyncMetadataDao
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()

        // Only add ETag for GET requests
        if (request.method != "GET") {
            return chain.proceed(request)
        }

        // Look up stored ETag for this URL
        val metadata = runBlocking { syncMetadataDao.get(url) }
        val etag = metadata?.etag

        val etagRequest = if (etag != null) {
            request.newBuilder()
                .header("If-None-Match", etag)
                .build()
        } else {
            request
        }

        val response = chain.proceed(etagRequest)

        // Store new ETag if present
        val newEtag = response.header("ETag")
        if (newEtag != null) {
            runBlocking {
                syncMetadataDao.upsert(
                    SyncMetadataEntity(
                        key = url,
                        etag = newEtag,
                        lastSyncTime = System.currentTimeMillis()
                    )
                )
            }
        }

        // 304 Not Modified — rate limit preserved
        if (response.code == 304) {
            GITOFYLogger.d("ETag hit (304 Not Modified): $url")
        }

        return response
    }
}
