package com.asmr.player.ui.library

import com.asmr.player.util.BulkProgress
import com.asmr.player.util.BulkProgressStore
import com.asmr.player.util.BulkPhase
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
 *   单专辑同步状态（syncStatus）与云同步选择队列。
 * - 批量进度状态自 T11 起改挂应用级 [BulkProgressStore]（扫描已下沉 CoroutineWorker，
 *   Worker 无法触达 VM 实例）：本类保留原方法签名委托至此，VM 侧调用面（删除/云同步族）
 *   与 VM 观察面（bulkProgress/uiState）零改动。
 * - syncStatus/albumJobs/bulkJob/cloudSyncSelectionQueue 以可变状态直接暴露，
 *   供迁移中的调用族逐字保持原操作（value += / map.remove(key, owner) 等）。
 */
internal class LibraryTaskCoordinator(
    private val messageManager: MessageManager,
    private val bulkProgressStore: BulkProgressStore,
) {
    val bulkStartMutex = Mutex()
    var bulkJob: Job? = null
    val albumJobs = ConcurrentHashMap<Long, Job>()
    val syncStatus = MutableStateFlow<Map<Long, SyncStatus>>(emptyMap())
    val bulkProgress: StateFlow<BulkProgress?> = bulkProgressStore.bulkProgress
    val cloudSyncSelectionQueue = CloudSyncSelectionRequestQueue()

    val syncingAlbums: StateFlow<Map<Long, SyncStatus>> = syncStatus.asStateFlow()

    fun cancelBulkTask() {
        val job = bulkJob
        job?.cancel()
        bulkJob = null
        cloudSyncSelectionQueue.cancelAll()
        bulkProgressStore.finishBulkProgress()
    }

    /**
     * 批量任务进行中：VM 级 bulkJob 活跃，或应用级 bulkProgress 非空——
     * T11 扫描下沉 CoroutineWorker 后不再经 bulkJob，扫描运行期以 BulkProgressStore 持批量信号
     * （LibraryScanWorker start/finish 成对），消费方（deleteAlbum/tryRegisterAlbumJob 预检）据此拒绝。
     */
    fun isBulkTaskRunning(): Boolean {
        return bulkJob?.isActive == true || bulkProgress.value != null
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

    fun startBulkProgress(phase: BulkPhase, total: Int, current: Int = 0, currentAlbumTitle: String = "") =
        bulkProgressStore.startBulkProgress(phase, total, current, currentAlbumTitle)

    fun updateBulkAlbumProgress(current: Int, currentAlbumTitle: String) =
        bulkProgressStore.updateBulkAlbumProgress(current, currentAlbumTitle)

    fun maybeUpdateBulkCurrentFile(currentFile: String) =
        bulkProgressStore.maybeUpdateBulkCurrentFile(currentFile)

    fun finishBulkProgress() = bulkProgressStore.finishBulkProgress()
}
