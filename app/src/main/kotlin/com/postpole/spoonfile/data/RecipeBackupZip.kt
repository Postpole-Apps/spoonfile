package com.postpole.spoonfile.data

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The backup container: a ZIP holding `backup.json` and one `images/<file>` entry per recipe photo.
 * Entry names are never used as paths directly: only a plain file name under `images/` is accepted.
 */
object RecipeBackupZip {
    const val JSON_ENTRY = "backup.json"
    const val IMAGES_PREFIX = "images/"
    private const val MAX_JSON_BYTES = 256L * 1024 * 1024
    private const val MAX_IMAGE_BYTES = 64L * 1024 * 1024

    /** What [read] found: the raw JSON text and the names of the photo files copied into the temp directory. */
    class Contents(val json: String, val imageNames: Set<String>)

    fun write(out: OutputStream, json: String, images: List<File>) {
        val zip = ZipOutputStream(out)
        zip.putNextEntry(ZipEntry(JSON_ENTRY))
        zip.write(json.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        for (file in images) {
            zip.putNextEntry(ZipEntry(IMAGES_PREFIX + file.name))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
        zip.finish()
        out.flush()
    }

    /**
     * Reads the archive; photos are written into [imagesTempDir] (created here). Throws
     * [InvalidBackupException] when the input isn't a usable backup archive.
     */
    fun read(input: InputStream, imagesTempDir: File): Contents {
        var json: String? = null
        val names = LinkedHashSet<String>()
        imagesTempDir.mkdirs()
        try {
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val name = entry.name.replace('\\', '/')
                    when {
                        name == JSON_ENTRY -> {
                            val bytes = readLimited(zip, MAX_JSON_BYTES)
                                ?: throw InvalidBackupException("backup.json is too large")
                            json = String(bytes, Charsets.UTF_8)
                        }
                        name.startsWith(IMAGES_PREFIX) -> {
                            val base = name.removePrefix(IMAGES_PREFIX)
                            if (!isPlainFileName(base)) continue
                            val target = File(imagesTempDir, base)
                            var copied = 0L
                            target.outputStream().use { out ->
                                val buf = ByteArray(8192)
                                while (true) {
                                    val n = zip.read(buf)
                                    if (n < 0) break
                                    copied += n
                                    if (copied > MAX_IMAGE_BYTES) throw InvalidBackupException("A photo is too large")
                                    out.write(buf, 0, n)
                                }
                            }
                            names += base
                        }
                    }
                }
            }
        } catch (e: ZipException) {
            throw InvalidBackupException("Not a valid backup archive", e)
        } catch (e: IOException) {
            throw InvalidBackupException("Could not read the backup archive", e)
        }
        return Contents(json ?: throw InvalidBackupException("Missing backup.json"), names)
    }

    fun isPlainFileName(name: String): Boolean =
        name.isNotEmpty() && name != "." && name != ".." && '/' !in name && '\\' !in name && '\u0000' !in name

    private fun readLimited(input: InputStream, limit: Long): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > limit) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
