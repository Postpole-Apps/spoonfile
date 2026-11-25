package com.postpole.spoonfile.ui.recipe

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver

enum class TimerStatus { IDLE, RUNNING, PAUSED, FINISHED }

/** The single cook-mode countdown. Immutable; every transition returns a new state. */
data class CookTimerState(val status: TimerStatus = TimerStatus.IDLE, val remainingMillis: Long = 0L) {
    val active: Boolean get() = status != TimerStatus.IDLE

    fun start(seconds: Int): CookTimerState =
        if (seconds <= 0) this else CookTimerState(TimerStatus.RUNNING, seconds * 1000L)

    fun pause(): CookTimerState = if (status == TimerStatus.RUNNING) copy(status = TimerStatus.PAUSED) else this

    fun resume(): CookTimerState = if (status == TimerStatus.PAUSED) copy(status = TimerStatus.RUNNING) else this

    /** Adds a minute; on a finished timer this restarts it with one minute left. */
    fun addMinute(): CookTimerState = when (status) {
        TimerStatus.IDLE -> this
        TimerStatus.FINISHED -> CookTimerState(TimerStatus.RUNNING, MINUTE)
        else -> copy(remainingMillis = remainingMillis + MINUTE)
    }

    fun reset(): CookTimerState = CookTimerState()

    fun finish(): CookTimerState = if (active) CookTimerState(TimerStatus.FINISHED, 0L) else this

    /** Advances a running timer by [elapsedMillis]; finishes it at zero. */
    fun tick(elapsedMillis: Long): CookTimerState {
        if (status != TimerStatus.RUNNING) return this
        val left = remainingMillis - elapsedMillis
        return if (left <= 0L) finish() else copy(remainingMillis = left)
    }

    companion object {
        const val MINUTE = 60_000L
        const val QUICK_SECONDS = 5 * 60

        val Saver: Saver<CookTimerState, Any> = listSaver(
            save = { listOf(it.status.name, it.remainingMillis) },
            restore = { CookTimerState(TimerStatus.valueOf(it[0] as String), it[1] as Long) },
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
