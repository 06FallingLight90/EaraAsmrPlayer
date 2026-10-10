package com.asmr.player.main

import androidx.compose.runtime.Composable
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.media3.common.MediaItem
import com.asmr.player.domain.model.AllSongsTrackRow
import com.asmr.player.playback.MediaItemFactory
import com.asmr.player.ui.groups.AlbumGroupsViewModel
import com.asmr.player.ui.playlists.PlaylistPickerScreen
import com.asmr.player.ui.playlists.PlaylistsViewModel

/**
 * T8 双目标选择器的宿主装配：main 已在 ci_guard 包级 SCC 大连通团内，由此
 * 统一构造两个 activity 级 VM 并把 AlbumGroupsViewModel 适配成
 * BatchAddGroupTarget 端口（适配方法体在 VM 上，调用方零新增 cross-feature import）。
 * RoundedTopSheet 外壳由各渲染点自行包裹，本函数只负责 sheet 内容。
 */
@Composable
internal fun BatchAddPickerSheet(
    request: BatchPlaylistPickerRequest,
    windowSizeClass: WindowSizeClass,
    activityViewModelStoreOwner: ViewModelStoreOwner,
    onDismiss: () -> Unit
) {
    val playlistsViewModel: PlaylistsViewModel = hiltViewModel(activityViewModelStoreOwner)
    val albumGroupsViewModel: AlbumGroupsViewModel = hiltViewModel(activityViewModelStoreOwner)
    PlaylistPickerScreen(
        windowSizeClass = windowSizeClass,
        items = request.items,
        onBack = onDismiss,
        embeddedInDialog = true,
        viewModel = playlistsViewModel,
        groupTarget = albumGroupsViewModel.asBatchAddGroupTarget(),
        defaultTarget = request.defaultTarget
    )
}

/**
 * 全部歌曲行投影 → 批量加入用 MediaItem（参照 T6 onPlayTrack 的
 * MediaItemFactory.fromDetails 用法；trackPath 兼任 mediaId/uri，双目标写入
 * 均以 track path 为关联键，T5 投影约定）。
 */
internal fun List<AllSongsTrackRow>.toBatchAddMediaItems(): List<MediaItem> = map { row ->
    MediaItemFactory.fromDetails(
        mediaId = row.trackPath,
        uri = row.trackPath,
        title = row.trackTitle,
        artist = row.artist.orEmpty(),
        albumTitle = row.albumTitle,
        artworkUri = row.coverPath,
        albumId = row.albumId,
        trackId = row.trackId
    )
}
