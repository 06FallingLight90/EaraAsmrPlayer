package com.asmr.player.data.remote.download

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import androidx.work.*
import com.asmr.player.data.remote.auth.DlsiteAuthStore
import com.asmr.player.data.remote.NetworkHeaders
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.AlbumFtsEntity
import com.asmr.player.data.local.db.dao.DownloadDao
import com.asmr.player.data.local.db.entities.DownloadItemEntity
import com.asmr.player.data.local.db.entities.DownloadTaskEntity
import com.asmr.player.data.local.db.entities.RemoteSubtitleSourceEntity
import com.asmr.player.data.local.db.entities.SubtitleEntity
import com.asmr.player.data.local.db.entities.TrackEntity
import com.asmr.player.data.local.db.AppDatabaseProvider
import com.asmr.player.data.local.library.LocalAlbumMergeService
import com.asmr.player.util.SubtitleEntry
import com.asmr.player.util.DlsiteWorkNo
import com.asmr.player.util.SubtitleMatchSupport
import com.asmr.player.util.SubtitleParser
import com.asmr.player.util.TrackKeyNormalizer
import com.asmr.player.util.isScannableLocalDirectoryName
import com.asmr.player.util.isScannableLocalStorageEntry
import com.asmr.player.work.AlbumCoverThumbWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.util.UUID
import java.util.zip.ZipFile
import kotlin.math.max
import javax.inject.Inject
import javax.inject.Singleton
import com.asmr.player.data.download.DownloadStorageEntry
import com.asmr.player.data.download.DownloadStorageGateway
import com.asmr.player.data.download.DownloadDestinationStore
import com.asmr.player.data.download.DownloadDestination
import com.asmr.player.data.download.DownloadDirectoryCoordinator

private const val DLSITE_PLAY_SCRAMBLED_PART_SUFFIX = ".dlsite-scrambled.part"

internal fun dlsitePlayImagePartFile(outputFile: File): File {
    return File(outputFile.parentFile, outputFile.name + DLSITE_PLAY_SCRAMBLED_PART_SUFFIX)
}

internal fun downloadStagingFile(context: Context, item: DownloadItemEntity): File {
    val root = File(context.getExternalFilesDir(null), "download-staging")
    return File(root, "${item.id}_${item.fileName}")
}

internal fun downloadMimeType(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
    "mp3" -> "audio/mpeg"
    "flac" -> "audio/flac"
    "wav" -> "audio/wav"
    "m4a" -> "audio/mp4"
    "ogg", "opus" -> "audio/ogg"
    "mp4" -> "video/mp4"
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "webp" -> "image/webp"
    "lrc" -> "application/octet-stream"
    "srt" -> "application/x-subrip"
    "vtt" -> "text/vtt"
    "txt" -> "text/plain"
    "pdf" -> "application/pdf"
    "zip" -> "application/zip"
    else -> "application/octet-stream"
}

internal fun parseDownloadedSubtitles(
    entries: List<DownloadStorageEntry>,
    readBytes: (DownloadStorageEntry) -> ByteArray,
): Map<String, List<SubtitleEntry>> {
    val subtitleCandidates = entries.asSequence()
        .filter { entry ->
            !entry.isDirectory && SubtitleMatchSupport.SubtitleExtensions.contains(
                entry.displayName.substringAfterLast('.', "").lowercase(),
            )
        }
        .mapNotNull { entry ->
            SubtitleMatchSupport.inferCandidate(entry.relativePath, entry.reference)?.let { candidate ->
                candidate to entry
            }
        }
        .toList()
    val candidates = subtitleCandidates.map { it.first }
    return entries.asSequence()
        .filter { entry ->
            !entry.isDirectory && SubtitleMatchSupport.AudioExtensions.contains(
                entry.displayName.substringAfterLast('.', "").lowercase(),
            )
        }
        .associate { audio ->
            val matched = SubtitleMatchSupport.matchBest(
                audio.relativePath.substringBeforeLast('.'),
                candidates,
            )
            val subtitle = matched?.let { hit ->
                subtitleCandidates.firstOrNull { it.first.sourceRef == hit.sourceRef }?.second
            }
            audio.reference to subtitle?.let { entry ->
                runCatching {
                    SubtitleParser.parseBytes(
                        entry.displayName.substringAfterLast('.', ""),
                        readBytes(entry),
                    )
                }.getOrDefault(emptyList())
            }.orEmpty()
        }
}

data class RelativeDownloadItem(
    val url: String,
    val relativePath: String,
    val dlsitePlayImageSeed: Int? = null,
    val dlsitePlayImageWidth: Int? = null,
    val dlsitePlayImageHeight: Int? = null,
)

data class DownloadBatchRequest(
    val albumDirectoryName: String,
    val logicalTaskKey: String,
    val items: List<RelativeDownloadItem>,
    val taskSubtitle: String = "",
    val albumTitle: String = "",
    val albumCircle: String = "",
    val albumCv: String = "",
    val albumTagsCsv: String = "",
    val albumCoverUrl: String = "",
    val albumDescription: String = "",
    val albumWorkId: String = "",
    val albumRjCode: String = "",
)

sealed interface EnqueueDownloadBatchResult {
    data class Accepted(val itemCount: Int) : EnqueueDownloadBatchResult
    data object DirectoryUnavailable : EnqueueDownloadBatchResult
    data object TaskBlocked : EnqueueDownloadBatchResult
}

private data class PendingDownloadItemRequest(
    val url: String,
    val fileName: String,
    val targetDir: String,
    val taskRootDir: String,
    val relativePath: String,
    val taskSubtitle: String,
    val tags: List<String>,
    val albumTitle: String,
    val albumCircle: String,
    val albumCv: String,
    val albumTagsCsv: String,
    val albumCoverUrl: String,
    val albumDescription: String,
    val albumWorkId: String,
    val albumRjCode: String,
    val dlsitePlayImageSeed: Int?,
    val dlsitePlayImageWidth: Int?,
    val dlsitePlayImageHeight: Int?,
)

internal fun DownloadItemEntity.hasDlsitePlayImageTransform(): Boolean {
    return dlsitePlayImageSeed != null &&
        (dlsitePlayImageWidth ?: 0) > 0 &&
        (dlsitePlayImageHeight ?: 0) > 0
}

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao,
    private val directoryCoordinator: DownloadDirectoryCoordinator,
    private val storage: DownloadStorageGateway,
) {
    suspend fun enqueueBatch(request: DownloadBatchRequest): EnqueueDownloadBatchResult {
        if (request.items.isEmpty()) return EnqueueDownloadBatchResult.Accepted(0)
        val albumDirectory = request.albumDirectoryName.replace('\\', '/').trim('/').ifBlank { "download" }
        if (albumDirectory.contains('/') || albumDirectory == "." || albumDirectory == "..") {
            return EnqueueDownloadBatchResult.DirectoryUnavailable
        }
        val defaultRoot = DownloadDestinationStore(context).defaultDestination().root
        val taskRoot = File(defaultRoot, albumDirectory)
        val pendingRequests = request.items.mapNotNull { item ->
            val relativePath = item.relativePath.replace('\\', '/').trim('/').ifBlank { "download.bin" }
            val segments = relativePath.split('/').filter { it.isNotBlank() }
            if (segments.isEmpty() || segments.any { it == "." || it == ".." }) return@mapNotNull null
            val fileName = relativePath.substringAfterLast('/').ifBlank { "download.bin" }
            val relativeDirectory = relativePath.substringBeforeLast('/', "")
            PendingDownloadItemRequest(
                url = item.url,
                fileName = fileName,
                targetDir = if (relativeDirectory.isBlank()) taskRoot.absolutePath else File(taskRoot, relativeDirectory).absolutePath,
                taskRootDir = taskRoot.absolutePath,
                relativePath = relativePath,
                taskSubtitle = request.taskSubtitle,
                tags = listOf(request.logicalTaskKey),
                albumTitle = request.albumTitle,
                albumCircle = request.albumCircle,
                albumCv = request.albumCv,
                albumTagsCsv = request.albumTagsCsv,
                albumCoverUrl = request.albumCoverUrl,
                albumDescription = request.albumDescription,
                albumWorkId = request.albumWorkId,
                albumRjCode = request.albumRjCode,
                dlsitePlayImageSeed = item.dlsitePlayImageSeed,
                dlsitePlayImageWidth = item.dlsitePlayImageWidth,
                dlsitePlayImageHeight = item.dlsitePlayImageHeight,
            )
        }
        if (pendingRequests.size != request.items.size) return EnqueueDownloadBatchResult.DirectoryUnavailable
        return enqueueRequests(pendingRequests)
    }

    private suspend fun enqueueRequests(requests: List<PendingDownloadItemRequest>): EnqueueDownloadBatchResult {
        return directoryCoordinator.withDirectoryLock {
            try {
                val destination = directoryCoordinator.currentDestination()
                if (destination is DownloadDestination.DocumentTree && !storage.hasPersistedWritePermission(destination.root)) {
                    return@withDirectoryLock EnqueueDownloadBatchResult.DirectoryUnavailable
                }
                val destinationKey = storage.stableIdentity(destination.root).hashCode().toUInt().toString(16)
                val hasBlockedItem = requests.any { request ->
                    val logicalTaskKey = request.tags.firstOrNull { it.startsWith("album:") }
                        ?: "dir:${request.taskRootDir}"
                    val task = downloadDao.getTaskByKey("$logicalTaskKey@$destinationKey")
                        ?: return@any false
                    val item = downloadDao.getItemByTaskAndRelativePath(task.id, request.relativePath)
                        ?: return@any false
                    item.state in setOf(
                        WorkInfo.State.RUNNING.name,
                        WorkInfo.State.ENQUEUED.name,
                        WorkInfo.State.BLOCKED.name,
                        DOWNLOAD_STATE_QUEUED,
                    )
                }
                if (hasBlockedItem) return@withDirectoryLock EnqueueDownloadBatchResult.TaskBlocked
                requests.forEach { request -> enqueueDownloadLocked(request) }
                EnqueueDownloadBatchResult.Accepted(requests.size)
            } catch (_: DownloadTaskBlockedException) {
                EnqueueDownloadBatchResult.TaskBlocked
            } catch (e: Exception) {
                Log.w(TAG, "enqueueBatch failed dir=${requests.firstOrNull()?.taskRootDir.orEmpty()}", e)
                EnqueueDownloadBatchResult.DirectoryUnavailable
            }
        }
    }

    private suspend fun enqueueDownloadLocked(request: PendingDownloadItemRequest) {
            val resolvedPaths = directoryCoordinator.resolveLegacyPaths(request.targetDir, request.taskRootDir)
            val url = request.url
            val fileName = request.fileName
            val targetDir = resolvedPaths.targetDir
            val taskRootDir = resolvedPaths.taskRootDir
            val relativePath = request.relativePath
            val taskSubtitle = request.taskSubtitle
            val tags = request.tags
            val albumTitle = request.albumTitle
            val albumCircle = request.albumCircle
            val albumCv = request.albumCv
            val albumTagsCsv = request.albumTagsCsv
            val albumCoverUrl = request.albumCoverUrl
            val albumDescription = request.albumDescription
            val albumWorkId = request.albumWorkId
            val albumRjCode = request.albumRjCode
            val dlsitePlayImageSeed = request.dlsitePlayImageSeed
            val dlsitePlayImageWidth = request.dlsitePlayImageWidth
            val dlsitePlayImageHeight = request.dlsitePlayImageHeight
            val now = System.currentTimeMillis()

            val logicalTaskKey = tags.firstOrNull { it.startsWith("album:") } ?: "dir:$taskRootDir"
            val destinationKey = storage.stableIdentity(resolvedPaths.destinationRoot).hashCode().toUInt().toString(16)
            val taskKey = "$logicalTaskKey@$destinationKey"
            val taskTitle = logicalTaskKey.removePrefix("album:").ifBlank { File(taskRootDir).name.ifBlank { "download" } }
            val safeTaskSubtitle = taskSubtitle.trim()
            val safeRelativePath = relativePath.ifBlank { fileName }.replace('\\', '/')
            val existingDestinationFile = storage.findFile(targetDir, fileName)
            val filePath = existingDestinationFile.orEmpty().ifBlank {
                if (storage.isDocumentReference(targetDir)) "" else File(targetDir, fileName).absolutePath
            }
            val safeAlbumTitle = albumTitle.trim().take(200)
            val safeAlbumCircle = albumCircle.trim().take(200)
            val safeAlbumCv = albumCv.trim().take(400)
            val safeAlbumTagsCsv = albumTagsCsv.trim().take(1200)
            val safeAlbumCoverUrl = albumCoverUrl.trim().take(800)
            val safeAlbumDescription = albumDescription.trim().take(0)
            val safeAlbumWorkId = albumWorkId.trim().take(40)
            val safeAlbumRjCode = albumRjCode.trim().take(40)
            val hasDlsitePlayImageTransform = dlsitePlayImageSeed != null &&
                (dlsitePlayImageWidth ?: 0) > 0 &&
                (dlsitePlayImageHeight ?: 0) > 0
            val safeDlsitePlayImageSeed = dlsitePlayImageSeed.takeIf { hasDlsitePlayImageTransform }
            val safeDlsitePlayImageWidth = dlsitePlayImageWidth.takeIf { hasDlsitePlayImageTransform }
            val safeDlsitePlayImageHeight = dlsitePlayImageHeight.takeIf { hasDlsitePlayImageTransform }
            val taskId = ensureTask(
                taskKey = taskKey,
                logicalTaskKey = logicalTaskKey,
                title = taskTitle,
                subtitle = safeTaskSubtitle,
                rootDir = taskRootDir,
                destinationRoot = resolvedPaths.destinationRoot,
                albumRootDir = resolvedPaths.albumRootDir,
                albumTitle = safeAlbumTitle,
                albumCircle = safeAlbumCircle,
                albumCv = safeAlbumCv,
                albumTagsCsv = safeAlbumTagsCsv,
                albumCoverUrl = safeAlbumCoverUrl,
                albumDescription = safeAlbumDescription,
                albumWorkId = safeAlbumWorkId,
                albumRjCode = safeAlbumRjCode,
                now = now
            )

            if (existingDestinationFile != null && storage.exists(existingDestinationFile)) {
                val size = storage.size(existingDestinationFile)
                val existingItem = downloadDao.getItemByTaskAndRelativePath(taskId, safeRelativePath)
                if (existingItem != null) {
                    downloadDao.updateItemProgress(
                        workId = existingItem.workId,
                        state = WorkInfo.State.SUCCEEDED.name,
                        downloaded = size,
                        total = size,
                        speed = 0L,
                        updatedAt = now
                    )
                } else {
                    val localWorkId = "local_${UUID.randomUUID()}"
                    downloadDao.upsertItem(
                        DownloadItemEntity(
                            taskId = taskId,
                            workId = localWorkId,
                            url = url,
                            relativePath = safeRelativePath,
                            fileName = fileName,
                            targetDir = targetDir,
                            filePath = filePath,
                            state = WorkInfo.State.SUCCEEDED.name,
                            downloaded = size,
                            total = size,
                            speed = 0L,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                }
                runCatching {
                    val task = downloadDao.getTaskByKey(taskKey)
                    if (task != null) {
                        val items = downloadDao.getItemsForTask(task.id)
                        val done = items.isNotEmpty() && items.all { it.state == WorkInfo.State.SUCCEEDED.name }
                        if (done) {
                            val db = AppDatabaseProvider.get(context)
                            db.withTransaction {
                                upsertDownloadedAlbumToLibrary(
                                    db = db,
                                    appContext = context,
                                    rootDir = resolvedPaths.albumRootDir,
                                    taskTitle = taskTitle,
                                    taskSubtitle = safeTaskSubtitle,
                                    albumTitle = safeAlbumTitle,
                                    albumCircle = safeAlbumCircle,
                                    albumCv = safeAlbumCv,
                                    albumTagsCsv = safeAlbumTagsCsv,
                                    albumCoverUrl = safeAlbumCoverUrl,
                                    albumDescription = safeAlbumDescription,
                                    albumWorkId = safeAlbumWorkId,
                                    albumRjCode = safeAlbumRjCode
                                )
                            }
                            runCatching {
                                if (storage.isDocumentReference(resolvedPaths.albumRootDir)) {
                                    storage.ensureFile(
                                        resolvedPaths.albumRootDir,
                                        ".download_complete",
                                        "application/octet-stream",
                                    )
                                } else {
                                    val marker = File(resolvedPaths.albumRootDir, ".download_complete")
                                    if (!marker.exists()) marker.createNewFile()
                                }
                                Unit
                            }
                        }
                    }
                }
                return
            }

            val existingItem = downloadDao.getItemByTaskAndRelativePath(taskId, safeRelativePath)
            val partialFile = existingItem?.let { downloadStagingFile(context, it) }
            val existingBytes = runCatching {
                when {
                    partialFile != null && partialFile.exists() -> partialFile.length()
                    filePath.isNotBlank() -> storage.size(filePath)
                    else -> 0L
                }
            }.getOrDefault(0L).coerceAtLeast(0L)
            if (existingItem != null) {
                val existingReference = existingItem.filePath.ifBlank { filePath }
                if (existingItem.state == WorkInfo.State.SUCCEEDED.name && storage.exists(existingReference)) {
                    return
                }
                if (
                    existingItem.state == WorkInfo.State.RUNNING.name ||
                    existingItem.state == WorkInfo.State.ENQUEUED.name ||
                    existingItem.state == WorkInfo.State.BLOCKED.name ||
                    existingItem.state == DOWNLOAD_STATE_QUEUED
                ) {
                    throw DownloadTaskBlockedException()
                }
                downloadDao.upsertItem(
                    existingItem.copy(
                        state = DOWNLOAD_STATE_QUEUED,
                        downloaded = existingBytes,
                        speed = 0L,
                        updatedAt = now,
                        dlsitePlayImageSeed = safeDlsitePlayImageSeed,
                        dlsitePlayImageWidth = safeDlsitePlayImageWidth,
                        dlsitePlayImageHeight = safeDlsitePlayImageHeight
                    )
                )
            } else {
                downloadDao.upsertItem(
                    DownloadItemEntity(
                        taskId = taskId,
                        workId = "queued_${UUID.randomUUID()}",
                        url = url,
                        relativePath = safeRelativePath,
                        fileName = fileName,
                        targetDir = targetDir,
                        filePath = filePath,
                        state = DOWNLOAD_STATE_QUEUED,
                        downloaded = existingBytes,
                        total = -1L,
                        speed = 0L,
                        createdAt = now,
                        updatedAt = now,
                        dlsitePlayImageSeed = safeDlsitePlayImageSeed,
                        dlsitePlayImageWidth = safeDlsitePlayImageWidth,
                        dlsitePlayImageHeight = safeDlsitePlayImageHeight
                    )
                )
            }
            DownloadQueueCoordinator.requestSchedule(context)
    }

    private class DownloadTaskBlockedException : IllegalStateException()

    private suspend fun ensureTask(
        taskKey: String,
        logicalTaskKey: String,
        title: String,
        subtitle: String,
        rootDir: String,
        destinationRoot: String,
        albumRootDir: String,
        albumTitle: String,
        albumCircle: String,
        albumCv: String,
        albumTagsCsv: String,
        albumCoverUrl: String,
        albumDescription: String,
        albumWorkId: String,
        albumRjCode: String,
        now: Long
    ): Long {
        val existing = downloadDao.getTaskByKey(taskKey)
        if (existing != null) {
            val resolvedSubtitle = existing.subtitle.ifBlank { subtitle }
            val resolvedAlbumTitle = existing.albumTitle.ifBlank { albumTitle }
            val resolvedAlbumCircle = existing.albumCircle.ifBlank { albumCircle }
            val resolvedAlbumCv = existing.albumCv.ifBlank { albumCv }
            val resolvedAlbumTagsCsv = existing.albumTagsCsv.ifBlank { albumTagsCsv }
            val resolvedAlbumCoverUrl = existing.albumCoverUrl.ifBlank { albumCoverUrl }
            val resolvedAlbumDescription = existing.albumDescription.ifBlank { albumDescription }
            val resolvedAlbumWorkId = existing.albumWorkId.ifBlank { albumWorkId }
            val resolvedAlbumRjCode = existing.albumRjCode.ifBlank { albumRjCode }
            if (
                resolvedSubtitle != existing.subtitle ||
                resolvedAlbumTitle != existing.albumTitle ||
                resolvedAlbumCircle != existing.albumCircle ||
                resolvedAlbumCv != existing.albumCv ||
                resolvedAlbumTagsCsv != existing.albumTagsCsv ||
                resolvedAlbumCoverUrl != existing.albumCoverUrl ||
                resolvedAlbumDescription != existing.albumDescription ||
                resolvedAlbumWorkId != existing.albumWorkId ||
                resolvedAlbumRjCode != existing.albumRjCode
            ) {
                runCatching {
                    downloadDao.updateTaskMetadata(
                        taskId = existing.id,
                        subtitle = resolvedSubtitle,
                        albumTitle = resolvedAlbumTitle,
                        albumCircle = resolvedAlbumCircle,
                        albumCv = resolvedAlbumCv,
                        albumTagsCsv = resolvedAlbumTagsCsv,
                        albumCoverUrl = resolvedAlbumCoverUrl,
                        albumDescription = resolvedAlbumDescription,
                        albumWorkId = resolvedAlbumWorkId,
                        albumRjCode = resolvedAlbumRjCode,
                        updatedAt = now
                    )
                }
            }
            return existing.id
        }
        val created = downloadDao.insertTask(
            DownloadTaskEntity(
                taskKey = taskKey,
                logicalTaskKey = logicalTaskKey,
                title = title,
                subtitle = subtitle,
                rootDir = rootDir,
                destinationRoot = destinationRoot,
                albumRootDir = albumRootDir,
                albumTitle = albumTitle,
                albumCircle = albumCircle,
                albumCv = albumCv,
                albumTagsCsv = albumTagsCsv,
                albumCoverUrl = albumCoverUrl,
                albumDescription = albumDescription,
                albumWorkId = albumWorkId,
                albumRjCode = albumRjCode,
                createdAt = now,
                updatedAt = now
            )
        )
        if (created > 0) return created
        return downloadDao.getTaskByKey(taskKey)?.id ?: 0L
    }

    companion object {
        private const val TAG = "DownloadManager"
    }
}

private fun File.copyFrom(input: InputStream) {
    parentFile?.mkdirs()
    FileOutputStream(this).use { output ->
        input.copyTo(output)
    }
}

private fun scoreZipEntryNames(names: List<String>): Int {
    if (names.isEmpty()) return Int.MIN_VALUE
    var score = 0
    names.forEach { name ->
        if (name.isBlank()) {
            score -= 100
            return@forEach
        }
        if ('\uFFFD' in name) score -= 500
        if (name.contains('?')) score -= 20
        if (name.any { it.code in 0xE000..0xF8FF }) score -= 50
        if (name.any { it.isLetterOrDigit() }) score += 10
        if (name.any { it.code in 0x3040..0x30FF }) score += 15
        if (name.any { it.code in 0x4E00..0x9FFF }) score += 15
        if (name.any { it in listOf('【', '】', '〜', '～', '・', '「', '」', '（', '）', '！') }) score += 8
        if (name.endsWith("/")) score += 1
    }
    return score
}

private data class ZipCharsetCandidate(
    val charset: Charset?,
    val score: Int
)

private inline fun <T> useBestEffortZipFile(zipFile: File, block: (ZipFile) -> T): T {
    val charsets = listOf(
        null,
        Charsets.UTF_8,
        Charset.forName("MS932"),
        Charset.forName("Shift_JIS"),
        Charset.forName("GB18030"),
        Charset.forName("Big5")
    )

    var best: ZipCharsetCandidate? = null
    var lastError: Throwable? = null

    charsets.forEach { charset ->
        val zip = runCatching {
            if (charset == null) ZipFile(zipFile) else ZipFile(zipFile, charset)
        }.getOrElse {
            lastError = it
            return@forEach
        }
        zip.use { archive ->
            val names = runCatching {
                archive.entries().asSequence().map { it.name }.take(200).toList()
            }.getOrElse {
                lastError = it
                return@use
            }
            val score = scoreZipEntryNames(names)
            if (best == null || score > best!!.score) {
                best = ZipCharsetCandidate(charset = charset, score = score)
            }
        }
    }

    val selectedCharset = best?.charset
    val selectedZip = runCatching {
        if (selectedCharset == null) ZipFile(zipFile) else ZipFile(zipFile, selectedCharset)
    }.getOrElse {
        throw (lastError ?: it)
    }
    selectedZip.use { return block(it) }
}

private fun unzipIntoRootDirectory(zipFile: File, rootDir: File) {
    useBestEffortZipFile(zipFile) { archive ->
        archive.entries().asSequence().forEach { entry ->
            val rawName = entry.name.replace('\\', '/').trimStart('/')
            if (rawName.isBlank()) return@forEach
            val outFile = File(rootDir, rawName)
            val canonicalRoot = rootDir.canonicalFile
            val canonicalOut = outFile.canonicalFile
            if (!canonicalOut.path.startsWith(canonicalRoot.path)) return@forEach
            if (entry.isDirectory) {
                canonicalOut.mkdirs()
            } else {
                archive.getInputStream(entry).use { input ->
                    canonicalOut.copyFrom(input)
                }
            }
        }
    }
}

internal fun finalizeDlsiteLosslessArchiveIfNeeded(rootDir: File, items: List<DownloadItemEntity>) {
    if (!rootDir.isDirectory) return
    val archive = items.asSequence()
        .filter { item -> item.fileName.equals("dlsite_lossless_archive.zip", ignoreCase = true) }
        .map { item -> File(item.filePath.ifBlank { File(item.targetDir, item.fileName).absolutePath }) }
        .firstOrNull { file ->
            file.exists() &&
                file.isFile &&
                file.parentFile?.absolutePath == rootDir.absolutePath &&
                file.extension.equals("zip", ignoreCase = true)
        }
        ?: return
    runCatching {
        unzipIntoRootDirectory(archive, rootDir)
        archive.delete()
    }
}

internal suspend fun finalizeDlsiteLosslessArchiveInStorageIfNeeded(
    context: Context,
    rootDir: String,
    items: List<DownloadItemEntity>,
    storage: DownloadStorageGateway,
) {
    val archiveItem = items.firstOrNull { item ->
        item.fileName.equals("dlsite_lossless_archive.zip", ignoreCase = true)
    } ?: return
    val stagingArchive = downloadStagingFile(context, archiveItem)
    if (!stagingArchive.isFile) return

    useBestEffortZipFile(stagingArchive) { archive ->
        archive.entries().asSequence().forEach { entry ->
            val normalized = entry.name.replace('\\', '/').trimStart('/')
            val segments = normalized.split('/').filter { it.isNotBlank() }
            if (segments.isEmpty() || segments.any { it == "." || it == ".." }) return@forEach
            val parentPath = segments.dropLast(1).joinToString("/")
            val parent = storage.resolveDirectory(rootDir, parentPath)
            if (!entry.isDirectory) {
                val outputReference = storage.ensureFile(
                    directory = parent,
                    name = segments.last(),
                    mimeType = downloadMimeType(segments.last()),
                )
                archive.getInputStream(entry).use { input ->
                    storage.openOutput(outputReference).use { output -> input.copyTo(output) }
                }
            }
        }
    }
    storage.delete(archiveItem.filePath)
    stagingArchive.delete()
}

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
        description = existing?.description?.takeIf { it.isNotBlank() } ?: albumDescription.trim()
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
