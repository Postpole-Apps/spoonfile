package com.postpole.spoonfile.ui.recipe

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver

enum class TimerStatus { IDLE, RUNNING, PAUSED, FINISHED }

/**
 * The single cook-mode countdown. Immutable; every transition returns a new state.
 *
 * While [TimerStatus.RUNNING] the truth is [endAtMillis], a `SystemClock.elapsedRealtime()` deadline
 * (it survives process death), and [remainingMillis] is only the value computed at the last [tick].
 * For PAUSED / FINISHED, [remainingMillis] is authoritative. Every time-dependent call takes `now`
 * (an `elapsedRealtime()` value) so this class stays pure.
 */
data class CookTimerState(
    val status: TimerStatus = TimerStatus.IDLE,
    val remainingMillis: Long = 0L,
    val endAtMillis: Long = 0L,
) {
    val active: Boolean get() = status != TimerStatus.IDLE

    fun start(seconds: Int, now: Long): CookTimerState =
        if (seconds <= 0) this else running(seconds * 1000L, now)

    fun pause(now: Long): CookTimerState {
        if (status != TimerStatus.RUNNING) return this
        val left = endAtMillis - now
        return if (left <= 0L) finish() else CookTimerState(TimerStatus.PAUSED, left)
    }

    fun resume(now: Long): CookTimerState =
        if (status == TimerStatus.PAUSED) running(remainingMillis, now) else this

    /** Adds a minute; on a finished timer this restarts it with one minute left. */
    fun addMinute(now: Long): CookTimerState = when (status) {
        TimerStatus.IDLE -> this
        TimerStatus.FINISHED -> running(MINUTE, now)
        TimerStatus.PAUSED -> copy(remainingMillis = remainingMillis + MINUTE)
        TimerStatus.RUNNING -> {
            val left = endAtMillis - now
            if (left <= 0L) running(MINUTE, now) else running(left + MINUTE, now)
        }
    }

    fun reset(): CookTimerState = CookTimerState()

    fun finish(): CookTimerState = if (active) CookTimerState(TimerStatus.FINISHED, 0L) else this

    /** Recomputes a running timer from its deadline; finishes it at once if the deadline has passed. */
    fun tick(now: Long): CookTimerState {
        if (status != TimerStatus.RUNNING) return this
        val left = endAtMillis - now
        return if (left <= 0L) finish() else copy(remainingMillis = left)
    }

    private fun running(millis: Long, now: Long) = CookTimerState(TimerStatus.RUNNING, millis, now + millis)

    companion object {
        const val MINUTE = 60_000L
        const val QUICK_SECONDS = 5 * 60

        val Saver: Saver<CookTimerState, Any> = listSaver(
            save = { listOf(it.status.name, it.remainingMillis, it.endAtMillis) },
            restore = { CookTimerState(TimerStatus.valueOf(it[0] as String), it[1] as Long, it[2] as Long) },
        )
    }
}

/** MM:SS, or H:MM:SS from one hour up. Partial seconds round up so the display hits 00:00 only at the end. */
fun formatTimer(millis: Long): String {
    val total = ((millis + 999) / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    val l = java.util.Locale.ROOT
    return if (h > 0) String.format(l, "%d:%02d:%02d", h, m, s) else String.format(l, "%02d:%02d", m, s)
}
