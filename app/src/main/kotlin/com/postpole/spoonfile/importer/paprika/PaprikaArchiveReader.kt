package com.postpole.spoonfile.importer.paprika

import org.json.JSONException
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

/** The input isn't a ZIP archive. */
class NotAnArchiveException : IOException("Not a ZIP archive")

enum class PaprikaFailure { NOT_GZIP, TOO_LARGE, INVALID, NO_TITLE }

class PaprikaReadSummary(
    /** Readable recipes passed to the callback. */
    val recipes: Int,
    val failures: Map<PaprikaFailure, Int>,
    /** True when reading stopped at a size/count limit (or a damaged spot) before the end of the archive. */
    val truncated: Boolean,
) {
    val failed: Int get() = failures.values.sum()
}

/**
 * Reads a `.paprikarecipes` ZIP: each `*.paprikarecipe` entry is one gzip-compressed UTF-8 JSON recipe.
 * Entries are only ever read as streams; entry names are never used as file paths (no zip-slip) or as titles.
 */
class PaprikaArchiveReader(private val now: () -> Long = System::currentTimeMillis) {

    /**
     * Streams the archive in file order and hands each readable recipe to [onRecipe] (return false to stop early).
     * Set [includePhotos] to decode `photo_data`. Throws [NotAnArchiveException] for a non-ZIP input.
     */
    fun read(
        input: InputStream,
        includePhotos: Boolean = true,
        onRecipe: (PaprikaRecipe) -> Boolean,
    ): PaprikaReadSummary {
        val buffered = if (input.markSupported()) input else BufferedInputStream(input)
        if (!looksLikeZip(buffered)) throw NotAnArchiveException()

        val failures = mutableMapOf<PaprikaFailure, Int>()
        fun fail(reason: PaprikaFailure) {
            failures[reason] = (failures[reason] ?: 0) + 1
        }
        var entries = 0
        var recipes = 0
        var totalBytes = 0L
        var truncated = false

        try {
            ZipInputStream(buffered).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory || !isRecipeEntry(entry.name)) continue
                    if (entries >= MAX_ENTRIES) {
                        truncated = true
                        break
                    }
                    entries++
                    val bytes = try {
                        readEntry(zip, MAX_TOTAL_BYTES - totalBytes)
                    } catch (_: IOException) {
                        fail(PaprikaFailure.NOT_GZIP)
                        continue
                    }
                    when (bytes) {
                        EntryBytes.TooLarge -> fail(PaprikaFailure.TOO_LARGE)
                        EntryBytes.OutOfBudget -> {
                            truncated = true
                            break
                        }
                        is EntryBytes.Data -> {
                            totalBytes += bytes.bytes.size
                            val recipe = try {
                                PaprikaRecipeJson.parse(recipes, String(bytes.bytes, Charsets.UTF_8), includePhotos, now())
                            } catch (_: JSONException) {
                                fail(PaprikaFailure.INVALID)
                                continue
                            }
                            if (recipe == null) {
                                fail(PaprikaFailure.NO_TITLE)
                            } else {
                                recipes++
                                if (!onRecipe(recipe)) break
                            }
                        }
                    }
                }
            }
        } catch (_: IOException) {
            // A damaged archive: keep what was read so far.
            if (entries == 0) throw NotAnArchiveException()
            truncated = true
        } catch (_: IllegalArgumentException) {
            if (entries == 0) throw NotAnArchiveException()
            truncated = true
        }
        return PaprikaReadSummary(recipes, failures, truncated)
    }

    private sealed interface EntryBytes {
        class Data(val bytes: ByteArray) : EntryBytes
        data object TooLarge : EntryBytes
        data object OutOfBudget : EntryBytes
    }

    /** Gunzips the current entry, reading at most [MAX_ENTRY_BYTES] (and at most [budget]) decompressed bytes. */
    private fun readEntry(zip: ZipInputStream, budget: Long): EntryBytes {
        val limit = minOf(MAX_ENTRY_BYTES.toLong(), budget)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        // Closing the gzip stream must not close the archive, so it reads through a wrapper that ignores close().
        val entryStream = object : java.io.FilterInputStream(zip) {
            override fun close() = Unit
        }
        GZIPInputStream(entryStream).use { gz ->
            while (true) {
                val n = gz.read(buf)
                if (n < 0) break
                if (out.size() + n > limit) {
                    return if (budget < MAX_ENTRY_BYTES) EntryBytes.OutOfBudget else EntryBytes.TooLarge
                }
                out.write(buf, 0, n)
            }
        }
        return EntryBytes.Data(out.toByteArray())
    }

    internal companion object {
        const val MAX_ENTRIES = 20_000
        const val MAX_ENTRY_BYTES = 20 * 1024 * 1024
        const val MAX_TOTAL_BYTES = 1024L * 1024 * 1024
        private const val EXTENSION = ".paprikarecipe"

        /** Any `.paprikarecipe` file at any depth, skipping `__MACOSX/` debris and dot-files. */
        fun isRecipeEntry(path: String): Boolean {
            val normalized = path.replace('\\', '/')
            if (normalized.contains("__MACOSX/")) return false
            val base = normalized.substringAfterLast('/')
            return !base.startsWith(".") && base.endsWith(EXTENSION, ignoreCase = true)
        }

        private fun looksLikeZip(input: InputStream): Boolean {
            input.mark(4)
            val head = ByteArray(4)
            var read = 0
            while (read < 4) {
                val n = input.read(head, read, 4 - read)
                if (n < 0) break
                read += n
            }
            input.reset()
            return read == 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() &&
                ((head[2].toInt() == 3 && head[3].toInt() == 4) || (head[2].toInt() == 5 && head[3].toInt() == 6))
        }
    }
}
