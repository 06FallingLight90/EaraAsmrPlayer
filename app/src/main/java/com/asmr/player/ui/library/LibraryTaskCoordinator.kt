package com.asmr.player.ui.library

import android.os.SystemClock
import com.asmr.player.util.MessageManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap

/**
 * R3-C1b-ii：LibraryViewModel 任务协调 State Holder（自 VM 逐字搬移，逻辑未改）。
 * - 承接单专辑任务注册表（albumJobs）、批量任务门（bulkStartMutex/bulkJob）、
 *   单专辑同步状态（syncStatus）、批量进度（bulkProgress）与云同步选择队列。
 * - 扫描/删除/云同步三族共享：批量入口（scanAllRoots 等五处 bulkStartMutex 模式）、
 *   删除族（deleteAlbum 的 isBulkTaskRunning/取消路径）与单专辑同步（tryRegisterAlbumJob）都依赖它。
 * - syncStatus/albumJobs/bulkJob/cloudSyncSelectionQueue 以可变状态直接暴露，
 *   供迁移中的调用族逐字保持原操作（value += / map.remove(key, owner) 等）。
 */
internal class LibraryTaskCoordinator(private val messageManager: MessageManager) {
    val bulkStartMutex = Mutex()
    var bulkJob: Job? = null
    val albumJobs = ConcurrentHashMap<Long, Job>()
    val syncStatus = MutableStateFlow<Map<Long, SyncStatus>>(emptyMap())
    private val _bulkProgress = MutableStateFlow<BulkProgress?>(null)
    val bulkProgress: StateFlow<BulkProgress?> = _bulkProgress.asStateFlow()
    val cloudSyncSelectionQueue = CloudSyncSelectionRequestQueue()
    private var lastFileUpdateElapsedMs: Long = 0L

    val syncingAlbums: StateFlow<Map<Long, SyncStatus>> = syncStatus.asStateFlow()

    fun cancelBulkTask() {
        val job = bulkJob
        job?.cancel()
        bulkJob = null
        cloudSyncSelectionQueue.cancelAll()
        _bulkProgress.value = null
    }

    fun isBulkTaskRunning(): Boolean {
        return bulkJob?.isActive == true
    }

    fun showSyncBusy(nextAction: String) {
        messageManager.showInfo("同步任务进行中，请等待完成或取消后再$nextAction")
    }

    fun tryRegisterAlbumJob(albumId: Long, taskName: String): Boolean {
        if (albumId <= 0L) return false
        if (isBulkTaskRunning()) {
            messageManager.showInfo("正在执行批量任务，请先取消后再$taskName")
            return false
        }
        val existing = albumJobs[albumId]
        if (existing?.isActive == true) {
            messageManager.showInfo("该专辑正在执行${taskName}")
            return false
        }
        return true
    }

    fun cancelAlbumTask(albumId: Long) {
        val job = albumJobs.remove(albumId)
        if (job == null) {
            messageManager.showInfo("没有可取消的任务")
            return
        }
        job.cancel()
        cloudSyncSelectionQueue.cancelForAlbum(albumId)
        syncStatus.value -= albumId
        messageManager.showInfo("已取消任务")
    }

    fun confirmCloudSyncSelection(workno: String) {
        cloudSyncSelectionQueue.resolveCurrent(workno)
    }

    fun cancelCloudSyncSelection() {
        cloudSyncSelectionQueue.resolveCurrent(null)
    }

    fun ignoreAllCloudSyncSelections() {
        cloudSyncSelectionQueue.ignoreAllRemainingInBatch()
        messageManager.showInfo("已忽略本轮剩余待确认项")
    }

    fun startBulkProgress(phase: BulkPhase, total: Int, current: Int = 0, currentAlbumTitle: String = "") {
        _bulkProgress.value = BulkProgress(
            phase = phase,
            current = current.coerceAtLeast(0),
            total = total.coerceAtLeast(0),
            currentAlbumTitle = currentAlbumTitle,
            currentFile = ""
        )
        lastFileUpdateElapsedMs = 0L
    }

    fun updateBulkAlbumProgress(current: Int, currentAlbumTitle: String) {
        val p = _bulkProgress.value ?: return
        _bulkProgress.value = p.copy(
            current = current.coerceAtLeast(0),
            currentAlbumTitle = currentAlbumTitle
        )
    }

    fun maybeUpdateBulkCurrentFile(currentFile: String) {
        val p = _bulkProgress.value ?: return
        val now = SystemClock.elapsedRealtime()
        if (now - lastFileUpdateElapsedMs < 120L) return
        lastFileUpdateElapsedMs = now
        _bulkProgress.value = p.copy(currentFile = currentFile)
    }

    fun finishBulkProgress() {
        _bulkProgress.value = null
        lastFileUpdateElapsedMs = 0L
    }
}
