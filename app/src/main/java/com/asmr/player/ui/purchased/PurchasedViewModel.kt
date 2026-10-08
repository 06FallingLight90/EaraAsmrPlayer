package com.asmr.player.ui.purchased

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asmr.player.domain.model.Album
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 已购曲库页的窄数据端口。SearchRepository 为 final 具体类且依赖网络栈，纯 JVM 单测
 * 无法构造；按最小侵入原则不改动 data/repository，而是由本包内抽象端口、装配层
 * （MainRouteContents.kt 的 Hilt module）委托 SearchRepository 完成。
 *
 * 登录态走 [PurchasedPageSource.hasDlsiteStoredCredentials]（与搜索"已购"过滤
 * SearchViewModel 的既有判断同源）：内部读取 DlsiteAuthStore 的 play cookie（只读）。
 * 本包不 import DlsiteAuthStore / SearchRepository：它们分别是 data.remote.* 与
 * ci_guard SCC 大连通团成员，ui 包直接引用会让新包入环（包级 SCC ratchet 禁增），
 * 故端口用本包自有 DTO [PurchasedPageData] 解耦。
 */
interface PurchasedPageSource {
    suspend fun searchPurchased(keyword: String, page: Int, pageSize: Int): PurchasedPageData
    fun hasDlsiteStoredCredentials(): Boolean
}

data class PurchasedPageData(
    val items: List<Album>,
    val canGoNext: Boolean
)

sealed class PurchasedUiState {
    /** 进入页面后尚未决定登录态/首屏。 */
    object Bootstrapping : PurchasedUiState()

    /** 未登录（无 play cookie）：引导去 dlsite_login。 */
    object NotLoggedIn : PurchasedUiState()

    object Loading : PurchasedUiState()

    data class Ready(
        val items: List<Album>,
        val loadedPage: Int = 1,
        val canLoadMore: Boolean = false,
        val isLoadingMore: Boolean = false,
        val isRefreshing: Boolean = false
    ) : PurchasedUiState() {
        val isEmpty: Boolean get() = items.isEmpty()
    }

    data class Error(val message: String) : PurchasedUiState()
}

/**
 * 错误文案的本包内映射（语义对齐 data/repository SearchErrorMessages.kt；
 * 因 ui 包禁止 import data.repository 的 SCC 成员而本地化）。
 * retrofit2 HttpException 等 net-stack 类型按 ui-to-net-stack 禁令不直接引用，
 * 统一落到兜底文案。
 */
internal fun purchasedErrorUserMessage(e: Throwable): String {
    val raw = e.message.orEmpty()
    if (raw.contains("请先登录")) return "请先登录后再查看已购作品"
    return when (e) {
        is SocketTimeoutException -> "连接超时，请稍后重试"
        is IOException -> "网络连接失败，请检查网络后重试"
        else -> "加载失败，请稍后重试"
    }
}

@HiltViewModel
class PurchasedViewModel @Inject constructor(
    private val source: PurchasedPageSource
) : ViewModel() {

    private val _uiState = MutableStateFlow<PurchasedUiState>(PurchasedUiState.Bootstrapping)
    val uiState: StateFlow<PurchasedUiState> = _uiState.asStateFlow()

    private var pageJob: Job? = null
    private var loggedIn = false

    /** 只读刷新登录态（play cookie 是否存在），返回最新登录态。 */
    fun refreshLoginState(): Boolean {
        loggedIn = source.hasDlsiteStoredCredentials()
        return loggedIn
    }

    /**
     * 屏幕每次进入组合时调用（返回本页也会再次执行）：刷新登录态并决定是否（重）载首屏。
     * 首次进入必载；未登录→登录回来、失败态回来会重载；已有列表的 Ready 态保持不动
     * （刷新交给显式 refresh），避免从作品详情返回时无谓重拉。
     */
    fun bootstrap() {
        refreshLoginState()
        when (_uiState.value) {
            is PurchasedUiState.Bootstrapping, is PurchasedUiState.Error -> refresh()
            is PurchasedUiState.NotLoggedIn -> if (loggedIn) refresh()
            else -> Unit
        }
    }

    /** 下拉/按钮刷新：未登录时直接落到 NotLoggedIn，不发请求。 */
    fun refresh() {
        refreshLoginState()
        if (!loggedIn) {
            pageJob?.cancel()
            _uiState.value = PurchasedUiState.NotLoggedIn
            return
        }
        startLoad(1)
    }

    fun retry() {
        refresh()
    }

    fun loadMore() {
        val current = _uiState.value as? PurchasedUiState.Ready ?: return
        if (!current.canLoadMore || current.isLoadingMore || current.isRefreshing) return
        startLoad(current.loadedPage + 1)
    }

    private fun startLoad(page: Int) {
        pageJob?.cancel()
        pageJob = viewModelScope.launch { loadPage(page) }
    }

    /**
     * 加载核心：分页拉取并按首屏（整页替换）/追加两种模式归约状态。
     * 独立成 suspend 是为了纯 JVM 单测经 runBlocking 直测，绕开 viewModelScope
     * 对 Dispatchers.Main 的依赖（本工程测试源集无 kotlinx-coroutines-test）。
     */
    internal suspend fun loadPage(page: Int) {
        val previous = _uiState.value as? PurchasedUiState.Ready
        val appendTarget = previous?.takeIf { page > 1 && page == it.loadedPage + 1 }
        if (appendTarget != null) {
            _uiState.value = appendTarget.copy(isLoadingMore = true)
        } else {
            _uiState.value = previous?.copy(isRefreshing = true) ?: PurchasedUiState.Loading
        }
        try {
            val resp = source.searchPurchased(REQUEST_KEYWORD, page, PAGE_SIZE)
            _uiState.value = if (appendTarget != null) {
                appendTarget.copy(
                    items = appendTarget.items + resp.items,
                    loadedPage = page,
                    canLoadMore = resp.canGoNext,
                    isLoadingMore = false
                )
            } else {
                PurchasedUiState.Ready(
                    items = resp.items,
                    loadedPage = page,
                    canLoadMore = resp.canGoNext
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _uiState.value = if (appendTarget != null) {
                // 追加失败：保留已有列表，回到可再次触发的状态
                appendTarget.copy(isLoadingMore = false)
            } else {
                PurchasedUiState.Error(purchasedErrorUserMessage(e))
            }
        }
    }

    companion object {
        /** 阶段一独立页不提供关键词过滤，空关键词 = 全量已购。 */
        internal const val REQUEST_KEYWORD = ""
        internal const val PAGE_SIZE = 30
    }
}
