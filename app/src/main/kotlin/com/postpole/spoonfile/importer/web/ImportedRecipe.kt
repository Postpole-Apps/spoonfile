package com.postpole.spoonfile.importer.web

/** One ingredient or step; a header is a section heading ("For the sauce"). */
data class ImportedLine(val text: String, val isHeader: Boolean = false)

/** A recipe read from a web page. Nothing here is saved until the user confirms the preview. */
data class ImportedRecipe(
    val title: String,
    val description: String? = null,
    val sourceName: String? = null,
    val sourceUrl: String,
    val ingredients: List<ImportedLine> = emptyList(),
    val steps: List<ImportedLine> = emptyList(),
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    val totalMinutes: Int? = null,
    val servingsText: String? = null,
    val servings: Double? = null,
    val imageUrl: String? = null,
    /** From recipeCategory / recipeCuisine. Offered in the preview but never ticked for the user. */
    val suggestedCategories: List<String> = emptyList(),
    val nutritionText: String? = null,
)
