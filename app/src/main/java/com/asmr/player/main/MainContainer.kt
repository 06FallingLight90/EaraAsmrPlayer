package com.asmr.player.main

import com.asmr.player.translation.LocalPageTranslationHeader
import com.asmr.player.translation.PageTranslationHeaderState
import android.view.Choreographer
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.rounded.*
import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import com.asmr.player.ui.common.core.isCompactWidth
import com.asmr.player.ui.common.core.isLandscape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.asmr.player.ui.library.AlbumDetailViewModel
import com.asmr.player.ui.library.CloudSyncSelectionDialog
import com.asmr.player.ui.library.LibraryViewModel
import com.asmr.player.performance.UiFrameWorkCoordinator
import com.asmr.player.ui.player.NowPlayingMotionLayout
import com.asmr.player.ui.player.NowPlayingMotionSpec
import com.asmr.player.ui.player.PlayerViewModel
import com.asmr.player.ui.player.rememberCoverDragPreviewState
import com.asmr.player.ui.player.rememberCoverMotionState
import com.asmr.player.ui.sidepanel.LocalRightPanelExpandedState
import com.asmr.player.ui.downloads.DownloadsViewModel
import com.asmr.player.hotlistening.ListeningTracker
import com.asmr.player.ui.search.SEARCH_ASSIST_RESULT_CHINESE_TRANSLATED_ONLY_KEY
import com.asmr.player.ui.search.SEARCH_ASSIST_RESULT_COLLECTED_ONLY_KEY
import com.asmr.player.ui.search.SEARCH_ASSIST_RESULT_COLLECTED_SORT_KEY
import com.asmr.player.ui.search.SEARCH_ASSIST_RESULT_HAS_SUBTITLE_KEY
import com.asmr.player.ui.search.SEARCH_ASSIST_RESULT_ALL_AGES_KEY
import com.asmr.player.ui.search.SEARCH_ASSIST_RESULT_KEY
import com.asmr.player.ui.search.SEARCH_ASSIST_RESULT_LOCALE_KEY
import com.asmr.player.ui.search.SEARCH_ASSIST_RESULT_ORDER_KEY
import com.asmr.player.ui.search.SEARCH_ASSIST_RESULT_PRESALE_ONLY_KEY
import com.asmr.player.ui.search.SEARCH_ASSIST_RESULT_PURCHASED_ONLY_KEY
import com.asmr.player.ui.search.SEARCH_ASSIST_RESULT_SIGNAL_KEY
import com.asmr.player.ui.search.SearchAssistSearchRequest
import com.asmr.player.ui.settings.AppUpdateState
import com.asmr.player.ui.settings.SettingsViewModel
import com.asmr.player.ui.settings.UpdateCheckSource
import com.asmr.player.ui.common.dialog.FlatTextFieldDialog
import com.asmr.player.ui.common.core.resolveMainPageBackgroundColor
import com.asmr.player.ui.drawer.DrawerStatusViewModel
import com.asmr.player.ui.nav.AppNavigator
import com.asmr.player.ui.nav.Routes
import com.asmr.player.ui.nav.bottomChromeNavItems
import com.asmr.player.ui.nav.bottomChromeOverlayHeight
import com.asmr.player.ui.nav.isPrimaryRoute
import com.asmr.player.ui.nav.resolvePrimaryRoute
import com.asmr.player.ui.common.list.LocalBottomOverlayPadding
import kotlinx.coroutines.launch

import androidx.compose.ui.platform.LocalContext
import com.asmr.player.ui.theme.AsmrTheme
import android.os.Build
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.animation.*
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalView

import com.asmr.player.ui.player.MiniPlayerDisplayMode

import com.asmr.player.data.local.datastore.SettingsDataStore
import com.asmr.player.data.settings.CoverPreviewMode
import com.asmr.player.data.settings.LyricsPageSettings
import com.asmr.player.data.settings.NowPlayingHomeLayoutMode
import com.asmr.player.data.settings.NowPlayingLyricsSettings
import com.asmr.player.util.MessageManager
import com.asmr.player.ui.common.list.StableWindowInsets
import com.asmr.player.ui.theme.dynamicPageContainerColor
import com.asmr.player.ui.update.AppUpdateInstallResult
import com.asmr.player.ui.update.launchDownloadedApkInstall
import com.asmr.player.ui.common.audio.AppVolumeWarningSessionState
import com.asmr.player.ui.common.audio.rememberAppVolumeWarningSessionState
import com.asmr.player.ui.common.audio.rememberCurrentAudioOutputRouteKind
import com.asmr.player.service.AudioOutputRouteKind
import com.asmr.player.service.PlaybackService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.asmr.player.domain.model.AppVolume

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@androidx.annotation.OptIn(UnstableApi::class)
fun MainContainer(
    windowSizeClass: WindowSizeClass,
    playerViewModel: PlayerViewModel,
    libraryViewModel: LibraryViewModel,
    settingsDataStore: SettingsDataStore,
    messageManager: MessageManager,
    listeningTracker: ListeningTracker,
    recentAlbumsPanelExpandedInitial: Boolean,
    startRouteFromIntent: String?,
    onShowQueue: () -> Unit,
    onShowSleepTimer: () -> Unit,
    onContentReady: () -> Unit,
    showMiniPlayerBar: Boolean,
    coverBackgroundEnabled: Boolean,
    coverBackgroundClarity: Float,
    coverPreviewMode: CoverPreviewMode,
    nowPlayingHomeLayoutMode: NowPlayingHomeLayoutMode,
    nowPlayingHomeLayoutHintDismissed: Boolean?,
    nowPlayingLyricsSettings: NowPlayingLyricsSettings,
    lyricsPageSettings: LyricsPageSettings,
    forceImmersive: Boolean,
    volumeKeyEventTick: Long
) {
    val activityViewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current)
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val mainRootView = LocalView.current
    var albumDetailStackPopTargetEntryId by remember { mutableStateOf<String?>(null) }
    var albumDetailPageOffsetReader by remember { mutableStateOf<(() -> Float)?>(null) }
    var albumDetailEnterPreparing by remember { mutableStateOf(false) }
    var albumDetailEnterRequestId by remember { mutableLongStateOf(0L) }
    val albumDetailInsetsDispatchSuppressor = remember(mainRootView) {
        InsetsAnimationDispatchSuppressor(mainRootView.rootView)
    }
    DisposableEffect(albumDetailInsetsDispatchSuppressor) {
        onDispose { albumDetailInsetsDispatchSuppressor.clear() }
    }
    val navigator = remember(navController, mainRootView, albumDetailInsetsDispatchSuppressor) {
        AppNavigator(navController) scheduler@{ navigation ->
            if (albumDetailEnterPreparing) return@scheduler
            albumDetailEnterPreparing = true
            val insetsSuppressionToken = albumDetailInsetsDispatchSuppressor.acquire()
            val requestId = ++albumDetailEnterRequestId
            UiFrameWorkCoordinator.markFrameCritical(
                SecondaryPageEnterDurationMs.toLong()
            )
            mainRootView.postDelayed({
                albumDetailInsetsDispatchSuppressor.release(insetsSuppressionToken)
            }, SecondaryPageEnterDurationMs + 180L)
            Choreographer.getInstance().postFrameCallback navigationFrame@{
                if (albumDetailEnterRequestId != requestId) return@navigationFrame
                try {
                    navigation()
                } finally {
                    albumDetailEnterPreparing = false
                }
            }
        }
    }
    val hasPreviousBackStackEntry = navController.previousBackStackEntry != null
    val currentPlaylistSystemType = navBackStackEntry?.arguments?.getString("type")
    val startRoute = remember(startRouteFromIntent) {
        startRouteFromIntent?.trim().orEmpty()
    }
    val initialDestination = remember(startRoute) {
        if (startRoute == Routes.Search) Routes.Search else Routes.Library
    }
    var lastPrimaryRoute by rememberSaveable { mutableStateOf(initialDestination) }
    val currentPrimaryRoute = resolveCurrentPrimaryDestinationRoute(
        currentRoute = currentRoute,
        playlistSystemType = currentPlaylistSystemType
    )
    val activePrimaryRoute = resolvePrimaryRoute(
        currentRoute = currentRoute,
        lastPrimaryRoute = lastPrimaryRoute,
        playlistSystemType = currentPlaylistSystemType
    )
    LaunchedEffect(currentRoute) {
        UiFrameWorkCoordinator.markFrameCritical(
            maxOf(SecondaryPageEnterDurationMs, SecondaryPageExitDurationMs) + 120L
        )
        if (!isAlbumDetailRoute(currentRoute)) {
            albumDetailStackPopTargetEntryId = null
            albumDetailPageOffsetReader = null
        }
    }
    val bottomNavItems = remember { bottomChromeNavItems() }
    val storedMiniPlayerDisplayMode by settingsDataStore.miniPlayerDisplayMode.collectAsStateWithLifecycle(
        initialValue = MiniPlayerDisplayMode.CoverOnly.name
    )
    var miniPlayerDisplayMode by rememberSaveable { mutableStateOf(MiniPlayerDisplayMode.CoverOnly) }
    var miniPlayerPlayFeedbackSignal by remember { mutableLongStateOf(0L) }
    fun requestMiniPlayerPlayFeedback() {
        miniPlayerPlayFeedbackSignal += 1L
    }
    val primaryPagerRoutes = remember(bottomNavItems) { bottomNavItems.map { it.route } }
    val primaryPagerBeyondBoundsPageCount = remember(primaryPagerRoutes) {
        resolvePrimaryPagerBeyondBoundsPageCount(primaryPagerRoutes.size)
    }
    val initialPrimaryPage = remember(initialDestination, primaryPagerRoutes) {
        primaryPagerRoutes.indexOf(initialDestination).takeIf { it >= 0 } ?: 0
    }
    val primaryPagerState = rememberPagerState(
        initialPage = initialPrimaryPage,
        pageCount = { primaryPagerRoutes.size }
    )
    val primaryPagerFlingBehavior = PagerDefaults.flingBehavior(
        state = primaryPagerState,
        snapPositionalThreshold = PrimaryPagerSnapThreshold
    )
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val primaryContentStateHolder = rememberSaveableStateHolder()
    var primaryPagerScrollLocked by remember { mutableStateOf(false) }
    var pendingPrimaryNavigationRoute by remember { mutableStateOf<String?>(null) }
    val visualPrimaryRoute = remember(activePrimaryRoute, pendingPrimaryNavigationRoute, primaryPagerRoutes) {
        resolvePrimaryNavVisualRoute(
            activeRoute = activePrimaryRoute,
            pendingRoute = pendingPrimaryNavigationRoute,
            pagerRoutes = primaryPagerRoutes
        )
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        onContentReady()
    }
    LaunchedEffect(Unit) {
        listeningTracker.start(this, playerViewModel.playback)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, listeningTracker) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                listeningTracker.flushNow()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    LaunchedEffect(navController, startRoute, initialDestination) {
        if (startRoute.isBlank() || startRoute == initialDestination) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        if (isPrimaryRoute(startRoute)) {
            navController.navigatePrimaryRoute(startRoute)
        } else {
            navController.navigateSingleTop(startRoute)
        }
    }
    var blockNavTouches by remember { mutableStateOf(false) }
    var lastRouteForTouchBlock by remember { mutableStateOf(currentPrimaryRoute ?: currentRoute) }
    var touchBlockSeq by remember { mutableIntStateOf(0) }
    var pendingDetailNavigation by remember { mutableStateOf(false) }
    var pendingDetailNavigationSeq by remember { mutableIntStateOf(0) }
    var cancelPendingDetailNavigation by remember { mutableStateOf(false) }
    var albumDetailExitInProgress by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val downloadsViewModel: DownloadsViewModel = hiltViewModel(activityViewModelStoreOwner)
    val settingsViewModel: SettingsViewModel = hiltViewModel(activityViewModelStoreOwner)
    val hasCurrentMediaItem by remember(playerViewModel) {
        playerViewModel.playback
            .map { it.currentMediaItem != null }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    val sharedPlayerItem by remember(playerViewModel) {
        playerViewModel.playback
            .map { it.currentMediaItem }
            .distinctUntilChanged { old, new ->
                old?.mediaId == new?.mediaId &&
                    old?.localConfiguration?.uri == new?.localConfiguration?.uri &&
                    old?.mediaMetadata?.artworkUri == new?.mediaMetadata?.artworkUri
            }
    }.collectAsStateWithLifecycle(initialValue = null)
    val drawerStatusViewModel: DrawerStatusViewModel = hiltViewModel(activityViewModelStoreOwner)
    val bulkProgress by libraryViewModel.bulkProgress.collectAsStateWithLifecycle()
    val cloudSyncSelectionDialogState by libraryViewModel.cloudSyncSelectionDialogState.collectAsStateWithLifecycle()
    val appVolumePercent by playerViewModel.appVolumePercent.collectAsStateWithLifecycle()
    var showManualRjDialog by remember { mutableStateOf(false) }
    var manualRjInput by remember { mutableStateOf("") }
    var showHardwareVolumeOverlay by remember { mutableStateOf(false) }
    var hardwareVolumeOverlayInteracting by remember { mutableStateOf(false) }
    var hardwareVolumeOverlayHoldTick by remember { mutableLongStateOf(0L) }
    var lastHandledVolumeKeyTick by remember { mutableLongStateOf(0L) }
    var lastLibraryBackPressElapsedRealtime by remember { mutableLongStateOf(0L) }
    var nowPlayingVolumeEventTick by remember { mutableLongStateOf(0L) }
    var lastNonZeroAppVolumePercent by rememberSaveable { mutableIntStateOf(AppVolume.DefaultPercent) }
    var hardwareVolumeOverlayBounds by remember { mutableStateOf<Rect?>(null) }
    var libraryScrollToTopSignal by remember { mutableLongStateOf(0L) }
    var searchScrollToTopSignal by remember { mutableLongStateOf(0L) }
    var submittedSearchKeyword by rememberSaveable { mutableStateOf("") }
    var submittedSearchOrderName by rememberSaveable { mutableStateOf(SearchAssistSearchRequest().orderName) }
    var submittedSearchPurchasedOnly by rememberSaveable { mutableStateOf(SearchAssistSearchRequest().purchasedOnly) }
    var submittedSearchPresaleOnly by rememberSaveable { mutableStateOf(SearchAssistSearchRequest().presaleOnly) }
    var submittedSearchChineseTranslatedOnly by rememberSaveable {
        mutableStateOf(SearchAssistSearchRequest().chineseTranslatedOnly)
    }
    var submittedSearchCollectedOnly by rememberSaveable { mutableStateOf(SearchAssistSearchRequest().collectedOnly) }
    var submittedSearchHasSubtitle by rememberSaveable { mutableStateOf(SearchAssistSearchRequest().hasSubtitle) }
    var submittedSearchAllAges by rememberSaveable { mutableStateOf(SearchAssistSearchRequest().allAges) }
    var submittedSearchCollectedSortName by rememberSaveable {
        mutableStateOf(SearchAssistSearchRequest().collectedSortName)
    }
    var submittedSearchLocale by rememberSaveable { mutableStateOf(SearchAssistSearchRequest().locale) }
    var submittedSearchSignal by rememberSaveable { mutableLongStateOf(0L) }
    var searchAssistInitialRequest by remember { mutableStateOf(SearchAssistSearchRequest()) }
    var favoritesScrollToTopSignal by remember { mutableLongStateOf(0L) }
    var playlistsScrollToTopSignal by remember { mutableLongStateOf(0L) }
    var groupsScrollToTopSignal by remember { mutableLongStateOf(0L) }
    var downloadsScrollToTopSignal by remember { mutableLongStateOf(0L) }
    var settingsScrollToTopSignal by remember { mutableLongStateOf(0L) }
    var settingsDetailPageVisible by rememberSaveable { mutableStateOf(false) }
    var hotListeningScrollToTopSignal by remember { mutableLongStateOf(0L) }
    val appVolumeWarningSessionState = rememberAppVolumeWarningSessionState()
    val audioOutputRouteKind = rememberCurrentAudioOutputRouteKind()
    
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.isLandscape
    // 使用 smallestScreenWidthDp 判定是否为手机 (一般 < 600dp 为手机)
    val isPhone = configuration.smallestScreenWidthDp < 600
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val updateState by settingsViewModel.updateState.collectAsStateWithLifecycle()
    var automaticUpdateDialogDismissed by rememberSaveable { mutableStateOf(false) }
    var automaticUpdateInstallRequested by rememberSaveable { mutableStateOf(false) }
    var pendingAutomaticInstallPath by rememberSaveable { mutableStateOf<String?>(null) }
    var nowPlayingVisible by rememberSaveable { mutableStateOf(false) }
    var nowPlayingVideoFullscreen by remember { mutableStateOf(false) }
    var nowPlayingUsesInlineVolumeControl by remember { mutableStateOf(false) }
    var nowPlayingEqualizerVisible by remember { mutableStateOf(false) }
    var nowPlayingBackdropActive by rememberSaveable { mutableStateOf(false) }
    var nowPlayingPortraitExitPending by remember { mutableStateOf(false) }
    var nowPlayingRouteExitFinished by remember { mutableStateOf(false) }
    var nowPlayingBackdropExitDurationMs by rememberSaveable {
        mutableIntStateOf(NowPlayingMotionSpec.totalExitDurationMs(NowPlayingMotionLayout.PORTRAIT))
    }
    var nowPlayingPlaylistPickerRequest by remember { mutableStateOf<PlaylistPickerRequest?>(null) }
    var albumBatchPlaylistPickerRequest by remember { mutableStateOf<BatchPlaylistPickerRequest?>(null) }
    var libraryGroupPickerAlbumId by remember { mutableStateOf<Long?>(null) }
    val hideStatusBarForImmersivePage = shouldHideStatusBarForImmersivePage(
        currentRoute = currentRoute
            .takeUnless { albumDetailExitInProgress }
            .let { route -> if (albumDetailEnterPreparing) "album_detail_preparing" else route },
        nowPlayingVisible = nowPlayingVisible
    )
    val openNowPlaying = openNowPlaying@{
        if (nowPlayingVisible) return@openNowPlaying
        nowPlayingPortraitExitPending = false
        nowPlayingRouteExitFinished = false
        nowPlayingBackdropActive = true
        nowPlayingVisible = true
    }
    val finalizeNowPlayingClose: () -> Unit = {
        nowPlayingPlaylistPickerRequest = null
        albumBatchPlaylistPickerRequest = null
        nowPlayingBackdropActive = false
        nowPlayingPortraitExitPending = false
        nowPlayingRouteExitFinished = false
        nowPlayingVideoFullscreen = false
        nowPlayingUsesInlineVolumeControl = false
        nowPlayingEqualizerVisible = false
        nowPlayingVisible = false
    }
    val closeNowPlaying: () -> Unit = {
        nowPlayingRouteExitFinished = true
        if (isPhone && isLandscape) {
            nowPlayingPortraitExitPending = true
            nowPlayingBackdropActive = true
        } else {
            finalizeNowPlayingClose()
        }
    }
    LaunchedEffect(
        nowPlayingPortraitExitPending,
        nowPlayingRouteExitFinished,
        isPhone,
        isLandscape
    ) {
        if (
            nowPlayingPortraitExitPending &&
            nowPlayingRouteExitFinished &&
            (!isPhone || !isLandscape)
        ) {
            finalizeNowPlayingClose()
        }
    }
    val playerBackdropVisible = nowPlayingVisible
    val sharedPlayerIsVideo = sharedPlayerItem.isVideoPlaybackItem()
    val videoOutputEnabled = shouldKeepVideoOutputEnabled(
        currentItemIsVideo = sharedPlayerIsVideo,
        miniPlayerEnabled = showMiniPlayerBar,
        nowPlayingVisible = nowPlayingVisible
    )
    DisposableEffect(playerViewModel, videoOutputEnabled) {
        playerViewModel.setVideoOutputEnabled(videoOutputEnabled)
        onDispose {
            if (videoOutputEnabled) playerViewModel.setVideoOutputEnabled(false)
        }
    }
    val sharedUseDragPreview = playerBackdropVisible &&
        coverBackgroundEnabled &&
        coverPreviewMode == CoverPreviewMode.Drag &&
        !sharedPlayerIsVideo
    val sharedUseMotionPreview = playerBackdropVisible &&
        coverBackgroundEnabled &&
        coverPreviewMode == CoverPreviewMode.Motion &&
        !sharedPlayerIsVideo
    val sharedCoverMotionState = rememberCoverMotionState(
        enabled = sharedUseMotionPreview,
        resetKey = sharedPlayerItem?.mediaId
    )
    val sharedCoverDragPreviewState = rememberCoverDragPreviewState(
        enabled = sharedUseDragPreview,
        resetKey = sharedPlayerItem?.mediaId
    )
    val sharedPlayerBackdropAlignment = when {
        sharedUseDragPreview -> BiasAlignment(
            horizontalBias = sharedCoverDragPreviewState.horizontalBias,
            verticalBias = sharedCoverDragPreviewState.verticalBias
        )
        sharedUseMotionPreview -> BiasAlignment(
            horizontalBias = sharedCoverMotionState.horizontalBias,
            verticalBias = sharedCoverMotionState.verticalBias
        )
        else -> Alignment.Center
    }
    val nowPlayingBackdropAlpha by animateFloatAsState(
        targetValue = if (nowPlayingBackdropActive) 1f else 0f,
        animationSpec = if (nowPlayingBackdropActive) {
            tween(
                durationMillis = 360,
                easing = LinearOutSlowInEasing
            )
        } else {
            keyframes {
                durationMillis = nowPlayingBackdropExitDurationMs
                1f at 0
                1f at (nowPlayingBackdropExitDurationMs * 0.58f).toInt()
                0f at nowPlayingBackdropExitDurationMs using FastOutLinearInEasing
            }
        },
        label = "nowPlayingBackdropAlpha"
    )
    val currentPrimaryRouteState = rememberUpdatedState(currentPrimaryRoute)
    val pendingPrimaryNavigationRouteState = rememberUpdatedState(pendingPrimaryNavigationRoute)
    var primaryNavigationJob by remember { mutableStateOf<Job?>(null) }
    var primaryNavigationRequestId by remember { mutableLongStateOf(0L) }
    DisposableEffect(Unit) {
        onDispose {
            primaryNavigationJob?.cancel()
        }
    }

    fun openPrimaryRoute(route: String, pagerRoutes: List<String> = primaryPagerRoutes) {
        val targetPage = pagerRoutes.indexOf(route)
        primaryNavigationJob?.cancel()
        primaryNavigationRequestId += 1L
        val requestId = primaryNavigationRequestId
        if (targetPage >= 0 && currentPrimaryRoute != null) {
            pendingPrimaryNavigationRoute = route
            UiFrameWorkCoordinator.markFrameCritical(
                PrimaryPageSwitchDurationMs + PrimaryPageSwitchQuietTailMs
            )
            primaryNavigationJob = scope.launch {
                var completed = false
                try {
                    primaryPagerState.stopScroll(MutatePriority.PreventUserInput)
                    // 相邻页已由 Pager 保留在屏外。先提交 active/data-active 状态，让它在
                    // 可见动画前完成一次状态恢复，避免数据订阅与动画首帧争抢主线程。
                    withFrameNanos { }
                    val currentPage = primaryPagerState.currentPage
                    resolvePrimaryPagerApproachPage(
                        currentPage = currentPage,
                        targetPage = targetPage
                    )?.let { approachPage ->
                        primaryPagerState.scrollToPage(approachPage)
                    }
                    UiFrameWorkCoordinator.markFrameCritical(
                        PrimaryPageSwitchDurationMs + PrimaryPageSwitchQuietTailMs
                    )
                    primaryPagerState.animateScrollToPage(
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
                    if (primaryNavigationRequestId == requestId) {
                        primaryNavigationJob = null
                        if (!completed && pendingPrimaryNavigationRouteState.value == route) {
                            pendingPrimaryNavigationRoute = null
                        }
                    }
                }
            }
        } else {
            primaryNavigationJob = null
            pendingPrimaryNavigationRoute = null
            navController.navigatePrimaryRoute(route)
        }
    }

    fun triggerPrimaryRouteScrollToTop(route: String) {
        when (route) {
            Routes.Library -> libraryScrollToTopSignal += 1L
            Routes.Search -> searchScrollToTopSignal += 1L
            Routes.HotListening -> hotListeningScrollToTopSignal += 1L
            "playlist_system/favorites" -> favoritesScrollToTopSignal += 1L
            "playlists" -> playlistsScrollToTopSignal += 1L
            "groups" -> groupsScrollToTopSignal += 1L
            "settings" -> settingsScrollToTopSignal += 1L
        }
    }

    fun openAlbumDetailFromSearch(albumId: Long?, rj: String?, preferDlsitePlay: Boolean = false) {
        val seq = ++pendingDetailNavigationSeq
        pendingDetailNavigation = true
        cancelPendingDetailNavigation = false
        navigator.openAlbumDetail(albumId = albumId, rj = rj, preferDlsitePlay = preferDlsitePlay)
        scope.launch {
            delay(700)
            if (pendingDetailNavigationSeq == seq) {
                pendingDetailNavigation = false
            }
        }
    }

    fun submitSearchAssistRequest(request: SearchAssistSearchRequest) {
        val targetEntry = runCatching {
            navController.getBackStackEntry(Routes.Search)
        }.getOrNull() ?: navController.previousBackStackEntry
        targetEntry?.savedStateHandle?.set(SEARCH_ASSIST_RESULT_KEY, request.keyword)
        targetEntry?.savedStateHandle?.set(SEARCH_ASSIST_RESULT_ORDER_KEY, request.orderName)
        targetEntry?.savedStateHandle?.set(SEARCH_ASSIST_RESULT_PURCHASED_ONLY_KEY, request.purchasedOnly)
        targetEntry?.savedStateHandle?.set(SEARCH_ASSIST_RESULT_PRESALE_ONLY_KEY, request.presaleOnly)
        targetEntry?.savedStateHandle?.set(
            SEARCH_ASSIST_RESULT_CHINESE_TRANSLATED_ONLY_KEY,
            request.chineseTranslatedOnly
        )
        targetEntry?.savedStateHandle?.set(SEARCH_ASSIST_RESULT_COLLECTED_ONLY_KEY, request.collectedOnly)
        targetEntry?.savedStateHandle?.set(SEARCH_ASSIST_RESULT_HAS_SUBTITLE_KEY, request.hasSubtitle)
        targetEntry?.savedStateHandle?.set(SEARCH_ASSIST_RESULT_ALL_AGES_KEY, request.allAges)
        targetEntry?.savedStateHandle?.set(SEARCH_ASSIST_RESULT_COLLECTED_SORT_KEY, request.collectedSortName)
        targetEntry?.savedStateHandle?.set(SEARCH_ASSIST_RESULT_LOCALE_KEY, request.locale)
        targetEntry?.savedStateHandle?.set(
            SEARCH_ASSIST_RESULT_SIGNAL_KEY,
            System.currentTimeMillis()
        )
        navController.popBackStack(Routes.Search, false)
    }

    fun submitMetaSearchKeyword(keyword: String) {
        val normalized = keyword.trim()
        if (normalized.isBlank()) return
        val request = SearchAssistSearchRequest(keyword = normalized)
        submittedSearchKeyword = request.keyword
        submittedSearchOrderName = request.orderName
        submittedSearchPurchasedOnly = request.purchasedOnly
        submittedSearchPresaleOnly = request.presaleOnly
        submittedSearchChineseTranslatedOnly = request.chineseTranslatedOnly
        submittedSearchCollectedOnly = request.collectedOnly
        submittedSearchHasSubtitle = request.hasSubtitle
        submittedSearchAllAges = request.allAges
        submittedSearchCollectedSortName = request.collectedSortName
        submittedSearchLocale = request.locale
        submittedSearchSignal = System.currentTimeMillis()
        openPrimaryRoute(Routes.Search)
    }

    fun handleAutomaticInstallResult(result: AppUpdateInstallResult, apkPath: String) {
        when (result) {
            AppUpdateInstallResult.Started -> {
                pendingAutomaticInstallPath = null
                messageManager.showInfo("正在打开系统安装器")
            }
            AppUpdateInstallResult.PermissionRequired -> {
                pendingAutomaticInstallPath = apkPath
                messageManager.showInfo("请允许 Eara 安装未知来源应用后继续安装")
            }
            AppUpdateInstallResult.FileInvalid -> {
                pendingAutomaticInstallPath = null
                messageManager.showError("下载文件无效，请重新下载")
            }
            is AppUpdateInstallResult.Failed -> {
                pendingAutomaticInstallPath = null
                messageManager.showError(result.message)
            }
        }
    }

    DisposableEffect(lifecycleOwner, pendingAutomaticInstallPath, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            val apkPath = pendingAutomaticInstallPath ?: return@LifecycleEventObserver
            val canInstall = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                context.packageManager.canRequestPackageInstalls()
            if (!canInstall) return@LifecycleEventObserver
            handleAutomaticInstallResult(
                result = launchDownloadedApkInstall(context, apkPath),
                apkPath = apkPath
            )
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(settingsViewModel) {
        settingsViewModel.checkUpdateAutomatically()
    }

    LaunchedEffect(updateState, automaticUpdateInstallRequested) {
        if (!automaticUpdateInstallRequested) return@LaunchedEffect
        when (val state = updateState) {
            is AppUpdateState.ReadyToInstall -> {
                if (state.source != UpdateCheckSource.Automatic) return@LaunchedEffect
                automaticUpdateInstallRequested = false
                handleAutomaticInstallResult(
                    result = launchDownloadedApkInstall(context, state.apkPath),
                    apkPath = state.apkPath
                )
            }
            is AppUpdateState.Failed -> {
                if (state.source != UpdateCheckSource.Automatic) return@LaunchedEffect
                automaticUpdateInstallRequested = false
                messageManager.showError(state.message)
            }
            else -> Unit
        }
    }

    LaunchedEffect(currentPrimaryRoute, primaryPagerRoutes, pendingPrimaryNavigationRoute, primaryNavigationJob) {
        val route = currentPrimaryRoute ?: return@LaunchedEffect
        val pendingRoute = pendingPrimaryNavigationRoute
        if (pendingRoute != null) {
            val pendingPage = primaryPagerRoutes.indexOf(pendingRoute)
            if (
                shouldClearPendingPrimaryNavigationRoute(
                    currentRoute = route,
                    pendingRoute = pendingRoute,
                    navigationInProgress = primaryNavigationJob != null,
                    pendingPage = pendingPage,
                    settledPage = primaryPagerState.settledPage
                )
            ) {
                pendingPrimaryNavigationRoute = null
            } else if (
                route == pendingRoute &&
                primaryNavigationJob == null &&
                shouldSyncPrimaryPagerToRoute(
                    targetPage = pendingPage,
                    settledPage = primaryPagerState.settledPage
                )
            ) {
                primaryPagerState.stopScroll(MutatePriority.PreventUserInput)
                primaryPagerState.scrollToPage(pendingPage)
            }
            return@LaunchedEffect
        }
        val targetPage = primaryPagerRoutes.indexOf(route)
        if (
            shouldSyncPrimaryPagerToRoute(
                targetPage = targetPage,
                settledPage = primaryPagerState.settledPage
            )
        ) {
            primaryPagerState.stopScroll(MutatePriority.PreventUserInput)
            primaryPagerState.scrollToPage(targetPage)
        }
    }

    LaunchedEffect(primaryPagerState) {
        snapshotFlow { primaryPagerState.isScrollInProgress }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
            }
    }

    LaunchedEffect(primaryPagerState, primaryPagerRoutes) {
        snapshotFlow { primaryPagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                if (pendingPrimaryNavigationRouteState.value != null) return@collect
                val currentPrimary = currentPrimaryRouteState.value ?: return@collect
                val targetRoute = primaryPagerRoutes.getOrNull(page) ?: return@collect
                if (targetRoute != currentPrimary) {
                    navController.navigatePrimaryRoute(targetRoute)
                }
            }
    }

    LaunchedEffect(currentRoute, currentPrimaryRoute) {
        showHardwareVolumeOverlay = false
        hardwareVolumeOverlayInteracting = false
        hardwareVolumeOverlayBounds = null
        if (pendingDetailNavigation && currentRoute?.startsWith("album_detail") == true) {
            pendingDetailNavigation = false
        }
        if (cancelPendingDetailNavigation && currentRoute?.startsWith("album_detail") == true) {
            cancelPendingDetailNavigation = false
            navController.popBackStack()
            return@LaunchedEffect
        }
        val normalizedCurrentRoute = currentPrimaryRoute ?: currentRoute
        val last = lastRouteForTouchBlock
        val seq = ++touchBlockSeq
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
            blockNavTouches = true
            try {
                delay(SecondaryPageTouchBlockDurationMs.toLong())
            } finally {
                if (touchBlockSeq == seq) {
                    blockNavTouches = false
                }
            }
        } else {
            blockNavTouches = false
        }
        lastRouteForTouchBlock = normalizedCurrentRoute
    }

    LaunchedEffect(activePrimaryRoute) {
        if (isPrimaryRoute(activePrimaryRoute)) {
            lastPrimaryRoute = activePrimaryRoute
        }
    }

    LaunchedEffect(currentPrimaryRoute, hasPreviousBackStackEntry, nowPlayingVisible, drawerState.isOpen) {
        if (currentPrimaryRoute != Routes.Library || hasPreviousBackStackEntry || nowPlayingVisible || drawerState.isOpen) {
            lastLibraryBackPressElapsedRealtime = 0L
        }
    }

    LaunchedEffect(storedMiniPlayerDisplayMode) {
        miniPlayerDisplayMode = runCatching {
            MiniPlayerDisplayMode.valueOf(storedMiniPlayerDisplayMode)
        }.getOrElse {
            MiniPlayerDisplayMode.CoverOnly
        }
    }

    LaunchedEffect(nowPlayingVisible) {
        if (!nowPlayingVisible) {
            nowPlayingUsesInlineVolumeControl = false
            nowPlayingEqualizerVisible = false
            return@LaunchedEffect
        }
        showHardwareVolumeOverlay = false
        hardwareVolumeOverlayInteracting = false
        hardwareVolumeOverlayBounds = null
        nowPlayingVolumeEventTick = 0L
    }

    val colorScheme = AsmrTheme.colorScheme
    val materialColorScheme = MaterialTheme.colorScheme
    val dynamicContainerColor = dynamicPageContainerColor(colorScheme)
    val isAlbumDetailRoute = currentRoute?.startsWith("album_detail") == true
    val topBarContentColor = if (isAlbumDetailRoute) Color.White else colorScheme.onSurface
    val drawerContainerColor = if (colorScheme.isDark) Color(0xFF121212) else Color.White

    val defaultSystemUi = remember(activity) {
        activity?.let { act -> captureDefaultSystemUiState(act.window) }
    }

    DisposableEffect(activity) {
        val act = activity ?: return@DisposableEffect onDispose { }
        onDispose {
            restoreMainContainerSystemUi(act.window, defaultSystemUi)
        }
    }

    DisposableEffect(albumDetailInsetsDispatchSuppressor, albumDetailExitInProgress) {
        if (albumDetailExitInProgress) {
            val token = albumDetailInsetsDispatchSuppressor.acquire()
            onDispose { albumDetailInsetsDispatchSuppressor.release(token) }
        } else {
            onDispose { }
        }
    }

    DisposableEffect(
        activity,
        defaultSystemUi,
        forceImmersive,
        hideStatusBarForImmersivePage,
        nowPlayingVisible,
        colorScheme.isDark
    ) {
        val act = activity ?: return@DisposableEffect onDispose { }
        applyMainContainerSystemUi(
            window = act.window,
            forceImmersive = forceImmersive,
            hideStatusBarForImmersivePage = hideStatusBarForImmersivePage,
            nowPlayingVisible = nowPlayingVisible,
            isDark = colorScheme.isDark
        )
        onDispose { }
    }

    // 普通播放页保持原方向策略，仅视频全屏时锁定横屏。
    LaunchedEffect(
        nowPlayingVisible,
        isPhone,
        nowPlayingVideoFullscreen,
        nowPlayingPortraitExitPending
    ) {
        activity?.let { act ->
            act.requestedOrientation = resolveMainRequestedOrientation(
                isPhone = isPhone,
                nowPlayingVisible = nowPlayingVisible,
                videoFullscreen = nowPlayingVideoFullscreen,
                portraitExitPending = nowPlayingPortraitExitPending
            )
        }
    }
    LaunchedEffect(appVolumePercent) {
        if (appVolumePercent > 0) {
            lastNonZeroAppVolumePercent = appVolumePercent
        }
    }

    LaunchedEffect(volumeKeyEventTick) {
        if (volumeKeyEventTick <= 0L) return@LaunchedEffect
        if (volumeKeyEventTick == lastHandledVolumeKeyTick) return@LaunchedEffect
        lastHandledVolumeKeyTick = volumeKeyEventTick
        if (nowPlayingUsesInlineVolumeControl && !nowPlayingEqualizerVisible) {
            showHardwareVolumeOverlay = false
            nowPlayingVolumeEventTick = volumeKeyEventTick
            return@LaunchedEffect
        }
        showHardwareVolumeOverlay = true
        hardwareVolumeOverlayHoldTick = volumeKeyEventTick
    }

    LaunchedEffect(showHardwareVolumeOverlay, hardwareVolumeOverlayHoldTick, hardwareVolumeOverlayInteracting, nowPlayingUsesInlineVolumeControl, nowPlayingEqualizerVisible) {
        if (!showHardwareVolumeOverlay) return@LaunchedEffect
        if (nowPlayingUsesInlineVolumeControl && !nowPlayingEqualizerVisible) {
            showHardwareVolumeOverlay = false
            hardwareVolumeOverlayBounds = null
            return@LaunchedEffect
        }
        if (hardwareVolumeOverlayInteracting) return@LaunchedEffect
        val snapshot = hardwareVolumeOverlayHoldTick
        delay(2_000)
        if (!hardwareVolumeOverlayInteracting && hardwareVolumeOverlayHoldTick == snapshot) {
            showHardwareVolumeOverlay = false
            hardwareVolumeOverlayBounds = null
        }
    }

    BackHandler(drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    BackHandler(pendingDetailNavigation && currentRoute == Routes.Search) {
        pendingDetailNavigation = false
        cancelPendingDetailNavigation = true
    }

    BackHandler(
        enabled = currentPrimaryRoute == Routes.Library &&
            !hasPreviousBackStackEntry &&
            !drawerState.isOpen &&
            !pendingDetailNavigation &&
            !nowPlayingVisible
    ) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastLibraryBackPressElapsedRealtime <= 2_000L) {
            activity?.let { currentActivity ->
                PlaybackService.requestShutdownForAppExit(currentActivity)
                currentActivity.finishAndRemoveTask()
            }
        } else {
            lastLibraryBackPressElapsedRealtime = now
            messageManager.showInfo("再按一次返回退出应用")
        }
    }

    val drawerGesturesEnabled = false

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerGesturesEnabled,
        drawerContent = {
            MainDrawerContent(
                currentRoute = currentRoute,
                navBackStackEntry = navBackStackEntry,
                drawerContainerColor = drawerContainerColor,
                colorScheme = colorScheme,
                drawerStatusViewModel = drawerStatusViewModel,
                onOpenPrimaryRoute = { route -> openPrimaryRoute(route) }
            )
        }
    ) {
        val miniPlayerVisible = showMiniPlayerBar &&
            hasCurrentMediaItem &&
            !nowPlayingVisible
        val bottomChromeVisible = !nowPlayingVisible
        val rightPanelExpandedFromStore by settingsDataStore.recentAlbumsPanelExpanded
            .collectAsStateWithLifecycle(initialValue = recentAlbumsPanelExpandedInitial)
        val rightPanelExpandedState = remember(settingsDataStore, scope, recentAlbumsPanelExpandedInitial) {
            PersistedBooleanState(initial = recentAlbumsPanelExpandedInitial) { expanded ->
                scope.launch { settingsDataStore.setRecentAlbumsPanelExpanded(expanded) }
            }
        }
        LaunchedEffect(rightPanelExpandedFromStore) {
            rightPanelExpandedState.updateFromStore(rightPanelExpandedFromStore)
        }
        // 专辑详情始终覆盖在主页面之上；让底层顶栏/页面 active 标记在整个详情生命周期内
        // 保持稳定，退出时只更新必要的详情页位移和裁剪，不在同一帧重建整套主页面 chrome。
        val currentScreenIsPrimary = currentPrimaryRoute != null ||
            isAlbumDetailRoute || albumDetailExitInProgress
        val showBackButton = !currentScreenIsPrimary
        val showPrimaryBrand = currentScreenIsPrimary
        val hasOverlayRoute = currentPrimaryRoute == null && !albumDetailExitInProgress
        val albumDetailTransitionActive = isAlbumDetailRoute || albumDetailExitInProgress
        val primaryPageParallaxActive = hasOverlayRoute && !albumDetailTransitionActive
        val primaryPageParallaxOffset = animateDpAsState(
            targetValue = if (primaryPageParallaxActive) -PrimaryPageParallaxOffset else 0.dp,
            animationSpec = tween(
                durationMillis = if (primaryPageParallaxActive) {
                    SecondaryPageEnterDurationMs
                } else {
                    SecondaryPageExitDurationMs
                },
                easing = SecondaryPageSlideEasing
            ),
            label = "primaryPageParallaxOffset"
        )
        val useLargeBottomChrome = !windowSizeClass.widthSizeClass.isCompactWidth && !isPhone
        val navigationBarBottomPadding = StableWindowInsets.navigationBars
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues()
            .calculateBottomPadding()
        val bottomChromeBottomPadding = 24.dp + navigationBarBottomPadding
        val bottomOverlayPadding = bottomChromeOverlayHeight(useLargeBottomChrome) + navigationBarBottomPadding
        var secondaryPageTopPadding by remember { mutableStateOf(0.dp) }
        val pageTranslationHeader = remember { PageTranslationHeaderState() }
        CompositionLocalProvider(
            LocalBottomOverlayPadding provides bottomOverlayPadding,
            LocalRightPanelExpandedState provides rightPanelExpandedState,
            LocalPageTranslationHeader provides pageTranslationHeader,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(resolveMainPageBackgroundColor(colorScheme))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val width = size.width.toFloat().coerceAtLeast(1f)
                            val visibleRight = if (albumDetailTransitionActive) {
                                albumDetailPageOffsetReader?.invoke()?.coerceIn(0f, width) ?: width
                            } else {
                                width
                            }
                            // 详情页是完全不透明的前景。只提交它左侧仍然可见的主页面区域，
                            // 裁剪保持在 RenderNode 属性更新路径，底层主页面不再跟随位移。
                            shape = HorizontalRectClipShape(0f, visibleRight)
                            clip = true
                        }
                ) {
                    Scaffold(
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        containerColor = Color.Transparent,
                        contentColor = colorScheme.onBackground,
                        topBar = {
                            MainTopBarContent(
                                navController = navController,
                                navBackStackEntry = navBackStackEntry,
                                currentRoute = currentRoute,
                                visualPrimaryRoute = visualPrimaryRoute,
                                currentScreenIsPrimary = currentScreenIsPrimary,
                                showBackButton = showBackButton,
                                showPrimaryBrand = showPrimaryBrand,
                                hasPreviousBackStackEntry = hasPreviousBackStackEntry,
                                albumDetailTransitionActive = albumDetailTransitionActive,
                                settingsDetailPageVisible = settingsDetailPageVisible,
                                activityViewModelStoreOwner = activityViewModelStoreOwner,
                                downloadsViewModel = downloadsViewModel,
                                libraryViewModel = libraryViewModel,
                                topBarContentColor = topBarContentColor,
                                colorScheme = colorScheme,
                                materialColorScheme = materialColorScheme,
                                dynamicContainerColor = dynamicContainerColor
                            )
                        }
                    ) { padding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(if (albumDetailTransitionActive) 1f else 0f)
                        ) {
                            val topContentPadding = padding.calculateTopPadding()
                            SideEffect {
                                if (secondaryPageTopPadding != topContentPadding) {
                                    secondaryPageTopPadding = topContentPadding
                                }
                            }
                            MainPrimaryPagerContent(
                                primaryContentStateHolder = primaryContentStateHolder,
                                primaryPagerState = primaryPagerState,
                                primaryPagerBeyondBoundsPageCount = primaryPagerBeyondBoundsPageCount,
                                primaryPagerFlingBehavior = primaryPagerFlingBehavior,
                                primaryPagerRoutes = primaryPagerRoutes,
                                visualPrimaryRoute = visualPrimaryRoute,
                                albumDetailTransitionActive = albumDetailTransitionActive,
                                hasOverlayRoute = hasOverlayRoute,
                                isAlbumDetailRoute = isAlbumDetailRoute,
                                primaryPageParallaxOffset = primaryPageParallaxOffset,
                                primaryPagerScrollLocked = primaryPagerScrollLocked,
                                setPrimaryPagerScrollLocked = { primaryPagerScrollLocked = it },
                                windowSizeClass = windowSizeClass,
                                activityViewModelStoreOwner = activityViewModelStoreOwner,
                                navigator = navigator,
                                navController = navController,
                                libraryViewModel = libraryViewModel,
                                playerViewModel = playerViewModel,
                                settingsViewModel = settingsViewModel,
                                scope = scope,
                                libraryScrollToTopSignal = libraryScrollToTopSignal,
                                searchScrollToTopSignal = searchScrollToTopSignal,
                                hotListeningScrollToTopSignal = hotListeningScrollToTopSignal,
                                favoritesScrollToTopSignal = favoritesScrollToTopSignal,
                                playlistsScrollToTopSignal = playlistsScrollToTopSignal,
                                groupsScrollToTopSignal = groupsScrollToTopSignal,
                                settingsScrollToTopSignal = settingsScrollToTopSignal,
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
                                searchAssistInitialRequest = searchAssistInitialRequest,
                                setSearchAssistInitialRequest = { searchAssistInitialRequest = it },
                                openNowPlaying = openNowPlaying,
                                requestMiniPlayerPlayFeedback = { requestMiniPlayerPlayFeedback() },
                                submitMetaSearchKeyword = { submitMetaSearchKeyword(it) },
                                setAlbumBatchPlaylistPickerRequest = { albumBatchPlaylistPickerRequest = it },
                                setLibraryGroupPickerAlbumId = { libraryGroupPickerAlbumId = it },
                                topContentPadding = topContentPadding,
                                setSettingsDetailPageVisible = { settingsDetailPageVisible = it },
                                openAlbumDetailFromSearch = { albumId, rj, preferDlsitePlay ->
                                    openAlbumDetailFromSearch(albumId, rj, preferDlsitePlay)
                                }
                            )

                        }
                    }
                }

                MainNavGraph(
                    navController = navController,
                    startDestination = initialDestination,
                    modifier = Modifier.fillMaxSize(),
                    contents = buildMainRouteContents(
                        host = MainRouteHost(
                            navController = navController,
                            navigator = navigator,
                            windowSizeClass = windowSizeClass,
                            activityViewModelStoreOwner = activityViewModelStoreOwner,
                            playerViewModel = playerViewModel,
                            libraryViewModel = libraryViewModel,
                            settingsViewModel = settingsViewModel,
                            downloadsViewModel = downloadsViewModel,
                            scope = scope,
                            secondaryPageTopPadding = secondaryPageTopPadding,
                            searchAssistInitialRequest = searchAssistInitialRequest,
                            downloadsScrollToTopSignal = downloadsScrollToTopSignal,
                            albumDetailStackPopTargetEntryId = albumDetailStackPopTargetEntryId,
                            setAlbumDetailStackPopTargetEntryId = { albumDetailStackPopTargetEntryId = it },
                            setAlbumDetailPageOffsetReader = { albumDetailPageOffsetReader = it },
                            setAlbumDetailExitInProgress = { albumDetailExitInProgress = it },
                            setManualRjInput = { manualRjInput = it },
                            setShowManualRjDialog = { showManualRjDialog = true },
                            setAlbumBatchPlaylistPickerRequest = { albumBatchPlaylistPickerRequest = it },
                            openNowPlaying = openNowPlaying,
                            requestMiniPlayerPlayFeedback = { requestMiniPlayerPlayFeedback() },
                            submitMetaSearchKeyword = { submitMetaSearchKeyword(it) },
                            submitSearchAssistRequest = { submitSearchAssistRequest(it) }
                        ),
                        searchBridge = { backStackEntry ->
                            MainSearchAssistBridge(backStackEntry) { values ->
                                submittedSearchKeyword = values.keyword
                                submittedSearchOrderName = values.orderName
                                submittedSearchPurchasedOnly = values.purchasedOnly
                                submittedSearchPresaleOnly = values.presaleOnly
                                submittedSearchChineseTranslatedOnly = values.chineseTranslatedOnly
                                submittedSearchCollectedOnly = values.collectedOnly
                                submittedSearchHasSubtitle = values.hasSubtitle
                                submittedSearchAllAges = values.allAges
                                submittedSearchCollectedSortName = values.collectedSortName
                                submittedSearchLocale = values.locale
                                submittedSearchSignal = values.signal
                            }
                        }
                    )
                )

                    if (blockNavTouches || albumDetailExitInProgress) {
                        if (isAlbumDetailRoute) {
                            val albumDetailTopBarTouchPassThroughHeight =
                                StableWindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
                                    AlbumDetailTopBarTouchPassThroughHeight
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(albumDetailTopBarTouchPassThroughHeight)
                                    .padding(start = AlbumDetailBackTouchPassThroughWidth)
                                    .pointerInteropFilter { true }
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = albumDetailTopBarTouchPassThroughHeight)
                                    .pointerInteropFilter { true }
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInteropFilter { true }
                            )
                        }
                    }
            if (showManualRjDialog && navBackStackEntry != null &&
                (currentRoute?.startsWith("album_detail/{albumId}") == true || currentRoute?.startsWith("album_detail/") == true)
            ) {
                val albumDetailViewModel: AlbumDetailViewModel = hiltViewModel(navBackStackEntry!!)
                FlatTextFieldDialog(
                    onDismissRequest = { showManualRjDialog = false },
                    message = "请输入 DLsite 作品编号，支持 RJ、BJ、VJ；保存后将自动执行云同步。",
                    value = manualRjInput,
                    onValueChange = { manualRjInput = it },
                    placeholder = "作品编号（如 BJ02370869）",
                    confirmText = "同步",
                    confirmEnabled = manualRjInput.trim().isNotBlank(),
                    onConfirm = {
                        showManualRjDialog = false
                        albumDetailViewModel.manualSetRjAndSync(manualRjInput.trim())
                    },
                )
            }

            cloudSyncSelectionDialogState?.let { dialogState ->
                val ignoreAllHandler = if (bulkProgress != null) {
                    { libraryViewModel.ignoreAllCloudSyncSelections() }
                } else {
                    null
                }
                CloudSyncSelectionDialog(
                    state = dialogState,
                    onSelect = libraryViewModel::confirmCloudSyncSelection,
                    onCancel = libraryViewModel::cancelCloudSyncSelection,
                    onIgnoreAll = ignoreAllHandler
                )
            }

        if (bottomChromeVisible) {
            MainBottomChromeContent(
                windowSizeClass = windowSizeClass,
                isPhone = isPhone,
                isLandscape = isLandscape,
                currentRoute = currentRoute,
                bottomChromeVisible = bottomChromeVisible,
                useLargeBottomChrome = useLargeBottomChrome,
                bottomChromeBottomPadding = bottomChromeBottomPadding,
                rightPanelExpandedState = rightPanelExpandedState,
                visualPrimaryRoute = visualPrimaryRoute,
                primaryPagerState = primaryPagerState,
                primaryPagerRoutes = primaryPagerRoutes,
                activePrimaryRoute = activePrimaryRoute,
                pendingPrimaryNavigationRoute = pendingPrimaryNavigationRoute,
                miniPlayerVisible = miniPlayerVisible,
                miniPlayerDisplayMode = miniPlayerDisplayMode,
                setMiniPlayerDisplayMode = { miniPlayerDisplayMode = it },
                miniPlayerPlayFeedbackSignal = miniPlayerPlayFeedbackSignal,
                bottomNavItems = bottomNavItems,
                nowPlayingVisible = nowPlayingVisible,
                openNowPlaying = openNowPlaying,
                onShowQueue = onShowQueue,
                currentPrimaryRoute = currentPrimaryRoute,
                triggerPrimaryRouteScrollToTop = { triggerPrimaryRouteScrollToTop(it) },
                openPrimaryRoute = { openPrimaryRoute(it) },
                scope = scope,
                settingsDataStore = settingsDataStore
            )
        }

        if (nowPlayingVisible) {
            MainNowPlayingOverlay(
                windowSizeClass = windowSizeClass,
                playerViewModel = playerViewModel,
                scope = scope,
                settingsDataStore = settingsDataStore,
                activityViewModelStoreOwner = activityViewModelStoreOwner,
                nowPlayingVisible = nowPlayingVisible,
                nowPlayingPlaylistPickerRequest = nowPlayingPlaylistPickerRequest,
                nowPlayingBackdropAlpha = nowPlayingBackdropAlpha,
                colorScheme = colorScheme,
                sharedPlayerItem = sharedPlayerItem,
                coverBackgroundEnabled = coverBackgroundEnabled,
                coverBackgroundClarity = coverBackgroundClarity,
                coverPreviewMode = coverPreviewMode,
                sharedPlayerBackdropAlignment = sharedPlayerBackdropAlignment,
                sharedCoverDragPreviewState = sharedCoverDragPreviewState,
                nowPlayingVolumeEventTick = nowPlayingVolumeEventTick,
                setNowPlayingUsesInlineVolumeControl = { nowPlayingUsesInlineVolumeControl = it },
                setNowPlayingEqualizerVisible = { nowPlayingEqualizerVisible = it },
                setNowPlayingVideoFullscreen = { nowPlayingVideoFullscreen = it },
                closeNowPlaying = closeNowPlaying,
                setNowPlayingBackdropExitDurationMs = { nowPlayingBackdropExitDurationMs = it },
                isPhone = isPhone,
                isLandscape = isLandscape,
                setNowPlayingPortraitExitPending = { nowPlayingPortraitExitPending = it },
                setNowPlayingBackdropActive = { nowPlayingBackdropActive = it },
                onShowQueue = onShowQueue,
                onShowSleepTimer = onShowSleepTimer,
                setNowPlayingPlaylistPickerRequest = { nowPlayingPlaylistPickerRequest = it },
                nowPlayingHomeLayoutMode = nowPlayingHomeLayoutMode,
                nowPlayingHomeLayoutHintDismissed = nowPlayingHomeLayoutHintDismissed,
                nowPlayingLyricsSettings = nowPlayingLyricsSettings,
                lyricsPageSettings = lyricsPageSettings,
                audioOutputRouteKind = audioOutputRouteKind,
                appVolumeWarningSessionState = appVolumeWarningSessionState,
                albumBatchPlaylistPickerRequest = albumBatchPlaylistPickerRequest,
                setAlbumBatchPlaylistPickerRequest = { albumBatchPlaylistPickerRequest = it }
            )
        }

        MainOverlayPickers(
            showBatchPicker = !nowPlayingVisible,
            albumBatchPlaylistPickerRequest = albumBatchPlaylistPickerRequest,
            setAlbumBatchPlaylistPickerRequest = { albumBatchPlaylistPickerRequest = it },
            libraryGroupPickerAlbumId = libraryGroupPickerAlbumId,
            setLibraryGroupPickerAlbumId = { libraryGroupPickerAlbumId = it },
            windowSizeClass = windowSizeClass,
            activityViewModelStoreOwner = activityViewModelStoreOwner
        )

        MainVolumeOverlayHost(
            showHardwareVolumeOverlay = showHardwareVolumeOverlay,
            setShowHardwareVolumeOverlay = { showHardwareVolumeOverlay = it },
            hardwareVolumeOverlayBounds = hardwareVolumeOverlayBounds,
            setHardwareVolumeOverlayBounds = { hardwareVolumeOverlayBounds = it },
            appVolumePercent = appVolumePercent,
            audioOutputRouteKind = audioOutputRouteKind,
            playerViewModel = playerViewModel,
            bumpVolumeOverlayHoldTick = { hardwareVolumeOverlayHoldTick += 1L },
            setHardwareVolumeOverlayInteracting = { hardwareVolumeOverlayInteracting = it },
            lastNonZeroAppVolumePercent = lastNonZeroAppVolumePercent,
            appVolumeWarningSessionState = appVolumeWarningSessionState
        )

        MainUpdateDialogsHost(
            updateState = updateState,
            automaticUpdateDialogDismissed = automaticUpdateDialogDismissed,
            setAutomaticUpdateDialogDismissed = { automaticUpdateDialogDismissed = true },
            setAutomaticUpdateInstallRequested = { automaticUpdateInstallRequested = true },
            settingsViewModel = settingsViewModel,
            messageManager = messageManager,
            context = context,
            colorScheme = colorScheme,
            forceImmersive = forceImmersive,
            settingsDataStore = settingsDataStore,
            closeNowPlaying = closeNowPlaying,
            navigator = navigator
        )
    }
}

}

}

