package com.postpole.spoonfile.importer.ocr

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.postpole.spoonfile.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Bundled ML Kit Latin text recognition; the photo never leaves the phone. */
class MlKitOcrEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : OcrEngine {
    private val lock = Any()
    private var client: TextRecognizer? = null

    /** Created on first use, and again after [close] if the engine is used for another scan. */
    private fun recognizer(): TextRecognizer = synchronized(lock) {
        client ?: TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).also { client = it }
    }

    override suspend fun recognize(uri: Uri): List<String> {
        val image = withContext(io) { InputImage.fromFilePath(context, uri) }
        val recognizer = recognizer()
        val text = suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        return orderedLines(text.textBlocks.map { block -> block.boundingBox to block.lines.map { it.text } })
    }

    override fun close() {
        val old = synchronized(lock) { client.also { client = null } }
        old?.close()
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
    @IoDispatcher private val io: CoroutineDispatcher,
) : PagePhotoReader {
    override suspend fun read(uri: Uri): ByteArray? = withContext(io) {
        try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (_: java.io.IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }
}

/** Deletes the `cacheDir/scan/page-*.jpg` files the camera wrote through FileProvider. */
class CacheDirScanPhotoCache @Inject constructor(
    @ApplicationContext private val context: Context,
) : ScanPhotoCache {
    override fun clear() {
        File(context.cacheDir, SCAN_DIR).listFiles()?.forEach { file ->
            if (file.isFile && file.name.startsWith("page-") && file.name.endsWith(".jpg")) file.delete()
        }
    }

    companion object {
        const val SCAN_DIR = "scan"
    }
}
