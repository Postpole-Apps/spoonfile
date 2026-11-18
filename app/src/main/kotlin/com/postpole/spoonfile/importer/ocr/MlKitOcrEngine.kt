package com.postpole.spoonfile.importer.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** On-device ML Kit text recognition (bundled Latin model). Nothing leaves the phone. */
@Singleton
class MlKitOcrEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : OcrEngine {
    override suspend fun recognize(uri: Uri): List<String> {
        val image = withContext(Dispatchers.IO) { InputImage.fromFilePath(context, uri) }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val text = suspendCancellableCoroutine<Text> { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
            return orderedLines(text)
        } finally {
            recognizer.close()
        }
    }

    internal companion object {
        /** Blocks top-to-bottom then left-to-right; lines keep ML Kit's order inside a block. */
        fun orderedLines(text: Text): List<String> =
            text.textBlocks
                .sortedWith(compareBy({ it.boundingBox?.top ?: Int.MAX_VALUE }, { it.boundingBox?.left ?: Int.MAX_VALUE }))
                .flatMap { block -> block.lines.map { it.text.trim() } }
                .filter { it.isNotEmpty() }
    }
}

@Singleton
class ContentResolverPageReader @Inject constructor(
    @ApplicationContext private val context: Context,
) : PageReader {
    override suspend fun readBytes(uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (_: java.io.IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }
}
