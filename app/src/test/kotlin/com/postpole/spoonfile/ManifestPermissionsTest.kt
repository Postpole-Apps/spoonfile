package com.postpole.spoonfile

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Reads the merged manifest (libraries included): page photos need no camera, storage or media permission. */
@RunWith(RobolectricTestRunner::class)
class ManifestPermissionsTest {
    private val requested: Set<String> by lazy {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().toSet()
    }

    @Test fun noCameraOrStoragePermissions() {
        val banned = listOf(
            "android.permission.CAMERA",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.MANAGE_EXTERNAL_STORAGE",
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
        )
        for (permission in banned) assertFalse("$permission must not be requested", permission in requested)
    }

    @Test fun adIdPermissionStaysOn() {
        assertTrue("com.google.android.gms.permission.AD_ID" in requested)
    }
}
