package com.asmr.player.ui.library.allsongs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.cachedIn
import com.asmr.player.domain.model.AllSongsQuery
import com.asmr.player.domain.model.AllSongsSort
import com.asmr.player.domain.model.AllSongsTrackRow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take

/**
 * 全部歌曲平铺视图（US-05/T6）的窄数据端口。LibraryReadRepository 在 ci_guard
 * 包级 SCC 大连通团内（data.repository），ui 包直接引用会让新包入环（SCC ratchet
 * 禁增），故按 PurchasedPageSource 同款端口模式：本包抽象端口、装配层
 * （MainRouteContents.kt 的 Hilt module）委托 LibraryReadRepository.allSongsPaged 完成。
 * 行投影 [AllSongsTrackRow] 归位 domain.model（T5 既有约定），本包不 import data 层。
 */
interface AllSongsPageSource {
    fun allSongsPaged(query: AllSongsQuery): PagingSource<Int, AllSongsTrackRow>
}

/**
 * 全部歌曲平铺视图 VM：文本过滤去抖 + 排序三态 → query 流 distinctUntilChanged →
 * flatMapLatest 切 Pager（paging 模式与 LibraryViewModel.pagedAlbums 同构：
 * PagingConfig(40, 10, false)，spec 变化即整流重启，配合 Room PagingSource 失效监听）。
 * pagedSongs 用 by lazy 包裹 cachedIn(viewModelScope)：保持属性初始化不触 Main 分发器，
 * 纯 JVM 钉测（无 kotlinx-coroutines-test）才能直接构造本 VM（同 PurchasedViewModel 约束）。
 */
@HiltViewModel
class AllSongsViewModel @Inject constructor(
    private val pageSource: AllSongsPageSource
) : ViewModel() {

    /** 框内文本原始输入（未规范化；规范化在 queryFlow 映射处统一做）。 */
    private val _filterInput = MutableStateFlow("")

    private val _sort = MutableStateFlow(AllSongsSort.AddedDesc)

    /** 当前排序（供屏上排序菜单回显当前选中项）。 */
    val sort: StateFlow<AllSongsSort> = _sort.asStateFlow()

    // ---- 多选（US-05/T8：批量加入歌单/合集）----
    // 勾选以 trackPath 为键（mediaId 约定键，双目标写入共用）；「全选/反选」作用于
    // 屏侧传入的当前已加载可见行（分页流无全量缓存，薄实现取舍同 DirectoryBrowserPanel）。

    private val _selectionActive = MutableStateFlow(false)

    /** 多选模式开关（长按行进入；工具条关闭/返回时退出并清空勾选）。 */
    val selectionActive: StateFlow<Boolean> = _selectionActive.asStateFlow()

    private val _selectedPaths = MutableStateFlow<Set<String>>(emptySet())

    /** 当前勾选的 trackPath 集合（保序交给屏侧按列表序投影，见 AllSongsScreen）。 */
    val selectedPaths: StateFlow<Set<String>> = _selectedPaths.asStateFlow()

    /** 进入多选模式；可选携带首个勾选项（长按行进入的语义）。 */
    fun enterSelectionMode(initialTrackPath: String? = null) {
        _selectionActive.value = true
        if (!initialTrackPath.isNullOrBlank()) {
            _selectedPaths.value = _selectedPaths.value + initialTrackPath
        }
    }

    /** 退出多选模式并清空勾选。 */
    fun exitSelectionMode() {
        _selectionActive.value = false
        _selectedPaths.value = emptySet()
    }

    /** 仅清空勾选，不退出多选模式（查询内容变化由 queryFlow onEach 复用）。 */
    fun clearSelection() {
        _selectedPaths.value = emptySet()
    }

    /** 行勾选切换（多选模式下行点击；非多选模式忽略）。 */
    fun toggleSelection(trackPath: String) {
        if (!_selectionActive.value || trackPath.isBlank()) return
        _selectedPaths.value = if (trackPath in _selectedPaths.value) {
            _selectedPaths.value - trackPath
        } else {
            _selectedPaths.value + trackPath
        }
    }

    /** 全选可见行（并集；已选的不可见项保留）。 */
    fun selectAllVisible(visibleTrackPaths: List<String>) {
        if (!_selectionActive.value) return
        _selectedPaths.value = _selectedPaths.value + visibleTrackPaths.filter { it.isNotBlank() }
    }

    /** 反选可见行（对可见项取 XOR；不可见项保留）。 */
    fun invertVisibleSelection(visibleTrackPaths: List<String>) {
        if (!_selectionActive.value) return
        val visible = visibleTrackPaths.filter { it.isNotBlank() }.toSet()
        val selected = _selectedPaths.value
        _selectedPaths.value = selected + visible - selected.intersect(visible)
    }

    /**
     * 框内文本输入流：首值立即生效（进入页面即可见列表，不等去抖），
     * 其后每次击键去抖 [FILTER_DEBOUNCE_MS]。take/drop 分支订阅均在收集启动的同一
     * 调度周期内完成，UI 线程上不存在"首值后被漏掉一个输入"的窗口。
     */
    @OptIn(FlowPreview::class)
    private val debouncedFilterInput: Flow<String> =
        merge(_filterInput.take(1), _filterInput.drop(1).debounce(FILTER_DEBOUNCE_MS))

    /**
     * 生效查询流：过滤去抖 + 排序即时 → AllSongsQuery，distinctUntilChanged 保证
     * 相同查询不重启 Pager。查询内容变化时清空多选勾选（内容已替换，沿用旧勾选会
     * 误选不可见项；多选模式本身保持，DirectoryBrowserPanel 的换目录清选同构）。
     * internal 供纯 JVM 钉测直收（验证去抖后查询参数与流切换驱动）。
     */
    internal val queryFlow: Flow<AllSongsQuery> = combine(
        debouncedFilterInput.map(::normalizedFilter),
        _sort
    ) { filter, sort -> AllSongsQuery(textFilter = filter, sort = sort) }
        .distinctUntilChanged()
        .onEach { _selectedPaths.value = emptySet() }

    /** 平铺分页流（PagingData；屏侧 collectAsLazyPagingItems 消费）。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val pagedSongs: Flow<PagingData<AllSongsTrackRow>> by lazy {
        queryFlow
            .flatMapLatest { query -> pagerFor(query) }
            .cachedIn(viewModelScope)
    }

    /** 单个 query 的 Pager 流（internal：钉测对端口装配点收流，验证 fake 端口收到正确查询参数）。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    internal fun pagerFor(query: AllSongsQuery): Flow<PagingData<AllSongsTrackRow>> =
        Pager(
            config = PAGING_CONFIG,
            pagingSourceFactory = { pageSource.allSongsPaged(query) }
        ).flow

    /** 框内文本过滤输入（原始输入，规范化延迟到 queryFlow）。 */
    fun setTextFilter(raw: String) {
        _filterInput.value = raw
    }

    /** 排序切换（三态由屏侧菜单逐项下发）。 */
    fun setSort(sort: AllSongsSort) {
        _sort.value = sort
    }

    companion object {
        /** 框内过滤去抖窗口（仓内无列表搜索去抖先例，取任务指定 300ms）。 */
        internal const val FILTER_DEBOUNCE_MS = 300L

        /** 与 LibraryViewModel.pagedAlbums 同参：页大 40、预取 10、无占位。 */
        internal val PAGING_CONFIG = PagingConfig(
            pageSize = 40,
            prefetchDistance = 10,
            enablePlaceholders = false
        )

        /** 与 LibraryFilterStateHolder.setSearchQuery 同口径：trim 后空白 = 不过滤。 */
        internal fun normalizedFilter(raw: String?): String? =
            raw?.trim()?.takeIf { it.isNotEmpty() }
    }
}
