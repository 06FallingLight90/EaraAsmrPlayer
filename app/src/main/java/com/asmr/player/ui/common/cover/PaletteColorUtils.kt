package com.asmr.player.ui.common.cover

import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

internal fun pickBestColorInt(
    palette: Palette,
    fallbackColorInt: Int,
    preferDarkBackground: Boolean,
    hintColorInt: Int? = null
): Int {
    val swatches = palette.swatches
    if (swatches.isEmpty()) {
        return palette.mutedSwatch?.rgb
            ?: palette.vibrantSwatch?.rgb
            ?: palette.dominantSwatch?.rgb
            ?: fallbackColorInt
    }

    val candidates = run {
        val tmp = FloatArray(3)
        swatches.filterNot { swatch ->
            ColorUtils.colorToHSL(swatch.rgb, tmp)
            val s = tmp[1]
            val l = tmp[2]
            l <= 0.06f || l >= 0.94f || s <= 0.08f
        }
    }
    if (candidates.isEmpty()) return fallbackColorInt

    val targetLightness = if (preferDarkBackground) 0.48f else 0.40f
    val maxPopulation = candidates.maxOfOrNull { it.population }?.coerceAtLeast(1) ?: 1

    var bestRgb: Int? = null
    var bestScore = -1f
    val hsl = FloatArray(3)
    val hintLab = if (hintColorInt != null) DoubleArray(3).also { ColorUtils.colorToLAB(hintColorInt, it) } else null
    val tmpLab = if (hintLab != null) DoubleArray(3) else null
    for (swatch in candidates) {
        val rgb = swatch.rgb
        ColorUtils.colorToHSL(rgb, hsl)
        val saturation = hsl[1]
        val lightness = hsl[2]

        val saturationScore = ((saturation - 0.12f) / 0.88f).coerceIn(0f, 1f)
        val lightnessScore = (1f - (abs(lightness - targetLightness) / 0.55f)).coerceIn(0f, 1f)
        val populationScore = (swatch.population.toFloat() / maxPopulation.toFloat()).coerceIn(0f, 1f)
        val popRatio = populationScore

        val grayPenalty = when {
            saturation < 0.06f -> 0.60f
            saturation < 0.10f -> 0.75f
            else -> 1f
        }
        val extremePenalty = if (lightness < 0.04f || lightness > 0.96f) 0.2f else 1f
        val brightPenalty = when {
            lightness > 0.88f && popRatio < 0.35f -> 0.08f
            lightness > 0.82f && popRatio < 0.35f -> 0.16f
            lightness > 0.82f -> 0.35f
            lightness > 0.74f && saturation > 0.22f -> 0.60f
            lightness > 0.68f && saturation > 0.55f -> 0.72f
            else -> 1f
        }

        val textLikePenalty = when {
            lightness > 0.90f && popRatio < 0.60f -> 0.12f
            lightness > 0.86f && popRatio < 0.45f -> 0.25f
            lightness > 0.78f && popRatio < 0.30f -> 0.45f
            else -> 1f
        }

        var score = populationScore * (0.58f * saturationScore + 0.42f * lightnessScore) * grayPenalty * extremePenalty * brightPenalty * textLikePenalty
        if (hintLab != null && tmpLab != null) {
            ColorUtils.colorToLAB(rgb, tmpLab)
            val dl = (tmpLab[0] - hintLab[0]).toFloat()
            val da = (tmpLab[1] - hintLab[1]).toFloat()
            val db = (tmpLab[2] - hintLab[2]).toFloat()
            val dist = sqrt(dl * dl + da * da + db * db)
            val hintScore = (1f - (dist / 55f)).coerceIn(0f, 1f)
            score *= (0.80f + 0.20f * hintScore)
        }
        if (score > bestScore) {
            bestScore = score
            bestRgb = rgb
        }
    }

    return bestRgb
        ?: palette.mutedSwatch?.rgb
        ?: palette.vibrantSwatch?.rgb
        ?: palette.dominantSwatch?.rgb
        ?: fallbackColorInt
}

internal fun adjustHslForUi(hsl: FloatArray, preferDarkBackground: Boolean) {
    val saturation = hsl[1].coerceIn(0f, 1f)
    val lightness = hsl[2].coerceIn(0f, 1f)

    val boostedSaturation = when {
        saturation < 0.10f -> saturation
        saturation < 0.28f -> (saturation * 1.30f).coerceAtMost(0.44f)
        saturation < 0.5f -> (saturation * 1.12f).coerceAtMost(0.70f)
        else -> saturation.coerceAtMost(0.9f)
    }

    val hue = hsl[0]
    val isNeonLike = boostedSaturation >= 0.55f && lightness >= 0.62f

    val deepenedLightness = if (isNeonLike) {
        val maxLightness = when {
            hue in 40f..85f -> if (preferDarkBackground) 0.44f else 0.40f
            hue in 85f..170f -> if (preferDarkBackground) 0.46f else 0.42f
            hue in 170f..260f -> if (preferDarkBackground) 0.50f else 0.46f
            else -> if (preferDarkBackground) 0.48f else 0.44f
        }
        lightness.coerceAtMost(maxLightness)
    } else {
        lightness
    }

    val isBright = deepenedLightness >= 0.62f
    val furtherDeepenedLightness = if (isBright) {
        val baseMax = if (preferDarkBackground) 0.66f else 0.60f
        val saturationAdj = when {
            boostedSaturation >= 0.60f -> 0.08f
            boostedSaturation >= 0.45f -> 0.05f
            boostedSaturation >= 0.30f -> 0.03f
            else -> 0f
        }
        val hueAdj = when {
            hue in 40f..85f -> 0.07f
            hue in 85f..170f -> 0.05f
            hue in 170f..260f -> 0.02f
            else -> 0.04f
        }
        val maxLightness = (baseMax - saturationAdj - hueAdj).coerceIn(0.38f, baseMax)
        deepenedLightness.coerceAtMost(maxLightness)
    } else {
        deepenedLightness
    }

    val adjustedLightness = if (preferDarkBackground) {
        furtherDeepenedLightness.coerceIn(0.18f, 0.70f)
    } else {
        furtherDeepenedLightness.coerceIn(0.12f, 0.62f)
    }

    val adjustedSaturation = if (isNeonLike) boostedSaturation.coerceAtMost(0.72f) else boostedSaturation

    var finalSaturation = adjustedSaturation
    var finalLightness = adjustedLightness
    val brightThreshold = if (preferDarkBackground) 0.54f else 0.50f
    if (finalLightness > brightThreshold && finalSaturation > 0.18f) {
        val t = ((finalLightness - brightThreshold) / (1f - brightThreshold)).coerceIn(0f, 1f)
        val satScale = (1f - 0.30f * t).coerceIn(0.64f, 1f)
        finalSaturation = (finalSaturation * satScale).coerceAtMost(0.70f)
        finalLightness = finalLightness.coerceAtMost(if (preferDarkBackground) 0.54f else 0.48f)
    }

    val darken = run {
        val pivot = if (preferDarkBackground) 0.34f else 0.26f
        val t = ((finalLightness - pivot) / (1f - pivot)).coerceIn(0f, 1f)
        val smooth = t * t * (3f - 2f * t)
        val minDarken = if (preferDarkBackground) 0.020f else 0.015f
        val maxDarken = if (preferDarkBackground) 0.095f else 0.075f
        minDarken + (maxDarken - minDarken) * smooth
    }
    finalLightness = (finalLightness - darken).coerceIn(
        minimumValue = if (preferDarkBackground) 0.16f else 0.10f,
        maximumValue = if (preferDarkBackground) 0.62f else 0.54f
    )

    hsl[1] = finalSaturation
    hsl[2] = finalLightness
}

