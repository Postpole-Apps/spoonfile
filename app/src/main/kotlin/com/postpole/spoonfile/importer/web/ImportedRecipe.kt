package com.postpole.spoonfile.importer.web

/** One ingredient or step line; [isHeader] marks a section heading. */
data class ImportedLine(val text: String, val isHeader: Boolean = false)

/** A recipe read from a web page. Nothing here is saved until the user confirms the preview. */
data class ImportedRecipe(
    val title: String,
    val description: String? = null,
    val ingredients: List<ImportedLine> = emptyList(),
    val steps: List<ImportedLine> = emptyList(),
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    val totalMinutes: Int? = null,
    val servings: Double? = null,
    val servingsText: String? = null,
    /** Absolute image URL resolved against the page, if the page names one. */
    val imageUrl: String? = null,
    /** From recipeCategory / recipeCuisine. Offered as suggestions, never applied automatically. */
    val suggestedCategories: List<String> = emptyList(),
    val nutritionText: String? = null,
    val sourceName: String? = null,
    val sourceUrl: String,
)
