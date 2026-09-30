package com.asmr.player.ui.common

import android.content.res.Configuration
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EaraWindowSizeTest {
    @Test
    fun isCompactWidth_trueOnlyForCompactWidth() {
        assertTrue(WindowWidthSizeClass.Compact.isCompactWidth)
        // 600-840dp 中间档（Medium）与 Expanded 均不算紧凑——原语义为 == Compact
        assertFalse(WindowWidthSizeClass.Medium.isCompactWidth)
        assertFalse(WindowWidthSizeClass.Expanded.isCompactWidth)
    }

    @Test
    fun isLandscapeOrientation_trueOnlyForLandscape() {
        assertTrue(isLandscapeOrientation(Configuration.ORIENTATION_LANDSCAPE))
        // PORTRAIT 与 UNDEFINED 均视为非横屏——原语义为 == ORIENTATION_LANDSCAPE
        assertFalse(isLandscapeOrientation(Configuration.ORIENTATION_PORTRAIT))
        assertFalse(isLandscapeOrientation(Configuration.ORIENTATION_UNDEFINED))
    }
}
