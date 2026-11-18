package com.postpole.spoonfile.exporter

import com.postpole.spoonfile.data.IngredientEntity
import com.postpole.spoonfile.data.RecipeEntity
import com.postpole.spoonfile.data.RecipeWithDetails
import com.postpole.spoonfile.data.StepEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class RecipeTextFormatterTest {
    private val labels = RecipeTextFormatter.Labels("Servings", "Ingredients", "Steps", "Source")

    private fun recipe(
        servingsText: String? = "4",
        sourceUrl: String? = "https://example.com/pancakes",
        ingredients: List<IngredientEntity> = listOf(
            IngredientEntity(position = 0, text = "Batter", isHeader = true),
            IngredientEntity(position = 1, text = "2 cups flour"),
            IngredientEntity(position = 2, text = "1 egg"),
        ),
        steps: List<StepEntity> = listOf(
            StepEntity(position = 0, text = "Mix."),
            StepEntity(position = 1, text = "Fry."),
        ),
    ) = RecipeWithDetails(
        recipe = RecipeEntity(uid = "u", title = " Pancakes ", servingsText = servingsText, sourceUrl = sourceUrl, createdAt = 0, updatedAt = 0),
        ingredients = ingredients,
        steps = steps,
        categories = emptyList(),
    )

    @Test
    fun formatsAllSections() {
        val expected = """
            Pancakes
            Servings: 4

            Ingredients

            Batter
            - 2 cups flour
            - 1 egg

            Steps
            1. Mix.
            2. Fry.

            Source: https://example.com/pancakes
        """.trimIndent()
        assertEquals(expected, RecipeTextFormatter.format(recipe(), labels))
    }

    @Test
    fun omitsMissingParts() {
        val text = RecipeTextFormatter.format(
            recipe(servingsText = null, sourceUrl = null, ingredients = emptyList(), steps = emptyList()),
            labels,
        )
        assertEquals("Pancakes", text)
    }

    @Test
    fun scalesIngredientsAndUsesGivenServings() {
        val text = RecipeTextFormatter.format(recipe(), labels, scale = 2.0, servings = "8")
        assertEquals(true, text.contains("Servings: 8"))
        assertEquals(true, text.contains("- 4 cups flour"))
        assertEquals(true, text.contains("- 2 egg"))
    }

    @Test
    fun stepNumbersRestartAfterAHeader() {
        val text = RecipeTextFormatter.format(
            recipe(
                steps = listOf(
                    StepEntity(position = 0, text = "A"),
                    StepEntity(position = 1, text = "Sauce", isHeader = true),
                    StepEntity(position = 2, text = "B"),
                ),
            ),
            labels,
        )
        assertEquals(true, text.contains("1. A\n\nSauce\n1. B"))
    }
}
