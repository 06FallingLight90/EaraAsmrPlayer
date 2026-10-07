package com.asmr.player.ui.search

import com.asmr.player.data.repository.SearchRepository
import com.asmr.player.domain.model.Album
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R3-C9-2：四分支搜索编排 seam 测试（前置安全网）。
 * 用 fake SearchQueryPort 表驱动钉住 SearchQueryStrategy.executeSearchQuery 的
 * 分支命中、参数传递与 locale 回退链（purchased / collected / 直 RJ / 默认）。
 */
class SearchQueryStrategyTest {

    private class CollectedArgs(
        val keyword: String,
        val limit: Int,
        val offset: Int,
        val sort: String,
        val hasSubtitle: Boolean,
        val allAges: Boolean
    )

    private class DlsiteArgs(
        val keyword: String,
        val page: Int,
        val order: String,
        val locale: String?,
        val presaleOnly: Boolean,
        val chineseTranslatedOnly: Boolean,
        val hasSubtitle: Boolean,
        val allAges: Boolean
    )

    private class FakePort : SearchQueryPort {
        var purchasedCalls = 0
        var purchasedKeyword: String? = null
        var purchasedPageNo = 0
        var purchasedPageSize = 0
        var purchasedPage = SearchRepository.PurchasedPage(items = emptyList(), canGoNext = false)

        var collectedCalls = 0
        var collectedArgs: CollectedArgs? = null
        var collectedPage = SearchRepository.CollectedPage(items = emptyList(), total = 0, offset = 0)

        var fallbackCalls = 0
        var fallbackWorkNo: String? = null
        var fallbackLocale: String? = null
        var fallbackResult: Album? = null

        var dlsiteCalls = 0
        var dlsiteArgs: DlsiteArgs? = null
        var dlsitePage = SearchRepository.DlsitePage(items = emptyList(), canGoNext = false)

        override suspend fun searchPurchased(
            keyword: String,
            page: Int,
            pageSize: Int
        ): SearchRepository.PurchasedPage {
            purchasedCalls++
            purchasedKeyword = keyword
            purchasedPageNo = page
            purchasedPageSize = pageSize
            return purchasedPage
        }

        override suspend fun searchCollected(
            keyword: String,
            limit: Int,
            offset: Int,
            sort: String,
            hasSubtitle: Boolean,
            allAges: Boolean
        ): SearchRepository.CollectedPage {
            collectedCalls++
            collectedArgs = CollectedArgs(keyword, limit, offset, sort, hasSubtitle, allAges)
            return collectedPage
        }

        override suspend fun getWorkInfoWithLocaleFallback(
            workNo: String,
            preferredLocale: String?
        ): Album? {
            fallbackCalls++
            fallbackWorkNo = workNo
            fallbackLocale = preferredLocale
            return fallbackResult
        }

        override suspend fun searchDlsite(
            keyword: String,
            page: Int,
            order: String,
            locale: String?,
            presaleOnly: Boolean,
            chineseTranslatedOnly: Boolean,
            hasSubtitle: Boolean,
            allAges: Boolean
        ): SearchRepository.DlsitePage {
            dlsiteCalls++
            dlsiteArgs = DlsiteArgs(keyword, page, order, locale, presaleOnly, chineseTranslatedOnly, hasSubtitle, allAges)
            return dlsitePage
        }
    }

    private var blockedReads = 0

    private fun request(
        keyword: String = "キーワード",
        page: Int = 1,
        pageSize: Int = 30,
        order: SearchSortOption = SearchSortOption.Trend,
        collectedSort: SearchCollectedSortOption = SearchCollectedSortOption.ReleaseNew,
        purchasedOnly: Boolean = false,
        presaleOnly: Boolean = false,
        chineseTranslatedOnly: Boolean = false,
        collectedOnly: Boolean = false,
        hasSubtitle: Boolean = false,
        allAges: Boolean = false,
        locale: String? = "ja_JP",
        blocked: List<String> = emptyList()
    ): SearchQueryRequest {
        blockedReads = 0
        return SearchQueryRequest(
            keyword = keyword,
            page = page,
            pageSize = pageSize,
            order = order,
            collectedSort = collectedSort,
            purchasedOnly = purchasedOnly,
            presaleOnly = presaleOnly,
            chineseTranslatedOnly = chineseTranslatedOnly,
            collectedOnly = collectedOnly,
            hasSubtitle = hasSubtitle,
            allAges = allAges,
            locale = locale,
            blockedKeywordsProvider = {
                blockedReads++
                blocked
            }
        )
    }

    private fun album(workNo: String): Album = Album(
        title = "t-$workNo",
        path = "",
        workId = workNo,
        rjCode = workNo
    )

    // ---- 分支 1：已购 ----

    @Test
    fun purchasedBranch_hitsPurchasedAndSkipsBlockedKeywordRead() = runBlocking {
        val port = FakePort()
        port.purchasedPage = SearchRepository.PurchasedPage(items = listOf(album("RJ111111")), canGoNext = true)

        val result = executeSearchQuery(port, request(purchasedOnly = true, blocked = listOf("排除")))

        assertEquals(1, port.purchasedCalls)
        assertEquals("キーワード", port.purchasedKeyword)
        assertEquals(1, port.purchasedPageNo)
        assertEquals(30, port.purchasedPageSize)
        assertEquals(0, blockedReads)
        assertEquals(0, port.collectedCalls)
        assertEquals(0, port.fallbackCalls)
        assertEquals(0, port.dlsiteCalls)
        assertEquals(listOf(album("RJ111111")), result.items)
        assertTrue(result.canGoNext)
        assertTrue(result.resolvedDetailRjCodes.isEmpty())
    }

    // ---- 分支 2：已收录 ----

    @Test
    fun collectedBranch_passesFiltersAndAppendsBlockedTerms() = runBlocking {
        val port = FakePort()
        port.collectedPage = SearchRepository.CollectedPage(
            items = listOf(album("RJ222222")),
            total = 40,
            offset = 0
        )

        val result = executeSearchQuery(
            port,
            request(
                collectedOnly = true,
                collectedSort = SearchCollectedSortOption.RatingHigh,
                hasSubtitle = true,
                allAges = true,
                blocked = listOf("-foo", "bar")
            )
        )

        assertEquals(1, port.collectedCalls)
        val args = port.collectedArgs!!
        assertEquals("キーワード -foo -bar", args.keyword)
        assertEquals(30, args.limit)
        assertEquals(0, args.offset)
        assertEquals("rating", args.sort)
        assertTrue(args.hasSubtitle)
        assertTrue(args.allAges)
        assertEquals(0, port.purchasedCalls)
        assertEquals(0, port.dlsiteCalls)
        assertTrue(result.canGoNext) // 0 + 1 < 40
        assertEquals(setOf("RJ222222"), result.resolvedDetailRjCodes)
    }

    @Test
    fun collectedBranch_computesOffsetFromPage() = runBlocking {
        val port = FakePort()
        port.collectedPage = SearchRepository.CollectedPage(items = listOf(album("RJ333333")), total = 10, offset = 30)

        val result = executeSearchQuery(port, request(collectedOnly = true, page = 2))

        assertEquals(30, port.collectedArgs!!.offset)
        // 30 + 1 < 10 为假 → 无下一页
        assertEquals(false, result.canGoNext)
    }

    @Test
    fun collectedBranch_emptyResultsFallBackToDirectWorkNo() = runBlocking {
        val port = FakePort()

        val result = executeSearchQuery(port, request(keyword = "rj123456", collectedOnly = true))

        assertTrue(port.collectedArgs!!.keyword.startsWith("rj123456"))
        assertEquals(1, result.items.size)
        assertEquals("RJ123456", result.items[0].workId)
        assertEquals("RJ123456", result.items[0].rjCode)
        assertTrue(result.items[0].hasAsmrOne)
        assertEquals(setOf("RJ123456"), result.resolvedDetailRjCodes)
    }

    @Test
    fun defaultBranch_chineseTranslatedModeForcesWorkFiltersOff() = runBlocking {
        val port = FakePort()

        executeSearchQuery(port, request(chineseTranslatedOnly = true, hasSubtitle = true, allAges = true))

        // ChineseTranslated 模式不支持工作筛选（supportsWorkFilters=false）→ 拉平为 false
        val args = port.dlsiteArgs!!
        assertEquals(false, args.hasSubtitle)
        assertEquals(false, args.allAges)
        assertTrue(args.chineseTranslatedOnly)
    }

    // ---- 分支 3：直 RJ ----

    @Test
    fun directRjBranch_hitsOnPageOneWithoutModifiers() = runBlocking {
        val port = FakePort()
        port.fallbackResult = album("RJ555555").copy(workId = "", rjCode = "")

        val result = executeSearchQuery(port, request(keyword = "RJ123456", locale = "zh_TW"))

        assertEquals(1, port.fallbackCalls)
        assertEquals("RJ123456", port.fallbackWorkNo)
        assertEquals("zh_TW", port.fallbackLocale)
        assertEquals(0, port.dlsiteCalls)
        assertEquals(1, result.items.size)
        assertEquals("RJ123456", result.items[0].workId)
        assertEquals("RJ123456", result.items[0].rjCode)
        assertEquals(false, result.canGoNext)
        assertEquals(setOf("RJ123456"), result.resolvedDetailRjCodes)
    }

    @Test
    fun directRjBranch_missFallsThroughToDlsiteSearch() = runBlocking {
        val port = FakePort()
        port.fallbackResult = null
        port.dlsitePage = SearchRepository.DlsitePage(items = listOf(album("RJ666666")), canGoNext = false)

        val result = executeSearchQuery(port, request(keyword = "RJ123456", order = SearchSortOption.ReleaseNew))

        assertEquals(1, port.fallbackCalls)
        assertEquals(1, port.dlsiteCalls)
        val args = port.dlsiteArgs!!
        assertEquals("RJ123456", args.keyword)
        assertEquals(1, args.page)
        assertEquals("release_d", args.order)
        assertEquals("ja_JP", args.locale)
        assertEquals(listOf(album("RJ666666")), result.items)
    }

    @Test
    fun directRjBranch_blockedByPresaleModifier() = runBlocking {
        val port = FakePort()

        executeSearchQuery(port, request(keyword = "RJ123456", presaleOnly = true))

        assertEquals(0, port.fallbackCalls)
        assertEquals(1, port.dlsiteCalls)
        assertTrue(port.dlsiteArgs!!.presaleOnly)
    }

    @Test
    fun directRjBranch_blockedByBlockedKeywords() = runBlocking {
        val port = FakePort()

        executeSearchQuery(port, request(keyword = "RJ123456", blocked = listOf("排除")))

        assertEquals(0, port.fallbackCalls)
        assertEquals(1, port.dlsiteCalls)
        assertEquals("RJ123456 -排除", port.dlsiteArgs!!.keyword)
    }

    @Test
    fun directRjBranch_blockedByWorkFiltersOnStandardMode() = runBlocking {
        val port = FakePort()

        executeSearchQuery(port, request(keyword = "RJ123456", hasSubtitle = true))

        assertEquals(0, port.fallbackCalls)
        assertEquals(1, port.dlsiteCalls)
        assertTrue(port.dlsiteArgs!!.hasSubtitle)
    }

    @Test
    fun directRjBranch_blockedByPageBeyondOne() = runBlocking {
        val port = FakePort()

        executeSearchQuery(port, request(keyword = "RJ123456", page = 2))

        assertEquals(0, port.fallbackCalls)
        assertEquals(1, port.dlsiteCalls)
        assertEquals(2, port.dlsiteArgs!!.page)
    }

    @Test
    fun directRjBranch_blockedByNonWorkNoKeyword() = runBlocking {
        val port = FakePort()

        executeSearchQuery(port, request(keyword = "普通搜索词"))

        assertEquals(0, port.fallbackCalls)
        assertEquals(1, port.dlsiteCalls)
    }

    // ---- 分支 4：默认 ----

    @Test
    fun defaultBranch_passesOrderAndWorkFilters() = runBlocking {
        val port = FakePort()
        port.dlsitePage = SearchRepository.DlsitePage(items = listOf(album("RJ777777")), canGoNext = true)

        val result = executeSearchQuery(
            port,
            request(
                keyword = "ソフト",
                page = 3,
                order = SearchSortOption.DLCount,
                hasSubtitle = true,
                allAges = true
            )
        )

        assertEquals(1, port.dlsiteCalls)
        val args = port.dlsiteArgs!!
        assertEquals("ソフト", args.keyword)
        assertEquals(3, args.page)
        assertEquals("dl_d", args.order)
        assertTrue(args.hasSubtitle)
        assertTrue(args.allAges)
        assertTrue(result.canGoNext)
    }

    @Test
    fun defaultBranch_localeResolutionChain_table() = runBlocking {
        val cases = listOf(
            Triple(null as String?, false, "ja_JP"),
            Triple("ja_JP", false, "ja_JP"),
            Triple("zh_CN", false, "zh_CN"),
            Triple("zh_TW", false, "zh_TW"),
            Triple("ja_JP", true, "zh_CN")
        )
        cases.forEach { (selected, chineseOnly, expected) ->
            val port = FakePort()
            executeSearchQuery(port, request(locale = selected, chineseTranslatedOnly = chineseOnly))
            assertEquals("selected=$selected chineseOnly=$chineseOnly", expected, port.dlsiteArgs!!.locale)
        }
    }

    @Test
    fun defaultBranch_unknownLocaleFallsBackToCanonicalJapanese() = runBlocking {
        val port = FakePort()

        executeSearchQuery(port, request(locale = "fr_FR"))

        assertEquals("ja_JP", port.dlsiteArgs!!.locale)
    }

    @Test
    fun defaultBranch_nonWorkNoKeywordNeverTouchesFallback() = runBlocking {
        val port = FakePort()

        executeSearchQuery(port, request(keyword = "キーワード"))

        assertEquals(0, port.fallbackCalls)
        assertEquals(1, port.dlsiteCalls)
        assertEquals("キーワード", port.dlsiteArgs!!.keyword)
    }
}
