package com.postpole.spoonfile.importer.paprika

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaprikaTimeTest {
    @Test
    fun minutesOnly() {
        assertEquals(15, PaprikaTime.parseMinutes("15 mins"))
        assertEquals(5, PaprikaTime.parseMinutes("5 min"))
        assertEquals(45, PaprikaTime.parseMinutes("45 minutes"))
        assertEquals(30, PaprikaTime.parseMinutes("30m"))
    }

    @Test
    fun hoursAndMinutes() {
        assertEquals(80, PaprikaTime.parseMinutes("1 hr 20 min"))
        assertEquals(125, PaprikaTime.parseMinutes("2 hours 5 minutes"))
        assertEquals(60, PaprikaTime.parseMinutes("1 hour"))
        assertEquals(90, PaprikaTime.parseMinutes("1h30m"))
    }

    @Test
    fun fractionalHours() {
        assertEquals(90, PaprikaTime.parseMinutes("1.5 hours"))
    }

    @Test
    fun unreadableOrEmptyIsNull() {
        assertNull(PaprikaTime.parseMinutes("overnight"))
        assertNull(PaprikaTime.parseMinutes(""))
        assertNull(PaprikaTime.parseMinutes(null))
        assertNull(PaprikaTime.parseMinutes("0 mins"))
        assertNull(PaprikaTime.parseMinutes("99999 hours"))
    }
}
