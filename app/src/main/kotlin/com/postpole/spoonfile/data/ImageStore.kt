package com.postpole.spoonfile.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.util.UUID
import kotlin.math.max

/** Recipe photos live as files in filesDir/images/<uuid>.jpg; the database stores only the file name. */
interface ImageStore {
    /** Downsamples (long edge <= [MAX_EDGE_PX]) and saves as JPEG. Returns the relative file name, or null if undecodable. */
    suspend fun save(bytes: ByteArray): String?

    fun delete(fileName: String?)

    fun resolve(fileName: String): File

    companion object {
        const val MAX_EDGE_PX = 1600
        const val JPEG_QUALITY = 88

        /** Power-of-two sample size that keeps the decoded long edge >= [maxEdge] when possible. */
        fun sampleSize(width: Int, height: Int, maxEdge: Int = MAX_EDGE_PX): Int {
            var sample = 1
            var long = max(width, height)
            while (long / 2 >= maxEdge) {
                long /= 2
                sample *= 2
            }
            return sample
        }
    }
}

class FileImageStore(context: Context) : ImageStore {
    private val dir = File(context.filesDir, "images")

    override suspend fun save(bytes: ByteArray): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = ImageStore.sampleSize(bounds.outWidth, bounds.outHeight)
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        val longEdge = max(decoded.width, decoded.height)
        val scaled = if (longEdge > ImageStore.MAX_EDGE_PX) {
            val f = ImageStore.MAX_EDGE_PX.toFloat() / longEdge
            Bitmap.createScaledBitmap(
                decoded,
                max(1, (decoded.width * f).toInt()),
                max(1, (decoded.height * f).toInt()),
                true,
            )
        } else {
            decoded
        }
        dir.mkdirs()
        val name = "${UUID.randomUUID()}.jpg"
        File(dir, name).outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, ImageStore.JPEG_QUALITY, it) }
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        return name
    }

    override fun delete(fileName: String?) {
        if (fileName.isNullOrBlank()) return
        // Only ever touch files directly inside the images dir.
        File(dir, File(fileName).name).delete()
    }

    override fun resolve(fileName: String): File = File(dir, File(fileName).name)
}
