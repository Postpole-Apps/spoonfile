package com.postpole.spoonfile.ui.recipe

import org.junit.Assert.assertEquals
import org.junit.Test

class RecipeLinesTest {
    @Test
    fun parseLines_hashMakesHeader_blankLinesDropped() {
        val parsed = parseLines("# Dough\n2 cups flour\n\n  1 tsp salt  \n##Sauce\n#\n")
        assertEquals(
            listOf("Dough" to true, "2 cups flour" to false, "1 tsp salt" to false, "Sauce" to true),
            parsed,
        )
    }

    @Test
    fun linesToText_roundTrips() {
        val items = listOf("Dough" to true, "2 cups flour" to false)
        assertEquals(items, parseLines(linesToText(items)))
    }

    @Test
    fun stepNumbers_skipHeaders() {
        assertEquals(listOf(0, 1, 2, 0, 3), stepNumbers(listOf(true, false, false, true, false)))
    }
}
