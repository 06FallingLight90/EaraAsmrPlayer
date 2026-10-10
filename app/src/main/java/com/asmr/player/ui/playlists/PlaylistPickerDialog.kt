package com.asmr.player.ui.playlists

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import com.asmr.player.domain.model.BatchAddTarget
import com.asmr.player.ui.common.dialog.BatchAddGroupTarget
import com.asmr.player.ui.common.dialog.CollectionPickerContent
import com.asmr.player.ui.common.dialog.CollectionPickerRow

/**
 * 批量加入选择器（T8 双目标化）：默认歌单单 tab（nowPlaying 单项加入语义保持不变）；
 * 宿主注入 [groupTarget] 时升级为「歌单 | 合集」双 tab 分段，初始 tab 由
 * [defaultTarget] 指定。合集 tab 经 BatchAddGroupTarget 端口取列表并按 track 粒度
 * （mediaId=trackPath）批量挂载，summary 反馈由端口实现侧负责。
 */
@Composable
fun PlaylistPickerScreen(
    windowSizeClass: WindowSizeClass,
    items: List<MediaItem>,
    onBack: () -> Unit,
    embeddedInDialog: Boolean = false,
    viewModel: PlaylistsViewModel = hiltViewModel(),
    groupTarget: BatchAddGroupTarget? = null,
    defaultTarget: BatchAddTarget = BatchAddTarget.PLAYLIST
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val userPlaylists = remember(playlists) { playlists.filter { it.category == "user" } }
    val groups = groupTarget?.groups?.collectAsStateWithLifecycle()?.value.orEmpty()
    val screenActive = remember { mutableStateOf(true) }
    var activeTarget by rememberSaveable { mutableStateOf(defaultTarget) }
    var createName by rememberSaveable { mutableStateOf("") }
    var addingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val isAdding = addingId != null

    BackHandler(enabled = isAdding) {}
    DisposableEffect(Unit) {
        onDispose { screenActive.value = false }
    }

    val dualTarget = groupTarget != null
    CollectionPickerContent(
        windowSizeClass = windowSizeClass,
        title = when {
            !dualTarget -> "选择列表"
            activeTarget == BatchAddTarget.PLAYLIST -> "加入歌单"
            else -> "加入合集"
        },
        createPlaceholder = if (dualTarget && activeTarget == BatchAddTarget.GROUP) {
            "新建分组名称"
        } else {
            "新建列表名称"
        },
        createName = createName,
        onCreateNameChange = { createName = it },
        onCreate = {
            val trimmed = createName.trim()
            if (trimmed.isNotBlank()) {
                if (dualTarget && activeTarget == BatchAddTarget.GROUP) {
                    groupTarget?.createGroup(trimmed)
                } else {
                    viewModel.createPlaylist(trimmed)
                }
                createName = ""
            }
        },
        onBack = onBack,
        embeddedInDialog = embeddedInDialog,
        isAdding = isAdding,
        isEmpty = if (dualTarget && activeTarget == BatchAddTarget.GROUP) {
            groups.isEmpty()
        } else {
            userPlaylists.isEmpty()
        },
        emptyText = if (dualTarget && activeTarget == BatchAddTarget.GROUP) "暂无可选分组" else "暂无可选列表",
        selectionSummary = if (items.size > 1) "将添加 ${items.size} 项" else null,
        extraHeader = if (dualTarget) {
            {
                BatchAddTargetTabs(
                    selected = activeTarget,
                    enabled = !isAdding,
                    onSelect = { activeTarget = it }
                )
            }
        } else {
            null
        }
    ) {
        if (dualTarget && activeTarget == BatchAddTarget.GROUP) {
            items(groups, key = { it.id }) { group ->
                CollectionPickerRow(
                    name = group.name,
                    artworkUri = group.firstArtworkUri,
                    summary = "共 ${group.albumCount} 张专辑 · ${group.itemCount} 首",
                    enabled = !isAdding,
                    isAdding = addingId == group.id,
                    onClick = {
                        addingId = group.id
                        groupTarget?.addTracksToGroupInBackground(
                            groupId = group.id,
                            mediaIds = items.map { mediaIdOf(it) },
                            onComplete = {
                                if (screenActive.value) {
                                    addingId = null
                                    onBack()
                                }
                            },
                            onFailure = {
                                if (screenActive.value) {
                                    addingId = null
                                }
                            }
                        )
                    }
                )
            }
        } else {
            items(userPlaylists, key = { it.id }) { playlist ->
                CollectionPickerRow(
                    name = playlist.name,
                    artworkUri = playlist.firstArtworkUri,
                    summary = "共 ${playlist.itemCount} 首",
                    enabled = !isAdding,
                    isAdding = addingId == playlist.id,
                    onClick = {
                        addingId = playlist.id
                        viewModel.addItemsToPlaylistInBackground(
                            playlistId = playlist.id,
                            items = items,
                            onComplete = {
                                if (screenActive.value) {
                                    addingId = null
                                    onBack()
                                }
                            },
                            onFailure = {
                                if (screenActive.value) {
                                    addingId = null
                                }
                            }
                        )
                    }
                )
            }
        }
    }
}

/** 与 PlaylistRepository.addItemsToPlaylist 的 mediaId 推导同口径。 */
private fun mediaIdOf(item: MediaItem): String =
    item.mediaId.ifBlank { item.localConfiguration?.uri.toString().orEmpty() }.trim()

/** 歌单 | 合集 双 tab 分段（T8）：薄实现，FilterChip 承载选中态。 */
@Composable
private fun BatchAddTargetTabs(
    selected: BatchAddTarget,
    enabled: Boolean,
    onSelect: (BatchAddTarget) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            BatchAddTarget.PLAYLIST to "歌单",
            BatchAddTarget.GROUP to "合集"
        ).forEach { (target, label) ->
            FilterChip(
                selected = selected == target,
                onClick = { if (target != selected) onSelect(target) },
                enabled = enabled,
                label = { Text(label) }
            )
        }
    }
}
