package com.asmr.player.ui.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.asmr.player.BuildConfig
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.remote.dlsite.DlsiteCloudSyncCandidate
import com.asmr.player.data.remote.dlsite.DlsiteCloudSyncResolveResult
import com.asmr.player.data.remote.dlsite.resolveCloudSyncWorkId
import com.asmr.player.data.repository.LibraryReadRepository
import com.asmr.player.data.repository.LibraryWriteRepository
import com.asmr.player.domain.model.TagSource
import com.asmr.player.util.BulkPhase
import com.asmr.player.util.MessageManager
import com.asmr.player.util.SyncCoordinator
import com.asmr.player.util.centerCropSquare
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Named
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * R3-C1d：LibraryViewModel 云同步族 State Holder（自 VM 逐字搬移，逻辑未改）。
 * - 承接全量/按根/单专辑云同步编排：syncMetadata / syncMetadataForRoot /
 *   syncAlbumMetadata / runBatchCloudSync / syncAlbumMetadataInternal 及其
 *   解析-应用-选择续跑链（resolveAlbumCloudSync / applyResolvedCloudSync / continueSync 等）。
 * - **applyResolvedCloudSync 的 title 覆盖语义原样随迁**（title = details.title
 *   空串回退 entity.title）——与 OnlineContentRepository.applyManualCloudSyncSuccess
 *   的 title 保留规则不同，两者不可混用（R2-C4b 取证记录）。
 * - **ensureAlbumCoverSaved 随云族逐字随迁**（VM 版：仅网络来源 / 2048 采样 /
 *   ARGB_8888 / 640 缩略图），受"双实现只记录不改动"用户决策保护（见
 *   docs/refactor-plan-r3.md §7），勿与 repo 版统一。
 * - 批量任务门/单专辑任务注册表/云同步选择队列经 [taskCoordinator]；
 *   同步全局门经 [syncCoordinator]；封面下载经 @Named("image") [imageOkHttpClient]。
 * - TAG 保持 "LibraryViewModel"：日志输出与抽取前逐字一致。
 */
internal class LibraryCloudSyncStateHolder(
    private val scope: CoroutineScope,
    private val context: Context,
    private val readRepository: LibraryReadRepository,
    private val writeRepository: LibraryWriteRepository,
    private val onlineContentRepository: com.asmr.player.data.repository.OnlineContentRepository,
    private val syncCoordinator: SyncCoordinator,
    private val taskCoordinator: LibraryTaskCoordinator,
    private val messageManager: MessageManager,
    @Named("image") private val imageOkHttpClient: OkHttpClient,
) {
    fun syncMetadata() {
        scope.launch {
            val token = syncCoordinator.tryBegin() ?: run {
                taskCoordinator.showSyncBusy("云同步")
                return@launch
            }
            try {
                taskCoordinator.bulkStartMutex.withLock {
                    taskCoordinator.bulkJob = currentCoroutineContext()[Job]
                    try {
                        val albums = withContext(Dispatchers.IO) { readRepository.getAllAlbumsOnce() }
                        runBatchCloudSync(albums)
                        messageManager.showSuccess("全量同步完成")
                    } catch (e: CancellationException) {
                        messageManager.showInfo("已取消云同步")
                    } catch (e: Exception) {
                        Log.e("LibraryViewModel", "syncMetadata failed", e)
                        messageManager.showError("云同步失败：${e.message}")
                    } finally {
                        taskCoordinator.finishBulkProgress()
                        if (taskCoordinator.bulkJob == currentCoroutineContext()[Job]) {
                            taskCoordinator.bulkJob = null
                        }
                    }
                }
            } finally {
                syncCoordinator.end(token)
            }
        }
    }

    fun syncMetadataForRoot(uriString: String) {
        if (uriString.isBlank()) return
        scope.launch {
            val token = syncCoordinator.tryBegin() ?: run {
                taskCoordinator.showSyncBusy("云同步")
                return@launch
            }
            try {
                taskCoordinator.bulkStartMutex.withLock {
                    taskCoordinator.bulkJob = currentCoroutineContext()[Job]
                    try {
                        val albums = withContext(Dispatchers.IO) {
                            readRepository.getAllAlbumsOnce()
                                .filter { entity ->
                                    entity.path.startsWith(uriString) || (entity.localPath?.startsWith(uriString) == true)
                                }
                        }
                        runBatchCloudSync(albums)
                        messageManager.showSuccess("云同步完成")
                    } catch (e: CancellationException) {
                        messageManager.showInfo("已取消云同步")
                    } catch (e: Exception) {
                        Log.e("LibraryViewModel", "syncMetadataForRoot failed", e)
                        messageManager.showError("云同步失败：${e.message}")
                    } finally {
                        taskCoordinator.finishBulkProgress()
                        if (taskCoordinator.bulkJob == currentCoroutineContext()[Job]) {
                            taskCoordinator.bulkJob = null
                        }
                    }
                }
            } finally {
                syncCoordinator.end(token)
            }
        }
    }

    fun syncAlbumMetadata(album: com.asmr.player.domain.model.Album) {
        if (!taskCoordinator.tryRegisterAlbumJob(album.id, "云同步")) return
        val job = scope.launch {
            val ownerJob = currentCoroutineContext()[Job]
            val token = syncCoordinator.tryBegin() ?: run {
                taskCoordinator.showSyncBusy("云同步")
                taskCoordinator.albumJobs.remove(album.id, ownerJob)
                return@launch
            }
            try {
                val entity = withContext(Dispatchers.IO) { readRepository.getAlbumById(album.id) } ?: return@launch
                withContext(Dispatchers.IO) { syncAlbumMetadataInternal(entity) }
            } catch (e: CancellationException) {
                messageManager.showInfo("已取消云同步")
            } finally {
                taskCoordinator.albumJobs.remove(album.id, ownerJob)
                syncCoordinator.end(token)
            }
        }
        taskCoordinator.albumJobs[album.id] = job
    }

    private suspend fun runBatchCloudSync(albums: List<AlbumEntity>) = coroutineScope {
        taskCoordinator.startBulkProgress(phase = BulkPhase.SyncingCloud, total = albums.size)
        taskCoordinator.cloudSyncSelectionQueue.beginBatchSession()
        try {
        val pendingSelections = mutableListOf<Deferred<Unit>>()
        var current = 0
        albums.forEach { entity ->
            currentCoroutineContext().ensureActive()
            current += 1
            taskCoordinator.updateBulkAlbumProgress(current = current, currentAlbumTitle = entity.title)
            withContext(Dispatchers.IO) {
                syncAlbumMetadataInternal(
                    entity = entity,
                    silent = true,
                    onAmbiguous = { pendingEntity, result ->
                        pendingSelections += this@coroutineScope.async(Dispatchers.IO) {
                            continueSyncAlbumMetadataAfterSelection(
                                entity = pendingEntity,
                                candidates = result.candidates,
                                silent = true
                            )
                        }
                    }
                )
            }
        }
        val pendingCount = taskCoordinator.cloudSyncSelectionQueue.pendingCount()
        if (pendingCount > 0) {
            messageManager.showInfo("主流程已完成，剩余${pendingCount}项待确认")
        }
        pendingSelections.awaitAll()
        } finally {
            taskCoordinator.cloudSyncSelectionQueue.endBatchSession()
        }
    }

    private suspend fun resolveAlbumCloudSync(entity: AlbumEntity): DlsiteCloudSyncResolveResult {
        return onlineContentRepository.resolveManualCloudSync(
            entity = entity,
            baseWorkno = entity.rjCode.ifBlank { entity.workId }.trim().uppercase()
        )
    }

    private suspend fun resolveSelectedAlbumCloudSync(workno: String): DlsiteCloudSyncResolveResult {
        return onlineContentRepository.resolveSelectedManualCloudSync(workno)
    }

    private suspend fun applyResolvedCloudSync(
        entity: AlbumEntity,
        result: DlsiteCloudSyncResolveResult.Success
    ) {
        val resolvedWorkno = result.workno
        val details = result.details
        val updated = entity.copy(
            title = details.title.ifBlank { entity.title },
            circle = details.circle.ifBlank { entity.circle },
            cv = details.cv.ifBlank { entity.cv },
            tags = if (details.tags.isNotEmpty()) details.tags.joinToString(",") else entity.tags,
            coverUrl = details.coverUrl.ifBlank { entity.coverUrl },
            description = details.description.ifBlank { entity.description },
            workId = resolveCloudSyncWorkId(entity.workId, resolvedWorkno),
            rjCode = resolvedWorkno
        )
        writeRepository.updateAlbum(updated)
        writeRepository.upsertAlbumFtsIndex(updated.id, updated)
        writeRepository.upsertAlbumTagsFromCsv(updated.id, updated.tags, TagSource.AUTO)
        if (updated.coverPath.trim().isBlank() && updated.coverThumbPath.trim().isBlank()) {
            runCatching {
                ensureAlbumCoverSaved(updated.id, updated.coverPath, updated.coverUrl)
            }
        }
    }

    private suspend fun continueSyncAlbumMetadataAfterSelection(
        entity: AlbumEntity,
        candidates: List<DlsiteCloudSyncCandidate>,
        silent: Boolean
    ) {
        try {
            val selectedWorkno = taskCoordinator.cloudSyncSelectionQueue.enqueue(
                albumId = entity.id.takeIf { it > 0L },
                albumTitle = entity.title,
                candidates = candidates
            ).await()
            currentCoroutineContext().ensureActive()
            if (selectedWorkno != null) {
                when (val selectedResult = resolveSelectedAlbumCloudSync(selectedWorkno)) {
                    is DlsiteCloudSyncResolveResult.Success -> {
                        applyResolvedCloudSync(entity, selectedResult)
                        if (!silent) {
                            messageManager.showSuccess("元数据同步成功")
                        }
                    }

                    is DlsiteCloudSyncResolveResult.Ambiguous -> {
                        if (!silent) {
                            messageManager.showError("同步失败：搜索结果不唯一")
                        }
                    }

                    DlsiteCloudSyncResolveResult.NotFound -> {
                        if (!silent) {
                            messageManager.showError("同步失败：未找到专辑信息")
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportSyncAlbumMetadataFailure(entity.id, e, silent)
            return
        } finally {
            taskCoordinator.syncStatus.value -= entity.id
        }
    }

    private suspend fun reportSyncAlbumMetadataFailure(entityId: Long, error: Exception, silent: Boolean) {
        Log.e("LibraryViewModel", "syncAlbumMetadataInternal failed: $entityId", error)
        taskCoordinator.syncStatus.value += (entityId to SyncStatus.Error(error.message ?: "同步失败"))
        if (!silent) {
            messageManager.showError("同步异常：${error.message}")
            delay(3000)
        }
        taskCoordinator.syncStatus.value -= entityId
    }

    private suspend fun syncAlbumMetadataInternal(
        entity: AlbumEntity,
        silent: Boolean = false,
        onAmbiguous: (suspend (AlbumEntity, DlsiteCloudSyncResolveResult.Ambiguous) -> Unit)? = null
    ) {
        val keyword = entity.title.trim()
        val currentWorkno = entity.rjCode.ifBlank { entity.workId }.trim().uppercase()
        if (currentWorkno.isBlank() && keyword.isBlank()) return

        taskCoordinator.syncStatus.value += (entity.id to SyncStatus.Syncing)
        var clearSyncStatus = true
        try {
            val result = resolveAlbumCloudSync(entity)
            currentCoroutineContext().ensureActive()
            when (result) {
                is DlsiteCloudSyncResolveResult.Success -> {
                    applyResolvedCloudSync(entity, result)
                    if (!silent) messageManager.showSuccess("元数据同步成功")
                }

                is DlsiteCloudSyncResolveResult.Ambiguous -> {
                    clearSyncStatus = false
                    if (onAmbiguous != null) {
                        onAmbiguous(entity, result)
                    } else {
                        continueSyncAlbumMetadataAfterSelection(entity, result.candidates, silent)
                    }
                    return
                }

                DlsiteCloudSyncResolveResult.NotFound -> {
                    if (!silent) messageManager.showError("同步失败：未找到专辑信息")
                }
            }
            if (clearSyncStatus) {
                taskCoordinator.syncStatus.value -= entity.id
            }
        } catch (e: CancellationException) {
            if (clearSyncStatus) {
                taskCoordinator.syncStatus.value -= entity.id
            }
            throw e
        } catch (e: Exception) {
            Log.e("LibraryViewModel", "syncAlbumMetadataInternal failed: ${entity.id}", e)
            taskCoordinator.syncStatus.value += (entity.id to SyncStatus.Error(e.message ?: "同步失败"))
            if (!silent) messageManager.showError("同步异常：${e.message}")
            if (!silent) delay(3000)
            taskCoordinator.syncStatus.value -= entity.id
        }
    }

    private suspend fun ensureAlbumCoverSaved(
        albumId: Long,
        coverPath: String,
        coverUrl: String
    ): Boolean {
        fun debugLog(msg: String) {
            if (BuildConfig.DEBUG) Log.d("LibraryViewModel", msg)
        }
        fun fail(reason: String): Boolean {
            debugLog("ensureAlbumCoverSaved fail albumId=$albumId reason=$reason coverPath=${coverPath.take(160)} coverUrl=${coverUrl.take(160)}")
            return false
        }

        val existingPathRaw = coverPath.trim().takeIf { it.isNotBlank() && it != "null" }
        if (existingPathRaw != null && !existingPathRaw.startsWith("content://", ignoreCase = true)) {
            val f = if (existingPathRaw.startsWith("file://", ignoreCase = true)) {
                runCatching { Uri.parse(existingPathRaw).path.orEmpty() }.getOrNull()?.let { File(it) }
            } else {
                File(existingPathRaw)
            }
            if (f != null && f.exists() && f.length() > 0L) return true
        }

        val url = coverUrl.trim().takeIf { it.isNotBlank() && it != "null" }?.let { u ->
            if (u.startsWith("//")) "https:$u" else u
        }.orEmpty()
        val canUseNetwork = url.isNotBlank() && !com.asmr.player.util.isLikelyPlaceholderCover(url)
        if (!canUseNetwork) return fail("no_network_cover")

        val sourceHash = url.hashCode().toString()
        val coverDir = File(context.filesDir, "album_covers").apply { if (!exists()) mkdirs() }
        val thumbDir = File(context.filesDir, "album_thumbs").apply { if (!exists()) mkdirs() }
        val coverFile = File(coverDir, "a_${albumId}_$sourceHash.jpg")
        val thumbFile = File(thumbDir, "a_${albumId}_${sourceHash}_v2.jpg")

        if (coverFile.exists() && coverFile.length() > 0L && thumbFile.exists() && thumbFile.length() > 0L) {
            val entity = runCatching { readRepository.getAlbumById(albumId) }.getOrNull()
            if (entity != null && (entity.coverPath != coverFile.absolutePath || entity.coverThumbPath != thumbFile.absolutePath)) {
                runCatching { writeRepository.updateAlbum(entity.copy(coverPath = coverFile.absolutePath, coverThumbPath = thumbFile.absolutePath)) }
            }
            return true
        }

        val tmpFile = File(coverDir, "a_${albumId}_$sourceHash.tmp")
        val bitmap = try {
            val req = Request.Builder()
                .url(url)
                .header("Accept", "image/*")
                .get()
                .build()
            imageOkHttpClient.newCall(req).execute().use { resp ->
                val contentType = resp.header("Content-Type").orEmpty()
                debugLog("ensureAlbumCoverSaved http albumId=$albumId code=${resp.code} type=$contentType url=${url.take(160)}")
                if (!resp.isSuccessful) return fail("http_${resp.code}")
                if (contentType.isNotBlank() && !contentType.startsWith("image/", ignoreCase = true)) return fail("not_image_$contentType")
                val body = resp.body ?: return fail("empty_body")
                body.byteStream().use { input ->
                    FileOutputStream(tmpFile).use { out ->
                        val buf = ByteArray(256 * 1024)
                        while (true) {
                            val read = input.read(buf)
                            if (read <= 0) break
                            out.write(buf, 0, read)
                        }
                        out.flush()
                    }
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tmpFile.absolutePath, bounds)
            val w = bounds.outWidth
            val h = bounds.outHeight
            if (w <= 0 || h <= 0) return fail("decode_bounds_invalid")
            val maxDim = maxOf(w, h)
            var sample = 1
            while (maxDim / sample > 2048) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(tmpFile.absolutePath, opts) ?: return fail("decode_failed_sample_$sample")
        } finally {
            runCatching { if (tmpFile.exists()) tmpFile.delete() }
        }

        FileOutputStream(coverFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        val thumb = centerCropSquare(bitmap, 640)
        FileOutputStream(thumbFile).use { out ->
            thumb.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }

        return runCatching {
            val entity = readRepository.getAlbumById(albumId) ?: return true
            writeRepository.updateAlbum(entity.copy(coverPath = coverFile.absolutePath, coverThumbPath = thumbFile.absolutePath))
            debugLog("ensureAlbumCoverSaved ok albumId=$albumId cover=${coverFile.length()} thumb=${thumbFile.length()}")
            true
        }.getOrElse { e ->
            fail("db_update_${e.javaClass.simpleName}")
        }
    }
}
