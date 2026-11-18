package com.postpole.spoonfile.importer.ocr

import com.postpole.spoonfile.importer.ocr.LineRole.IGNORE
import com.postpole.spoonfile.importer.ocr.LineRole.INGREDIENT
import com.postpole.spoonfile.importer.ocr.LineRole.NOTE
import com.postpole.spoonfile.importer.ocr.LineRole.STEP
import com.postpole.spoonfile.importer.ocr.LineRole.TITLE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LineRoleGuesserTest {
    /** Guesses [line] when it follows a title line. */
    private fun one(line: String) = LineRoleGuesser.guess(listOf("Title", line))[1]

    @Test fun firstShortLineIsTitle() {
        assertEquals(listOf(TITLE), LineRoleGuesser.guess(listOf("Lemon Drizzle Cake")))
    }

    @Test fun onlyOneTitle() {
        assertEquals(listOf(TITLE, NOTE), LineRoleGuesser.guess(listOf("Lemon Cake", "Serves a crowd")))
    }

    @Test fun plainQuantityIsIngredient() = assertEquals(INGREDIENT, one("200 g plain flour"))

    @Test fun decimalAndRangeQuantity() {
        assertEquals(INGREDIENT, one("1.5 cups milk"))
        assertEquals(INGREDIENT, one("2-3 carrots"))
    }

    @Test fun fractionQuantity() {
        assertEquals(INGREDIENT, one("1/2 tsp salt"))
        assertEquals(INGREDIENT, one("½ cup sugar"))
        assertEquals(INGREDIENT, one("1 1/2 cups oats"))
    }

    @Test fun bulletIsIngredient() {
        assertEquals(INGREDIENT, one("• butter, softened"))
        assertEquals(INGREDIENT, one("- a handful of parsley"))
    }

    @Test fun numberedLineIsStep() {
        assertEquals(STEP, one("1. Preheat the oven"))
        assertEquals(STEP, one("2) Mix well"))
        assertEquals(STEP, one("Step 3: bake"))
    }

    @Test fun longSentenceIsStep() {
        assertEquals(STEP, one("Stir the flour into the butter until the mixture looks like fine crumbs."))
    }

    @Test fun shortUnnumberedTextAfterTitleIsNote() = assertEquals(NOTE, one("Keeps for three days"))

    @Test fun blankLineIsIgnored() = assertEquals(IGNORE, one("   "))

    @Test fun numberedWinsOverQuantity() = assertEquals(STEP, one("1. 200 g flour into a bowl"))

    @Test fun stripMarkersForSaving() {
        assertEquals("Preheat the oven", LineRoleGuesser.stripMarker("1. Preheat the oven", STEP))
        assertEquals("butter", LineRoleGuesser.stripMarker("• butter", INGREDIENT))
        assertEquals("200 g flour", LineRoleGuesser.stripMarker("200 g flour", INGREDIENT))
        assertEquals("1. Title", LineRoleGuesser.stripMarker("1. Title", TITLE))
    }

    @Test fun markerHelpers() {
        assertTrue(LineRoleGuesser.startsWithBullet("* eggs"))
        assertFalse(LineRoleGuesser.startsWithBullet("eggs"))
        assertTrue(LineRoleGuesser.startsWithNumber("4. Serve"))
        assertTrue(LineRoleGuesser.startsWithQuantity("3 eggs"))
    }
}
