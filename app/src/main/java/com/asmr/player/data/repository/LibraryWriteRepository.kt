package com.asmr.player.data.repository

import android.content.Context
import com.asmr.player.data.local.datastore.LibraryPreferencesStore
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.TrackEntity
import com.asmr.player.data.local.library.shouldBackfillLegacyOnlineSavedAlbumRoot
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.LibraryFilterPreset
import com.asmr.player.domain.model.LibraryQuerySpec
import com.asmr.player.domain.model.LibrarySort
import com.asmr.player.domain.model.PersistedLibraryFilters
import com.asmr.player.domain.model.RemoteSubtitleSource
import com.asmr.player.domain.model.TreeFileType
import com.asmr.player.util.SubtitleEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 库写入数据访问（R2-B4a 自 LibraryViewModel 下沉；R3-C3 实现拆族至同包 support 类）。
 * 门面保留全部公开/internal 签名与数据投影类型（调用方零改动）：
 * - [LibraryTagWriteSupport]：标签整替/重命名/删除、FTS 行、CSV 标签、AUTO 播种；
 * - [LibraryDeleteWriteSupport]：删专辑/删音轨/删下载任务/缺失整册清除；
 * - [LibraryScanWriteSupport]：聚合、回填、目录树缓存、prune 族、扫描同步/入库/重扫；
 * - [LibraryOnlineSaveSupport]：在线选择保存事务。
 * 只搬数据访问：事务边界、删除顺序、runCatching 吞错范围与原实现逐一对应，
 * 行为契约见 docs/behavior-notes/library-delete-family.md。
 * 编排（消息提示、FTS 刷新时机、进度回调、querySpec 更新）仍归调用方 ViewModel。
 */
@Singleton
class LibraryWriteRepository @Inject constructor(
    private val database: AppDatabase,
    @ApplicationContext private val context: Context,
) {
    /** R3-B5a：预设/排序/过滤持久化写出口（原 VM 手动构造 LibraryPreferencesStore 的写侧透传）。 */
    private val preferencesStore = LibraryPreferencesStore(context)

    private val tagWrite = LibraryTagWriteSupport(database)
    private val deleteWrite = LibraryDeleteWriteSupport(database)
    private val scanWrite = LibraryScanWriteSupport(database, tagWrite, deleteWrite)
    private val onlineSave = LibraryOnlineSaveSupport(database)

    // ---------- 过滤/预设持久化 ----------

    suspend fun setLibrarySort(sort: LibrarySort) = preferencesStore.setSort(sort)

    suspend fun setLibraryFilters(filters: PersistedLibraryFilters) = preferencesStore.setFilters(filters)

    suspend fun saveLibraryPreset(name: String, spec: LibraryQuerySpec): LibraryFilterPreset =
        preferencesStore.savePreset(name, spec)

    suspend fun deleteLibraryPreset(id: String) = preferencesStore.deletePreset(id)

    // ---------- 标签/FTS 写族（实现：LibraryTagWriteSupport） ----------

    suspend fun replaceAlbumUserTags(albumId: Long, tags: List<Pair<String, String>>) =
        tagWrite.replaceAlbumUserTags(albumId, tags)

    suspend fun replaceTrackUserTags(trackId: Long, tags: List<Pair<String, String>>) =
        tagWrite.replaceTrackUserTags(trackId, tags)

    suspend fun renameUserTag(tagId: Long, newName: String): Set<Long> = tagWrite.renameUserTag(tagId, newName)

    suspend fun deleteUserTag(tagId: Long): Set<Long> = tagWrite.deleteUserTag(tagId)

    suspend fun upsertAlbumFtsIndex(albumId: Long, entity: AlbumEntity) = tagWrite.upsertAlbumFtsIndex(albumId, entity)

    suspend fun upsertAlbumTagsFromCsv(albumId: Long, tagsCsv: String, source: Int) =
        tagWrite.upsertAlbumTagsFromCsv(albumId, tagsCsv, source)

    suspend fun seedAutoTagsFromAlbumTags(albums: List<AlbumEntity>) = tagWrite.seedAutoTagsFromAlbumTags(albums)

    // ---------- 删除写族（实现：LibraryDeleteWriteSupport） ----------

    suspend fun deleteAlbumWithContent(albumId: Long, entity: AlbumEntity) =
        deleteWrite.deleteAlbumWithContent(albumId, entity)

    suspend fun deleteVerifiedTracksAndResources(albumId: Long, verifiedTrackIds: List<Long>, resourceIds: List<Long>) =
        deleteWrite.deleteVerifiedTracksAndResources(albumId, verifiedTrackIds, resourceIds)

    suspend fun deleteTrackCompletely(trackId: Long, albumId: Long) =
        deleteWrite.deleteTrackCompletely(trackId, albumId)

    suspend fun deleteAlbumTracksAndSubtitles(albumId: Long) = deleteWrite.deleteAlbumTracksAndSubtitles(albumId)

    suspend fun deleteTrackWithSubtitlesById(trackId: Long) = deleteWrite.deleteTrackWithSubtitlesById(trackId)

    suspend fun deleteTracksWithSubtitles(trackIds: List<Long>) = deleteWrite.deleteTracksWithSubtitles(trackIds)

    suspend fun deleteAlbumEntity(entity: AlbumEntity) = deleteWrite.deleteAlbumEntity(entity)

    suspend fun deleteDownloadTaskWithItems(taskId: Long) = deleteWrite.deleteDownloadTaskWithItems(taskId)

    suspend fun deleteAlbumIfMissingLocally(
        albumId: Long,
        isMissing: (AlbumEntity, List<TrackEntity>) -> Boolean,
    ): Set<String> = deleteWrite.deleteAlbumIfMissingLocally(albumId, isMissing)

    // ---------- 单行透传（1 行实现留门面） ----------

    /** 透传 updateAlbum（封面/路径改写等单行更新由调用方组装 entity）。 */
    suspend fun updateAlbum(entity: AlbumEntity) {
        database.albumDao().updateAlbum(entity)
    }

    /** 插入专辑行（REPLACE 冲突策略，原 VM 直调 albumDao.insertAlbum 透传）。 */
    suspend fun insertAlbum(entity: AlbumEntity): Long = database.albumDao().insertAlbum(entity)

    /** 清空专辑目录树缓存。 */
    suspend fun clearLocalTreeCache(albumId: Long) {
        database.localTreeCacheDao().deleteByAlbum(albumId)
    }

    // ---------- 扫描/清理写族（实现：LibraryScanWriteSupport） ----------

    /** 专辑音频聚合三字段（数量/总时长/总字节）。fileSizeQuery 由调用方注入（文件系统探查属平台侧）。 */
    data class AlbumAudioAggregate(
        val trackCount: Int,
        val totalDuration: Double,
        val totalSizeBytes: Long,
    )

    suspend fun computeAlbumAudioAggregate(
        trackSpecs: List<TrackEntity>,
        fileSizeQuery: suspend (String) -> Long?,
    ): AlbumAudioAggregate = scanWrite.computeAlbumAudioAggregate(trackSpecs, fileSizeQuery)

    suspend fun refreshAlbumAudioAggregate(albumId: Long, fileSizeQuery: suspend (String) -> Long?) =
        scanWrite.refreshAlbumAudioAggregate(albumId, fileSizeQuery)

    suspend fun backfillLegacyOnlineSavedAlbumRoots(
        albums: List<AlbumEntity>,
        resolveLegacyDir: suspend (AlbumEntity) -> String,
    ) = scanWrite.backfillLegacyOnlineSavedAlbumRoots(albums, resolveLegacyDir)

    /** 目录树缓存叶子（Gson 载荷；TreeFileType 枚举名与原 VM 私有 CacheTreeFileType 一致，载荷 JSON 不变）。internal：暴露 internal TreeFileType。 */
    internal data class ScanCacheLeaf(val relativePath: String, val absolutePath: String, val fileType: TreeFileType)

    /** 文档树扫描的音轨规格（原 scanFromDocumentTree/scanSingleAlbumFromDocumentUri 内部 TrackSpec）。 */
    data class ScanTrackSpec(val title: String, val path: String, val group: String)

    internal suspend fun upsertLocalTreeCache(
        albumId: Long,
        albumPaths: List<String>,
        leaves: List<ScanCacheLeaf>,
        stampProvider: (List<String>) -> Long,
    ) = scanWrite.upsertLocalTreeCache(albumId, albumPaths, leaves, stampProvider)

    suspend fun pruneMissingDownloadedAlbums(missing: List<AlbumEntity>) =
        scanWrite.pruneMissingDownloadedAlbums(missing)

    suspend fun pruneDocumentDownloadAlbum(entity: AlbumEntity, rootUriString: String) =
        scanWrite.pruneDocumentDownloadAlbum(entity, rootUriString)

    suspend fun pruneMissingDocumentAlbums(missing: List<AlbumEntity>, rootUriString: String) =
        scanWrite.pruneMissingDocumentAlbums(missing, rootUriString)

    suspend fun pruneOrphanedAlbums(
        albums: List<AlbumEntity>,
        uriOrFileExists: (String) -> Boolean,
        fileExists: (String) -> Boolean,
        resolveLegacyDir: suspend (AlbumEntity) -> String,
    ) = scanWrite.pruneOrphanedAlbums(albums, uriOrFileExists, fileExists, resolveLegacyDir)

    suspend fun syncScannedLocalAlbumTracks(
        tracksToUpdate: List<TrackEntity>,
        tracksToInsert: List<TrackEntity>,
        subtitleEntriesByAudioPath: Map<String, List<SubtitleEntry>>,
        subtitleEntriesByExistingTrackId: Map<Long, List<SubtitleEntry>>,
        removedIds: List<Long>,
    ) = scanWrite.syncScannedLocalAlbumTracks(
        tracksToUpdate, tracksToInsert, subtitleEntriesByAudioPath, subtitleEntriesByExistingTrackId, removedIds,
    )

    /** 文档树整册扫描入库结果：新专辑 id + 是否写过字幕（供调用方决定歌词重载）。 */
    internal data class DocumentScanResult(val albumId: Long, val wroteAnySubtitles: Boolean)

    internal suspend fun upsertScannedDocumentAlbum(
        entity: AlbumEntity,
        scanRootPath: String,
        trackSpecs: List<ScanTrackSpec>,
        subtitlesByAudioPath: Map<String, List<SubtitleEntry>>,
        cacheLeaves: List<ScanCacheLeaf>,
        fileSizeQuery: suspend (String) -> Long?,
        stampProvider: (List<String>) -> Long,
    ): DocumentScanResult = scanWrite.upsertScannedDocumentAlbum(
        entity, scanRootPath, trackSpecs, subtitlesByAudioPath, cacheLeaves, fileSizeQuery, stampProvider,
    )

    /** 文档树单册重扫结果：是否写过字幕 + 持久化路径（供调用方写缓存）。 */
    internal data class DocumentRescanResult(val wroteAnySubtitles: Boolean, val persistedPaths: List<String>)

    internal suspend fun rescanDocumentAlbum(
        albumId: Long,
        coverPath: String,
        treePrefix: String,
        trackSpecs: List<ScanTrackSpec>,
        subtitlesByAudioPath: Map<String, List<SubtitleEntry>>,
    ): DocumentRescanResult = scanWrite.rescanDocumentAlbum(albumId, coverPath, treePrefix, trackSpecs, subtitlesByAudioPath)

    // ---------- 在线保存写族（实现：LibraryOnlineSaveSupport） ----------

    /** 在线选择保存到本地库的音轨规格（原 AlbumDetailViewModel private OnlineSaveLeaf 的数据面投影）。 */
    data class OnlineSaveTrackSpec(
        val title: String,
        val url: String,
        val duration: Double,
        val group: String,
        val subtitleSources: List<RemoteSubtitleSource>,
    )

    /** 在线选择保存到本地库的资源规格。internal：暴露 internal TreeFileType。 */
    internal data class OnlineSaveResourceSpec(
        val relativePath: String,
        val url: String,
        val fileType: TreeFileType,
    )

    /** 在线保存事务结果：命中专辑 id / 新插音轨数 / 保存资源数（与原外层 var 捕获语义一致）。 */
    data class OnlineSaveResult(val albumId: Long, val insertedCount: Int, val resourceSavedCount: Int)

    internal suspend fun saveOnlineSelectedToLibrary(
        targetLocalAlbumId: Long?,
        workKey: String,
        onlinePath: String,
        albumDirPath: String,
        displayAlbum: Album,
        rj: String,
        playableLeaves: List<OnlineSaveTrackSpec>,
        resourceLeaves: List<OnlineSaveResourceSpec>,
        canonicalUrl: (String) -> String,
    ): OnlineSaveResult = onlineSave.saveOnlineSelectedToLibrary(
        targetLocalAlbumId, workKey, onlinePath, albumDirPath, displayAlbum, rj,
        playableLeaves, resourceLeaves, canonicalUrl,
    )
}
