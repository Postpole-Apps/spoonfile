package com.postpole.spoonfile.importer.paprika

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Builds recipe-file archives in memory (ZipOutputStream + GZIPOutputStream) for the reader tests. */
object PaprikaFixtures {

    fun gzip(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }

    fun json(vararg pairs: Pair<String, Any?>): String {
        val o = JSONObject()
        pairs.forEach { (k, v) ->
            o.put(k, if (v == null) JSONObject.NULL else if (v is List<*>) JSONArray(v) else v)
        }
        return o.toString()
    }

    fun recipeJson(name: String?, uid: String? = null, vararg extra: Pair<String, Any?>): String {
        val pairs = mutableListOf<Pair<String, Any?>>()
        if (name != null) pairs += "name" to name
        if (uid != null) pairs += "uid" to uid
        pairs += extra
        return json(*pairs.toTypedArray())
    }

    /** One zip entry: [bytes] are written as-is. */
    class Entry(val name: String, val bytes: ByteArray)

    fun recipe(path: String, json: String) = Entry(path, gzip(json.toByteArray(Charsets.UTF_8)))

    fun raw(path: String, bytes: ByteArray) = Entry(path, bytes)

    fun zip(vararg entries: Entry): ByteArray = zip(entries.toList())

    fun zip(entries: List<Entry>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            entries.forEach {
                z.putNextEntry(ZipEntry(it.name))
                z.write(it.bytes)
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    fun flat(): ByteArray = zip(
        recipe("Tomato Soup.paprikarecipe", recipeJson("Tomato Soup", "u1", "ingredients" to "1 onion\n\n2 tomatoes\n")),
        recipe("Pancakes.paprikarecipe", recipeJson("Pancakes", "u2")),
    )

    fun nestedWithDebris(): ByteArray = zip(
        recipe("export/Salad.paprikarecipe", recipeJson("Salad", "n1")),
        raw("__MACOSX/export/._Salad.paprikarecipe", byteArrayOf(0, 5, 22, 7)),
        raw("export/.DS_Store", byteArrayOf(0, 0, 0, 1)),
        recipe("export/.hidden.paprikarecipe", recipeJson("Hidden", "n2")),
        raw("export/readme.txt", "hello".toByteArray()),
    )

    /** Non-ASCII and decomposed (NFD) entry names; the title must come from the JSON `name`. */
    fun nonAsciiNames(): ByteArray = zip(
        recipe("Crème brûlée.paprikarecipe", recipeJson("Crème brûlée", "a1")),
        recipe("Crème.paprikarecipe", recipeJson("Crème", "a2")),
        recipe("カレー.paprikarecipe", recipeJson("カレー", "a3")),
    )

    fun badBase64(): ByteArray = zip(
        recipe("x.paprikarecipe", recipeJson("Photo Fail", "b1", "photo_data" to "@@not base64@@")),
    )

    fun goodPhoto(bytes: ByteArray = byteArrayOf(1, 2, 3, 4)): ByteArray = zip(
        recipe("x.paprikarecipe", recipeJson("Photo", "b2", "photo_data" to Base64.getEncoder().encodeToString(bytes))),
    )

    fun nonGzipEntry(): ByteArray = zip(
        raw("bad.paprikarecipe", "plain text, not gzip".toByteArray()),
        recipe("ok.paprikarecipe", recipeJson("Fine", "g1")),
    )

    /** An entry that decompresses to more than the 20 MB per-entry limit. */
    fun oversizedEntry(): ByteArray {
        val big = ByteArray(PaprikaArchiveReader.MAX_ENTRY_BYTES + 1024) { 'a'.code.toByte() }
        return zip(
            raw("big.paprikarecipe", gzip(big)),
            recipe("ok.paprikarecipe", recipeJson("Fine", "o1")),
        )
    }

    fun duplicateUid(): ByteArray = zip(
        recipe("a.paprikarecipe", recipeJson("First", "dup")),
        recipe("b.paprikarecipe", recipeJson("Second", "dup")),
    )
}
