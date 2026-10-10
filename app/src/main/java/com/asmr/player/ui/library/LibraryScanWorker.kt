package com.asmr.player.ui.library

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.asmr.player.data.download.DownloadDestination
import com.asmr.player.util.BulkPhase
import com.asmr.player.util.BulkProgressStore
import com.asmr.player.util.MessageManager
import com.asmr.player.util.ScanRootsStore
import com.asmr.player.util.SyncCoordinator
import com.asmr.player.util.isScannableLocalDirectoryName
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/**
 * T11：本地库批量扫描下沉 CoroutineWorker（编排体自 [LibraryScanStateHolder] 三个批量入口
 * 纯搬移，逻辑未改；扫描逻辑本体在 [LibraryScanPipeline]）。
 * - 触发点：设置页手动（扫描全部/导入下载目录/刷新单根）+ LibraryViewModel init 自动扫描，
 *   统一经 [enqueue] 入队；设置页与 VM 调用面签名不变。
 * - 幂等：enqueueUniqueWork + [ExistingWorkPolicy.KEEP]——对齐原 scanMutex/syncCoordinator
 *   的"互斥不排队"语义：已有同名列任务（排队/运行中）时本次请求丢弃；Worker 运行时仍经
 *   [SyncCoordinator.tryBegin] 全局门（与云同步/删除族互斥），抢门失败提示与原实现同文案。
 * - 进度：经应用级 [BulkProgressStore]（VM 侧 LibraryTaskCoordinator 委托同一单例，
 *   bulkProgress StateFlow 观察面不变）；完成/失败/取消 toast 文案与原实现逐一对应。
 * - 原 VM 级 bulkStartMutex/scanMutex/bulkJob 编排随 Worker 化移除（互斥由 KEEP 去重 +
 *   全局门承担）；TAG 保持 "LibraryViewModel"：日志输出与搬移前逐字一致。
 * - 与管线同置 ui.library 包（不落 work 包）：ui→work 的 import 方向禁令排除了 ui 层
 *   直接入队 work 包 Worker（不新增 baseline 违规），且管线依赖的 data.repository/playback
 *   均在既有巨型连通团内，任何新包收管线都会撑大 SCC——同包零新增包级边。
 * - 依赖获取沿用 DownloadWorker 的 @EntryPoint 惯例（项目未引入 hilt-work）。
 */
class LibraryScanWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface LibraryScanWorkerEntryPoint {
        fun scanPipeline(): LibraryScanPipeline
        fun syncCoordinator(): SyncCoordinator
        fun messageManager(): MessageManager
        fun bulkProgressStore(): BulkProgressStore
    }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext, LibraryScanWorkerEntryPoint::class.java
        )
        return when (inputData.getInt(KEY_MODE, MODE_SCAN_ALL_ROOTS)) {
            MODE_SCAN_DOWNLOAD_IMPORT -> scanCurrentDownloadDestinationAsImport(entryPoint)
            MODE_SCAN_SINGLE_ROOT -> scanSingleRoot(entryPoint)
            else -> scanAllRoots(entryPoint)
        }
    }

    private suspend fun scanAllRoots(entryPoint: LibraryScanWorkerEntryPoint): Result {
        val pipeline = entryPoint.scanPipeline()
        val syncCoordinator = entryPoint.syncCoordinator()
        val messageManager = entryPoint.messageManager()
        val progress = entryPoint.bulkProgressStore()
        val token = syncCoordinator.tryBegin() ?: run {
            messageManager.showInfo("同步任务进行中，请等待完成或取消后再刷新本地")
            return Result.success()
        }
        try {
            try {
                withContext(Dispatchers.IO) {
                    currentCoroutineContext().ensureActive()
                    val roots = ScanRootsStore(applicationContext).getRoots().toList()
                    val downloadedAlbumCount = runCatching {
                        when (val destination = pipeline.currentDownloadDestination()) {
                            is DownloadDestination.Default -> File(destination.root).listFiles()
                                ?.count {
                                    it.isDirectory && isScannableLocalDirectoryName(it.name) &&
                                        File(it, ".download_complete").exists()
                                }
                                ?: 0
                            is DownloadDestination.DocumentTree -> {
                                val uri = Uri.parse(destination.root)
                                val treeId = DocumentsContract.getTreeDocumentId(uri)
                                pipeline.queryChildren(uri, treeId).count { child ->
                                    child.mimeType == DocumentsContract.Document.MIME_TYPE_DIR &&
                                        isScannableLocalDirectoryName(child.displayName) &&
                                        pipeline.queryChildren(uri, child.documentId).any { it.displayName == ".download_complete" }
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
                            totalAlbums += pipeline.queryChildren(uri, treeDocId).count {
                                it.mimeType == DocumentsContract.Document.MIME_TYPE_DIR &&
                                    isScannableLocalDirectoryName(it.displayName)
                            }
                        }
                    }
                    progress.startBulkProgress(phase = BulkPhase.ScanningLocal, total = totalAlbums)

                    var current = 0
                    roots.forEach { root ->
                        currentCoroutineContext().ensureActive()
                        pipeline.scanFromDocumentTree(root) { title ->
                            current += 1
                            progress.updateBulkAlbumProgress(current = current, currentAlbumTitle = title)
                        }
                    }
                    pipeline.scanFromDownloadedDir { title ->
                        current += 1
                        progress.updateBulkAlbumProgress(current = current, currentAlbumTitle = title)
                    }
                    pipeline.pruneOrphanedAlbumsByFilesystem()
                }
                messageManager.showSuccess("扫描完成")
            } catch (e: CancellationException) {
                messageManager.showInfo("已取消扫描")
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "scanAllRoots failed", e)
                messageManager.showError("扫描失败：${e.message}")
            } finally {
                progress.finishBulkProgress()
            }
        } finally {
            syncCoordinator.end(token)
        }
        return Result.success()
    }

    private suspend fun scanCurrentDownloadDestinationAsImport(entryPoint: LibraryScanWorkerEntryPoint): Result {
        val pipeline = entryPoint.scanPipeline()
        val syncCoordinator = entryPoint.syncCoordinator()
        val messageManager = entryPoint.messageManager()
        val progress = entryPoint.bulkProgressStore()
        val token = syncCoordinator.tryBegin() ?: run {
            messageManager.showInfo("同步任务进行中，请等待完成或取消后再扫描目标下载目录")
            return Result.success()
        }
        try {
            try {
                withContext(Dispatchers.IO) {
                    currentCoroutineContext().ensureActive()
                    val destination = pipeline.currentDownloadDestination()
                    val totalAlbums = when (destination) {
                        is DownloadDestination.Default -> File(destination.root).listFiles()
                            ?.count { it.isDirectory && isScannableLocalDirectoryName(it.name) }
                            ?: 0

                        is DownloadDestination.DocumentTree -> {
                            val uri = Uri.parse(destination.root)
                            val treeId = DocumentsContract.getTreeDocumentId(uri)
                            pipeline.queryChildren(uri, treeId).count {
                                it.mimeType == DocumentsContract.Document.MIME_TYPE_DIR &&
                                    isScannableLocalDirectoryName(it.displayName)
                            }
                        }
                    }
                    progress.startBulkProgress(phase = BulkPhase.ScanningLocal, total = totalAlbums)
                    var current = 0
                    pipeline.scanFromDownloadedDir(
                        onAlbumScanned = { title ->
                            current += 1
                            progress.updateBulkAlbumProgress(current = current, currentAlbumTitle = title)
                        },
                        importAll = true,
                    )
                }
                messageManager.showSuccess("目标下载目录扫描完成")
            } catch (error: CancellationException) {
                messageManager.showInfo("已取消目标目录扫描")
            } catch (error: Exception) {
                Log.e(TAG, "scanCurrentDownloadDestinationAsImport failed", error)
                messageManager.showError("目标目录扫描失败：${error.message}")
            } finally {
                progress.finishBulkProgress()
            }
        } finally {
            syncCoordinator.end(token)
        }
        return Result.success()
    }

    private suspend fun scanSingleRoot(entryPoint: LibraryScanWorkerEntryPoint): Result {
        val uriString = inputData.getString(KEY_ROOT_URI).orEmpty()
        if (uriString.isBlank()) return Result.success()
        val pipeline = entryPoint.scanPipeline()
        val syncCoordinator = entryPoint.syncCoordinator()
        val messageManager = entryPoint.messageManager()
        val progress = entryPoint.bulkProgressStore()
        val token = syncCoordinator.tryBegin() ?: run {
            messageManager.showInfo("同步任务进行中，请等待完成或取消后再刷新目录")
            return Result.success()
        }
        try {
            try {
                withContext(Dispatchers.IO) {
                    currentCoroutineContext().ensureActive()
                    val uri = runCatching { Uri.parse(uriString) }.getOrNull()
                    val treeDocId = uri?.let { runCatching { DocumentsContract.getTreeDocumentId(it) }.getOrNull() }
                    val totalAlbums = if (uri != null && !treeDocId.isNullOrBlank()) {
                        pipeline.queryChildren(uri, treeDocId).count {
                            it.mimeType == DocumentsContract.Document.MIME_TYPE_DIR &&
                                isScannableLocalDirectoryName(it.displayName)
                        }
                    } else {
                        0
                    }
                    progress.startBulkProgress(phase = BulkPhase.ScanningLocal, total = totalAlbums)
                    var current = 0
                    pipeline.scanFromDocumentTree(uriString) { title ->
                        current += 1
                        progress.updateBulkAlbumProgress(current = current, currentAlbumTitle = title)
                    }
                }
                messageManager.showSuccess("目录刷新完成")
            } catch (e: CancellationException) {
                messageManager.showInfo("已取消刷新")
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "scanSingleRoot failed", e)
                messageManager.showError("刷新失败：${e.message}")
            } finally {
                progress.finishBulkProgress()
            }
        } finally {
            syncCoordinator.end(token)
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "LibraryViewModel"
        const val UNIQUE_WORK_NAME = "library_bulk_scan"
        const val KEY_MODE = "scanMode"
        const val KEY_ROOT_URI = "scanRootUri"
        const val MODE_SCAN_ALL_ROOTS = 1
        const val MODE_SCAN_DOWNLOAD_IMPORT = 2
        const val MODE_SCAN_SINGLE_ROOT = 3

        /**
         * 批量扫描唯一入队（幂等）：KEEP——已存在同名列任务（排队/运行中）时本次请求被丢弃，
         * 对齐原 scanMutex 互斥不排队语义；触发侧的全局门预检负责给出 busy 提示。
         */
        fun enqueue(context: Context, mode: Int, rootUri: String?) {
            val request = OneTimeWorkRequestBuilder<LibraryScanWorker>()
                .setInputData(workDataOf(KEY_MODE to mode, KEY_ROOT_URI to rootUri.orEmpty()))
                .addTag(UNIQUE_WORK_NAME)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
