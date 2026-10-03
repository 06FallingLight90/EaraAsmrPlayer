package com.asmr.player.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import com.asmr.player.cache.AppCacheManager
import com.asmr.player.data.remote.NetworkHeaders
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
import com.asmr.player.data.remote.dlsite.descrambleDlsitePlayBitmap
import com.asmr.player.data.remote.dlsite.parseDlsitePlayImageSeed
import com.asmr.player.data.remote.requestRemoteFileSize
import com.asmr.player.data.remote.scraper.DlsiteRecommendations
import com.asmr.player.data.remote.scraper.DlsiteRecommendedWork
import com.asmr.player.util.DlsiteWorkNo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
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
import kotlinx.coroutines.sync.Semaphore
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
) {
    // 原 VM 用 viewModelScope 承载在途去重；repo 内以独立 scope 等价承载。
    private val resolutionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val asmrOneResolvedCache = linkedMapOf<String, Pair<Long, Pair<String, Int?>?>>()
    private val asmrOneResolvedDetailsCache = linkedMapOf<String, WorkDetailsResponse>()
    private val asmrOneResolutionInFlight = mutableMapOf<String, Deferred<Pair<String, Int?>?>>()
    private val asmrOneTracksCache = linkedMapOf<String, Pair<Long, AsmrOneTracksResult>>()
    private val remoteFileSizeCache = linkedMapOf<String, Long?>()

    private fun cacheAsmrOneResolution(
        key: String,
        result: AsmrOneSearchResult
    ): Pair<String, Int?>? {
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
        return resolved
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
        val cached = asmrOneResolvedCache[key]
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

        val request = asmrOneResolutionInFlight[key] ?: resolutionScope.async {
            runCatching { resolveAsmrOneWorkUncached(key, throwOnRequestFailure = false) }.getOrNull()
        }.also { deferred ->
            asmrOneResolutionInFlight[key] = deferred
            deferred.invokeOnCompletion {
                asmrOneResolutionInFlight.remove(key, deferred)
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
        val cookie = buildDlsiteCookieHeader(DlsiteAuthStore(context).getPlayCookie())
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

    private companion object {
        const val MAX_RECOMMENDATION_ASMR_ONE_ENRICH = 12
        const val RECOMMENDATION_ASMR_ONE_ENRICH_CONCURRENCY = 4
    }
}
