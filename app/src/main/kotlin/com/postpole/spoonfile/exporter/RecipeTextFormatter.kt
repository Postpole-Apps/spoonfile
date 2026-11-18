package com.postpole.spoonfile.exporter

import com.postpole.spoonfile.data.RecipeWithDetails

/** Plain-text version of one recipe for the system Share sheet. */
object RecipeTextFormatter {
    /** Labels come from resources so the text follows the app language. */
    class Labels(val servings: String, val ingredients: String, val steps: String)

    fun format(details: RecipeWithDetails, labels: Labels): String {
        val r = details.recipe
        val sb = StringBuilder()
        sb.append(r.title)
        val servings = r.servingsText?.takeIf { it.isNotBlank() }
            ?: r.servings?.let { if (it == Math.floor(it)) it.toLong().toString() else it.toString() }
        if (servings != null) sb.append("\n").append(labels.servings).append(": ").append(servings)

        if (details.ingredients.isNotEmpty()) {
            sb.append("\n\n").append(labels.ingredients)
            details.ingredients.forEach { line ->
                sb.append('\n')
                if (line.isHeader) sb.append(line.text) else sb.append("- ").append(line.text)
            }
        }
        if (details.steps.isNotEmpty()) {
            sb.append("\n\n").append(labels.steps)
            var number = 0
            details.steps.forEach { step ->
                sb.append('\n')
                if (step.isHeader) {
                    sb.append(step.text)
                } else {
                    number++
                    sb.append(number).append(". ").append(step.text)
                }
            }
        }
        r.sourceUrl?.takeIf { it.isNotBlank() }?.let { sb.append("\n\n").append(it) }
        return sb.toString()
    }
}
