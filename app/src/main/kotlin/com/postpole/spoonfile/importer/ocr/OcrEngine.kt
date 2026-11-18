package com.postpole.spoonfile.importer.ocr

import android.net.Uri

/** Reads the text of one page photo. Tests use a fake; the app uses [MlKitOcrEngine]. */
interface OcrEngine {
    /** The recognised lines of the photo at [uri], in reading order. Throws if the photo can't be read. */
    suspend fun recognize(uri: Uri): List<String>
}

/** Reads the raw bytes of a page photo (for "Use page photo as recipe photo"). */
interface PageReader {
    suspend fun readBytes(uri: Uri): ByteArray?
}
