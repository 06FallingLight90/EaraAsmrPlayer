package com.asmr.player.data.repository

import com.asmr.player.domain.model.Album
import com.asmr.player.data.remote.api.AsmrOneAvailabilityApi
import com.asmr.player.data.remote.api.AsmrOneCollectedSearchItem
import com.asmr.player.data.remote.api.WorkDetailsResponse
import com.asmr.player.data.remote.crawler.AsmrOneCrawler
import com.asmr.player.data.remote.dlsite.DlsitePlayLibraryClient
import com.asmr.player.data.remote.scraper.DLSiteScraper
import com.asmr.player.util.DlsiteWorkNo
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CancellationException

/**
 * R2-C4a：从 SearchViewModel 下沉的搜索侧 remote 编排。
 * 行为契约（原 SearchViewModel 内联实现，逐行搬迁）：
 * - getWorkInfoWithLocaleFallback：优先 preferred locale，失败依次回退
 *   preferred → zh_CN → ja_JP → 默认；preferred 为空时 zh_CN → ja_JP → 默认；
 * - searchCollected：DTO → Album 映射在 Dispatchers.Default 上执行；
 * - resolveCollectedWorkNo：超时内抓取 asmr.one 详情并解析 workNo，
 *   失败/超时返回空串，取消异常原样保留。
 */
@Singleton
class SearchRepository @Inject constructor(
    private val dlsiteScraper: DLSiteScraper,
    private val dlsitePlayLibraryClient: DlsitePlayLibraryClient,
    private val asmrOneAvailabilityApi: AsmrOneAvailabilityApi,
    private val asmrOneCrawler: AsmrOneCrawler,
) {
    data class PurchasedPage(
        val items: List<Album>,
        val canGoNext: Boolean,
    )

    data class CollectedPage(
        val items: List<Album>,
        val total: Int,
        val offset: Int,
    )

    data class DlsitePage(
        val items: List<Album>,
        val canGoNext: Boolean,
    )

    fun hasDlsiteStoredCredentials(): Boolean = dlsitePlayLibraryClient.hasStoredCredentials()

    suspend fun searchPurchased(keyword: String, page: Int, pageSize: Int): PurchasedPage {
        val resp = dlsitePlayLibraryClient.searchPurchased(keyword, page, pageSize)
        return PurchasedPage(items = resp.items, canGoNext = resp.canGoNext)
    }

    suspend fun searchCollected(
        keyword: String,
        limit: Int,
        offset: Int,
        sort: String,
        hasSubtitle: Boolean,
        allAges: Boolean,
    ): CollectedPage {
        val resp = asmrOneAvailabilityApi.search(
            keyword = keyword,
            limit = limit,
            offset = offset,
            sort = sort,
            hasSubtitle = hasSubtitle,
            allAges = allAges
        )
        val collectedItems = resp.items.orEmpty()
        val mappedItems = withContext(Dispatchers.Default) {
            collectedItems.map { it.toCollectedAlbum() }
        }
        return CollectedPage(items = mappedItems, total = resp.total, offset = resp.offset)
    }

    suspend fun checkAvailability(rjs: List<String>): Map<String, Boolean> =
        asmrOneAvailabilityApi.check(rjs)

    suspend fun getWorkInfoWithLocaleFallback(workNo: String, preferredLocale: String?): Album? {
        val info = when {
            !preferredLocale.isNullOrBlank() -> {
                runCatching { dlsiteScraper.getWorkInfo(workNo, locale = preferredLocale) }.getOrNull()
                    ?: runCatching { dlsiteScraper.getWorkInfo(workNo, locale = "zh_CN") }.getOrNull()
                    ?: runCatching { dlsiteScraper.getWorkInfo(workNo, locale = "ja_JP") }.getOrNull()
                    ?: runCatching { dlsiteScraper.getWorkInfo(workNo) }.getOrNull()
            }

            else -> {
                runCatching { dlsiteScraper.getWorkInfo(workNo, locale = "zh_CN") }.getOrNull()
                    ?: runCatching { dlsiteScraper.getWorkInfo(workNo, locale = "ja_JP") }.getOrNull()
                    ?: runCatching { dlsiteScraper.getWorkInfo(workNo) }.getOrNull()
            }
        }
        return info?.album
    }

    suspend fun getWorkInfoAlbum(
        workId: String,
        locale: String,
        allowJapaneseCvFallback: Boolean,
    ): Album? = dlsiteScraper.getWorkInfo(
        workId = workId,
        locale = locale,
        allowJapaneseCvFallback = allowJapaneseCvFallback
    )?.album

    suspend fun searchDlsite(
        keyword: String,
        page: Int,
        order: String,
        locale: String?,
        presaleOnly: Boolean,
        chineseTranslatedOnly: Boolean,
        hasSubtitle: Boolean,
        allAges: Boolean,
    ): DlsitePage {
        val result = dlsiteScraper.search(
            keyword = keyword,
            page = page,
            order = order,
            locale = locale,
            presaleOnly = presaleOnly,
            chineseTranslatedOnly = chineseTranslatedOnly,
            hasSubtitle = hasSubtitle,
            allAges = allAges
        )
        return DlsitePage(items = result.items, canGoNext = result.canGoNext)
    }

    suspend fun resolveCollectedWorkNo(workId: Int, timeoutMs: Long): String {
        return resolveOptionalCollectedWorkNo(
            timeoutMs = timeoutMs,
            fetchDetails = { asmrOneCrawler.getDetailsFromMain(workId.toString()) }
        )
    }
}

internal fun AsmrOneCollectedSearchItem.resolvedWorkNo(fallbackWorkNo: String = ""): String {
    return buildList {
        add(rj)
        add(sourceId)
        add(originalWorkno)
        add(fallbackWorkNo)
        addAll(matchedRjs.orEmpty())
    }
        .asSequence()
        .map { DlsiteWorkNo.normalizeWorkNo(it, minimumDigits = 6) }
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
}

internal fun WorkDetailsResponse.resolvedWorkNo(): String {
    return buildList {
        add(source_id)
        add(original_workno.orEmpty())
        language_editions.orEmpty().forEach { edition ->
            add(edition.workno.orEmpty())
        }
    }
        .asSequence()
        .map { DlsiteWorkNo.normalizeWorkNo(it, minimumDigits = 6) }
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
}

internal fun AsmrOneCollectedSearchItem.toCollectedAlbum(fallbackWorkNo: String = ""): Album {
    val workNo = resolvedWorkNo(fallbackWorkNo)
    return Album(
        title = title.trim().ifBlank { workNo.ifBlank { "已收录作品" } },
        path = "",
        workId = workNo,
        rjCode = workNo,
        circle = circle.trim(),
        cv = cvs.orEmpty()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", "),
        tags = tags.orEmpty()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct(),
        coverUrl = mainCoverUrl.trim(),
        releaseDate = releaseDate.trim(),
        ratingValue = rateAverage2dp?.takeIf { it > 0.0 },
        ratingCount = (rateCount ?: reviewCount ?: 0).coerceAtLeast(0),
        dlCount = 0,
        priceJpy = price ?: 0,
        hasAsmrOne = true,
        asmrOneWorkId = workId.takeIf { it > 0 }
    )
}

internal suspend fun resolveOptionalCollectedWorkNo(
    timeoutMs: Long,
    fetchDetails: suspend () -> WorkDetailsResponse
): String {
    return try {
        withTimeoutOrNull(timeoutMs.coerceAtLeast(1L)) {
            fetchDetails().resolvedWorkNo()
        }.orEmpty()
    } catch (_: CancellationException) {
        currentCoroutineContext().ensureActive()
        ""
    } catch (_: Throwable) {
        ""
    }
}
