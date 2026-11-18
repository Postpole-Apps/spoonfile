package com.postpole.spoonfile.importer.ocr

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The page scan uses the camera app and the system Photo Picker, so the merged manifest asks for neither. */
@RunWith(RobolectricTestRunner::class)
class NoScanPermissionsTest {
    private val forbidden = listOf(
        "android.permission.CAMERA",
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.MANAGE_EXTERNAL_STORAGE",
        "android.permission.READ_MEDIA_IMAGES",
        "android.permission.READ_MEDIA_VIDEO",
        "android.permission.READ_MEDIA_AUDIO",
        "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
    )

    @Test
    fun mergedManifestHasNoCameraOrStoragePermissions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val requested = info.requestedPermissions?.toList().orEmpty()
        assertTrue("expected the app's own permissions to be listed", "android.permission.INTERNET" in requested)
        assertEquals(emptyList<String>(), requested.filter { it in forbidden })
    }
}
