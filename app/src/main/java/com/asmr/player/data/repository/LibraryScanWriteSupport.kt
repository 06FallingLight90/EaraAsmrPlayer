package com.asmr.player.data.repository

import androidx.room.withTransaction
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.LocalTreeCacheEntity
import com.asmr.player.data.local.db.entities.SubtitleEntity
import com.asmr.player.data.local.db.entities.TrackEntity
import com.asmr.player.data.local.library.buildOnlineAlbumPath
import com.asmr.player.data.local.library.shouldBackfillLegacyOnlineSavedAlbumRoot
import com.asmr.player.domain.model.TagSource
import com.asmr.player.domain.model.TreeFileType
import com.asmr.player.util.SubtitleEntry
import com.asmr.player.util.isOnlineTrackPath
import com.asmr.player.util.isVirtualAlbumPath
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * R3-C3：扫描/清理写族实现（自 LibraryWriteRepository 逐字搬移，逻辑未改）。
 * 门面 [LibraryWriteRepository] 保留全部签名委托至此；调用方零改动。
 * 跨族事务调用经构造注入的 [tagWrite]/[deleteWrite]（原同类内直调改为显式依赖，事务体逐字不变）。
 * 行为契约见 docs/behavior-notes/library-delete-family.md。
 */
internal class LibraryScanWriteSupport(
    private val database: AppDatabase,
    private val tagWrite: LibraryTagWriteSupport,
    private val deleteWrite: LibraryDeleteWriteSupport,
    private val scanMetadata: LibraryScanMetadataSupport,
    private val autoClassify: AutoClassifySupport,
) {
    /** 专辑音频聚合三字段（数量/总时长/总字节）。fileSizeQuery 由调用方注入（文件系统探查属平台侧）。 */
    suspend fun computeAlbumAudioAggregate(
        trackSpecs: List<TrackEntity>,
        fileSizeQuery: suspend (String) -> Long?,
    ): LibraryWriteRepository.AlbumAudioAggregate {
        val totalSizeBytes = withContext(Dispatchers.IO) {
            trackSpecs.sumOf { track -> fileSizeQuery(track.path) ?: 0L }
        }
        return LibraryWriteRepository.AlbumAudioAggregate(
            trackCount = trackSpecs.size,
            totalDuration = trackSpecs.sumOf { it.duration },
            totalSizeBytes = totalSizeBytes,
        )
    }

    /** 重算并回写专辑音频聚合三字段（原 VM refreshAlbumAudioAggregate 逐字下沉，size 探查经注入）。 */
    suspend fun refreshAlbumAudioAggregate(albumId: Long, fileSizeQuery: suspend (String) -> Long?) {
        if (albumId <= 0L) return
        val entity = database.albumDao().getAlbumById(albumId) ?: return
        val tracks = database.trackDao().getTracksForAlbumOnce(albumId)
        val aggregate = computeAlbumAudioAggregate(tracks, fileSizeQuery)
        database.albumDao().updateAlbum(
            entity.copy(
                audioTrackCount = aggregate.trackCount,
                audioTotalDuration = aggregate.totalDuration,
                audioTotalSizeBytes = aggregate.totalSizeBytes,
            )
        )
    }

    /**
     * 旧版在线保存专辑的本地根回填事务（原 VM backfillLegacyOnlineSavedAlbumRoots 事务体逐字下沉）。
     * 单事务包全部专辑；tracks 判定与 FTS/缓存写都在事务内。
     * [resolveLegacyDir] 注入目录创建（File IO 不可回滚，与原实现一样发生在事务内，返回绝对路径）。
     */
    suspend fun backfillLegacyOnlineSavedAlbumRoots(
        albums: List<AlbumEntity>,
        resolveLegacyDir: suspend (AlbumEntity) -> String,
    ) {
        database.withTransaction {
            albums.forEach { entity ->
                if (entity.localPath?.trim().orEmpty().isNotBlank() || entity.downloadPath?.trim().orEmpty().isNotBlank()) {
                    return@forEach
                }
                val tracks = database.trackDao().getTracksForAlbumOnce(entity.id)
                if (!shouldBackfillLegacyOnlineSavedAlbumRoot(entity, tracks)) return@forEach

                val dirPath = resolveLegacyDir(entity)
                val updated = entity.copy(localPath = dirPath)
                database.albumDao().updateAlbum(updated)
                runCatching { database.localTreeCacheDao().deleteByAlbum(entity.id) }
                tagWrite.upsertAlbumFtsIndex(updated.id, updated)
            }
        }
    }

    /**
     * 目录树缓存写（原 VM upsertLocalTreeCache 逐字下沉）。
     * stamp 经注入（File/Document lastModified 探查属平台侧，原实现在调用点同步完成）。
     */
    internal suspend fun upsertLocalTreeCache(
        albumId: Long,
        albumPaths: List<String>,
        leaves: List<LibraryWriteRepository.ScanCacheLeaf>,
        stampProvider: (List<String>) -> Long,
    ) {
        if (albumId <= 0L) return
        val normalizedPaths = albumPaths.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (normalizedPaths.isEmpty()) return
        val payload = Gson().toJson(leaves)
        val key = normalizedPaths.map { it.trim() }.filter { it.isNotBlank() }.sorted().joinToString("|")
        val stamp = stampProvider(normalizedPaths)
        database.localTreeCacheDao().upsert(
            LocalTreeCacheEntity(
                albumId = albumId,
                cacheKey = key,
                stamp = stamp,
                payloadJson = payload,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /** 下载目录缺失清理事务（原 pruneMissingDownloadedAlbums 事务体逐字下沉；missing 筛选含 File.exists 在调用方）。 */
    suspend fun pruneMissingDownloadedAlbums(missing: List<AlbumEntity>) {
        database.withTransaction {
            missing.forEach { entity ->
                if (entity.localPath.isNullOrBlank() && !entity.path.startsWith("content://")) {
                    deleteWrite.deleteAlbumTracksAndSubtitles(entity.id)
                    deleteWrite.deleteAlbumEntity(entity)
                } else {
                    val dl = entity.downloadPath?.trim().orEmpty()
                    val tracks = database.trackDao().getTracksForAlbumOnce(entity.id)
                    tracks.filter { it.path.startsWith(dl) }.forEach { track ->
                        deleteWrite.deleteTrackWithSubtitlesById(track.id)
                    }

                    val updated = entity.copy(
                        path = if (entity.path.startsWith(dl)) (entity.localPath ?: entity.path) else entity.path,
                        downloadPath = null,
                        coverPath = if (entity.coverPath.startsWith(dl)) "" else entity.coverPath
                    )
                    database.albumDao().updateAlbum(updated)
                    tagWrite.upsertAlbumFtsIndex(updated.id, updated)
                }
            }
        }
    }

    /** 文档树下载根缺失清理（原 pruneMissingDocumentDownloadAlbums 循环体；无事务，与原实现一致）。 */
    suspend fun pruneDocumentDownloadAlbum(entity: AlbumEntity, rootUriString: String) {
        val tracks = database.trackDao().getTracksForAlbumOnce(entity.id)
        val removedIds = tracks.filter { it.path.startsWith(rootUriString) }.map { it.id }
        if (removedIds.isNotEmpty()) {
            database.trackDao().deleteSubtitlesForTracks(removedIds)
            database.remoteSubtitleSourceDao().deleteByTrackIds(removedIds)
            database.trackTagDao().deleteTrackTagsByTrackIds(removedIds)
            database.trackDao().deleteTracksByIds(removedIds)
        }
        database.albumDao().updateAlbum(entity.copy(downloadPath = null))
        database.localTreeCacheDao().deleteByAlbum(entity.id)
    }

    /** 文档树专辑缺失清理事务（原 pruneMissingDocumentAlbums 事务体逐字下沉；missing 筛选在调用方）。⚠️ 整册删分支（无在线）不删 album_tag——孤儿残留原状，与 deleteAlbumWithContent 不同，勿"顺手"清理。 */
    suspend fun pruneMissingDocumentAlbums(missing: List<AlbumEntity>, rootUriString: String) {
        database.withTransaction {
            missing.forEach { entity ->
                if (entity.downloadPath.isNullOrBlank()) {
                    val tracks = database.trackDao().getTracksForAlbumOnce(entity.id)
                    val hasOnline = isVirtualAlbumPath(entity.path) || tracks.any { isOnlineTrackPath(it.path) }
                    if (!hasOnline) {
                        deleteWrite.deleteAlbumTracksAndSubtitles(entity.id)
                        deleteWrite.deleteAlbumEntity(entity)
                    } else {
                        val root = rootUriString.trim()
                        tracks.filter { it.path.startsWith(root) }.forEach { track ->
                            deleteWrite.deleteTrackWithSubtitlesById(track.id)
                        }
                        val updatedPath = if (entity.path.startsWith(root)) (buildOnlineAlbumPath(entity) ?: entity.path) else entity.path
                        val updated = entity.copy(
                            path = updatedPath,
                            localPath = entity.localPath?.takeIf { !it.startsWith(root) },
                            coverPath = if (entity.coverPath.startsWith(root)) "" else entity.coverPath
                        )
                        database.albumDao().updateAlbum(updated)
                        tagWrite.upsertAlbumFtsIndex(updated.id, updated)
                    }
                } else {
                    val root = rootUriString.trim()
                    val tracks = database.trackDao().getTracksForAlbumOnce(entity.id)
                    tracks.filter { it.path.startsWith(root) }.forEach { track ->
                        deleteWrite.deleteTrackWithSubtitlesById(track.id)
                    }

                    val updated = entity.copy(
                        path = if (entity.path.startsWith(root)) entity.downloadPath else entity.path,
                        localPath = entity.localPath?.takeIf { !it.startsWith(root) },
                        coverPath = if (entity.coverPath.startsWith(root)) "" else entity.coverPath
                    )
                    database.albumDao().updateAlbum(updated)
                    tagWrite.upsertAlbumFtsIndex(updated.id, updated)
                }
            }
        }
    }

    /**
     * 文件系统孤儿专辑清理事务（原 pruneOrphanedAlbumsByFilesystem 事务体逐字下沉）。
     * [uriOrFileExists]/[fileExists] 注入存在性探查（File/DocumentResolver IO，原实现在事务内同步调用，
     * 且 local/main 用 uriOrFileExists、download 仅用 fileExists——两种探查语义不同，不可合并）；
     * [resolveLegacyDir] 同 backfillLegacyOnlineSavedAlbumRoots。
     */
    suspend fun pruneOrphanedAlbums(
        albums: List<AlbumEntity>,
        uriOrFileExists: (String) -> Boolean,
        fileExists: (String) -> Boolean,
        resolveLegacyDir: suspend (AlbumEntity) -> String,
    ) {
        database.withTransaction {
            albums.forEach { entity ->
                val local = entity.localPath?.trim().orEmpty()
                val download = entity.downloadPath?.trim().orEmpty()
                val main = entity.path.trim()

                val localMissing = local.isNotBlank() && !uriOrFileExists(local)
                val downloadMissing = download.isNotBlank() && !fileExists(download)
                val mainMissing = main.isNotBlank() && !uriOrFileExists(main)

                var cachedTracks: List<TrackEntity>? = null
                var cachedHasOnlineTracks: Boolean? = null

                var anyValid = when {
                    local.isNotBlank() -> !localMissing
                    download.isNotBlank() -> !downloadMissing
                    else -> !mainMissing
                } || (!localMissing && local.isNotBlank()) || (!downloadMissing && download.isNotBlank())

                if (!anyValid) {
                    val hasOnlineTracks = cachedHasOnlineTracks ?: run {
                        val ts = cachedTracks ?: database.trackDao().getTracksForAlbumOnce(entity.id).also { cachedTracks = it }
                        (isVirtualAlbumPath(entity.path) || ts.any { isOnlineTrackPath(it.path) })
                            .also { cachedHasOnlineTracks = it }
                    }
                    if (!hasOnlineTracks) {
                        deleteWrite.deleteAlbumTracksAndSubtitles(entity.id)
                        deleteWrite.deleteAlbumEntity(entity)
                        return@forEach
                    }
                }

                var updated = entity

                if (localMissing) {
                    val ts = cachedTracks ?: database.trackDao().getTracksForAlbumOnce(entity.id).also { cachedTracks = it }
                    ts.filter { it.path.startsWith(local) }.forEach { track ->
                        deleteWrite.deleteTrackWithSubtitlesById(track.id)
                    }
                    updated = updated.copy(
                        path = if (updated.path.startsWith(local)) (download.ifBlank { updated.path }) else updated.path,
                        localPath = null,
                        coverPath = if (updated.coverPath.startsWith(local)) "" else updated.coverPath
                    )
                }

                if (downloadMissing) {
                    val ts = cachedTracks ?: database.trackDao().getTracksForAlbumOnce(entity.id).also { cachedTracks = it }
                    ts.filter { it.path.startsWith(download) }.forEach { track ->
                        deleteWrite.deleteTrackWithSubtitlesById(track.id)
                    }
                    updated = updated.copy(
                        path = if (updated.path.startsWith(download)) (local.ifBlank { updated.path }) else updated.path,
                        downloadPath = null,
                        coverPath = if (updated.coverPath.startsWith(download)) "" else updated.coverPath
                    )
                }

                val hasOnlineTracks = cachedHasOnlineTracks ?: run {
                    val ts = cachedTracks ?: database.trackDao().getTracksForAlbumOnce(entity.id).also { cachedTracks = it }
                    ts.any { isOnlineTrackPath(it.path) }.also { cachedHasOnlineTracks = it }
                }

                if (updated.localPath.isNullOrBlank() &&
                    updated.downloadPath.isNullOrBlank() &&
                    shouldBackfillLegacyOnlineSavedAlbumRoot(
                        updated,
                        cachedTracks ?: database.trackDao().getTracksForAlbumOnce(entity.id).also { cachedTracks = it }
                    )
                ) {
                    val dirPath = resolveLegacyDir(updated)
                    updated = updated.copy(localPath = dirPath)
                    runCatching { database.localTreeCacheDao().deleteByAlbum(updated.id) }
                }

                if (updated.path.isNotBlank() && !uriOrFileExists(updated.path) && hasOnlineTracks) {
                    val onlinePath = buildOnlineAlbumPath(updated)
                    if (!onlinePath.isNullOrBlank()) {
                        updated = updated.copy(path = onlinePath)
                    }
                }

                if (updated.localPath.isNullOrBlank() && updated.downloadPath.isNullOrBlank() && updated.path.isNotBlank()) {
                    val stillMissing = !uriOrFileExists(updated.path)
                    if (stillMissing) {
                        if (!hasOnlineTracks) {
                            deleteWrite.deleteAlbumTracksAndSubtitles(entity.id)
                            deleteWrite.deleteAlbumEntity(entity)
                            return@forEach
                        }
                        val onlinePath = buildOnlineAlbumPath(updated)
                        if (!onlinePath.isNullOrBlank()) {
                            updated = updated.copy(path = onlinePath)
                        }
                    }
                }

                if (updated != entity) {
                    database.albumDao().updateAlbum(updated)
                    tagWrite.upsertAlbumFtsIndex(updated.id, updated)
                }
            }
        }
    }

    /** 本地文件专辑扫描同步事务（原 scanTracksAndSubtitlesFromFileAlbum 事务体逐字下沉；diff/解析在调用方）。 */
    suspend fun syncScannedLocalAlbumTracks(
        tracksToUpdate: List<TrackEntity>,
        tracksToInsert: List<TrackEntity>,
        subtitleEntriesByAudioPath: Map<String, List<SubtitleEntry>>,
        subtitleEntriesByExistingTrackId: Map<Long, List<SubtitleEntry>>,
        removedIds: List<Long>,
    ) {
        database.withTransaction {
            val subtitlesByTrackId = linkedMapOf<Long, List<SubtitleEntry>>()
            subtitlesByTrackId.putAll(subtitleEntriesByExistingTrackId.filterKeys { it > 0L })

            if (tracksToUpdate.isNotEmpty()) database.trackDao().updateTracks(tracksToUpdate)
            tracksToUpdate.forEach { trackEntity ->
                val entriesForTrack = subtitleEntriesByAudioPath[trackEntity.path].orEmpty()
                if (trackEntity.id > 0L && entriesForTrack.isNotEmpty()) {
                    subtitlesByTrackId[trackEntity.id] = entriesForTrack
                }
            }
            if (tracksToInsert.isNotEmpty()) {
                val insertedTrackIds = database.trackDao().insertTracks(tracksToInsert)
                insertedTrackIds.zip(tracksToInsert).forEach { (trackId, trackEntity) ->
                    val entriesForTrack = subtitleEntriesByAudioPath[trackEntity.path].orEmpty()
                    if (trackId > 0L && entriesForTrack.isNotEmpty()) {
                        subtitlesByTrackId[trackId] = entriesForTrack
                    }
                }
                // T7：新插轨增量挂默认合集（update 分支不挂，二扫不回加；source 取专辑最终定性值）。
                attachInsertedTracksToDefaultGroups(
                    insertedTrackIds.zip(tracksToInsert).filter { it.first > 0L }.map { it.second }
                )
            }

            if (subtitlesByTrackId.isNotEmpty()) {
                val trackIds = subtitlesByTrackId.keys.toList()
                val subtitlesToInsert = subtitlesByTrackId.flatMap { (trackId, entries) ->
                    entries.map { entry ->
                        SubtitleEntity(
                            trackId = trackId,
                            startMs = entry.startMs,
                            endMs = entry.endMs,
                            text = entry.text
                        )
                    }
                }
                database.trackDao().deleteSubtitlesForTracks(trackIds)
                database.trackDao().insertSubtitles(subtitlesToInsert)
            }

            if (removedIds.isNotEmpty()) {
                database.trackDao().deleteSubtitlesForTracks(removedIds)
                database.remoteSubtitleSourceDao().deleteByTrackIds(removedIds)
                database.trackTagDao().deleteTrackTagsByTrackIds(removedIds)
                database.trackDao().deleteTracksByIds(removedIds)
            }
        }
    }

    /**
     * T7：新插轨增量挂默认合集（仅本事务真正插入成功的 track；source 取专辑行最终定性值——
     * RJ 合并场景下载专辑重扫仍归音声；albumId<=0 或专辑行缺失时退回 fallbackSource）。
     * 行为契约见 docs/behavior-notes/collection-auto-classify.md。
     */
    private suspend fun attachInsertedTracksToDefaultGroups(
        insertedTracks: List<TrackEntity>,
        fallbackSource: String? = null,
    ) {
        if (insertedTracks.isEmpty()) return
        insertedTracks
            .groupBy { it.albumId }
            .forEach { (albumId, tracks) ->
                val source = if (albumId > 0L) {
                    database.albumDao().getAlbumById(albumId)?.source ?: fallbackSource
                } else {
                    fallbackSource
                }
                autoClassify.attachTracksToDefaultGroups(albumId, tracks.map { it.path }, source)
            }
    }

    /**
     * T11-P2：事务前预读文档树扫描的新插轨元数据（round-1 P2：MMR 文件 IO 原在
     * [upsertScannedDocumentAlbum] 写事务内逐轨执行，大专辑首扫拉长持锁；现移到事务前，
     * 与 File 分支（读取本就在事务外）一致化）。
     * - 新专辑（entity.id<=0）全部规格均为新插，逐一预读；已有专辑按 scanRootPath 内
     *   path-diff 只对新插轨预读——「仅新插轨读取」增量闸门语义不变（fake 计数钉测照旧）。
     * - 事务内 diff 与预读之间存在竞态窗口：预读判"已存在"、进事务前被删的轨会以
     *   map 缺失（null 元数据）落库，不再事务内补读（单册极端并发场景，量级可忽略，接受）。
     */
    suspend fun prepareDocumentTrackMetadata(
        entity: AlbumEntity,
        scanRootPath: String,
        trackSpecs: List<LibraryWriteRepository.ScanTrackSpec>,
    ): Map<String, LibraryScanMetadataSupport.ScannedTrackMetadata?> {
        if (trackSpecs.isEmpty()) return emptyMap()
        val existingUnderRoot = if (entity.id > 0L) {
            database.trackDao().getTracksForAlbumOnce(entity.id)
                .filter { it.path.startsWith(scanRootPath) }
                .associateBy { it.path }
        } else {
            emptyMap()
        }
        return trackSpecs.associate { spec ->
            // 已存在轨不读取（映射值为 null）；仅 path-diff 判定的新插轨在事务前读元数据。
            val metadata = if (existingUnderRoot.containsKey(spec.path)) null else scanMetadata.readForNewTrack(spec.path)
            spec.path to metadata
        }
    }

    /**
     * 文档树整册扫描入库事务（原 scanFromDocumentTree 事务体逐字下沉）。
     * entity/trackSpecs/subtitlesByAudioPath/cacheLeaves 均为调用方预计算的纯数据；
     * fileSizeQuery/stampProvider 注入平台探查（原实现于事务内同步调用）。
     * T11-P2：新插轨元数据由 [prepareDocumentTrackMetadata] 在事务前批量读出，经
     * [metadataByPath] 传入——事务内不再做 MMR 文件 IO，纯写。
     */
    internal suspend fun upsertScannedDocumentAlbum(
        entity: AlbumEntity,
        scanRootPath: String,
        trackSpecs: List<LibraryWriteRepository.ScanTrackSpec>,
        subtitlesByAudioPath: Map<String, List<SubtitleEntry>>,
        cacheLeaves: List<LibraryWriteRepository.ScanCacheLeaf>,
        fileSizeQuery: suspend (String) -> Long?,
        stampProvider: (List<String>) -> Long,
        metadataByPath: Map<String, LibraryScanMetadataSupport.ScannedTrackMetadata?>,
    ): LibraryWriteRepository.DocumentScanResult {
        var wroteAnySubtitles = false
        var firstInsertedCoverBytes: ByteArray? = null
        val insertedAlbumId = database.withTransaction {
            val id = database.albumDao().insertAlbum(entity)
            tagWrite.upsertAlbumFtsIndex(id, entity.copy(id = id))
            tagWrite.upsertAlbumTagsFromCsv(id, entity.tags, TagSource.SCAN)

            val allExistingTracks = database.trackDao().getTracksForAlbumOnce(id)
            val existingUnderRoot = allExistingTracks
                .filter { it.path.startsWith(scanRootPath) }
                .associateBy { it.path }
            val scannedPaths = trackSpecs.map { it.path }.toSet()
            val toDelete = existingUnderRoot.values.filter { it.path !in scannedPaths }.map { it.id }
            if (toDelete.isNotEmpty()) {
                database.trackDao().deleteSubtitlesForTracks(toDelete)
                database.remoteSubtitleSourceDao().deleteByTrackIds(toDelete)
                database.trackTagDao().deleteTrackTagsByTrackIds(toDelete)
                database.trackDao().deleteTracksByIds(toDelete)
            }

            val tracksToInsert = mutableListOf<Pair<TrackEntity, LibraryWriteRepository.ScanTrackSpec>>()
            val tracksToUpdate = mutableListOf<Pair<TrackEntity, LibraryWriteRepository.ScanTrackSpec>>()
            // T3'：元数据仅对本次新插轨读取（增量语义，闸门在 LibraryScanMetadataSupport）；
            // 已存在轨只覆写 title/group，artist/albumTag 不动。
            // T11-P2：元数据已在事务前经 prepareDocumentTrackMetadata 读出，此处查表，不再 MMR 文件 IO。
            trackSpecs.forEach { spec ->
                val existingTrack = existingUnderRoot[spec.path]
                if (existingTrack == null) {
                    val metadata = metadataByPath[spec.path]
                    tracksToInsert += scanMetadata.newTrackEntity(id, spec.title, spec.path, spec.group, metadata) to spec
                    if (firstInsertedCoverBytes == null) firstInsertedCoverBytes = metadata?.embeddedCover
                } else {
                    tracksToUpdate += scanMetadata.updatedTrackEntity(existingTrack, spec.title, spec.group) to spec
                }
            }

            if (tracksToUpdate.isNotEmpty()) {
                database.trackDao().updateTracks(tracksToUpdate.map { it.first })
                tracksToUpdate.forEach { (track, spec) ->
                    val entries = subtitlesByAudioPath[spec.path].orEmpty()
                    if (entries.isNotEmpty()) {
                        database.trackDao().deleteSubtitlesForTrack(track.id)
                        database.trackDao().insertSubtitles(
                            entries.map { entry ->
                                SubtitleEntity(
                                    trackId = track.id,
                                    startMs = entry.startMs,
                                    endMs = entry.endMs,
                                    text = entry.text,
                                )
                            },
                        )
                        wroteAnySubtitles = true
                    }
                }
            }
            if (tracksToInsert.isNotEmpty()) {
                val insertedTrackIds = database.trackDao().insertTracks(tracksToInsert.map { it.first })
                val subtitlesToInsert = ArrayList<SubtitleEntity>()
                insertedTrackIds.zip(tracksToInsert.map { it.second }).forEach { (trackId, spec) ->
                    val entries = subtitlesByAudioPath[spec.path].orEmpty()
                    entries.forEach { e ->
                        subtitlesToInsert.add(
                            SubtitleEntity(
                                trackId = trackId,
                                startMs = e.startMs,
                                endMs = e.endMs,
                                text = e.text
                            )
                        )
                    }
                }
                if (subtitlesToInsert.isNotEmpty()) {
                    database.trackDao().insertSubtitles(subtitlesToInsert)
                    wroteAnySubtitles = true
                }
                // T7：新插轨增量挂默认合集（entity.source 已在调用方经 resolveAlbumSource 定性）。
                attachInsertedTracksToDefaultGroups(
                    insertedTrackIds.zip(tracksToInsert).filter { it.first > 0L }.map { it.second.first },
                    fallbackSource = entity.source,
                )
            }

            val allAfterInsert = database.trackDao().getTracksForAlbumOnce(id)
            val localAfterInsert = allAfterInsert.filter { !it.path.trim().startsWith("http", ignoreCase = true) }
            val localPathToId = LinkedHashMap<String, Long>()
            localAfterInsert.forEach { t ->
                localPathToId.putIfAbsent(t.path, t.id)
            }
            val onlineTracks = allAfterInsert.filter { it.path.trim().startsWith("http", ignoreCase = true) }
            onlineTracks.forEach { online ->
                val targetId = localPathToId[online.path]
                if (targetId != null) {
                    val sourceSubs = database.trackDao().getSubtitlesForTrack(online.id)
                    if (sourceSubs.isNotEmpty()) {
                        val targetHasSubs = database.trackDao().getSubtitlesForTrack(targetId).isNotEmpty()
                        if (!targetHasSubs) {
                            database.trackDao().insertSubtitles(
                                sourceSubs.map { s ->
                                    SubtitleEntity(
                                        trackId = targetId,
                                        startMs = s.startMs,
                                        endMs = s.endMs,
                                        text = s.text
                                    )
                                }
                            )
                        }
                    }
                }
            }

            refreshAlbumAudioAggregate(id, fileSizeQuery)

            val paths = listOfNotNull(entity.path, entity.localPath, entity.downloadPath).map { it.trim() }.filter { it.isNotBlank() }.distinct()
            upsertLocalTreeCache(albumId = id, albumPaths = paths, leaves = cacheLeaves, stampProvider = stampProvider)
            id
        }
        return LibraryWriteRepository.DocumentScanResult(
            albumId = insertedAlbumId,
            wroteAnySubtitles = wroteAnySubtitles,
            firstInsertedCoverBytes = firstInsertedCoverBytes,
        )
    }

    /** 文档树单册重扫事务（原 scanSingleAlbumFromDocumentUri 事务体逐字下沉）。 */
    internal suspend fun rescanDocumentAlbum(
        albumId: Long,
        coverPath: String,
        treePrefix: String,
        trackSpecs: List<LibraryWriteRepository.ScanTrackSpec>,
        subtitlesByAudioPath: Map<String, List<SubtitleEntry>>,
    ): LibraryWriteRepository.DocumentRescanResult {
        var wroteAnySubtitles = false
        var persistedPaths: List<String> = emptyList()
        database.withTransaction {
            val entity = database.albumDao().getAlbumById(albumId) ?: return@withTransaction
            if (coverPath.isNotBlank()) {
                database.albumDao().updateAlbum(entity.copy(coverPath = coverPath))
            }

            persistedPaths = listOfNotNull(entity.path, entity.localPath, entity.downloadPath)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()

            val allExistingTracks = database.trackDao().getTracksForAlbumOnce(albumId)

            val toDelete = allExistingTracks.filter { it.path.startsWith(treePrefix) }.map { it.id }
            if (toDelete.isNotEmpty()) {
                database.trackDao().deleteSubtitlesForTracks(toDelete)
                database.trackDao().deleteTracksByIds(toDelete)
            }

            val tracksToInsert = trackSpecs.map { spec ->
                TrackEntity(
                    albumId = albumId,
                    title = spec.title,
                    path = spec.path,
                    duration = 0.0,
                    group = spec.group
                )
            }
            if (tracksToInsert.isNotEmpty()) {
                val insertedTrackIds = database.trackDao().insertTracks(tracksToInsert)
                val subtitlesToInsert = ArrayList<SubtitleEntity>()
                insertedTrackIds.zip(trackSpecs).forEach { (trackId, spec) ->
                    val entries = subtitlesByAudioPath[spec.path].orEmpty()
                    entries.forEach { e ->
                        subtitlesToInsert.add(
                            SubtitleEntity(
                                trackId = trackId,
                                startMs = e.startMs,
                                endMs = e.endMs,
                                text = e.text
                            )
                        )
                    }
                }
                if (subtitlesToInsert.isNotEmpty()) {
                    database.trackDao().insertSubtitles(subtitlesToInsert)
                    wroteAnySubtitles = true
                }
            }
        }
        return LibraryWriteRepository.DocumentRescanResult(wroteAnySubtitles = wroteAnySubtitles, persistedPaths = persistedPaths)
    }
}
