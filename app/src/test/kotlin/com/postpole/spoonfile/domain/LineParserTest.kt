package com.postpole.spoonfile.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class LineParserTest {
    @Test
    fun hashLinesAreHeaders_blankLinesDropped() {
        val lines = parseLines("# Dough\n2 cups flour\n\n  1 tsp salt  \n#Sauce\n# \n")
        assertEquals(
            listOf(
                ParsedLine("Dough", true),
                ParsedLine("2 cups flour", false),
                ParsedLine("1 tsp salt", false),
                ParsedLine("Sauce", true),
            ),
            lines,
        )
    }

    @Test
    fun roundTrip() {
        val text = "# A\none\ntwo"
        assertEquals(text, linesToEditorText(parseLines(text)))
    }
}
