package com.postpole.spoonfile.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlin.math.max

/** Recipe photos live as `<uuid>.jpg` files in [dir]; the database stores only the file name. */
open class ImageStore(val dir: File) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(File(context.filesDir, "images"))

    open fun resolve(name: String): File = File(dir, File(name).name)

    open fun delete(name: String): Boolean = resolve(name).delete()

    /** Decodes image bytes, downsamples to at most [MAX_EDGE] px on the long edge, saves as JPEG. */
    open fun save(bytes: ByteArray): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight) }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            ?: throw IllegalArgumentException("Not a decodable image")
        return saveBitmap(decoded)
    }

    /** Decodes the stored photo [name] downsampled so its long edge is about [maxEdge] px; null if missing/unreadable. */
    open fun decode(name: String, maxEdge: Int): Bitmap? {
        val file = resolve(name)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        var edge = max(bounds.outWidth, bounds.outHeight)
        while (edge / 2 >= maxEdge) {
            sample *= 2
            edge /= 2
        }
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    open fun saveBitmap(bitmap: Bitmap): String {
        val scaled = downsample(bitmap)
        dir.mkdirs()
        val name = "${UUID.randomUUID()}.jpg"
        File(dir, name).outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        return name
    }

    companion object {
        const val MAX_EDGE = 1600
        private const val JPEG_QUALITY = 85

        fun sampleSize(width: Int, height: Int): Int {
            var sample = 1
            var longEdge = max(width, height)
            while (longEdge / 2 >= MAX_EDGE) {
                sample *= 2
                longEdge /= 2
            }
            return sample
        }

        fun downsample(bitmap: Bitmap): Bitmap {
            val longEdge = max(bitmap.width, bitmap.height)
            if (longEdge <= MAX_EDGE) return bitmap
            val scale = MAX_EDGE.toFloat() / longEdge
            return Bitmap.createScaledBitmap(
                bitmap,
                max(1, (bitmap.width * scale).toInt()),
                max(1, (bitmap.height * scale).toInt()),
                true,
            )
        }
    }
}
