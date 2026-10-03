package com.asmr.player.data.remote.download

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import androidx.work.*
import com.asmr.player.data.local.db.AppDatabaseProvider
import com.asmr.player.data.local.db.entities.DownloadTaskEntity
import com.asmr.player.data.remote.NetworkHeaders
import com.asmr.player.data.remote.auth.DlsiteAuthStore
import com.asmr.player.data.remote.auth.buildDlsiteCookieHeader
import com.asmr.player.data.remote.auth.mergeDlsiteCookieHeaders
import com.asmr.player.data.remote.dlsite.descrambleDlsitePlayImageFile
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import com.asmr.player.data.download.DownloadStorageGateway

class DownloadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DownloadWorkerEntryPoint {
        fun okHttpClient(): OkHttpClient
    }

    override suspend fun doWork(): ListenableWorker.Result = withContext(downloadDispatcher(applicationContext)) {
        executeDownloadWork()
    }

    private suspend fun executeDownloadWork(): ListenableWorker.Result {
        val url = inputData.getString("url") ?: return ListenableWorker.Result.failure()
        val fileName = inputData.getString("fileName") ?: return ListenableWorker.Result.failure()
        val targetDir = inputData.getString("targetDir") ?: return ListenableWorker.Result.failure()
        val taskKey = inputData.getString("taskKey").orEmpty()
        val taskTitle = inputData.getString("taskTitle").orEmpty()
        val taskSubtitle = inputData.getString("taskSubtitle").orEmpty()
        val taskRootDir = inputData.getString("taskRootDir").orEmpty()
        val albumRootDir = inputData.getString("albumRootDir").orEmpty()
        val relativePath = inputData.getString("relativePath").orEmpty().ifBlank { fileName }.replace('\\', '/')
        val albumTitle = inputData.getString("albumTitle").orEmpty()
        val albumCircle = inputData.getString("albumCircle").orEmpty()
        val albumCv = inputData.getString("albumCv").orEmpty()
        val albumTagsCsv = inputData.getString("albumTagsCsv").orEmpty()
        val albumCoverUrl = inputData.getString("albumCoverUrl").orEmpty()
        val albumDescription = inputData.getString("albumDescription").orEmpty()
        val albumWorkId = inputData.getString("albumWorkId").orEmpty()
        val albumRjCode = inputData.getString("albumRjCode").orEmpty()
        val dlsitePlayImageSeed = inputData.getInt("dlsitePlayImageSeed", -1).takeIf { it >= 0 }
        val dlsitePlayImageWidth = inputData.getInt("dlsitePlayImageWidth", -1).takeIf { it > 0 }
        val dlsitePlayImageHeight = inputData.getInt("dlsitePlayImageHeight", -1).takeIf { it > 0 }
        val hasDlsitePlayImageTransform = dlsitePlayImageSeed != null &&
            dlsitePlayImageWidth != null &&
            dlsitePlayImageHeight != null

        return try {
            val workId = id.toString()
            val appDb = AppDatabaseProvider.get(applicationContext)
            val dao = appDb.downloadDao()
            val dailyStatDao = appDb.dailyStatDao()
            val storage = DownloadStorageGateway(applicationContext)
            val now0 = System.currentTimeMillis()
            val resolvedTaskKey = taskKey.ifBlank { "dir:${taskRootDir.ifBlank { targetDir }}" }
            val resolvedRootDir = taskRootDir.ifBlank { targetDir }
            val resolvedTitle = taskTitle.ifBlank { resolvedTaskKey.removePrefix("album:").ifBlank { File(resolvedRootDir).name.ifBlank { "download" } } }
            val resolvedSubtitle = taskSubtitle.trim()
            val taskId = run {
                val existing = dao.getTaskByKey(resolvedTaskKey)
                if (existing != null) {
                    val mergedSubtitle = existing.subtitle.ifBlank { resolvedSubtitle }
                    val mergedAlbumTitle = existing.albumTitle.ifBlank { albumTitle }
                    val mergedAlbumCircle = existing.albumCircle.ifBlank { albumCircle }
                    val mergedAlbumCv = existing.albumCv.ifBlank { albumCv }
                    val mergedAlbumTagsCsv = existing.albumTagsCsv.ifBlank { albumTagsCsv }
                    val mergedAlbumCoverUrl = existing.albumCoverUrl.ifBlank { albumCoverUrl }
                    val mergedAlbumDescription = existing.albumDescription.ifBlank { albumDescription }
                    val mergedAlbumWorkId = existing.albumWorkId.ifBlank { albumWorkId }
                    val mergedAlbumRjCode = existing.albumRjCode.ifBlank { albumRjCode }
                    if (
                        mergedSubtitle != existing.subtitle ||
                        mergedAlbumTitle != existing.albumTitle ||
                        mergedAlbumCircle != existing.albumCircle ||
                        mergedAlbumCv != existing.albumCv ||
                        mergedAlbumTagsCsv != existing.albumTagsCsv ||
                        mergedAlbumCoverUrl != existing.albumCoverUrl ||
                        mergedAlbumDescription != existing.albumDescription ||
                        mergedAlbumWorkId != existing.albumWorkId ||
                        mergedAlbumRjCode != existing.albumRjCode
                    ) {
                        runCatching {
                            dao.updateTaskMetadata(
                                taskId = existing.id,
                                subtitle = mergedSubtitle,
                                albumTitle = mergedAlbumTitle,
                                albumCircle = mergedAlbumCircle,
                                albumCv = mergedAlbumCv,
                                albumTagsCsv = mergedAlbumTagsCsv,
                                albumCoverUrl = mergedAlbumCoverUrl,
                                albumDescription = mergedAlbumDescription,
                                albumWorkId = mergedAlbumWorkId,
                                albumRjCode = mergedAlbumRjCode,
                                updatedAt = now0
                            )
                        }
                    }
                    existing.id
                } else {
                    val inserted = dao.insertTask(
                        DownloadTaskEntity(
                            taskKey = resolvedTaskKey,
                            title = resolvedTitle,
                            subtitle = resolvedSubtitle,
                            rootDir = resolvedRootDir,
                            albumTitle = albumTitle,
                            albumCircle = albumCircle,
                            albumCv = albumCv,
                            albumTagsCsv = albumTagsCsv,
                            albumCoverUrl = albumCoverUrl,
                            albumDescription = albumDescription,
                            albumWorkId = albumWorkId,
                            albumRjCode = albumRjCode,
                            createdAt = now0,
                            updatedAt = now0
                        )
                    )
                    if (inserted > 0) inserted else (dao.getTaskByKey(resolvedTaskKey)?.id ?: 0L)
                }
            }

            val currentItem = dao.getItemByWorkId(workId)
                ?: return ListenableWorker.Result.failure()
            val usesDocumentTree = storage.isDocumentReference(targetDir)
            val targetFolder = if (usesDocumentTree) null else File(targetDir)
            val file = if (usesDocumentTree) {
                downloadStagingFile(applicationContext, currentItem)
            } else {
                File(checkNotNull(targetFolder), fileName)
            }
            val partialFile = dlsitePlayImagePartFile(file)
            val transferFile = if (hasDlsitePlayImageTransform) partialFile else file
            file.parentFile?.mkdirs()
            if (targetFolder != null && !targetFolder.exists()) targetFolder.mkdirs()
            runCatching {
                if (usesDocumentTree) {
                    storage.ensureFile(targetDir, ".nomedia", "application/octet-stream")
                } else {
                    val albumsRoot = File(applicationContext.getExternalFilesDir(null), "albums")
                    if (!albumsRoot.exists()) albumsRoot.mkdirs()
                    val rootMarker = File(albumsRoot, ".nomedia")
                    if (!rootMarker.exists()) rootMarker.createNewFile()
                    val marker = File(checkNotNull(targetFolder), ".nomedia")
                    if (!marker.exists()) marker.createNewFile()
                }
                Unit
            }

            val entryPoint = EntryPointAccessors.fromApplication(applicationContext, DownloadWorkerEntryPoint::class.java)
            val baseClient = entryPoint.okHttpClient()
            val requestBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", NetworkHeaders.USER_AGENT)
            
            val transformAlreadyApplied = hasDlsitePlayImageTransform && file.exists() && !partialFile.exists()
            var existingBytes = if (transferFile.exists()) transferFile.length().coerceAtLeast(0L) else 0L
            val lowerUrl = url.lowercase()
            val sessionCookieJar = SessionCookieJar()
            if (lowerUrl.contains("play.dlsite.com")) {
                val cookie = mergeDlsiteCookieHeaders(
                    buildDlsiteCookieHeader(DlsiteAuthStore(applicationContext).getDlsiteCookie()),
                    buildDlsiteCookieHeader(DlsiteAuthStore(applicationContext).getPlayCookie())
                )
                requestBuilder
                    .addHeader("Referer", "https://play.dlsite.com/library")
                    .addHeader("Accept-Language", NetworkHeaders.ACCEPT_LANGUAGE)
                if (cookie.isNotBlank()) {
                    requestBuilder.addHeader("Cookie", cookie)
                    sessionCookieJar.seedFromHeader(cookie, url)
                }
            } else if (lowerUrl.contains("dlsite")) {
                val cookie = mergeDlsiteCookieHeaders(
                    buildDlsiteCookieHeader(DlsiteAuthStore(applicationContext).getDlsiteCookie()),
                    buildDlsiteCookieHeader(DlsiteAuthStore(applicationContext).getPlayCookie())
                )
                requestBuilder
                    .addHeader("Referer", "https://play.dlsite.com/")
                    .addHeader("Accept-Language", NetworkHeaders.ACCEPT_LANGUAGE)
                if (cookie.isNotBlank()) {
                    requestBuilder.addHeader("Cookie", cookie)
                    sessionCookieJar.seedFromHeader(cookie, url)
                }
            }
            val client = if (lowerUrl.contains("dlsite")) {
                baseClient.newBuilder().cookieJar(sessionCookieJar).build()
            } else {
                baseClient
            }
            if (existingBytes > 0L) {
                requestBuilder.addHeader("Range", "bytes=$existingBytes-")
            }

            val knownTotal = dao.getItemByWorkId(workId)?.total?.takeIf { it > 0L } ?: -1L
            var total = knownTotal
            var downloaded = if (transformAlreadyApplied) knownTotal.coerceAtLeast(file.length()) else existingBytes
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now0))
            var pendingTrafficBytes = 0L

            suspend fun flushTrafficStats() {
                if (pendingTrafficBytes <= 0L) return
                dailyStatDao.addTraffic(today, pendingTrafficBytes)
                pendingTrafficBytes = 0L
            }

            suspend fun failCurrentDownload(
                downloadedBytes: Long = downloaded.coerceAtLeast(0L),
                totalBytes: Long = -1L
            ): ListenableWorker.Result {
                runCatching {
                    dao.updateItemProgress(
                        workId = workId,
                        state = WorkInfo.State.FAILED.name,
                        downloaded = downloadedBytes,
                        total = totalBytes,
                        speed = 0L,
                        updatedAt = System.currentTimeMillis()
                    )
                }
                return ListenableWorker.Result.failure(
                    workDataOf(
                        "fileName" to fileName,
                        "targetDir" to targetDir,
                        "filePath" to File(targetDir, fileName).absolutePath,
                        "relativePath" to relativePath,
                        "taskKey" to resolvedTaskKey
                    )
                )
            }

            val transferAlreadyComplete = transformAlreadyApplied ||
                (hasDlsitePlayImageTransform && knownTotal > 0L && existingBytes >= knownTotal)
            if (!transferAlreadyComplete) client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "download failed code=${response.code} url=${response.request.url}")
                    return failCurrentDownload(totalBytes = existingBytes.coerceAtLeast(0L))
                }
                val body = response.body ?: return failCurrentDownload(totalBytes = existingBytes.coerceAtLeast(0L))
                val supportsRange = response.code == 206 && existingBytes > 0L
                if (!supportsRange && existingBytes > 0L) {
                    existingBytes = 0L
                    downloaded = 0L
                }
                val contentLen = body.contentLength().takeIf { it > 0 } ?: -1L
                total = when {
                    supportsRange && contentLen > 0 -> existingBytes + contentLen
                    contentLen > 0 -> contentLen
                    else -> -1L
                }
                val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                @Suppress("UNUSED_VARIABLE") var progressBaselineBytes = 0L
                @Suppress("UNUSED_VARIABLE") var progressBaselineTs = System.currentTimeMillis()

                dao.upsertItem(
                    currentItem.copy(
                        taskId = taskId,
                        workId = workId,
                        targetDir = targetDir,
                        state = WorkInfo.State.RUNNING.name,
                        downloaded = downloaded,
                        total = total,
                        speed = 0L,
                        updatedAt = now0,
                    )
                )

                body.byteStream().use { input ->
                    FileOutputStream(transferFile, existingBytes > 0L).use { output ->
                        while (true) {
                            if (isStopped) {
                                val now = System.currentTimeMillis()
                                flushTrafficStats()
                                dao.updateItemState(workId, "PAUSED", now)
                                DownloadQueueCoordinator.requestSchedule(applicationContext)
                                return ListenableWorker.Result.success(
                                    workDataOf(
                                        "fileName" to fileName,
                                        "targetDir" to targetDir,
                                        "filePath" to file.absolutePath,
                                        "relativePath" to relativePath,
                                        "taskKey" to resolvedTaskKey
                                    )
                                )
                            }
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            pendingTrafficBytes += read.toLong()

                            val now = System.currentTimeMillis()
                            val shouldUpdate = now - progressBaselineTs >= PROGRESS_UPDATE_INTERVAL_MS
                            if (shouldUpdate) {
                                flushTrafficStats()
                                val dt = (now - progressBaselineTs).coerceAtLeast(1)
                                val speed = ((downloaded - progressBaselineBytes) * 1000 / dt).coerceAtLeast(0)
                                dao.updateItemProgress(
                                    workId = workId,
                                    state = WorkInfo.State.RUNNING.name,
                                    downloaded = downloaded,
                                    total = total,
                                    speed = speed,
                                    updatedAt = now
                                )
                                progressBaselineBytes = downloaded
                                progressBaselineTs = now
                            }
                        }
                    }
                }
            }
            if (hasDlsitePlayImageTransform && !transformAlreadyApplied) {
                descrambleDlsitePlayImageFile(
                    scrambledFile = partialFile,
                    outputFile = file,
                    seed = checkNotNull(dlsitePlayImageSeed),
                    width = checkNotNull(dlsitePlayImageWidth),
                    height = checkNotNull(dlsitePlayImageHeight)
                )
                if (partialFile.exists() && !partialFile.delete()) {
                    throw IOException("Unable to remove scrambled DLsite Play image")
                }
            }
            val publishedReference = if (usesDocumentTree) {
                val destinationReference = storage.ensureFile(
                    directory = targetDir,
                    name = fileName,
                    mimeType = downloadMimeType(fileName),
                )
                storage.openOutput(destinationReference).use { output ->
                    file.inputStream().use { input -> input.copyTo(output) }
                }
                dao.updateItemDestination(
                    workId = workId,
                    filePath = destinationReference,
                    targetDir = targetDir,
                    updatedAt = System.currentTimeMillis(),
                )
                if (!fileName.equals("dlsite_lossless_archive.zip", ignoreCase = true)) {
                    file.delete()
                }
                destinationReference
            } else {
                file.absolutePath
            }
            val now = System.currentTimeMillis()
            try {
                flushTrafficStats()
            } catch (e: Exception) {
                Log.w(TAG, "flushTrafficStats failed", e)
            }
            val finalTotal = if (total > 0) total else downloaded
            dao.updateItemProgress(
                workId = workId,
                state = WorkInfo.State.SUCCEEDED.name,
                downloaded = downloaded,
                total = finalTotal,
                speed = 0L,
                updatedAt = now
            )
            runCatching {
                val finalizeInput = workDataOf(
                    "taskKey" to resolvedTaskKey,
                    "taskTitle" to resolvedTitle,
                    "taskSubtitle" to resolvedSubtitle,
                    "taskRootDir" to resolvedRootDir,
                    "albumRootDir" to albumRootDir,
                    "albumTitle" to albumTitle,
                    "albumCircle" to albumCircle,
                    "albumCv" to albumCv,
                    "albumTagsCsv" to albumTagsCsv,
                    "albumCoverUrl" to albumCoverUrl,
                    "albumDescription" to albumDescription,
                    "albumWorkId" to albumWorkId,
                    "albumRjCode" to albumRjCode
                )
                val request = OneTimeWorkRequestBuilder<FinalizeDownloadTaskWorker>()
                    .setInputData(finalizeInput)
                    .addTag("download_finalize")
                    .addTag(resolvedTaskKey)
                    .build()
                val unique = "download_finalize_${resolvedTaskKey.hashCode()}"
                WorkManager.getInstance(applicationContext)
                    .enqueueUniqueWork(unique, ExistingWorkPolicy.REPLACE, request)
            }
            DownloadQueueCoordinator.requestSchedule(applicationContext)
            ListenableWorker.Result.success(
                workDataOf(
                    "fileName" to fileName,
                    "targetDir" to targetDir,
                    "filePath" to publishedReference,
                    "relativePath" to relativePath,
                    "taskKey" to resolvedTaskKey
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "download work failed file=$fileName dir=$targetDir attempt=$runAttemptCount", e)
            // 弱网自动重试（20261001 体检 P2-1）：IO 类异常且未超上限时走 WorkManager 指数退避，
            // 不改 DB 失败态；上限后按原路径落失败
            if (e is java.io.IOException && runAttemptCount < MAX_AUTO_RETRY_ATTEMPTS) {
                DownloadQueueCoordinator.requestSchedule(applicationContext)
                return ListenableWorker.Result.retry()
            }
            val now = System.currentTimeMillis()
            runCatching {
                val workId = id.toString()
                val dao = AppDatabaseProvider.get(applicationContext).downloadDao()
                dao.updateItemState(workId, WorkInfo.State.FAILED.name, now)
            }
            DownloadQueueCoordinator.requestSchedule(applicationContext)
            ListenableWorker.Result.failure(
                workDataOf(
                    "fileName" to fileName,
                    "targetDir" to targetDir,
                    "filePath" to if (targetDir.startsWith("content://")) "" else File(targetDir, fileName).absolutePath,
                    "relativePath" to relativePath,
                    "taskKey" to taskKey
                )
            )
        }
    }

    companion object {
        private const val TAG = "DownloadWorker"

        /** IO 类失败的最大自动重试次数（总尝试 = 1 + 该值）；退避沿用 WorkManager 默认指数策略。 */
        private const val MAX_AUTO_RETRY_ATTEMPTS = 2
        private const val DOWNLOAD_BUFFER_SIZE = 64 * 1024
        private const val PROGRESS_UPDATE_INTERVAL_MS = 1_000L

        @Volatile
        private var lowRamDispatcher: ExecutorCoroutineDispatcher? = null

        @Volatile
        private var defaultDispatcher: ExecutorCoroutineDispatcher? = null

        private val dispatcherLock = Any()

        private fun downloadDispatcher(context: Context): CoroutineDispatcher {
            val lowRamDevice = DownloadRuntimeConfig.isLowRamDevice(context)
            val existing = if (lowRamDevice) lowRamDispatcher else defaultDispatcher
            if (existing != null) return existing

            return synchronized(dispatcherLock) {
                val cached = if (lowRamDevice) lowRamDispatcher else defaultDispatcher
                if (cached != null) {
                    cached
                } else {
                    // Each file maps to its own worker, so we cap download execution here to
                    // avoid dozens of concurrent network streams overwhelming low-memory phones.
                    val created = Executors
                        .newFixedThreadPool(DownloadRuntimeConfig.maxConcurrentDownloads(context))
                        .asCoroutineDispatcher()
                    if (lowRamDevice) {
                        lowRamDispatcher = created
                    } else {
                        defaultDispatcher = created
                    }
                    created
                }
            }
        }
    }
}

class FinalizeDownloadTaskWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): ListenableWorker.Result {
        val taskKey = inputData.getString("taskKey").orEmpty()
        val taskTitle = inputData.getString("taskTitle").orEmpty()
        val taskSubtitle = inputData.getString("taskSubtitle").orEmpty()
        val taskRootDir = inputData.getString("taskRootDir").orEmpty()
        val albumRootDir = inputData.getString("albumRootDir").orEmpty()
        val albumTitle = inputData.getString("albumTitle").orEmpty()
        val albumCircle = inputData.getString("albumCircle").orEmpty()
        val albumCv = inputData.getString("albumCv").orEmpty()
        val albumTagsCsv = inputData.getString("albumTagsCsv").orEmpty()
        val albumCoverUrl = inputData.getString("albumCoverUrl").orEmpty()
        val albumDescription = inputData.getString("albumDescription").orEmpty()
        val albumWorkId = inputData.getString("albumWorkId").orEmpty()
        val albumRjCode = inputData.getString("albumRjCode").orEmpty()

        if (taskKey.isBlank() && taskRootDir.isBlank()) return ListenableWorker.Result.success()

        return try {
            val db = AppDatabaseProvider.get(applicationContext)
            db.withTransaction {
                val dao = db.downloadDao()
                val task = if (taskKey.isNotBlank()) {
                    dao.getTaskByKey(taskKey)
                } else {
                    dao.getTaskByRootDir(taskRootDir)
                } ?: return@withTransaction

                val items = dao.getItemsForTask(task.id)
                val done = items.isNotEmpty() && items.all { it.state == WorkInfo.State.SUCCEEDED.name }
                if (!done) return@withTransaction

                val storage = DownloadStorageGateway(applicationContext)
                val resolvedAlbumRoot = task.albumRootDir
                    .ifBlank { albumRootDir }
                    .ifBlank { task.rootDir.ifBlank { taskRootDir } }
                if (storage.isDocumentReference(resolvedAlbumRoot)) {
                    runCatching {
                        finalizeDlsiteLosslessArchiveInStorageIfNeeded(
                            context = applicationContext,
                            rootDir = resolvedAlbumRoot,
                            items = items,
                            storage = storage,
                        )
                    }
                } else {
                    runCatching { finalizeDlsiteLosslessArchiveIfNeeded(File(resolvedAlbumRoot), items) }
                }

                upsertDownloadedAlbumToLibrary(
                    db = db,
                    appContext = applicationContext,
                    rootDir = resolvedAlbumRoot,
                    taskTitle = task.title.ifBlank { taskTitle },
                    taskSubtitle = task.subtitle.ifBlank { taskSubtitle },
                    albumTitle = albumTitle,
                    albumCircle = albumCircle,
                    albumCv = albumCv,
                    albumTagsCsv = albumTagsCsv,
                    albumCoverUrl = albumCoverUrl,
                    albumDescription = albumDescription,
                    albumWorkId = albumWorkId,
                    albumRjCode = albumRjCode
                )

                runCatching {
                    if (storage.isDocumentReference(resolvedAlbumRoot)) {
                        storage.ensureFile(resolvedAlbumRoot, ".download_complete", "application/octet-stream")
                    } else {
                        val marker = File(resolvedAlbumRoot, ".download_complete")
                        if (!marker.exists()) marker.createNewFile()
                    }
                    Unit
                }
            }
            ListenableWorker.Result.success()
        } catch (e: Exception) {
            Log.w("FinalizeDownloadTaskWorker", "finalize download task failed; reporting success", e)
            ListenableWorker.Result.success()
        }
    }
}


private class SessionCookieJar : CookieJar {
    private val store = LinkedHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val key = url.host.lowercase()
        val list = store.getOrPut(key) { mutableListOf() }
        cookies.forEach { cookie ->
            list.removeAll { it.name == cookie.name && it.path == cookie.path && it.domain == cookie.domain }
            list.add(cookie)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host.lowercase()
        val path = url.encodedPath
        val now = System.currentTimeMillis()
        val out = mutableListOf<Cookie>()
        store.values.forEach { cookies ->
            cookies.removeAll { it.expiresAt < now }
            cookies.forEach { cookie ->
                val domainMatches = if (cookie.hostOnly) {
                    host == cookie.domain.lowercase()
                } else {
                    host == cookie.domain.lowercase() || host.endsWith(".${cookie.domain.lowercase()}")
                }
                val pathMatches = path.startsWith(cookie.path)
                if (domainMatches && pathMatches) out += cookie
            }
        }
        return out
    }

    fun seedFromHeader(header: String, url: String) {
        val httpUrl = runCatching { url.toHttpUrl() }.getOrNull() ?: return
        header.split(';')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { item ->
                val idx = item.indexOf('=')
                if (idx <= 0) return@forEach
                val name = item.substring(0, idx).trim()
                val value = item.substring(idx + 1).trim()
                if (name.isBlank()) return@forEach
                val cookie = Cookie.Builder()
                    .name(name)
                    .value(value)
                    .domain(httpUrl.host)
                    .path("/")
                    .build()
                saveFromResponse(httpUrl, listOf(cookie))
            }
    }
}
