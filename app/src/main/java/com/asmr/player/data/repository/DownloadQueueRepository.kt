package com.asmr.player.data.repository

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.asmr.player.data.local.db.entities.DownloadItemEntity
import com.asmr.player.data.local.db.entities.DownloadTaskEntity
import com.asmr.player.data.remote.download.DOWNLOAD_STATE_QUEUED
import com.asmr.player.data.remote.download.DownloadQueueCoordinator
import com.asmr.player.data.remote.download.FinalizeDownloadTaskWorker
import com.asmr.player.data.remote.download.downloadStagingFile
import com.asmr.player.data.remote.download.dlsitePlayImagePartFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * R2-C4a：从 DownloadsViewModel 下沉的下载队列编排（WorkManager 协调）。
 * 行为契约（原 VM 内联调用，逐行搬迁）：
 * - 四个取消/调度原语全部转发 DownloadQueueCoordinator（尽力而为，不抛错）；
 * - enqueueFinalizeDownloadTask 以 REPLACE 策略入队唯一名
 *   "download_finalize_<taskKey.hashCode()>" 的 FinalizeDownloadTaskWorker；
 * - stagingFile / dlsiteImagePartFile 为下载暂存与 DLSite 混淆图分片路径的纯路径计算。
 */
@Singleton
class DownloadQueueRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** 队列态条目状态常量（原 DOWNLOAD_STATE_QUEUED）。 */
    val queuedState: String get() = DOWNLOAD_STATE_QUEUED

    suspend fun cancelWorkAndUpdateState(workId: String, state: String) =
        DownloadQueueCoordinator.cancelWorkAndUpdateState(context, workId, state)

    fun cancelWork(workId: String) = DownloadQueueCoordinator.cancelWork(context, workId)

    fun cancelWorksByTag(tag: String) = DownloadQueueCoordinator.cancelWorksByTag(context, tag)

    fun requestSchedule() = DownloadQueueCoordinator.requestSchedule(context)

    fun stagingFile(item: DownloadItemEntity): File = downloadStagingFile(context, item)

    fun dlsiteImagePartFile(outputFile: File): File = dlsitePlayImagePartFile(outputFile)

    fun enqueueFinalizeDownloadTask(task: DownloadTaskEntity) {
        val finalizeInput = workDataOf(
            "taskKey" to task.taskKey,
            "taskTitle" to task.title,
            "taskSubtitle" to task.subtitle,
            "taskRootDir" to task.rootDir,
            "albumRootDir" to task.albumRootDir,
        )
        val request = OneTimeWorkRequestBuilder<FinalizeDownloadTaskWorker>()
            .setInputData(finalizeInput)
            .addTag("download_finalize")
            .addTag(task.taskKey)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "download_finalize_${task.taskKey.hashCode()}",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
