package com.postpole.spoonfile.importer.paprika

import com.postpole.spoonfile.importer.web.ImportedLine
import com.postpole.spoonfile.importer.web.ImportedRecipe
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Base64

/** One recipe read from a recipe-file entry. [index] is its position among the readable entries, in file order. */
class PaprikaRecipe(
    val index: Int,
    val uid: String?,
    val recipe: ImportedRecipe,
    val notes: String?,
    val rating: Int,
    val categories: List<String>,
    val createdAt: Long,
    /** Decoded photo bytes; only filled when the reader was asked for photos. */
    val photo: ByteArray?,
    val hasPhoto: Boolean,
)

/** Maps the JSON object of one `.paprikarecipe` entry. Unknown keys are ignored. */
internal object PaprikaRecipeJson {
    private val createdFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val numbering = Regex("""^\d+\.\s+""")
    private val blankLines = Regex("""\n[ \t]*\n""")
    private val leadingNumber = Regex("""^\s*(\d+(?:[.,]\d+)?)""")
    private const val MAX_RATING = 5
    private val BOM = 0xFEFF.toChar()

    /** Null when the JSON has no usable `name`. Throws [JSONException] when [json] isn't a JSON object. */
    fun parse(index: Int, json: String, includePhoto: Boolean, now: Long): PaprikaRecipe? {
        val o = JSONObject(json.removePrefix(BOM.toString()))
        val title = o.text("name") ?: return null

        val noteParts = mutableListOf<String>()
        o.text("notes")?.let(noteParts::add)
        o.text("difficulty")?.let { noteParts += "Difficulty: $it" }
        val prep = timeOf(o, "prep_time", "Prep time", noteParts)
        val cook = timeOf(o, "cook_time", "Cook time", noteParts)
        val total = timeOf(o, "total_time", "Total time", noteParts)

        val servingsText = o.text("servings")
        val servings = servingsText?.let { leadingNumber.find(it)?.groupValues?.get(1) }
            ?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it > 0 }

        val photoText = o.text("photo_data")
        val photo = if (includePhoto) photoText?.let(::decodePhoto) else null

        val recipe = ImportedRecipe(
            title = title,
            description = o.text("description"),
            sourceName = o.text("source"),
            sourceUrl = o.text("source_url").orEmpty(),
            ingredients = o.rawText("ingredients").orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }
                .map { ImportedLine(it) },
            steps = splitDirections(o.rawText("directions").orEmpty()).map { ImportedLine(it) },
            prepMinutes = prep,
            cookMinutes = cook,
            totalMinutes = total,
            servingsText = servingsText,
            servings = servings,
            nutritionText = o.text("nutritional_info"),
        )
        return PaprikaRecipe(
            index = index,
            uid = o.text("uid"),
            recipe = recipe,
            notes = noteParts.joinToString("\n\n").ifEmpty { null },
            rating = o.optInt("rating", 0).coerceIn(0, MAX_RATING),
            categories = categories(o.optJSONArray("categories")),
            createdAt = parseCreated(o.text("created")) ?: now,
            photo = photo,
            hasPhoto = photoText != null,
        )
    }

    /** Steps split on blank lines; if there are none, on single line breaks. A leading "1. " is dropped. */
    fun splitDirections(text: String): List<String> {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (normalized.isEmpty()) return emptyList()
        val parts = if (blankLines.containsMatchIn(normalized)) normalized.split(blankLines) else normalized.split('\n')
        return parts.map { it.trim().replaceFirst(numbering, "").trim() }.filter { it.isNotEmpty() }
    }

    fun parseCreated(text: String?): Long? = try {
        text?.let { LocalDateTime.parse(it, createdFormat).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
    } catch (_: DateTimeParseException) {
        null
    }

    private fun timeOf(o: JSONObject, key: String, label: String, notes: MutableList<String>): Int? {
        val text = o.text(key) ?: return null
        val minutes = PaprikaTime.parseMinutes(text)
        if (minutes == null) notes += "$label: $text"
        return minutes
    }

    private fun categories(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            if (array.isNull(i)) null else (array.opt(i) as? String)?.trim()?.takeIf { it.isNotEmpty() }
        }.distinct()
    }

    private fun decodePhoto(base64: String): ByteArray? = try {
        Base64.getMimeDecoder().decode(base64).takeIf { it.isNotEmpty() }
    } catch (_: IllegalArgumentException) {
        null
    }

    /** String value, or null when absent, JSON null or not a string. Not trimmed. */
    private fun JSONObject.rawText(key: String): String? = if (isNull(key)) null else opt(key) as? String

    /** Trimmed string value, or null when absent, blank or not a string. */
    private fun JSONObject.text(key: String): String? = rawText(key)?.trim()?.takeIf { it.isNotEmpty() }
}
