package com.asmr.player.ui.theme

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.QuantizerCelebi
import com.google.android.material.color.utilities.Score
import com.asmr.player.util.computeCenterWeightedHintColorInt
import kotlin.math.sqrt

private const val DEFAULT_MAX_COLORS = 24
private const val MIN_VISIBLE_ALPHA = 32
private const val MONOCHROME_CHROMA_THRESHOLD = 5.0

internal fun monetSeedColorFromBitmap(
    bitmap: Bitmap,
    fallbackColor: Color,
    centerRegionRatio: Float
): Color {
    val pixels = sampledPixelsForMonet(bitmap)
    if (pixels.isEmpty()) return fallbackColor

    val quantized = runCatching {
        QuantizerCelebi.quantize(pixels, DEFAULT_MAX_COLORS)
    }.getOrNull().orEmpty()

    if (quantized.isEmpty()) return fallbackColor

    val hintColorInt = computeCenterWeightedHintColorInt(bitmap, centerRegionRatio)
    val ranked = Score.score(quantized, 4, fallbackColor.toArgb(), true)
    val best = ranked
        .asSequence()
        .mapNotNull { candidate ->
            val hct = Hct.fromInt(candidate)
            if (isColorUnsuitableForTheme(hct)) return@mapNotNull null
            candidate to candidateScore(
                candidate = candidate,
                population = quantized[candidate] ?: 0,
                maxPopulation = quantized.values.maxOrNull()?.coerceAtLeast(1) ?: 1,
                hintColorInt = hintColorInt
            )
        }
        .maxByOrNull { it.second }
        ?.first

    return Color(best ?: fallbackColor.toArgb())
}

private fun sampledPixelsForMonet(bitmap: Bitmap): IntArray {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 1 || height <= 1) return IntArray(0)

    val allPixels = IntArray(width * height)
    runCatching { bitmap.getPixels(allPixels, 0, width, 0, 0, width, height) }.getOrNull() ?: return IntArray(0)

    val step = when {
        width * height <= 24_000 -> 1
        width * height <= 96_000 -> 2
        else -> 3
    }

    val sampled = ArrayList<Int>(allPixels.size / (step * step).coerceAtLeast(1))
    var y = 0
    while (y < height) {
        val row = y * width
        var x = 0
        while (x < width) {
            val argb = allPixels[row + x]
            val alpha = (argb ushr 24) and 0xFF
            if (alpha >= MIN_VISIBLE_ALPHA) {
                sampled += argb or (0xFF shl 24)
            }
            x += step
        }
        y += step
    }
    return sampled.toIntArray()
}

private fun candidateScore(
    candidate: Int,
    population: Int,
    maxPopulation: Int,
    hintColorInt: Int?
): Float {
    val populationScore = (population.toFloat() / maxPopulation.toFloat()).coerceIn(0f, 1f)
    val hct = Hct.fromInt(candidate)
    val chromaScore = (hct.chroma / 48.0).toFloat().coerceIn(0f, 1f)
    val toneScore = when {
        hct.tone in 20.0..85.0 -> 1f
        hct.tone in 10.0..92.0 -> 0.75f
        else -> 0.45f
    }

    val hintScore = if (hintColorInt == null) {
        1f
    } else {
        val hintLab = DoubleArray(3)
        val candidateLab = DoubleArray(3)
        ColorUtils.colorToLAB(hintColorInt, hintLab)
        ColorUtils.colorToLAB(candidate, candidateLab)
        val dl = (candidateLab[0] - hintLab[0]).toFloat()
        val da = (candidateLab[1] - hintLab[1]).toFloat()
        val db = (candidateLab[2] - hintLab[2]).toFloat()
        val dist = sqrt(dl * dl + da * da + db * db)
        (1f - (dist / 70f)).coerceIn(0f, 1f)
    }

    return (populationScore * 0.50f) + (chromaScore * 0.22f) + (toneScore * 0.10f) + (hintScore * 0.18f)
}

private fun isColorUnsuitableForTheme(hct: Hct): Boolean {
    if (hct.tone <= 4.0 || hct.tone >= 97.0) return true
    if (hct.chroma < MONOCHROME_CHROMA_THRESHOLD && hct.tone !in 12.0..88.0) return true
    return false
}
