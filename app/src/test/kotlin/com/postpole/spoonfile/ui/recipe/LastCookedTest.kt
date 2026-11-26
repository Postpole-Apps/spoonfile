package com.postpole.spoonfile.ui.recipe

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class LastCookedTest {
    private val zone = ZoneId.of("UTC")

    private fun at(y: Int, m: Int, d: Int, h: Int) = ZonedDateTime.of(y, m, d, h, 0, 0, 0, zone).toInstant().toEpochMilli()

    @Test fun sameDay_isToday() {
        assertEquals(LastCookedLabel.Today, lastCookedLabel(at(2026, 11, 26, 0), at(2026, 11, 26, 23), zone))
    }

    @Test fun previousDay_isYesterday_evenAcrossMidnightByMinutes() {
        assertEquals(LastCookedLabel.Yesterday, lastCookedLabel(at(2026, 11, 25, 23), at(2026, 11, 26, 0), zone))
    }

    @Test fun older_isADate() {
        assertEquals(
            LastCookedLabel.On(LocalDate.of(2026, 11, 3)),
            lastCookedLabel(at(2026, 11, 3, 12), at(2026, 11, 26, 12), zone),
        )
    }
}
