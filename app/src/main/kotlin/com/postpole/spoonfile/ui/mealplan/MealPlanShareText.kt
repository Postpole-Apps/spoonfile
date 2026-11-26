package com.postpole.spoonfile.ui.mealplan

/**
 * Plain-text body for sharing a week: the [heading], then one line per day that has recipes
 * (`dayLine(weekday, titles joined by ", ")`), in order. Days without recipes are omitted; a week with no
 * recipes at all is the heading followed by [emptyLine].
 */
internal fun formatMealPlanShare(
    heading: String,
    days: List<Pair<String, List<String>>>,
    emptyLine: String,
    dayLine: (weekday: String, titles: String) -> String,
): String {
    val lines = days
        .filter { it.second.isNotEmpty() }
        .map { (weekday, titles) -> dayLine(weekday, titles.joinToString(", ")) }
    return (listOf(heading) + lines.ifEmpty { listOf(emptyLine) }).joinToString("\n")
}
