package com.asmr.player.util

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * T11：批量进度通道的应用级单例（自 LibraryTaskCoordinator 的进度半边原样拆出，逻辑未改）。
 * - 原状态在 VM 级 LibraryTaskCoordinator 内，扫描下沉 CoroutineWorker 后 Worker 无法触达
 *   VM 实例，故进度状态改挂应用级；VM 级协调器委托至此，Worker（同包）直接注入本单例。
 * - VM 中途重建时新实例经委托同样读到进行中的进度（原 VM 级状态做不到，属预期改善）。
 * - 字段更新口径（start 归零节流、currentFile 120ms 节流）与原实现逐一对应；
 *   时钟经 [nowMs] 注入（@Inject 零参副构造 = 生产 SystemClock.elapsedRealtime；
 *   测试传手控时钟以钉住 120ms 节流窗口），仅 SystemClock.elapsedRealtime() 调用点替换。
 */
@Singleton
class BulkProgressStore(private val nowMs: () -> Long) {
    @Inject constructor() : this(SystemClock::elapsedRealtime)

    private val _bulkProgress = MutableStateFlow<BulkProgress?>(null)
    val bulkProgress: StateFlow<BulkProgress?> = _bulkProgress.asStateFlow()
    private var lastFileUpdateElapsedMs: Long = 0L

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
        val now = nowMs()
        if (now - lastFileUpdateElapsedMs < 120L) return
        lastFileUpdateElapsedMs = now
        _bulkProgress.value = p.copy(currentFile = currentFile)
    }

    fun finishBulkProgress() {
        _bulkProgress.value = null
        lastFileUpdateElapsedMs = 0L
    }
}
