package com.postpole.spoonfile.importer.ocr

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Bundled ML Kit Latin text recognition; the photo never leaves the phone. */
class MlKitOcrEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : OcrEngine {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override suspend fun recognize(uri: Uri): List<String> {
        val image = withContext(Dispatchers.IO) { InputImage.fromFilePath(context, uri) }
        val text = suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        return orderedLines(text.textBlocks.map { block -> block.boundingBox to block.lines.map { it.text } })
    }

    internal companion object {
        /** Blocks top-to-bottom, then left-to-right; a block without a box goes last. */
        fun orderedLines(blocks: List<Pair<Rect?, List<String>>>): List<String> =
            blocks.sortedWith(
                compareBy<Pair<Rect?, List<String>>> { it.first?.top ?: Int.MAX_VALUE }
                    .thenBy { it.first?.left ?: Int.MAX_VALUE },
            ).flatMap { it.second }.map { it.trim() }.filter { it.isNotEmpty() }
    }
}

class ContentResolverPagePhotoReader @Inject constructor(
    @ApplicationContext private val context: Context,
) : PagePhotoReader {
    override suspend fun read(uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (_: java.io.IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }
}
