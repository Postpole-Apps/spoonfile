package com.postpole.spoonfile.exporter

import com.postpole.spoonfile.data.RecipeWithDetails
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes recipes as a recipe file another app can import: a ZIP with one gzip-compressed UTF-8 JSON entry per
 * recipe (`*.paprikarecipe`). The counterpart of `PaprikaArchiveReader`.
 */
class PaprikaArchiveWriter(private val zone: ZoneId = ZoneId.systemDefault()) {

    /** Writes every recipe from [recipes]; [photo] supplies the photo bytes for one (null for none). Returns the count. */
    fun write(out: OutputStream, recipes: Sequence<RecipeWithDetails>, photo: (RecipeWithDetails) -> ByteArray?): Int {
        val zip = ZipOutputStream(out)
        val usedNames = HashSet<String>()
        var count = 0
        for (details in recipes) {
            val json = toJson(details, photo(details)).toString().toByteArray(Charsets.UTF_8)
            zip.putNextEntry(ZipEntry(entryName(details.recipe.title, usedNames)))
            // The gzip stream must be finished but must not close the ZIP entry's underlying stream.
            val gz = GZIPOutputStream(object : java.io.FilterOutputStream(zip) {
                override fun write(b: ByteArray, off: Int, len: Int) = zip.write(b, off, len)
                override fun close() = Unit
            })
            gz.write(json)
            gz.finish()
            zip.closeEntry()
            count++
        }
        zip.finish()
        out.flush()
        return count
    }

    internal fun toJson(details: RecipeWithDetails, photo: ByteArray?): JSONObject {
        val r = details.recipe
        val ingredients = details.ingredients.sortedBy { it.position }.joinToString("\n") { it.text }
        // Blank lines separate steps in the file, so a step's own blank lines are folded into single breaks.
        val directions = details.steps.sortedBy { it.position }
            .joinToString("\n\n") { it.text.trim().replace(BLANK_LINES, "\n") }
        val o = JSONObject()
        o.put("uid", r.uid)
        o.put("name", r.title)
        o.put("ingredients", ingredients)
        o.put("directions", directions)
        o.putIfPresent("notes", r.notes)
        o.putIfPresent("nutritional_info", r.nutritionText)
        o.putIfPresent("description", r.description)
        o.putIfPresent("servings", r.servingsText ?: r.servings?.let(::servingsText))
        r.prepMinutes?.let { o.put("prep_time", "$it min") }
        r.cookMinutes?.let { o.put("cook_time", "$it min") }
        r.totalMinutes?.let { o.put("total_time", "$it min") }
        o.put("rating", r.rating)
        o.put("categories", JSONArray(details.categories.sortedBy { it.sortOrder }.map { it.name }))
        o.putIfPresent("source", r.sourceName)
        o.putIfPresent("source_url", r.sourceUrl)
        if (photo != null && photo.isNotEmpty()) o.put("photo_data", Base64.getEncoder().encodeToString(photo))
        o.put("created", CREATED_FORMAT.format(Instant.ofEpochMilli(r.createdAt).atZone(zone)))
        o.put("hash", sha256(o.toString()))
        return o
    }

    private fun JSONObject.putIfPresent(key: String, value: String?) {
        if (!value.isNullOrBlank()) put(key, value)
    }

    private fun servingsText(value: Double): String =
        if (value == Math.floor(value)) value.toLong().toString() else value.toString()

    /** A readable, unique file name inside the archive; never used as a path on import. */
    private fun entryName(title: String, used: MutableSet<String>): String {
        val base = title.replace(UNSAFE, "_").trim().trim('.').take(80).ifEmpty { "recipe" }
        var name = "$base$EXTENSION"
        var n = 2
        while (!used.add(name.lowercase())) name = "$base ${n++}$EXTENSION"
        return name
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    companion object {
        private const val EXTENSION = ".paprikarecipe"
        private val BLANK_LINES = Regex("""\n[ \t]*\n+""")
        private val UNSAFE = Regex("""[\\/:*?"<>|\u0000-\u001f]""")
        private val CREATED_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
