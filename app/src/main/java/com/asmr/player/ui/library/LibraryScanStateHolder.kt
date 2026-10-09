package com.asmr.player.ui.library

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.asmr.player.data.download.DownloadDestination
import com.asmr.player.data.download.DownloadDestinationStore
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.TrackEntity
import com.asmr.player.data.local.library.LocalAlbumMergeService
import com.asmr.player.data.local.library.buildOnlineAlbumPath
import com.asmr.player.data.local.library.ensureLibraryAlbumDir
import com.asmr.player.data.local.library.legacyOnlineSavedAlbumFolderName
import com.asmr.player.data.local.tree.SafDocNode
import com.asmr.player.data.local.tree.SafTreeSupport
import com.asmr.player.data.repository.LibraryReadRepository
import com.asmr.player.data.repository.LibraryWriteRepository
import com.asmr.player.data.repository.LibraryWriteRepository.ScanCacheLeaf
import com.asmr.player.data.repository.LibraryWriteRepository.ScanTrackSpec
import com.asmr.player.domain.model.TagSource
import com.asmr.player.domain.model.TreeFileType
import com.asmr.player.domain.model.treeFileTypeForName
import com.asmr.player.playback.PlayerConnection
import com.asmr.player.ui.common.audio.queryTrackFileSize
import com.asmr.player.util.DlsiteWorkNo
import com.asmr.player.util.EmbeddedMediaExtractor
import com.asmr.player.util.MessageManager
import com.asmr.player.util.ScanRootsStore
import com.asmr.player.util.SubtitleEntry
import com.asmr.player.util.SubtitleMatchSupport
import com.asmr.player.util.SubtitleParser
import com.asmr.player.util.SyncCoordinator
import com.asmr.player.util.isOnlineTrackPath
import com.asmr.player.util.isScannableLocalDirectoryName
import com.asmr.player.util.isVirtualAlbumPath
import com.asmr.player.work.AlbumCoverThumbWorker
import com.asmr.player.work.TrackDurationWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * R3-C1c：LibraryViewModel 扫描族 State Holder（自 VM 逐字搬移，逻辑未改）。
 * - 承接扫描底层：封面挑选、树缓存叶、SAF 遍历、字幕匹配、下载目录/文档树扫描、
 *   孤儿清理、WorkManager 后处理任务入队，以及 resolveAndMerge 合并入口。
 * - C1c-b 承接扫描根管理（scanRootsStore/_scanRoots/scanRoots、addScanRoot/
 *   removeScanRoot/removeScanRootAndDeleteAlbums）与三个批量扫描入口
 *   （scanAllRoots/scanCurrentDownloadDestinationAsImport/scanSingleRoot，含 scanMutex）。
 * - 批量进度回报经 [taskCoordinator]（C1b-ii-a 就位）；批量任务全局门经
 *   [syncCoordinator]；字幕写库后经 [playerConnection] 触发歌词重载。
 * - SafTreeSupport 委托随迁消除（queryChildren/walkTree/documentExists/
 *   readSubtitleFromUri 直调实现）。
 * - TAG 保持 "LibraryViewModel"：日志输出与抽取前逐字一致。
 */
internal class LibraryScanStateHolder(
    private val scope: CoroutineScope,
    private val context: Context,
    private val readRepository: LibraryReadRepository,
    private val writeRepository: LibraryWriteRepository,
    private val downloadDestinationStore: DownloadDestinationStore,
    private val localAlbumMergeService: LocalAlbumMergeService,
    private val playerConnection: PlayerConnection,
    private val taskCoordinator: LibraryTaskCoordinator,
    private val syncCoordinator: SyncCoordinator,
    private val messageManager: MessageManager,
) {
    private companion object {
        const val TAG = "LibraryViewModel"
    }

    private val scanRootsStore = ScanRootsStore(context)
    private val _scanRoots = MutableStateFlow<Set<String>>(emptySet())
    val scanRoots: StateFlow<List<String>> = _scanRoots
        .map { it.toList().sorted() }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val scanMutex = Mutex()

    /** VM init 自动扫描判定读取（行为保持：原 VM 直读 scanRootsStore）。 */
    fun getRootsFromStore(): Set<String> = scanRootsStore.getRoots()

    /** VM init 恢复扫描根列表（原 init 首行逐字随迁）。 */
    fun restoreScanRootsFromStore() {
        _scanRoots.value = runCatching { scanRootsStore.getRoots() }.getOrDefault(emptySet())
    }

    private fun isImageName(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").trim().lowercase()
        return ext in setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")
    }

    private fun pickCoverFileFromAlbumDir(albumDir: File): File? {
        val top = albumDir.listFiles()?.toList().orEmpty()
        val named = top.firstOrNull { it.isFile && it.name.startsWith("cover.", ignoreCase = true) && isImageName(it.name) }
        if (named != null) return named

        val topImages = top.filter { it.isFile && isImageName(it.name) }
        val topLargest = topImages.maxByOrNull { it.length() }
        if (topLargest != null) return topLargest

        var best: File? = null
        var bestSize = 0L
        albumDir.walkTopDown()
            .onEnter { directory -> directory == albumDir || isScannableLocalDirectoryName(directory.name) }
            .forEach { f ->
            if (!f.isFile) return@forEach
            if (!isImageName(f.name)) return@forEach
            val size = runCatching { f.length() }.getOrDefault(0L)
            if (size > bestSize) {
                bestSize = size
                best = f
            }
        }
        return best
    }

    private fun pickCoverNode(nodes: List<SafDocNode>, treeUri: Uri, albumDocumentId: String): String {
        val named = nodes.firstOrNull { it.mimeType != DocumentsContract.Document.MIME_TYPE_DIR && it.displayName.startsWith("cover.", ignoreCase = true) && isImageName(it.displayName) }
        if (named != null) {
            return DocumentsContract.buildDocumentUriUsingTree(treeUri, named.documentId).toString()
        }

        val images = nodes.filter { it.mimeType != DocumentsContract.Document.MIME_TYPE_DIR && isImageName(it.displayName) }
        val largest = images.maxByOrNull { it.sizeBytes }
        if (largest != null) {
            return DocumentsContract.buildDocumentUriUsingTree(treeUri, largest.documentId).toString()
        }

        val deep = walkTree(treeUri, albumDocumentId)
            .filter { it.mimeType != DocumentsContract.Document.MIME_TYPE_DIR && isImageName(it.displayName) }
            .maxByOrNull { it.sizeBytes }
        return deep?.let { DocumentsContract.buildDocumentUriUsingTree(treeUri, it.documentId).toString() }.orEmpty()
    }

    private enum class CacheTreeFileType {
        Audio,
        Video,
        Image,
        Subtitle,
        Text,
        Pdf,
        Archive,
        Document,
        Spreadsheet,
        Presentation,
        Code,
        Ebook,
        Font,
        AppPackage,
        Other
    }

    private data class CacheLeafEntry(
        val relativePath: String,
        val absolutePath: String,
        val fileType: CacheTreeFileType
    )

    private fun cacheFileTypeForName(fileName: String): CacheTreeFileType {
        return when (treeFileTypeForName(fileName)) {
            TreeFileType.Audio -> CacheTreeFileType.Audio
            TreeFileType.Video -> CacheTreeFileType.Video
            TreeFileType.Image -> CacheTreeFileType.Image
            TreeFileType.Subtitle -> CacheTreeFileType.Subtitle
            TreeFileType.Text -> CacheTreeFileType.Text
            TreeFileType.Pdf -> CacheTreeFileType.Pdf
            TreeFileType.Archive -> CacheTreeFileType.Archive
            TreeFileType.Document -> CacheTreeFileType.Document
            TreeFileType.Spreadsheet -> CacheTreeFileType.Spreadsheet
            TreeFileType.Presentation -> CacheTreeFileType.Presentation
            TreeFileType.Code -> CacheTreeFileType.Code
            TreeFileType.Ebook -> CacheTreeFileType.Ebook
            TreeFileType.Font -> CacheTreeFileType.Font
            TreeFileType.AppPackage -> CacheTreeFileType.AppPackage
            TreeFileType.Other -> CacheTreeFileType.Other
        }
    }

    private fun computePathsStamp(paths: List<String>): Long {
        val items = paths.map { it.trim() }.filter { it.isNotBlank() }.sorted()
        var acc = 1469598103934665603L
        items.forEach { p ->
            val v = if (p.startsWith("content://")) queryDocumentLastModified(p) else runCatching { File(p).lastModified() }.getOrDefault(0L)
            acc = (acc xor v) * 1099511628211L
        }
        return acc
    }

    private fun queryDocumentLastModified(uriString: String): Long {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return 0L
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED),
                null,
                null,
                null
            )?.use { cursor ->
                val idx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                if (idx < 0) return@use 0L
                if (!cursor.moveToFirst()) return@use 0L
                cursor.getLong(idx)
            } ?: 0L
        }.getOrDefault(0L)
    }

    private suspend fun upsertLocalTreeCache(albumId: Long, albumPaths: List<String>, leaves: List<CacheLeafEntry>) {
        writeRepository.upsertLocalTreeCache(
            albumId = albumId,
            albumPaths = albumPaths,
            leaves = leaves.map { leaf ->
                ScanCacheLeaf(leaf.relativePath, leaf.absolutePath, TreeFileType.valueOf(leaf.fileType.name))
            },
            stampProvider = { paths -> computePathsStamp(paths) },
        )
    }

    // R3-C1c-b：扫描根管理与三个批量扫描入口迁入前，VM 批量入口仍引用本方法。

    fun queryChildren(treeUri: Uri, parentDocumentId: String, parentRelativePath: String = ""): List<SafDocNode> =
        SafTreeSupport.queryChildren(context, treeUri, parentDocumentId, parentRelativePath)

    private fun documentExists(treeUri: Uri, documentId: String): Boolean =
        SafTreeSupport.documentExists(context, treeUri, documentId)

    private fun walkTree(treeUri: Uri, rootDocumentId: String): List<SafDocNode> =
        SafTreeSupport.walkTree(context, treeUri, rootDocumentId)

    private fun readSubtitleFromUri(treeUri: Uri, documentId: String, displayName: String): List<SubtitleEntry> =
        SafTreeSupport.readSubtitleFromUri(context, treeUri, documentId, displayName)

    private fun extractWorkNo(input: String): String {
        return DlsiteWorkNo.extractWorkNo(input)
    }

    fun legacyOnlineSavedAlbumDir(entity: AlbumEntity): File {
        val baseDir = File(context.getExternalFilesDir(null), "albums")
        val folderName = legacyOnlineSavedAlbumFolderName(entity)
        return File(baseDir, folderName)
    }

    suspend fun backfillLegacyOnlineSavedAlbumRoots() {
        val albums = runCatching { readRepository.getAllAlbumsOnce() }.getOrDefault(emptyList())
        if (albums.isEmpty()) return

        writeRepository.backfillLegacyOnlineSavedAlbumRoots(albums) { entity ->
            val albumDir = legacyOnlineSavedAlbumDir(entity)
            ensureLibraryAlbumDir(albumDir)
            albumDir.absolutePath
        }
    }

    suspend fun scanFromDownloadedDir(
        importAll: Boolean = false,
        onAlbumScanned: ((String) -> Unit)? = null,
    ) {
        val destination = downloadDestinationStore.current()
        if (destination is DownloadDestination.DocumentTree) {
            scanFromDocumentTree(
                uriString = destination.root,
                asDownloadRoot = !importAll,
                requireCompletionMarker = !importAll,
                onAlbumScanned = onAlbumScanned,
                // T3'：下载目录管线的 SAF 委托分支——被扫描对象仍是 DLsite 作品落盘，来源不随 asDownloadRoot 翻转。
                pipelineSource = AlbumEntity.SOURCE_DLSITE_DOWNLOAD,
            )
            return
        }
        val baseDir = File(destination.root)
        if (!baseDir.exists() || !baseDir.isDirectory) return
        val foundDownloadPaths = LinkedHashSet<String>()
        baseDir.listFiles()
            ?.filter { albumDir ->
                albumDir.isDirectory && isScannableLocalDirectoryName(albumDir.name) &&
                    (importAll || File(albumDir, ".download_complete").exists())
            }
            ?.forEach { albumDir ->
            currentCoroutineContext().ensureActive()
            foundDownloadPaths.add(albumDir.absolutePath)
            val coverFile = pickCoverFileFromAlbumDir(albumDir)
            val title = albumDir.name
            val rj = extractWorkNo(title)

            onAlbumScanned?.invoke(title)
            val existing = resolveAndMergeAlbumForRj(
                rj = rj,
                fallbackPath = albumDir.absolutePath,
                fallbackTitle = title,
                localPath = albumDir.absolutePath.takeIf { importAll },
                downloadPath = albumDir.absolutePath.takeUnless { importAll },
            )
            val aggregateTracks = albumDir.walkTopDown()
                .onEnter { directory -> directory == albumDir || isScannableLocalDirectoryName(directory.name) }
                .filter { it.isFile && setOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "opus").contains(it.extension.lowercase()) }
                .map { file -> TrackEntity(albumId = 0L, title = file.nameWithoutExtension, path = file.absolutePath, duration = 0.0, group = "") }
                .toList()
            val aggregate = writeRepository.computeAlbumAudioAggregate(aggregateTracks) { path ->
                queryTrackFileSize(context, path)
            }
            val entity = AlbumEntity(
                id = existing?.id ?: 0L,
                title = existing?.title?.takeIf { it.isNotBlank() && it != title } ?: title,
                path = existing?.path?.takeIf { it.isNotBlank() } ?: albumDir.absolutePath,
                localPath = if (importAll) albumDir.absolutePath else existing?.localPath,
                downloadPath = if (importAll) existing?.downloadPath else albumDir.absolutePath,
                circle = existing?.circle ?: "",
                cv = existing?.cv ?: "",
                tags = existing?.tags ?: "",
                coverUrl = existing?.coverUrl ?: "",
                coverPath = coverFile?.absolutePath ?: (existing?.coverPath ?: ""),
                coverThumbPath = existing?.coverThumbPath ?: "",
                workId = existing?.workId?.takeIf { it.isNotBlank() } ?: rj,
                rjCode = existing?.rjCode?.takeIf { it.isNotBlank() } ?: rj,
                description = existing?.description ?: "",
                audioTrackCount = aggregate.trackCount,
                audioTotalDuration = aggregate.totalDuration,
                audioTotalSizeBytes = aggregate.totalSizeBytes,
                // T3'：下载目录管线（DLsite 作品落盘）来源回填；已有非空 source 保留（单点规则见行为档案）。
                source = writeRepository.scanMetadataSupport.resolveAlbumSource(
                    existing?.source, AlbumEntity.SOURCE_DLSITE_DOWNLOAD
                ),
            )
            val albumId = writeRepository.insertAlbum(entity)
            writeRepository.upsertAlbumFtsIndex(albumId, entity.copy(id = albumId))
            writeRepository.upsertAlbumTagsFromCsv(albumId, entity.tags, TagSource.SCAN)
            if (entity.coverPath.isBlank()) {
                val audio = albumDir.walkTopDown()
                    .onEnter { directory -> directory == albumDir || isScannableLocalDirectoryName(directory.name) }
                    .firstOrNull { it.isFile && setOf("mp3","flac","wav","m4a","ogg","aac","opus").contains(it.extension.lowercase()) }
                if (audio != null) {
                    // T3'：封面缺失时内嵌图优先（元数据读取独立于音轨增量链，与原 extractArtwork 同为按需探查），
                    // 读不到再回退既有 EmbeddedMediaExtractor 提取——触发条件（coverPath 为空）不变。
                    val embeddedCover = writeRepository.scanMetadataSupport
                        .readForNewTrack(audio.absolutePath)?.embeddedCover
                    val bmp = writeRepository.scanMetadataSupport.decodeEmbeddedCover(embeddedCover)
                        ?: EmbeddedMediaExtractor.extractArtwork(context, audio.absolutePath)
                    if (bmp != null) {
                        val saved = EmbeddedMediaExtractor.saveArtworkToCache(context, albumId, bmp)
                        if (!saved.isNullOrBlank()) {
                            val updated = entity.copy(coverPath = saved)
                            writeRepository.updateAlbum(updated)
                        }
                    }
                }
            }
            enqueueAlbumCoverThumbWork(albumId)
            scanTracksAndSubtitlesFromFileAlbum(albumId, albumDir)
        }
        if (!importAll) {
            pruneMissingDownloadedAlbums(baseDir = baseDir, foundDownloadPaths = foundDownloadPaths)
        }
    }

    private suspend fun pruneMissingDownloadedAlbums(
        baseDir: File,
        foundDownloadPaths: Set<String>
    ) {
        val basePrefix = baseDir.absolutePath.trimEnd('\\', '/') + File.separator
        val albums = readRepository.getAllAlbumsOnce()
        val missing = albums.filter { entity ->
            val dl = entity.downloadPath?.trim().orEmpty()
            dl.isNotBlank() &&
                dl.startsWith(basePrefix) &&
                !foundDownloadPaths.contains(dl) &&
                (!File(dl).exists() || !isScannableLocalDirectoryName(File(dl).name))
        }
        if (missing.isEmpty()) return

        writeRepository.pruneMissingDownloadedAlbums(missing)
    }

    private fun enqueueTrackDurationWork(albumId: Long) {
        if (albumId <= 0L) return
        val request = OneTimeWorkRequestBuilder<TrackDurationWorker>()
            .setInputData(workDataOf(TrackDurationWorker.KEY_ALBUM_ID to albumId))
            .addTag("track_duration")
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("track_duration_album_$albumId", ExistingWorkPolicy.REPLACE, request)
    }

    private fun enqueueAlbumCoverThumbWork(albumId: Long) {
        if (albumId <= 0L) return
        val request = OneTimeWorkRequestBuilder<AlbumCoverThumbWorker>()
            .setInputData(workDataOf(AlbumCoverThumbWorker.KEY_ALBUM_ID to albumId))
            .addTag("album_cover_thumb")
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("album_cover_thumb_$albumId", ExistingWorkPolicy.REPLACE, request)
    }

    suspend fun scanTracksAndSubtitlesFromFileAlbum(albumId: Long, albumDir: File) {
        val prefix = albumDir.absolutePath.trimEnd('\\', '/') + File.separator
        val audioExtensions = setOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "opus")

        val audioFiles = mutableListOf<File>()
        val subtitleFiles = mutableListOf<File>()
        val cacheLeaves = mutableListOf<CacheLeafEntry>()
        albumDir.walkTopDown()
            .onEnter { directory -> directory == albumDir || isScannableLocalDirectoryName(directory.name) }
            .forEach { f ->
            currentCoroutineContext().ensureActive()
            if (!f.isFile) return@forEach
            val ext = f.extension.lowercase()
            if (audioExtensions.contains(ext)) {
                audioFiles.add(f)
            }

            if (SubtitleMatchSupport.SubtitleExtensions.contains(ext)) {
                subtitleFiles.add(f)
            }

            val type = cacheFileTypeForName(f.name)
            if (type != CacheTreeFileType.Other) {
                val rawRel = runCatching { f.relativeTo(albumDir).path }.getOrElse { f.name }
                val rel = rawRel.replace('\\', '/').trim().trimStart('/')
                if (rel.isNotBlank()) {
                    cacheLeaves.add(CacheLeafEntry(relativePath = rel, absolutePath = f.absolutePath, fileType = type))
                }
            }
        }
        audioFiles.sortBy { it.absolutePath }

        val allExistingTracks = readRepository.getTracksForAlbumOnce(albumId)

        val existingTracks = allExistingTracks
            .filter { it.path.startsWith(prefix) }
            .associateBy { it.path }
        val subtitleCandidates = subtitleFiles.mapNotNull { file ->
            val relative = runCatching { file.relativeTo(albumDir).path.replace('\\', '/') }.getOrNull().orEmpty()
            val candidate = SubtitleMatchSupport.inferCandidate(relative, file.absolutePath) ?: return@mapNotNull null
            candidate to file
        }
        val subtitleCandidateList = subtitleCandidates.map { it.first }

        fun parseBestSubtitle(relativePathNoExt: String): List<SubtitleEntry> {
            val matchedSubtitle = SubtitleMatchSupport.matchBest(relativePathNoExt, subtitleCandidateList) ?: return emptyList()
            val subtitleFile = subtitleCandidates.firstOrNull { it.first.sourceRef == matchedSubtitle.sourceRef }?.second ?: return emptyList()
            return SubtitleParser.parse(subtitleFile.absolutePath)
        }

        val seenPaths = linkedSetOf<String>()
        val tracksToInsert = ArrayList<TrackEntity>(audioFiles.size)
        val tracksToUpdate = ArrayList<TrackEntity>(audioFiles.size)
        val subtitleEntriesByAudioPath = linkedMapOf<String, List<SubtitleEntry>>()
        val subtitleEntriesByExistingTrackId = linkedMapOf<Long, List<SubtitleEntry>>()

        audioFiles.forEach { audio ->
            currentCoroutineContext().ensureActive()
            taskCoordinator.maybeUpdateBulkCurrentFile(audio.name)
            val trackTitle = audio.nameWithoutExtension
            val relPath = audio.relativeTo(albumDir).path.replace('\\', '/')
            val group =
                if (relPath.contains("/")) relPath.substringBeforeLast('/').substringAfterLast('/', relPath.substringBeforeLast('/')) else ""
            val audioPath = audio.absolutePath
            val relativePathNoExt = relPath.substringBeforeLast('.')
            seenPaths.add(audioPath)

            val parsed = parseBestSubtitle(relativePathNoExt)
            if (parsed.isNotEmpty()) {
                subtitleEntriesByAudioPath[audioPath] = parsed
            }

            val existingTrack = existingTracks[audioPath]
            // T3'：仅新插轨读元数据（增量闸门在 LibraryScanMetadataSupport）；已存在轨只覆写 title/group。
            val scanMetadata = writeRepository.scanMetadataSupport
            val scannedTrack = if (existingTrack == null) {
                scanMetadata.newTrackEntity(
                    albumId = albumId,
                    title = trackTitle,
                    path = audioPath,
                    group = group,
                    metadata = scanMetadata.readForNewTrack(audioPath),
                )
            } else {
                scanMetadata.updatedTrackEntity(existingTrack, trackTitle, group)
            }
            if (existingTrack == null) tracksToInsert.add(scannedTrack) else tracksToUpdate.add(scannedTrack)
        }

        val removedIds = existingTracks.values
            .asSequence()
            .filter { !seenPaths.contains(it.path) }
            .map { it.id }
            .toList()

        writeRepository.syncScannedLocalAlbumTracks(
            tracksToUpdate = tracksToUpdate,
            tracksToInsert = tracksToInsert,
            subtitleEntriesByAudioPath = subtitleEntriesByAudioPath,
            subtitleEntriesByExistingTrackId = subtitleEntriesByExistingTrackId,
            removedIds = removedIds,
        )

        if (subtitleEntriesByAudioPath.isNotEmpty() || subtitleEntriesByExistingTrackId.isNotEmpty()) {
            playerConnection.requestLyricsReload()
        }

        refreshAlbumAudioAggregate(albumId)

        upsertLocalTreeCache(
            albumId = albumId,
            albumPaths = listOf(albumDir.absolutePath),
            leaves = cacheLeaves.distinctBy { it.relativePath }
        )
        enqueueTrackDurationWork(albumId)
    }

    suspend fun scanFromDocumentTree(
        uriString: String,
        asDownloadRoot: Boolean = false,
        requireCompletionMarker: Boolean = asDownloadRoot,
        // T3'：来源回填按"进入哪条管线"决定——扫描根直扫 = local_scan（默认）；
        // scanFromDownloadedDir 的 SAF 委托分支（下载根/整体导入）传 dlsite_download。
        // 注意排位在 onAlbumScanned 之前：既有的尾 lambda 调用点（scanAllRoots/scanSingleRoot）才能继续匹配回调。
        pipelineSource: String? = AlbumEntity.SOURCE_LOCAL_SCAN,
        onAlbumScanned: ((String) -> Unit)? = null,
    ) {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return
        val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return
        val children = queryChildren(uri, treeDocId).filter { child ->
            child.mimeType == DocumentsContract.Document.MIME_TYPE_DIR &&
                isScannableLocalDirectoryName(child.displayName) &&
                (!requireCompletionMarker || queryChildren(uri, child.documentId).any { it.displayName == ".download_complete" })
        }
        val foundAlbumPaths = LinkedHashSet<String>()

        children.forEach { albumDir ->
            currentCoroutineContext().ensureActive()
            foundAlbumPaths.add(
                scanSingleDocumentAlbum(uri, albumDir, asDownloadRoot, onAlbumScanned, pipelineSource)
            )
        }
        if (asDownloadRoot) {
            pruneMissingDocumentDownloadAlbums(rootUriString = uriString, foundAlbumPaths = foundAlbumPaths)
        } else {
            pruneMissingDocumentAlbums(rootUriString = uriString, foundAlbumPaths = foundAlbumPaths)
        }
    }

    /** C6-1（原 scanFromDocumentTree forEach 体逐字随迁）：扫描单个 SAF 目录专辑，返回其 albumPath 供 prune 对账。 */
    private suspend fun scanSingleDocumentAlbum(
        uri: Uri,
        albumDir: SafDocNode,
        asDownloadRoot: Boolean,
        onAlbumScanned: ((String) -> Unit)?,
        pipelineSource: String?,
    ): String {
        val albumUri = DocumentsContract.buildDocumentUriUsingTree(uri, albumDir.documentId)
        val albumPath = albumUri.toString()
        val title = albumDir.displayName.ifBlank { "album" }
        val rj = extractWorkNo(title)
        val albumChildren = queryChildren(uri, albumDir.documentId)
        val coverPath = pickCoverNode(albumChildren, uri, albumDir.documentId)

        onAlbumScanned?.invoke(title)
        val existing = resolveAndMergeAlbumForRj(
            rj = rj,
            fallbackPath = albumPath,
            fallbackTitle = title,
            localPath = if (asDownloadRoot) null else albumPath,
            downloadPath = if (asDownloadRoot) albumPath else null,
        )

        val all = walkTree(uri, albumDir.documentId).filter { it.mimeType != DocumentsContract.Document.MIME_TYPE_DIR }
        val audioFiles = all.filter { documentAudioExtensions().contains(it.displayName.substringAfterLast('.', "").lowercase()) }
        val subtitleNodes = all.filter { documentSubtitleExtensions().contains(it.displayName.substringAfterLast('.', "").lowercase()) }
        val subtitleCandidates = subtitleNodes.mapNotNull { node ->
            val candidate = SubtitleMatchSupport.inferCandidate(node.relativePath, node.documentId) ?: return@mapNotNull null
            candidate to node
        }
        val subtitleCandidateList = subtitleCandidates.map { it.first }

        val (trackSpecs, audioRelativeBaseByPath) = buildDocumentTrackSpecs(uri, audioFiles)

        val subtitlesByAudioPath: Map<String, List<SubtitleEntry>> = trackSpecs.associate { spec ->
            val key = audioRelativeBaseByPath[spec.path].orEmpty()
            val matched = if (key.isBlank()) null else SubtitleMatchSupport.matchBest(key, subtitleCandidateList)
            val node = matched?.let { hit -> subtitleCandidates.firstOrNull { it.first.sourceRef == hit.sourceRef }?.second }
            val entries = node?.let { readSubtitleFromUri(uri, it.documentId, it.displayName) }.orEmpty()
            spec.path to entries
        }

        val entity = buildDocumentAlbumEntity(existing, title, albumPath, asDownloadRoot, rj, coverPath, pipelineSource)
        val leaves = all.asSequence()
            .mapNotNull { node ->
                val t = treeFileTypeForName(node.displayName)
                if (t == TreeFileType.Other) return@mapNotNull null
                val abs = DocumentsContract.buildDocumentUriUsingTree(uri, node.documentId).toString()
                ScanCacheLeaf(relativePath = node.relativePath, absolutePath = abs, fileType = t)
            }
            .distinctBy { it.relativePath }
            .toList()

        val scanResult = writeRepository.upsertScannedDocumentAlbum(
            entity = entity,
            scanRootPath = albumPath,
            trackSpecs = trackSpecs,
            subtitlesByAudioPath = subtitlesByAudioPath,
            cacheLeaves = leaves,
            fileSizeQuery = { path -> queryTrackFileSize(context, path) },
            stampProvider = { paths -> computePathsStamp(paths) },
        )
        val insertedAlbumId = scanResult.albumId
        if (scanResult.wroteAnySubtitles) {
            playerConnection.requestLyricsReload()
        }
        backfillDocumentAlbumCover(insertedAlbumId, trackSpecs, scanResult.firstInsertedCoverBytes)
        enqueueAlbumCoverThumbWork(insertedAlbumId)
        enqueueTrackDurationWork(insertedAlbumId)
        return albumPath
    }

    private fun documentAudioExtensions(): Set<String> = setOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "opus")

    private fun documentSubtitleExtensions(): Set<String> = setOf("lrc", "srt", "vtt")

    /** C6-1（原 scanFromDocumentTree 内联段逐字随迁）：音轨扫描规格 + 音轨路径→无扩展名相对基名映射。 */
    private suspend fun buildDocumentTrackSpecs(
        uri: Uri,
        audioFiles: List<SafDocNode>,
    ): Pair<List<ScanTrackSpec>, LinkedHashMap<String, String>> {
        val trackSpecs = ArrayList<ScanTrackSpec>(audioFiles.size)
        val audioRelativeBaseByPath = LinkedHashMap<String, String>(audioFiles.size)
        audioFiles.sortedBy { it.documentId }.forEach { audio ->
            currentCoroutineContext().ensureActive()
            taskCoordinator.maybeUpdateBulkCurrentFile(audio.displayName)
            val audioUri = DocumentsContract.buildDocumentUriUsingTree(uri, audio.documentId)
            val trackTitle = audio.displayName.substringBeforeLast('.').ifBlank { "track" }
            val group = if (audio.relativePath.contains("/")) audio.relativePath.substringBeforeLast('/').substringAfterLast('/') else ""
            val relativeBase = audio.relativePath.substringBeforeLast('.')
            trackSpecs.add(
                ScanTrackSpec(
                    title = trackTitle,
                    path = audioUri.toString(),
                    group = group
                )
            )
            audioRelativeBaseByPath[audioUri.toString()] = relativeBase
        }
        return trackSpecs to audioRelativeBaseByPath
    }

    /** C6-1（原 scanFromDocumentTree 内联段逐字随迁）：合并既有元数据构造待落库实体。 */
    private fun buildDocumentAlbumEntity(
        existing: AlbumEntity?,
        title: String,
        albumPath: String,
        asDownloadRoot: Boolean,
        rj: String,
        coverPath: String,
        pipelineSource: String?,
    ): AlbumEntity {
        return AlbumEntity(
            id = existing?.id ?: 0L,
            title = existing?.title?.takeIf { it.isNotBlank() && it != title } ?: title,
            path = existing?.path?.takeIf { it.isNotBlank() } ?: albumPath,
            localPath = if (asDownloadRoot) existing?.localPath else albumPath,
            downloadPath = if (asDownloadRoot) albumPath else existing?.downloadPath,
            circle = existing?.circle ?: "",
            cv = existing?.cv ?: "",
            tags = existing?.tags ?: "",
            coverUrl = existing?.coverUrl ?: "",
            coverPath = coverPath.ifBlank { existing?.coverPath.orEmpty() },
            coverThumbPath = existing?.coverThumbPath.orEmpty(),
            workId = existing?.workId?.takeIf { it.isNotBlank() } ?: rj,
            rjCode = existing?.rjCode?.takeIf { it.isNotBlank() } ?: rj,
            description = existing?.description ?: "",
            // T3'：SAF 扫描管线来源回填（与 File 分支共用单点解析规则）。
            source = writeRepository.scanMetadataSupport.resolveAlbumSource(existing?.source, pipelineSource)
        )
    }

    /** C6-1（原 scanFromDocumentTree 内联段逐字随迁）：封面缺失时从首个音轨提取内嵌封面回填。
     *  T3'：内嵌图源优先取本次扫描首插轨的 AudioMetadata.embeddedCover（增量读取的副产品，零额外 MMR 打开），
     *  为 null 回退既有 EmbeddedMediaExtractor——触发条件（coverPath 为空）不变。 */
    private suspend fun backfillDocumentAlbumCover(
        albumId: Long,
        trackSpecs: List<ScanTrackSpec>,
        firstInsertedCoverBytes: ByteArray?,
    ) {
        runCatching {
            val persisted = readRepository.getAlbumById(albumId)
            val needCover = persisted?.coverPath?.trim().orEmpty().isBlank()
            if (needCover) {
                val firstAudio = trackSpecs.firstOrNull()?.path
                if (!firstAudio.isNullOrBlank()) {
                    val bmp = writeRepository.scanMetadataSupport.decodeEmbeddedCover(firstInsertedCoverBytes)
                        ?: EmbeddedMediaExtractor.extractArtwork(context, firstAudio)
                    if (bmp != null) {
                        val saved = EmbeddedMediaExtractor.saveArtworkToCache(context, albumId, bmp)
                        if (!saved.isNullOrBlank()) {
                            val updated = persisted!!.copy(coverPath = saved)
                            writeRepository.updateAlbum(updated)
                        }
                    }
                }
            }
        }
    }

    private suspend fun pruneMissingDocumentDownloadAlbums(
        rootUriString: String,
        foundAlbumPaths: Set<String>,
    ) {
        val albums = readRepository.getAllAlbumsOnce()
        albums.filter { entity ->
            val download = entity.downloadPath?.trim().orEmpty()
            download.isNotBlank() && download.startsWith(rootUriString) && !foundAlbumPaths.contains(download)
        }.forEach { entity ->
            writeRepository.pruneDocumentDownloadAlbum(entity, rootUriString)
        }
    }

    private suspend fun pruneMissingDocumentAlbums(
        rootUriString: String,
        foundAlbumPaths: Set<String>
    ) {
        val albums = readRepository.getAllAlbumsOnce()
        val missing = albums.filter { entity ->
            val local = entity.localPath?.trim().orEmpty()
            local.isNotBlank() &&
                local.startsWith(rootUriString) &&
                !foundAlbumPaths.contains(local)
        }
        if (missing.isEmpty()) return

        writeRepository.pruneMissingDocumentAlbums(missing, rootUriString)
    }

    private fun existsLocalUri(uriString: String): Boolean {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return false
        val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return false
        val docId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return false
        val treeUri = DocumentsContract.buildTreeDocumentUri(uri.authority, treeDocId)
        return documentExists(treeUri, docId)
    }

    suspend fun pruneOrphanedAlbumsByFilesystem() {
        val albums = readRepository.getAllAlbumsOnce()
        if (albums.isEmpty()) return

        fun fileExists(path: String): Boolean = runCatching { File(path).exists() }.getOrDefault(false)
        fun uriOrFileExists(pathOrUri: String): Boolean {
            val v = pathOrUri.trim()
            if (v.isBlank()) return false
            if (isVirtualAlbumPath(v) || isOnlineTrackPath(v)) return true
            return if (v.startsWith("content://")) existsLocalUri(v) else fileExists(v)
        }

        writeRepository.pruneOrphanedAlbums(
            albums = albums,
            uriOrFileExists = { uriOrFileExists(it) },
            fileExists = { fileExists(it) },
            resolveLegacyDir = { entity ->
                val albumDir = legacyOnlineSavedAlbumDir(entity)
                ensureLibraryAlbumDir(albumDir)
                albumDir.absolutePath
            },
        )
    }

    suspend fun scanSingleAlbumFromDocumentUri(albumId: Long, albumUriString: String) {
        val albumUri = runCatching { Uri.parse(albumUriString) }.getOrNull() ?: return
        val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(albumUri) }.getOrNull() ?: return
        val treeUri = DocumentsContract.buildTreeDocumentUri(albumUri.authority, treeDocId)
        val albumDocId = runCatching { DocumentsContract.getDocumentId(albumUri) }.getOrNull() ?: return

        val audioExtensions = setOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "opus")
        val subtitleExtensions = setOf("lrc", "srt", "vtt")

        val albumChildren = queryChildren(treeUri, albumDocId)
        val coverPath = pickCoverNode(albumChildren, treeUri, albumDocId)

        val all = walkTree(treeUri, albumDocId).filter { it.mimeType != DocumentsContract.Document.MIME_TYPE_DIR }
        val audioFiles = all.filter { audioExtensions.contains(it.displayName.substringAfterLast('.', "").lowercase()) }.sortedBy { it.documentId }
        val subtitleNodes = all.filter { subtitleExtensions.contains(it.displayName.substringAfterLast('.', "").lowercase()) }
        val subtitleCandidates = subtitleNodes.mapNotNull { node ->
            val candidate = SubtitleMatchSupport.inferCandidate(node.relativePath, node.documentId) ?: return@mapNotNull null
            candidate to node
        }
        val subtitleCandidateList = subtitleCandidates.map { it.first }

        val trackSpecs = ArrayList<ScanTrackSpec>(audioFiles.size)
        val audioRelativeBaseByPath = LinkedHashMap<String, String>(audioFiles.size)
        audioFiles.forEach { audio ->
            taskCoordinator.maybeUpdateBulkCurrentFile(audio.displayName)
            val audioUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, audio.documentId)
            val trackTitle = audio.displayName.substringBeforeLast('.').ifBlank { "track" }
            val group = if (audio.relativePath.contains("/")) audio.relativePath.substringBeforeLast('/').substringAfterLast('/') else ""
            val relativeBase = audio.relativePath.substringBeforeLast('.')
            trackSpecs.add(
                ScanTrackSpec(
                    title = trackTitle,
                    path = audioUri.toString(),
                    group = group
                )
            )
            audioRelativeBaseByPath[audioUri.toString()] = relativeBase
        }

        val subtitlesByAudioPath: Map<String, List<SubtitleEntry>> = trackSpecs.associate { spec ->
            val key = audioRelativeBaseByPath[spec.path].orEmpty()
            val matched = if (key.isBlank()) null else SubtitleMatchSupport.matchBest(key, subtitleCandidateList)
            val node = matched?.let { hit -> subtitleCandidates.firstOrNull { it.first.sourceRef == hit.sourceRef }?.second }
            val entries = node?.let { readSubtitleFromUri(treeUri, it.documentId, it.displayName) }.orEmpty()
            spec.path to entries
        }

        val cacheLeaves = all.mapNotNull { node ->
            val type = cacheFileTypeForName(node.displayName)
            if (type == CacheTreeFileType.Other) return@mapNotNull null
            val rawRel = node.relativePath.replace('\\', '/').trim().trimStart('/')
            if (rawRel.isBlank()) return@mapNotNull null
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, node.documentId).toString()
            CacheLeafEntry(relativePath = rawRel, absolutePath = docUri, fileType = type)
        }
        val treePrefix = treeUri.toString().trimEnd('/') + "/document/"

        val rescanResult = writeRepository.rescanDocumentAlbum(
            albumId = albumId,
            coverPath = coverPath,
            treePrefix = treePrefix,
            trackSpecs = trackSpecs,
            subtitlesByAudioPath = subtitlesByAudioPath,
        )
        if (rescanResult.wroteAnySubtitles) {
            playerConnection.requestLyricsReload()
        }
        refreshAlbumAudioAggregate(albumId)
        if (rescanResult.persistedPaths.isNotEmpty() && cacheLeaves.isNotEmpty()) {
            upsertLocalTreeCache(
                albumId = albumId,
                albumPaths = rescanResult.persistedPaths,
                leaves = cacheLeaves.distinctBy { it.relativePath }
            )
        }
        enqueueAlbumCoverThumbWork(albumId)
        enqueueTrackDurationWork(albumId)
    }

    private suspend fun resolveAndMergeAlbumForRj(
        rj: String,
        fallbackPath: String,
        fallbackTitle: String,
        localPath: String?,
        downloadPath: String?
    ): AlbumEntity? = localAlbumMergeService.resolveAndMerge(
        rj = rj,
        fallbackPath = fallbackPath,
        fallbackTitle = fallbackTitle,
        localPath = localPath,
        downloadPath = downloadPath,
    )

    private suspend fun refreshAlbumAudioAggregate(albumId: Long) {
        writeRepository.refreshAlbumAudioAggregate(albumId) { path ->
            queryTrackFileSize(context, path)
        }
    }

    fun addScanRoot(uriString: String): Boolean {
        val existingRoots = scanRootsStore.getRoots()

        // 检查重复
        if (existingRoots.contains(uriString)) {
            messageManager.showInfo("扫描目录已存在")
            return false
        }

        // 检查嵌套：新目录是否是现有目录的子目录
        val newUri = runCatching { Uri.parse(uriString) }.getOrNull()
        if (newUri != null) {
            for (existingRoot in existingRoots) {
                val existingUri = runCatching { Uri.parse(existingRoot) }.getOrNull() ?: continue

                // 检查新目录是否是现有目录的子目录
                if (isSubdirectory(newUri, existingUri)) {
                    messageManager.showInfo("该目录已被包含在现有扫描目录中")
                    return false
                }

                // 检查现有目录是否是新目录的子目录
                if (isSubdirectory(existingUri, newUri)) {
                    messageManager.showInfo("该目录包含了现有的扫描目录，请先移除子目录")
                    return false
                }
            }
        }

        val added = scanRootsStore.addRoot(uriString)
        _scanRoots.value = runCatching { scanRootsStore.getRoots() }.getOrDefault(emptySet())
        if (added) {
            messageManager.showSuccess("已添加扫描目录")
        }
        return added
    }

    private fun isSubdirectory(child: Uri, parent: Uri): Boolean {
        // 如果是相同的 URI scheme 和 authority
        if (child.scheme != parent.scheme || child.authority != parent.authority) {
            return false
        }

        // 获取文档树 ID
        val childTreeId = runCatching {
            DocumentsContract.getTreeDocumentId(child)
        }.getOrNull() ?: return false

        val parentTreeId = runCatching {
            DocumentsContract.getTreeDocumentId(parent)
        }.getOrNull() ?: return false

        // 检查子目录关系
        return childTreeId.startsWith(parentTreeId) && childTreeId != parentTreeId
    }

    fun removeScanRoot(uriString: String) {
        scanRootsStore.removeRoot(uriString)
        _scanRoots.value = runCatching { scanRootsStore.getRoots() }.getOrDefault(emptySet())
        messageManager.showInfo("已移除扫描目录")
    }

    fun removeScanRootAndDeleteAlbums(uriString: String) {
        scope.launch(Dispatchers.IO) {
            scanRootsStore.removeRoot(uriString)
            _scanRoots.value = runCatching { scanRootsStore.getRoots() }.getOrDefault(emptySet())

            val allAlbums = readRepository.getAllAlbumsOnce()
            val affected = allAlbums.filter { entity ->
                entity.path.startsWith(uriString) ||
                    (entity.localPath?.startsWith(uriString) == true) ||
                    entity.coverPath.startsWith(uriString)
            }

            affected.forEach { entity ->
                val downloadPath = entity.downloadPath
                val keepByDownload = !downloadPath.isNullOrBlank() &&
                    !downloadPath.startsWith("content://") &&
                    runCatching { File(downloadPath).exists() }.getOrDefault(false)

                if (!keepByDownload) {
                    val tracks = readRepository.getTracksForAlbumOnce(entity.id)
                    val hasOnline = isVirtualAlbumPath(entity.path) || tracks.any { isOnlineTrackPath(it.path) }
                    if (!hasOnline) {
                        writeRepository.deleteAlbumTracksAndSubtitles(entity.id)
                        writeRepository.deleteAlbumEntity(entity)
                    } else {
                        tracks.filter { it.path.startsWith(uriString) }.forEach { track ->
                            writeRepository.deleteTrackWithSubtitlesById(track.id)
                        }
                        val updatedPath = if (entity.path.startsWith(uriString)) (buildOnlineAlbumPath(entity) ?: entity.path) else entity.path
                        val updated = entity.copy(
                            path = updatedPath,
                            localPath = entity.localPath?.takeIf { !it.startsWith(uriString) },
                            coverPath = if (entity.coverPath.startsWith(uriString)) "" else entity.coverPath
                        )
                        writeRepository.updateAlbum(updated)
                        writeRepository.upsertAlbumFtsIndex(updated.id, updated)
                    }
                } else {
                    val tracks = readRepository.getTracksForAlbumOnce(entity.id)
                    tracks.filter { it.path.startsWith(uriString) }.forEach { track ->
                        writeRepository.deleteTrackWithSubtitlesById(track.id)
                    }

                    val updated = entity.copy(
                        path = if (entity.path.startsWith(uriString)) downloadPath!! else entity.path,
                        localPath = entity.localPath?.takeIf { !it.startsWith(uriString) },
                        coverPath = if (entity.coverPath.startsWith(uriString)) "" else entity.coverPath
                    )
                    writeRepository.updateAlbum(updated)
                    writeRepository.upsertAlbumFtsIndex(updated.id, updated)
                }
            }
        }
    }

    fun scanAllRoots() {
        scope.launch {
            val token = syncCoordinator.tryBegin() ?: run {
                taskCoordinator.showSyncBusy("刷新本地")
                return@launch
            }
            try {
                taskCoordinator.bulkStartMutex.withLock {
                    taskCoordinator.bulkJob = currentCoroutineContext()[Job]
                    try {
                        withContext(Dispatchers.IO) {
                            scanMutex.withLock {
                                currentCoroutineContext().ensureActive()
                                val roots = scanRootsStore.getRoots().toList()
                                val downloadedAlbumCount = runCatching {
                                    when (val destination = downloadDestinationStore.current()) {
                                        is DownloadDestination.Default -> File(destination.root).listFiles()
                                            ?.count {
                                                it.isDirectory && isScannableLocalDirectoryName(it.name) &&
                                                    File(it, ".download_complete").exists()
                                            }
                                            ?: 0
                                        is DownloadDestination.DocumentTree -> {
                                            val uri = Uri.parse(destination.root)
                                            val treeId = DocumentsContract.getTreeDocumentId(uri)
                                            queryChildren(uri, treeId).count { child ->
                                                child.mimeType == DocumentsContract.Document.MIME_TYPE_DIR &&
                                                    isScannableLocalDirectoryName(child.displayName) &&
                                                    queryChildren(uri, child.documentId).any { it.displayName == ".download_complete" }
                                            }
                                        }
                                    }
                                }.getOrDefault(0)

                                var totalAlbums = downloadedAlbumCount
                                roots.forEach { root ->
                                    currentCoroutineContext().ensureActive()
                                    val uri = runCatching { Uri.parse(root) }.getOrNull()
                                    val treeDocId = uri?.let { runCatching { DocumentsContract.getTreeDocumentId(it) }.getOrNull() }
                                    if (uri != null && !treeDocId.isNullOrBlank()) {
                                        totalAlbums += queryChildren(uri, treeDocId).count {
                                            it.mimeType == DocumentsContract.Document.MIME_TYPE_DIR &&
                                                isScannableLocalDirectoryName(it.displayName)
                                        }
                                    }
                                }
                                taskCoordinator.startBulkProgress(phase = BulkPhase.ScanningLocal, total = totalAlbums)

                                var current = 0
                                roots.forEach { root ->
                                    currentCoroutineContext().ensureActive()
                                    scanFromDocumentTree(root) { title ->
                                        current += 1
                                        taskCoordinator.updateBulkAlbumProgress(current = current, currentAlbumTitle = title)
                                    }
                                }
                                scanFromDownloadedDir { title ->
                                    current += 1
                                    taskCoordinator.updateBulkAlbumProgress(current = current, currentAlbumTitle = title)
                                }
                                pruneOrphanedAlbumsByFilesystem()
                            }
                        }
                        messageManager.showSuccess("扫描完成")
                    } catch (e: CancellationException) {
                        messageManager.showInfo("已取消扫描")
                    } catch (e: Exception) {
                        Log.e("LibraryViewModel", "scanAllRoots failed", e)
                        messageManager.showError("扫描失败：${e.message}")
                    } finally {
                        taskCoordinator.finishBulkProgress()
                        if (taskCoordinator.bulkJob == currentCoroutineContext()[Job]) {
                            taskCoordinator.bulkJob = null
                        }
                    }
                }
            } finally {
                syncCoordinator.end(token)
            }
        }
    }

    fun scanCurrentDownloadDestinationAsImport() {
        scope.launch {
            val token = syncCoordinator.tryBegin() ?: run {
                taskCoordinator.showSyncBusy("扫描目标下载目录")
                return@launch
            }
            try {
                taskCoordinator.bulkStartMutex.withLock {
                    taskCoordinator.bulkJob = currentCoroutineContext()[Job]
                    try {
                        withContext(Dispatchers.IO) {
                            scanMutex.withLock {
                                currentCoroutineContext().ensureActive()
                                val destination = downloadDestinationStore.current()
                                val totalAlbums = when (destination) {
                                    is DownloadDestination.Default -> File(destination.root).listFiles()
                                        ?.count { it.isDirectory && isScannableLocalDirectoryName(it.name) }
                                        ?: 0

                                    is DownloadDestination.DocumentTree -> {
                                        val uri = Uri.parse(destination.root)
                                        val treeId = DocumentsContract.getTreeDocumentId(uri)
                                        queryChildren(uri, treeId).count {
                                            it.mimeType == DocumentsContract.Document.MIME_TYPE_DIR &&
                                                isScannableLocalDirectoryName(it.displayName)
                                        }
                                    }
                                }
                                taskCoordinator.startBulkProgress(phase = BulkPhase.ScanningLocal, total = totalAlbums)
                                var current = 0
                                scanFromDownloadedDir(
                                    onAlbumScanned = { title ->
                                        current += 1
                                        taskCoordinator.updateBulkAlbumProgress(current = current, currentAlbumTitle = title)
                                    },
                                    importAll = true,
                                )
                            }
                        }
                        messageManager.showSuccess("目标下载目录扫描完成")
                    } catch (error: CancellationException) {
                        messageManager.showInfo("已取消目标目录扫描")
                    } catch (error: Exception) {
                        Log.e(TAG, "scanCurrentDownloadDestinationAsImport failed", error)
                        messageManager.showError("目标目录扫描失败：${error.message}")
                    } finally {
                        taskCoordinator.finishBulkProgress()
                        if (taskCoordinator.bulkJob == currentCoroutineContext()[Job]) taskCoordinator.bulkJob = null
                    }
                }
            } finally {
                syncCoordinator.end(token)
            }
        }
    }

    fun scanSingleRoot(uriString: String) {
        if (uriString.isBlank()) return
        scope.launch {
            val token = syncCoordinator.tryBegin() ?: run {
                taskCoordinator.showSyncBusy("刷新目录")
                return@launch
            }
            try {
                taskCoordinator.bulkStartMutex.withLock {
                    taskCoordinator.bulkJob = currentCoroutineContext()[Job]
                    try {
                        withContext(Dispatchers.IO) {
                            scanMutex.withLock {
                                currentCoroutineContext().ensureActive()
                                val uri = runCatching { Uri.parse(uriString) }.getOrNull()
                                val treeDocId = uri?.let { runCatching { DocumentsContract.getTreeDocumentId(it) }.getOrNull() }
                                val totalAlbums = if (uri != null && !treeDocId.isNullOrBlank()) {
                                    queryChildren(uri, treeDocId).count {
                                        it.mimeType == DocumentsContract.Document.MIME_TYPE_DIR &&
                                            isScannableLocalDirectoryName(it.displayName)
                                    }
                                } else {
                                    0
                                }
                                taskCoordinator.startBulkProgress(phase = BulkPhase.ScanningLocal, total = totalAlbums)
                                var current = 0
                                scanFromDocumentTree(uriString) { title ->
                                    current += 1
                                    taskCoordinator.updateBulkAlbumProgress(current = current, currentAlbumTitle = title)
                                }
                            }
                        }
                        messageManager.showSuccess("目录刷新完成")
                    } catch (e: CancellationException) {
                        messageManager.showInfo("已取消刷新")
                    } catch (e: Exception) {
                        Log.e("LibraryViewModel", "scanSingleRoot failed", e)
                        messageManager.showError("刷新失败：${e.message}")
                    } finally {
                        taskCoordinator.finishBulkProgress()
                        if (taskCoordinator.bulkJob == currentCoroutineContext()[Job]) {
                            taskCoordinator.bulkJob = null
                        }
                    }
                }
            } finally {
                syncCoordinator.end(token)
            }
        }
    }
}
