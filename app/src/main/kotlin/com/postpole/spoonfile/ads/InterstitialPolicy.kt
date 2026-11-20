package com.postpole.spoonfile.ads

import java.time.Instant
import java.time.ZoneId

/** What the interstitial caps need to remember between launches (persisted in DataStore). */
data class InterstitialHistory(
    /** How many app sessions (process starts) have happened, including the current one. */
    val sessionCount: Int = 0,
    /** Epoch millis of the last interstitial shown, or null if never. */
    val lastShownAt: Long? = null,
    /** Local date (ISO yyyy-MM-dd) that [shownToday] counts for. */
    val day: String? = null,
    val shownToday: Int = 0,
    /** Interstitials shown since this process started. */
    val shownThisSession: Int = 0,
)

/**
 * Pure interstitial frequency rules: not the first session, not within the first N install days,
 * at least M seconds apart, at most K per session and L per day.
 */
object InterstitialPolicy {
    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun isDue(
        config: AdsConfig,
        history: InterstitialHistory,
        installedAt: Long,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        if (config.interstitialBlockFirstSession && history.sessionCount <= 1) return false
        if (now - installedAt < config.interstitialMinInstallDays * DAY_MS) return false
        if (history.shownThisSession >= config.interstitialMaxPerSession) return false
        val today = dayKey(now, zone)
        val shownToday = if (history.day == today) history.shownToday else 0
        if (shownToday >= config.interstitialMaxPerDay) return false
        val last = history.lastShownAt
        // A clock that moved backwards counts as "too soon" rather than letting an ad through.
        if (last != null && now - last < config.interstitialMinIntervalSec * 1000L) return false
        return true
    }

    /** History after an interstitial was actually shown at [now]. */
    fun recordShown(
        history: InterstitialHistory,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): InterstitialHistory {
        val today = dayKey(now, zone)
        val shownToday = if (history.day == today) history.shownToday else 0
        return history.copy(
            lastShownAt = now,
            day = today,
            shownToday = shownToday + 1,
            shownThisSession = history.shownThisSession + 1,
        )
    }

    fun dayKey(now: Long, zone: ZoneId): String = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toString()
}
