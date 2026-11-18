package com.postpole.spoonfile.importer.ocr

import com.postpole.spoonfile.importer.ocr.LineRole.IGNORE
import com.postpole.spoonfile.importer.ocr.LineRole.INGREDIENT
import com.postpole.spoonfile.importer.ocr.LineRole.NOTE
import com.postpole.spoonfile.importer.ocr.LineRole.STEP
import com.postpole.spoonfile.importer.ocr.LineRole.TITLE
import org.junit.Assert.assertEquals
import org.junit.Test

class LineRoleGuesserTest {
    private fun guess(vararg lines: String, hasTitle: Boolean = false) = LineRoleGuesser.guess(lines.toList(), hasTitle)

    @Test
    fun firstShortLineIsTheTitle() {
        assertEquals(listOf(TITLE), guess("Grandma's Tomato Soup"))
    }

    @Test
    fun firstLineIsNotATitleWhenItStartsWithAQuantity() {
        assertEquals(listOf(INGREDIENT), guess("2 cups flour"))
    }

    @Test
    fun firstLongSentenceIsNotATitle() {
        val long = "Heat the oven to 180 degrees and grease a large baking tin with butter before you start."
        assertEquals(listOf(STEP), guess(long))
    }

    @Test
    fun quantityLinesAreIngredients() {
        assertEquals(
            listOf(INGREDIENT, INGREDIENT, INGREDIENT, INGREDIENT, INGREDIENT),
            guess("200 g sugar", "1/2 tsp salt", "1 1/2 cups milk", "½ cup oil", "2-3 cloves garlic", hasTitle = true),
        )
    }

    @Test
    fun bulletLinesAreIngredients() {
        assertEquals(
            listOf(INGREDIENT, INGREDIENT, INGREDIENT),
            guess("• butter", "- a pinch of salt", "* fresh basil", hasTitle = true),
        )
    }

    @Test
    fun numberedLinesAreSteps() {
        assertEquals(
            listOf(STEP, STEP, STEP),
            guess("1. Mix the flour", "2) Add the eggs", "Step 3: Bake", hasTitle = true),
        )
    }

    @Test
    fun aDecimalQuantityIsNotANumberedStep() {
        assertEquals(listOf(INGREDIENT), guess("1.5 cups water", hasTitle = true))
    }

    @Test
    fun longSentencesAreSteps() {
        assertEquals(
            listOf(STEP),
            guess("Stir everything together until smooth, then leave to rest for ten minutes.", hasTitle = true),
        )
    }

    @Test
    fun onlyTheFirstLineCanBeTheTitle() {
        assertEquals(listOf(TITLE, NOTE), guess("Lemon Cake", "Serves a crowd"))
    }

    @Test
    fun existingTitleStopsAnotherBeingGuessed() {
        assertEquals(listOf(NOTE), guess("Page two heading", hasTitle = true))
    }

    @Test
    fun sectionHeadingsAreIgnored() {
        assertEquals(
            listOf(TITLE, IGNORE, INGREDIENT, IGNORE, STEP),
            guess("Pancakes", "Ingredients", "2 eggs", "Method:", "1. Whisk"),
        )
    }

    @Test
    fun shortLineAfterAnIngredientIsAnIngredient() {
        assertEquals(listOf(INGREDIENT, INGREDIENT), guess("2 cups flour", "Salt", hasTitle = true))
    }

    @Test
    fun blankLinesAreIgnored() {
        assertEquals(listOf(IGNORE), guess("   ", hasTitle = true))
    }

    @Test
    fun stripsBulletsAndNumbers() {
        assertEquals("butter", LineRoleGuesser.stripBullet("• butter"))
        assertEquals("Mix well", LineRoleGuesser.stripNumber("2. Mix well"))
        assertEquals("Bake", LineRoleGuesser.stripNumber("Step 3: Bake"))
        assertEquals("1.5 cups water", LineRoleGuesser.stripNumber("1.5 cups water"))
    }
}
