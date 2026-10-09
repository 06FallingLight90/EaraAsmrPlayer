package com.asmr.player.domain.model

/**
 * 全部歌曲平铺视图（US-05）的行投影（T5）。
 * 归位 domain.model：ui 经 repository 消费 PagingData<AllSongsTrackRow>（同 LibraryTrackRow 约定）。
 * trackPath 兼任 mediaId 约定键（播放 MediaItemFactory / 歌单合集批量加入均以 track path 为关联键）；
 * 文件名展示由 ui 侧从 trackPath 取末段派生（SQLite 无 basename，避免投影层字符串体操）。
 */
data class AllSongsTrackRow(
    val trackId: Long,
    val albumId: Long,
    /** displayTitle 回退 title（与既有库投影口径一致）。 */
    val trackTitle: String,
    val trackPath: String,
    val duration: Double,
    /** 本地音乐艺术家（Room 32 新列，扫描缺失时为 null）。 */
    val artist: String?,
    /** 所属专辑展示标题（平铺行上下文）。 */
    val albumTitle: String,
    val coverPath: String
)
