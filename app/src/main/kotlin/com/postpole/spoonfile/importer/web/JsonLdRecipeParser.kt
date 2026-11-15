package com.postpole.spoonfile.importer.web

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URI
import java.net.URISyntaxException

/**
 * Reads a schema.org `Recipe` out of a page's JSON-LD blocks. Pure parsing: no network, no saving.
 * Written against the public schema.org vocabulary; no third-party code or recipe text is included.
 */
object JsonLdRecipeParser {
    private const val MAX_DEPTH = 12
    private const val MAX_CATEGORIES = 20
    private const val MAX_CATEGORY_CHARS = 40

    private val whitespace = Regex("\\s+")
    private val blockBreaks = Regex("(?i)<\\s*/?\\s*(li|p|br|div|ul|ol)\\b[^>]*>")
    private val stepPrefix = Regex("^(?:step\\s*\\d+\\s*[.:)\\-]?|\\d{1,2}\\s*[.):](?=\\s|$))\\s*", RegexOption.IGNORE_CASE)
    private val leadingNumber = Regex("^\\s*(\\d+(?:[.,]\\d+)?)")

    /** The first recipe found on [html], or null when the page has none (or none with ingredients or steps). */
    fun parse(html: String, pageUrl: String): ImportedRecipe? {
        val doc = Jsoup.parse(html, pageUrl)
        val nodes = mutableListOf<JSONObject>()
        for (script in doc.select("script[type]")) {
            if (!script.attr("type").trim().lowercase().startsWith("application/ld+json")) continue
            val root = readJson(script.data()) ?: continue
            collect(root, nodes, 0)
        }
        val recipe = nodes.firstOrNull { it.isType("recipe") } ?: return null

        val ingredients = ingredients(recipe)
        val steps = mutableListOf<ImportedLine>().also { instructions(recipe.optAny("recipeInstructions", "RecipeInstructions"), it, 0) }
        if (ingredients.isEmpty() && steps.isEmpty()) return null

        val host = SharedTextUrl.host(pageUrl)
        val title = cleanLine(recipe.opt("name")).ifEmpty {
            doc.select("meta[property=og:title]").attr("content").let(::cleanLine).ifEmpty { cleanLine(doc.title()) }
        }.ifEmpty { host.orEmpty() }

        val yieldText = firstText(recipe.opt("recipeYield"))
        val servings = yieldText?.let { leadingNumber.find(it)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull() }
            ?.takeIf { it > 0 }

        return ImportedRecipe(
            title = title,
            description = cleanLine(recipe.opt("description")).ifEmpty { null },
            ingredients = ingredients,
            steps = steps,
            prepMinutes = minutes(recipe.opt("prepTime")),
            cookMinutes = minutes(recipe.opt("cookTime")),
            totalMinutes = minutes(recipe.opt("totalTime")),
            servings = servings,
            servingsText = yieldText,
            imageUrl = image(recipe.opt("image"), pageUrl),
            suggestedCategories = categories(recipe),
            nutritionText = nutrition(recipe.opt("nutrition")),
            sourceName = sourceName(doc.select("meta[property=og:site_name]").attr("content"), nodes, recipe, host),
            sourceUrl = pageUrl,
        )
    }

    // ---- JSON-LD discovery -------------------------------------------------------------------

    private fun readJson(raw: String): Any? {
        val text = raw.trim().removePrefix("<!--").removeSuffix("-->").trim()
        if (text.isEmpty()) return null
        return try {
            JSONObject(text)
        } catch (_: JSONException) {
            try {
                JSONArray(text)
            } catch (_: JSONException) {
                null
            }
        }
    }

    /** Flattens top-level objects/arrays, `@graph` and `mainEntity` into [out], parents before children. */
    private fun collect(node: Any?, out: MutableList<JSONObject>, depth: Int) {
        if (depth > MAX_DEPTH) return
        when (node) {
            is JSONArray -> for (i in 0 until node.length()) collect(node.opt(i), out, depth + 1)
            is JSONObject -> {
                out += node
                collect(node.opt("@graph"), out, depth + 1)
                collect(node.opt("mainEntity"), out, depth + 1)
            }
        }
    }

    private fun JSONObject.isType(expected: String): Boolean {
        val type = opt("@type")
        val names = when (type) {
            is String -> listOf(type)
            is JSONArray -> (0 until type.length()).mapNotNull { type.opt(it) as? String }
            else -> emptyList()
        }
        return names.any {
            val n = it.trim().lowercase()
            n == expected || n.endsWith("/$expected")
        }
    }

    private fun JSONObject.optAny(vararg keys: String): Any? = keys.firstNotNullOfOrNull { opt(it).takeUnless { v -> v == null || v == JSONObject.NULL } }

    // ---- Text cleanup -------------------------------------------------------------------------

    /** Decodes entities, strips tags and collapses whitespace. */
    internal fun cleanLine(value: Any?): String {
        val raw = when (value) {
            is String -> value
            is Number -> value.toString()
            else -> return ""
        }
        if (raw.isBlank()) return ""
        val text = if (raw.any { it == '<' || it == '&' }) Jsoup.parse(raw).text() else raw
        return text.replace(' ', ' ').replace(whitespace, " ").trim()
    }

    /** Splits on newlines and block-level tags, cleaning each piece and dropping empties. */
    private fun cleanLines(raw: String): List<String> =
        raw.replace(blockBreaks, "\n").lines().map(::cleanLine).filter { it.isNotEmpty() }

    private fun firstText(value: Any?): String? = when (value) {
        is JSONArray -> (0 until value.length()).firstNotNullOfOrNull { firstText(value.opt(it)) }
        is JSONObject -> null
        else -> cleanLine(value).ifEmpty { null }
    }

    // ---- Fields -------------------------------------------------------------------------------

    private fun ingredients(recipe: JSONObject): List<ImportedLine> {
        val raw = recipe.optAny("recipeIngredient", "ingredients")
        val lines = mutableListOf<String>()
        fun add(node: Any?, depth: Int) {
            if (depth > MAX_DEPTH) return
            when (node) {
                is String -> lines += cleanLines(node)
                is JSONArray -> for (i in 0 until node.length()) add(node.opt(i), depth + 1)
                is JSONObject -> add(node.optAny("text", "name"), depth + 1)
            }
        }
        add(raw, 0)
        return lines.map { ImportedLine(it) }
    }

    private fun instructions(node: Any?, out: MutableList<ImportedLine>, depth: Int) {
        if (depth > MAX_DEPTH) return
        when (node) {
            is String -> cleanLines(node).forEach { addStep(it, out) }
            is JSONArray -> for (i in 0 until node.length()) instructions(node.opt(i), out, depth + 1)
            is JSONObject -> {
                val name = cleanLine(node.opt("name"))
                val text = cleanLines(node.optString("text", "")).joinToString(" ")
                val children = node.opt("itemListElement")
                when {
                    node.isType("howtosection") || (text.isEmpty() && children != null) -> {
                        if (name.isNotEmpty()) out += ImportedLine(name, isHeader = true)
                        instructions(children, out, depth + 1)
                    }
                    else -> addStep(stepText(name, text), out)
                }
            }
        }
    }

    private fun stepText(name: String, text: String): String = when {
        name.isEmpty() -> text
        text.isEmpty() -> name
        text.startsWith(name, ignoreCase = true) || name.startsWith(text, ignoreCase = true) -> text
        name.last() in ".:!?" -> "$name $text"
        else -> "$name: $text"
    }

    private fun addStep(text: String, out: MutableList<ImportedLine>) {
        val stripped = text.replace(stepPrefix, "").trim()
        if (stripped.isNotEmpty()) out += ImportedLine(stripped)
    }

    private fun minutes(value: Any?): Int? = when (value) {
        is String -> IsoDuration.toMinutes(value)
        is Number -> IsoDuration.validate(value.toLong())
        is JSONObject -> {
            val max = value.opt("maxValue") ?: value.opt("value")
            when (max) {
                is Number -> IsoDuration.validate(max.toLong())
                is String -> max.trim().toDoubleOrNull()?.let { IsoDuration.validate(it.toLong()) }
                else -> null
            }
        }
        is JSONArray -> (0 until value.length()).firstNotNullOfOrNull { minutes(value.opt(it)) }
        else -> null
    }

    private fun image(value: Any?, pageUrl: String): String? {
        val candidates = mutableListOf<Pair<String, Int?>>()
        fun add(node: Any?, depth: Int) {
            if (depth > 4) return
            when (node) {
                is String -> if (node.isNotBlank()) candidates += node.trim() to null
                is JSONArray -> for (i in 0 until node.length()) add(node.opt(i), depth + 1)
                is JSONObject -> {
                    val url = (node.opt("url") ?: node.opt("contentUrl")) as? String
                    val width = when (val w = node.opt("width")) {
                        is Number -> w.toInt()
                        is String -> w.filter(Char::isDigit).toIntOrNull()
                        is JSONObject -> (w.opt("value") as? Number)?.toInt()
                        else -> null
                    }
                    if (!url.isNullOrBlank()) candidates += url.trim() to width
                }
            }
        }
        add(value, 0)
        val best = candidates.filter { it.second != null }.maxByOrNull { it.second!! } ?: candidates.firstOrNull()
        return best?.first?.let { resolve(it, pageUrl) }
    }

    private fun resolve(url: String, pageUrl: String): String? = try {
        URI(pageUrl).resolve(URI(url.replace(" ", "%20"))).toString()
            .takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
    } catch (_: URISyntaxException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun categories(recipe: JSONObject): List<String> {
        val out = linkedMapOf<String, String>()
        fun add(node: Any?) {
            when (node) {
                is String -> cleanLine(node).split(',').map { it.trim() }.forEach {
                    if (it.isNotEmpty() && it.length <= MAX_CATEGORY_CHARS) out.putIfAbsent(it.lowercase(), it)
                }
                is JSONArray -> for (i in 0 until node.length()) add(node.opt(i))
            }
        }
        add(recipe.opt("recipeCategory"))
        add(recipe.opt("recipeCuisine"))
        return out.values.take(MAX_CATEGORIES)
    }

    private fun nutrition(value: Any?): String? {
        val obj = value as? JSONObject ?: return null
        val parts = obj.keys().asSequence()
            .filter { !it.startsWith("@") }
            .mapNotNull { key ->
                val text = cleanLine(obj.opt(key))
                if (text.isEmpty()) null else "${key.removeSuffix("Content")}: $text"
            }
            .toList()
        return parts.joinToString(", ").ifEmpty { null }
    }

    private fun nameOf(value: Any?): String? = when (value) {
        is String -> cleanLine(value).ifEmpty { null }
        is JSONObject -> cleanLine(value.opt("name")).ifEmpty { null }
        is JSONArray -> (0 until value.length()).mapNotNull { nameOf(value.opt(it)) }.distinct().joinToString(", ").ifEmpty { null }
        else -> null
    }

    /** og:site_name, else WebSite.name, else publisher.name, else the host (with the author appended when only the host is known). */
    private fun sourceName(ogSiteName: String, nodes: List<JSONObject>, recipe: JSONObject, host: String?): String? {
        val site = cleanLine(ogSiteName).ifEmpty { null }
            ?: nodes.firstOrNull { it.isType("website") }?.let { nameOf(it.opt("name")) }
            ?: nameOf(recipe.opt("publisher"))
        if (site != null) return site
        val author = nameOf(recipe.opt("author"))
        return when {
            host == null -> author
            author == null -> host
            else -> "$host ($author)"
        }
    }
}
