package com.asmr.player.ui.library.allsongs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import com.asmr.player.domain.model.AllSongsSort
import com.asmr.player.domain.model.AllSongsTrackRow
import com.asmr.player.domain.model.BatchAddTarget
import com.asmr.player.ui.common.list.ActiveDropdownMenuItem
import com.asmr.player.ui.common.list.LocalBottomOverlayPadding
import com.asmr.player.ui.common.list.rememberCalmScrollableFlingBehavior
import com.asmr.player.util.Formatting

private val AllSongsRowCornerRadius = 12.dp
private val AllSongsRowHorizontalPadding = 12.dp
private const val ALL_SONGS_SORT_TITLE_TAG = "allsongs_sort_title_item"
private const val ALL_SONGS_SORT_FILE_NAME_TAG = "allsongs_sort_file_name_item"
private const val ALL_SONGS_SORT_ADDED_TAG = "allsongs_sort_added_item"

/**
 * 全部歌曲平铺视图（US-05/T6）：返回 + 框内过滤输入 + 排序三态菜单 +
 * 按文件名展示的曲目平铺列表（文件名从 trackPath 取末段，US-02 语义：不强依赖标签）。
 * 行点击 → [onPlayTrack]（宿主装配处经 PlayerViewModel 单曲播放）。
 * T8/US-05：长按行进入多选（风格对齐 DirectoryBrowserPanel.selectionMode 先例），
 * 多选工具条提供全选/反选（作用于已加载可见行）、已选计数、加入歌单/加入合集
 * （经 [onOpenBatchPicker] 上抛宿主，宿主构造 MediaItem 并弹双目标选择器）、退出多选。
 * 本包不在 ci_guard 连通团内：ui.common.core（经 SearchBlockedKeywordsViewModel →
 * data.settings 入环）与 ui.library 均不可引用，故过滤框/按钮为本包薄实现
 * （同 PurchasedScreen 自持实现的先例），仅复用已验证环外的 ui.common.list 与 util。
 */
@Composable
fun AllSongsScreen(
    onBack: () -> Unit,
    onPlayTrack: (AllSongsTrackRow) -> Unit,
    modifier: Modifier = Modifier,
    onOpenBatchPicker: (BatchAddTarget, List<AllSongsTrackRow>) -> Unit = { _, _ -> },
    viewModel: AllSongsViewModel = hiltViewModel()
) {
    val pagedSongs = viewModel.pagedSongs.collectAsLazyPagingItems()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val selectionActive by viewModel.selectionActive.collectAsStateWithLifecycle()
    val selectedPaths by viewModel.selectedPaths.collectAsStateWithLifecycle()
    var searchText by remember { mutableStateOf("") }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    /** 把当前勾选投影为列表序的行集合（勾选集合无序，按平铺列表顺序上抛）。 */
    fun selectedRowsInListOrder(): List<AllSongsTrackRow> =
        pagedSongs.itemSnapshotList.items.filter { it.trackPath in selectedPaths }

    Column(modifier = modifier.fillMaxSize()) {
        if (selectionActive) {
            AllSongsSelectionTopBar(
                selectedCount = selectedPaths.size,
                visibleCount = pagedSongs.itemSnapshotList.items.count { it.trackPath.isNotBlank() },
                onSelectAll = { viewModel.selectAllVisible(pagedSongs.itemSnapshotList.items.map { it.trackPath }) },
                onInvert = { viewModel.invertVisibleSelection(pagedSongs.itemSnapshotList.items.map { it.trackPath }) },
                onAddToPlaylist = {
                    val rows = selectedRowsInListOrder()
                    if (rows.isNotEmpty()) onOpenBatchPicker(BatchAddTarget.PLAYLIST, rows)
                },
                onAddToGroup = {
                    val rows = selectedRowsInListOrder()
                    if (rows.isNotEmpty()) onOpenBatchPicker(BatchAddTarget.GROUP, rows)
                },
                onExit = viewModel::exitSelectionMode
            )
        } else {
            AllSongsTopBar(
                searchText = searchText,
                onSearchTextChange = {
                    searchText = it
                    viewModel.setTextFilter(it)
                },
                onClearSearch = {
                    searchText = ""
                    viewModel.setTextFilter("")
                },
                currentSort = sort,
                sortMenuExpanded = sortMenuExpanded,
                onSortMenuExpandedChange = { sortMenuExpanded = it },
                onSortTitle = { viewModel.setSort(AllSongsSort.TitleAsc) },
                onSortFileName = { viewModel.setSort(AllSongsSort.FileNameAsc) },
                onSortAdded = { viewModel.setSort(AllSongsSort.AddedDesc) },
                onBack = onBack
            )
        }
        AllSongsListContent(
            pagedSongs = pagedSongs,
            selectionActive = selectionActive,
            selectedPaths = selectedPaths,
            onRowClick = { row ->
                if (selectionActive) viewModel.toggleSelection(row.trackPath) else onPlayTrack(row)
            },
            onRowLongClick = { row ->
                if (!selectionActive) viewModel.enterSelectionMode(row.trackPath)
            }
        )
    }
}

@Composable
private fun AllSongsTopBar(
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    currentSort: AllSongsSort,
    sortMenuExpanded: Boolean,
    onSortMenuExpandedChange: (Boolean) -> Unit,
    onSortTitle: () -> Unit,
    onSortFileName: () -> Unit,
    onSortAdded: () -> Unit,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回"
            )
        }
        AllSongsFilterField(
            value = searchText,
            onValueChange = onSearchTextChange,
            onClear = onClearSearch,
            modifier = Modifier.weight(1f)
        )
        Box {
            IconButton(onClick = { onSortMenuExpandedChange(true) }) {
                Icon(
                    imageVector = Icons.Rounded.SwapVert,
                    contentDescription = "排序"
                )
            }
            DropdownMenu(
                expanded = sortMenuExpanded,
                onDismissRequest = { onSortMenuExpandedChange(false) }
            ) {
                ActiveDropdownMenuItem(
                    label = "标题",
                    selected = currentSort == AllSongsSort.TitleAsc,
                    onClick = {
                        onSortMenuExpandedChange(false)
                        onSortTitle()
                    },
                    testTag = ALL_SONGS_SORT_TITLE_TAG
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
                ActiveDropdownMenuItem(
                    label = "文件名",
                    selected = currentSort == AllSongsSort.FileNameAsc,
                    onClick = {
                        onSortMenuExpandedChange(false)
                        onSortFileName()
                    },
                    testTag = ALL_SONGS_SORT_FILE_NAME_TAG
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
                ActiveDropdownMenuItem(
                    label = "最近加入",
                    selected = currentSort == AllSongsSort.AddedDesc,
                    onClick = {
                        onSortMenuExpandedChange(false)
                        onSortAdded()
                    },
                    testTag = ALL_SONGS_SORT_ADDED_TAG
                )
            }
        }
    }
}

/**
 * 多选工具条（US-05）：退出 + 已选计数 + 全选/反选（作用于已加载可见行）+
 * 加入歌单/加入合集（无勾选时禁用）。薄实现，不入连通团。
 */
@Composable
private fun AllSongsSelectionTopBar(
    selectedCount: Int,
    visibleCount: Int,
    onSelectAll: () -> Unit,
    onInvert: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onAddToGroup: () -> Unit,
    onExit: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val hasSelection = selectedCount > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onExit) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "退出多选"
            )
        }
        Text(
            text = "已选 $selectedCount 项",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        TextButton(
            onClick = onSelectAll,
            enabled = visibleCount > 0
        ) {
            Text("全选", style = MaterialTheme.typography.labelLarge)
        }
        TextButton(
            onClick = onInvert,
            enabled = visibleCount > 0
        ) {
            Text("反选", style = MaterialTheme.typography.labelLarge)
        }
        IconButton(onClick = onAddToPlaylist, enabled = hasSelection) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                contentDescription = "加入歌单"
            )
        }
        IconButton(onClick = onAddToGroup, enabled = hasSelection) {
            Icon(
                imageVector = Icons.Rounded.CreateNewFolder,
                contentDescription = "加入合集"
            )
        }
    }
}

/** 框内过滤输入的薄实现：圆角底 + 搜索图标 + 占位文案 + 清除按钮（视觉对齐库页搜索框）。 */
@Composable
private fun AllSongsFilterField(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colorScheme.onSurface),
        cursorBrush = SolidColor(colorScheme.primary),
        modifier = modifier,
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(colorScheme.surfaceVariant.copy(alpha = 0.55f)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = "标题 / 文件名 / 歌手...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    inner()
                }
                if (value.isNotBlank()) {
                    IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "清除过滤",
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(if (value.isNotBlank()) 4.dp else 12.dp))
            }
        }
    )
}

@Composable
private fun AllSongsListContent(
    pagedSongs: LazyPagingItems<AllSongsTrackRow>,
    selectionActive: Boolean,
    selectedPaths: Set<String>,
    onRowClick: (AllSongsTrackRow) -> Unit,
    onRowLongClick: (AllSongsTrackRow) -> Unit
) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        flingBehavior = rememberCalmScrollableFlingBehavior(),
        contentPadding = PaddingValues(
            top = 4.dp,
            bottom = LocalBottomOverlayPadding.current + 16.dp
        )
    ) {
        items(
            count = pagedSongs.itemCount,
            key = { idx -> pagedSongs.itemSnapshotList.getOrNull(idx)?.trackId?.takeIf { it > 0L } ?: idx },
            contentType = { "allSongsTrackRow" }
        ) { idx ->
            val row = pagedSongs[idx] ?: return@items
            AllSongsRow(
                row = row,
                selectionMode = selectionActive,
                selected = row.trackPath in selectedPaths,
                onClick = { onRowClick(row) },
                onLongClick = { onRowLongClick(row) }
            )
        }
        if (pagedSongs.loadState.append is LoadState.Loading) {
            item(key = "allsongs-loading-more") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }
        if (
            pagedSongs.loadState.refresh is LoadState.NotLoading &&
            pagedSongs.itemCount == 0
        ) {
            item(key = "allsongs-empty") {
                AllSongsEmptyHint()
            }
        }
    }
    if (pagedSongs.loadState.refresh is LoadState.Loading && pagedSongs.itemCount == 0) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                strokeWidth = 3.dp
            )
        }
    }
}

@Composable
private fun AllSongsEmptyHint() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(40.dp)
        )
        Text(
            text = "没有匹配的歌曲",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 平铺行：左小封面（缺失时曲目图标占位）+ 文件名主行 + 专辑/歌手副行 + 时长尾注。
 * 文件名从 trackPath 取末段（含 '/' 与 '\\' 两种分隔符，与 AlbumDetailScreenSupport 同口径）。
 * 多选模式（US-05）：行首勾选圈 + 选中底色；行点击切换勾选，长按进入多选
 * （combinedClickable，风格对齐 DirectoryBrowserPanel 的 selectionMode 交互）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AllSongsRow(
    row: AllSongsTrackRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = AllSongsRowHorizontalPadding,
                vertical = 6.dp
            )
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(AllSongsRowCornerRadius))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                } else {
                    Color.Transparent
                }
            )
            .combinedClickable(
                onClick = onClick,
                // 屏级 onRowLongClick 自带守卫（未进多选→进入并勾选首项；多选中→无操作），
                // 必须无条件注册，否则长按入口永不可达（实机走查发现）。
                onLongClick = onLongClick
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = if (selected) "已选中" else "未选中",
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        val cover = row.coverPath.takeIf { it.isNotBlank() }
        if (cover != null) {
            AsyncImage(
                model = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = remember(row.trackPath) { fileNameForDisplay(row.trackPath) },
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val metaLine = listOfNotNull(
                row.albumTitle.takeIf { it.isNotBlank() },
                row.artist?.trim()?.takeIf { it.isNotEmpty() }
            ).joinToString(" · ")
            if (metaLine.isNotEmpty()) {
                Text(
                    text = metaLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        val durationLabel = remember(row.duration) { Formatting.formatTrackSeconds(row.duration) }
        if (durationLabel.isNotBlank()) {
            Text(
                text = durationLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

internal fun fileNameForDisplay(trackPath: String): String {
    // SAF 扫描入库的 track.path 是编码后的 document URI（primary%3AMusic%2F...），
    // 文件名展示前先解码再取末段（实机走查发现：不解码时整条 URI 原样入列）。
    // 仅对确为 document URI（':' 被编码为 %3A）的路径解码，避免误伤含 % 的普通文件路径。
    val decoded = if (trackPath.contains("%3A")) android.net.Uri.decode(trackPath) else trackPath
    return decoded.substringAfterLast('/').substringAfterLast('\\')
        .ifBlank { trackPath }
}
