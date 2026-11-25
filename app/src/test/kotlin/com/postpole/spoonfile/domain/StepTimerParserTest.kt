package com.postpole.spoonfile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StepTimerParserTest {
    private fun p(s: String) = StepTimerParser.parse(s)

    @Test fun minutesInAllLocales() {
        assertEquals(900, p("Simmer for 15 min"))
        assertEquals(900, p("Cook 15 mins"))
        assertEquals(900, p("Simmer 15 minutes."))
        assertEquals(900, p("15 Minuten köcheln"))
        assertEquals(900, p("Cocer 15 minutos"))
        assertEquals(900, p("Cuire 15 minutes"))
        assertEquals(900, p("Cuocere 15 minuti"))
        assertEquals(900, p("Asse por 15 minutos"))
        assertEquals(600, p("Bake 10min"))
    }

    @Test fun seconds() {
        assertEquals(45, p("Sear 45 sec"))
        assertEquals(45, p("Sear 45 s"))
        assertEquals(45, p("45 Sek. braten"))
        assertEquals(30, p("30 seconds"))
        assertEquals(30, p("30 secondes"))
        assertEquals(30, p("30 secondi"))
        assertEquals(30, p("30 segundos"))
        assertEquals(30, p("30 Sekunden"))
    }

    @Test fun hours() {
        assertEquals(7200, p("Roast 2 hours"))
        assertEquals(3600, p("1 Std. ruhen"))
        assertEquals(3600, p("1 heure"))
        assertEquals(3600, p("1 ora"))
        assertEquals(3600, p("1 hora"))
        assertEquals(7200, p("2 ore"))
        assertEquals(3600, p("1 h"))
        assertEquals(5400, p("1.5 hours"))
    }

    @Test fun compound() {
        assertEquals(5400, p("Bake 1 h 30 min"))
        assertEquals(4500, p("Chill 1 hr 15 min"))
        assertEquals(5400, p("1 Std. 30 Min."))
        assertEquals(5400, p("1 hour and 30 minutes"))
    }

    @Test fun rangesTakeLowerBound() {
        assertEquals(600, p("Bake 10–12 min"))
        assertEquals(600, p("Bake 10-12 min"))
        assertEquals(600, p("Bake 10 to 12 minutes"))
        assertEquals(600, p("10 bis 12 Minuten"))
        assertEquals(600, p("10 a 12 minutos"))
    }

    @Test fun ignoresTemperaturesAndQuantities() {
        assertNull(p("Preheat the oven to 180 °C"))
        assertNull(p("Preheat to 350 F"))
        assertNull(p("Add 2 cups flour and 250 g sugar"))
        assertNull(p("Cut into 3 slices"))
        assertEquals(1200, p("Heat oven to 180 °C, bake 20 min"))
    }

    @Test fun noneOrTooLong() {
        assertNull(p("Stir well"))
        assertNull(p(""))
        assertNull(p("Marinate 13 hours"))
        assertEquals(12 * 3600, p("Marinate 12 hours"))
        assertNull(p("0 min"))
    }
}
