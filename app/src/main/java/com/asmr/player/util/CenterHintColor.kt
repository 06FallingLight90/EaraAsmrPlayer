package com.asmr.player.util

import androidx.core.graphics.ColorUtils
import android.graphics.Bitmap
import kotlin.math.exp
import kotlin.math.pow

/** R3-B2 环2 消解自 ui/common/cover/PaletteColorUtils.kt 迁入（ui.theme 与 ui.common.cover 共用的纯颜色计算）。 */
internal fun computeCenterWeightedHintColorInt(bitmap: Bitmap, centerRegionRatio: Float): Int? {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 1 || height <= 1) return null

    val ratio = centerRegionRatio.coerceIn(0.1f, 1f)
    val regionW = (width * ratio).toInt().coerceAtLeast(1)
    val regionH = (height * ratio).toInt().coerceAtLeast(1)
    val left = ((width - regionW) / 2).coerceAtLeast(0)
    val top = ((height - regionH) / 2).coerceAtLeast(0)

    val pixels = IntArray(regionW * regionH)
    runCatching { bitmap.getPixels(pixels, 0, regionW, left, top, regionW, regionH) }.getOrNull() ?: return null

    val cx = (regionW - 1) / 2f
    val cy = (regionH - 1) / 2f
    val sigma = 0.55f
    val inv2Sigma2 = 1f / (2f * sigma * sigma)

    var sumW = 0f
    var sumR = 0f
    var sumG = 0f
    var sumB = 0f
    val tmpHsl = FloatArray(3)

    val step = if (regionW * regionH <= 24_000) 1 else 2
    for (y in 0 until regionH step step) {
        val yn = (y - cy) / (cy.coerceAtLeast(1f))
        val row = y * regionW
        for (x in 0 until regionW step step) {
            val argb = pixels[row + x]
            val a = (argb ushr 24) and 0xFF
            if (a < 32) continue
            ColorUtils.colorToHSL(argb, tmpHsl)
            val s = tmpHsl[1]
            val l = tmpHsl[2]
            if (l < 0.03f || l > 0.93f) continue
            if (l > 0.78f && s < 0.10f) continue

            val xn = (x - cx) / (cx.coerceAtLeast(1f))
            val w = exp(-(xn * xn + yn * yn) * inv2Sigma2)

            val r = ((argb ushr 16) and 0xFF) / 255f
            val g = ((argb ushr 8) and 0xFF) / 255f
            val b = (argb and 0xFF) / 255f

            val lr = srgbToLinear(r)
            val lg = srgbToLinear(g)
            val lb = srgbToLinear(b)

            sumW += w
            sumR += w * lr
            sumG += w * lg
            sumB += w * lb
        }
    }

    if (sumW <= 0f) return null
    val r = linearToSrgb(sumR / sumW).coerceIn(0f, 1f)
    val g = linearToSrgb(sumG / sumW).coerceIn(0f, 1f)
    val b = linearToSrgb(sumB / sumW).coerceIn(0f, 1f)

    val ri = (r * 255f + 0.5f).toInt().coerceIn(0, 255)
    val gi = (g * 255f + 0.5f).toInt().coerceIn(0, 255)
    val bi = (b * 255f + 0.5f).toInt().coerceIn(0, 255)

    return (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
}

private fun srgbToLinear(c: Float): Float {
    return if (c <= 0.04045f) c / 12.92f else (((c + 0.055f) / 1.055f).toDouble().pow(2.4)).toFloat()
}

private fun linearToSrgb(c: Float): Float {
    return if (c <= 0.0031308f) c * 12.92f else ((1.055 * c.toDouble().pow(1.0 / 2.4) - 0.055)).toFloat()
}
