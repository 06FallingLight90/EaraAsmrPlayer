package com.asmr.player.ui.common

import android.content.res.Configuration
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass

/**
 * 设备形态断点判断统一入口（P1-3）。
 *
 * 三族判断在 600-840dp 中间档语义不等价，禁止互换使用：
 * - 宽度档：[WindowWidthSizeClass]（Compact <600dp / Medium 600-840dp / Expanded >840dp）
 * - 方向：[Configuration.orientation]（横竖屏，与宽度档独立）
 * - 最小宽度：[Configuration.smallestScreenWidthDp]（isPhone 判断，仅 MainContainer 使用，留其拆分后收敛）
 */
val WindowWidthSizeClass.isCompactWidth: Boolean
    get() = this == WindowWidthSizeClass.Compact

fun isLandscapeOrientation(orientation: Int): Boolean =
    orientation == Configuration.ORIENTATION_LANDSCAPE

val Configuration.isLandscape: Boolean
    get() = isLandscapeOrientation(orientation)
