package com.postpole.spoonfile.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class CookColorsTest {
    @Test
    fun cookModeBackgroundInDarkIsPureBlack() {
        assertEquals(Color(0xFF000000), CookBackgroundDark)
    }
}
