package com.asmr.player.ui.library

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.asmr.player.data.local.library.buildOnlineAlbumPath
import com.asmr.player.data.local.tree.SafTreeSupport
import com.asmr.player.data.repository.DownloadQueueRepository
import com.asmr.player.data.repository.LibraryReadRepository
import com.asmr.player.data.repository.LibraryWriteRepository
import com.asmr.player.domain.model.Album
import com.asmr.player.ui.common.audio.queryTrackFileSize
import com.asmr.player.ui.library.albumdetail.LocalTreeDeletionTarget
import com.asmr.player.ui.library.albumdetail.localTreePathMatchesTarget
import com.asmr.player.ui.library.albumdetail.normalizeLocalTreeRelativePath
import com.asmr.player.util.MessageManager
import com.asmr.player.util.SyncCoordinator
import com.asmr.player.util.isOnlineTrackPath
import com.asmr.player.util.isVirtualAlbumPath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * R3-C1b-ii：LibraryViewModel 删除族 State Holder（自 VM 逐字搬移，逻辑未改）。
 * - 成员：rescanAlbum / deleteAlbum / deleteAlbumTreeEntry / removeTrackFromAlbum。
 * - 任务注册/取消/同步状态经 [taskCoordinator]（C1b-ii-a 就位）；
 *   同步全局门经 [syncCoordinator]（VM 构造注入透传）。
 * - rescanAlbum 依赖的扫描底层两函数（scanSingleAlbumFromDocumentUri /
 *   scanTracksAndSubtitlesFromFileAlbum）经 [scanHolder]（C1c-a 扫描 holder）调用。
 * - 1 行代理随迁消除：upsertAlbumFtsIndex / refreshAlbumAudioAggregate /
 *   SafTreeSupport 委托（documentExists/deletePathSafely 等）直调实现。
 * - TAG 保持 "LibraryViewModel"：日志输出与抽取前逐字一致。
 */
internal class LibraryDeleteStateHolder(
    private val scope: CoroutineScope,
    private val context: Context,
    private val readRepository: LibraryReadRepository,
    private val writeRepository: LibraryWriteRepository,
    private val downloadQueueRepository: DownloadQueueRepository,
    private val syncCoordinator: SyncCoordinator,
    private val taskCoordinator: LibraryTaskCoordinator,
    private val messageManager: MessageManager,
    private val scanHolder: LibraryScanStateHolder,
) {
    private companion object {
        const val TAG = "LibraryViewModel"
    }

    fun rescanAlbum(album: Album) {
        val localPaths = album.getAllLocalPaths()
        if (localPaths.isEmpty()) return
        if (!taskCoordinator.tryRegisterAlbumJob(album.id, "本地同步")) return
        val job = scope.launch {
            val ownerJob = currentCoroutineContext()[Job]
            val token = syncCoordinator.tryBegin() ?: run {
                taskCoordinator.showSyncBusy("本地同步")
                taskCoordinator.albumJobs.remove(album.id, ownerJob)
                return@launch
            }
            taskCoordinator.syncStatus.value += (album.id to SyncStatus.Syncing)
            try {
                var removed = false
                withContext(Dispatchers.IO) {
                    currentCoroutineContext().ensureActive()
                    runCatching { writeRepository.clearLocalTreeCache(album.id) }

                    var scannedAny = false
                    localPaths.forEach { path ->
                        currentCoroutineContext().ensureActive()
                        val p = path.trim()
                        if (p.isBlank()) return@forEach

                        if (p.startsWith("content://")) {
                            val uri = runCatching { Uri.parse(p) }.getOrNull()
                            val docId = uri?.let { runCatching { DocumentsContract.getDocumentId(it) }.getOrNull() }
                            val treeDocId = uri?.let { runCatching { DocumentsContract.getTreeDocumentId(it) }.getOrNull() }
                            val treeUri = if (uri != null && !treeDocId.isNullOrBlank()) {
                                DocumentsContract.buildTreeDocumentUri(uri.authority, treeDocId)
                            } else null
                            val exists = treeUri != null && !docId.isNullOrBlank() &&
                                SafTreeSupport.documentExists(context, treeUri, docId)
                            if (exists) {
                                scanHolder.scanSingleAlbumFromDocumentUri(album.id, p)
                                scannedAny = true
                            }
                        } else {
                            val root = File(p)
                            val exists = root.exists()
                            val isDirectory = root.isDirectory
                            if (exists && isDirectory) {
                                scanHolder.scanTracksAndSubtitlesFromFileAlbum(album.id, root)
                                scannedAny = true
                            }
                        }
                    }

                    if (!scannedAny) {
                        val entity = readRepository.getAlbumById(album.id) ?: return@withContext
                        val tracks = readRepository.getTracksForAlbumOnce(entity.id)
                        val hasOnline = isVirtualAlbumPath(entity.path) || tracks.any { isOnlineTrackPath(it.path) }
                        if (!hasOnline) {
                            writeRepository.deleteAlbumTracksAndSubtitles(entity.id)
                            writeRepository.deleteAlbumEntity(entity)
                            removed = true
                        } else {
                            val prefixes = localPaths.map { it.trim() }.filter { it.isNotBlank() }
                            val toRemove = tracks.filter { t ->
                                !isOnlineTrackPath(t.path) && prefixes.any { pfx -> t.path.startsWith(pfx) }
                            }.map { it.id }
                            if (toRemove.isNotEmpty()) {
                                writeRepository.deleteTracksWithSubtitles(toRemove)
                            }

                            val updatedPath = if (prefixes.any { pfx -> entity.path.startsWith(pfx) }) {
                                buildOnlineAlbumPath(entity) ?: entity.path
                            } else {
                                entity.path
                            }
                            val updated = entity.copy(
                                path = updatedPath,
                                localPath = entity.localPath?.takeIf { lp -> prefixes.none { pfx -> lp.startsWith(pfx) } },
                                downloadPath = entity.downloadPath?.takeIf { dp -> prefixes.none { pfx -> dp.startsWith(pfx) } },
                                coverPath = entity.coverPath.takeIf { cp -> prefixes.none { pfx -> cp.startsWith(pfx) } }.orEmpty()
                            )
                            writeRepository.updateAlbum(updated)
                            writeRepository.upsertAlbumFtsIndex(updated.id, updated)
                        }
                    }
                }
                taskCoordinator.syncStatus.value -= album.id
                if (removed) {
                    messageManager.showInfo("目录不存在，已从本地库移除")
                } else {
                    messageManager.showSuccess("重扫完成")
                }
            } catch (e: CancellationException) {
                taskCoordinator.syncStatus.value -= album.id
                messageManager.showInfo("已取消重扫")
            } catch (e: Exception) {
                Log.e(TAG, "rescanAlbum failed: ${album.id}", e)
                taskCoordinator.syncStatus.value += (album.id to SyncStatus.Error(e.message ?: "重扫失败"))
                messageManager.showError("重扫失败：${e.message}")
                delay(3000)
                taskCoordinator.syncStatus.value -= album.id
            } finally {
                taskCoordinator.albumJobs.remove(album.id, ownerJob)
                syncCoordinator.end(token)
            }
        }
        taskCoordinator.albumJobs[album.id] = job
    }

    fun deleteAlbum(album: Album) {
        scope.launch(Dispatchers.IO) {
            if (album.id <= 0L) return@launch
            if (taskCoordinator.isBulkTaskRunning()) {
                messageManager.showInfo("正在执行批量任务，请先取消后再删除")
                return@launch
            }

            taskCoordinator.albumJobs.remove(album.id)?.cancel()
            taskCoordinator.syncStatus.value -= album.id

            try {
                val entity = readRepository.getAlbumById(album.id) ?: return@launch
                val downloadRoot = entity.downloadPath.orEmpty()

                writeRepository.deleteAlbumWithContent(album.id, entity)

                if (downloadRoot.isNotBlank()) {
                    val task = runCatching { readRepository.getDownloadTaskByRootDir(downloadRoot) }.getOrNull()
                    if (task != null) {
                        downloadQueueRepository.cancelWorksByTag(task.taskKey)
                        writeRepository.deleteDownloadTaskWithItems(task.id)
                    }
                    SafTreeSupport.deletePathSafely(context, downloadRoot)
                }

                messageManager.showSuccess("已删除专辑")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "deleteAlbum failed: ${album.id}", e)
                messageManager.showError("删除失败：${e.message ?: "未知错误"}")
            }
        }
    }

    fun deleteAlbumTreeEntry(
        album: Album,
        target: LocalTreeDeletionTarget,
        onComplete: (Boolean) -> Unit = {},
    ) {
        scope.launch(Dispatchers.IO) {
            val relativePath = normalizeLocalTreeRelativePath(target.relativePath)
            if (album.id <= 0L || relativePath == null) {
                messageManager.showError("删除失败：目录项路径无效")
                withContext(Dispatchers.Main.immediate) { onComplete(false) }
                return@launch
            }

            try {
                val albumRoots = album.getAllLocalPaths()
                val physicalDeletionSucceeded = when {
                    !target.hasLocalContent -> true
                    target.isDirectory -> SafTreeSupport.deleteLocalTreeDirectories(context, albumRoots, relativePath)
                    else -> SafTreeSupport.deleteLocalTreeFile(context, albumRoots, target.absolutePath.orEmpty())
                }
                check(physicalDeletionSucceeded) {
                    if (target.isDirectory) "无法删除目录，请检查存储权限" else "无法删除文件，请检查存储权限"
                }

                val verifiedTrackIds = target.trackIds
                    .asSequence()
                    .filter { it > 0L }
                    .distinct()
                    .toList()
                    .takeIf { it.isNotEmpty() }
                    ?.let { ids ->
                        readRepository.getTracksByIdsOnce(ids)
                            .filter { it.albumId == album.id }
                            .map { it.id }
                    }
                    .orEmpty()
                val resourceIds = readRepository.getOnlineSavedResourcesForAlbum(album.id)
                    .filter { resource ->
                        localTreePathMatchesTarget(
                            candidatePath = resource.relativePath,
                            targetPath = relativePath,
                            targetIsDirectory = target.isDirectory,
                        )
                    }
                    .map { it.id }

                writeRepository.deleteVerifiedTracksAndResources(album.id, verifiedTrackIds, resourceIds)
                if (verifiedTrackIds.isNotEmpty()) {
                    refreshAlbumAudioAggregate(album.id)
                }

                messageManager.showSuccess(if (target.isDirectory) "已删除目录" else "已删除文件")
                withContext(Dispatchers.Main.immediate) { onComplete(true) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "deleteAlbumTreeEntry failed: ${target.relativePath}", error)
                messageManager.showError("删除失败：${error.message ?: "未知错误"}")
                withContext(Dispatchers.Main.immediate) { onComplete(false) }
            }
        }
    }

    fun removeTrackFromAlbum(trackId: Long) {
        scope.launch(Dispatchers.IO) {
            val track = readRepository.getTrackByIdOnce(trackId) ?: return@launch
            val album = readRepository.getAlbumById(track.albumId)
            val path = track.path.trim()

            val deletedFile = if (path.startsWith("http", ignoreCase = true) || path.startsWith("content://", ignoreCase = true)) {
                false
            } else {
                val base = context.getExternalFilesDir(null)
                val allowedRoots = listOfNotNull(
                    album?.downloadPath?.takeIf { it.isNotBlank() }?.let { File(it) },
                    base
                )
                    .mapNotNull { runCatching { it.canonicalFile }.getOrNull() ?: it.absoluteFile }
                val target = runCatching { File(path).canonicalFile }.getOrNull() ?: File(path).absoluteFile
                val isInAllowed = allowedRoots.any { root -> SafTreeSupport.isCanonicalDescendant(target, root) }
                if (isInAllowed) SafTreeSupport.deletePathSafely(context, target.absolutePath) else false
            }

            writeRepository.deleteTrackCompletely(trackId, track.albumId)

            refreshAlbumAudioAggregate(track.albumId)

            if (deletedFile) {
                messageManager.showSuccess("已删除文件并移除")
            } else {
                messageManager.showSuccess("已从专辑移除")
            }
        }
    }

    private suspend fun refreshAlbumAudioAggregate(albumId: Long) {
        writeRepository.refreshAlbumAudioAggregate(albumId) { path ->
            queryTrackFileSize(context, path)
        }
    }
}
