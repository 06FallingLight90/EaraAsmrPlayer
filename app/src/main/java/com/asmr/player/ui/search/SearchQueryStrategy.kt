package com.asmr.player.ui.search

import com.asmr.player.data.repository.SearchRepository
import com.asmr.player.domain.model.Album
import com.asmr.player.util.DlsiteWorkNo

/**
 * R3-C9：SearchViewModel 四分支搜索编排（purchased / collected / 直 RJ / 默认）的策略层。
 * 行为契约（原 SearchViewModel.fetchPage 内联实现，逐行搬迁）：
 * - 分支优先级：已购 > 已收录 > 直 RJ（仅第 1 页且无修饰筛选）> 默认 DLsite 搜索；
 * - 已购分支不读取屏蔽词（blockedKeywordsProvider 不被调用）；
 * - 直 RJ 分支要求屏蔽词未追加（keywordWithBlockedTerms == normalizedKeyword），
 *   且抓取失败时回退默认搜索；
 * - 已收录分支空结果时以直 RJ（若可归一）单条兜底。
 *
 * SearchQueryPort 是面向测试的窄端口：生产实现委托 SearchRepository，
 * seam 测试用 fake 端口表驱动钉住各分支命中与参数传递。
 */
internal interface SearchQueryPort {
    suspend fun searchPurchased(
        keyword: String,
        page: Int,
        pageSize: Int
    ): SearchRepository.PurchasedPage

    suspend fun searchCollected(
        keyword: String,
        limit: Int,
        offset: Int,
        sort: String,
        hasSubtitle: Boolean,
        allAges: Boolean
    ): SearchRepository.CollectedPage

    suspend fun getWorkInfoWithLocaleFallback(
        workNo: String,
        preferredLocale: String?
    ): Album?

    suspend fun searchDlsite(
        keyword: String,
        page: Int,
        order: String,
        locale: String?,
        presaleOnly: Boolean,
        chineseTranslatedOnly: Boolean,
        hasSubtitle: Boolean,
        allAges: Boolean
    ): SearchRepository.DlsitePage
}

internal data class SearchQueryRequest(
    val keyword: String,
    val page: Int,
    val pageSize: Int,
    val order: SearchSortOption,
    val collectedSort: SearchCollectedSortOption,
    val purchasedOnly: Boolean,
    val presaleOnly: Boolean,
    val chineseTranslatedOnly: Boolean,
    val collectedOnly: Boolean,
    val hasSubtitle: Boolean,
    val allAges: Boolean,
    val locale: String?,
    val blockedKeywordsProvider: suspend () -> List<String>
)

internal data class SearchPageResult(
    val items: List<Album>,
    val canGoNext: Boolean,
    val resolvedDetailRjCodes: Set<String> = emptySet()
)

/**
 * R3-C9-3：搜索请求状态（取代 VM 内 9 个 mutable var：order/collectedSort/四个互斥
 * 过滤开关/hasSubtitle/allAges/locale）。
 * 归一化语义与原 normalizeSearchFilters 逐字等价：已购 > 中文 > 预售 > 已收录 的
 * 互斥优先级，排在前面的开关为 true 时后面的强制 false。
 */
internal data class SearchRequestState(
    val order: SearchSortOption = SearchSortOption.Trend,
    val collectedSort: SearchCollectedSortOption = SearchCollectedSortOption.ReleaseNew,
    val purchasedOnly: Boolean = false,
    val presaleOnly: Boolean = false,
    val chineseTranslatedOnly: Boolean = false,
    val collectedOnly: Boolean = true,
    val hasSubtitle: Boolean = false,
    val allAges: Boolean = false,
    val locale: String? = "ja_JP"
) {
    fun normalized(): SearchRequestState {
        val normalizedPurchasedOnly = purchasedOnly
        val normalizedChineseTranslatedOnly = !normalizedPurchasedOnly && chineseTranslatedOnly
        val normalizedPresaleOnly =
            !normalizedPurchasedOnly && !normalizedChineseTranslatedOnly && presaleOnly
        val normalizedCollectedOnly =
            !normalizedPurchasedOnly && !normalizedChineseTranslatedOnly && !normalizedPresaleOnly && collectedOnly
        return copy(
            purchasedOnly = normalizedPurchasedOnly,
            presaleOnly = normalizedPresaleOnly,
            chineseTranslatedOnly = normalizedChineseTranslatedOnly,
            collectedOnly = normalizedCollectedOnly
        )
    }
}

internal suspend fun executeSearchQuery(
    port: SearchQueryPort,
    request: SearchQueryRequest
): SearchPageResult {
    val selectedFilter = SearchFilterOption.fromState(
        purchasedOnly = request.purchasedOnly,
        presaleOnly = request.presaleOnly,
        chineseTranslatedOnly = request.chineseTranslatedOnly,
        collectedOnly = request.collectedOnly
    )
    val appliedHasSubtitle = request.hasSubtitle && selectedFilter.supportsWorkFilters
    val appliedAllAges = request.allAges && selectedFilter.supportsWorkFilters
    if (request.purchasedOnly) {
        val resp = port.searchPurchased(request.keyword, request.page, request.pageSize)
        return SearchPageResult(items = resp.items, canGoNext = resp.canGoNext)
    }
    val keywordWithBlockedTerms = appendBlockedKeywordsForOnlineSearch(
        keyword = request.keyword,
        blockedKeywords = request.blockedKeywordsProvider()
    )
    if (request.collectedOnly) {
        val offset = (request.page.coerceAtLeast(1) - 1) * request.pageSize
        val resp = port.searchCollected(
            keyword = keywordWithBlockedTerms,
            limit = request.pageSize,
            offset = offset,
            sort = request.collectedSort.backendSort,
            hasSubtitle = appliedHasSubtitle,
            allAges = appliedAllAges
        )
        val mappedItems = resp.items
        val directWorkNo = DlsiteWorkNo.normalizeWorkNo(request.keyword, minimumDigits = 6)
        val items = mappedItems.ifEmpty {
            directWorkNo.takeIf { it.isNotBlank() }?.let { workNo ->
                listOf(
                    Album(
                        title = workNo,
                        path = "",
                        workId = workNo,
                        rjCode = workNo,
                        hasAsmrOne = true
                    )
                )
            }.orEmpty()
        }
        val total = resp.total.coerceAtLeast(0)
        val responseOffset = resp.offset.coerceAtLeast(offset)
        return SearchPageResult(
            items = items,
            canGoNext = responseOffset + items.size < total,
            resolvedDetailRjCodes = items
                .mapNotNull { it.rjCode.ifBlank { it.workId }.trim().uppercase().takeIf(String::isNotBlank) }
                .toSet()
        )
    }
    val normalizedKeyword = request.keyword.trim()
    val normalizedWorkNo = DlsiteWorkNo.normalizeWorkNo(normalizedKeyword, minimumDigits = 6)
    if (
        keywordWithBlockedTerms == normalizedKeyword &&
        !request.presaleOnly &&
        !request.chineseTranslatedOnly &&
        !appliedHasSubtitle &&
        !appliedAllAges &&
        request.page == 1 &&
        normalizedWorkNo.isNotBlank()
    ) {
        val preferred = request.locale
        val info = port.getWorkInfoWithLocaleFallback(normalizedWorkNo, preferred)
        if (info != null) {
            val album = info.copy(workId = normalizedWorkNo, rjCode = normalizedWorkNo)
            return SearchPageResult(
                items = listOf(album),
                canGoNext = false,
                resolvedDetailRjCodes = setOf(normalizedWorkNo)
            )
        }
    }
    val result = port.searchDlsite(
        keyword = keywordWithBlockedTerms,
        page = request.page,
        order = request.order.dlsiteOrder,
        locale = resolveSearchRequestLocale(request.locale, request.chineseTranslatedOnly),
        presaleOnly = request.presaleOnly,
        chineseTranslatedOnly = request.chineseTranslatedOnly,
        hasSubtitle = appliedHasSubtitle,
        allAges = appliedAllAges
    )
    return SearchPageResult(items = result.items, canGoNext = result.canGoNext)
}
