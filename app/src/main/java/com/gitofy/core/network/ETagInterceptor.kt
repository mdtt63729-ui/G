package com.gitofy.core.network

import com.gitofy.core.logging.GITOFYLogger
import com.gitofy.data.local.dao.SyncMetadataDao
import com.gitofy.data.local.entity.SyncMetadataEntity
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * ETag Interceptor — PRD §2, §16: Conditional HTTP Caching.
 *
 * Adds If-None-Match header for GET requests to handle 304 Not Modified.
 *
 * PRD §2 bug fix: When GitHub returns 304, the response body is empty.
 * Previously this caused Retrofit's response.isSuccessful to be false (304 is not 2xx),
 * which propagated as "GitHub API error (304)" to the UI.
 *
 * Fix (PRD §16 "Preferred" strategy): Cache the response body alongside the ETag.
 * On 304, return a new 200 response with the cached body so Retrofit treats it as success.
 * If no cached body exists, fall back to PRD §16 "Simpler robust fallback": retry without ETag.
 */
class ETagInterceptor(
    private val syncMetadataDao: SyncMetadataDao
) : Interceptor {

    private val dbExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "gitofy-etag-db").apply { isDaemon = true }
    }

    private fun <T> dbCall(block: () -> T): T = dbExecutor.submit(block).get(2, TimeUnit.SECONDS)

    @Suppress("OPT_IN_USAGE")
    private fun <T> suspendDbCall(block: suspend () -> T): T {
        val callable = java.util.concurrent.Callable { runBlocking { block() } }
        return dbExecutor.submit(callable).get(2, TimeUnit.SECONDS)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()

        // Only add ETag for GET requests
        if (request.method != "GET") {
            return chain.proceed(request)
        }

        // Look up stored ETag and cached body for this URL
        val metadata = runCatching { suspendDbCall { syncMetadataDao.get(url) } }.getOrNull()
        val etag = metadata?.etag
        val cachedBody = metadata?.cachedBody

        val etagRequest = if (etag != null) {
            request.newBuilder()
                .header("If-None-Match", etag)
                .build()
        } else {
            request
        }

        val response = chain.proceed(etagRequest)

        // Store new ETag and cache body if present (200 response)
        if (response.isSuccessful) {
            val newEtag = response.header("ETag")
            if (newEtag != null) {
                // PRD §16 Preferred: Cache response body alongside ETag
                val bodyString = response.peekBody(Long.MAX_VALUE).string()
                runCatching {
                    suspendDbCall {
                        syncMetadataDao.upsert(
                            SyncMetadataEntity(
                                key = url,
                                etag = newEtag,
                                cachedBody = bodyString,
                                lastSyncTime = System.currentTimeMillis()
                            )
                        )
                    }
                }
                // Rebuild response with the body since peekBody consumes it
                val contentType = response.body?.contentType()
                return response.newBuilder()
                    .body(bodyString.toResponseBody(contentType))
                    .build()
            }
        }

        // PRD §16: 304 Not Modified — use cached body if available
        if (response.code == 304) {
            GITOFYLogger.d("ETag hit (304 Not Modified): $url")

            if (cachedBody != null) {
                // Return cached body as a 200 response so Retrofit treats it as success
                val contentType = response.body?.contentType()
                return Response.Builder()
                    .request(request)
                    .protocol(response.protocol)
                    .code(200)
                    .message("OK (from cache)")
                    .body(cachedBody.toResponseBody(contentType))
                    .build()
            } else {
                // PRD §16 Simpler robust fallback: no cached body → re-fetch without ETag
                GITOFYLogger.d("304 with no cached body, re-fetching: $url")
                val freshRequest = request.newBuilder().removeHeader("If-None-Match").build()
                return chain.proceed(freshRequest)
            }
        }

        return response
    }
}
