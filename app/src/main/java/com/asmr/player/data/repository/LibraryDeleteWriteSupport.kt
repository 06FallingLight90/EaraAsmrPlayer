package com.asmr.player.data.repository

import androidx.room.withTransaction
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.TrackEntity

/**
 * R3-C3：删除写族实现（自 LibraryWriteRepository 逐字搬移，逻辑未改）。
 * 门面 [LibraryWriteRepository] 保留全部签名委托至此；调用方零改动。
 * 行为契约见 docs/behavior-notes/library-delete-family.md。
 */
internal class LibraryDeleteWriteSupport(private val database: AppDatabase) {
    /**
     * 删专辑事务。事务内顺序固定：字幕 → 音轨 → 专辑实体（在线缓存资源 + 专辑行）→ 专辑标签 → FTS 行。
     * 专辑行缺失的早退判定由调用方在事务前完成。
     */
    suspend fun deleteAlbumWithContent(albumId: Long, entity: AlbumEntity) {
        database.withTransaction {
            database.trackDao().deleteSubtitlesForAlbum(albumId)
            database.trackDao().deleteTracksForAlbum(albumId)
            deleteAlbumEntity(entity)
            database.tagDao().deleteAlbumTagsByAlbumId(albumId)
            database.albumFtsDao().deleteByAlbumId(albumId)
        }
    }

    /**
     * 目录树删除事务：已验证音轨（远程字幕源/字幕/音轨标签/音轨行）+ 指定在线资源 + 专辑目录树缓存。
     * trackIds/resourceIds 的验证与过滤由调用方在事务前完成。
     */
    suspend fun deleteVerifiedTracksAndResources(albumId: Long, verifiedTrackIds: List<Long>, resourceIds: List<Long>) {
        database.withTransaction {
            if (verifiedTrackIds.isNotEmpty()) {
                database.remoteSubtitleSourceDao().deleteByTrackIds(verifiedTrackIds)
                database.trackDao().deleteSubtitlesForTracks(verifiedTrackIds)
                database.trackTagDao().deleteTrackTagsByTrackIds(verifiedTrackIds)
                database.trackDao().deleteTracksByIds(verifiedTrackIds)
            }
            if (resourceIds.isNotEmpty()) {
                database.onlineSavedResourceDao().deleteByIds(resourceIds)
            }
            database.localTreeCacheDao().deleteByAlbum(albumId)
        }
    }

    /** 删单音轨事务：每步 runCatching 吞错不中断（与原实现一致），最后清目录树缓存。 */
    suspend fun deleteTrackCompletely(trackId: Long, albumId: Long) {
        database.withTransaction {
            runCatching { database.remoteSubtitleSourceDao().deleteByTrackId(trackId) }
            runCatching { database.trackDao().deleteSubtitlesForTrack(trackId) }
            runCatching { database.trackTagDao().deleteTrackTagsByTrackId(trackId) }
            runCatching { database.trackDao().deleteTrackById(trackId) }
            runCatching { database.localTreeCacheDao().deleteByAlbum(albumId) }
        }
    }

    /** 无事务组合删：专辑字幕 + 音轨行（目录树清理路径用，与原实现一致不加事务）。 */
    suspend fun deleteAlbumTracksAndSubtitles(albumId: Long) {
        database.trackDao().deleteSubtitlesForAlbum(albumId)
        database.trackDao().deleteTracksForAlbum(albumId)
    }

    /** 无事务组合删：单音轨字幕 + 音轨行（目录树清理循环逐轨调用，与原实现一致不加事务）。 */
    suspend fun deleteTrackWithSubtitlesById(trackId: Long) {
        database.trackDao().deleteSubtitlesForTrack(trackId)
        database.trackDao().deleteTrackById(trackId)
    }

    /** 无事务组合删：批量音轨字幕 + 音轨行（在线专辑重扫清理用，空守卫留在调用侧）。 */
    suspend fun deleteTracksWithSubtitles(trackIds: List<Long>) {
        database.trackDao().deleteSubtitlesForTracks(trackIds)
        database.trackDao().deleteTracksByIds(trackIds)
    }

    /** 删专辑实体：在线缓存资源 + 专辑行（原 VM private 方法转公开）。 */
    suspend fun deleteAlbumEntity(entity: AlbumEntity) {
        database.onlineSavedResourceDao().deleteByAlbumId(entity.id)
        database.albumDao().deleteAlbum(entity)
    }

    /** 删下载任务及其条目：先条目后任务，各自 runCatching 吞错（与原 VM deleteAlbum 收尾逐字一致）。 */
    suspend fun deleteDownloadTaskWithItems(taskId: Long) {
        runCatching { database.downloadDao().deleteItemsForTask(taskId) }
        runCatching { database.downloadDao().deleteTaskById(taskId) }
    }

    /**
     * 本地专辑缺失即整册清除（原 AlbumDetailViewModel loadLocalAlbumByIdWithAvailabilityCheck 事务体逐字下沉）。
     * 事务内复核 [isMissing]（平台可用性探查经调用方注入，原实现在事务内同步调用），命中则按固定顺序清：
     * 音轨级（字幕任务条目/远程字幕源/轨标签）→ 专辑字幕+轨 → 播放进度/目录树缓存/在线资源/专辑标签/FTS/播放统计 → 专辑行。
     * @return 被删音轨的 path 集合；空集表示未触发删除。
     * ⚠️ 本路径不删 playlist_item / listening_sessions 等关联表（原实现即如此）。
     */
    suspend fun deleteAlbumIfMissingLocally(
        albumId: Long,
        isMissing: (AlbumEntity, List<TrackEntity>) -> Boolean,
    ): Set<String> {
        var removedMediaIds: Set<String> = emptySet()
        database.withTransaction {
            val latest = database.albumDao().getAlbumById(albumId) ?: return@withTransaction
            val latestTracks = database.trackDao().getTracksForAlbumOnce(albumId)
            if (!isMissing(latest, latestTracks)) {
                return@withTransaction
            }
            val trackIds = latestTracks.map { it.id }
            removedMediaIds = latestTracks.map { it.path }.filter(String::isNotBlank).toSet()
            if (trackIds.isNotEmpty()) {
                database.subtitleTaskDao().deleteItemsForTracks(trackIds)
                database.remoteSubtitleSourceDao().deleteByTrackIds(trackIds)
                database.trackTagDao().deleteTrackTagsByTrackIds(trackIds)
            }
            database.trackDao().deleteSubtitlesForAlbum(albumId)
            database.trackDao().deleteTracksForAlbum(albumId)
            database.trackPlaybackProgressDao().deleteByAlbumId(albumId)
            database.localTreeCacheDao().deleteByAlbum(albumId)
            database.onlineSavedResourceDao().deleteByAlbumId(albumId)
            database.tagDao().deleteAlbumTagsByAlbumId(albumId)
            database.albumFtsDao().deleteByAlbumId(albumId)
            database.playStatDao().deleteByAlbumId(albumId)
            database.albumDao().deleteAlbum(latest)
        }
        return removedMediaIds
    }
}
