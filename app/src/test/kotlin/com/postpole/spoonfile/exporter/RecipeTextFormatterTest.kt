package com.postpole.spoonfile.exporter

import com.postpole.spoonfile.data.IngredientEntity
import com.postpole.spoonfile.data.RecipeEntity
import com.postpole.spoonfile.data.RecipeWithDetails
import com.postpole.spoonfile.data.StepEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class RecipeTextFormatterTest {
    private val labels = RecipeTextFormatter.Labels("Servings", "Ingredients", "Steps")

    private fun details(recipe: RecipeEntity, ingredients: List<IngredientEntity>, steps: List<StepEntity>) =
        RecipeWithDetails(recipe, ingredients, steps, emptyList())

    private fun recipe(servingsText: String? = null, servings: Double? = null, sourceUrl: String? = null) =
        RecipeEntity(uid = "u", title = "Tomato Soup", servingsText = servingsText, servings = servings, sourceUrl = sourceUrl, createdAt = 0, updatedAt = 0)

    @Test
    fun formatsTitleServingsIngredientsStepsAndSource() {
        val text = RecipeTextFormatter.format(
            details(
                recipe(servingsText = "4 bowls", sourceUrl = "https://example.com/soup"),
                listOf(
                    IngredientEntity(position = 0, text = "For the soup", isHeader = true),
                    IngredientEntity(position = 1, text = "1 onion"),
                    IngredientEntity(position = 2, text = "2 tomatoes"),
                ),
                listOf(StepEntity(position = 0, text = "Chop"), StepEntity(position = 1, text = "Simmer")),
            ),
            labels,
        )
        assertEquals(
            "Tomato Soup\nServings: 4 bowls\n\nIngredients\nFor the soup\n- 1 onion\n- 2 tomatoes\n\nSteps\n1. Chop\n2. Simmer\n\nhttps://example.com/soup",
            text,
        )
    }

    @Test
    fun usesNumericServingsAndSkipsMissingParts() {
        assertEquals("Tomato Soup\nServings: 4", RecipeTextFormatter.format(details(recipe(servings = 4.0), emptyList(), emptyList()), labels))
        assertEquals("Tomato Soup", RecipeTextFormatter.format(details(recipe(), emptyList(), emptyList()), labels))
    }

    @Test
    fun ingredientsAreNeverTruncated() {
        val many = List(60) { IngredientEntity(position = it, text = "ingredient number $it with a long description") }
        val text = RecipeTextFormatter.format(details(recipe(), many, emptyList()), labels)
        assertEquals(60, text.lines().count { it.startsWith("- ingredient number") })
    }
}
