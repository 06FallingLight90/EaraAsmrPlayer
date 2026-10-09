package com.asmr.player.ui.library.allsongs

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.asmr.player.domain.model.AllSongsQuery
import com.asmr.player.domain.model.AllSongsSort
import com.asmr.player.domain.model.AllSongsTrackRow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AllSongsViewModel 纯 JVM 钉测（fake 端口替身，同 PurchasedViewModelTest 风格）。
 * 本工程测试源集无 kotlinx-coroutines-test，无法虚拟时间去抖：去抖用真实 300ms 窗口
 * （断言上限 3s，充裕且不拖慢套件）；pagedSongs 的 cachedIn(viewModelScope) 以 by lazy
 * 延迟，本测不触该属性，因此不依赖 Dispatchers.Main。去抖语义经"持续收集 + 首值落地
 * 后再输入"的确定性编排验证（首值走 take(1) 即时分支，后续输入走 drop(1).debounce 分支）。
 */
class AllSongsViewModelTest {

    /** 记录型 fake 端口：记录每次取源的查询参数，返回空页 PagingSource。 */
    private class RecordingAllSongsSource : AllSongsPageSource {
        val queries = mutableListOf<AllSongsQuery>()

        override fun allSongsPaged(query: AllSongsQuery): PagingSource<Int, AllSongsTrackRow> {
            queries += query
            return object : PagingSource<Int, AllSongsTrackRow>() {
                override suspend fun load(
                    params: LoadParams<Int>
                ): LoadResult<Int, AllSongsTrackRow> =
                    LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)

                override fun getRefreshKey(state: PagingState<Int, AllSongsTrackRow>): Int? = null
            }
        }
    }

    @Test
    fun normalizedFilter_trimsAndBlankBecomesNoFilter() {
        assertEquals("rj", AllSongsViewModel.normalizedFilter("  rj  "))
        assertEquals(null, AllSongsViewModel.normalizedFilter("   "))
        assertEquals(null, AllSongsViewModel.normalizedFilter(""))
        assertEquals(null, AllSongsViewModel.normalizedFilter(null))
    }

    @Test
    fun initialQuery_isNoFilterWithAddedDescSort() = runBlocking {
        val viewModel = AllSongsViewModel(RecordingAllSongsSource())

        val initial = withTimeout(2_000) { viewModel.queryFlow.first() }

        assertEquals(AllSongsQuery(textFilter = null, sort = AllSongsSort.AddedDesc), initial)
        assertEquals(AllSongsSort.AddedDesc, viewModel.sort.value)
    }

    @Test
    fun filterDebounce_convergesToNormalizedQueryWithCurrentSort() = runBlocking {
        val viewModel = AllSongsViewModel(RecordingAllSongsSource())
        val firstQuery = CompletableDeferred<AllSongsQuery>()
        val settled = CompletableDeferred<AllSongsQuery>()
        val collector = launch {
            var isFirst = true
            viewModel.queryFlow.collect { query ->
                if (isFirst) {
                    isFirst = false
                    firstQuery.complete(query)
                } else {
                    settled.complete(query)
                }
            }
        }

        // 首值（即时分支）落地后再输入，保证输入走去抖分支
        val initial = withTimeout(2_000) { firstQuery.await() }
        assertEquals(AllSongsQuery(textFilter = null, sort = AllSongsSort.AddedDesc), initial)

        viewModel.setTextFilter("  rj  ")
        val debounced = withTimeout(3_000) { settled.await() }
        assertEquals("rj", debounced.textFilter)
        assertEquals(AllSongsSort.AddedDesc, debounced.sort)
        collector.cancelAndJoin()
    }

    @Test
    fun filterDebounce_rapidInputs_convergeToLastNormalizedValue() = runBlocking {
        val viewModel = AllSongsViewModel(RecordingAllSongsSource())
        val firstQuery = CompletableDeferred<AllSongsQuery>()
        val settled = CompletableDeferred<AllSongsQuery>()
        val collector = launch {
            var isFirst = true
            viewModel.queryFlow.collect { query ->
                if (isFirst) {
                    isFirst = false
                    firstQuery.complete(query)
                } else {
                    settled.complete(query)
                }
            }
        }

        withTimeout(2_000) { firstQuery.await() }

        // 同一去抖窗口内的连续击键收敛到最后一值（中间值不被发出）
        viewModel.setTextFilter("ab")
        viewModel.setTextFilter("abc ")
        val debounced = withTimeout(3_000) { settled.await() }
        assertEquals("abc", debounced.textFilter)
        collector.cancelAndJoin()
    }

    @Test
    fun sortSwitch_threeStatesEachDriveDistinctQuery() = runBlocking {
        val viewModel = AllSongsViewModel(RecordingAllSongsSource())

        val initial = withTimeout(2_000) { viewModel.queryFlow.first() }
        assertEquals(AllSongsQuery(textFilter = null, sort = AllSongsSort.AddedDesc), initial)

        viewModel.setSort(AllSongsSort.TitleAsc)
        assertEquals(AllSongsSort.TitleAsc, viewModel.sort.value)
        assertEquals(
            AllSongsQuery(textFilter = null, sort = AllSongsSort.TitleAsc),
            withTimeout(2_000) { viewModel.queryFlow.first() }
        )

        viewModel.setSort(AllSongsSort.FileNameAsc)
        assertEquals(AllSongsSort.FileNameAsc, viewModel.sort.value)
        assertEquals(
            AllSongsQuery(textFilter = null, sort = AllSongsSort.FileNameAsc),
            withTimeout(2_000) { viewModel.queryFlow.first() }
        )

        viewModel.setSort(AllSongsSort.AddedDesc)
        assertEquals(AllSongsSort.AddedDesc, viewModel.sort.value)
        assertEquals(
            AllSongsQuery(textFilter = null, sort = AllSongsSort.AddedDesc),
            withTimeout(2_000) { viewModel.queryFlow.first() }
        )
    }

    @Test
    fun pagerFor_invokesPortWithExactQuery() = runBlocking {
        val source = RecordingAllSongsSource()
        val viewModel = AllSongsViewModel(source)
        val query = AllSongsQuery(textFilter = "rj", sort = AllSongsSort.FileNameAsc)

        withTimeout(5_000) { viewModel.pagerFor(query).first() }

        assertEquals(listOf(query), source.queries)
    }

    @Test
    fun pagedSongs_isLazyAndNotMaterializedByConstruction() {
        // 构造即返回、不触 viewModelScope（Main 分发器）；仅作可构造性钉证。
        val viewModel = AllSongsViewModel(RecordingAllSongsSource())
        assertTrue(viewModel.sort.value == AllSongsSort.AddedDesc)
    }
}
