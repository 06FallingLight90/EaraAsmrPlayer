package com.asmr.player.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.util.Formatting
import kotlinx.coroutines.delay

@Composable
internal fun DownloadTaskCard(
    task: DownloadTaskUi,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onPauseItem: (String) -> Unit,
    onResumeItem: (String) -> Unit,
    onRetryItem: (String) -> Unit,
    onDeleteItem: (String) -> Unit
) {
    val folderExpanded = remember(task.taskId) { mutableStateListOf<String>() }
    val treeEntries = remember(task.items, folderExpanded.toList()) {
        flattenDownloadTreeForUi(task.items, folderExpanded.toSet())
    }
    val hasUnknownTotalRunningItem = remember(task.items) {
        task.items.any { it.state == DownloadItemState.RUNNING && it.total <= 0 }
    }
    val colors = AsmrTheme.colorScheme
    val taskSummary by rememberTaskSummary(
        downloadedBytes = task.downloadedBytes,
        totalBytes = task.totalBytes,
        speed = task.speed,
        hasUnknownTotalRunning = hasUnknownTotalRunningItem,
        state = task.state
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(colors.surface.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TaskGroupHeader(
                expanded = expanded,
                title = task.title,
                subtitle = task.subtitle,
                summary = taskSummary,
                summaryColor = colors.textSecondary,
                summaryOnTitleLine = true,
                albumCover = task.albumCover,
                progress = task.progressFraction,
                progressIndeterminate = task.progressFraction == null && hasUnknownTotalRunningItem,
                reserveProgressSpace = true,
                onToggleExpanded = onToggleExpanded
            )

            if (expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    treeEntries.forEachIndexed { index, entry ->
                        when (entry) {
                            is DownloadTreeUiEntry.Folder -> {
                                DownloadFolderRow(
                                    title = entry.title,
                                    depth = entry.depth,
                                    expanded = folderExpanded.contains(entry.path),
                                    onToggle = {
                                        if (folderExpanded.contains(entry.path)) {
                                            folderExpanded.remove(entry.path)
                                        } else {
                                            folderExpanded.add(entry.path)
                                        }
                                    }
                                )
                            }

                            is DownloadTreeUiEntry.File -> {
                                DownloadFileRow(
                                    item = entry.item,
                                    depth = entry.depth,
                                    onPause = { onPauseItem(entry.item.workId) },
                                    onResume = { onResumeItem(entry.item.workId) },
                                    onRetry = { onRetryItem(entry.item.workId) },
                                    onDelete = { onDeleteItem(entry.item.workId) }
                                )
                            }
                        }
                        if (index < treeEntries.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                thickness = 0.5.dp,
                                color = colors.onSurfaceVariant.copy(alpha = 0.2f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private sealed class DownloadTreeUiEntry {
    abstract val path: String
    abstract val title: String
    abstract val depth: Int

    data class Folder(
        override val path: String,
        override val title: String,
        override val depth: Int
    ) : DownloadTreeUiEntry()

    data class File(
        override val path: String,
        override val title: String,
        override val depth: Int,
        val item: DownloadItemUi
    ) : DownloadTreeUiEntry()
}

private fun flattenDownloadTreeForUi(
    items: List<DownloadItemUi>,
    expanded: Set<String>
): List<DownloadTreeUiEntry> {
    data class Node(
        val name: String,
        val path: String,
        val children: MutableMap<String, Node> = linkedMapOf(),
        var item: DownloadItemUi? = null
    )

    val root = Node(name = "", path = "")
    items.forEach { item ->
        val rel = item.relativePath.replace('\\', '/').trim().trimStart('/')
        val segments = rel.split('/').filter { it.isNotBlank() }
        if (segments.isEmpty()) return@forEach
        var current = root
        segments.forEachIndexed { index, segment ->
            val isLeaf = index == segments.lastIndex
            val nextPath = if (current.path.isBlank()) segment else "${current.path}/$segment"
            val child = current.children.getOrPut(segment) { Node(name = segment, path = nextPath) }
            if (isLeaf) child.item = item
            current = child
        }
    }

    fun nodeKey(node: Node): String = node.name.lowercase()

    val out = mutableListOf<DownloadTreeUiEntry>()
    fun walk(node: Node, depth: Int) {
        val folders = node.children.values.filter { it.children.isNotEmpty() }.sortedBy(::nodeKey)
        val files = node.children.values.filter { it.children.isEmpty() && it.item != null }.sortedBy(::nodeKey)

        folders.forEach { folder ->
            out.add(DownloadTreeUiEntry.Folder(path = folder.path, title = folder.name, depth = depth))
            if (expanded.contains(folder.path)) walk(folder, depth + 1)
        }

        files.forEach { file ->
            val item = file.item ?: return@forEach
            out.add(DownloadTreeUiEntry.File(path = file.path, title = file.name, depth = depth, item = item))
        }
    }

    walk(root, 0)
    return out
}

@Composable
private fun DownloadFolderRow(
    title: String,
    depth: Int,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val colors = AsmrTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 16).dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onToggle
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.Folder,
            contentDescription = null,
            tint = colors.primary.copy(alpha = 0.8f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = if (expanded) Icons.Rounded.KeyboardArrowDown else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun DownloadFileRow(
    item: DownloadItemUi,
    depth: Int,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = AsmrTheme.colorScheme
    val percent: Int? = when {
        item.state == DownloadItemState.SUCCEEDED -> 100
        item.total > 0 -> ((item.downloaded * 100 / item.total).toInt().coerceIn(0, 100))
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .padding(start = 10.dp + (depth * 12).dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = pickFileIcon(item.fileName),
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(16.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = colors.textPrimary
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = downloadItemStateLabel(item.state),
                            style = MaterialTheme.typography.labelSmall,
                            color = when (item.state) {
                                DownloadItemState.SUCCEEDED -> colors.primary
                                DownloadItemState.FAILED -> colors.danger
                                DownloadItemState.RUNNING -> colors.primary
                                else -> colors.textSecondary
                            },
                            fontSize = 11.sp
                        )
                    }

                    if (percent != null) {
                        Text(
                            text = "$percent%",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = colors.textSecondary,
                            fontSize = 11.sp
                        )
                    }

                    formatSpeed(item.speed).takeIf { it.isNotBlank() }?.let { speed ->
                        Text(
                            text = speed,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                when {
                    percent != null && item.state != DownloadItemState.SUCCEEDED -> {
                        CompactProgressBar(
                            progress = percent / 100f,
                            trackColor = colors.surface.copy(alpha = 0.8f),
                            progressColor = colors.primary,
                            indeterminate = false
                        )
                    }

                    item.state == DownloadItemState.SUCCEEDED -> {
                        CompactProgressBar(
                            progress = 1f,
                            trackColor = colors.primary.copy(alpha = 0.2f),
                            progressColor = colors.primary.copy(alpha = 0.45f),
                            indeterminate = false
                        )
                    }

                    item.total <= 0 && item.state == DownloadItemState.RUNNING -> {
                        CompactProgressBar(
                            progress = null,
                            trackColor = colors.surface.copy(alpha = 0.8f),
                            progressColor = colors.primary,
                            indeterminate = true
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                when (item.state) {
                    DownloadItemState.RUNNING, DownloadItemState.ENQUEUED -> {
                        IconButton(
                            onClick = onPause,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Pause,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    DownloadItemState.PAUSED, DownloadItemState.CANCELLED -> {
                        IconButton(
                            onClick = onResume,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    DownloadItemState.FAILED -> {
                        IconButton(
                            onClick = onRetry,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = null,
                                tint = colors.danger,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    else -> Unit
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberTaskSummary(
    downloadedBytes: Long,
    totalBytes: Long?,
    speed: Long,
    hasUnknownTotalRunning: Boolean,
    state: DownloadItemState
) : androidx.compose.runtime.State<String> {
    val latestDownloadedBytes = rememberUpdatedState(downloadedBytes)
    val latestTotalBytes = rememberUpdatedState(totalBytes)
    val latestSpeed = rememberUpdatedState(speed)
    val latestHasUnknownTotalRunning = rememberUpdatedState(hasUnknownTotalRunning)
    val latestState = rememberUpdatedState(state)

    return produceState(
        initialValue = buildTaskSummaryText(
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            speed = speed,
            hasUnknownTotalRunning = hasUnknownTotalRunning,
            state = state
        )
    ) {
        while (true) {
            value = buildTaskSummaryText(
                downloadedBytes = latestDownloadedBytes.value,
                totalBytes = latestTotalBytes.value,
                speed = latestSpeed.value,
                hasUnknownTotalRunning = latestHasUnknownTotalRunning.value,
                state = latestState.value
            )
            delay(1_000)
        }
    }
}

private fun buildTaskSummaryText(
    downloadedBytes: Long,
    totalBytes: Long?,
    speed: Long,
    hasUnknownTotalRunning: Boolean,
    state: DownloadItemState
): String {
    val progressText = buildString {
        if (state == DownloadItemState.SUCCEEDED) {
            // Task is complete: "x / x" is redundant, show the final size only.
            append(Formatting.formatFileSize(totalBytes ?: downloadedBytes))
        } else {
            append(Formatting.formatFileSize(downloadedBytes))
            totalBytes?.takeIf { it > 0L }?.let {
                append(" / ")
                append(Formatting.formatFileSize(it))
            }
        }
    }
    val speedText = when {
        speed > 0L -> formatSpeed(speed)
        hasUnknownTotalRunning || state == DownloadItemState.RUNNING -> "下载中"
        else -> ""
    }
    return when {
        progressText.isBlank() -> speedText
        speedText.isBlank() -> progressText
        else -> "$progressText · $speedText"
    }
}


private fun downloadItemStateLabel(state: DownloadItemState): String {
    return when (state) {
        DownloadItemState.SUCCEEDED -> "已完成"
        DownloadItemState.FAILED -> "失败"
        DownloadItemState.RUNNING -> "下载中"
        DownloadItemState.PAUSED -> "已暂停"
        DownloadItemState.CANCELLED -> "已取消"
        DownloadItemState.ENQUEUED -> "等待中"
    }
}

@Composable
private fun pickFileIcon(fileName: String): androidx.compose.ui.graphics.vector.ImageVector {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when {
        ext in setOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "opus") -> Icons.Rounded.MusicNote
        ext in setOf("lrc", "srt", "vtt") -> Icons.Rounded.Subtitles
        ext in setOf("jpg", "jpeg", "png", "webp") -> Icons.Rounded.Image
        ext in setOf("mp4", "m4v", "webm", "mkv", "mov") -> Icons.Rounded.Movie
        else -> Icons.AutoMirrored.Rounded.InsertDriveFile
    }
}

private fun formatSpeed(bytesPerSec: Long): String {
    if (bytesPerSec <= 0) return ""
    val kb = bytesPerSec / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> String.format("%.1f MB/s", mb)
        kb >= 1.0 -> String.format("%.1f KB/s", kb)
        else -> "$bytesPerSec B/s"
    }
}

@Composable
internal fun TaskProgressMeta(
    progressFraction: Float?,
    hasUnknownTotalRunning: Boolean,
    state: DownloadItemState,
    emphasizeProgress: Boolean = false
) {
    val colors = AsmrTheme.colorScheme
    val text = when {
        progressFraction != null -> "${(progressFraction * 100).toInt()}%"
        hasUnknownTotalRunning -> "下载中"
        else -> downloadItemStateLabel(state)
    }
    val color = when (state) {
        DownloadItemState.FAILED -> colors.danger
        DownloadItemState.RUNNING, DownloadItemState.ENQUEUED, DownloadItemState.SUCCEEDED -> if (emphasizeProgress) colors.textSecondary else colors.primary
        else -> colors.textSecondary
    }

    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
        color = color,
        maxLines = 1
    )
}
