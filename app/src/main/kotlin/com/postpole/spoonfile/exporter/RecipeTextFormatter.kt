package com.postpole.spoonfile.exporter

import com.postpole.spoonfile.data.RecipeWithDetails
import com.postpole.spoonfile.domain.QuantityScaler

/** Turns one recipe into plain text for the Share sheet: title, servings, ingredients, steps, source link. */
object RecipeTextFormatter {

    class Labels(val servings: String, val ingredients: String, val steps: String, val source: String)

    /**
     * [scale] is applied to ingredient quantities and [servings] replaces the saved servings text, so the text
     * matches what the screen shows after the servings stepper was used.
     */
    fun format(
        details: RecipeWithDetails,
        labels: Labels,
        scale: Double = 1.0,
        servings: String? = null,
    ): String {
        val r = details.recipe
        val servingsLine = servings ?: r.servingsText?.takeIf { it.isNotBlank() }
        val ingredients = details.ingredients.sortedBy { it.position }
        val steps = details.steps.sortedBy { it.position }
        return buildString {
            append(r.title.trim())
            if (servingsLine != null) append("\n").append(labels.servings).append(": ").append(servingsLine)
            if (ingredients.isNotEmpty()) {
                append("\n\n").append(labels.ingredients)
                ingredients.forEach { line ->
                    if (line.isHeader) append("\n\n").append(line.text.trim()) else {
                        append("\n- ").append(QuantityScaler.scale(line.text.trim(), scale))
                    }
                }
            }
            if (steps.isNotEmpty()) {
                append("\n\n").append(labels.steps)
                var number = 0
                steps.forEach { step ->
                    if (step.isHeader) {
                        append("\n\n").append(step.text.trim())
                        number = 0
                    } else {
                        append("\n").append(++number).append(". ").append(step.text.trim())
                    }
                }
            }
            r.sourceUrl?.takeIf { it.isNotBlank() }?.let { append("\n\n").append(labels.source).append(": ").append(it.trim()) }
        }
    }
}
