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
 * Writes all recipes as a recipe file another app can open: a ZIP with one gzip-compressed JSON
 * `.paprikarecipe` entry per recipe (the format [com.postpole.spoonfile.importer.paprika.PaprikaArchiveReader] reads).
 */
class PaprikaArchiveWriter {

    /**
     * Writes [recipes] to [out] and returns how many were written. [photoBytes] returns the stored
     * photo for a recipe's image file name, or null when it is missing.
     */
    fun write(out: OutputStream, recipes: List<RecipeWithDetails>, photoBytes: (String) -> ByteArray?): Int {
        val zip = ZipOutputStream(out)
        val usedNames = HashSet<String>()
        for (details in recipes) {
            val json = recipeJson(details, details.recipe.imageFile?.let(photoBytes))
            var name = entryName(details.recipe.uid)
            var n = 2
            while (!usedNames.add(name)) name = entryName("${details.recipe.uid}-${n++}")
            zip.putNextEntry(ZipEntry(name))
            GZIPOutputStream(NonClosing(zip)).use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
            zip.closeEntry()
        }
        zip.finish()
        out.flush()
        return recipes.size
    }

    internal fun recipeJson(details: RecipeWithDetails, photo: ByteArray?): JSONObject {
        val r = details.recipe
        val o = JSONObject()
        o.put("uid", r.uid)
        o.put("name", r.title)
        o.put("ingredients", details.ingredients.joinToString("\n") { it.text })
        o.put("directions", details.steps.joinToString("\n\n") { it.text })
        r.notes?.let { o.put("notes", it) }
        r.nutritionText?.let { o.put("nutritional_info", it) }
        r.description?.let { o.put("description", it) }
        (r.servingsText ?: r.servings?.let(::formatServings))?.let { o.put("servings", it) }
        r.prepMinutes?.let { o.put("prep_time", "$it min") }
        r.cookMinutes?.let { o.put("cook_time", "$it min") }
        r.totalMinutes?.let { o.put("total_time", "$it min") }
        o.put("rating", r.rating)
        o.put("categories", JSONArray(details.categories.map { it.name }))
        r.sourceName?.let { o.put("source", it) }
        r.sourceUrl?.let { o.put("source_url", it) }
        photo?.let { o.put("photo_data", Base64.getEncoder().encodeToString(it)) }
        o.put("created", CREATED_FORMAT.format(Instant.ofEpochMilli(r.createdAt)))
        // Hash of the recipe content (everything above), as other apps expect a per-recipe digest.
        o.put("hash", sha256(o.toString().toByteArray(Charsets.UTF_8)))
        return o
    }

    private fun formatServings(value: Double): String =
        if (value == Math.floor(value)) value.toLong().toString() else value.toString()

    private fun entryName(uid: String): String {
        val safe = uid.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }.joinToString("").take(80)
        return "${safe.ifEmpty { "recipe" }}.paprikarecipe"
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Lets the gzip stream finish without closing the surrounding ZIP. */
    private class NonClosing(out: OutputStream) : java.io.FilterOutputStream(out) {
        override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
        override fun close() = flush()
    }

    companion object {
        private val CREATED_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

        /** Saved file name for an export; the extension appears only here, never in on-screen text. */
        fun fileName(date: String): String = "spoonfile-recipes-$date.paprikarecipes"
    }
}
