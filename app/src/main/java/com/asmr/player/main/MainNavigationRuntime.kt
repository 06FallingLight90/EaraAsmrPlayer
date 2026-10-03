package com.asmr.player.main

import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.navigation.NavHostController
import com.asmr.player.performance.UiFrameWorkCoordinator
import com.asmr.player.ui.nav.Routes
import com.asmr.player.ui.search.SearchAssistSearchRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * 主页面 primary 路由运行时：openPrimaryRoute 的帧关键段编排、pending 路由与
 * Pager 的双向同步、滚动中的输入收起。navigationJob 需作为快照状态参与 LaunchedEffect key。
 */
@Stable
internal class PrimaryNavigationState(
    val pendingRoute: MutableState<String?>,
    val navigationJobState: MutableState<Job?>,
    private val navController: NavHostController,
    private val scope: CoroutineScope,
    val pagerState: PagerState,
    internal val currentPrimaryRouteState: State<String?>,
    private val pendingRouteState: State<String?>
) {
    var navigationJob by navigationJobState
    private var navigationRequestId = 0L

    internal fun navigateToPrimaryRoute(route: String) {
        navController.navigatePrimaryRoute(route)
    }

    fun openPrimaryRoute(route: String, pagerRoutes: List<String>) {
        val targetPage = pagerRoutes.indexOf(route)
        navigationJob?.cancel()
        navigationRequestId += 1L
        val requestId = navigationRequestId
        if (targetPage >= 0 && currentPrimaryRouteState.value != null) {
            pendingRoute.value = route
            UiFrameWorkCoordinator.markFrameCritical(
                PrimaryPageSwitchDurationMs + PrimaryPageSwitchQuietTailMs
            )
            navigationJob = scope.launch {
                var completed = false
                try {
                    pagerState.stopScroll(MutatePriority.PreventUserInput)
                    // 相邻页已由 Pager 保留在屏外。先提交 active/data-active 状态，让它在
                    // 可见动画前完成一次状态恢复，避免数据订阅与动画首帧争抢主线程。
                    withFrameNanos { }
                    val currentPage = pagerState.currentPage
                    resolvePrimaryPagerApproachPage(
                        currentPage = currentPage,
                        targetPage = targetPage
                    )?.let { approachPage ->
                        pagerState.scrollToPage(approachPage)
                    }
                    UiFrameWorkCoordinator.markFrameCritical(
                        PrimaryPageSwitchDurationMs + PrimaryPageSwitchQuietTailMs
                    )
                    pagerState.animateScrollToPage(
                        page = targetPage,
                        animationSpec = tween(
                            durationMillis = PrimaryPageSwitchDurationMs,
                            easing = PrimaryPageSwitchEasing
                        )
                    )
                    if (currentPrimaryRouteState.value != route) {
                        navController.navigatePrimaryRoute(route)
                    }
                    completed = true
                } finally {
                    if (navigationRequestId == requestId) {
                        navigationJob = null
                        if (!completed && pendingRouteState.value == route) {
                            pendingRoute.value = null
                        }
                    }
                }
            }
        } else {
            navigationJob = null
            pendingRoute.value = null
            navController.navigatePrimaryRoute(route)
        }
    }
}

@Composable
internal fun rememberPrimaryNavigationState(
    navController: NavHostController,
    scope: CoroutineScope,
    pagerState: PagerState,
    currentPrimaryRoute: String?
): PrimaryNavigationState {
    val pendingRoute = remember { mutableStateOf<String?>(null) }
    val navigationJobState = remember { mutableStateOf<Job?>(null) }
    val currentPrimaryRouteState = rememberUpdatedState(currentPrimaryRoute)
    val pendingRouteState = rememberUpdatedState(pendingRoute.value)
    val state = remember(navController, scope, pagerState) {
        PrimaryNavigationState(
            pendingRoute = pendingRoute,
            navigationJobState = navigationJobState,
            navController = navController,
            scope = scope,
            pagerState = pagerState,
            currentPrimaryRouteState = currentPrimaryRouteState,
            pendingRouteState = pendingRouteState
        )
    }
    DisposableEffect(state) {
        onDispose {
            state.navigationJob?.cancel()
        }
    }
    return state
}

@Composable
internal fun PrimaryNavigationEffects(
    state: PrimaryNavigationState,
    currentPrimaryRoute: String?,
    primaryPagerRoutes: List<String>,
    focusManager: FocusManager,
    keyboardController: SoftwareKeyboardController?
) {
    LaunchedEffect(
        currentPrimaryRoute,
        primaryPagerRoutes,
        state.pendingRoute.value,
        state.navigationJob
    ) {
        val route = currentPrimaryRoute ?: return@LaunchedEffect
        val pendingRoute = state.pendingRoute.value
        if (pendingRoute != null) {
            val pendingPage = primaryPagerRoutes.indexOf(pendingRoute)
            if (
                shouldClearPendingPrimaryNavigationRoute(
                    currentRoute = route,
                    pendingRoute = pendingRoute,
                    navigationInProgress = state.navigationJob != null,
                    pendingPage = pendingPage,
                    settledPage = state.pagerState.settledPage
                )
            ) {
                state.pendingRoute.value = null
            } else if (
                route == pendingRoute &&
                state.navigationJob == null &&
                shouldSyncPrimaryPagerToRoute(
                    targetPage = pendingPage,
                    settledPage = state.pagerState.settledPage
                )
            ) {
                state.pagerState.stopScroll(MutatePriority.PreventUserInput)
                state.pagerState.scrollToPage(pendingPage)
            }
            return@LaunchedEffect
        }
        val targetPage = primaryPagerRoutes.indexOf(route)
        if (
            shouldSyncPrimaryPagerToRoute(
                targetPage = targetPage,
                settledPage = state.pagerState.settledPage
            )
        ) {
            state.pagerState.stopScroll(MutatePriority.PreventUserInput)
            state.pagerState.scrollToPage(targetPage)
        }
    }

    LaunchedEffect(state.pagerState) {
        snapshotFlow { state.pagerState.isScrollInProgress }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
            }
    }

    LaunchedEffect(state.pagerState, primaryPagerRoutes) {
        snapshotFlow { state.pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                if (state.pendingRoute.value != null) return@collect
                val currentPrimary = state.currentPrimaryRouteState.value ?: return@collect
                val targetRoute = primaryPagerRoutes.getOrNull(page) ?: return@collect
                if (targetRoute != currentPrimary) {
                    state.navigateToPrimaryRoute(targetRoute)
                }
            }
    }
}

// ——————————————————————————————————————————————
// 触摸阻断 / 详情页导航挂起状态
// ——————————————————————————————————————————————

@Stable
internal class NavTouchBlockState(
    val blockNavTouches: MutableState<Boolean>,
    val lastRoute: MutableState<String?>,
    val seq: MutableIntState,
    val pendingDetailNavigation: MutableState<Boolean>,
    val pendingDetailNavigationSeq: MutableIntState,
    val cancelPendingDetailNavigation: MutableState<Boolean>
) {
    var blockTouches by blockNavTouches
    var lastRouteValue by lastRoute
    var seqValue by seq
    var pendingDetail by pendingDetailNavigation
    var pendingDetailSeq by pendingDetailNavigationSeq
    var cancelPendingDetail by cancelPendingDetailNavigation
}

@Composable
internal fun rememberNavTouchBlockState(initialRoute: String?): NavTouchBlockState {
    val blockNavTouches = remember { mutableStateOf(false) }
    val lastRoute = remember { mutableStateOf(initialRoute) }
    val seq = remember { mutableIntStateOf(0) }
    val pendingDetailNavigation = remember { mutableStateOf(false) }
    val pendingDetailNavigationSeq = remember { mutableIntStateOf(0) }
    val cancelPendingDetailNavigation = remember { mutableStateOf(false) }
    return remember {
        NavTouchBlockState(
            blockNavTouches = blockNavTouches,
            lastRoute = lastRoute,
            seq = seq,
            pendingDetailNavigation = pendingDetailNavigation,
            pendingDetailNavigationSeq = pendingDetailNavigationSeq,
            cancelPendingDetailNavigation = cancelPendingDetailNavigation
        )
    }
}

@Composable
internal fun NavTouchBlockEffect(
    state: NavTouchBlockState,
    currentRoute: String?,
    currentPrimaryRoute: String?,
    primaryPagerRoutes: List<String>,
    navController: NavHostController,
    volume: HardwareVolumeOverlayState
) {
    LaunchedEffect(currentRoute, currentPrimaryRoute) {
        volume.showOverlay = false
        volume.interacting = false
        volume.bounds = null
        if (state.pendingDetail && currentRoute?.startsWith("album_detail") == true) {
            state.pendingDetail = false
        }
        if (state.cancelPendingDetail && currentRoute?.startsWith("album_detail") == true) {
            state.cancelPendingDetail = false
            navController.popBackStack()
            return@LaunchedEffect
        }
        val normalizedCurrentRoute = currentPrimaryRoute ?: currentRoute
        val last = state.lastRouteValue
        val seq = ++state.seqValue
        val isPrimaryPagerSwitch =
            last != null &&
                normalizedCurrentRoute != null &&
                last != normalizedCurrentRoute &&
                last in primaryPagerRoutes &&
                normalizedCurrentRoute in primaryPagerRoutes
        val isReturningToPrimaryPage =
            last != null &&
                normalizedCurrentRoute != null &&
                last != normalizedCurrentRoute &&
                last !in primaryPagerRoutes &&
                normalizedCurrentRoute in primaryPagerRoutes
        if (
            last != null &&
            normalizedCurrentRoute != null &&
            last != normalizedCurrentRoute &&
            !isPrimaryPagerSwitch &&
            !isReturningToPrimaryPage
        ) {
            state.blockTouches = true
            try {
                delay(SecondaryPageTouchBlockDurationMs.toLong())
            } finally {
                if (state.seqValue == seq) {
                    state.blockTouches = false
                }
            }
        } else {
            state.blockTouches = false
        }
        state.lastRouteValue = normalizedCurrentRoute
    }
}

// ——————————————————————————————————————————————
// primary 路由滚动到顶信号
// ——————————————————————————————————————————————

@Stable
internal class ScrollToTopSignals {
    var library by mutableLongStateOf(0L)
    var search by mutableLongStateOf(0L)
    var hotListening by mutableLongStateOf(0L)
    var favorites by mutableLongStateOf(0L)
    var playlists by mutableLongStateOf(0L)
    var groups by mutableLongStateOf(0L)
    var settings by mutableLongStateOf(0L)
    var downloads by mutableLongStateOf(0L)

    fun trigger(route: String) {
        when (route) {
            Routes.Library -> library += 1L
            Routes.Search -> search += 1L
            Routes.HotListening -> hotListening += 1L
            "playlist_system/favorites" -> favorites += 1L
            "playlists" -> playlists += 1L
            "groups" -> groups += 1L
            "settings" -> settings += 1L
        }
    }
}

// ——————————————————————————————————————————————
// 已提交搜索条件状态（saveable）
// ——————————————————————————————————————————————

@Stable
internal class SubmittedSearchState(
    keywordState: MutableState<String>,
    orderNameState: MutableState<String>,
    purchasedOnlyState: MutableState<Boolean>,
    presaleOnlyState: MutableState<Boolean>,
    chineseTranslatedOnlyState: MutableState<Boolean>,
    collectedOnlyState: MutableState<Boolean>,
    hasSubtitleState: MutableState<Boolean>,
    allAgesState: MutableState<Boolean>,
    collectedSortNameState: MutableState<String>,
    localeState: MutableState<String>,
    signalState: MutableLongState
) {
    var keyword by keywordState
    var orderName by orderNameState
    var purchasedOnly by purchasedOnlyState
    var presaleOnly by presaleOnlyState
    var chineseTranslatedOnly by chineseTranslatedOnlyState
    var collectedOnly by collectedOnlyState
    var hasSubtitle by hasSubtitleState
    var allAges by allAgesState
    var collectedSortName by collectedSortNameState
    var locale by localeState
    var signal by signalState

    fun apply(request: SearchAssistSearchRequest) {
        keyword = request.keyword
        orderName = request.orderName
        purchasedOnly = request.purchasedOnly
        presaleOnly = request.presaleOnly
        chineseTranslatedOnly = request.chineseTranslatedOnly
        collectedOnly = request.collectedOnly
        hasSubtitle = request.hasSubtitle
        allAges = request.allAges
        collectedSortName = request.collectedSortName
        locale = request.locale
        signal = System.currentTimeMillis()
    }

    fun apply(values: MainSubmittedSearchValues) {
        keyword = values.keyword
        orderName = values.orderName
        purchasedOnly = values.purchasedOnly
        presaleOnly = values.presaleOnly
        chineseTranslatedOnly = values.chineseTranslatedOnly
        collectedOnly = values.collectedOnly
        hasSubtitle = values.hasSubtitle
        allAges = values.allAges
        collectedSortName = values.collectedSortName
        locale = values.locale
        signal = values.signal
    }
}

@Composable
internal fun rememberSubmittedSearchState(): SubmittedSearchState {
    val keyword = rememberSaveable { mutableStateOf("") }
    val orderName = rememberSaveable { mutableStateOf(SearchAssistSearchRequest().orderName) }
    val purchasedOnly = rememberSaveable { mutableStateOf(SearchAssistSearchRequest().purchasedOnly) }
    val presaleOnly = rememberSaveable { mutableStateOf(SearchAssistSearchRequest().presaleOnly) }
    val chineseTranslatedOnly = rememberSaveable {
        mutableStateOf(SearchAssistSearchRequest().chineseTranslatedOnly)
    }
    val collectedOnly = rememberSaveable { mutableStateOf(SearchAssistSearchRequest().collectedOnly) }
    val hasSubtitle = rememberSaveable { mutableStateOf(SearchAssistSearchRequest().hasSubtitle) }
    val allAges = rememberSaveable { mutableStateOf(SearchAssistSearchRequest().allAges) }
    val collectedSortName = rememberSaveable {
        mutableStateOf(SearchAssistSearchRequest().collectedSortName)
    }
    val locale = rememberSaveable { mutableStateOf(SearchAssistSearchRequest().locale) }
    val signal = rememberSaveable { mutableLongStateOf(0L) }
    return remember {
        SubmittedSearchState(
            keywordState = keyword,
            orderNameState = orderName,
            purchasedOnlyState = purchasedOnly,
            presaleOnlyState = presaleOnly,
            chineseTranslatedOnlyState = chineseTranslatedOnly,
            collectedOnlyState = collectedOnly,
            hasSubtitleState = hasSubtitle,
            allAgesState = allAges,
            collectedSortNameState = collectedSortName,
            localeState = locale,
            signalState = signal
        )
    }
}
