package com.postpole.spoonfile.importer.ocr

import android.net.Uri

/** Reads the text lines from a page photo. Tests use a fake; the app uses [MlKitOcrEngine]. */
interface OcrEngine {
    /** Lines in reading order (blocks top-to-bottom, then left-to-right). Throws if the image can't be read. */
    suspend fun recognize(uri: Uri): List<String>
}

/** Reads the bytes of a page photo so it can become the recipe photo. Null if it can't be read. */
interface PagePhotoReader {
    suspend fun read(uri: Uri): ByteArray?
}
