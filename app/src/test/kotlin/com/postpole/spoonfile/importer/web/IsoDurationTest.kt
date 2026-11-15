package com.postpole.spoonfile.importer.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IsoDurationTest {
    @Test
    fun parsesCommonForms() {
        assertEquals(90, IsoDuration.toMinutes("PT1H30M"))
        assertEquals(90, IsoDuration.toMinutes("PT90M"))
        assertEquals(20, IsoDuration.toMinutes("P0DT0H20M"))
        assertEquals(90, IsoDuration.toMinutes("PT1H30M0S"))
        assertEquals(1500, IsoDuration.toMinutes("P1DT1H"))
    }

    @Test
    fun fallbackReadsLooseText() {
        assertEquals(135, IsoDuration.toMinutes("2 h 15 min"))
        assertEquals(45, IsoDuration.toMinutes("45 minutes"))
    }

    @Test
    fun ignoresZeroNegativeHugeAndGarbage() {
        assertNull(IsoDuration.toMinutes("PT0M"))
        assertNull(IsoDuration.toMinutes("-PT5M"))
        assertNull(IsoDuration.toMinutes("PT10001M"))
        assertEquals(10_000, IsoDuration.toMinutes("PT10000M"))
        assertNull(IsoDuration.toMinutes("soon"))
        assertNull(IsoDuration.toMinutes(""))
        assertNull(IsoDuration.toMinutes(null))
    }
}
