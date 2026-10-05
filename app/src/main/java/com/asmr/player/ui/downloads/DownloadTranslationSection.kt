package com.asmr.player.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asmr.player.subtitle.SubtitleItemState
import com.asmr.player.subtitle.SubtitleTaskItemUi
import com.asmr.player.subtitle.SubtitleTaskMode
import com.asmr.player.subtitle.SubtitleTaskUi
import com.asmr.player.subtitle.normalizedSubtitleAlbumKey
import com.asmr.player.ui.common.list.LocalBottomOverlayPadding
import com.asmr.player.ui.common.list.rememberCalmScrollableFlingBehavior
import com.asmr.player.ui.theme.AsmrTheme

private data class TranslationTaskGroupUi(
    val rjCode: String,
    val title: String,
    val albumCover: TaskAlbumCoverUi,
    val subtitles: List<TranslationSubtitleUi>,
    val tasks: List<TranslationTaskUi>,
    val isPolishing: Boolean = false
)

internal data class TranslationTaskUi(
    val itemId: String,
    val taskId: String,
    val createdAtMillis: Long,
    val trackId: Long,
    val rjCode: String,
    val title: String,
    val state: String,
    val progress: Float?,
    val progressLabel: String,
    val completedLines: Int,
    val totalLines: Int,
    val stage: String,
    val message: String
)

private fun TaskAlbumCoverUi.hasImageSource(): Boolean =
    coverThumbPath.isNotBlank() || coverPath.isNotBlank() || coverUrl.isNotBlank()
@Composable
internal fun TranslationManagementContent(
    normalizedQuery: String,
    listState: androidx.compose.foundation.lazy.LazyListState,
    subtitleGroups: List<TranslationSubtitleGroupUi>,
    subtitleTasks: List<SubtitleTaskUi>,
    polishingRjCodes: Set<String>,
    loadAlbumCovers: suspend (List<Long>) -> Map<Long, TaskAlbumCoverUi>,
    revealedRj: String?,
    closeController: SwipeRevealCloseController,
    onRevealedRjChange: (String?) -> Unit,
    onRevealedBoundsChanged: (Rect) -> Unit,
    onDeleteSubtitle: (trackId: Long, title: String) -> Unit,
    onDeleteSubtitleGroup: (rjCode: String, trackIds: List<Long>) -> Unit,
    onRetrySubtitle: (trackId: Long, title: String) -> Unit,
    onPolishAlbum: (rjCode: String) -> Unit,
    onPauseItem: (String) -> Unit,
    onResumeItem: (String) -> Unit,
    onCancelItem: (String) -> Unit,
    onRetryItem: (String) -> Unit,
    onPauseTask: (String) -> Unit,
    onResumeTask: (String) -> Unit,
    onCancelTask: (String) -> Unit
) {
    val displayedTasks = remember(subtitleTasks) {
        subtitleTasks.flatMap { task ->
            task.items.map { item -> item.toTranslationTaskUi(task) }
        }
    }
    val taskTrackIds = remember(displayedTasks) {
        displayedTasks.map(TranslationTaskUi::trackId).distinct()
    }
    val taskAlbumCovers by produceState<Map<Long, TaskAlbumCoverUi>>(
        initialValue = emptyMap(),
        key1 = taskTrackIds
    ) {
        value = loadAlbumCovers(taskTrackIds)
    }
    val groups = remember(
        displayedTasks,
        subtitleGroups,
        taskAlbumCovers,
        polishingRjCodes,
        normalizedQuery
    ) {
        val tasksByRj = displayedTasks
            .sortedWith(
                compareBy<TranslationTaskUi> { it.state.translationTaskSortPriority() }
                    .thenByDescending(TranslationTaskUi::createdAtMillis)
            )
            .groupBy { it.rjCode }
        val subtitleGroupsByRj = subtitleGroups.associateBy(TranslationSubtitleGroupUi::rjCode)
        (tasksByRj.keys + subtitleGroupsByRj.keys)
            .asSequence()
            .filter { rjCode ->
                normalizedQuery.isBlank() || rjCode.equals(normalizedQuery, ignoreCase = true)
            }
            .sortedWith(compareBy<String> { it == "未知作品编号" }.thenBy { it.lowercase() })
            .map { rjCode ->
                val subtitleGroup = subtitleGroupsByRj[rjCode]
                val tasks = tasksByRj[rjCode].orEmpty()
                val albumCover = sequenceOf(
                    subtitleGroup?.albumCover,
                    tasks.asSequence()
                        .mapNotNull { task -> taskAlbumCovers[task.trackId] }
                        .firstOrNull { it.hasImageSource() }
                ).filterNotNull()
                    .firstOrNull { it.hasImageSource() }
                    ?: TaskAlbumCoverUi()
                TranslationTaskGroupUi(
                    rjCode = rjCode,
                    title = subtitleGroup?.title?.takeIf { it.isNotBlank() }
                        ?: tasks.firstOrNull { it.title.isNotBlank() }?.title.orEmpty(),
                    albumCover = albumCover,
                    subtitles = subtitleGroup?.subtitles.orEmpty(),
                    tasks = tasks,
                    isPolishing = polishingRjCodes.contains(rjCode.normalizedSubtitleAlbumKey())
                )
            }
            .filter { it.subtitles.isNotEmpty() || it.tasks.isNotEmpty() }
            .toList()
    }
    val expandedGroups = remember { mutableStateListOf<String>() }

    if (groups.isEmpty()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (normalizedQuery.isBlank()) "暂无翻译任务" else "未找到任务：$normalizedQuery",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        flingBehavior = rememberCalmScrollableFlingBehavior(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(
            top = 4.dp,
            bottom = LocalBottomOverlayPadding.current + 6.dp
        )
    ) {
        items(groups, key = { it.rjCode }) { group ->
            val colors = AsmrTheme.colorScheme
            val expanded = expandedGroups.contains(group.rjCode)
            val activeTask = group.tasks.firstOrNull { it.state.isActivelyRunning() }
            val controlledTask = activeTask ?: group.tasks.firstOrNull()
            // 操作列：暂停/继续、润色、（有任务时）取消；（只有字幕时）润色、删除
            val actionCount = if (controlledTask == null) 2 else 3
            val groupActionsEnabled = !group.isPolishing
            SwipeRevealActionsBox(
                modifier = Modifier.fillMaxWidth(),
                revealed = revealedRj == group.rjCode,
                enabled = !expanded,
                closeController = closeController,
                onRevealedBoundsChanged = onRevealedBoundsChanged,
                onRevealedChange = { open ->
                    onRevealedRjChange(
                        when {
                            open -> group.rjCode
                            revealedRj == group.rjCode -> null
                            else -> revealedRj
                        }
                    )
                },
                actionWidth = SwipeActionButtonWidth * actionCount,
                actions = {
                    controlledTask?.let { task ->
                        when (task.state) {
                            SubtitleItemState.PAUSED, SubtitleItemState.INTERRUPTED, SubtitleItemState.FAILED -> {
                                SwipeRevealAction(
                                    backgroundColor = colors.primary,
                                    tint = colors.onPrimary,
                                    icon = Icons.Rounded.PlayArrow,
                                    contentDescription = "继续字幕任务",
                                    enabled = groupActionsEnabled,
                                    onClick = { onResumeTask(task.taskId) }
                                )
                            }
                            else -> {
                                SwipeRevealAction(
                                    backgroundColor = colors.primary,
                                    tint = colors.onPrimary,
                                    icon = Icons.Rounded.Pause,
                                    contentDescription = "暂停字幕任务",
                                    enabled = groupActionsEnabled,
                                    onClick = { onPauseTask(task.taskId) }
                                )
                            }
                        }
                    }
                    SwipeRevealAction(
                        backgroundColor = colors.primaryContainer,
                        tint = colors.onPrimaryContainer,
                        icon = Icons.Rounded.AutoAwesome,
                        contentDescription = if (group.isPolishing) "润色中" else "润色该作品字幕",
                        enabled = groupActionsEnabled,
                        onClick = {
                            onRevealedRjChange(null)
                            onPolishAlbum(group.rjCode)
                        }
                    )
                    if (controlledTask != null) {
                        SwipeRevealAction(
                            backgroundColor = colors.danger,
                            tint = Color.White,
                            icon = Icons.Rounded.Close,
                            contentDescription = "取消字幕任务",
                            enabled = groupActionsEnabled,
                            onClick = { onCancelTask(controlledTask.taskId) }
                        )
                    } else {
                        SwipeRevealAction(
                            backgroundColor = colors.danger,
                            tint = Color.White,
                            icon = Icons.Rounded.Close,
                            contentDescription = "删除该作品的全部字幕",
                            enabled = groupActionsEnabled,
                            onClick = {
                                onDeleteSubtitleGroup(
                                    group.rjCode,
                                    group.subtitles.map(TranslationSubtitleUi::trackId)
                                )
                            }
                        )
                    }
                }
            ) {
                TranslationTaskGroupCard(
                    group = group,
                    expanded = expanded,
                    onToggleExpanded = {
                        if (expanded) {
                            expandedGroups.remove(group.rjCode)
                        } else {
                            onRevealedRjChange(null)
                            expandedGroups.add(group.rjCode)
                        }
                    },
                    onDeleteSubtitle = onDeleteSubtitle,
                    onRetrySubtitle = onRetrySubtitle,
                    onPauseItem = onPauseItem,
                    onResumeItem = onResumeItem,
                    onCancelItem = onCancelItem,
                    onRetryItem = onRetryItem
                )
            }
        }
    }
}

@Composable
private fun TranslationTaskGroupCard(
    group: TranslationTaskGroupUi,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onDeleteSubtitle: (trackId: Long, title: String) -> Unit,
    onRetrySubtitle: (trackId: Long, title: String) -> Unit,
    onPauseItem: (String) -> Unit,
    onResumeItem: (String) -> Unit,
    onCancelItem: (String) -> Unit,
    onRetryItem: (String) -> Unit
) {
    val colors = AsmrTheme.colorScheme
    val isPolishing = group.isPolishing
    val activeTask = remember(group.tasks) { group.tasks.firstOrNull { it.state.isActivelyRunning() } }
    val controlledTask = remember(group.tasks, activeTask) { activeTask ?: group.tasks.firstOrNull() }
    val summary = remember(group.subtitles, group.tasks, activeTask, isPolishing) {
        when {
            isPolishing -> "整体润色中"
            activeTask != null -> activeTask.stage
            group.subtitles.isNotEmpty() -> "${group.subtitles.size} 个字幕"
            else -> "暂无字幕"
        }
    }
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
                title = group.rjCode,
                subtitle = group.title,
                summary = summary,
                summaryColor = if (activeTask != null || isPolishing) colors.primary else colors.textSecondary,
                albumCover = group.albumCover,
                progress = if (isPolishing) null else activeTask?.progress,
                progressIndeterminate = false,
                reserveProgressSpace = !isPolishing && controlledTask != null,
                summaryOnTitleLine = true,
                onToggleExpanded = onToggleExpanded
            )

            if (expanded) {
                val taskByTrackId = group.tasks
                    .associateBy(TranslationTaskUi::trackId)
                    .toMap()
                val subtitleTrackIds = group.subtitles.mapTo(mutableSetOf(), TranslationSubtitleUi::trackId)
                val rows = buildList {
                    group.subtitles.forEach { subtitle ->
                        add(TranslationRowUi.Subtitle(subtitle, taskByTrackId[subtitle.trackId]))
                    }
                    group.tasks
                        .filter { task -> task.trackId !in subtitleTrackIds }
                        .forEach { task -> add(TranslationRowUi.Task(task)) }
                }
                val actionsEnabled = !isPolishing
                rows.forEachIndexed { index, row ->
                    when (row) {
                        is TranslationRowUi.Subtitle -> TranslationSubtitleRow(
                            subtitle = row.subtitle,
                            task = row.task,
                            actionsEnabled = actionsEnabled,
                            retryEnabled = true,
                            onDelete = { onDeleteSubtitle(row.subtitle.trackId, row.subtitle.title) },
                            onRetry = { onRetrySubtitle(row.subtitle.trackId, row.subtitle.title) },
                            onPause = row.task?.takeIf { it.state.isActivelyRunning() }
                                ?.let { { onPauseItem(it.itemId) } },
                            onResume = row.task?.takeIf { it.state in setOf(SubtitleItemState.PAUSED, SubtitleItemState.INTERRUPTED) }
                                ?.let { { onResumeItem(it.itemId) } },
                            onCancel = row.task?.let { { onCancelItem(it.itemId) } },
                            onRetryTask = row.task?.takeIf { it.state == SubtitleItemState.FAILED }
                                ?.let { { onRetryItem(it.itemId) } }
                        )

                        is TranslationRowUi.Task -> TranslationTaskRow(
                            task = row.task,
                            actionsEnabled = actionsEnabled,
                            retryEnabled = true,
                            onPause = { onPauseItem(row.task.itemId) },
                            onResume = { onResumeItem(row.task.itemId) },
                            onCancel = { onCancelItem(row.task.itemId) },
                            onRetry = { onRetryItem(row.task.itemId) }
                        )
                    }
                    if (index < rows.lastIndex) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = colors.onSurfaceVariant.copy(alpha = 0.2f)
                        )
                    }
                }
            }
        }
        if (isPolishing) {
            FinalPolishBottomProgress(
                trackColor = colors.primary.copy(alpha = 0.14f),
                progressColor = colors.primary
            )
        }
    }
}

internal const val FINAL_POLISH_PROGRESS_TAG = "final_polish_bottom_progress"

@Composable
internal fun BoxScope.FinalPolishBottomProgress(
    trackColor: Color,
    progressColor: Color
) {
    Box(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            .height(1.dp)
            .testTag(FINAL_POLISH_PROGRESS_TAG)
    ) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxSize(),
            color = progressColor,
            trackColor = trackColor
        )
    }
}


@Composable
private fun TranslationSubtitleRow(
    subtitle: TranslationSubtitleUi,
    task: TranslationTaskUi?,
    actionsEnabled: Boolean = true,
    retryEnabled: Boolean = actionsEnabled,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onPause: (() -> Unit)?,
    onResume: (() -> Unit)?,
    onCancel: (() -> Unit)?,
    onRetryTask: (() -> Unit)?
) {
    val colors = AsmrTheme.colorScheme
    val progressText = task?.let(::translationTaskProgressText)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            Icons.Rounded.Subtitles,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(20.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = subtitle.title,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = task?.message?.ifBlank { task.stage }
                    ?: "${subtitle.subtitleCount} 行字幕",
                style = MaterialTheme.typography.labelSmall,
                color = if (task?.state == SubtitleItemState.FAILED) colors.danger else colors.textTertiary,
                maxLines = if (task?.state == SubtitleItemState.FAILED) 2 else 1,
                overflow = TextOverflow.Ellipsis
            )
            if (task != null) {
                StableProgressSlot(
                    progress = task.progress,
                    visible = task.progress != null,
                    trackColor = colors.surface.copy(alpha = 0.8f),
                    progressColor = colors.primary,
                    indeterminate = false
                )
            }
        }
        if (progressText != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (task.state in setOf(SubtitleItemState.TRANSCRIBING, SubtitleItemState.TRANSLATING) && task.progress == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.5.dp,
                        color = colors.primary,
                        trackColor = colors.surface.copy(alpha = 0.8f)
                    )
                }
                Text(
                    text = progressText,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = when (task.state) {
                        SubtitleItemState.FAILED -> colors.danger
                        else -> colors.textSecondary
                    },
                    maxLines = 1
                )
            }
        }
        if (onPause != null) {
            IconButton(onClick = onPause, enabled = actionsEnabled, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Pause, "暂停字幕任务", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
            }
        }
        if (onResume != null) {
            IconButton(onClick = onResume, enabled = actionsEnabled, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.PlayArrow, "继续字幕任务", tint = colors.primary, modifier = Modifier.size(16.dp))
            }
        }
        if (onRetryTask != null) {
            IconButton(onClick = onRetryTask, enabled = retryEnabled, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Refresh, "重试字幕任务", tint = colors.primary, modifier = Modifier.size(16.dp))
            }
        }
        if (onCancel != null) {
            IconButton(
                onClick = onCancel,
                enabled = actionsEnabled,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "取消翻译",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        if (task == null) {
            IconButton(
                onClick = onRetry,
                enabled = retryEnabled,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = "重新翻译",
                    tint = colors.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
            IconButton(
                onClick = onDelete,
                enabled = actionsEnabled,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "删除字幕",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
internal fun TranslationTaskRow(
    task: TranslationTaskUi,
    actionsEnabled: Boolean = true,
    retryEnabled: Boolean = actionsEnabled,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    val colors = AsmrTheme.colorScheme
    val progressText = translationTaskProgressText(task)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("translation_task_row_${task.itemId}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            Icons.Rounded.Subtitles,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(20.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = progressText,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = when (task.state) {
                        SubtitleItemState.FAILED -> colors.danger
                        else -> colors.textSecondary
                    },
                    maxLines = 1
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (task.state in setOf(SubtitleItemState.TRANSCRIBING, SubtitleItemState.TRANSLATING) && task.progress == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.5.dp,
                        color = colors.primary,
                        trackColor = colors.surface.copy(alpha = 0.8f)
                    )
                }
                Text(
                    text = task.message.ifBlank { task.stage },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (task.state == SubtitleItemState.FAILED) colors.danger else colors.textTertiary,
                    maxLines = if (task.state == SubtitleItemState.FAILED) 2 else 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            StableProgressSlot(
                progress = task.progress,
                visible = task.progress != null,
                trackColor = colors.surface.copy(alpha = 0.8f),
                progressColor = colors.primary,
                indeterminate = false
            )
        }

        when (task.state) {
            SubtitleItemState.PAUSED, SubtitleItemState.INTERRUPTED -> IconButton(
                onClick = onResume,
                enabled = actionsEnabled,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Rounded.PlayArrow, "继续字幕任务", tint = colors.primary, modifier = Modifier.size(16.dp))
            }
            SubtitleItemState.FAILED -> IconButton(
                onClick = onRetry,
                enabled = retryEnabled,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Rounded.Refresh, "重试字幕任务", tint = colors.primary, modifier = Modifier.size(16.dp))
            }
            else -> IconButton(
                onClick = onPause,
                enabled = actionsEnabled,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Rounded.Pause, "暂停字幕任务", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
            }
        }
        run {
            IconButton(
                onClick = onCancel,
                enabled = actionsEnabled,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "取消翻译",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private sealed class TranslationRowUi {
    data class Subtitle(
        val subtitle: TranslationSubtitleUi,
        val task: TranslationTaskUi?
    ) : TranslationRowUi()

    data class Task(
        val task: TranslationTaskUi
    ) : TranslationRowUi()
}

private fun translationTaskProgressText(task: TranslationTaskUi): String {
    return when {
        task.progressLabel.isNotBlank() -> task.progressLabel
        task.totalLines > 0 -> "${task.completedLines}/${task.totalLines} 行"
        else -> translationStateLabel(task.state)
    }
}

internal fun SubtitleTaskItemUi.toTranslationTaskUi(task: SubtitleTaskUi): TranslationTaskUi {
    val usesTranscriptionProgress = translationTotal <= 0 && mode == SubtitleTaskMode.GENERATED
    val fraction = when {
        translationTotal > 0 -> translationCursor.toFloat() / translationTotal.toFloat()
        usesTranscriptionProgress -> transcriptionProgress / 100f
        else -> null
    }?.coerceIn(0f, 1f)
    return TranslationTaskUi(
        itemId = id,
        taskId = task.id,
        createdAtMillis = createdAt,
        trackId = trackId,
        rjCode = task.rjCode,
        title = title,
        state = state,
        progress = fraction,
        progressLabel = when {
            usesTranscriptionProgress -> "$transcriptionProgress%"
            translationTotal > 0 -> "已确认 $translationCursor/$translationTotal"
            else -> ""
        },
        completedLines = translationCursor,
        totalLines = translationTotal,
        stage = subtitleItemStage(this),
        message = errorMessage.takeIf { state == SubtitleItemState.FAILED }.orEmpty()
    )
}

internal fun subtitleItemStage(item: SubtitleTaskItemUi): String = when (item.state) {
    SubtitleItemState.QUEUED_TRANSCRIPTION -> "排队转录"
    SubtitleItemState.TRANSCRIBING -> "转录中"
    SubtitleItemState.QUEUED_TRANSLATION -> "日文已生成，等待翻译"
    SubtitleItemState.WAITING_SLOT -> "等待翻译槽位"
    SubtitleItemState.WAITING_NETWORK -> "等待网络"
    SubtitleItemState.TRANSLATING -> "AI 正在确认字幕"
    SubtitleItemState.RETRY_WAIT -> "重试等待 ${item.attempt + 1}/4${item.errorMessage.asStageReason()}"
    SubtitleItemState.PAUSE_REQUESTED -> "暂停中"
    SubtitleItemState.PAUSED -> "已暂停"
    SubtitleItemState.INTERRUPTED -> "异常中断${item.errorMessage.asStageReason()}"
    SubtitleItemState.CANCEL_REQUESTED -> "取消中"
    SubtitleItemState.FAILED -> "失败"
    SubtitleItemState.SUCCEEDED -> "已完成"
    SubtitleItemState.CANCELED -> "已取消"
    else -> item.state
}

private fun String.asStageReason(): String = trim().takeIf(String::isNotEmpty)?.let { "：$it" }.orEmpty()

private fun translationStateLabel(state: String): String = when (state) {
    SubtitleItemState.PAUSED -> "已暂停"
    SubtitleItemState.INTERRUPTED -> "异常中断"
    SubtitleItemState.FAILED -> "失败"
    else -> "处理中"
}

internal fun String.isActivelyRunning(): Boolean = this !in setOf(
    SubtitleItemState.PAUSED,
    SubtitleItemState.INTERRUPTED,
    SubtitleItemState.FAILED,
    SubtitleItemState.SUCCEEDED,
    SubtitleItemState.CANCELED
)

private fun String.translationTaskSortPriority(): Int = when (this) {
    SubtitleItemState.TRANSCRIBING, SubtitleItemState.TRANSLATING -> 0
    SubtitleItemState.QUEUED_TRANSCRIPTION,
    SubtitleItemState.QUEUED_TRANSLATION,
    SubtitleItemState.WAITING_SLOT,
    SubtitleItemState.WAITING_NETWORK,
    SubtitleItemState.RETRY_WAIT -> 1
    SubtitleItemState.PAUSE_REQUESTED, SubtitleItemState.CANCEL_REQUESTED -> 2
    SubtitleItemState.PAUSED, SubtitleItemState.INTERRUPTED -> 3
    SubtitleItemState.FAILED -> 4
    else -> 5
}
