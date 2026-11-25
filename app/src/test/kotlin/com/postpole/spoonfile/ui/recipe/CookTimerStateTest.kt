package com.postpole.spoonfile.ui.recipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CookTimerStateTest {
    @Test fun startRuns() {
        val t = CookTimerState().start(90)
        assertEquals(TimerStatus.RUNNING, t.status)
        assertEquals(90_000L, t.remainingMillis)
    }

    @Test fun pauseAndResume() {
        val t = CookTimerState().start(60).tick(10_000)
        val paused = t.pause()
        assertEquals(TimerStatus.PAUSED, paused.status)
        assertEquals(paused, paused.tick(5_000))
        assertEquals(TimerStatus.RUNNING, paused.resume().status)
        assertEquals(50_000L, paused.resume().remainingMillis)
    }

    @Test fun addMinute() {
        assertEquals(120_000L, CookTimerState().start(60).addMinute().remainingMillis)
        val finished = CookTimerState().start(1).finish()
        val again = finished.addMinute()
        assertEquals(TimerStatus.RUNNING, again.status)
        assertEquals(60_000L, again.remainingMillis)
        assertFalse(CookTimerState().addMinute().active)
    }

    @Test fun tickFinishesAtZero() {
        val t = CookTimerState().start(1).tick(1_000)
        assertEquals(TimerStatus.FINISHED, t.status)
        assertEquals(0L, t.remainingMillis)
    }

    @Test fun resetClears() {
        assertEquals(CookTimerState(), CookTimerState().start(30).reset())
        assertEquals(CookTimerState(), CookTimerState().start(1).finish().reset())
    }

    @Test fun formatting() {
        assertEquals("15:00", formatTimer(900_000))
        assertEquals("00:01", formatTimer(500))
        assertEquals("1:30:00", formatTimer(5_400_000))
        assertEquals("05:00", formatTimer(CookTimerState.QUICK_SECONDS * 1000L))
    }
}
