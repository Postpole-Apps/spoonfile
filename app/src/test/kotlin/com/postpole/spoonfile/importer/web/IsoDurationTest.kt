package com.postpole.spoonfile.importer.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IsoDurationTest {
    @Test
    fun parsesCommonForms() {
        assertEquals(90, IsoDuration.parseMinutes("PT1H30M"))
        assertEquals(90, IsoDuration.parseMinutes("PT90M"))
        assertEquals(20, IsoDuration.parseMinutes("P0DT0H20M"))
        assertEquals(90, IsoDuration.parseMinutes("PT1H30M0S"))
        assertEquals(45, IsoDuration.parseMinutes("pt45m"))
    }

    @Test
    fun fallsBackToHoursAndMinutesRegex() {
        assertEquals(75, IsoDuration.parseMinutes("1 H 15 M"))
        assertEquals(30, IsoDuration.parseMinutes("30 min"))
        assertEquals(120, IsoDuration.parseMinutes("2h"))
    }

    @Test
    fun ignoresZeroNegativeHugeAndGarbage() {
        assertNull(IsoDuration.parseMinutes("PT0M"))
        assertNull(IsoDuration.parseMinutes("-PT5M"))
        assertNull(IsoDuration.parseMinutes("PT10001M"))
        assertEquals(10_000, IsoDuration.parseMinutes("PT10000M"))
        assertNull(IsoDuration.parseMinutes("soon"))
        assertNull(IsoDuration.parseMinutes(""))
        assertNull(IsoDuration.parseMinutes(null))
    }
}
