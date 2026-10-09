package com.asmr.player.data.download

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.AlbumFtsEntity
import com.asmr.player.data.local.db.entities.RemoteSubtitleSourceEntity
import com.asmr.player.data.local.db.entities.SubtitleEntity
import com.asmr.player.data.local.db.entities.TrackEntity
import com.asmr.player.data.local.library.LocalAlbumMergeService
import com.asmr.player.util.DlsiteWorkNo
import com.asmr.player.util.SubtitleEntry
import com.asmr.player.util.SubtitleMatchSupport
import com.asmr.player.util.SubtitleParser
import com.asmr.player.util.TrackKeyNormalizer
import com.asmr.player.util.isScannableLocalDirectoryName
import com.asmr.player.util.isScannableLocalStorageEntry
import com.asmr.player.work.AlbumCoverThumbWorker
import java.io.File

private const val LIBRARY_UPSERT_TAG = "DownloadLibraryUpsert"

internal suspend fun upsertDownloadedAlbumToLibrary(
    db: com.asmr.player.data.local.db.AppDatabase,
    appContext: Context,
    rootDir: String,
    taskTitle: String,
    taskSubtitle: String,
    albumTitle: String = "",
    albumCircle: String = "",
    albumCv: String = "",
    albumTagsCsv: String = "",
    albumCoverUrl: String = "",
    albumDescription: String = "",
    albumWorkId: String = "",
    albumRjCode: String = ""
) {
    if (rootDir.startsWith("content://")) {
        upsertDownloadedDocumentAlbumToLibrary(
            db = db,
            appContext = appContext,
            rootDir = rootDir,
            taskTitle = taskTitle,
            taskSubtitle = taskSubtitle,
            albumTitle = albumTitle,
            albumCircle = albumCircle,
            albumCv = albumCv,
            albumTagsCsv = albumTagsCsv,
            albumCoverUrl = albumCoverUrl,
            albumDescription = albumDescription,
            albumWorkId = albumWorkId,
            albumRjCode = albumRjCode,
        )
        return
    }
    val dir = File(rootDir)
    if (!dir.exists() || !dir.isDirectory) return

    val titleTrimmed = taskTitle.trim()
    val subtitleTrimmed = taskSubtitle.trim()
    val normalizedWorkId = albumRjCode.trim().ifBlank { albumWorkId.trim() }
    val rj = DlsiteWorkNo.extractWorkNo(normalizedWorkId.ifBlank { titleTrimmed.ifBlank { dir.name } })

    val albumDao = db.albumDao()
    val trackDao = db.trackDao()
    val albumFtsDao = db.albumFtsDao()

    val mergeService = LocalAlbumMergeService(db, DownloadStorageGateway(appContext))
    val existing = mergeService.resolveAndMerge(
        rj = rj,
        fallbackPath = dir.absolutePath,
        fallbackTitle = subtitleTrimmed.ifBlank { albumTitle.trim() }.ifBlank { titleTrimmed },
        localPath = null,
        downloadPath = dir.absolutePath,
    ) ?: try {
        albumDao.getAlbumByPathOnce(dir.absolutePath)
    } catch (e: Exception) {
        Log.w(LIBRARY_UPSERT_TAG, "lookup downloaded album by path failed", e)
        null
    } ?: try {
        albumDao.getAllAlbumsOnce().firstOrNull { album ->
            listOfNotNull(album.localPath, album.downloadPath)
                .map { it.trim() }
                .any { it.equals(dir.absolutePath, ignoreCase = false) }
        }
    } catch (e: Exception) {
        Log.w(LIBRARY_UPSERT_TAG, "scan downloaded albums by directory failed", e)
        null
    } ?: try {
        if (rj.isNotBlank()) albumDao.getAlbumByWorkIdOnce(rj) else null
    } catch (e: Exception) {
        Log.w(LIBRARY_UPSERT_TAG, "lookup downloaded album by work id failed", e)
        null
    }

    val cover = pickCoverFileFromAlbumDir(dir)
    val existingCoverPath = existing?.coverPath
        ?.takeIf { it.isNotBlank() && it != "null" }
    val entity = AlbumEntity(
        id = existing?.id ?: 0L,
        title = subtitleTrimmed
            .ifBlank { albumTitle.trim() }
            .ifBlank { existing?.title?.takeIf { it.isNotBlank() } ?: titleTrimmed.ifBlank { dir.name } },
        path = existing?.path?.takeIf { it.isNotBlank() } ?: dir.absolutePath,
        localPath = existing?.localPath,
        downloadPath = dir.absolutePath,
        circle = existing?.circle?.takeIf { it.isNotBlank() } ?: albumCircle.trim(),
        cv = existing?.cv?.takeIf { it.isNotBlank() } ?: albumCv.trim(),
        tags = existing?.tags?.takeIf { it.isNotBlank() } ?: albumTagsCsv.trim(),
        coverUrl = existing?.coverUrl?.takeIf { it.isNotBlank() } ?: albumCoverUrl.trim(),
        coverPath = resolveDownloadedAlbumCoverPath(
            existingCoverPath = existingCoverPath,
            downloadedCoverPath = cover?.absolutePath
        ),
        coverThumbPath = if (existingCoverPath != null) existing.coverThumbPath else "",
        workId = existing?.workId?.takeIf { it.isNotBlank() }
            ?: albumWorkId.trim().ifBlank { rj },
        rjCode = existing?.rjCode?.takeIf { it.isNotBlank() }
            ?: albumRjCode.trim().ifBlank { rj },
        description = existing?.description?.takeIf { it.isNotBlank() } ?: albumDescription.trim(),
        // 下载入库路径：新专辑定性 dlsite_download；已有专辑保留原 source（永不覆盖，见 behavior-notes/scan-metadata-sourcing.md）
        source = existing?.source?.takeIf { it.isNotBlank() } ?: AlbumEntity.SOURCE_DLSITE_DOWNLOAD
    )

    val albumId = try {
        if (existing == null) {
            albumDao.insertAlbum(entity)
        } else {
            albumDao.updateAlbum(entity)
            entity.id
        }
    } catch (e: Exception) {
        Log.w(LIBRARY_UPSERT_TAG, "upsert downloaded album row failed dir=${dir.name}", e)
        0L
    }
    if (albumId <= 0L) return

    val coverThumbWork = OneTimeWorkRequestBuilder<AlbumCoverThumbWorker>()
        .setInputData(workDataOf(AlbumCoverThumbWorker.KEY_ALBUM_ID to albumId))
        .addTag("album_cover_thumb")
        .build()
    WorkManager.getInstance(appContext)
        .enqueueUniqueWork("album_cover_thumb_$albumId", ExistingWorkPolicy.REPLACE, coverThumbWork)

    val fts = AlbumFtsEntity(
        albumId = albumId,
        title = entity.title,
        circle = entity.circle,
        cv = entity.cv,
        rjCode = entity.rjCode,
        workId = entity.workId,
        tagsToken = entity.tags.replace(',', ' ').trim()
    )
    try {
        albumFtsDao.upsert(listOf(fts))
    } catch (e: Exception) {
        Log.w(LIBRARY_UPSERT_TAG, "upsert downloaded album fts failed", e)
    }

    val audioExtensions = setOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "opus")
    val audioFiles = dir.walkTopDown()
        .onEnter { directory -> directory == dir || isScannableLocalDirectoryName(directory.name) }
        .filter { it.isFile && audioExtensions.contains(it.extension.lowercase()) }
        .toList()
        .sortedBy { it.absolutePath.lowercase() }
    val subtitleCandidates = dir.walkTopDown()
        .onEnter { directory -> directory == dir || isScannableLocalDirectoryName(directory.name) }
        .filter { it.isFile && SubtitleMatchSupport.SubtitleExtensions.contains(it.extension.lowercase()) }
        .mapNotNull { file ->
            val relative = runCatching { file.relativeTo(dir).path.replace('\\', '/') }.getOrNull().orEmpty()
            val candidate = SubtitleMatchSupport.inferCandidate(relative, file.absolutePath) ?: return@mapNotNull null
            candidate to file
        }
        .toList()
    val subtitleCandidateList = subtitleCandidates.map { it.first }

    fun parseBestSubtitle(audio: File): List<SubtitleEntry> {
        val relativePathNoExt = runCatching { audio.relativeTo(dir).path.replace('\\', '/') }
            .getOrElse { audio.name }
            .substringBeforeLast('.')
        val matched = SubtitleMatchSupport.matchBest(relativePathNoExt, subtitleCandidateList) ?: return emptyList()
        val subtitleFile = subtitleCandidates.firstOrNull { it.first.sourceRef == matched.sourceRef }?.second ?: return emptyList()
        return runCatching { SubtitleParser.parse(subtitleFile.absolutePath) }.getOrDefault(emptyList())
    }

    val existingTracks = try {
        trackDao.getTracksForAlbumOnce(albumId)
    } catch (e: Exception) {
        Log.w(LIBRARY_UPSERT_TAG, "load existing downloaded tracks failed", e)
        emptyList()
    }
    val prefix = dir.absolutePath.trimEnd('\\', '/') + File.separator
    val audioIdentities = audioFiles.map { file -> runCatching { file.canonicalPath }.getOrDefault(file.absolutePath) }.toSet()
    val existingIdentities = existingTracks.associateBy { track ->
        runCatching { File(track.path).canonicalPath }.getOrDefault(track.path)
    }
    val toDelete = existingTracks.filter { track ->
        track.path.startsWith(dir.absolutePath) &&
            runCatching { File(track.path).canonicalPath }.getOrDefault(track.path) !in audioIdentities
    }.map { it.id }
    if (toDelete.isNotEmpty()) {
        runCatching { trackDao.deleteSubtitlesForTracks(toDelete) }
        runCatching { db.remoteSubtitleSourceDao().deleteByTrackIds(toDelete) }
        runCatching { db.trackTagDao().deleteTrackTagsByTrackIds(toDelete) }
        runCatching { trackDao.deleteTracksByIds(toDelete) }
    }

    val filteredAudioFiles = ArrayList<File>(audioFiles.size)
    audioFiles.forEach { f ->
        val identity = runCatching { f.canonicalPath }.getOrDefault(f.absolutePath)
        if (identity !in existingIdentities) filteredAudioFiles += f
    }

    val newTracks = filteredAudioFiles.map { f ->
        val group = if (f.parentFile != null && f.parentFile?.absolutePath != dir.absolutePath) f.parentFile?.name.orEmpty() else ""
        TrackEntity(
            albumId = albumId,
            title = f.nameWithoutExtension.ifBlank { "track" },
            path = f.absolutePath,
            duration = 0.0,
            group = group
        )
    }
    if (newTracks.isNotEmpty()) runCatching { trackDao.insertTracks(newTracks) }

    val indexedTracksByIdentity = trackDao.getTracksForAlbumOnce(albumId).associateBy { track ->
        runCatching { File(track.path).canonicalPath }.getOrDefault(track.path)
    }
    audioFiles.forEach { audio ->
        val entries = parseBestSubtitle(audio)
        if (entries.isEmpty()) return@forEach
        val identity = runCatching { audio.canonicalPath }.getOrDefault(audio.absolutePath)
        val trackId = indexedTracksByIdentity[identity]?.id ?: return@forEach
        runCatching {
            trackDao.deleteSubtitlesForTrack(trackId)
            trackDao.insertSubtitles(entries.map { entry -> entry.toEntity(trackId) })
        }
    }

    replaceMatchedOnlineTracksWithLocalTracks(db, albumId, prefix)
    mergeService.deduplicateTracks(albumId)
    runCatching { db.localTreeCacheDao().deleteByAlbum(albumId) }
}

private suspend fun upsertDownloadedDocumentAlbumToLibrary(
    db: com.asmr.player.data.local.db.AppDatabase,
    appContext: Context,
    rootDir: String,
    taskTitle: String,
    taskSubtitle: String,
    albumTitle: String,
    albumCircle: String,
    albumCv: String,
    albumTagsCsv: String,
    albumCoverUrl: String,
    albumDescription: String,
    albumWorkId: String,
    albumRjCode: String,
) {
    val storage = DownloadStorageGateway(appContext)
    val entries = storage.walk(rootDir).filter { entry ->
        isScannableLocalStorageEntry(entry.relativePath, entry.isDirectory)
    }
    if (entries.isEmpty()) return

    val titleTrimmed = taskTitle.trim()
    val subtitleTrimmed = taskSubtitle.trim()
    val normalizedWorkId = albumRjCode.trim().ifBlank { albumWorkId.trim() }
    val rj = DlsiteWorkNo.extractWorkNo(normalizedWorkId.ifBlank { titleTrimmed })
    val albumDao = db.albumDao()
    val trackDao = db.trackDao()
    val albumFtsDao = db.albumFtsDao()
    val mergeService = LocalAlbumMergeService(db, storage)
    val existing = mergeService.resolveAndMerge(
        rj = rj,
        fallbackPath = rootDir,
        fallbackTitle = subtitleTrimmed.ifBlank { albumTitle.trim() }.ifBlank { titleTrimmed },
        localPath = null,
        downloadPath = rootDir,
    ) ?: albumDao.getAlbumByPathOnce(rootDir)
    val cover = entries.firstOrNull { entry ->
        !entry.isDirectory &&
            entry.displayName.substringBeforeLast('.').equals("cover", ignoreCase = true) &&
            entry.displayName.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp")
    } ?: entries.firstOrNull { entry ->
        !entry.isDirectory && entry.displayName.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp")
    }
    val audioEntries = entries.filter { entry ->
        !entry.isDirectory && entry.displayName.substringAfterLast('.', "").lowercase() in
            setOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "opus")
    }
    val subtitlesByAudioReference = parseDownloadedSubtitles(entries) { subtitle ->
        storage.openInput(subtitle.reference).use { input -> input.readBytes() }
    }
    val base = existing ?: AlbumEntity(title = "", path = rootDir)
    val entity = base.copy(
        title = subtitleTrimmed
            .ifBlank { albumTitle.trim() }
            .ifBlank { base.title.ifBlank { titleTrimmed.ifBlank { rj.ifBlank { "album" } } } },
        path = base.path.takeIf { it.isNotBlank() } ?: rootDir,
        downloadPath = rootDir,
        circle = base.circle.ifBlank { albumCircle.trim() },
        cv = base.cv.ifBlank { albumCv.trim() },
        tags = base.tags.ifBlank { albumTagsCsv.trim() },
        coverUrl = base.coverUrl.ifBlank { albumCoverUrl.trim() },
        coverPath = base.coverPath.ifBlank { cover?.reference.orEmpty() },
        workId = base.workId.ifBlank { albumWorkId.trim().ifBlank { rj } },
        rjCode = base.rjCode.ifBlank { albumRjCode.trim().ifBlank { rj } },
        description = base.description.ifBlank { albumDescription.trim() },
        audioTrackCount = audioEntries.size,
        audioTotalSizeBytes = audioEntries.sumOf { it.sizeBytes },
    )
    val albumId = if (existing == null) albumDao.insertAlbum(entity) else {
        albumDao.updateAlbum(entity)
        entity.id
    }
    if (albumId <= 0L) return

    val existingTracks = trackDao.getTracksForAlbumOnce(albumId)
    val audioIdentities = audioEntries.map { storage.stableIdentity(it.reference) }.toSet()
    val existingIdentities = existingTracks.associateBy { storage.stableIdentity(it.path) }
    val staleTracks = existingTracks.filter { track ->
        storage.isSameOrDescendant(track.path, rootDir) && storage.stableIdentity(track.path) !in audioIdentities
    }
    if (staleTracks.isNotEmpty()) {
        val ids = staleTracks.map { it.id }
        trackDao.deleteSubtitlesForTracks(ids)
        db.remoteSubtitleSourceDao().deleteByTrackIds(ids)
        db.trackTagDao().deleteTrackTagsByTrackIds(ids)
        trackDao.deleteTracksByIds(ids)
    }

    val seenReferences = linkedSetOf<String>()
    val tracksToInsert = mutableListOf<TrackEntity>()
    val tracksToUpdate = mutableListOf<TrackEntity>()
    audioEntries.forEach { entry ->
        val identity = storage.stableIdentity(entry.reference)
        if (!seenReferences.add(identity)) return@forEach
        val title = entry.displayName.substringBeforeLast('.').ifBlank { "track" }
        val group = entry.relativePath.substringBeforeLast('/', "").substringAfterLast('/', "")
        val existingTrack = existingIdentities[identity]
        if (existingTrack == null) {
            tracksToInsert += TrackEntity(
                albumId = albumId,
                title = title,
                path = entry.reference,
                duration = 0.0,
                group = group,
            )
        } else {
            tracksToUpdate += existingTrack.copy(title = title, group = group)
        }
    }
    if (tracksToUpdate.isNotEmpty()) trackDao.updateTracks(tracksToUpdate)
    if (tracksToInsert.isNotEmpty()) trackDao.insertTracks(tracksToInsert)

    val indexedTracksByIdentity = trackDao.getTracksForAlbumOnce(albumId)
        .associateBy { track -> storage.stableIdentity(track.path) }
    audioEntries.forEach { audio ->
        val entriesForTrack = subtitlesByAudioReference[audio.reference].orEmpty()
        if (entriesForTrack.isEmpty()) return@forEach
        val trackId = indexedTracksByIdentity[storage.stableIdentity(audio.reference)]?.id ?: return@forEach
        trackDao.deleteSubtitlesForTrack(trackId)
        trackDao.insertSubtitles(entriesForTrack.map { entry -> entry.toEntity(trackId) })
    }
    replaceMatchedOnlineTracksWithLocalTracks(db, albumId, rootDir)
    mergeService.deduplicateTracks(albumId)
    albumFtsDao.upsert(
        listOf(
            AlbumFtsEntity(
                albumId = albumId,
                title = entity.title,
                circle = entity.circle,
                cv = entity.cv,
                rjCode = entity.rjCode,
                workId = entity.workId,
                tagsToken = entity.tags.replace(',', ' ').trim(),
            )
        )
    )
    db.localTreeCacheDao().deleteByAlbum(albumId)
    WorkManager.getInstance(appContext)
        .enqueueUniqueWork(
            "album_cover_thumb_$albumId",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<AlbumCoverThumbWorker>()
                .setInputData(workDataOf(AlbumCoverThumbWorker.KEY_ALBUM_ID to albumId))
                .addTag("album_cover_thumb")
                .build(),
        )
}

private fun SubtitleEntry.toEntity(trackId: Long): SubtitleEntity = SubtitleEntity(
    trackId = trackId,
    startMs = startMs,
    endMs = endMs,
    text = text,
)

internal suspend fun replaceMatchedOnlineTracksWithLocalTracks(
    db: com.asmr.player.data.local.db.AppDatabase,
    albumId: Long,
    preferredLocalPrefix: String
) {
    val trackDao = db.trackDao()
    val remoteSubtitleSourceDao = db.remoteSubtitleSourceDao()
    val allTracks = runCatching { trackDao.getTracksForAlbumOnce(albumId) }.getOrDefault(emptyList())
    val localTracks = allTracks.filter { !it.path.trim().startsWith("http", ignoreCase = true) }
    val orderedLocalTracks = localTracks
        .sortedWith(compareByDescending<TrackEntity> { it.path.startsWith(preferredLocalPrefix) }.thenBy { it.id })
    val localTracksByKey = orderedLocalTracks.groupBy { track ->
        TrackKeyNormalizer.buildKey(track.title, track.group, null)
    }
    val localTracksByKeyWithoutGroup = orderedLocalTracks.groupBy { track ->
        TrackKeyNormalizer.buildKey(track.title, "", null)
    }
    val onlineTracks = allTracks.filter { it.path.trim().startsWith("http", ignoreCase = true) }
    val consumedLocalTrackIds = linkedSetOf<Long>()
    val matchedOnlineTrackIds = linkedSetOf<Long>()
    val matchedPairs = mutableListOf<Pair<TrackEntity, TrackEntity>>()

    onlineTracks.forEach { online ->
        val key = TrackKeyNormalizer.buildKey(online.title, online.group, null)
        val target = localTracksByKey[key]
            ?.firstOrNull { local -> local.id !in consumedLocalTrackIds }
            ?: return@forEach
        consumedLocalTrackIds += target.id
        matchedOnlineTrackIds += online.id
        matchedPairs += online to target
    }

    onlineTracks
        .filter { online -> online.id !in matchedOnlineTrackIds }
        .groupBy { online -> TrackKeyNormalizer.buildKey(online.title, "", null) }
        .forEach { (keyWithoutGroup, unmatchedOnlineTracks) ->
            val remainingLocalTracks = localTracksByKeyWithoutGroup[keyWithoutGroup]
                .orEmpty()
                .filter { local -> local.id !in consumedLocalTrackIds }
            if (unmatchedOnlineTracks.size == 1 && remainingLocalTracks.size == 1) {
                val target = remainingLocalTracks.single()
                consumedLocalTrackIds += target.id
                val online = unmatchedOnlineTracks.single()
                matchedOnlineTrackIds += online.id
                matchedPairs += online to target
            }
        }

    val onlineIdsToDelete = ArrayList<Long>()
    matchedPairs.forEach { (online, target) ->
        val sourceSubs = runCatching { trackDao.getSubtitlesForTrack(online.id) }.getOrDefault(emptyList())
        if (sourceSubs.isNotEmpty()) {
            val targetHasSubs = runCatching { trackDao.getSubtitlesForTrack(target.id) }.getOrDefault(emptyList()).isNotEmpty()
            if (!targetHasSubs) {
                runCatching {
                    trackDao.insertSubtitles(
                        sourceSubs.map { subtitle ->
                            SubtitleEntity(
                                trackId = target.id,
                                startMs = subtitle.startMs,
                                endMs = subtitle.endMs,
                                text = subtitle.text
                            )
                        }
                    )
                }
            }
        }

        val remoteSources = runCatching { remoteSubtitleSourceDao.getSourcesForTrackOnce(online.id) }.getOrDefault(emptyList())
        if (remoteSources.isNotEmpty()) {
            val targetHasRemoteSources = runCatching {
                remoteSubtitleSourceDao.getSourcesForTrackOnce(target.id)
            }.getOrDefault(emptyList()).isNotEmpty()
            if (!targetHasRemoteSources) {
                runCatching {
                    remoteSubtitleSourceDao.insertAll(
                        remoteSources.map { source ->
                            RemoteSubtitleSourceEntity(
                                trackId = target.id,
                                url = source.url,
                                language = source.language,
                                ext = source.ext
                            )
                        }
                    )
                }
            }
        }

        onlineIdsToDelete += online.id
    }

    if (onlineIdsToDelete.isNotEmpty()) {
        runCatching { trackDao.deleteSubtitlesForTracks(onlineIdsToDelete) }
        runCatching { remoteSubtitleSourceDao.deleteByTrackIds(onlineIdsToDelete) }
        runCatching { trackDao.deleteTracksByIds(onlineIdsToDelete) }
    }
}

internal fun resolveDownloadedAlbumCoverPath(
    existingCoverPath: String?,
    downloadedCoverPath: String?
): String {
    return existingCoverPath
        ?.takeIf { it.isNotBlank() && it != "null" }
        ?: downloadedCoverPath.orEmpty()
}

private fun pickCoverFileFromAlbumDir(dir: File): File? {
    val exts = setOf("jpg", "jpeg", "png", "webp")
    val direct = dir.listFiles()?.firstOrNull { f ->
        f.isFile && f.nameWithoutExtension.equals("cover", ignoreCase = true) && exts.contains(f.extension.lowercase())
    }
    if (direct != null) return direct
    return dir.walkTopDown()
        .firstOrNull { f -> f.isFile && exts.contains(f.extension.lowercase()) }
}
