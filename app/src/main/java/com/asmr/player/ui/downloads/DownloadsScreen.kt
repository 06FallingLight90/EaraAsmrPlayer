package com.asmr.player.ui.downloads

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asmr.player.subtitle.SubtitleTaskUi
import com.asmr.player.ui.common.core.isCompactWidth
import com.asmr.player.ui.common.dialog.FlatActionDialog
import com.asmr.player.ui.common.dialog.FlatDialogAction
import com.asmr.player.ui.common.dialog.FlatDialogActionTone
import com.asmr.player.ui.common.list.LocalBottomOverlayPadding
import com.asmr.player.ui.common.list.rememberCalmScrollableFlingBehavior
import com.asmr.player.ui.common.list.smoothScrollToTop
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.util.DlsiteWorkNo

private val DownloadsPageHorizontalPadding = 8.dp

internal fun normalizeDownloadWorkNoQuery(input: String): String {
    val raw = input.trim()
    if (raw.isBlank()) return ""
    return DlsiteWorkNo.extractWorkNo(raw)
        .ifBlank { if (raw.all(Char::isDigit)) "RJ$raw" else raw }
}

@Composable
fun DownloadsScreen(
    windowSizeClass: WindowSizeClass,
    isActive: Boolean = true,
    scrollToTopSignal: Long = 0L,
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val subtitleTasks by viewModel.subtitleTasks.collectAsStateWithLifecycle()
    val downloadDestination by viewModel.downloadDestination.collectAsStateWithLifecycle()
    val polishingRjCodes by viewModel.polishingRjCodes.collectAsStateWithLifecycle()
    val activeDownloadFileCount = remember(tasks) { countActiveDownloadFiles(tasks) }
    val activeTranslationTaskCount = remember(subtitleTasks) { countActiveSubtitleTaskItems(subtitleTasks) }
    val expandedTasks = remember { mutableStateListOf<Long>() }
    var rjQuery by rememberSaveable { mutableStateOf("") }
    var managementMode by rememberSaveable { mutableStateOf(DownloadManagementMode.Downloads) }
    var pendingDelete by remember { mutableStateOf<PendingDeleteAction?>(null) }
    var revealedDownloadTaskId by remember { mutableStateOf<Long?>(null) }
    var revealedTranslationRj by remember { mutableStateOf<String?>(null) }
    var revealedTaskBoundsInRoot by remember { mutableStateOf<Rect?>(null) }
    var pagePositionInRoot by remember { mutableStateOf(Offset.Zero) }
    val swipeRevealCloseController = remember { SwipeRevealCloseController() }
    val listState = rememberLazyListState()

    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal == 0L) return@LaunchedEffect
        listState.smoothScrollToTop()
    }
    LaunchedEffect(isActive) {
        if (isActive) return@LaunchedEffect
        listState.stopScroll(MutatePriority.PreventUserInput)
    }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            revealedDownloadTaskId = null
            revealedTranslationRj = null
            revealedTaskBoundsInRoot = null
        }
    }
    LaunchedEffect(managementMode) {
        revealedDownloadTaskId = null
        revealedTranslationRj = null
        revealedTaskBoundsInRoot = null
    }

    val isCompact = windowSizeClass.widthSizeClass.isCompactWidth

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                pagePositionInRoot = coordinates.positionInRoot()
            }
            .pointerInput(
                revealedDownloadTaskId,
                revealedTranslationRj,
                revealedTaskBoundsInRoot,
                pagePositionInRoot
            ) {
                if (revealedDownloadTaskId == null && revealedTranslationRj == null) {
                    return@pointerInput
                }
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial
                    )
                    val downInRoot = down.position + pagePositionInRoot
                    if (revealedTaskBoundsInRoot?.contains(downInRoot) != true) {
                        swipeRevealCloseController.requestClose()
                        revealedDownloadTaskId = null
                        revealedTranslationRj = null
                        revealedTaskBoundsInRoot = null
                    }
                }
            },
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = if (isCompact) {
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = DownloadsPageHorizontalPadding, vertical = 10.dp)
            } else {
                Modifier
                    .fillMaxHeight()
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .padding(horizontal = DownloadsPageHorizontalPadding, vertical = 10.dp)
            },
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DownloadManagementModeTabs(
                selected = managementMode,
                activeDownloadFileCount = activeDownloadFileCount,
                activeTranslationTaskCount = activeTranslationTaskCount,
                onSelected = { managementMode = it }
            )

            OutlinedTextField(
                value = rjQuery,
                onValueChange = { rjQuery = it },
                label = { Text("作品编号精准搜索") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            val normalizedQuery = remember(rjQuery) {
                normalizeDownloadWorkNoQuery(rjQuery)
            }

            when (managementMode) {
                DownloadManagementMode.Downloads -> {
                    val shownTasks = remember(tasks, normalizedQuery) {
                        if (normalizedQuery.isBlank()) tasks
                        else tasks.filter { it.title.equals(normalizedQuery, ignoreCase = true) }
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
                        if (shownTasks.isEmpty()) {
                            item(key = "download_empty_state") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = if (normalizedQuery.isBlank()) "暂无下载任务" else "未找到任务：$normalizedQuery",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            items(shownTasks, key = { it.taskId }) { task ->
                                val colors = AsmrTheme.colorScheme
                                val expanded = expandedTasks.contains(task.taskId)
                                val hasFailedItems = task.items.any { it.state == DownloadItemState.FAILED }
                                val hasActiveItems = task.items.any {
                                    it.state == DownloadItemState.RUNNING || it.state == DownloadItemState.ENQUEUED
                                }
                                val hasPausedItems = task.items.any { it.state == DownloadItemState.PAUSED }
                                val actionCount = (if (hasFailedItems) 1 else 0) +
                                    (if (hasActiveItems || hasPausedItems) 1 else 0) + 1
                                SwipeRevealActionsBox(
                                    modifier = Modifier.fillMaxWidth(),
                                    revealed = revealedDownloadTaskId == task.taskId,
                                    enabled = !expanded,
                                    closeController = swipeRevealCloseController,
                                    onRevealedBoundsChanged = { revealedTaskBoundsInRoot = it },
                                    onRevealedChange = { open ->
                                        revealedDownloadTaskId = when {
                                            open -> task.taskId
                                            revealedDownloadTaskId == task.taskId -> null
                                            else -> revealedDownloadTaskId
                                        }
                                    },
                                    actionWidth = SwipeActionButtonWidth * actionCount,
                                    actions = {
                                        if (hasFailedItems) {
                                            SwipeRevealAction(
                                                backgroundColor = colors.surfaceVariant,
                                                tint = colors.danger,
                                                icon = Icons.Rounded.Refresh,
                                                contentDescription = "重试失败项",
                                                onClick = { viewModel.retryFailedInTask(task.taskId) }
                                            )
                                        }
                                        if (hasActiveItems) {
                                            SwipeRevealAction(
                                                backgroundColor = colors.primary,
                                                tint = colors.onPrimary,
                                                icon = Icons.Rounded.Pause,
                                                contentDescription = "暂停下载任务",
                                                onClick = { viewModel.pauseTask(task.taskId) }
                                            )
                                        } else if (hasPausedItems) {
                                            SwipeRevealAction(
                                                backgroundColor = colors.primary,
                                                tint = colors.onPrimary,
                                                icon = Icons.Rounded.PlayArrow,
                                                contentDescription = "继续下载任务",
                                                onClick = { viewModel.resumeTask(task.taskId) }
                                            )
                                        }
                                        SwipeRevealAction(
                                            backgroundColor = colors.danger,
                                            tint = Color.White,
                                            icon = Icons.Rounded.Close,
                                            contentDescription = "删除下载任务",
                                            onClick = { pendingDelete = PendingDeleteAction.Task(task.taskId) }
                                        )
                                    }
                                ) {
                                    DownloadTaskCard(
                                        task = task,
                                        expanded = expanded,
                                        onToggleExpanded = {
                                            if (expanded) {
                                                expandedTasks.remove(task.taskId)
                                            } else {
                                                revealedDownloadTaskId = null
                                                expandedTasks.add(task.taskId)
                                            }
                                        },
                                        onPauseItem = { viewModel.pauseItem(it) },
                                        onResumeItem = { viewModel.resumeItem(it) },
                                        onRetryItem = { viewModel.retryItem(it) },
                                        onDeleteItem = { pendingDelete = PendingDeleteAction.Item(workId = it) }
                                    )
                                }
                            }
                        }
                        item(key = "download_root") {
                            Text(
                                text = "下载目录：${downloadDestination.label} · ${downloadDestination.displayPath}",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 2.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                DownloadManagementMode.Translations -> {
                    // 「翻译任务」数据只在切到该 Tab 时才订阅：进入页面默认落在
                    // 「下载任务」，避免转场期间再触发一次 Room 查询并整页重组。
                    val translationSubtitleGroups by viewModel.translationSubtitleGroups.collectAsStateWithLifecycle()
                    TranslationManagementContent(
                        normalizedQuery = normalizedQuery,
                        listState = listState,
                        subtitleGroups = translationSubtitleGroups,
                        subtitleTasks = subtitleTasks,
                        polishingRjCodes = polishingRjCodes,
                        loadAlbumCovers = viewModel::loadTranslationAlbumCovers,
                        revealedRj = revealedTranslationRj,
                        closeController = swipeRevealCloseController,
                        onRevealedRjChange = { revealedTranslationRj = it },
                        onRevealedBoundsChanged = { revealedTaskBoundsInRoot = it },
                        onDeleteSubtitle = { trackId, title ->
                            pendingDelete = PendingDeleteAction.Subtitle(trackId = trackId, title = title)
                        },
                        onDeleteSubtitleGroup = { rjCode, trackIds ->
                            revealedTranslationRj = null
                            pendingDelete = PendingDeleteAction.SubtitleGroup(
                                rjCode = rjCode,
                                trackIds = trackIds
                            )
                        },
                        onRetrySubtitle = viewModel::retrySubtitleTranslation,
                        onPolishAlbum = viewModel::polishSubtitleAlbum,
                        onPauseItem = viewModel::pauseSubtitleItem,
                        onResumeItem = viewModel::resumeSubtitleItem,
                        onCancelItem = viewModel::cancelSubtitleItem,
                        onRetryItem = viewModel::retrySubtitleItem,
                        onPauseTask = viewModel::pauseSubtitleTask,
                        onResumeTask = viewModel::resumeSubtitleTask,
                        onCancelTask = viewModel::cancelSubtitleTask
                    )
                }
            }
        }

        val action = pendingDelete
        if (action != null) {
            val resolved = remember(action, tasks) {
                when (action) {
                    is PendingDeleteAction.Task -> {
                        val task = tasks.firstOrNull { it.taskId == action.taskId } ?: return@remember null
                        ResolvedDeleteText(
                            message = "将物理删除“${task.title}”目录下的文件，且不可恢复。"
                        )
                    }

                    is PendingDeleteAction.Item -> {
                        val item = tasks.asSequence()
                            .flatMap { it.items.asSequence() }
                            .firstOrNull { it.workId == action.workId } ?: return@remember null
                        ResolvedDeleteText(
                            message = "将物理删除文件“${item.fileName}”，且不可恢复。"
                        )
                    }

                    is PendingDeleteAction.Subtitle -> ResolvedDeleteText(
                        message = "将删除字幕“${action.title}”，播放时不会再显示该字幕。"
                    )

                    is PendingDeleteAction.SubtitleGroup -> ResolvedDeleteText(
                        message = "将删除“${action.rjCode}”下的 ${action.trackIds.size} 个字幕，播放时不会再显示这些字幕。"
                    )
                }
            }

            if (resolved != null) {
                FlatActionDialog(
                    onDismissRequest = { pendingDelete = null },
                    message = resolved.message,
                    actions = listOf(
                        FlatDialogAction("取消", onClick = { pendingDelete = null }),
                        FlatDialogAction(
                            text = "删除",
                            tone = FlatDialogActionTone.Danger,
                            onClick = {
                                pendingDelete = null
                                when (action) {
                                    is PendingDeleteAction.Task -> viewModel.deleteTask(action.taskId)
                                    is PendingDeleteAction.Item -> viewModel.deleteItem(action.workId)
                                    is PendingDeleteAction.Subtitle -> viewModel.deleteSubtitleTrack(action.trackId)
                                    is PendingDeleteAction.SubtitleGroup -> viewModel.deleteSubtitleTracks(action.trackIds)
                                }
                            }
                        )
                    )
                )
            } else {
                pendingDelete = null
            }
        }
    }
}

private sealed class PendingDeleteAction {
    data class Task(val taskId: Long) : PendingDeleteAction()
    data class Item(val workId: String) : PendingDeleteAction()
    data class Subtitle(val trackId: Long, val title: String) : PendingDeleteAction()
    data class SubtitleGroup(val rjCode: String, val trackIds: List<Long>) : PendingDeleteAction()
}

private data class ResolvedDeleteText(
    val message: String
)

private enum class DownloadManagementMode(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Downloads("下载任务", Icons.Rounded.Download),
    Translations("翻译任务", Icons.Rounded.Translate)
}


@Composable
private fun DownloadManagementModeTabs(
    selected: DownloadManagementMode,
    activeDownloadFileCount: Int,
    activeTranslationTaskCount: Int,
    onSelected: (DownloadManagementMode) -> Unit
) {
    val colors = AsmrTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface.copy(alpha = 0.55f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        DownloadManagementMode.entries.forEach { mode ->
            val isSelected = selected == mode
            val activeTaskCount = when (mode) {
                DownloadManagementMode.Downloads -> activeDownloadFileCount
                DownloadManagementMode.Translations -> activeTranslationTaskCount
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) colors.primarySoft else Color.Transparent)
                    .clickable { onSelected(mode) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = mode.icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isSelected) colors.primaryStrong else colors.textSecondary
                    )
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isSelected) colors.primaryStrong else colors.textSecondary
                    )
                    if (activeTaskCount > 0) {
                        Badge(
                            containerColor = colors.primaryStrong,
                            contentColor = colors.onPrimary
                        ) {
                            Text(activeTaskCount.toString())
                        }
                    }
                }
            }
        }
    }
}

internal fun countActiveDownloadFiles(tasks: List<DownloadTaskUi>): Int {
    return tasks.sumOf { task ->
        task.items.count { item ->
            item.state == DownloadItemState.RUNNING || item.state == DownloadItemState.ENQUEUED
        }
    }
}

internal fun countActiveSubtitleTaskItems(tasks: List<SubtitleTaskUi>): Int {
    return tasks.sumOf { task ->
        task.items.count { item -> item.state.isActivelyRunning() }
    }
}

