package com.asmr.player.ui.library.albumdetail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.asmr.player.ui.theme.AsmrTheme

internal enum class DlsiteEmptyArtworkKind {
    Gallery,
    One,
    Trial,
}

@Composable
internal fun DlsiteSectionEmptyState(
    text: String,
    artworkKind: DlsiteEmptyArtworkKind,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AlbumDetailHorizontalPadding, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DlsiteSectionEmptyArtwork(
            kind = artworkKind,
            modifier = Modifier.size(width = 92.dp, height = 60.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = AsmrTheme.colorScheme.textSecondary
        )
    }
}

@Composable
private fun DlsiteSectionEmptyArtwork(
    kind: DlsiteEmptyArtworkKind,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val strokeColor = colorScheme.textTertiary.copy(alpha = if (colorScheme.isDark) 0.86f else 0.76f)
    val accentColor = colorScheme.primary.copy(alpha = if (colorScheme.isDark) 0.76f else 0.68f)

    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.05f
        when (kind) {
            DlsiteEmptyArtworkKind.Gallery -> drawGalleryEmptyArtwork(
                strokeColor = strokeColor,
                accentColor = accentColor,
                strokeWidth = strokeWidth
            )
            DlsiteEmptyArtworkKind.One -> drawOneEmptyArtwork(
                strokeColor = strokeColor,
                accentColor = accentColor,
                strokeWidth = strokeWidth
            )
            DlsiteEmptyArtworkKind.Trial -> drawTrialEmptyArtwork(
                strokeColor = strokeColor,
                accentColor = accentColor,
                strokeWidth = strokeWidth
            )
        }
    }
}

private fun DrawScope.drawGalleryEmptyArtwork(
    strokeColor: Color,
    accentColor: Color,
    strokeWidth: Float
) {
    val frameSize = Size(size.width * 0.34f, size.height * 0.48f)
    val corner = CornerRadius(strokeWidth * 1.8f, strokeWidth * 1.8f)

    fun drawPhotoFrame(origin: Offset) {
        drawRoundRect(
            color = strokeColor,
            topLeft = origin,
            size = frameSize,
            cornerRadius = corner,
            style = Stroke(width = strokeWidth)
        )
        drawCircle(
            color = accentColor,
            radius = strokeWidth * 0.8f,
            center = origin + Offset(frameSize.width * 0.72f, frameSize.height * 0.24f)
        )
        drawLine(
            color = strokeColor,
            start = origin + Offset(frameSize.width * 0.16f, frameSize.height * 0.72f),
            end = origin + Offset(frameSize.width * 0.40f, frameSize.height * 0.48f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = strokeColor,
            start = origin + Offset(frameSize.width * 0.40f, frameSize.height * 0.48f),
            end = origin + Offset(frameSize.width * 0.58f, frameSize.height * 0.62f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = strokeColor,
            start = origin + Offset(frameSize.width * 0.58f, frameSize.height * 0.62f),
            end = origin + Offset(frameSize.width * 0.82f, frameSize.height * 0.42f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }

    drawPhotoFrame(Offset(size.width * 0.14f, size.height * 0.26f))
    drawPhotoFrame(Offset(size.width * 0.42f, size.height * 0.14f))
    drawLine(
        color = strokeColor,
        start = Offset(size.width * 0.20f, size.height * 0.84f),
        end = Offset(size.width * 0.80f, size.height * 0.84f),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawOneEmptyArtwork(
    strokeColor: Color,
    accentColor: Color,
    strokeWidth: Float
) {
    val folderSize = Size(size.width * 0.28f, size.height * 0.18f)
    val folderTopLeft = Offset(size.width * 0.10f, size.height * 0.14f)
    val corner = CornerRadius(strokeWidth * 1.6f, strokeWidth * 1.6f)

    drawRoundRect(
        color = strokeColor,
        topLeft = folderTopLeft,
        size = folderSize,
        cornerRadius = corner,
        style = Stroke(width = strokeWidth)
    )

    drawLine(
        color = strokeColor,
        start = Offset(size.width * 0.18f, size.height * 0.14f),
        end = Offset(size.width * 0.26f, size.height * 0.14f),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )

    val trunkX = size.width * 0.28f
    val trunkStartY = size.height * 0.40f
    val branchYs = listOf(size.height * 0.50f, size.height * 0.66f, size.height * 0.82f)
    drawLine(
        color = strokeColor,
        start = Offset(trunkX, trunkStartY),
        end = Offset(trunkX, branchYs.last()),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    drawLine(
        color = strokeColor,
        start = Offset(size.width * 0.24f, size.height * 0.32f),
        end = Offset(trunkX, trunkStartY),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )

    branchYs.forEach { y ->
        drawLine(
            color = strokeColor,
            start = Offset(trunkX, y),
            end = Offset(size.width * 0.48f, y),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = accentColor,
            radius = strokeWidth * 0.72f,
            center = Offset(size.width * 0.48f, y)
        )
        drawLine(
            color = strokeColor,
            start = Offset(size.width * 0.58f, y),
            end = Offset(size.width * 0.82f, y),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawTrialEmptyArtwork(
    strokeColor: Color,
    accentColor: Color,
    strokeWidth: Float
) {
    val screenTopLeft = Offset(size.width * 0.10f, size.height * 0.18f)
    val screenSize = Size(size.width * 0.46f, size.height * 0.34f)
    val corner = CornerRadius(strokeWidth * 1.8f, strokeWidth * 1.8f)

    drawRoundRect(
        color = strokeColor,
        topLeft = screenTopLeft,
        size = screenSize,
        cornerRadius = corner,
        style = Stroke(width = strokeWidth)
    )

    val playCenter = screenTopLeft + Offset(screenSize.width * 0.50f, screenSize.height * 0.50f)
    drawLine(
        color = accentColor,
        start = Offset(playCenter.x - size.width * 0.03f, playCenter.y - size.height * 0.09f),
        end = Offset(playCenter.x - size.width * 0.03f, playCenter.y + size.height * 0.09f),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    drawLine(
        color = accentColor,
        start = Offset(playCenter.x - size.width * 0.03f, playCenter.y - size.height * 0.09f),
        end = Offset(playCenter.x + size.width * 0.08f, playCenter.y),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    drawLine(
        color = accentColor,
        start = Offset(playCenter.x - size.width * 0.03f, playCenter.y + size.height * 0.09f),
        end = Offset(playCenter.x + size.width * 0.08f, playCenter.y),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )

    val barWidth = size.width * 0.07f
    val barBottom = size.height * 0.80f
    val barXs = listOf(0.62f, 0.72f, 0.82f)
    val barHeights = listOf(0.18f, 0.30f, 0.22f)
    barXs.zip(barHeights).forEach { (xFraction, heightFraction) ->
        val height = size.height * heightFraction
        val left = size.width * xFraction - barWidth / 2f
        val top = barBottom - height
        drawLine(
            color = strokeColor,
            start = Offset(left + barWidth / 2f, barBottom),
            end = Offset(left + barWidth / 2f, top),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }

    drawLine(
        color = strokeColor,
        start = Offset(size.width * 0.10f, size.height * 0.80f),
        end = Offset(size.width * 0.94f, size.height * 0.80f),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
}