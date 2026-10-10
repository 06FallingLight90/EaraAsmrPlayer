package com.asmr.player.util

import android.os.SystemClock

/**
 * T11：批量任务进度模型（自 ui/library/LibraryViewModel.kt 原样随迁，逻辑未改）。
 * 迁至 util 使应用级进度通道（BulkProgressStore）与扫描 Worker（scan 包）都能引用，
 * 避免 work/scan → ui 的逆向依赖（ui→work 被 import 方向守护禁止，且会撑大包级 SCC）。
 */
enum class BulkPhase {
    ScanningLocal,
    SyncingCloud
}

data class BulkProgress(
    val phase: BulkPhase,
    val current: Int,
    val total: Int,
    val currentAlbumTitle: String = "",
    val currentFile: String = "",
    val startedAtElapsedMs: Long = SystemClock.elapsedRealtime()
) {
    val fraction: Float = if (total <= 0) 0f else (current.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}
