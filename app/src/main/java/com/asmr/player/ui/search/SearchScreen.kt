package com.asmr.player.ui.search

import com.asmr.player.ui.translation.PageTranslationHost

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import com.asmr.player.ui.common.core.isCompactWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.asmr.player.domain.model.Album
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.titleForDisplay
import com.asmr.player.ui.common.list.ActiveDropdownMenuItem
import com.asmr.player.ui.common.core.CustomSearchBar
import com.asmr.player.ui.common.cover.EaraLogoLoadingIndicator
import com.asmr.player.ui.common.list.LocalBottomOverlayPadding
import com.asmr.player.ui.common.list.interruptScrollableFlingOnPointerDown
import com.asmr.player.ui.common.core.clearFocusOnTapOutside
import com.asmr.player.ui.common.list.CollapsibleHeaderState
import com.asmr.player.ui.common.list.collapsibleHeaderUiState
import com.asmr.player.ui.common.core.consumeTapThrough
import com.asmr.player.ui.common.list.rememberCollapsibleHeaderState
import com.asmr.player.ui.common.list.rememberSaveablePrefetchedLazyListState
import com.asmr.player.ui.common.list.collectAsStateWhileActive
import com.asmr.player.ui.library.AlbumMetaActionDialog
import com.asmr.player.ui.library.rememberAlbumMetaCopyAction
import com.asmr.player.ui.groups.AlbumGroupsViewModel
import com.asmr.player.ui.playlists.PlaylistsViewModel
import com.asmr.player.ui.common.core.SearchBlockedKeywordsViewModel
import com.asmr.player.ui.sidepanel.LandscapeRightPanelHost
import com.asmr.player.ui.sidepanel.RecentAlbumsPanel
import com.asmr.player.ui.theme.AsmrTheme
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.snapshotFlow
import kotlin.math.roundToInt

internal const val SEARCH_INPUT_TAG = "search_input"
internal const val SEARCH_SCOPE_BUTTON_TAG = "search_scope_button"
internal const val SEARCH_SCOPE_OPTION_TAG_PREFIX = "search_scope_option"
internal const val SEARCH_SORT_BUTTON_TAG = "search_sort_button"
internal const val SEARCH_COLLECTED_SORT_OPTION_TAG_PREFIX = "search_collected_sort_option"
internal const val SEARCH_SORT_OPTION_TAG_PREFIX = "search_sort_option"
internal const val SEARCH_LANGUAGE_OPTION_TAG_PREFIX = "search_language_option"
internal const val SEARCH_HAS_SUBTITLE_OPTION_TAG = "search_has_subtitle_option"
internal const val SEARCH_ALL_AGES_OPTION_TAG = "search_all_ages_option"
internal const val SEARCH_CLEAR_BUTTON_TAG = "search_clear_button"
internal const val SEARCH_SUBMIT_BUTTON_TAG = "search_submit_button"
internal const val SEARCH_SUBMIT_SPINNER_TAG = "search_submit_spinner"
internal const val SEARCH_FIRST_PAGE_BUTTON_TAG = "search_first_page_button"
internal const val SEARCH_PREV_BUTTON_TAG = "search_prev_button"
internal const val SEARCH_NEXT_BUTTON_TAG = "search_next_button"
internal const val SEARCH_PAGINATION_TAG = "search_pagination"
internal const val SEARCH_CHROME_TAG = "search_chrome"
private val SearchChromeContentGap = 16.dp
internal val SearchPageHorizontalPadding = 8.dp
internal fun searchResultScrollKey(success: SearchUiState.Success?): String {
    if (success == null) return "search-results:none"
    return buildString {
        append("search-results:")
        append(success.resultRevision)
        append(':')
        append(success.page)
        append(':')
        append(success.keyword)
        append(':')
        append(success.order.name)
        append(':')
        append(success.collectedSort.name)
        append(':')
        append(success.purchasedOnly)
        append(':')
        append(success.presaleOnly)
        append(':')
        append(success.chineseTranslatedOnly)
        append(':')
        append(success.collectedOnly)
        append(':')
        append(success.hasSubtitle)
        append(':')
        append(success.allAges)
        append(':')
        append(success.locale.orEmpty())
    }
}

internal data class SearchChromeLockState(
    val interactionLocked: Boolean,
    val filterControlsLocked: Boolean,
    val searchSubmitLocked: Boolean,
    val showSearchSpinner: Boolean
)

internal fun resolveSearchChromeLockState(uiState: SearchUiState): SearchChromeLockState {
    val success = uiState as? SearchUiState.Success
    val interactionLocked = success?.isBusy == true
    val requestLocked = uiState is SearchUiState.Loading || interactionLocked
    return SearchChromeLockState(
        interactionLocked = interactionLocked,
        filterControlsLocked = requestLocked,
        searchSubmitLocked = requestLocked,
        showSearchSpinner = interactionLocked
    )
}

@Composable
private fun SearchFilterIconView(
    icon: SearchFilterIcon,
    tint: Color,
    modifier: Modifier = Modifier
) {
    when (icon) {
        is SearchFilterIcon.Vector -> Icon(
            painter = rememberVectorPainter(icon.imageVector),
            contentDescription = null,
            tint = tint,
            modifier = modifier
        )

        is SearchFilterIcon.Drawable -> Icon(
            painter = painterResource(icon.resId),
            contentDescription = null,
            tint = tint,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    windowSizeClass: WindowSizeClass,
    isActive: Boolean = true,
    isDataActive: Boolean = isActive,
    onAlbumClick: (Album, Boolean, Boolean) -> Unit,
    onOpenSearchAssist: (SearchAssistSearchRequest) -> Unit = {},
    submittedSearchKeyword: String = "",
    submittedSearchOrderName: String = SearchSortOption.Trend.name,
    submittedSearchPurchasedOnly: Boolean = false,
    submittedSearchPresaleOnly: Boolean = false,
    submittedSearchChineseTranslatedOnly: Boolean = false,
    submittedSearchCollectedOnly: Boolean = true,
    submittedSearchHasSubtitle: Boolean = false,
    submittedSearchAllAges: Boolean = false,
    submittedSearchCollectedSortName: String = SearchCollectedSortOption.ReleaseNew.name,
    submittedSearchLocale: String = "ja_JP",
    submittedSearchSignal: Long = 0L,
    scrollToTopSignal: Long = 0L,
    onHorizontalPagerScrollLockChanged: (Boolean) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel()
) {
    PageTranslationHost(active = isActive && isDataActive, headerKey = "search") {
        SearchScreenContent(
            windowSizeClass = windowSizeClass,
            isActive = isActive,
            isDataActive = isDataActive,
            onAlbumClick = onAlbumClick,
            onOpenSearchAssist = onOpenSearchAssist,
            submittedSearchKeyword = submittedSearchKeyword,
            submittedSearchOrderName = submittedSearchOrderName,
            submittedSearchPurchasedOnly = submittedSearchPurchasedOnly,
            submittedSearchPresaleOnly = submittedSearchPresaleOnly,
            submittedSearchChineseTranslatedOnly = submittedSearchChineseTranslatedOnly,
            submittedSearchCollectedOnly = submittedSearchCollectedOnly,
            submittedSearchHasSubtitle = submittedSearchHasSubtitle,
            submittedSearchAllAges = submittedSearchAllAges,
            submittedSearchCollectedSortName = submittedSearchCollectedSortName,
            submittedSearchLocale = submittedSearchLocale,
            submittedSearchSignal = submittedSearchSignal,
            scrollToTopSignal = scrollToTopSignal,
            onHorizontalPagerScrollLockChanged = onHorizontalPagerScrollLockChanged,
            viewModel = viewModel,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
private fun SearchScreenContent(
    windowSizeClass: WindowSizeClass,
    isActive: Boolean = true,
    isDataActive: Boolean = isActive,
    onAlbumClick: (Album, Boolean, Boolean) -> Unit,
    onOpenSearchAssist: (SearchAssistSearchRequest) -> Unit = {},
    submittedSearchKeyword: String = "",
    submittedSearchOrderName: String = SearchSortOption.Trend.name,
    submittedSearchPurchasedOnly: Boolean = false,
    submittedSearchPresaleOnly: Boolean = false,
    submittedSearchChineseTranslatedOnly: Boolean = false,
    submittedSearchCollectedOnly: Boolean = true,
    submittedSearchHasSubtitle: Boolean = false,
    submittedSearchAllAges: Boolean = false,
    submittedSearchCollectedSortName: String = SearchCollectedSortOption.ReleaseNew.name,
    submittedSearchLocale: String = "ja_JP",
    submittedSearchSignal: Long = 0L,
    scrollToTopSignal: Long = 0L,
    onHorizontalPagerScrollLockChanged: (Boolean) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel()
) {
    var keyword by rememberSaveable { mutableStateOf("") }
    var purchasedOnly by rememberSaveable { mutableStateOf(false) }
    var presaleOnly by rememberSaveable { mutableStateOf(false) }
    var chineseTranslatedOnly by rememberSaveable { mutableStateOf(false) }
    var collectedOnly by rememberSaveable { mutableStateOf(true) }
    var hasSubtitle by rememberSaveable { mutableStateOf(false) }
    var allAges by rememberSaveable { mutableStateOf(false) }
    var selectedCollectedSortName by rememberSaveable { mutableStateOf(SearchCollectedSortOption.ReleaseNew.name) }
    var selectedLocale by rememberSaveable { mutableStateOf("ja_JP") }
    var selectedOrderName by rememberSaveable { mutableStateOf(SearchSortOption.Trend.name) }
    val selectedOrder = remember(selectedOrderName) {
        SearchSortOption.values().firstOrNull { it.name == selectedOrderName } ?: SearchSortOption.Trend
    }
    val selectedCollectedSort = remember(selectedCollectedSortName) {
        SearchCollectedSortOption.fromName(selectedCollectedSortName)
    }
    val selectedFilter = remember(purchasedOnly, presaleOnly, chineseTranslatedOnly, collectedOnly) {
        SearchFilterOption.fromState(
            purchasedOnly = purchasedOnly,
            presaleOnly = presaleOnly,
            chineseTranslatedOnly = chineseTranslatedOnly,
            collectedOnly = collectedOnly
        )
    }
    val viewMode by viewModel.viewMode.collectAsStateWhileActive(isDataActive)
    val uiState by viewModel.uiState.collectAsStateWhileActive(isDataActive)
    val hotKeywordTerms by viewModel.hotKeywordTerms.collectAsStateWhileActive(isDataActive)
    val showHotKeywordFallback by viewModel.showHotKeywordFallback.collectAsStateWhileActive(isDataActive)
    val hotKeywordCarouselItem = rememberSearchHotKeywordCarouselItem(
        terms = hotKeywordTerms,
        showFallback = showHotKeywordFallback
    )
    val success = uiState as? SearchUiState.Success
    val resultScrollKey = searchResultScrollKey(success)
    val listState = rememberSaveablePrefetchedLazyListState(stateKey = resultScrollKey)
    val gridState = rememberSaveable(resultScrollKey, saver = LazyStaggeredGridState.Saver) { LazyStaggeredGridState() }
    val colorScheme = AsmrTheme.colorScheme
    val copyMeta = rememberAlbumMetaCopyAction(viewModel.messageManager)
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val isCompact = windowSizeClass.widthSizeClass.isCompactWidth
    val chromeState = rememberCollapsibleHeaderState()
    val chromeResetKey = remember(resultScrollKey, viewMode) { "$resultScrollKey:$viewMode" }
    var lastChromeResetKey by rememberSaveable { mutableStateOf(chromeResetKey) }

    var keywordSyncedFromState by rememberSaveable { mutableStateOf(false) }
    var optionsSyncedFromState by rememberSaveable { mutableStateOf(false) }
    var lastHandledSubmittedSearchSignal by rememberSaveable { mutableStateOf(0L) }
    var metaActionKeyword by rememberSaveable { mutableStateOf<String?>(null) }

    fun openMetaActions(value: String) {
        val normalized = value.trim()
        if (normalized.isNotBlank()) metaActionKeyword = normalized
    }

    LaunchedEffect(Unit) {
        viewModel.bootstrap(
            initialKeyword = keyword,
            initialPurchasedOnly = purchasedOnly,
            initialLocale = selectedLocale,
            initialCollectedOnly = collectedOnly,
            initialCollectedSort = selectedCollectedSort,
            initialHasSubtitle = hasSubtitle,
            initialAllAges = allAges
        )
    }

    LaunchedEffect(isDataActive, viewModel) {
        if (isDataActive) viewModel.ensureHotKeywordTermsLoaded()
    }

    LaunchedEffect(success?.keyword) {
        val state = success ?: return@LaunchedEffect
        if (!keywordSyncedFromState) {
            keyword = state.keyword
            keywordSyncedFromState = true
        }
    }

    LaunchedEffect(
        success?.pendingRequest,
        success?.order,
        success?.collectedSort,
        success?.purchasedOnly,
        success?.presaleOnly,
        success?.chineseTranslatedOnly,
        success?.collectedOnly,
        success?.hasSubtitle,
        success?.allAges,
        success?.locale
    ) {
        val state = success ?: return@LaunchedEffect
        if (!optionsSyncedFromState || state.pendingRequest == null) {
            purchasedOnly = state.purchasedOnly
            presaleOnly = state.presaleOnly
            chineseTranslatedOnly = state.chineseTranslatedOnly
            collectedOnly = state.collectedOnly
            hasSubtitle = state.hasSubtitle
            allAges = state.allAges
            selectedCollectedSortName = state.collectedSort.name
            selectedLocale = state.locale ?: "ja_JP"
            selectedOrderName = state.order.name
            optionsSyncedFromState = true
        }
    }

    val chromeLockState = remember(uiState) { resolveSearchChromeLockState(uiState) }
    val interactionLocked = chromeLockState.interactionLocked
    val filterControlsLocked = chromeLockState.filterControlsLocked
    val searchSubmitLocked = chromeLockState.searchSubmitLocked
    val showSearchSpinner = chromeLockState.showSearchSpinner
    val highlightedPage = success?.page ?: 1
    val canGoPrev = success?.canGoPrev == true && !success.isSearching
    val canGoNext = success?.canGoNext == true && !success.isSearching
    val chromeReservedHeightPx = when {
        chromeState.heightPx > 0f -> chromeState.heightPx
        success != null -> with(androidx.compose.ui.platform.LocalDensity.current) { 120.dp.toPx() }
        else -> with(androidx.compose.ui.platform.LocalDensity.current) { 80.dp.toPx() }
    }
    val topPadding = with(androidx.compose.ui.platform.LocalDensity.current) { chromeReservedHeightPx.toDp() } + SearchChromeContentGap

    fun scrollResultsToTop() {
        scope.launch {
            runCatching { listState.stopScroll(MutatePriority.PreventUserInput) }
            runCatching { gridState.stopScroll(MutatePriority.PreventUserInput) }
            runCatching { listState.scrollToItem(0) }
            runCatching { gridState.scrollToItem(0) }
        }
    }

    fun requestNextPage() {
        scrollResultsToTop()
        viewModel.nextPage()
    }

    fun submitSearch() {
        if (searchSubmitLocked) return
        val nextKeyword = keyword.trim()
            .ifBlank { hotKeywordCarouselItem.keyword.orEmpty() }
        if (nextKeyword.isBlank()) return
        keyboardController?.hide()
        val accepted = viewModel.search(nextKeyword)
        if (!accepted) return
        keyword = nextKeyword
        scrollResultsToTop()
    }

    fun clearKeywordAndSearch() {
        if (searchSubmitLocked) return
        keyword = ""
        keyboardController?.hide()
        val accepted = viewModel.search("")
        if (!accepted) return
        scrollResultsToTop()
        chromeState.expand()
    }

    fun searchMetaKeyword(value: String) {
        if (searchSubmitLocked) {
            viewModel.messageManager.showInfo("搜索处理中，请稍后再试")
            return
        }
        val normalized = value.trim()
        if (normalized.isBlank()) return
        keyboardController?.hide()
        val accepted = viewModel.search(
            keyword = normalized,
            order = selectedOrder,
            collectedSort = selectedCollectedSort,
            purchasedOnly = purchasedOnly,
            presaleOnly = presaleOnly,
            chineseTranslatedOnly = chineseTranslatedOnly,
            collectedOnly = collectedOnly,
            hasSubtitle = hasSubtitle,
            allAges = allAges,
            locale = selectedLocale
        )
        if (!accepted) return
        keyword = normalized
        scrollResultsToTop()
        chromeState.expand()
    }

    fun currentSearchAssistRequest(): SearchAssistSearchRequest {
        return SearchAssistSearchRequest(
            keyword = keyword,
            orderName = selectedOrder.name,
            purchasedOnly = purchasedOnly,
            presaleOnly = presaleOnly,
            chineseTranslatedOnly = chineseTranslatedOnly,
            collectedOnly = collectedOnly,
            hasSubtitle = hasSubtitle,
            allAges = allAges,
            collectedSortName = selectedCollectedSort.name,
            locale = selectedLocale
        )
    }

    LaunchedEffect(
        submittedSearchSignal,
        submittedSearchKeyword,
        submittedSearchOrderName,
        submittedSearchPurchasedOnly,
        submittedSearchPresaleOnly,
        submittedSearchChineseTranslatedOnly,
        submittedSearchCollectedOnly,
        submittedSearchHasSubtitle,
        submittedSearchAllAges,
        submittedSearchCollectedSortName,
        submittedSearchLocale,
        searchSubmitLocked
    ) {
        if (
            submittedSearchSignal == 0L ||
            submittedSearchSignal == lastHandledSubmittedSearchSignal ||
            searchSubmitLocked
        ) {
            return@LaunchedEffect
        }
        val normalizedKeyword = submittedSearchKeyword.trim()
        if (searchSubmitLocked) return@LaunchedEffect
        val submittedOrder = SearchSortOption.values()
            .firstOrNull { it.name == submittedSearchOrderName }
            ?: SearchSortOption.Trend
        val submittedCollectedSort = SearchCollectedSortOption.fromName(submittedSearchCollectedSortName)
        keyboardController?.hide()
        val accepted = viewModel.search(
            keyword = normalizedKeyword,
            order = submittedOrder,
            collectedSort = submittedCollectedSort,
            purchasedOnly = submittedSearchPurchasedOnly,
            presaleOnly = submittedSearchPresaleOnly,
            chineseTranslatedOnly = submittedSearchChineseTranslatedOnly,
            collectedOnly = submittedSearchCollectedOnly,
            hasSubtitle = submittedSearchHasSubtitle,
            allAges = submittedSearchAllAges,
            locale = submittedSearchLocale
        )
        if (!accepted) return@LaunchedEffect
        lastHandledSubmittedSearchSignal = submittedSearchSignal
        keyword = normalizedKeyword
        selectedOrderName = submittedOrder.name
        selectedCollectedSortName = submittedCollectedSort.name
        purchasedOnly = submittedSearchPurchasedOnly
        presaleOnly = submittedSearchPresaleOnly
        chineseTranslatedOnly = submittedSearchChineseTranslatedOnly
        collectedOnly = submittedSearchCollectedOnly
        hasSubtitle = submittedSearchHasSubtitle
        allAges = submittedSearchAllAges
        selectedLocale = submittedSearchLocale
        scrollResultsToTop()
        chromeState.expand()
    }

    val pullGesture = rememberSearchPullGestureState(
        resultScrollKey = resultScrollKey,
        viewMode = viewMode,
        listState = listState,
        gridState = gridState,
        pullNextPageEnabledBase = success?.results?.isNotEmpty() == true &&
            canGoNext &&
            !interactionLocked,
        topPadding = topPadding,
        requestNextPage = ::requestNextPage,
        onHorizontalPagerScrollLockChanged = onHorizontalPagerScrollLockChanged
    )
    var pullRefreshStartedAtMs by remember { mutableLongStateOf(0L) }
    val latestKeyword by rememberUpdatedState(keyword)
    fun stopActiveScroll() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (!pullGesture.pullNextPageReturnInProgress) {
                pullGesture.pullNextPageDragPx = 0f
                pullGesture.pullNextPageGestureActive = false
                pullGesture.pullNextPageRequestAfterReturn = false
                pullGesture.reportScrollLock(false)
            }
            runCatching { listState.stopScroll(MutatePriority.UserInput) }
            runCatching { gridState.stopScroll(MutatePriority.UserInput) }
        }
    }

    LaunchedEffect(pullGesture.pullToRefreshState.isRefreshing) {
        if (!pullGesture.pullToRefreshState.isRefreshing) {
            pullRefreshStartedAtMs = 0L
            return@LaunchedEffect
        }
        pullRefreshStartedAtMs = android.os.SystemClock.elapsedRealtime()
        when (val state = uiState) {
            is SearchUiState.Success -> {
                if (state.isBusy) {
                    pullGesture.pullToRefreshState.endRefresh()
                } else {
                    viewModel.refreshPage()
                }
            }

            is SearchUiState.Loading -> Unit
            else -> viewModel.search(latestKeyword)
        }
    }
    LaunchedEffect(uiState) {
        if (!pullGesture.pullToRefreshState.isRefreshing) return@LaunchedEffect
        val canEnd = when (val state = uiState) {
            is SearchUiState.Success -> !state.isBusy
            is SearchUiState.Loading -> false
            else -> true
        }
        if (canEnd) {
            val elapsedMillis = android.os.SystemClock.elapsedRealtime() - pullRefreshStartedAtMs
            val remainingFeedbackMillis =
                (SearchPullRefreshMinFeedbackMillis - elapsedMillis).coerceAtLeast(0L)
            if (remainingFeedbackMillis > 0L) delay(remainingFeedbackMillis)
            if (pullGesture.pullToRefreshState.isRefreshing) pullGesture.pullToRefreshState.endRefresh()
        }
    }

    LaunchedEffect(chromeResetKey) {
        if (lastChromeResetKey != chromeResetKey) {
            chromeState.expand()
            lastChromeResetKey = chromeResetKey
        }
    }
    LaunchedEffect(listState, viewMode) {
        if (viewMode != 0) return@LaunchedEffect
        snapshotFlow {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
            .distinctUntilChanged()
            .collect { atTop ->
                if (atTop) chromeState.expand()
            }
    }
    LaunchedEffect(gridState, viewMode) {
        if (viewMode == 0) return@LaunchedEffect
        snapshotFlow {
            gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
        }
            .distinctUntilChanged()
            .collect { atTop ->
                if (atTop) chromeState.expand()
            }
    }
    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal == 0L) return@LaunchedEffect
        pullGesture.pullNextPageDragPx = 0f
        pullGesture.pullNextPageGestureActive = false
        pullGesture.pullNextPageRequestAfterReturn = false
        pullGesture.pullNextPageReturnInProgress = false
        when (viewMode) {
            0 -> {
                runCatching { listState.stopScroll(MutatePriority.PreventUserInput) }
                runCatching { listState.scrollToItem(0) }
            }
            else -> {
                runCatching { gridState.stopScroll(MutatePriority.PreventUserInput) }
                runCatching { gridState.scrollToItem(0) }
            }
        }
        chromeState.expand()
    }

    LaunchedEffect(isActive, viewMode) {
        if (isActive) return@LaunchedEffect
        when (viewMode) {
            0 -> listState.stopScroll(MutatePriority.PreventUserInput)
            else -> gridState.stopScroll(MutatePriority.PreventUserInput)
        }
        pullGesture.pullNextPageDragPx = 0f
        pullGesture.pullNextPageGestureActive = false
        pullGesture.pullNextPageRequestAfterReturn = false
        pullGesture.pullNextPageReturnInProgress = false
        pullGesture.reportScrollLock(false)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent,
        contentColor = colorScheme.onBackground
    ) { padding ->
        LandscapeRightPanelHost(
            windowSizeClass = windowSizeClass,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            topPanel = {
                RecentAlbumsPanel(
                    onOpenAlbum = { album ->
                        onAlbumClick(
                            Album(
                                id = album.id,
                                title = album.titleForDisplay,
                                path = album.path,
                                localPath = album.localPath,
                                downloadPath = album.downloadPath,
                                circle = album.circle,
                                cv = album.cv,
                                tags = album.tags.split(",").map { it.trim() }.filter { it.isNotBlank() },
                                coverUrl = album.coverUrl,
                                coverPath = album.coverPath,
                                coverThumbPath = album.coverThumbPath,
                                workId = album.workId,
                                rjCode = album.rjCode,
                                description = album.description
                            ),
                            false,
                            false
                        )
                    },
                    modifier = Modifier.fillMaxHeight()
                )
            },
            bottomPanel = null
        ) { contentModifier, hasRightPanel, rightPanelToggle ->
            Box(
                modifier = contentModifier,
                contentAlignment = if (hasRightPanel) Alignment.TopStart else Alignment.TopCenter
            ) {
                val searchContentModifier = if (isCompact || hasRightPanel) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxHeight()
                        .widthIn(max = 800.dp)
                        .fillMaxWidth()
                }
                Box(
                    modifier = searchContentModifier
                        .interruptScrollableFlingOnPointerDown { stopActiveScroll() }
                ) {
                    CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(pullGesture.dragModifier(resultScrollKey, viewMode))
                        ) {
                        SearchPullRefreshHintOverlay(
                            state = pullGesture,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clearFocusOnTapOutside()
                                .graphicsLayer { translationY = pullGesture.listStretchOffsetPx }
                        ) {
                            SearchResultsContent(
                                state = uiState,
                                topPadding = topPadding,
                                viewMode = viewMode,
                                isActive = isActive,
                                isCompact = isCompact,
                                listState = listState,
                                gridState = gridState,
                                chromeNestedScrollConnection = chromeState.nestedScrollConnection,
                                onAlbumClick = onAlbumClick,
                                onMetaAction = ::openMetaActions,
                                onRetry = { viewModel.retry() }
                            )
                        }

                        SearchPullNextPageHintOverlay(
                            state = pullGesture,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                        }
                    }

                    SearchChrome(
                        modifier = Modifier.align(Alignment.TopCenter),
                        keyword = keyword,
                        onKeywordChange = { keyword = it },
                        placeholder = hotKeywordCarouselItem.placeholder,
                        searchFieldReadOnly = true,
                        onSearchFieldClick = { onOpenSearchAssist(currentSearchAssistRequest()) },
                        selectedFilter = selectedFilter,
                        selectedOrder = selectedOrder,
                        selectedCollectedSort = selectedCollectedSort,
                        hasSubtitle = hasSubtitle,
                        allAges = allAges,
                        selectedLocale = selectedLocale,
                        filterControlsLocked = filterControlsLocked,
                        searchSubmitLocked = searchSubmitLocked,
                        showSearchSpinner = showSearchSpinner,
                        showPagination = success != null,
                        page = highlightedPage,
                        canGoPrev = canGoPrev,
                        canGoNext = canGoNext,
                        controlsLocked = interactionLocked,
                        rightPanelToggle = rightPanelToggle,
                        chromeState = chromeState,
                        onMeasured = { size: IntSize -> chromeState.updateHeight(size.height.toFloat()) },
                        onSearchSubmit = { submitSearch() },
                        onClearKeyword = { clearKeywordAndSearch() },
                        onOptionsChanged = { options ->
                            val option = options.scope
                            val resultSetOptionsChanged =
                                option != selectedFilter ||
                                    options.order != selectedOrder ||
                                    options.collectedSort != selectedCollectedSort ||
                                    options.hasSubtitle != hasSubtitle ||
                                    options.allAges != allAges
                            val accepted = viewModel.updateSearchOptions(
                                order = options.order,
                                collectedSort = options.collectedSort,
                                purchasedOnly = option.isPurchasedOnly,
                                presaleOnly = option.isPresaleOnly,
                                chineseTranslatedOnly = option.isChineseTranslated,
                                collectedOnly = option.isCollectedOnly,
                                hasSubtitle = options.hasSubtitle,
                                allAges = options.allAges,
                                locale = options.locale
                            )
                            if (accepted) {
                                purchasedOnly = option.isPurchasedOnly
                                presaleOnly = option.isPresaleOnly
                                chineseTranslatedOnly = option.isChineseTranslated
                                collectedOnly = option.isCollectedOnly
                                selectedOrderName = options.order.name
                                selectedCollectedSortName = options.collectedSort.name
                                hasSubtitle = options.hasSubtitle
                                allAges = options.allAges
                                selectedLocale = options.locale
                                if (resultSetOptionsChanged) {
                                    scrollResultsToTop()
                                    chromeState.expand()
                                }
                            }
                        },
                        onFirstPage = {
                            scrollResultsToTop()
                            viewModel.firstPage()
                        },
                        onPrev = {
                            scrollResultsToTop()
                            viewModel.prevPage()
                        },
                        onNext = {
                            requestNextPage()
                        }
                    )
                }
            }
        }
    }

    metaActionKeyword?.let { targetKeyword ->
        val playlistsViewModel: PlaylistsViewModel = hiltViewModel()
        val albumGroupsViewModel: AlbumGroupsViewModel = hiltViewModel()
        val blockedKeywordsViewModel: SearchBlockedKeywordsViewModel = hiltViewModel()
        val searchBlockedKeywords by blockedKeywordsViewModel.searchBlockedKeywords.collectAsStateWhileActive(isDataActive)
        AlbumMetaActionDialog(
            keyword = targetKeyword,
            onDismissRequest = { metaActionKeyword = null },
            onSearch = ::searchMetaKeyword,
            onCreatePlaylist = playlistsViewModel::createPlaylist,
            onCreateGroup = albumGroupsViewModel::createGroup,
            onAddBlockedKeyword = { value ->
                val normalized = value.trim()
                if (normalized.isNotBlank()) {
                    val exists = searchBlockedKeywords.any { it.equals(normalized, ignoreCase = true) }
                    blockedKeywordsViewModel.addSearchBlockedKeyword(normalized)
                    if (exists) {
                        viewModel.messageManager.showInfo("屏蔽词已存在：$normalized")
                    } else {
                        viewModel.messageManager.showSuccess("已添加屏蔽词：$normalized")
                    }
                }
            },
            onCopy = { copyMeta("内容", it) },
        )
    }
}


@Composable
internal fun SearchChrome(
    modifier: Modifier = Modifier,
    keyword: String,
    onKeywordChange: (String) -> Unit,
    placeholder: String = DefaultSearchPlaceholder,
    searchFieldReadOnly: Boolean = false,
    onSearchFieldClick: (() -> Unit)? = null,
    selectedFilter: SearchFilterOption,
    selectedOrder: SearchSortOption = SearchSortOption.Trend,
    selectedCollectedSort: SearchCollectedSortOption = SearchCollectedSortOption.ReleaseNew,
    hasSubtitle: Boolean = false,
    allAges: Boolean = false,
    selectedLocale: String,
    filterControlsLocked: Boolean,
    searchSubmitLocked: Boolean,
    showSearchSpinner: Boolean,
    showPagination: Boolean,
    page: Int,
    canGoPrev: Boolean,
    canGoNext: Boolean,
    controlsLocked: Boolean,
    rightPanelToggle: (@Composable (Modifier) -> Unit)?,
    chromeState: CollapsibleHeaderState,
    chromeTestTag: String = SEARCH_CHROME_TAG,
    inputTestTag: String = SEARCH_INPUT_TAG,
    clearButtonTestTag: String = SEARCH_CLEAR_BUTTON_TAG,
    submitButtonTestTag: String = SEARCH_SUBMIT_BUTTON_TAG,
    inputFocusRequester: FocusRequester? = null,
    onMeasured: (IntSize) -> Unit,
    onSearchSubmit: () -> Unit,
    onClearKeyword: (() -> Unit)? = null,
    onOptionsChanged: (SearchToolbarOptions) -> Unit,
    onFirstPage: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val collapseStateDescription by remember(chromeState) {
        derivedStateOf { collapsibleHeaderUiState(chromeState.collapseFraction) }
    }
    Column(
        modifier = modifier
            .onSizeChanged(onMeasured)
            // Use layout offset instead of a graphics layer so Android text selection
            // toolbars anchor to the real on-screen position of the editable field.
            .offset { IntOffset(x = 0, y = chromeState.offsetPx.roundToInt()) }
            .semantics { stateDescription = collapseStateDescription }
            .testTag(chromeTestTag)
    ) {
        SearchToolbar(
            keyword = keyword,
            onKeywordChange = onKeywordChange,
            placeholder = placeholder,
            searchFieldReadOnly = searchFieldReadOnly,
            onSearchFieldClick = onSearchFieldClick,
            selectedFilter = selectedFilter,
            selectedOrder = selectedOrder,
            selectedCollectedSort = selectedCollectedSort,
            hasSubtitle = hasSubtitle,
            allAges = allAges,
            selectedLocale = selectedLocale,
            filterControlsLocked = filterControlsLocked,
            searchSubmitLocked = searchSubmitLocked,
            showSearchSpinner = showSearchSpinner,
            inputTestTag = inputTestTag,
            clearButtonTestTag = clearButtonTestTag,
            submitButtonTestTag = submitButtonTestTag,
            inputFocusRequester = inputFocusRequester,
            onSearchSubmit = onSearchSubmit,
            onClearKeyword = onClearKeyword,
            onOptionsChanged = onOptionsChanged,
            rightPanelToggle = rightPanelToggle
        )
        if (showPagination) {
            SearchPaginationHeader(
                page = page,
                canGoPrev = canGoPrev,
                canGoNext = canGoNext,
                controlsLocked = controlsLocked,
                onFirstPage = onFirstPage,
                onPrev = onPrev,
                onNext = onNext
            )
        }
    }
}

internal data class SearchToolbarOptions(
    val scope: SearchFilterOption,
    val order: SearchSortOption,
    val collectedSort: SearchCollectedSortOption,
    val hasSubtitle: Boolean,
    val allAges: Boolean,
    val locale: String
)

@Composable
internal fun SearchToolbar(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    placeholder: String = DefaultSearchPlaceholder,
    searchFieldReadOnly: Boolean = false,
    onSearchFieldClick: (() -> Unit)? = null,
    selectedFilter: SearchFilterOption,
    selectedOrder: SearchSortOption = SearchSortOption.Trend,
    selectedCollectedSort: SearchCollectedSortOption = SearchCollectedSortOption.ReleaseNew,
    hasSubtitle: Boolean = false,
    allAges: Boolean = false,
    selectedLocale: String,
    filterControlsLocked: Boolean,
    searchSubmitLocked: Boolean,
    showSearchSpinner: Boolean,
    inputTestTag: String = SEARCH_INPUT_TAG,
    clearButtonTestTag: String = SEARCH_CLEAR_BUTTON_TAG,
    submitButtonTestTag: String = SEARCH_SUBMIT_BUTTON_TAG,
    inputFocusRequester: FocusRequester? = null,
    onSearchSubmit: () -> Unit,
    onClearKeyword: (() -> Unit)? = null,
    onOptionsChanged: (SearchToolbarOptions) -> Unit,
    rightPanelToggle: (@Composable (Modifier) -> Unit)? = null
) {
    val colorScheme = AsmrTheme.colorScheme
    var filterMenuExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val options = SearchToolbarOptions(
        scope = selectedFilter,
        order = selectedOrder,
        collectedSort = selectedCollectedSort,
        hasSubtitle = hasSubtitle,
        allAges = allAges,
        locale = selectedLocale
    )
    val supportsWorkFilters = selectedFilter.supportsWorkFilters
    val supportsSortAndLanguageOptions = selectedFilter.supportsSortAndLanguageOptions
    val activeWorkFilterCount = if (supportsWorkFilters) {
        (if (hasSubtitle) 1 else 0) + (if (allAges) 1 else 0)
    } else {
        0
    }
    val dropdownContainerColor = lerp(
        colorScheme.surface,
        colorScheme.primarySoft,
        if (colorScheme.isDark) 0.16f else 0.26f
    ).copy(alpha = if (colorScheme.isDark) 0.95f else 0.97f)
        .compositeOver(colorScheme.background)

    LaunchedEffect(filterControlsLocked, searchSubmitLocked, supportsSortAndLanguageOptions) {
        if (filterControlsLocked || searchSubmitLocked || !supportsSortAndLanguageOptions) {
            filterMenuExpanded = false
            sortMenuExpanded = false
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SearchPageHorizontalPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CustomSearchBar(
            value = keyword,
            onValueChange = onKeywordChange,
            placeholder = placeholder,
            modifier = Modifier.weight(1f),
            readOnly = searchFieldReadOnly,
            onFieldClick = onSearchFieldClick,
            focusRequester = inputFocusRequester,
            inputTestTag = inputTestTag,
            leadingIcon = {
                Box {
                    TextButton(
                        onClick = { filterMenuExpanded = true },
                        enabled = !filterControlsLocked,
                        modifier = Modifier
                            .height(32.dp)
                            .semantics {
                                stateDescription = when {
                                    !supportsWorkFilters -> "当前范围不支持作品筛选"
                                    activeWorkFilterCount == 0 -> "未启用作品筛选"
                                    activeWorkFilterCount == 1 -> "已启用 1 项作品筛选"
                                    else -> "已启用 2 项作品筛选"
                                }
                            }
                            .testTag(SEARCH_SCOPE_BUTTON_TAG),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = colorScheme.primary,
                            disabledContentColor = colorScheme.textTertiary
                        )
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box {
                                SearchFilterIconView(
                                    icon = selectedFilter.icon,
                                    tint = if (filterControlsLocked) {
                                        colorScheme.textTertiary
                                    } else {
                                        colorScheme.primary
                                    },
                                    modifier = Modifier.size(14.dp)
                                )
                                if (activeWorkFilterCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(5.dp)
                                            .background(colorScheme.primaryStrong, CircleShape)
                                    )
                                }
                            }
                            Text(
                                text = selectedFilter.label,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = filterMenuExpanded,
                        onDismissRequest = { filterMenuExpanded = false },
                        modifier = Modifier.background(dropdownContainerColor)
                    ) {
                        SearchFilterOption.entries.forEachIndexed { index, option ->
                            if (index > 0) {
                                SearchMenuDivider()
                            }
                            DropdownMenuItem(
                                modifier = Modifier.testTag(
                                    "${SEARCH_SCOPE_OPTION_TAG_PREFIX}_${option.name}"
                                ),
                                text = {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        SearchFilterIconView(
                                            icon = option.icon,
                                            tint = if (option == selectedFilter) {
                                                colorScheme.primary
                                            } else {
                                                colorScheme.textSecondary
                                            },
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = option.label,
                                            color = if (option == selectedFilter) {
                                                colorScheme.primary
                                            } else {
                                                colorScheme.textPrimary
                                            }
                                        )
                                    }
                                },
                                onClick = {
                                    filterMenuExpanded = false
                                    if (option != selectedFilter) {
                                        onOptionsChanged(
                                            options.copy(scope = option)
                                        )
                                    }
                                }
                            )
                        }

                        if (supportsWorkFilters) {
                            SearchMenuSectionLabel("作品筛选")
                            SearchCheckableMenuItem(
                                label = "有字幕",
                                icon = Icons.Rounded.Subtitles,
                                selected = hasSubtitle,
                                testTag = SEARCH_HAS_SUBTITLE_OPTION_TAG,
                                onClick = {
                                    onOptionsChanged(options.copy(hasSubtitle = !hasSubtitle))
                                }
                            )
                            SearchMenuDivider()
                            SearchCheckableMenuItem(
                                label = "全年龄",
                                icon = Icons.Rounded.FamilyRestroom,
                                selected = allAges,
                                testTag = SEARCH_ALL_AGES_OPTION_TAG,
                                onClick = {
                                    onOptionsChanged(options.copy(allAges = !allAges))
                                }
                            )
                        }

                    }
                }
            },
            trailingIcon = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (keyword.isNotBlank()) {
                        IconButton(
                            onClick = { onClearKeyword?.invoke() ?: onKeywordChange("") },
                            enabled = !searchSubmitLocked,
                            modifier = Modifier
                                .size(28.dp)
                                .testTag(clearButtonTestTag)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    if (supportsSortAndLanguageOptions) {
                        Box {
                            TextButton(
                                onClick = { sortMenuExpanded = true },
                                enabled = !filterControlsLocked,
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 1.dp, minHeight = 30.dp)
                                    .height(30.dp)
                                    .testTag(SEARCH_SORT_BUTTON_TAG),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = colorScheme.primary,
                                    disabledContentColor = colorScheme.textTertiary
                                )
                            ) {
                                Text(
                                    text = if (selectedFilter.isCollectedOnly) {
                                        selectedCollectedSort.label
                                    } else {
                                        selectedOrder.label
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            }
                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false },
                                modifier = Modifier.background(dropdownContainerColor)
                            ) {
                                if (selectedFilter.isCollectedOnly) {
                                    SearchCollectedSortOption.entries.forEachIndexed { index, option ->
                                        if (index > 0) {
                                            SearchMenuDivider()
                                        }
                                        ActiveDropdownMenuItem(
                                            label = option.label,
                                            selected = option == selectedCollectedSort,
                                            testTag = "${SEARCH_COLLECTED_SORT_OPTION_TAG_PREFIX}_${option.name}",
                                            activeColor = colorScheme.primary,
                                            inactiveColor = colorScheme.textPrimary,
                                            onClick = {
                                                sortMenuExpanded = false
                                                if (option != selectedCollectedSort) {
                                                    onOptionsChanged(options.copy(collectedSort = option))
                                                }
                                            }
                                        )
                                    }
                                } else {
                                    SearchSortOption.entries.forEachIndexed { index, option ->
                                        if (index > 0) {
                                            SearchMenuDivider()
                                        }
                                        ActiveDropdownMenuItem(
                                            label = option.label,
                                            selected = option == selectedOrder,
                                            testTag = "${SEARCH_SORT_OPTION_TAG_PREFIX}_${option.name}",
                                            activeColor = colorScheme.primary,
                                            inactiveColor = colorScheme.textPrimary,
                                            onClick = {
                                                sortMenuExpanded = false
                                                if (option != selectedOrder) {
                                                    onOptionsChanged(options.copy(order = option))
                                                }
                                            }
                                        )
                                    }

                                    SearchMenuSectionLabel("作品语言")
                                    SearchLocaleOptions.forEachIndexed { index, (locale, label) ->
                                        if (index > 0) {
                                            SearchMenuDivider()
                                        }
                                        ActiveDropdownMenuItem(
                                            label = label,
                                            selected = locale == selectedLocale.trim(),
                                            testTag = "${SEARCH_LANGUAGE_OPTION_TAG_PREFIX}_$locale",
                                            activeColor = colorScheme.primary,
                                            inactiveColor = colorScheme.textPrimary,
                                            onClick = {
                                                sortMenuExpanded = false
                                                if (locale != selectedLocale.trim()) {
                                                    onOptionsChanged(options.copy(locale = locale))
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    IconButton(
                        onClick = onSearchSubmit,
                        enabled = !searchSubmitLocked,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag(submitButtonTestTag)
                    ) {
                        if (showSearchSpinner) {
                            EaraLogoLoadingIndicator(
                                size = 14.dp,
                                tint = colorScheme.primary,
                                modifier = Modifier.testTag(SEARCH_SUBMIT_SPINNER_TAG)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = if (!searchSubmitLocked) {
                                    colorScheme.primary
                                } else {
                                    colorScheme.textTertiary
                                },
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = ImeAction.Search
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSearch = { onSearchSubmit() }
            )
        )
        if (rightPanelToggle != null) {
            Spacer(modifier = Modifier.width(8.dp))
            rightPanelToggle(Modifier.size(50.dp))
        }
    }
}

private val SearchLocaleOptions = listOf(
    "ja_JP" to "日语",
    "zh_CN" to "简中",
    "zh_TW" to "繁中"
)

@Composable
private fun SearchMenuSectionLabel(label: String) {
    val colorScheme = AsmrTheme.colorScheme
    HorizontalDivider(
        modifier = Modifier.padding(top = 4.dp, start = 8.dp, end = 8.dp),
        thickness = 0.5.dp,
        color = colorScheme.textSecondary.copy(alpha = 0.24f)
    )
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = colorScheme.textSecondary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun SearchMenuDivider() {
    val colorScheme = AsmrTheme.colorScheme
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 8.dp),
        thickness = 0.5.dp,
        color = colorScheme.textSecondary.copy(alpha = 0.2f)
    )
}

@Composable
private fun SearchCheckableMenuItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    DropdownMenuItem(
        text = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) colorScheme.primary else colorScheme.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = label,
                    color = if (selected) colorScheme.primary else colorScheme.textPrimary
                )
            }
        },
        onClick = onClick,
        modifier = Modifier
            .semantics {
                this.selected = selected
                stateDescription = if (selected) "已筛选" else "未筛选"
            }
            .testTag(testTag)
    )
}

@Composable
internal fun SearchPaginationHeader(
    page: Int,
    canGoPrev: Boolean,
    canGoNext: Boolean,
    controlsLocked: Boolean,
    onFirstPage: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val isDark = colorScheme.isDark
    val canGoFirst = canGoPrev
    val paginationContainerColor = lerp(
        colorScheme.surface,
        colorScheme.primarySoft,
        if (isDark) 0.06f else 0.10f
    ).copy(alpha = if (isDark) 0.93f else 0.95f)
        .compositeOver(colorScheme.background)
    val paginationBorderColor = if (isDark) {
        Color.White.copy(alpha = 0.14f)
    } else {
        colorScheme.primaryStrong.copy(alpha = 0.14f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SearchPageHorizontalPadding, vertical = 1.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = if (isDark) 10.dp else 6.dp,
                    shape = RoundedCornerShape(12.dp),
                    spotColor = if (isDark) Color.Black.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.25f),
                    ambientColor = if (isDark) Color.Black.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.25f)
                )
                .then(
                    Modifier.border(
                        width = 1.dp,
                        color = paginationBorderColor,
                        shape = RoundedCornerShape(12.dp)
                    )
                )
                .clip(RoundedCornerShape(12.dp))
                .background(paginationContainerColor)
                .testTag(SEARCH_PAGINATION_TAG)
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .consumeTapThrough()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SearchPaginationIconButton(
                            onClick = onFirstPage,
                            enabled = canGoFirst && !controlsLocked,
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = "回到第一页",
                            modifier = Modifier.testTag(SEARCH_FIRST_PAGE_BUTTON_TAG)
                        )
                        SearchPaginationIconButton(
                            onClick = onPrev,
                            enabled = canGoPrev && !controlsLocked,
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "上一页",
                            modifier = Modifier.testTag(SEARCH_PREV_BUTTON_TAG)
                        )
                    }
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "第 ${page.coerceAtLeast(1)} 页",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) colorScheme.textPrimary else Color.Black
                    )
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    SearchPaginationIconButton(
                        onClick = onNext,
                        enabled = canGoNext && !controlsLocked,
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = "下一页",
                        modifier = Modifier.testTag(SEARCH_NEXT_BUTTON_TAG)
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchPaginationIconButton(
    onClick: () -> Unit,
    enabled: Boolean,
    imageVector: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val colorScheme = AsmrTheme.colorScheme
    Box(
        modifier = modifier
            .size(30.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = if (enabled) colorScheme.primary else colorScheme.textTertiary,
            modifier = Modifier.size(17.dp)
        )
    }
}
