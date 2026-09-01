package com.gitofy.core.network

import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PRD §80: Rate Limit Management.
 *
 * Tracks the remaining GitHub API quota by parsing the standard rate-limit response
 * headers returned on every API call. Components can query [isRateLimited] before issuing
 * requests, or read [getRemainingPercentage] to drive UI affordances such as a quota meter.
 */
@Singleton
class RateLimitManager @Inject constructor() {

    @Volatile
    private var rateLimitInfo: RateLimitInfo = RateLimitInfo(
        limit = 0,
        remaining = 0,
        resetTime = null,
    )

    /**
     * Updates the cached rate-limit state from the response headers of an API call.
     *
     * Recognises both the canonical lowercase header names (`x-ratelimit-limit`,
     * `x-ratelimit-remaining`, `x-ratelimit-reset`) and any case variant, since HTTP
     * header field names are case-insensitive. Values that cannot be parsed are ignored
     * so a partial or malformed header set never corrupts the previous known-good state.
     */
    fun updateFromHeaders(headers: Map<String, String>) {
        val limit = headers.firstInt(HEADER_LIMIT) ?: rateLimitInfo.limit
        val remaining = headers.firstInt(HEADER_REMAINING) ?: rateLimitInfo.remaining
        val resetTime = headers.firstLong(HEADER_RESET) ?: rateLimitInfo.resetTime

        rateLimitInfo = RateLimitInfo(
            limit = limit,
            remaining = remaining,
            resetTime = resetTime,
        )
    }

    /** Returns a snapshot of the current rate-limit state. */
    fun getRateLimitInfo(): RateLimitInfo = rateLimitInfo

    /**
     * Returns the remaining quota as a percentage in the range `0..100`. Returns `100` when
     * no limit has been reported yet (so callers do not erroneously throttle before the
     * first successful request).
     */
    fun getRemainingPercentage(): Int {
        val info = rateLimitInfo
        if (info.limit <= 0) return 100
        val percentage = (info.remaining.toDouble() / info.limit.toDouble() * 100).toInt()
        return percentage.coerceIn(0, 100)
    }

    /**
     * Returns the number of milliseconds remaining until the quota resets, or `null` when
     * no reset time is known.
     */
    fun getTimeUntilReset(): Long? {
        val resetTime = rateLimitInfo.resetTime ?: return null
        val now = System.currentTimeMillis()
        val remaining = resetTime - now
        return remaining.takeIf { it > 0 } ?: 0L
    }

    /**
     * Returns the remaining seconds until the quota resets, or `null` when no reset time
     * is known.
     */
    fun getSecondsUntilReset(): Long? = getTimeUntilReset()?.let {
        TimeUnit.MILLISECONDS.toSeconds(it)
    }

    /**
     * Returns `true` when the remaining quota is exhausted. A request made while this
     * returns `true` will be rejected by GitHub with HTTP 403.
     */
    fun isRateLimited(): Boolean {
        val info = rateLimitInfo
        return info.limit > 0 && info.remaining <= 0
    }

    private fun Map<String, String>.firstInt(header: String): Int? =
        findHeader(header)?.toIntOrNull()

    private fun Map<String, String>.firstLong(header: String): Long? =
        findHeader(header)?.toLongOrNull()

    /**
     * Locates a header value by name, performing a case-insensitive comparison so that
     * `x-ratelimit-limit` matches `X-RateLimit-Limit`.
     */
    private fun Map<String, String>.findHeader(header: String): String? =
        entries.firstOrNull { (key, _) ->
            key.equals(header, ignoreCase = true)
        }?.value

    companion object {
        private const val HEADER_LIMIT = "x-ratelimit-limit"
        private const val HEADER_REMAINING = "x-ratelimit-remaining"
        private const val HEADER_RESET = "x-ratelimit-reset"
    }
}

/**
 * Immutable snapshot of the GitHub rate-limit state at a point in time.
 *
 * @param limit     The maximum number of requests allowed per window.
 * @param remaining The number of requests still available in the current window.
 * @param resetTime Epoch millisecond timestamp (UTC) at which the window resets, or `null`
 *                  when the server has not yet reported it.
 */
data class RateLimitInfo(
    val limit: Int,
    val remaining: Int,
    val resetTime: Long?,
)
