package com.postpole.spoonfile.importer.web

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.net.URI

/**
 * Reads a schema.org `Recipe` out of a page's JSON-LD. Pure parsing: no network, no storage.
 * Written from the schema.org vocabulary; behaviour mirrors what recipe-scrapers accepts, no code or text copied.
 */
object JsonLdRecipeParser {
    private const val MAX_DEPTH = 10
    private const val MAX_CATEGORIES = 12

    /** The first Recipe on the page, or null when there is none (or it has neither ingredients nor steps). */
    fun parse(html: String, pageUrl: String): ImportedRecipe? {
        val doc = Jsoup.parse(html, pageUrl)
        val roots = doc.select("script[type]")
            .filter { it.attr("type").substringBefore(';').trim().equals("application/ld+json", ignoreCase = true) }
            .mapNotNull { readJson(it.data()) }
        val recipe = roots.firstNotNullOfOrNull { findRecipe(it, 0) } ?: return null

        val ingredients = ingredients(recipe)
        val steps = steps(recipe.opt("recipeInstructions") ?: recipe.opt("RecipeInstructions"))
        if (ingredients.isEmpty() && steps.isEmpty()) return null

        val ogSite = doc.selectFirst("meta[property=og:site_name]")?.attr("content")?.let(::cleanInline)?.ifEmpty { null }
        val siteName = ogSite ?: roots.firstNotNullOfOrNull { websiteName(it, 0) } ?: publisherName(recipe.opt("publisher"))
        val host = hostOf(pageUrl)
        val servingsText = yieldText(recipe.opt("recipeYield"))

        return ImportedRecipe(
            title = cleanInline(str(recipe, "name")).ifEmpty { doc.title().let(::cleanInline) }.ifEmpty { host.orEmpty() },
            description = cleanInline(str(recipe, "description")).ifEmpty { null },
            // The author only stands in when the page names no site; the bare host is the last resort.
            sourceName = siteName ?: names(recipe.opt("author")).joinToString(", ").ifEmpty { null } ?: host,
            sourceUrl = pageUrl,
            ingredients = ingredients.map { ImportedLine(it) },
            steps = steps,
            prepMinutes = minutes(recipe.opt("prepTime")),
            cookMinutes = minutes(recipe.opt("cookTime")),
            totalMinutes = minutes(recipe.opt("totalTime")),
            servingsText = servingsText,
            servings = servingsText?.let(::leadingNumber),
            imageUrl = imageUrl(recipe.opt("image"), pageUrl),
            suggestedCategories = (strings(recipe.opt("recipeCategory")) + strings(recipe.opt("recipeCuisine")))
                .flatMap { it.split(',') }.map(::cleanInline).filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }.take(MAX_CATEGORIES),
            nutritionText = nutrition(recipe.opt("nutrition")),
        )
    }

    // ---- JSON-LD discovery ----

    private fun readJson(raw: String): Any? {
        val text = raw.trim().trimStart(Char(0xFEFF)).trim()
        if (text.isEmpty()) return null
        return try {
            when (val value = JSONTokener(text).nextValue()) {
                is JSONObject, is JSONArray -> value
                else -> null
            }
        } catch (_: JSONException) {
            null
        }
    }

    /** A string or number field; JSON null and other shapes read as empty. */
    private fun str(node: JSONObject, key: String): String = when (val v = node.opt(key)) {
        is String -> v
        is Number -> v.toString()
        else -> ""
    }

    /** `@type` values, lower-cased, with any `schema:` / `https://schema.org/` prefix dropped. */
    private fun typesOf(node: JSONObject): List<String> =
        strings(node.opt("@type")).map { it.trim().substringAfterLast('/').substringAfterLast(':').lowercase() }

    private fun findRecipe(node: Any?, depth: Int): JSONObject? {
        if (depth > MAX_DEPTH) return null
        return when (node) {
            is JSONArray -> (0 until node.length()).firstNotNullOfOrNull { findRecipe(node.opt(it), depth + 1) }
            is JSONObject -> when {
                "recipe" in typesOf(node) -> node
                else -> findRecipe(node.opt("@graph"), depth + 1) ?: findRecipe(node.opt("mainEntity"), depth + 1)
            }
            else -> null
        }
    }

    private fun websiteName(node: Any?, depth: Int): String? {
        if (depth > MAX_DEPTH) return null
        return when (node) {
            is JSONArray -> (0 until node.length()).firstNotNullOfOrNull { websiteName(node.opt(it), depth + 1) }
            is JSONObject ->
                if ("website" in typesOf(node)) cleanInline(str(node, "name")).ifEmpty { null }
                else websiteName(node.opt("@graph"), depth + 1) ?: websiteName(node.opt("mainEntity"), depth + 1)
            else -> null
        }
    }

    private fun publisherName(node: Any?): String? = names(node).firstOrNull()

    // ---- fields ----

    /** Strings from a string, number, object with `name`, or array of those. */
    private fun names(node: Any?): List<String> = when (node) {
        is String -> listOf(cleanInline(node))
        is JSONObject -> listOf(cleanInline(str(node, "name")))
        is JSONArray -> (0 until node.length()).flatMap { names(node.opt(it)) }
        else -> emptyList()
    }.filter { it.isNotEmpty() }

    private fun strings(node: Any?): List<String> = when (node) {
        is String -> listOf(node)
        is Number -> listOf(node.toString())
        is JSONArray -> (0 until node.length()).flatMap { strings(node.opt(it)) }
        else -> emptyList()
    }

    private fun ingredients(recipe: JSONObject): List<String> {
        val raw = recipe.opt("recipeIngredient") ?: recipe.opt("ingredients")
        return strings(raw).flatMap(::htmlLines)
    }

    private fun minutes(node: Any?): Int? = when (node) {
        is String -> IsoDuration.parseMinutes(node)
        is Number -> IsoDuration.validMinutes(node.toLong())
        is JSONObject -> minutes(node.opt("maxValue") ?: node.opt("value") ?: node.opt("minValue"))
        else -> null
    }

    private fun yieldText(node: Any?): String? = when (node) {
        is String -> cleanInline(node).ifEmpty { null }
        is Number -> formatNumber(node.toDouble())
        is JSONArray -> (0 until node.length()).firstNotNullOfOrNull { yieldText(node.opt(it)) }
        else -> null
    }

    private fun leadingNumber(text: String): Double? =
        Regex("""^\s*(\d+(?:[.,]\d+)?)""").find(text)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it > 0 }

    private fun formatNumber(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    private fun nutrition(node: Any?): String? {
        val obj = node as? JSONObject ?: return null
        val parts = obj.keys().asSequence().filter { it != "@type" && it != "@context" }.mapNotNull { key ->
            val value = cleanInline(obj.opt(key)?.takeIf { it is String || it is Number }?.toString().orEmpty())
            if (value.isEmpty()) null else "${nutritionLabel(key)}: $value"
        }.toList()
        return parts.joinToString(", ").ifEmpty { null }
    }

    /** `saturatedFatContent` → "saturated fat". */
    private fun nutritionLabel(key: String): String =
        key.removeSuffix("Content").replace(Regex("([a-z])([A-Z])"), "$1 $2").lowercase()

    // ---- images ----

    private fun imageUrl(node: Any?, pageUrl: String): String? {
        val candidates = mutableListOf<Pair<String, Int?>>()
        collectImages(node, candidates, 0)
        val best = candidates.filter { it.second != null }.maxByOrNull { it.second!! } ?: candidates.firstOrNull()
        return best?.first?.let { resolve(pageUrl, it) }
    }

    private fun collectImages(node: Any?, out: MutableList<Pair<String, Int?>>, depth: Int) {
        if (depth > MAX_DEPTH) return
        when (node) {
            is String -> node.trim().takeIf { it.isNotEmpty() }?.let { out += it to null }
            is JSONArray -> for (i in 0 until node.length()) collectImages(node.opt(i), out, depth + 1)
            is JSONObject -> {
                val url = (node.opt("url") ?: node.opt("contentUrl")) as? String
                if (!url.isNullOrBlank()) {
                    val width = when (val w = node.opt("width")) {
                        is Number -> w.toInt()
                        is String -> w.filter(Char::isDigit).toIntOrNull()
                        is JSONObject -> (w.opt("value") as? Number)?.toInt()
                        else -> null
                    }
                    out += url.trim() to width?.takeIf { it > 0 }
                }
            }
        }
    }

    private fun resolve(base: String, ref: String): String? = try {
        URI(base).resolve(ref.replace(" ", "%20")).toString()
    } catch (_: Exception) {
        null
    }

    private fun hostOf(url: String): String? = try {
        URI(url).host?.removePrefix("www.")
    } catch (_: java.net.URISyntaxException) {
        null
    }

    // ---- instructions ----

    private val sectionTypes = setOf("howtosection")

    private fun steps(node: Any?): List<ImportedLine> = buildList { collectSteps(node, this, 0) }

    private fun collectSteps(node: Any?, out: MutableList<ImportedLine>, depth: Int) {
        if (depth > MAX_DEPTH) return
        when (node) {
            is String -> htmlLines(node).map(::stripStepNumber).filter { it.isNotEmpty() }.forEach { out += ImportedLine(it) }
            is JSONArray -> for (i in 0 until node.length()) collectSteps(node.opt(i), out, depth + 1)
            is JSONObject -> {
                val items = node.opt("itemListElement")
                val isSection = typesOf(node).any { it in sectionTypes }
                if (isSection) {
                    cleanInline(str(node, "name")).takeIf { it.isNotEmpty() }?.let { out += ImportedLine(it, isHeader = true) }
                    collectSteps(items, out, depth + 1)
                } else if (items != null && node.opt("text") == null) {
                    collectSteps(items, out, depth + 1)
                } else {
                    step(node)?.let { out += ImportedLine(it) }
                }
            }
        }
    }

    /** A HowToStep: its `text`, led by its `name` only when that name is not just the start of the text. */
    private fun step(node: JSONObject): String? {
        val text = htmlLines(str(node, "text")).map(::stripStepNumber).filter { it.isNotEmpty() }.joinToString(" ")
        val name = stripStepNumber(cleanInline(str(node, "name")))
        val full = when {
            text.isEmpty() -> name
            name.isEmpty() || text.startsWith(name, ignoreCase = true) || name.startsWith(text, ignoreCase = true) -> text
            else -> "$name: $text"
        }
        return full.ifEmpty { null }
    }

    // ---- text cleanup ----

    private val breakTags = Regex("""<\s*/?\s*(?:br|li|p|div|ul|ol)\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val anyTag = Regex("""<[^>]*>""")
    private val whitespace = Regex("""\s+""")
    private val stepNumber = Regex("""^(?:step\s*\d+\s*[.:)\-]?|\d+\s*[.)])(?!\d)\s*""", RegexOption.IGNORE_CASE)

    /** HTML-ish text as clean lines: breaks at newlines, `<li>`, `<p>`, `<br>`; tags stripped, entities decoded. */
    internal fun htmlLines(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        val withBreaks = breakTags.replace(raw, "\n")
        val text = Parser.unescapeEntities(anyTag.replace(withBreaks, ""), false)
        return text.split('\n', '\r').map { whitespace.replace(it.replace(' ', ' '), " ").trim() }.filter { it.isNotEmpty() }
    }

    internal fun cleanInline(raw: String): String = htmlLines(raw).joinToString(" ")

    internal fun stripStepNumber(line: String): String = stepNumber.replaceFirst(line, "").trim()
}
