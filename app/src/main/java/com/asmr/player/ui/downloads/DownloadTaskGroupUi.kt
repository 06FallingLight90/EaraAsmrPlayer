package com.asmr.player.ui.downloads

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asmr.player.ui.common.cover.AsmrAsyncImage
import com.asmr.player.ui.common.cover.DiscPlaceholder
import com.asmr.player.ui.common.cover.albumCoverImageModel
import com.asmr.player.ui.theme.AsmrTheme

@Composable
internal fun TaskGroupHeader(
    expanded: Boolean,
    title: String,
    subtitle: String,
    summary: String,
    summaryColor: Color,
    albumCover: TaskAlbumCoverUi,
    progress: Float?,
    progressIndeterminate: Boolean,
    reserveProgressSpace: Boolean,
    summaryOnTitleLine: Boolean = false,
    onToggleExpanded: () -> Unit,
    actions: (@Composable () -> Unit)? = null
) {
    val colors = AsmrTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onToggleExpanded
            )
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (expanded) {
                Icons.Rounded.KeyboardArrowDown
            } else {
                Icons.AutoMirrored.Rounded.KeyboardArrowRight
            },
            contentDescription = if (expanded) "收起任务详情" else "展开任务详情",
            tint = colors.primary,
            modifier = Modifier.size(22.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (summaryOnTitleLine && summary.isNotBlank()) {
                    TaskGroupSummary(summary, summaryColor)
                }
            }
            if (subtitle.isNotBlank() || (!summaryOnTitleLine && summary.isNotBlank())) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (!summaryOnTitleLine && summary.isNotBlank()) {
                        TaskGroupSummary(summary, summaryColor)
                    }
                }
            }
            if (reserveProgressSpace) {
                StableProgressSlot(
                    progress = progress,
                    visible = progress != null || progressIndeterminate,
                    trackColor = colors.surface.copy(alpha = 0.8f),
                    progressColor = colors.primary,
                    indeterminate = progressIndeterminate
                )
            }
        }
        if (actions != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                actions()
            }
        }
        TaskGroupCover(albumCover)
    }
}

@Composable
internal fun TaskGroupSummary(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = 112.dp)
    )
}

@Composable
internal fun TaskGroupCover(albumCover: TaskAlbumCoverUi) {
    val coverModel = remember(
        albumCover.coverThumbPath,
        albumCover.coverPath,
        albumCover.coverUrl
    ) {
        albumCoverImageModel(
            coverThumbPath = albumCover.coverThumbPath,
            coverPath = albumCover.coverPath,
            coverUrl = albumCover.coverUrl
        )
    }
    if (coverModel == null) {
        DiscPlaceholder(
            cornerRadius = 8,
            modifier = Modifier.size(48.dp)
        )
    } else {
        AsmrAsyncImage(
            model = coverModel,
            contentDescription = "作品封面",
            contentScale = ContentScale.Crop,
            placeholderCornerRadius = 8,
            fadeIn = false,
            peekAnySizeForInitial = true,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
        )
    }
}

@Composable
internal fun StableProgressSlot(
    progress: Float?,
    visible: Boolean,
    trackColor: Color,
    progressColor: Color,
    indeterminate: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
    ) {
        if (visible) {
            CompactProgressBar(
                progress = progress,
                trackColor = trackColor,
                progressColor = progressColor,
                indeterminate = indeterminate
            )
        }
    }
}

@Composable
internal fun CompactProgressBar(
    progress: Float?,
    trackColor: Color,
    progressColor: Color,
    indeterminate: Boolean,
    modifier: Modifier = Modifier
) {
    if (indeterminate) {
        LinearProgressIndicator(
            modifier = modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = progressColor,
            trackColor = trackColor
        )
        return
    }

    val animatedProgress = animateFloatAsState(
        targetValue = progress?.coerceIn(0f, 1f) ?: 0f,
        animationSpec = tween(durationMillis = 300),
        label = "compact_progress"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .drawBehind {
                val radius = CornerRadius(size.height / 2f, size.height / 2f)
                drawRoundRect(color = trackColor, cornerRadius = radius)
                clipRect(right = size.width * animatedProgress.value) {
                    drawRoundRect(color = progressColor, cornerRadius = radius)
                }
            }
    )
}
