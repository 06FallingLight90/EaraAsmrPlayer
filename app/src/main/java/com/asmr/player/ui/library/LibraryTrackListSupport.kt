package com.asmr.player.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asmr.player.ui.common.audio.AudioItemMenuAction
import com.asmr.player.ui.common.audio.AudioItemRow
import com.asmr.player.ui.common.audio.queryCachedTrackFileSize
import com.asmr.player.ui.common.cover.AsmrAsyncImage
import com.asmr.player.ui.common.cover.EaraLogoLoadingIndicator
import com.asmr.player.ui.common.cover.NoImageLoadingIndicator
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.translation.translatedPageText
import com.asmr.player.util.Formatting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


private val LibraryTrackListHeaderCornerRadius = 10.dp
private val LibraryTrackListItemCornerRadius = 10.dp

@Composable
internal fun TrackAlbumHeader(
    albumTitle: String,
    rjCode: String,
    trackCount: Int,
    totalDurationSeconds: Double,
    totalSizeBytes: Long?,
    coverModel: Any?,
    expanded: Boolean,
    isFirstInList: Boolean,
    isLastInList: Boolean,
    onToggle: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val containerShape = if (expanded) {
        RoundedCornerShape(
            topStart = if (isFirstInList) LibraryTrackListHeaderCornerRadius else 0.dp,
            topEnd = if (isFirstInList) LibraryTrackListHeaderCornerRadius else 0.dp,
            bottomStart = 0.dp,
            bottomEnd = 0.dp
        )
    } else {
        RoundedCornerShape(
            topStart = if (isFirstInList) LibraryTrackListHeaderCornerRadius else 0.dp,
            topEnd = if (isFirstInList) LibraryTrackListHeaderCornerRadius else 0.dp,
            bottomStart = if (isLastInList) LibraryTrackListHeaderCornerRadius else 0.dp,
            bottomEnd = if (isLastInList) LibraryTrackListHeaderCornerRadius else 0.dp
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(containerShape)
            .background(colorScheme.surface)
            .clickable { onToggle() }
            .padding(horizontal = LibraryPageHorizontalPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsmrAsyncImage(
            model = coverModel,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholderCornerRadius = 8,
            peekAnySizeForInitial = true,
            loading = NoImageLoadingIndicator,
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = translatedPageText(albumTitle).ifBlank { rjCode.ifBlank { "专辑" } },
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = colorScheme.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            val footerSegments = buildList {
                if (rjCode.isNotBlank()) add(rjCode)
                add("$trackCount 音频")
                Formatting.formatTrackSeconds(totalDurationSeconds).takeIf { it.isNotBlank() }?.let(::add)
                totalSizeBytes?.takeIf { it > 0L }?.let(Formatting::formatFileSize)?.let(::add)
            }
            if (footerSegments.isNotEmpty()) {
                Text(
                    text = footerSegments.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

}

@Composable
internal fun rememberAlbumTrackListTotalSizeBytes(
    rows: List<com.asmr.player.domain.model.LibraryTrackRow>,
    loadFileSizes: Boolean
): Long? {
    if (rows.isEmpty() || !loadFileSizes) return null
    val context = LocalContext.current
    val paths = remember(rows) { rows.map { it.trackPath } }
    return androidx.compose.runtime.produceState<Long?>(initialValue = null, paths, loadFileSizes) {
        value = withContext(Dispatchers.IO) {
            val total = rows.sumOf { row ->
                queryCachedTrackFileSize(context, row.trackPath) ?: 0L
            }
            total.takeIf { it > 0L }
        }
    }.value
}

@Composable
internal fun TrackListRow(
    title: String,
    subtitle: String,
    fixedTrailingSubtitle: String,
    showSubtitleStamp: Boolean,
    isLastInSection: Boolean,
    onClick: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onManageTags: (() -> Unit)? = null,
    onRemove: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val rowShape = if (isLastInSection) {
        RoundedCornerShape(
            topStart = 0.dp,
            topEnd = 0.dp,
            bottomStart = LibraryTrackListItemCornerRadius,
            bottomEnd = LibraryTrackListItemCornerRadius
        )
    } else {
        RoundedCornerShape(0.dp)
    }

    AudioItemRow(
        title = title,
        subtitle = subtitle,
        fixedTrailingSubtitle = fixedTrailingSubtitle,
        showSubtitleStamp = showSubtitleStamp,
        onClick = onClick,
        compact = true,
        compactContentPadding = PaddingValues(horizontal = 16.dp, vertical = 5.dp),
        titleMaxLines = 1,
        titleTextStyle = MaterialTheme.typography.bodyMedium,
        subtitleTextStyle = MaterialTheme.typography.labelSmall.copy(lineHeight = 14.sp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(colorScheme.surface),
        actions = buildList {
            add(
                AudioItemMenuAction(
                    label = "添加到播放队列",
                    onClick = onAddToQueue,
                    icon = Icons.AutoMirrored.Rounded.QueueMusic
                )
            )
            add(
                AudioItemMenuAction(
                    label = "添加到播放列表",
                    onClick = onAddToPlaylist,
                    icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    showDividerBefore = true
                )
            )
            if (onManageTags != null) {
                add(
                    AudioItemMenuAction(
                        label = "标签管理",
                        onClick = onManageTags,
                        icon = Icons.AutoMirrored.Rounded.Label,
                        showDividerBefore = true
                    )
                )
            }
            add(
                AudioItemMenuAction(
                    label = "从专辑移除",
                    onClick = onRemove,
                    icon = Icons.Rounded.Delete,
                    showDividerBefore = true
                )
            )
        }
    )
}

@Composable
internal fun AlbumSyncStatusOverlay(
    syncStatus: SyncStatus,
    indicatorSize: Dp,
    blurRadius: Dp,
) {
    when (syncStatus) {
        SyncStatus.Idle -> Unit
        SyncStatus.Syncing -> Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .blur(blurRadius),
            contentAlignment = Alignment.Center,
        ) {
            EaraLogoLoadingIndicator(
                size = indicatorSize,
                tint = Color.White,
                glowColor = Color.White,
                showGlow = false,
            )
        }
        is SyncStatus.Error -> Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Red.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = "同步失败",
                tint = Color.White,
                modifier = Modifier.size(indicatorSize),
            )
        }
    }
}
