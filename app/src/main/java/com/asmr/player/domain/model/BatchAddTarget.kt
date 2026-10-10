package com.asmr.player.domain.model

/**
 * 批量加入选择器（US-04/T8）的目标类型。归位 domain.model：ui/library/allsongs 的
 * 多选回调与 main 的 BatchPlaylistPickerRequest 都要携带它，而 allsongs 包不得引用
 * main / ui.common.dialog（会把该环外包拉进 SCC 大连通团），domain.model 是双方
 * 既有的环外公共依赖（同 AllSongsQuery/AllSongsSort 归位约定）。
 */
enum class BatchAddTarget {
    /** 加入歌单（PlaylistRepository.addItemsToPlaylist，MediaItem 粒度）。 */
    PLAYLIST,

    /** 加入合集（AlbumGroupRepository.addTracksToGroup，trackPath=mediaId 粒度）。 */
    GROUP
}
