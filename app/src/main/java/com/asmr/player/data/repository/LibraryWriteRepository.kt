package com.asmr.player.data.repository

import androidx.room.withTransaction
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.AlbumFtsEntity
import com.asmr.player.data.local.db.entities.AlbumTagEntity
import com.asmr.player.data.local.db.entities.LocalTreeCacheEntity
import com.asmr.player.data.local.db.entities.SubtitleEntity
import com.asmr.player.data.local.db.entities.TagEntity
import com.asmr.player.data.local.db.entities.TagSource
import com.asmr.player.data.local.db.entities.TrackEntity
import com.asmr.player.data.local.db.entities.TrackTagEntity
import com.asmr.player.data.local.db.entities.TreeFileType
import com.asmr.player.data.local.library.buildOnlineAlbumPath
import com.asmr.player.data.local.library.shouldBackfillLegacyOnlineSavedAlbumRoot
import com.asmr.player.util.TagNormalizer
import com.asmr.player.util.SubtitleEntry
import com.asmr.player.util.buildTagsToken
import com.asmr.player.util.isOnlineTrackPath
import com.asmr.player.util.isVirtualAlbumPath
import com.asmr.player.util.parseAlbumTags
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 库写入数据访问（R2-B4a 自 LibraryViewModel 下沉）。
 * 只搬数据访问：事务边界、删除顺序、runCatching 吞错范围与原实现逐一对应，
 * 行为契约见 docs/behavior-notes/library-delete-family.md。
 * 编排（消息提示、FTS 刷新时机、进度回调、querySpec 更新）仍归调用方 ViewModel。
 */
@Singleton
class LibraryWriteRepository @Inject constructor(
    private val database: AppDatabase,
) {
    /** 事务内整替专辑 USER 标签：先删后插，tag 行按归一名复用（IGNORE 冲突策略），其它源不动。 */
    suspend fun replaceAlbumUserTags(albumId: Long, tags: List<Pair<String, String>>) {
        val tagDao = database.tagDao()
        database.withTransaction {
            tagDao.deleteAlbumTagsByAlbumIdAndSource(albumId, TagSource.USER)
            if (tags.isNotEmpty()) {
                val tagEntities = tags.map { (name, normalized) ->
                    TagEntity(name = name, nameNormalized = normalized)
                }
                tagDao.insertTags(tagEntities)
                val persisted = tagDao.getTagsByNormalized(tags.map { it.second })
                val idByNormalized = persisted.associateBy({ it.nameNormalized }, { it.id })
                val refs = tags.mapNotNull { (_, normalized) ->
                    val tagId = idByNormalized[normalized] ?: return@mapNotNull null
                    AlbumTagEntity(albumId = albumId, tagId = tagId, source = TagSource.USER)
                }
                if (refs.isNotEmpty()) tagDao.insertAlbumTags(refs)
            }
        }
    }

    /** 事务内整替音轨 USER 标签，语义同 [replaceAlbumUserTags]。 */
    suspend fun replaceTrackUserTags(trackId: Long, tags: List<Pair<String, String>>) {
        val tagDao = database.tagDao()
        val trackTagDao = database.trackTagDao()
        database.withTransaction {
            trackTagDao.deleteTrackTagsByTrackIdAndSource(trackId, TagSource.USER)
            if (tags.isNotEmpty()) {
                val tagEntities = tags.map { (name, normalized) ->
                    TagEntity(name = name, nameNormalized = normalized)
                }
                tagDao.insertTags(tagEntities)
                val persisted = tagDao.getTagsByNormalized(tags.map { it.second })
                val idByNormalized = persisted.associateBy({ it.nameNormalized }, { it.id })
                val refs = tags.mapNotNull { (_, normalized) ->
                    val tagId = idByNormalized[normalized] ?: return@mapNotNull null
                    TrackTagEntity(trackId = trackId, tagId = tagId, source = TagSource.USER)
                }
                if (refs.isNotEmpty()) trackTagDao.insertTrackTags(refs)
            }
        }
    }

    /**
     * 重命名用户标签。归一名冲突时把原标签的专辑/音轨引用合并到已有标签后删除原标签。
     * @return 受影响专辑 id 集合（事务前捕获；合并分支并入冲突标签的专辑），供调用方 FTS 刷新。
     *   标签缺失或归一名为空早退时同样返回事务前集合（与原实现一致）。
     */
    suspend fun renameUserTag(tagId: Long, newName: String): Set<Long> {
        val tagDao = database.tagDao()
        val albumIds = tagDao.getAlbumIdsForTag(tagId).toMutableSet()
        database.withTransaction {
            val existing = tagDao.getTagById(tagId) ?: return@withTransaction
            val newNormalized = TagNormalizer.normalize(newName)
            if (newNormalized.isBlank()) return@withTransaction

            val conflict = tagDao.getTagByNormalized(newNormalized)
            if (conflict != null && conflict.id != existing.id) {
                albumIds.addAll(tagDao.getAlbumIdsForTag(conflict.id))
                tagDao.moveAlbumTagsToAnotherTag(existing.id, conflict.id)
                database.trackTagDao().moveTrackTagsToAnotherTag(existing.id, conflict.id)
                tagDao.deleteAlbumTagsByTagId(existing.id)
                database.trackTagDao().deleteTrackTagsByTagId(existing.id)
                tagDao.deleteTag(existing.id)
            } else {
                tagDao.updateTag(existing.id, newName, newNormalized)
            }
        }
        return albumIds
    }

    /** 删除用户标签及其全部专辑/音轨引用。@return 事务前关联的专辑 id，供调用方 FTS 刷新。 */
    suspend fun deleteUserTag(tagId: Long): Set<Long> {
        val tagDao = database.tagDao()
        val albumIds = tagDao.getAlbumIdsForTag(tagId)
        database.withTransaction {
            tagDao.deleteAlbumTagsByTagId(tagId)
            database.trackTagDao().deleteTrackTagsByTagId(tagId)
            tagDao.deleteTag(tagId)
        }
        return albumIds.toSet()
    }

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

    // ---------- R2-B4b：扫描/初始化/聚合写族（行为契约见 docs/behavior-notes/library-delete-family.md 附录） ----------

    /** 刷新专辑 FTS 行：tags + USER 标签 CSV 合并建 tagsToken（原 VM upsertAlbumFtsIndex 逐字下沉）。 */
    suspend fun upsertAlbumFtsIndex(albumId: Long, entity: AlbumEntity) {
        val userTagsCsv = database.tagDao().getAlbumTagsCsvOnce(albumId, TagSource.USER).orEmpty()
        val combinedTagsCsv = buildString {
            append(entity.tags)
            if (userTagsCsv.isNotBlank()) {
                if (isNotEmpty() && last() != ',') append(',')
                append(userTagsCsv)
            }
        }
        val tagsToken = buildTagsToken(combinedTagsCsv)
        database.albumFtsDao().upsert(
            listOf(
                AlbumFtsEntity(
                    albumId = albumId,
                    title = entity.title,
                    circle = entity.circle,
                    cv = entity.cv,
                    rjCode = entity.rjCode,
                    workId = entity.workId,
                    tagsToken = tagsToken
                )
            )
        )
    }

    /**
     * 按 CSV 整替专辑标签（指定 source）：insertTags → getTagsByNormalized →
     * deleteAlbumTagsByAlbumIdExceptSource（保留 USER 源）→ insertAlbumTags（原 VM upsertAlbumTagsFromCsv 逐字下沉）。
     * 空标签直接返回，无任何写。
     */
    suspend fun upsertAlbumTagsFromCsv(albumId: Long, tagsCsv: String, source: Int) {
        val tags = parseAlbumTags(tagsCsv)
        if (tags.isEmpty()) return

        val tagEntities = tags.map { (name, normalized) ->
            TagEntity(name = name, nameNormalized = normalized)
        }
        val tagDao = database.tagDao()
        tagDao.insertTags(tagEntities)

        val normalizedList = tags.map { it.second }
        val persisted = tagDao.getTagsByNormalized(normalizedList)
        val idByNormalized = persisted.associateBy({ it.nameNormalized }, { it.id })

        tagDao.deleteAlbumTagsByAlbumIdExceptSource(albumId, TagSource.USER)
        val refs = normalizedList.mapNotNull { normalized ->
            val tagId = idByNormalized[normalized] ?: return@mapNotNull null
            AlbumTagEntity(albumId = albumId, tagId = tagId, source = source)
        }
        if (refs.isNotEmpty()) tagDao.insertAlbumTags(refs)
    }

    /**
     * 首次启动把专辑 tags CSV 播种为 AUTO 标签引用（原 VM ensureTagTablesInitialized 的事务段逐字下沉）。
     * countTags 空表判定与专辑清单获取由调用方完成。
     */
    suspend fun seedAutoTagsFromAlbumTags(albums: List<AlbumEntity>) {
        val firstNameByNormalized = LinkedHashMap<String, String>()
        albums.flatMap { parseAlbumTags(it.tags) }.forEach { (name, normalized) ->
            if (!firstNameByNormalized.containsKey(normalized)) firstNameByNormalized[normalized] = name
        }
        if (firstNameByNormalized.isEmpty()) return

        val tagDao = database.tagDao()
        val tagEntities = firstNameByNormalized.map { (normalized, name) ->
            TagEntity(name = name, nameNormalized = normalized)
        }
        tagDao.insertTags(tagEntities)
        val persisted = tagDao.getTagsByNormalized(firstNameByNormalized.keys.toList())
        val idByNormalized = persisted.associateBy({ it.nameNormalized }, { it.id })

        database.withTransaction {
            albums.forEach { album ->
                val pairs = parseAlbumTags(album.tags)
                if (pairs.isEmpty()) return@forEach
                val refs = pairs.mapNotNull { (_, normalized) ->
                    val tagId = idByNormalized[normalized] ?: return@mapNotNull null
                    AlbumTagEntity(albumId = album.id, tagId = tagId, source = TagSource.AUTO)
                }
                if (refs.isNotEmpty()) {
                    tagDao.deleteAlbumTagsByAlbumIdExceptSource(album.id, TagSource.USER)
                    tagDao.insertAlbumTags(refs)
                }
            }
        }
    }

    /** 透传 updateAlbum（封面/路径改写等单行更新由调用方组装 entity）。 */
    suspend fun updateAlbum(entity: AlbumEntity) {
        database.albumDao().updateAlbum(entity)
    }

    /** 专辑音频聚合三字段（数量/总时长/总字节）。fileSizeQuery 由调用方注入（文件系统探查属平台侧）。 */
    data class AlbumAudioAggregate(
        val trackCount: Int,
        val totalDuration: Double,
        val totalSizeBytes: Long,
    )

    /** 计算聚合：总字节经 fileSizeQuery 逐轨探查（IO 在 Dispatchers.IO 上），数量/时长为纯求和。 */
    suspend fun computeAlbumAudioAggregate(
        trackSpecs: List<TrackEntity>,
        fileSizeQuery: suspend (String) -> Long?,
    ): AlbumAudioAggregate {
        val totalSizeBytes = withContext(Dispatchers.IO) {
            trackSpecs.sumOf { track -> fileSizeQuery(track.path) ?: 0L }
        }
        return AlbumAudioAggregate(
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
                upsertAlbumFtsIndex(updated.id, updated)
            }
        }
    }

    // ---------- R2-B4b-2：扫描/清理事务族 ----------

    /** 目录树缓存叶子（Gson 载荷；TreeFileType 枚举名与原 VM 私有 CacheTreeFileType 一致，载荷 JSON 不变）。internal：暴露 internal TreeFileType。 */
    internal data class ScanCacheLeaf(val relativePath: String, val absolutePath: String, val fileType: TreeFileType)

    /** 文档树扫描的音轨规格（原 scanFromDocumentTree/scanSingleAlbumFromDocumentUri 内部 TrackSpec）。 */
    data class ScanTrackSpec(val title: String, val path: String, val group: String)

    /**
     * 目录树缓存写（原 VM upsertLocalTreeCache 逐字下沉）。
     * stamp 经注入（File/Document lastModified 探查属平台侧，原实现在调用点同步完成）。
     */
    internal suspend fun upsertLocalTreeCache(
        albumId: Long,
        albumPaths: List<String>,
        leaves: List<ScanCacheLeaf>,
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

    /** 清空专辑目录树缓存。 */
    suspend fun clearLocalTreeCache(albumId: Long) {
        database.localTreeCacheDao().deleteByAlbum(albumId)
    }

    /** 插入专辑行（REPLACE 冲突策略，原 VM 直调 albumDao.insertAlbum 透传）。 */
    suspend fun insertAlbum(entity: AlbumEntity): Long = database.albumDao().insertAlbum(entity)

    /** 删下载任务及其条目：先条目后任务，各自 runCatching 吞错（与原 VM deleteAlbum 收尾逐字一致）。 */
    suspend fun deleteDownloadTaskWithItems(taskId: Long) {
        runCatching { database.downloadDao().deleteItemsForTask(taskId) }
        runCatching { database.downloadDao().deleteTaskById(taskId) }
    }

    /** 下载目录缺失清理事务（原 pruneMissingDownloadedAlbums 事务体逐字下沉；missing 筛选含 File.exists 在调用方）。 */
    suspend fun pruneMissingDownloadedAlbums(missing: List<AlbumEntity>) {
        database.withTransaction {
            missing.forEach { entity ->
                if (entity.localPath.isNullOrBlank() && !entity.path.startsWith("content://")) {
                    deleteAlbumTracksAndSubtitles(entity.id)
                    deleteAlbumEntity(entity)
                } else {
                    val dl = entity.downloadPath?.trim().orEmpty()
                    val tracks = database.trackDao().getTracksForAlbumOnce(entity.id)
                    tracks.filter { it.path.startsWith(dl) }.forEach { track ->
                        deleteTrackWithSubtitlesById(track.id)
                    }

                    val updated = entity.copy(
                        path = if (entity.path.startsWith(dl)) (entity.localPath ?: entity.path) else entity.path,
                        downloadPath = null,
                        coverPath = if (entity.coverPath.startsWith(dl)) "" else entity.coverPath
                    )
                    database.albumDao().updateAlbum(updated)
                    upsertAlbumFtsIndex(updated.id, updated)
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
                        deleteAlbumTracksAndSubtitles(entity.id)
                        deleteAlbumEntity(entity)
                    } else {
                        val root = rootUriString.trim()
                        tracks.filter { it.path.startsWith(root) }.forEach { track ->
                            deleteTrackWithSubtitlesById(track.id)
                        }
                        val updatedPath = if (entity.path.startsWith(root)) (buildOnlineAlbumPath(entity) ?: entity.path) else entity.path
                        val updated = entity.copy(
                            path = updatedPath,
                            localPath = entity.localPath?.takeIf { !it.startsWith(root) },
                            coverPath = if (entity.coverPath.startsWith(root)) "" else entity.coverPath
                        )
                        database.albumDao().updateAlbum(updated)
                        upsertAlbumFtsIndex(updated.id, updated)
                    }
                } else {
                    val root = rootUriString.trim()
                    val tracks = database.trackDao().getTracksForAlbumOnce(entity.id)
                    tracks.filter { it.path.startsWith(root) }.forEach { track ->
                        deleteTrackWithSubtitlesById(track.id)
                    }

                    val updated = entity.copy(
                        path = if (entity.path.startsWith(root)) entity.downloadPath else entity.path,
                        localPath = entity.localPath?.takeIf { !it.startsWith(root) },
                        coverPath = if (entity.coverPath.startsWith(root)) "" else entity.coverPath
                    )
                    database.albumDao().updateAlbum(updated)
                    upsertAlbumFtsIndex(updated.id, updated)
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
                        deleteAlbumTracksAndSubtitles(entity.id)
                        deleteAlbumEntity(entity)
                        return@forEach
                    }
                }

                var updated = entity

                if (localMissing) {
                    val ts = cachedTracks ?: database.trackDao().getTracksForAlbumOnce(entity.id).also { cachedTracks = it }
                    ts.filter { it.path.startsWith(local) }.forEach { track ->
                        deleteTrackWithSubtitlesById(track.id)
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
                        deleteTrackWithSubtitlesById(track.id)
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
                            deleteAlbumTracksAndSubtitles(entity.id)
                            deleteAlbumEntity(entity)
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
                    upsertAlbumFtsIndex(updated.id, updated)
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

    /** 文档树整册扫描入库结果：新专辑 id + 是否写过字幕（供调用方决定歌词重载）。 */
    internal data class DocumentScanResult(val albumId: Long, val wroteAnySubtitles: Boolean)

    /**
     * 文档树整册扫描入库事务（原 scanFromDocumentTree 事务体逐字下沉）。
     * entity/trackSpecs/subtitlesByAudioPath/cacheLeaves 均为调用方预计算的纯数据；
     * fileSizeQuery/stampProvider 注入平台探查（原实现于事务内同步调用）。
     */
    internal suspend fun upsertScannedDocumentAlbum(
        entity: AlbumEntity,
        scanRootPath: String,
        trackSpecs: List<ScanTrackSpec>,
        subtitlesByAudioPath: Map<String, List<SubtitleEntry>>,
        cacheLeaves: List<ScanCacheLeaf>,
        fileSizeQuery: suspend (String) -> Long?,
        stampProvider: (List<String>) -> Long,
    ): DocumentScanResult {
        var wroteAnySubtitles = false
        val insertedAlbumId = database.withTransaction {
            val id = database.albumDao().insertAlbum(entity)
            upsertAlbumFtsIndex(id, entity.copy(id = id))
            upsertAlbumTagsFromCsv(id, entity.tags, TagSource.SCAN)

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

            val tracksToInsert = mutableListOf<Pair<TrackEntity, ScanTrackSpec>>()
            val tracksToUpdate = mutableListOf<Pair<TrackEntity, ScanTrackSpec>>()
            trackSpecs.forEach { spec ->
                val existingTrack = existingUnderRoot[spec.path]
                if (existingTrack == null) {
                    tracksToInsert += TrackEntity(
                        albumId = id,
                        title = spec.title,
                        path = spec.path,
                        duration = 0.0,
                        group = spec.group,
                    ) to spec
                } else {
                    tracksToUpdate += existingTrack.copy(title = spec.title, group = spec.group) to spec
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
        return DocumentScanResult(albumId = insertedAlbumId, wroteAnySubtitles = wroteAnySubtitles)
    }

    /** 文档树单册重扫结果：是否写过字幕 + 持久化路径（供调用方写缓存）。 */
    internal data class DocumentRescanResult(val wroteAnySubtitles: Boolean, val persistedPaths: List<String>)

    /** 文档树单册重扫事务（原 scanSingleAlbumFromDocumentUri 事务体逐字下沉）。 */
    internal suspend fun rescanDocumentAlbum(
        albumId: Long,
        coverPath: String,
        treePrefix: String,
        trackSpecs: List<ScanTrackSpec>,
        subtitlesByAudioPath: Map<String, List<SubtitleEntry>>,
    ): DocumentRescanResult {
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
        return DocumentRescanResult(wroteAnySubtitles = wroteAnySubtitles, persistedPaths = persistedPaths)
    }
}
