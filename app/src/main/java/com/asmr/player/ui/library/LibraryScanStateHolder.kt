package com.asmr.player.ui.library

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.library.buildOnlineAlbumPath
import com.asmr.player.data.local.library.ensureLibraryAlbumDir
import com.asmr.player.data.repository.LibraryReadRepository
import com.asmr.player.data.repository.LibraryWriteRepository
import com.asmr.player.util.MessageManager
import com.asmr.player.util.ScanRootsStore
import com.asmr.player.util.SyncCoordinator
import com.asmr.player.util.isOnlineTrackPath
import com.asmr.player.util.isVirtualAlbumPath
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/**
 * R3-C1c：LibraryViewModel 扫描族 State Holder（R3 期自 VM 逐字搬移；T11 起职责收缩）。
 * - T11：扫描底层（封面挑选/树缓存叶/SAF 遍历/字幕匹配/下载目录与文档树扫描/孤儿清理/
 *   WorkManager 后处理入队/resolveAndMerge）下沉 `scan.LibraryScanPipeline`（与批量入口
 *   CoroutineWorker 共享同一管线本体）；三个批量扫描入口改为一行调度（全局门预检 +
 *   [LibraryScanWorker.enqueue] 幂等入队），设置页与 VM 调用面签名不变。
 * - 本类保留：扫描根管理（scanRootsStore/_scanRoots/scanRoots、addScanRoot/removeScanRoot/
 *   removeScanRootAndDeleteAlbums）、VM init 的根恢复与旧版在线保存根回填，
 *   以及删除族单册重扫依赖的两个管线委托（scanSingleAlbumFromDocumentUri/
 *   scanTracksAndSubtitlesFromFileAlbum）。
 * - 批量取消经 [cancelScheduledScanWork]（WorkManager unique work 取消），
 *   由 VM.cancelBulkTask 与原任务协调取消路径合并调用。
 */
internal class LibraryScanStateHolder(
    private val scope: CoroutineScope,
    private val context: Context,
    private val readRepository: LibraryReadRepository,
    private val writeRepository: LibraryWriteRepository,
    private val syncCoordinator: SyncCoordinator,
    private val taskCoordinator: LibraryTaskCoordinator,
    private val messageManager: MessageManager,
    private val pipeline: LibraryScanPipeline,
) {
    private val scanRootsStore = ScanRootsStore(context)
    private val _scanRoots = MutableStateFlow<Set<String>>(emptySet())
    val scanRoots: StateFlow<List<String>> = _scanRoots
        .map { it.toList().sorted() }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** VM init 自动扫描判定读取（行为保持：原 VM 直读 scanRootsStore）。 */
    fun getRootsFromStore(): Set<String> = scanRootsStore.getRoots()

    /** VM init 恢复扫描根列表（原 init 首行逐字随迁）。 */
    fun restoreScanRootsFromStore() {
        _scanRoots.value = runCatching { scanRootsStore.getRoots() }.getOrDefault(emptySet())
    }

    fun legacyOnlineSavedAlbumDir(entity: AlbumEntity): File = pipeline.legacyOnlineSavedAlbumDir(entity)

    suspend fun backfillLegacyOnlineSavedAlbumRoots() {
        val albums = runCatching { readRepository.getAllAlbumsOnce() }.getOrDefault(emptyList())
        if (albums.isEmpty()) return

        writeRepository.backfillLegacyOnlineSavedAlbumRoots(albums) { entity ->
            val albumDir = pipeline.legacyOnlineSavedAlbumDir(entity)
            ensureLibraryAlbumDir(albumDir)
            albumDir.absolutePath
        }
    }

    /** 删除族（LibraryDeleteStateHolder.rescanAlbum）依赖的单册 SAF 重扫入口（T11 起委托管线本体）。 */
    suspend fun scanSingleAlbumFromDocumentUri(albumId: Long, albumUriString: String) =
        pipeline.scanSingleAlbumFromDocumentUri(albumId, albumUriString)

    /** 删除族（LibraryDeleteStateHolder.rescanAlbum）依赖的单册 File 重扫入口（T11 起委托管线本体）。 */
    suspend fun scanTracksAndSubtitlesFromFileAlbum(albumId: Long, albumDir: File) =
        pipeline.scanTracksAndSubtitlesFromFileAlbum(albumId, albumDir)

    // ---------- 批量扫描入口（T11：全局门预检 + Worker 幂等入队，编排体见 LibraryScanWorker） ----------

    fun scanAllRoots() = scheduleScan(LibraryScanWorker.MODE_SCAN_ALL_ROOTS, null, "刷新本地")

    fun scanCurrentDownloadDestinationAsImport() =
        scheduleScan(LibraryScanWorker.MODE_SCAN_DOWNLOAD_IMPORT, null, "扫描目标下载目录")

    fun scanSingleRoot(uriString: String) {
        if (uriString.isBlank()) return
        scheduleScan(LibraryScanWorker.MODE_SCAN_SINGLE_ROOT, uriString, "刷新目录")
    }

    /** 取消 Worker 内运行的/排队的批量扫描（VM.cancelBulkTask 与原协调器取消路径合并调用）。 */
    fun cancelScheduledScanWork() {
        WorkManager.getInstance(context).cancelUniqueWork(LibraryScanWorker.UNIQUE_WORK_NAME)
    }

    /**
     * 触发侧全局门预检：门被占（云同步/删除族/运行中的扫描）时提示并丢弃请求——
     * 与原实现"tryBegin 失败即 showSyncBusy 返回"的互斥不排队语义对齐；
     * 门空闲才入队，KEEP 策略兜底去重（竞态窗口内的重复触发静默丢弃）。
     */
    private fun scheduleScan(mode: Int, rootUri: String?, busyNextAction: String) {
        if (syncCoordinator.state.value != null) {
            taskCoordinator.showSyncBusy(busyNextAction)
            return
        }
        LibraryScanWorker.enqueue(context, mode, rootUri)
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
}
