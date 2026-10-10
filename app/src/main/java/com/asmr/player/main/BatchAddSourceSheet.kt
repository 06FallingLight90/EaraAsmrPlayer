package com.asmr.player.main

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import com.asmr.player.domain.model.AllSongsQuery
import com.asmr.player.domain.model.AllSongsSort
import com.asmr.player.domain.model.AllSongsTrackRow
import com.asmr.player.ui.common.dialog.RoundedTopSheet
import com.asmr.player.ui.library.allsongs.AllSongsPageSource
import com.asmr.player.ui.library.allsongs.AllSongsViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.paging.LoadState

/**
 * T8/US-04 后半：歌单/合集详情页「添加音频」选源 sheet 的 VM。数据面走
 * AllSongsPageSource 端口（装配已由 MainRouteContents 的 AllSongsSourceModule 提供），
 * 固定标题升序、无过滤——薄面板只承担勾选与全选/反选。
 */
@HiltViewModel
internal class BatchAddSourceViewModel @Inject constructor(
    pageSource: AllSongsPageSource
) : ViewModel() {

    /** 平铺分页流（cachedIn 延迟到首次收集；sheet 每次打开都收集）。 */
    val pagedSongs: Flow<PagingData<AllSongsTrackRow>> by lazy {
        Pager(
            config = AllSongsViewModel.PAGING_CONFIG,
            pagingSourceFactory = {
                pageSource.allSongsPaged(AllSongsQuery(sort = AllSongsSort.TitleAsc))
            }
        ).flow.cachedIn(viewModelScope)
    }

    private val _selectedPaths = MutableStateFlow<Set<String>>(emptySet())

    /** 勾选集合（键 = trackPath，与双目标写入的 mediaId 约定一致）。 */
    val selectedPaths: StateFlow<Set<String>> = _selectedPaths.asStateFlow()

    fun toggle(trackPath: String) {
        if (trackPath.isBlank()) return
        _selectedPaths.value = if (trackPath in _selectedPaths.value) {
            _selectedPaths.value - trackPath
        } else {
            _selectedPaths.value + trackPath
        }
    }

    /** 全选已加载可见行（并集）。 */
    fun selectAll(visibleTrackPaths: List<String>) {
        _selectedPaths.value = _selectedPaths.value + visibleTrackPaths.filter { it.isNotBlank() }
    }

    /** 反选已加载可见行（XOR）。 */
    fun invert(visibleTrackPaths: List<String>) {
        val visible = visibleTrackPaths.filter { it.isNotBlank() }.toSet()
        val selected = _selectedPaths.value
        _selectedPaths.value = selected + visible - selected.intersect(visible)
    }

    /** 每次打开 sheet 重置（避免上一轮选择残留）。 */
    fun clearSelection() {
        _selectedPaths.value = emptySet()
    }
}

/**
 * 全部歌曲选源薄面板（RoundedTopSheet）：关闭 + 标题/已选计数 + 确认 + 全选/反选 +
 * 分页平铺勾选列表。歌单/合集详情页各挂一个入口按钮，宿主在 onConfirm 里把选中行
 * 写回对应目标（歌单走 addItemsToPlaylist，合集走 addTracksToGroup）。
 * 「全选」作用于已加载页：分页流无全量缓存，与全部歌曲页多选同取舍。
 */
@Composable
internal fun BatchAddSourceSheet(
    onDismiss: () -> Unit,
    onConfirm: (List<AllSongsTrackRow>) -> Unit,
    viewModel: BatchAddSourceViewModel = hiltViewModel()
) {
    val pagedSongs = viewModel.pagedSongs.collectAsLazyPagingItems()
    val selectedPaths by viewModel.selectedPaths.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.clearSelection()
    }
    val visiblePaths = pagedSongs.itemSnapshotList.items.map { it.trackPath }
    val selectedCount = selectedPaths.size

    RoundedTopSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 12.dp, top = 2.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "关闭"
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = "添加音频",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "已选 $selectedCount 项",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = { onConfirm(pagedSongs.itemSnapshotList.items.filter { it.trackPath in selectedPaths }) },
                    enabled = selectedCount > 0
                ) {
                    Text("添加")
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "从全部歌曲中选择",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { viewModel.selectAll(visiblePaths) },
                    enabled = visiblePaths.isNotEmpty()
                ) {
                    Text("全选")
                }
                TextButton(
                    onClick = { viewModel.invert(visiblePaths) },
                    enabled = visiblePaths.isNotEmpty()
                ) {
                    Text("反选")
                }
            }
            BatchAddSourceList(
                pagedSongs = pagedSongs,
                selectedPaths = selectedPaths,
                onToggle = viewModel::toggle
            )
        }
    }
}

@Composable
private fun BatchAddSourceList(
    pagedSongs: LazyPagingItems<AllSongsTrackRow>,
    selectedPaths: Set<String>,
    onToggle: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 2.dp, bottom = 16.dp)
    ) {
        items(
            count = pagedSongs.itemCount,
            key = { idx -> pagedSongs.itemSnapshotList.getOrNull(idx)?.trackId?.takeIf { it > 0L } ?: idx },
            contentType = { "batchAddSourceRow" }
        ) { idx ->
            val row = pagedSongs[idx] ?: return@items
            BatchAddSourceRow(
                row = row,
                selected = row.trackPath in selectedPaths,
                onClick = { onToggle(row.trackPath) }
            )
        }
        if (pagedSongs.loadState.append is LoadState.Loading) {
            item(key = "batchadd-loading-more") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }
        }
        if (
            pagedSongs.loadState.refresh is LoadState.NotLoading &&
            pagedSongs.itemCount == 0
        ) {
            item(key = "batchadd-empty") {
                Text(
                    text = "没有可选的音频",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp)
                )
            }
        }
    }
}

/** 选源行：勾选圈 + 封面/占位 + 文件名主行 + 专辑/歌手副行（视觉对齐 AllSongsRow）。 */
@Composable
private fun BatchAddSourceRow(
    row: AllSongsTrackRow,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .heightIn(min = 54.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                } else {
                    Color.Transparent
                }
            )
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(8.dp))
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
        Spacer(modifier = Modifier.width(10.dp))
        val cover = row.coverPath.takeIf { it.isNotBlank() }
        if (cover != null) {
            AsyncImage(
                model = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = row.trackPath.substringAfterLast('/').substringAfterLast('\\').ifBlank { row.trackPath },
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
    }
}
