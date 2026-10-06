package com.asmr.player.data.download

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import androidx.work.*
import com.asmr.player.data.remote.download.DOWNLOAD_STATE_QUEUED
import com.asmr.player.data.remote.download.DownloadQueueCoordinator
import com.asmr.player.data.local.db.dao.DownloadDao
import com.asmr.player.data.local.db.entities.DownloadItemEntity
import com.asmr.player.data.local.db.entities.DownloadTaskEntity
import com.asmr.player.data.local.db.AppDatabaseProvider
import com.asmr.player.util.SubtitleEntry
import com.asmr.player.util.SubtitleMatchSupport
import com.asmr.player.util.SubtitleParser
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

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
