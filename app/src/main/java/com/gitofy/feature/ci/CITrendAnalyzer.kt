package com.gitofy.feature.ci

import javax.inject.Inject
import javax.inject.Singleton

/**
 * CI Trend Dashboard — PRD v4.5 Section 43.
 * Display: Last 10 Runs, Success Rate, Average Duration, Failure Rate, Slowest Job.
 * Do not infer engineering health from insufficient sample sizes.
 */
@Singleton
class CITrendAnalyzer @Inject constructor() {

    data class CITrend(
        val totalRuns: Int,
        val successRate: Float,
        val failureRate: Float,
        val averageDurationMs: Long,
        val medianDurationMs: Long,
        val slowestJob: String?,
        val isStatisticallySignificant: Boolean
    ) {
        companion object {
            val EMPTY = CITrend(0, 0f, 0f, 0, 0, null, false)
        }
    }

    fun analyze(runDurations: List<Pair<String, Long>>, isSuccessful: List<Pair<String, Boolean>>): CITrend {
        if (runDurations.size < 10) {
            return CITrend(
                totalRuns = runDurations.size,
                successRate = if (runDurations.isEmpty()) 0f else isSuccessful.count { it.second }.toFloat() / isSuccessful.size,
                failureRate = if (runDurations.isEmpty()) 0f else isSuccessful.count { !it.second }.toFloat() / isSuccessful.size,
                averageDurationMs = if (runDurations.isEmpty()) 0 else runDurations.map { it.second }.average().toLong(),
                medianDurationMs = if (runDurations.isEmpty()) 0 else runDurations.map { it.second }.sorted().let { it[it.size / 2] },
                slowestJob = runDurations.maxByOrNull { it.second }?.first,
                isStatisticallySignificant = false
            )
        }

        val durations = runDurations.map { it.second }
        return CITrend(
            totalRuns = runDurations.size,
            successRate = isSuccessful.count { it.second }.toFloat() / isSuccessful.size,
            failureRate = isSuccessful.count { !it.second }.toFloat() / isSuccessful.size,
            averageDurationMs = durations.average().toLong(),
            medianDurationMs = durations.sorted()[durations.size / 2],
            slowestJob = runDurations.maxByOrNull { it.second }?.first,
            isStatisticallySignificant = true
        )
    }
}
