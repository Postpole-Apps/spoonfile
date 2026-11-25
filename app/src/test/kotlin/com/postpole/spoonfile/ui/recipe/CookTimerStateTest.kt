package com.postpole.spoonfile.ui.recipe

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CookTimerStateTest {
    private fun roundTrip(t: CookTimerState): CookTimerState = with(CookTimerState.Saver) {
        val saved = SaverScope { true }.save(t)!!
        restore(saved)!!
    }

    @Test fun startRuns() {
        val t = CookTimerState().start(90, now = 1_000)
        assertEquals(TimerStatus.RUNNING, t.status)
        assertEquals(90_000L, t.remainingMillis)
        assertEquals(91_000L, t.endAtMillis)
    }

    @Test fun pauseAndResume() {
        val t = CookTimerState().start(60, 0).tick(10_000)
        val paused = t.pause(10_000)
        assertEquals(TimerStatus.PAUSED, paused.status)
        assertEquals(50_000L, paused.remainingMillis)
        assertEquals(paused, paused.tick(99_000))
        val resumed = paused.resume(20_000)
        assertEquals(TimerStatus.RUNNING, resumed.status)
        assertEquals(70_000L, resumed.endAtMillis)
        assertEquals(30_000L, resumed.tick(40_000).remainingMillis)
    }

    @Test fun addMinute() {
        val running = CookTimerState().start(60, 0).addMinute(10_000)
        assertEquals(110_000L, running.remainingMillis)
        assertEquals(120_000L, running.endAtMillis)
        val paused = CookTimerState().start(60, 0).pause(10_000).addMinute(50_000)
        assertEquals(110_000L, paused.remainingMillis)
        val again = CookTimerState().start(1, 0).finish().addMinute(5_000)
        assertEquals(TimerStatus.RUNNING, again.status)
        assertEquals(60_000L, again.remainingMillis)
        assertEquals(65_000L, again.endAtMillis)
        assertFalse(CookTimerState().addMinute(0).active)
    }

    @Test fun tickFinishesAtDeadline() {
        val t = CookTimerState().start(1, 0).tick(1_000)
        assertEquals(TimerStatus.FINISHED, t.status)
        assertEquals(0L, t.remainingMillis)
    }

    @Test fun restoreAfterDeadlineFinishes() {
        val restored = roundTrip(CookTimerState().start(60, 5_000))
        assertEquals(TimerStatus.RUNNING, restored.status)
        assertEquals(TimerStatus.FINISHED, restored.tick(65_000).status)
        assertEquals(TimerStatus.FINISHED, restored.tick(500_000).status)
    }

    @Test fun restoreBeforeDeadlineKeepsRemainder() {
        val restored = roundTrip(CookTimerState().start(60, 5_000))
        val t = restored.tick(35_000)
        assertEquals(TimerStatus.RUNNING, t.status)
        assertEquals(30_000L, t.remainingMillis)
    }

    @Test fun pausedSurvivesRestore() {
        val paused = CookTimerState().start(60, 0).pause(20_000)
        assertEquals(paused, roundTrip(paused))
        assertEquals(40_000L, roundTrip(paused).tick(900_000).remainingMillis)
    }

    @Test fun resetClears() {
        assertEquals(CookTimerState(), CookTimerState().start(30, 0).reset())
        assertEquals(CookTimerState(), CookTimerState().start(1, 0).finish().reset())
    }

    @Test fun formatting() {
        assertEquals("15:00", formatTimer(900_000))
        assertEquals("00:01", formatTimer(500))
        assertEquals("1:30:00", formatTimer(5_400_000))
        assertEquals("05:00", formatTimer(CookTimerState.QUICK_SECONDS * 1000L))
    }
}
