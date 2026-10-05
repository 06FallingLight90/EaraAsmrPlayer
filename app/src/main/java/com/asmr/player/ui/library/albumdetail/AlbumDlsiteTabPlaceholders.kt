package com.asmr.player.ui.library.albumdetail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.asmr.player.domain.model.Track
import com.asmr.player.ui.common.audio.AudioItemMenuButtonSize
import com.asmr.player.ui.common.cover.AsmrShimmerPlaceholder
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.translation.translatedPageText
import com.asmr.player.util.Formatting


@Composable
internal fun DlsiteGalleryAwaitingPreview(
    galleryCount: Int?
) {
    val colorScheme = AsmrTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(DlsiteGallerySectionHeight)
            .padding(horizontal = AlbumDetailHorizontalPadding, vertical = 10.dp),
        shape = RoundedCornerShape(DlsiteGalleryThumbCornerRadius.dp),
        color = colorScheme.surfaceVariant.copy(alpha = if (colorScheme.isDark) 0.24f else 0.48f),
        border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.24f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "样图未加载",
                style = MaterialTheme.typography.titleSmall,
                color = colorScheme.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = galleryCount
                    ?.let { count -> "共 $count 张 · 点击右上角“预览”后加载到本地" }
                    ?: "获取到样图链接后，可点击右上角“预览”加载",
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.textSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
internal fun DlsiteStaticPlaceholderLine(
    widthFraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 14.dp,
    cornerRadius: Int = 8,
) {
    AsmrShimmerPlaceholder(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height),
        cornerRadius = cornerRadius,
        animateHighlight = false,
    )
}

@Composable
private fun rememberDlsiteDirectoryListHeight(): Dp {
    val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    return remember(screenHeight) {
        (screenHeight * 0.48f).coerceIn(240.dp, 460.dp)
    }
}

@Composable
internal fun rememberStableOneDirectoryContainerHeight(): Dp {
    return rememberDlsiteDirectoryListHeight() + 104.dp
}

@Composable
internal fun DlsiteDirectoryLoadingPanel() {
    val fixedHeight = rememberDlsiteDirectoryListHeight()
    val colorScheme = AsmrTheme.colorScheme
    val headerSectionColor = directoryBrowserHeaderBackground(colorScheme)
    val actionSectionColor = colorScheme.surfaceVariant.copy(alpha = if (colorScheme.isDark) 0.24f else 0.42f)
    val listSectionColor = colorScheme.surface.copy(alpha = if (colorScheme.isDark) 0.28f else 0.62f)
    val sectionDividerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        color = colorScheme.surfaceVariant.copy(alpha = if (colorScheme.isDark) 0.28f else 0.46f),
        border = BorderStroke(0.5.dp, sectionDividerColor),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AlbumDetailHorizontalPadding, vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(listSectionColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerSectionColor)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AsmrShimmerPlaceholder(
                    modifier = Modifier.size(22.dp),
                    cornerRadius = 7,
                    animateHighlight = false,
                )
                AsmrShimmerPlaceholder(
                    modifier = Modifier.size(width = 54.dp, height = 22.dp),
                    cornerRadius = 8,
                    animateHighlight = false,
                )
                AsmrShimmerPlaceholder(
                    modifier = Modifier.size(width = 5.dp, height = 12.dp),
                    cornerRadius = 3,
                    animateHighlight = false,
                )
                AsmrShimmerPlaceholder(
                    modifier = Modifier.size(width = 82.dp, height = 22.dp),
                    cornerRadius = 8,
                    animateHighlight = false,
                )
            }
            HorizontalDivider(
                thickness = 0.5.dp,
                color = sectionDividerColor,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(actionSectionColor)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    DlsiteStaticPlaceholderLine(
                        widthFraction = 0.42f,
                        height = 12.dp,
                        cornerRadius = 6,
                    )
                    DlsiteStaticPlaceholderLine(
                        widthFraction = 0.64f,
                        height = 9.dp,
                        cornerRadius = 5,
                    )
                }
                AsmrShimmerPlaceholder(
                    modifier = Modifier.size(width = 78.dp, height = 30.dp),
                    cornerRadius = 15,
                    animateHighlight = false,
                )
            }
            HorizontalDivider(
                thickness = 0.5.dp,
                color = sectionDividerColor,
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(fixedHeight),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp),
                userScrollEnabled = false,
            ) {
                item(key = "directoryLoadingFolders", contentType = "folderLoadingGroup") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                colorScheme.surfaceVariant.copy(
                                    alpha = if (colorScheme.isDark) 0.52f else 0.72f
                                )
                            )
                    ) {
                        repeat(2) { index ->
                            DlsiteDirectoryFolderPlaceholder(
                                titleWidthFraction = if (index == 0) 0.64f else 0.48f,
                            )
                        }
                    }
                }
                items(
                    count = 4,
                    key = { index -> "directoryLoadingFile:$index" },
                    contentType = { "fileLoading" },
                ) { index ->
                    DlsiteDirectoryFilePlaceholder(
                        titleWidthFraction = when (index) {
                            0 -> 0.78f
                            1 -> 0.58f
                            2 -> 0.70f
                            else -> 0.52f
                        },
                        metaWidthFraction = if (index % 2 == 0) 0.36f else 0.24f,
                        showThumbnail = index == 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun DlsiteDirectoryFolderPlaceholder(
    titleWidthFraction: Float,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 42.dp)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsmrShimmerPlaceholder(
            modifier = Modifier.size(18.dp),
            cornerRadius = 5,
            animateHighlight = false,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f)) {
            DlsiteStaticPlaceholderLine(
                widthFraction = titleWidthFraction,
                height = 14.dp,
                cornerRadius = 7,
            )
        }
        AsmrShimmerPlaceholder(
            modifier = Modifier.size(18.dp),
            cornerRadius = 6,
            animateHighlight = false,
        )
    }
}

@Composable
private fun DlsiteDirectoryFilePlaceholder(
    titleWidthFraction: Float,
    metaWidthFraction: Float,
    showThumbnail: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 58.dp)
            .padding(start = 8.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(if (showThumbnail) 42.dp else 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            AsmrShimmerPlaceholder(
                modifier = Modifier.size(if (showThumbnail) 42.dp else 21.dp),
                cornerRadius = if (showThumbnail) 8 else 5,
                animateHighlight = false,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            DlsiteStaticPlaceholderLine(
                widthFraction = titleWidthFraction,
                height = 13.dp,
                cornerRadius = 7,
            )
            DlsiteStaticPlaceholderLine(
                widthFraction = metaWidthFraction,
                height = 9.dp,
                cornerRadius = 5,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        AsmrShimmerPlaceholder(
            modifier = Modifier.size(20.dp),
            cornerRadius = 6,
            animateHighlight = false,
        )
        Spacer(modifier = Modifier.width(8.dp))
    }
}

@Composable
internal fun DlsiteTrialLoadingList() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AlbumDetailHorizontalPadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(3) { index ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 1.dp,
                color = AsmrTheme.colorScheme.surface.copy(alpha = 0.36f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DlsiteStaticPlaceholderLine(
                        widthFraction = if (index == 0) 0.62f else 0.48f,
                        height = 15.dp
                    )
                    DlsiteStaticPlaceholderLine(
                        widthFraction = if (index == 2) 0.26f else 0.18f,
                        height = 11.dp
                    )
                }
            }
        }
    }
}

@Composable
internal fun DlsiteTrialAudioItem(
    track: Track,
    onClick: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null,
) {
    val colorScheme = AsmrTheme.colorScheme
    val isOnline = remember(track.path) { track.path.trim().startsWith("http", ignoreCase = true) }
    val durationText = remember(track.duration) { Formatting.formatTrackSeconds(track.duration) }
    val subtitleText = remember(isOnline, durationText) {
        when {
            isOnline && durationText.isNotBlank() -> "在线 · $durationText"
            isOnline -> "在线"
            durationText.isNotBlank() -> durationText
            else -> "在线播放"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = AlbumDetailHorizontalPadding, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = colorScheme.primary
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = translatedPageText(track.title, fileName = true),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = colorScheme.textPrimary
            )
            Text(
                text = subtitleText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.textTertiary
            )
        }
        if (onAddToPlaylist != null) {
            IconButton(
                onClick = onAddToPlaylist,
                modifier = Modifier.size(AudioItemMenuButtonSize)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}