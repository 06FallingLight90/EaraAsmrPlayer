package com.asmr.player.ui.library.albumdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import com.asmr.player.domain.model.TreeFileType
import com.asmr.player.ui.common.audio.AudioItemMenuButtonSize
import com.asmr.player.ui.common.audio.AudioItemSubtitleStampSpacing
import com.asmr.player.ui.common.audio.SubtitleStamp
import com.asmr.player.ui.common.cover.AsmrAsyncImage
import com.asmr.player.ui.common.cover.NoImageLoadingIndicator
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.theme.dynamicPageContainerColor
import com.asmr.player.ui.translation.translatedPageText

@Composable
internal fun CompactBreadcrumbNode(
    text: String,
    selected: Boolean,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) colorScheme.primary else colorScheme.textSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = if (selected) {
                MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
            } else {
                MaterialTheme.typography.labelLarge
            },
            color = if (selected) colorScheme.primary else colorScheme.textSecondary,
            modifier = Modifier.widthIn(max = 112.dp)
        )
    }
}

@Composable
internal fun DirectoryActionGroupButton(
    text: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDisabledClick: (() -> Unit)? = null
) {
    val colorScheme = AsmrTheme.colorScheme
    TextButton(
        onClick = {
            if (enabled) onClick() else onDisabledClick?.invoke()
        },
        enabled = enabled || onDisabledClick != null,
        modifier = modifier.height(32.dp),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp),
        colors = ButtonDefaults.textButtonColors(
            contentColor = colorScheme.primary,
            disabledContentColor = colorScheme.textTertiary
        )
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun CompactDirectoryBreadcrumbContent(
    currentPath: String,
    breadcrumbs: List<DirectoryBreadcrumbSegment>,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val displayedCrumbs = remember(breadcrumbs) {
        when {
            breadcrumbs.size <= 2 -> breadcrumbs
            else -> listOf(
                DirectoryBreadcrumbSegment(label = "...", path = ""),
                breadcrumbs[breadcrumbs.lastIndex - 1],
                breadcrumbs.last()
            )
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        CompactBreadcrumbNode(
            text = "根目录",
            selected = currentPath.isBlank(),
            icon = Icons.Rounded.Home,
            onClick = { onNavigate("") }
        )
        displayedCrumbs.forEach { crumb ->
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = colorScheme.textTertiary.copy(alpha = 0.72f),
                modifier = Modifier.size(14.dp)
            )
            if (crumb.label == "..." && crumb.path.isBlank()) {
                Text(
                    text = "...",
                    style = MaterialTheme.typography.labelLarge,
                    color = colorScheme.textTertiary,
                    maxLines = 1
                )
            } else {
                CompactBreadcrumbNode(
                    text = translatedPageText(crumb.label),
                    selected = crumb.path == currentPath,
                    onClick = { onNavigate(crumb.path) }
                )
            }
        }
    }
}

@Composable
internal fun DirectoryFolderRow(
    title: String,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val colorScheme = AsmrTheme.colorScheme
    val materialColorScheme = MaterialTheme.colorScheme
    val dynamicContainerColor = dynamicPageContainerColor(colorScheme)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(11.dp))
        Text(
            text = translatedPageText(title),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = colorScheme.textPrimary
        )
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = colorScheme.textTertiary,
            modifier = Modifier.size(17.dp)
        )
        if (onDelete != null) {
            var showMenuExpanded by rememberSaveable(title) { mutableStateOf(false) }
            Box {
                IconButton(
                    onClick = { showMenuExpanded = true },
                    modifier = Modifier.size(AudioItemMenuButtonSize)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "目录操作",
                        tint = colorScheme.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                MaterialTheme(
                    colorScheme = materialColorScheme.copy(
                        surface = dynamicContainerColor,
                        surfaceContainer = dynamicContainerColor
                    )
                ) {
                    DropdownMenu(
                        expanded = showMenuExpanded,
                        onDismissRequest = { showMenuExpanded = false },
                        modifier = Modifier.background(dynamicContainerColor)
                    ) {
                        DropdownMenuItem(
                            text = { Text("删除目录", color = materialColorScheme.error) },
                            onClick = {
                                showMenuExpanded = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = null,
                                    tint = materialColorScheme.error
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun DirectoryBatchBarEmbedded(
    targets: List<PlaylistAddTarget>,
    summaryText: String,
    hintText: String,
    showActions: Boolean,
    modifier: Modifier = Modifier,
    onAddToFavorites: (List<MediaItem>) -> Unit,
    onOpenBatchPlaylistPicker: (List<MediaItem>) -> Unit,
    onAddMediaItemsToQueue: (List<MediaItem>) -> Unit,
    showTranslateAction: Boolean = false,
    subtitleGenerationText: String = "生成并翻译字幕",
    subtitleGenerationEnabled: Boolean = false,
    onGenerateSubtitles: () -> Unit = {},
    onSubtitleGenerationUnavailable: (() -> Unit)? = null
) {
    val mediaItems = remember(targets) { targets.map { it.toMediaItem() } }
    val hasMediaItems = mediaItems.isNotEmpty()
    Row(
        modifier = modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = summaryText,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 16.sp
                ),
                color = AsmrTheme.colorScheme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = hintText,
                style = MaterialTheme.typography.labelSmall,
                color = AsmrTheme.colorScheme.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (showActions) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DirectoryActionGroupButton(
                    text = "收藏",
                    icon = Icons.Rounded.FavoriteBorder,
                    enabled = hasMediaItems,
                    onClick = { onAddToFavorites(mediaItems) }
                )
                DirectoryActionGroupButton(
                    text = "列表",
                    icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    enabled = hasMediaItems,
                    onClick = {
                        if (hasMediaItems) {
                            onOpenBatchPlaylistPicker(mediaItems.toList())
                        }
                    }
                )
                DirectoryActionGroupButton(
                    text = "队列",
                    icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    enabled = hasMediaItems,
                    onClick = { onAddMediaItemsToQueue(mediaItems) }
                )
                if (showTranslateAction) {
                    DirectoryActionGroupButton(
                        text = subtitleGenerationText,
                        icon = Icons.Rounded.Translate,
                        enabled = subtitleGenerationEnabled,
                        onClick = onGenerateSubtitles,
                        onDisabledClick = onSubtitleGenerationUnavailable
                    )
                }
            }
        } else if (showTranslateAction) {
            DirectoryActionGroupButton(
                text = subtitleGenerationText,
                icon = Icons.Rounded.Translate,
                enabled = subtitleGenerationEnabled,
                onClick = onGenerateSubtitles,
                onDisabledClick = onSubtitleGenerationUnavailable
            )
        }
    }
}


@Composable
internal fun TreeFolderRow(
    title: String,
    depth: Int,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp)
    ) {
        ListItem(
            headlineContent = { 
                Text(
                    translatedPageText(title, fileName = true),
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis,
                    color = colorScheme.textPrimary,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                ) 
            },
            leadingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (depth > 0) Spacer(modifier = Modifier.width((depth * 12).dp))
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            trailingContent = {
                Icon(
                    imageVector = if (expanded) Icons.Rounded.KeyboardArrowDown else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colorScheme.textTertiary,
                    modifier = Modifier.size(20.dp)
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@Composable
internal fun TreeFileRow(
    title: String,
    depth: Int,
    fileType: TreeFileType,
    isPlayable: Boolean,
    isOnline: Boolean = true,
    showSubtitleStamp: Boolean = false,
    thumbnailModel: Any? = null,
    onPrimary: () -> Unit,
    onSetAsCover: (() -> Unit)? = null,
    onDownload: (() -> Unit)?,
    onAddToQueue: (() -> Unit)?,
    onAddToPlaylist: (() -> Unit)?,
    onManageTags: (() -> Unit)? = null,
    onRemoveFromAlbum: (() -> Unit)? = null
) {
    val colorScheme = AsmrTheme.colorScheme
    val materialColorScheme = MaterialTheme.colorScheme
    val dynamicContainerColor = dynamicPageContainerColor(colorScheme)
    val icon = treeFileTypeIcon(fileType)
    val iconTint = treeFileTypeTint(fileType, colorScheme)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPrimary)
            .padding(vertical = 2.dp)
    ) {
        val showPrimaryAction = isPlayable
        val showMenu = showPrimaryAction || onDownload != null || onAddToQueue != null || onAddToPlaylist != null || onManageTags != null || onRemoveFromAlbum != null
        val showTrailing = onSetAsCover != null || showMenu
        val metaLine = if (fileType == TreeFileType.Audio) {
            if (isOnline) "在线音频" else "本地音频"
        } else {
            fileTypeLabel(fileType)
        }
        ListItem(
            headlineContent = { 
                Text(
                    translatedPageText(title, fileName = true),
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis,
                    color = colorScheme.textSecondary,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                ) 
            },
            supportingContent = {
                Text(
                    text = metaLine,
                    color = colorScheme.textTertiary,
                    style = MaterialTheme.typography.bodySmall
                )
            },
            leadingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (depth > 0) Spacer(modifier = Modifier.width((depth * 12).dp))
                    if (fileType == TreeFileType.Image && thumbnailModel != null) {
                        AsmrAsyncImage(
                            model = thumbnailModel,
                            contentDescription = null,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop,
                            placeholderCornerRadius = 6,
                            loading = NoImageLoadingIndicator,
                        )
                    } else {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            trailingContent = if (showTrailing) {
                {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (showSubtitleStamp) {
                            SubtitleStamp(modifier = Modifier.padding(end = AudioItemSubtitleStampSpacing))
                        }
                        if (onSetAsCover != null) {
                            IconButton(
                                onClick = onSetAsCover,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Photo,
                                    contentDescription = "设为封面",
                                    tint = colorScheme.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        if (showMenu) {
                            var showMenuExpanded by rememberSaveable { mutableStateOf(false) }
                            Box {
                                IconButton(
                                    onClick = { showMenuExpanded = true },
                                    modifier = Modifier.size(AudioItemMenuButtonSize)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MoreVert,
                                        contentDescription = "更多操作",
                                        tint = colorScheme.textSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                MaterialTheme(
                                    colorScheme = materialColorScheme.copy(
                                        surface = dynamicContainerColor,
                                        surfaceContainer = dynamicContainerColor
                                    )
                                ) {
                                    val dividerColor = materialColorScheme.outlineVariant.copy(alpha = 0.3f)
                                    var hasVisibleItem = false
                                    @Composable
                                    fun addDividerIfNeeded() {
                                        if (hasVisibleItem) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(horizontal = 8.dp),
                                                thickness = 0.5.dp,
                                                color = dividerColor
                                            )
                                        }
                                        hasVisibleItem = true
                                    }
                                    DropdownMenu(
                                        expanded = showMenuExpanded,
                                        onDismissRequest = { showMenuExpanded = false },
                                        modifier = Modifier.background(dynamicContainerColor)
                                    ) {
                                        if (showPrimaryAction) {
                                            addDividerIfNeeded()
                                            DropdownMenuItem(
                                                text = { Text("播放") },
                                                onClick = {
                                                    onPrimary()
                                                    showMenuExpanded = false
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Rounded.PlayArrow,
                                                        contentDescription = null,
                                                        tint = colorScheme.primary
                                                    )
                                                }
                                            )
                                        }
                                        if (onDownload != null) {
                                            addDividerIfNeeded()
                                            DropdownMenuItem(
                                                text = { Text("下载") },
                                                onClick = {
                                                    onDownload.invoke()
                                                    showMenuExpanded = false
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Rounded.Download,
                                                        contentDescription = null,
                                                        tint = colorScheme.textSecondary
                                                    )
                                                }
                                            )
                                        }
                                        if (onAddToQueue != null) {
                                            addDividerIfNeeded()
                                            DropdownMenuItem(
                                                text = { Text("添加到播放队列") },
                                                onClick = {
                                                    onAddToQueue.invoke()
                                                    showMenuExpanded = false
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.AutoMirrored.Rounded.PlaylistPlay,
                                                        contentDescription = null,
                                                        tint = colorScheme.textSecondary
                                                    )
                                                }
                                            )
                                        }
                                        if (onAddToPlaylist != null) {
                                            addDividerIfNeeded()
                                            DropdownMenuItem(
                                                text = { Text("添加到我的列表") },
                                                onClick = {
                                                    onAddToPlaylist.invoke()
                                                    showMenuExpanded = false
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.AutoMirrored.Rounded.PlaylistAdd,
                                                        contentDescription = null,
                                                        tint = colorScheme.textSecondary
                                                    )
                                                }
                                            )
                                        }
                                        if (onManageTags != null) {
                                            addDividerIfNeeded()
                                            DropdownMenuItem(
                                                text = { Text("标签管理") },
                                                onClick = {
                                                    onManageTags.invoke()
                                                    showMenuExpanded = false
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.AutoMirrored.Rounded.Label,
                                                        contentDescription = null,
                                                        tint = colorScheme.textSecondary
                                                    )
                                                }
                                            )
                                        }
                                        if (onRemoveFromAlbum != null) {
                                            addDividerIfNeeded()
                                            DropdownMenuItem(
                                                text = { Text("从专辑移除") },
                                                onClick = {
                                                    onRemoveFromAlbum.invoke()
                                                    showMenuExpanded = false
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Rounded.Delete,
                                                        contentDescription = null,
                                                        tint = colorScheme.textSecondary
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else null,
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

