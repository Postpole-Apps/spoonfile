package com.postpole.spoonfile.ui.importer

import com.postpole.spoonfile.importer.web.ImportedLine
import com.postpole.spoonfile.importer.web.ImportedRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ImportMappingTest {
    @Test
    fun servings_numberOnly_hasNoText() {
        assertEquals(4.0 to null, splitServings("4"))
        assertEquals(1.5 to null, splitServings("1,5"))
    }

    @Test
    fun servings_withWords_keepsTextAndLeadingNumber() {
        assertEquals(4.0 to "4 servings", splitServings("4 servings"))
        assertEquals(null to "a few", splitServings("a few"))
        assertEquals(null to null, splitServings("  "))
    }

    @Test
    fun suggestedCategories_startUnticked_andHeadersSurvive() {
        val form = ImportedRecipe(
            title = "T",
            ingredients = listOf(ImportedLine("Dough", true), ImportedLine("flour")),
            steps = listOf(ImportedLine("Mix")),
            suggestedCategories = listOf("Dinner", "Soup"),
            sourceUrl = "https://example.com/r",
        ).toForm("https://example.com/r")
        assertEquals("# Dough\nflour", form.ingredients)
        assertEquals(listOf("Dinner", "Soup"), form.categories.map { it.name })
        assertFalse(form.categories.any { it.ticked })
        assertEquals("https://example.com/r", form.sourceUrl)
        assertNull(form.categories.firstOrNull { it.ticked })
    }
}
