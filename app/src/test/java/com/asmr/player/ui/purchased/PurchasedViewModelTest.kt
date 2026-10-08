package com.asmr.player.ui.purchased

import com.asmr.player.domain.model.Album
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * PurchasedViewModel 纯 JVM 单测。SearchRepository 为 final 具体类且依赖网络栈，
 * 不可在 JVM 侧构造；VM 依赖窄端口 [PurchasedPageSource]，此处以手写 fake 桩替。
 * 加载路径经 internal suspend loadPage 直测（runBlocking），绕开 viewModelScope
 * 对 Dispatchers.Main 的依赖（测试源集无 kotlinx-coroutines-test）；
 * 登录态分支经 refresh/bootstrap 的未登录短路路径覆盖（不触 Main）。
 */
class PurchasedViewModelTest {

    private class FakePurchasedSource(
        private val loggedIn: Boolean = true,
        private val pages: Map<Int, PurchasedPageData> = emptyMap(),
        private val error: Throwable? = null
    ) : PurchasedPageSource {
        val requestedPages = mutableListOf<Int>()
        val requestedKeywords = mutableListOf<String>()
        val requestedPageSizes = mutableListOf<Int>()

        override suspend fun searchPurchased(
            keyword: String,
            page: Int,
            pageSize: Int
        ): PurchasedPageData {
            requestedKeywords += keyword
            requestedPages += page
            requestedPageSizes += pageSize
            error?.let { throw it }
            return pages[page] ?: PurchasedPageData(items = emptyList(), canGoNext = false)
        }

        override fun hasDlsiteStoredCredentials(): Boolean = loggedIn
    }

    private fun album(rj: String, title: String = "作品 $rj"): Album = Album(
        title = title,
        path = "",
        rjCode = rj,
        circle = "社团 $rj"
    )

    @Test
    fun loadPage_success_emitsReadyWithItems() = runBlocking {
        val source = FakePurchasedSource(
            pages = mapOf(
                1 to PurchasedPageData(
                    items = listOf(album("RJ000001"), album("RJ000002")),
                    canGoNext = true
                )
            )
        )
        val viewModel = PurchasedViewModel(source)

        viewModel.loadPage(1)

        val state = viewModel.uiState.value
        assertTrue(state is PurchasedUiState.Ready)
        val ready = state as PurchasedUiState.Ready
        assertEquals(listOf("RJ000001", "RJ000002"), ready.items.map { it.rjCode })
        assertEquals(1, ready.loadedPage)
        assertTrue(ready.canLoadMore)
        assertFalse(ready.isRefreshing)
        assertFalse(ready.isLoadingMore)
        // 阶段一独立页：空关键词全量拉取，固定页大小
        assertEquals(PurchasedViewModel.REQUEST_KEYWORD, source.requestedKeywords.single())
        assertEquals(PurchasedViewModel.PAGE_SIZE, source.requestedPageSizes.single())
    }

    @Test
    fun loadPage_empty_emitsReadyWithNoItems() = runBlocking {
        val source = FakePurchasedSource(
            pages = mapOf(1 to PurchasedPageData(items = emptyList(), canGoNext = false))
        )
        val viewModel = PurchasedViewModel(source)

        viewModel.loadPage(1)

        val state = viewModel.uiState.value
        assertTrue(state is PurchasedUiState.Ready)
        val ready = state as PurchasedUiState.Ready
        assertTrue(ready.isEmpty)
        assertFalse(ready.canLoadMore)
    }

    @Test
    fun loadPage_ioError_emitsErrorWithUserMessage() = runBlocking {
        val source = FakePurchasedSource(error = IOException("boom"))
        val viewModel = PurchasedViewModel(source)

        viewModel.loadPage(1)

        val state = viewModel.uiState.value
        assertTrue(state is PurchasedUiState.Error)
        assertEquals("网络连接失败，请检查网络后重试", (state as PurchasedUiState.Error).message)
    }

    @Test
    fun loadPage_loginRequiredError_mapsToLoginMessage() = runBlocking {
        val source = FakePurchasedSource(
            error = IllegalStateException("请先登录 DLsite（需要 play.dlsite.com 的 Cookie）")
        )
        val viewModel = PurchasedViewModel(source)

        viewModel.loadPage(1)

        val state = viewModel.uiState.value
        assertTrue(state is PurchasedUiState.Error)
        assertTrue((state as PurchasedUiState.Error).message.contains("请先登录"))
    }

    @Test
    fun refresh_notLoggedIn_emitsNotLoggedInWithoutFetching() {
        val source = FakePurchasedSource(loggedIn = false)
        val viewModel = PurchasedViewModel(source)

        viewModel.refresh()

        assertTrue(viewModel.uiState.value is PurchasedUiState.NotLoggedIn)
        assertTrue(source.requestedPages.isEmpty())
    }

    @Test
    fun bootstrap_notLoggedIn_reachesNotLoggedInState() {
        val source = FakePurchasedSource(loggedIn = false)
        val viewModel = PurchasedViewModel(source)
        assertEquals(PurchasedUiState.Bootstrapping, viewModel.uiState.value)

        viewModel.bootstrap()

        assertTrue(viewModel.uiState.value is PurchasedUiState.NotLoggedIn)
        assertTrue(source.requestedPages.isEmpty())
    }

    @Test
    fun bootstrap_readyState_keepsListWithoutReload() = runBlocking {
        val source = FakePurchasedSource(
            pages = mapOf(
                1 to PurchasedPageData(
                    items = listOf(album("RJ000003")),
                    canGoNext = false
                )
            )
        )
        val viewModel = PurchasedViewModel(source)
        viewModel.loadPage(1)

        viewModel.bootstrap()

        val state = viewModel.uiState.value
        assertTrue(state is PurchasedUiState.Ready)
        assertEquals(listOf("RJ000003"), (state as PurchasedUiState.Ready).items.map { it.rjCode })
        assertTrue(source.requestedPages.single() == 1)
    }

    @Test
    fun loadPage_append_mergesItemsAndAdvancesPage() = runBlocking {
        val source = FakePurchasedSource(
            pages = mapOf(
                1 to PurchasedPageData(
                    items = listOf(album("RJ000001")),
                    canGoNext = true
                ),
                2 to PurchasedPageData(
                    items = listOf(album("RJ000002")),
                    canGoNext = false
                )
            )
        )
        val viewModel = PurchasedViewModel(source)

        viewModel.loadPage(1)
        viewModel.loadPage(2)

        val state = viewModel.uiState.value
        assertTrue(state is PurchasedUiState.Ready)
        val ready = state as PurchasedUiState.Ready
        assertEquals(listOf("RJ000001", "RJ000002"), ready.items.map { it.rjCode })
        assertEquals(2, ready.loadedPage)
        assertFalse(ready.canLoadMore)
        assertFalse(ready.isLoadingMore)
        assertEquals(listOf(1, 2), source.requestedPages)
    }

    @Test
    fun loadPage_appendFailure_keepsExistingList() = runBlocking {
        var failPageTwo = false
        val source = FakePurchasedSource(
            pages = mapOf(
                1 to PurchasedPageData(
                    items = listOf(album("RJ000001")),
                    canGoNext = true
                )
            )
        )
        val failingSource = object : PurchasedPageSource {
            override suspend fun searchPurchased(
                keyword: String,
                page: Int,
                pageSize: Int
            ): PurchasedPageData {
                if (failPageTwo) throw IOException("boom")
                return source.searchPurchased(keyword, page, pageSize)
            }

            override fun hasDlsiteStoredCredentials(): Boolean =
                source.hasDlsiteStoredCredentials()
        }
        val viewModel = PurchasedViewModel(failingSource)
        viewModel.loadPage(1)

        failPageTwo = true
        viewModel.loadPage(2)

        val state = viewModel.uiState.value
        assertTrue(state is PurchasedUiState.Ready)
        val ready = state as PurchasedUiState.Ready
        assertEquals(listOf("RJ000001"), ready.items.map { it.rjCode })
        assertEquals(1, ready.loadedPage)
        assertTrue(ready.canLoadMore)
        assertFalse(ready.isLoadingMore)
    }
}
