package com.asmr.player.data.repository

import androidx.room.withTransaction
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.AlbumFtsEntity
import com.asmr.player.data.local.db.entities.OnlineSavedResourceEntity
import com.asmr.player.data.local.db.entities.RemoteSubtitleSourceEntity
import com.asmr.player.data.local.db.entities.TrackEntity
import com.asmr.player.domain.model.Album

/**
 * R3-C3：在线保存写族实现（自 LibraryWriteRepository 逐字搬移，逻辑未改）。
 * 门面 [LibraryWriteRepository] 保留全部签名委托至此；调用方零改动。
 */
internal class LibraryOnlineSaveSupport(
    private val database: AppDatabase,
    private val autoClassify: AutoClassifySupport,
) {
    /**
     * 在线选择保存到本地库事务（原 AlbumDetailViewModel saveOnlineSelectedToLibrary 事务体逐字下沉）。
     * existing 按 targetLocalAlbumId 优先、workKey 兜底读取；专辑行/FTS/音轨/远程字幕源/在线资源逐段 runCatching。
     * ⚠️ FTS 的 tagsToken = entity.tags.replace(',', ' ').trim()——与 upsertAlbumFtsIndex 的合并逻辑不同，
     * 是原实现两处独立写法，勿"顺手"统一。url 去重（canonicalUrl）在事务内经注入调用（原样）。
     */
    internal suspend fun saveOnlineSelectedToLibrary(
        targetLocalAlbumId: Long?,
        workKey: String,
        onlinePath: String,
        albumDirPath: String,
        displayAlbum: Album,
        rj: String,
        playableLeaves: List<LibraryWriteRepository.OnlineSaveTrackSpec>,
        resourceLeaves: List<LibraryWriteRepository.OnlineSaveResourceSpec>,
        canonicalUrl: (String) -> String,
    ): LibraryWriteRepository.OnlineSaveResult {
        var insertedCount = 0
        var resourceSavedCount = 0
        var albumIdResult = 0L
        database.withTransaction {
            val existing = targetLocalAlbumId
                ?.let { database.albumDao().getAlbumById(it) }
                ?: if (workKey.isNotBlank()) {
                    database.albumDao().getAlbumByWorkIdOnce(workKey)
                } else {
                    null
                }

            val tagsCsv = displayAlbum.tags.joinToString(",")
            val entity = AlbumEntity(
                id = existing?.id ?: 0L,
                title = existing?.title?.takeIf { it.isNotBlank() } ?: displayAlbum.title,
                path = existing?.path?.takeIf { it.isNotBlank() } ?: onlinePath,
                localPath = existing?.localPath?.takeIf { it.isNotBlank() } ?: albumDirPath,
                downloadPath = existing?.downloadPath,
                circle = existing?.circle?.takeIf { it.isNotBlank() } ?: displayAlbum.circle,
                cv = existing?.cv?.takeIf { it.isNotBlank() } ?: displayAlbum.cv,
                tags = existing?.tags?.takeIf { it.isNotBlank() } ?: tagsCsv,
                coverUrl = existing?.coverUrl?.takeIf { it.isNotBlank() } ?: displayAlbum.coverUrl,
                coverPath = existing?.coverPath.orEmpty(),
                coverThumbPath = existing?.coverThumbPath.orEmpty(),
                workId = existing?.workId?.takeIf { it.isNotBlank() } ?: displayAlbum.workId.trim().ifBlank { workKey },
                rjCode = existing?.rjCode?.takeIf { it.isNotBlank() } ?: displayAlbum.rjCode.trim().ifBlank { rj },
                description = existing?.description?.takeIf { it.isNotBlank() } ?: displayAlbum.description.trim(),
                // 在线保存：仅保留既有 source（非 dlsite/扫描两条定性管线，新建留 null 走「其它」兜底归类），永不覆盖
                source = existing?.source?.takeIf { it.isNotBlank() }
            )
            val insertedId = runCatching { database.albumDao().insertAlbum(entity) }.getOrDefault(0L)
            val albumId = if (insertedId > 0L) insertedId else (existing?.id ?: 0L)
            if (albumId <= 0L) return@withTransaction
            albumIdResult = albumId
            runCatching { database.localTreeCacheDao().deleteByAlbum(albumId) }

            val fts = AlbumFtsEntity(
                albumId = albumId,
                title = entity.title,
                circle = entity.circle,
                cv = entity.cv,
                rjCode = entity.rjCode,
                workId = entity.workId,
                tagsToken = entity.tags.replace(',', ' ').trim()
            )
            runCatching { database.albumFtsDao().upsert(listOf(fts)) }

            val existingTracks = runCatching { database.trackDao().getTracksForAlbumOnce(albumId) }.getOrDefault(emptyList())
            val existingUrlKeys = existingTracks
                .asSequence()
                .map { canonicalUrl(it.path) }
                .filter { it.isNotBlank() }
                .toSet()

            val seenUrlKeys = linkedSetOf<String>()
            val newLeaves = playableLeaves.filter { leaf ->
                val urlKey = canonicalUrl(leaf.url)
                val duplicate = urlKey.isBlank() ||
                    existingUrlKeys.contains(urlKey) ||
                    seenUrlKeys.contains(urlKey)
                if (!duplicate) {
                    seenUrlKeys.add(urlKey)
                    true
                } else {
                    false
                }
            }
            val newTracks = newLeaves.map { leaf ->
                TrackEntity(
                    albumId = albumId,
                    title = leaf.title,
                    path = leaf.url.trim(),
                    duration = leaf.duration,
                    group = leaf.group
                )
            }
            if (newTracks.isNotEmpty()) {
                val insertedTrackIds = runCatching { database.trackDao().insertTracks(newTracks) }.getOrDefault(emptyList())
                insertedCount = insertedTrackIds.count { it > 0L }
                // T7：在线保存新插轨挂默认合集（新专辑 source 留 null → 其它音频；仅挂真正插入成功的轨，
                // 见 behavior-notes/collection-auto-classify.md）。
                runCatching {
                    autoClassify.attachTracksToDefaultGroups(
                        albumId,
                        insertedTrackIds.zip(newTracks).filter { it.first > 0L }.map { it.second.path },
                        entity.source,
                    )
                }
                val sources = insertedTrackIds.zip(newLeaves).flatMap { (trackId, leaf) ->
                    leaf.subtitleSources.mapNotNull { src ->
                        val url = src.url.trim()
                        if (url.isBlank()) return@mapNotNull null
                        RemoteSubtitleSourceEntity(
                            trackId = trackId,
                            url = url,
                            language = src.language,
                            ext = src.ext
                        )
                    }
                }
                if (sources.isNotEmpty()) {
                    runCatching { database.remoteSubtitleSourceDao().insertAll(sources) }
                }
            }

            val existingResourcesByPath = database.onlineSavedResourceDao().getForAlbumOnce(albumId)
                .associateBy { it.relativePath }
            val changedResources = resourceLeaves.mapNotNull { leaf ->
                val relativePath = leaf.relativePath.replace('\\', '/').trim().trimStart('/')
                val url = leaf.url.trim()
                if (relativePath.isBlank() || !url.startsWith("http", ignoreCase = true)) {
                    return@mapNotNull null
                }
                val existingResource = existingResourcesByPath[relativePath]
                val resource = OnlineSavedResourceEntity(
                    id = existingResource?.id ?: 0L,
                    albumId = albumId,
                    relativePath = relativePath,
                    url = url,
                    fileType = leaf.fileType.name
                )
                resource.takeUnless { it == existingResource }
            }.distinctBy { it.relativePath }
            if (changedResources.isNotEmpty()) {
                database.onlineSavedResourceDao().insertAll(changedResources)
                resourceSavedCount = changedResources.size
            }
        }
        return LibraryWriteRepository.OnlineSaveResult(albumId = albumIdResult, insertedCount = insertedCount, resourceSavedCount = resourceSavedCount)
    }
}
