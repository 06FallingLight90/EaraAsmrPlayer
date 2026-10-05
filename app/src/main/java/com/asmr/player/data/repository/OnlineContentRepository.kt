package com.asmr.player.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import com.asmr.player.BuildConfig
import com.asmr.player.cache.AppCacheManager
import com.asmr.player.util.NetworkHeaders
import com.asmr.player.data.remote.api.AsmrOneAvailabilityApi
import com.asmr.player.data.remote.api.AsmrOneEndpoint
import com.asmr.player.data.remote.api.AsmrOneTrackNodeResponse
import com.asmr.player.data.remote.api.WorkDetailsResponse
import com.asmr.player.data.remote.auth.DlsiteAuthStore
import com.asmr.player.data.remote.auth.buildDlsiteCookieHeader
import com.asmr.player.data.remote.crawler.AsmrOneCrawler
import com.asmr.player.data.remote.crawler.AsmrOneSearchResult
import com.asmr.player.data.remote.crawler.AsmrOneTracksResult
import com.asmr.player.data.remote.crawler.asmrOneTracksCacheKey
import com.asmr.player.data.remote.crawler.fetchAsmrOneTracksFromBackup
import com.asmr.player.data.remote.crawler.selectAsmrOneWorkForRj
import com.asmr.player.data.remote.dlsite.DLSITE_PLAY_PREVIEW_CACHE_VERSION
import com.asmr.player.data.remote.dlsite.DlsiteCloudSyncResolveResult
import com.asmr.player.data.remote.dlsite.DlsiteProductInfoClient
import com.asmr.player.data.remote.dlsite.descrambleDlsitePlayBitmap
import com.asmr.player.data.remote.dlsite.parseDlsitePlayImageSeed
import com.asmr.player.data.remote.dlsite.resolveCloudSyncWorkId
import com.asmr.player.data.remote.dlsite.resolveDlsiteCloudSync
import com.asmr.player.data.remote.dlsite.resolveSelectedDlsiteCloudSync
import com.asmr.player.util.centerCropSquare
import com.asmr.player.util.isLikelyPlaceholderCover
import com.asmr.player.data.remote.scraper.DLSiteScraper
import com.asmr.player.data.remote.scraper.DlsiteRecommendations
import com.asmr.player.data.remote.scraper.DlsiteRecommendedWork
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.domain.model.TagSource
import com.asmr.player.data.remote.requestRemoteFileSize
import com.asmr.player.util.DlsiteWorkNo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * R2-C4b-3：从 AlbumDetailViewModel 下沉的在线内容编排。
 * 行为契约（原 VM 内联实现，逐行搬迁）：
 * - resolveAsmrOneWork：解析缓存 TTL（未命中 5s / 命中 10min），上限 500 条；
 *   throwOnRequestFailure=true 时同步带超时执行，否则在途去重 + withTimeoutOrNull；
 * - getAsmrOneTracksCached：按端点+workId 缓存非空目录树（10min，上限 200），空树不缓存；
 * - prepareDlsitePlayImagePreview：非 crypt 直通；crypt 路径下载 → 去扰 → 落盘
 *   cacheDir/DLSITE_PREVIEW_CACHE_DIR_NAME，命中已有文件直接复用；新文件经 onPreviewWritten 通知；
 * - loadRemoteFileSize：HEAD 失败回退 Range GET，内存缓存上限 512。
 */
@Singleton
class OnlineContentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("image") private val imageOkHttpClient: OkHttpClient,
    private val asmrOneCrawler: AsmrOneCrawler,
    private val asmrOneAvailabilityApi: AsmrOneAvailabilityApi,
    private val dlsiteScraper: DLSiteScraper,
    private val dlsiteProductInfoClient: DlsiteProductInfoClient,
    private val libraryReadRepository: LibraryReadRepository,
    private val libraryWriteRepository: LibraryWriteRepository,
    private val dlsiteAuthStore: DlsiteAuthStore,
) {
    // 原 VM 用 viewModelScope 承载在途去重；repo 内以独立 scope 承载。
    // 注意：原 VM 全部缓存访问在 Main.immediate 单线程串行；repo 后可能被多实例/多协程并发调用，
    // 故缓存容器改 ConcurrentHashMap，复合读写（TTL 判定+写入+淘汰、在途去重 get-or-put）用 cacheMutex 串行化。
    private val resolutionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cacheMutex = Mutex()

    private val asmrOneResolvedCache = ConcurrentHashMap<String, Pair<Long, Pair<String, Int?>?>>()
    private val asmrOneResolvedDetailsCache = ConcurrentHashMap<String, WorkDetailsResponse>()
    private val asmrOneResolutionInFlight = ConcurrentHashMap<String, Deferred<Pair<String, Int?>?>>()
    private val asmrOneTracksCache = ConcurrentHashMap<String, Pair<Long, AsmrOneTracksResult>>()
    private val remoteFileSizeCache = ConcurrentHashMap<String, Long?>()

    private suspend fun cacheAsmrOneResolution(
        key: String,
        result: AsmrOneSearchResult
    ): Pair<String, Int?>? = cacheMutex.withLock {
        val found = selectAsmrOneWorkForRj(result.response.works, key)
        val workId = found?.id?.toString()?.trim().orEmpty()
        if (found == null) {
            asmrOneResolvedDetailsCache.remove(key)
        } else {
            asmrOneResolvedDetailsCache[key] = found
        }
        val resolved = workId
            .takeIf { it.isNotBlank() }
            ?.let { it to result.trace.site }
        asmrOneResolvedCache[key] = SystemClock.elapsedRealtime() to resolved
        if (asmrOneResolvedCache.size > 500) {
            val firstKey = asmrOneResolvedCache.entries.firstOrNull()?.key
            if (firstKey != null) {
                asmrOneResolvedCache.remove(firstKey)
                asmrOneResolvedDetailsCache.remove(firstKey)
            }
        }
        resolved
    }

    private suspend fun resolveAsmrOneWorkUncached(
        key: String,
        throwOnRequestFailure: Boolean
    ): Pair<String, Int?>? {
        return cacheAsmrOneResolution(
            key = key,
            result = asmrOneCrawler.searchWithTrace(key, throwOnFailure = throwOnRequestFailure)
        )
    }

    suspend fun resolveAsmrOneWork(
        workNo: String,
        timeoutMs: Long = 12_000L,
        throwOnRequestFailure: Boolean = false
    ): Pair<String, Int?>? {
        val key = workNo.trim().uppercase()
        if (key.isBlank()) return null
        val now = SystemClock.elapsedRealtime()
        val cached = cacheMutex.withLock { asmrOneResolvedCache[key] }
        if (cached != null) {
            val ttlMs = if (cached.second == null) 5_000L else 10 * 60_000L
            if ((now - cached.first) <= ttlMs && (!throwOnRequestFailure || cached.second != null)) {
                return cached.second
            }
        }

        if (throwOnRequestFailure) {
            return withTimeout(timeoutMs) {
                resolveAsmrOneWorkUncached(key, throwOnRequestFailure = true)
            }
        }

        val existing = asmrOneResolutionInFlight[key]
        val request = if (existing != null) {
            existing
        } else {
            val deferred = resolutionScope.async {
                runCatching { resolveAsmrOneWorkUncached(key, throwOnRequestFailure = false) }.getOrNull()
            }
            val winner = asmrOneResolutionInFlight.putIfAbsent(key, deferred)
            if (winner != null) {
                deferred.cancel()
                winner
            } else {
                deferred.invokeOnCompletion {
                    asmrOneResolutionInFlight.remove(key, deferred)
                }
                deferred
            }
        }
        return withTimeoutOrNull(timeoutMs) { request.await() }
    }

    /** ensureAsmrOneLoaded 依赖：解析过程写入的详情缓存可按 RJ 读取。 */
    fun peekAsmrOneResolvedDetails(rj: String): WorkDetailsResponse? {
        return asmrOneResolvedDetailsCache[rj.trim().uppercase()]
    }

    /** invalidateAsmrOneEndpointState（端点切换）：清全部解析/曲目缓存并取消在途请求。 */
    fun invalidateAsmrOneCaches() {
        asmrOneResolvedCache.clear()
        asmrOneResolvedDetailsCache.clear()
        asmrOneResolutionInFlight.values.forEach { it.cancel() }
        asmrOneResolutionInFlight.clear()
        asmrOneTracksCache.clear()
    }

    /** cancelPendingOnlineJobs（页面退出/切专辑）：仅取消在途解析。 */
    fun cancelAsmrOneResolutionInFlight() {
        asmrOneResolutionInFlight.values.forEach { it.cancel() }
        asmrOneResolutionInFlight.clear()
    }

    /** refreshAsmrOneSection（手动刷新）：移除单条解析缓存与其旧曲目树缓存。 */
    fun forgetAsmrOneResolution(keyRj: String, site: Int?, workId: String?) {
        asmrOneResolvedCache.remove(keyRj)
        asmrOneResolvedDetailsCache.remove(keyRj)
        asmrOneResolutionInFlight.remove(keyRj)?.cancel()
        val oldWorkId = workId?.trim().orEmpty()
        if (oldWorkId.isNotBlank()) {
            asmrOneTracksCache.remove(asmrOneTracksCacheKey(site, oldWorkId))
        }
    }

    suspend fun getAsmrOneTracksCached(
        workId: String,
        throwOnRequestFailure: Boolean = false
    ): AsmrOneTracksResult {
        val normalizedId = workId.trim()
        if (normalizedId.isBlank()) return AsmrOneTracksResult(emptyList(), null)
        val selectedSite = asmrOneCrawler.selectedEndpoint()
        val cacheKey = asmrOneTracksCacheKey(selectedSite, normalizedId)
        val now = SystemClock.elapsedRealtime()
        val cached = asmrOneTracksCache[cacheKey]
        if (cached != null && (now - cached.first) <= 10 * 60_000L) return cached.second
        val result = if (throwOnRequestFailure) {
            asmrOneCrawler.getTracksWithTrace(normalizedId)
        } else {
            runCatching {
                asmrOneCrawler.getTracksWithTrace(normalizedId)
            }.getOrDefault(AsmrOneTracksResult(emptyList(), null))
        }
        if (result.tree.isNotEmpty()) {
            val resultCacheKey = asmrOneTracksCacheKey(result.site, normalizedId)
            asmrOneTracksCache[resultCacheKey] = now to result
            if (asmrOneTracksCache.size > 200) {
                val firstKey = asmrOneTracksCache.entries.firstOrNull()?.key
                if (firstKey != null) asmrOneTracksCache.remove(firstKey)
            }
        }
        return result
    }

    /** 备用端点按 RJ 直取目录树（原 VM fetchBackupAsmrOneTracksByRj）。 */
    suspend fun fetchBackupAsmrOneTracksByRj(
        rj: String,
        throwOnRequestFailure: Boolean = false
    ): Pair<String, List<AsmrOneTrackNodeResponse>>? {
        val normalizedRj = DlsiteWorkNo.normalizeWorkNo(rj, minimumDigits = 6)
        if (normalizedRj.isBlank()) return null
        val result = if (throwOnRequestFailure) {
            asmrOneAvailabilityApi.getTrackTreeByRj(normalizedRj)
        } else {
            runCatching { asmrOneAvailabilityApi.getTrackTreeByRj(normalizedRj) }.getOrNull()
                ?: return null
        }
        val tree = result.trackTree.orEmpty()
        if (tree.isEmpty()) return null
        val workId = result.workId.takeIf { it > 0 }?.toString().orEmpty()
        return workId to tree
    }

    /** 依次尝试候选 RJ 的备用端点目录树（原 VM 内对 fetchAsmrOneTracksFromBackup 的调用形态）。 */
    suspend fun fetchAsmrOneTracksFromBackupEndpoints(
        candidateRjs: List<String>,
        throwWhenAllRequestsFail: Boolean = false
    ): Pair<String?, List<AsmrOneTrackNodeResponse>> {
        return fetchAsmrOneTracksFromBackup(
            candidateRjs = candidateRjs,
            throwWhenAllRequestsFail = throwWhenAllRequestsFail,
            fetchBackup = { fetchBackupAsmrOneTracksByRj(it, throwOnRequestFailure = true) }
        )
    }

    suspend fun prepareDlsitePlayImagePreview(
        url: String,
        optimizedName: String?,
        crypt: Boolean,
        width: Int?,
        height: Int?,
        onPreviewWritten: (File) -> Unit = {}
    ): String? = withContext(Dispatchers.IO) {
        val normalizedUrl = url.trim()
        if (normalizedUrl.isBlank()) return@withContext null
        if (!crypt) return@withContext normalizedUrl
        val imageWidth = width ?: return@withContext null
        val imageHeight = height ?: return@withContext null
        if (imageWidth <= 0 || imageHeight <= 0) return@withContext null
        val name = optimizedName?.trim().orEmpty().ifBlank {
            normalizedUrl.substringBefore('?').substringAfterLast('/')
        }
        val seed = parseDlsitePlayImageSeed(name) ?: return@withContext null

        val previewDir = File(context.cacheDir, AppCacheManager.DLSITE_PREVIEW_CACHE_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }
        val previewKey = listOf(
            DLSITE_PLAY_PREVIEW_CACHE_VERSION.toString(),
            normalizedUrl,
            name,
            imageWidth.toString(),
            imageHeight.toString(),
            seed.toString()
        ).joinToString("|")
        val previewFile = File(previewDir, "${previewKey.hashCode()}_descrambled.png")
        if (previewFile.exists() && previewFile.length() > 0L) return@withContext previewFile.absolutePath

        val requestBuilder = Request.Builder()
            .url(normalizedUrl)
            .header("Accept", "image/*,*/*;q=0.8")
            .header("Referer", "https://play.dlsite.com/")
            .header("User-Agent", NetworkHeaders.USER_AGENT)
            .header("Accept-Language", NetworkHeaders.ACCEPT_LANGUAGE)
            .get()
        val cookie = buildDlsiteCookieHeader(dlsiteAuthStore.getPlayCookie())
        if (cookie.isNotBlank()) requestBuilder.header("Cookie", cookie)

        val bytes = runCatching {
            imageOkHttpClient.newCall(requestBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.bytes()
            }
        }.getOrNull() ?: return@withContext null
        val scrambled = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
        val descrambled = descrambleDlsitePlayBitmap(scrambled, seed, imageWidth, imageHeight)
        FileOutputStream(previewFile).use { out ->
            descrambled.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        if (descrambled !== scrambled && !descrambled.isRecycled) descrambled.recycle()
        if (!scrambled.isRecycled) scrambled.recycle()
        onPreviewWritten(previewFile)
        previewFile.absolutePath
    }

    suspend fun loadRemoteFileSize(url: String): Long? {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return null
        remoteFileSizeCache[trimmed]?.let { return it }

        val resolved = withContext(Dispatchers.IO) {
            requestRemoteFileSize(trimmed, imageOkHttpClient)
        }
        remoteFileSizeCache[trimmed] = resolved
        while (remoteFileSizeCache.size > 512) {
            val firstKey = remoteFileSizeCache.entries.firstOrNull()?.key ?: break
            remoteFileSizeCache.remove(firstKey)
        }
        return resolved
    }

    suspend fun enrichRecommendationsWithAsmrOne(recommendations: DlsiteRecommendations): DlsiteRecommendations {
        val candidates = (recommendations.circleWorks + recommendations.sameVoiceWorks + recommendations.alsoBoughtWorks)
            .asSequence()
            .map { DlsiteWorkNo.normalizeWorkNo(it.rjCode, minimumDigits = 6) }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_RECOMMENDATION_ASMR_ONE_ENRICH)
            .toList()
        if (candidates.isEmpty()) return recommendations

        val semaphore = Semaphore(RECOMMENDATION_ASMR_ONE_ENRICH_CONCURRENCY)
        val detailsByRj = coroutineScope {
            candidates.map { rj ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val asmr = runCatching { asmrOneCrawler.getDetails(rj) }.getOrNull()
                        rj to asmr
                    }
                }
            }.mapNotNull { deferred ->
                val (rj, details) = runCatching { deferred.await() }.getOrNull() ?: return@mapNotNull null
                details?.let { rj to it }
            }.toMap()
        }
        if (detailsByRj.isEmpty()) return recommendations

        fun enrich(list: List<DlsiteRecommendedWork>): List<DlsiteRecommendedWork> {
            return list.map { work ->
                val asmr = detailsByRj[work.rjCode.trim().uppercase()] ?: return@map work
                work.copy(
                    title = asmr.title.ifBlank { work.title },
                    coverUrl = asmr.mainCoverUrl.ifBlank { work.coverUrl }
                )
            }
        }
        return DlsiteRecommendations(
            circleWorks = enrich(recommendations.circleWorks),
            sameVoiceWorks = enrich(recommendations.sameVoiceWorks),
            alsoBoughtWorks = enrich(recommendations.alsoBoughtWorks)
        )
    }

    // ---------- DLsite 云同步（R2-C4b-3b 从 AlbumDetailViewModel 下沉，纯搬迁） ----------
    // DlsiteCloudSyncResolveResult 在 data.remote.dlsite 为 internal，本模块内使用故方法同为 internal。

    internal suspend fun resolveManualCloudSync(
        entity: AlbumEntity,
        baseWorkno: String
    ): DlsiteCloudSyncResolveResult {
        return resolveDlsiteCloudSync(
            keyword = entity.title.trim(),
            baseWorkno = baseWorkno,
            search = { searchKeyword, locale ->
                dlsiteScraper.search(searchKeyword, page = 1, order = "trend", locale = locale).items
            },
            fetchLanguageEditions = { productId ->
                dlsiteProductInfoClient.fetchLanguageEditions(productId)
            },
            fetchDetails = { workno, locale ->
                dlsiteScraper.getDetails(workno, locale = locale)
            }
        )
    }

    internal suspend fun resolveSelectedManualCloudSync(workno: String): DlsiteCloudSyncResolveResult {
        return resolveSelectedDlsiteCloudSync(
            workno = workno,
            fetchLanguageEditions = { productId ->
                dlsiteProductInfoClient.fetchLanguageEditions(productId)
            },
            fetchDetails = { selectedWorkno, locale ->
                dlsiteScraper.getDetails(selectedWorkno, locale = locale)
            }
        )
    }

    internal suspend fun applyManualCloudSyncSuccess(
        entity: AlbumEntity,
        updatedWorkId: String,
        result: DlsiteCloudSyncResolveResult.Success
    ): String {
        val resolvedWorkno = result.workno
        val details = result.details
        val oldTitle = entity.title.trim()
        val newTitle = details.title.trim()
        val finalTitle = when {
            oldTitle.isNotBlank() && newTitle.isBlank() -> oldTitle
            oldTitle.isNotBlank() && newTitle.isNotBlank() && oldTitle.contains(newTitle) && oldTitle.length > newTitle.length -> oldTitle
            else -> newTitle.ifBlank { oldTitle }
        }
        val updated = entity.copy(
            title = finalTitle,
            circle = details.circle.ifBlank { entity.circle },
            cv = details.cv.ifBlank { entity.cv },
            tags = if (details.tags.isNotEmpty()) details.tags.joinToString(",") else entity.tags,
            coverUrl = details.coverUrl.ifBlank { entity.coverUrl },
            description = details.description.ifBlank { entity.description },
            workId = resolveCloudSyncWorkId(updatedWorkId, resolvedWorkno),
            rjCode = resolvedWorkno
        )
        withContext(Dispatchers.IO) {
            libraryWriteRepository.updateAlbum(updated)
            libraryWriteRepository.upsertAlbumFtsIndex(updated.id, updated)
            libraryWriteRepository.upsertAlbumTagsFromCsv(updated.id, updated.tags, TagSource.AUTO)
            if (updated.coverPath.trim().isBlank() && updated.coverThumbPath.trim().isBlank()) {
                runCatching {
                    ensureAlbumCoverSaved(updated.id, updated.coverPath, updated.coverUrl)
                }
            }
        }
        return resolvedWorkno
    }

    // ---------- 专辑封面补全（R2-C4b-3b 从 AlbumDetailViewModel 下沉，纯搬迁） ----------

    suspend fun ensureAlbumCoverSaved(
        albumId: Long,
        coverPath: String,
        coverUrl: String
    ): Boolean {
        fun debugLog(msg: String) {
            if (BuildConfig.DEBUG) Log.d("OnlineContentRepository", msg)
        }
        fun fail(reason: String): Boolean {
            debugLog("ensureAlbumCoverSaved fail albumId=$albumId reason=$reason coverPath=${coverPath.take(160)} coverUrl=${coverUrl.take(160)}")
            return false
        }

        val existingPathRaw = coverPath.trim().takeIf { it.isNotBlank() && it != "null" }
        if (existingPathRaw != null && !existingPathRaw.startsWith("content://", ignoreCase = true)) {
            val f = if (existingPathRaw.startsWith("file://", ignoreCase = true)) {
                runCatching { File(android.net.Uri.parse(existingPathRaw).path.orEmpty()) }.getOrNull()
            } else {
                File(existingPathRaw)
            }
            if (f != null && f.exists() && f.length() > 0L) return true
        }

        val url = coverUrl.trim().takeIf { it.isNotBlank() && it != "null" }?.let { u ->
            if (u.startsWith("//")) "https:$u" else u
        }.orEmpty()
        val canUseNetwork = url.isNotBlank() && !isLikelyPlaceholderCover(url)

        val localCandidate = existingPathRaw
            ?: url.takeIf { it.startsWith("content://", ignoreCase = true) || it.startsWith("file://", ignoreCase = true) }
        val sourceKey = (if (canUseNetwork) url else localCandidate).orEmpty()
        if (sourceKey.isBlank()) return fail("empty_source")
        val sourceHash = sourceKey.hashCode().toString()
        val coverDir = File(context.filesDir, "album_covers").apply { if (!exists()) mkdirs() }
        val thumbDir = File(context.filesDir, "album_thumbs").apply { if (!exists()) mkdirs() }
        val coverFile = File(coverDir, "a_${albumId}_$sourceHash.jpg")
        val thumbFile = File(thumbDir, "a_${albumId}_${sourceHash}_v2.jpg")

        if (coverFile.exists() && coverFile.length() > 0L && thumbFile.exists() && thumbFile.length() > 0L) {
            val entity = try {
                libraryReadRepository.getAlbumById(albumId)
            } catch (_: Exception) {
                null
            }
            if (entity != null && (entity.coverPath != coverFile.absolutePath || entity.coverThumbPath != thumbFile.absolutePath)) {
                try {
                    libraryWriteRepository.updateAlbum(entity.copy(coverPath = coverFile.absolutePath, coverThumbPath = thumbFile.absolutePath))
                } catch (_: Exception) {
                }
            }
            return true
        }

        val bitmap = if (canUseNetwork) {
            val tmpFile = File(coverDir, "a_${albumId}_$sourceHash.tmp")
            try {
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
                while (maxDim / sample > 1280) sample *= 2 // 减小最大尺寸从 2048 到 1280
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.RGB_565 // 使用 RGB_565 减少一半内存占用
                }
                BitmapFactory.decodeFile(tmpFile.absolutePath, opts) ?: return fail("decode_failed_sample_$sample")
            } finally {
                runCatching { if (tmpFile.exists()) tmpFile.delete() }
            }
        } else {
            val p = localCandidate ?: return fail("empty_local_source")
            if (p.startsWith("file://", ignoreCase = true)) {
                val filePath = runCatching { android.net.Uri.parse(p).path.orEmpty() }.getOrNull().orEmpty()
                if (filePath.isBlank()) return fail("file_uri_no_path")
                val f = File(filePath)
                if (!f.exists() || f.length() <= 0L) return fail("file_not_found")
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.absolutePath, bounds)
                val w = bounds.outWidth
                val h = bounds.outHeight
                if (w <= 0 || h <= 0) return fail("file_decode_bounds_invalid")
                val maxDim = maxOf(w, h)
                var sample = 1
                while (maxDim / sample > 1280) sample *= 2
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                BitmapFactory.decodeFile(f.absolutePath, opts) ?: return fail("file_decode_failed_sample_$sample")
            } else {
                if (!p.startsWith("content://", ignoreCase = true)) return fail("unsupported_local_scheme")
                val uri = runCatching { android.net.Uri.parse(p) }.getOrNull() ?: return fail("content_uri_parse_failed")
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, bounds)
                } ?: return fail("content_open_failed")
                val w = bounds.outWidth
                val h = bounds.outHeight
                if (w <= 0 || h <= 0) return fail("content_decode_bounds_invalid")
                val maxDim = maxOf(w, h)
                var sample = 1
                while (maxDim / sample > 1280) sample *= 2
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, opts)
                } ?: return fail("content_decode_failed_sample_$sample")
            }
        }

        FileOutputStream(coverFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        val thumb = centerCropSquare(bitmap, 640)
        FileOutputStream(thumbFile).use { out ->
            thumb.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }

        return try {
            val entity = libraryReadRepository.getAlbumById(albumId) ?: return true
            libraryWriteRepository.updateAlbum(entity.copy(coverPath = coverFile.absolutePath, coverThumbPath = thumbFile.absolutePath))
            debugLog("ensureAlbumCoverSaved ok albumId=$albumId cover=${coverFile.length()} thumb=${thumbFile.length()}")
            true
        } catch (e: Exception) {
            fail("db_update_${e.javaClass.simpleName}")
        }
    }

    private companion object {
        const val MAX_RECOMMENDATION_ASMR_ONE_ENRICH = 12
        const val RECOMMENDATION_ASMR_ONE_ENRICH_CONCURRENCY = 4
    }
}
