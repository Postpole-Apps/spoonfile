package com.postpole.spoonfile

import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Guards that the manifest launcher icons resolve to the adaptive "Spoon Cookie" icon. */
@RunWith(RobolectricTestRunner::class)
class LauncherIconTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val iconId: Int
        get() = context.packageManager.getApplicationInfo(context.packageName, 0).icon

    @Test
    fun manifestIconIsTheAdaptiveMipmap() {
        val res = context.resources
        assertEquals("mipmap", res.getResourceTypeName(iconId))
        assertEquals("ic_launcher", res.getResourceEntryName(iconId))
        assertTrue(res.getDrawable(iconId, context.theme) is AdaptiveIconDrawable)
    }

    @Test
    fun adaptiveIconHasForegroundBackgroundAndMonochrome() {
        val icon = context.resources.getDrawable(iconId, context.theme) as AdaptiveIconDrawable
        assertNotNull(icon.background)
        assertNotNull(icon.foreground)
        assertNotNull(icon.monochrome)
    }

    @Test
    fun roundIconResolvesToTheAdaptiveRoundMipmap() {
        val res = context.resources
        val roundId = res.getIdentifier("ic_launcher_round", "mipmap", context.packageName)
        assertTrue(roundId != 0)
        assertTrue(res.getDrawable(roundId, context.theme) is AdaptiveIconDrawable)
    }
}
