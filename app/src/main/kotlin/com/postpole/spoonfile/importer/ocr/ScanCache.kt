package com.postpole.spoonfile.importer.ocr

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/** The app's own cache of camera captures. It never touches photos the user picked. */
interface ScanCache {
    /** Deletes every cached scan JPEG. Safe to call any time, and more than once. */
    fun clear()
}

/** Camera captures live in `cacheDir/scan/page-<uuid>.jpg`. */
class FileScanCache @Inject constructor(
    @ApplicationContext private val context: Context,
) : ScanCache {
    override fun clear() {
        File(context.cacheDir, DIR).listFiles()?.forEach { it.delete() }
    }

    companion object {
        const val DIR = "scan"
    }
}
