package com.asmr.player.main

import com.asmr.player.translation.LocalPageTranslationHeader
import com.asmr.player.translation.PageTranslationHeaderState
import android.view.Choreographer
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.graphicsLayer
import android.app.Activity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import com.asmr.player.ui.common.core.isCompactWidth
import com.asmr.player.ui.common.core.isLandscape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.asmr.player.ui.library.LibraryViewModel
import com.asmr.player.ui.player.PlayerViewModel
import com.asmr.player.performance.UiFrameWorkCoordinator
import com.asmr.player.ui.player.MiniPlayerDisplayMode
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
import com.asmr.player.ui.common.core.SearchBlockedKeywordsViewModel
import com.asmr.player.ui.settings.SettingsViewModel
import com.asmr.player.ui.drawer.DrawerStatusViewModel
import com.asmr.player.ui.nav.AppNavigator
import com.asmr.player.ui.nav.Routes
import com.asmr.player.ui.nav.bottomChromeNavItems
import com.asmr.player.ui.nav.bottomChromeOverlayHeight
import com.asmr.player.ui.nav.isPrimaryRoute
import com.asmr.player.ui.nav.resolvePrimaryRoute
import com.asmr.player.ui.common.list.LocalBottomOverlayPadding
import com.asmr.player.ui.common.core.resolveMainPageBackgroundColor
import com.asmr.player.ui.common.audio.AppVolumeWarningSessionState
import com.asmr.player.ui.common.audio.rememberAppVolumeWarningSessionState
import com.asmr.player.ui.common.audio.rememberCurrentAudioOutputRouteKind
import com.asmr.player.service.AudioOutputRouteKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.compose.ui.platform.LocalContext
import com.asmr.player.ui.theme.AsmrTheme

import com.asmr.player.data.local.datastore.SettingsDataStore
import com.asmr.player.data.settings.CoverPreviewMode
import com.asmr.player.data.settings.LyricsPageSettings
import com.asmr.player.data.settings.NowPlayingHomeLayoutMode
import com.asmr.player.data.settings.NowPlayingLyricsSettings
import com.asmr.player.util.MessageManager
import com.asmr.player.ui.common.list.StableWindowInsets
import com.asmr.player.ui.theme.dynamicPageContainerColor

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

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
    val miniPlayer = rememberMiniPlayerDisplayModeState(settingsDataStore)
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
    val scope = rememberCoroutineScope()
    val primaryNav = rememberPrimaryNavigationState(
        navController = navController,
        scope = scope,
        pagerState = primaryPagerState,
        currentPrimaryRoute = currentPrimaryRoute
    )
    val visualPrimaryRoute = remember(activePrimaryRoute, primaryNav.pendingRoute.value, primaryPagerRoutes) {
        resolvePrimaryNavVisualRoute(
            activeRoute = activePrimaryRoute,
            pendingRoute = primaryNav.pendingRoute.value,
            pagerRoutes = primaryPagerRoutes
        )
    }
    MainStartupEffects(
        navController = navController,
        startRoute = startRoute,
        initialDestination = initialDestination,
        onContentReady = onContentReady,
        listeningTracker = listeningTracker,
        playerViewModel = playerViewModel
    )
    var albumDetailExitInProgress by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val touchBlock = rememberNavTouchBlockState(currentPrimaryRoute ?: currentRoute)
    val downloadsViewModel: DownloadsViewModel = hiltViewModel(activityViewModelStoreOwner)
    val settingsViewModel: SettingsViewModel = hiltViewModel(activityViewModelStoreOwner)
    val blockedKeywordsViewModel: SearchBlockedKeywordsViewModel = hiltViewModel(activityViewModelStoreOwner)
    val drawerStatusViewModel: DrawerStatusViewModel = hiltViewModel(activityViewModelStoreOwner)
    val bulkProgress by libraryViewModel.bulkProgress.collectAsStateWithLifecycle()
    val cloudSyncSelectionDialogState by libraryViewModel.cloudSyncSelectionDialogState.collectAsStateWithLifecycle()
    val appVolumePercent by playerViewModel.appVolumePercent.collectAsStateWithLifecycle()
    var showManualRjDialog by remember { mutableStateOf(false) }
    var manualRjInput by remember { mutableStateOf("") }
    val volume = rememberHardwareVolumeOverlayState()
    val scrollToTop = remember { ScrollToTopSignals() }
    val submitted = rememberSubmittedSearchState()
    var searchAssistInitialRequest by remember { mutableStateOf(SearchAssistSearchRequest()) }
    var settingsDetailPageVisible by rememberSaveable { mutableStateOf(false) }
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
    val nowPlaying = rememberNowPlayingPresentationState()
    var nowPlayingPlaylistPickerRequest by remember { mutableStateOf<PlaylistPickerRequest?>(null) }
    var albumBatchPlaylistPickerRequest by remember { mutableStateOf<BatchPlaylistPickerRequest?>(null) }
    var libraryGroupPickerAlbumId by remember { mutableStateOf<Long?>(null) }
    val hideStatusBarForImmersivePage = shouldHideStatusBarForImmersivePage(
        currentRoute = currentRoute
            .takeUnless { albumDetailExitInProgress }
            .let { route -> if (albumDetailEnterPreparing) "album_detail_preparing" else route },
        nowPlayingVisible = nowPlaying.visible
    )
    val openNowPlaying = openNowPlaying@{
        if (nowPlaying.visible) return@openNowPlaying
        nowPlaying.portraitExitPending = false
        nowPlaying.routeExitFinished = false
        nowPlaying.backdropActive = true
        nowPlaying.visible = true
    }
    val finalizeNowPlayingClose: () -> Unit = {
        nowPlayingPlaylistPickerRequest = null
        albumBatchPlaylistPickerRequest = null
        nowPlaying.backdropActive = false
        nowPlaying.portraitExitPending = false
        nowPlaying.routeExitFinished = false
        nowPlaying.videoFullscreen = false
        nowPlaying.usesInlineVolumeControl = false
        nowPlaying.equalizerVisible = false
        nowPlaying.visible = false
    }
    val closeNowPlaying: () -> Unit = {
        nowPlaying.routeExitFinished = true
        if (isPhone && isLandscape) {
            nowPlaying.portraitExitPending = true
            nowPlaying.backdropActive = true
        } else {
            finalizeNowPlayingClose()
        }
    }
    NowPlayingPortraitExitEffect(
        state = nowPlaying,
        isPhone = isPhone,
        isLandscape = isLandscape,
        finalizeNowPlayingClose = finalizeNowPlayingClose
    )
    val playerBackdropVisible = nowPlaying.visible
    val sharedBackdrop = rememberSharedPlayerBackdropState(
        playerViewModel = playerViewModel,
        playerBackdropVisible = playerBackdropVisible,
        coverBackgroundEnabled = coverBackgroundEnabled,
        coverPreviewMode = coverPreviewMode
    )
    val nowPlayingBackdropAlpha = rememberNowPlayingBackdropAlpha(nowPlaying)

    fun openAlbumDetailFromSearch(albumId: Long?, rj: String?, preferDlsitePlay: Boolean = false) {
        val seq = ++touchBlock.pendingDetailSeq
        touchBlock.pendingDetail = true
        touchBlock.cancelPendingDetail = false
        navigator.openAlbumDetail(albumId = albumId, rj = rj, preferDlsitePlay = preferDlsitePlay)
        scope.launch {
            delay(700)
            if (touchBlock.pendingDetailSeq == seq) {
                touchBlock.pendingDetail = false
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
        submitted.apply(request)
        primaryNav.openPrimaryRoute(Routes.Search, primaryPagerRoutes)
    }

    AutomaticUpdateEffects(
        settingsViewModel = settingsViewModel,
        messageManager = messageManager,
        context = context,
        updateState = updateState,
        automaticUpdateInstallRequested = automaticUpdateInstallRequested,
        setAutomaticUpdateInstallRequested = { automaticUpdateInstallRequested = it }
    )

    PrimaryNavigationEffects(
        state = primaryNav,
        currentPrimaryRoute = currentPrimaryRoute,
        primaryPagerRoutes = primaryPagerRoutes,
        focusManager = focusManager,
        keyboardController = keyboardController
    )

    NavTouchBlockEffect(
        state = touchBlock,
        currentRoute = currentRoute,
        currentPrimaryRoute = currentPrimaryRoute,
        primaryPagerRoutes = primaryPagerRoutes,
        navController = navController,
        volume = volume
    )

    LaunchedEffect(activePrimaryRoute) {
        if (isPrimaryRoute(activePrimaryRoute)) {
            lastPrimaryRoute = activePrimaryRoute
        }
    }

    MainSystemUiEffects(
        activity = activity,
        forceImmersive = forceImmersive,
        hideStatusBarForImmersivePage = hideStatusBarForImmersivePage,
        nowPlayingVisible = playerBackdropVisible,
        isDark = AsmrTheme.colorScheme.isDark,
        isPhone = isPhone,
        nowPlayingVideoFullscreen = nowPlaying.videoFullscreen,
        nowPlayingPortraitExitPending = nowPlaying.portraitExitPending
    )

    HardwareVolumeOverlayEffects(
        state = volume,
        volumeKeyEventTick = volumeKeyEventTick,
        appVolumePercent = appVolumePercent,
        nowPlaying = nowPlaying
    )

    MainBackHandlers(
        drawerState = drawerState,
        scope = scope,
        touchBlock = touchBlock,
        currentRoute = currentRoute,
        currentPrimaryRoute = currentPrimaryRoute,
        hasPreviousBackStackEntry = hasPreviousBackStackEntry,
        nowPlayingVisible = nowPlaying.visible,
        activity = activity,
        messageManager = messageManager
    )

    val colorScheme = AsmrTheme.colorScheme
    val materialColorScheme = MaterialTheme.colorScheme
    val dynamicContainerColor = dynamicPageContainerColor(colorScheme)
    val isAlbumDetailRoute = currentRoute?.startsWith("album_detail") == true
    val topBarContentColor = if (isAlbumDetailRoute) Color.White else colorScheme.onSurface
    val drawerContainerColor = if (colorScheme.isDark) Color(0xFF121212) else Color.White

    DisposableEffect(albumDetailInsetsDispatchSuppressor, albumDetailExitInProgress) {
        if (albumDetailExitInProgress) {
            val token = albumDetailInsetsDispatchSuppressor.acquire()
            onDispose { albumDetailInsetsDispatchSuppressor.release(token) }
        } else {
            onDispose { }
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
                onOpenPrimaryRoute = { route -> primaryNav.openPrimaryRoute(route, primaryPagerRoutes) }
            )
        }
    ) {
        val miniPlayerVisible = showMiniPlayerBar &&
            sharedBackdrop.hasCurrentMediaItem &&
            !nowPlaying.visible
        val bottomChromeVisible = !nowPlaying.visible
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
                                libraryScrollToTopSignal = scrollToTop.library,
                                searchScrollToTopSignal = scrollToTop.search,
                                hotListeningScrollToTopSignal = scrollToTop.hotListening,
                                favoritesScrollToTopSignal = scrollToTop.favorites,
                                playlistsScrollToTopSignal = scrollToTop.playlists,
                                groupsScrollToTopSignal = scrollToTop.groups,
                                settingsScrollToTopSignal = scrollToTop.settings,
                                submittedSearchKeyword = submitted.keyword,
                                submittedSearchOrderName = submitted.orderName,
                                submittedSearchPurchasedOnly = submitted.purchasedOnly,
                                submittedSearchPresaleOnly = submitted.presaleOnly,
                                submittedSearchChineseTranslatedOnly = submitted.chineseTranslatedOnly,
                                submittedSearchCollectedOnly = submitted.collectedOnly,
                                submittedSearchHasSubtitle = submitted.hasSubtitle,
                                submittedSearchAllAges = submitted.allAges,
                                submittedSearchCollectedSortName = submitted.collectedSortName,
                                submittedSearchLocale = submitted.locale,
                                submittedSearchSignal = submitted.signal,
                                searchAssistInitialRequest = searchAssistInitialRequest,
                                setSearchAssistInitialRequest = { searchAssistInitialRequest = it },
                                openNowPlaying = openNowPlaying,
                                requestMiniPlayerPlayFeedback = { miniPlayer.requestPlayFeedback() },
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
                            blockedKeywordsViewModel = blockedKeywordsViewModel,
                            downloadsViewModel = downloadsViewModel,
                            scope = scope,
                            secondaryPageTopPadding = secondaryPageTopPadding,
                            searchAssistInitialRequest = searchAssistInitialRequest,
                            downloadsScrollToTopSignal = scrollToTop.downloads,
                            albumDetailStackPopTargetEntryId = albumDetailStackPopTargetEntryId,
                            setAlbumDetailStackPopTargetEntryId = { albumDetailStackPopTargetEntryId = it },
                            setAlbumDetailPageOffsetReader = { albumDetailPageOffsetReader = it },
                            setAlbumDetailExitInProgress = { albumDetailExitInProgress = it },
                            setManualRjInput = { manualRjInput = it },
                            setShowManualRjDialog = { showManualRjDialog = true },
                            setAlbumBatchPlaylistPickerRequest = { albumBatchPlaylistPickerRequest = it },
                            openNowPlaying = openNowPlaying,
                            requestMiniPlayerPlayFeedback = { miniPlayer.requestPlayFeedback() },
                            submitMetaSearchKeyword = { submitMetaSearchKeyword(it) },
                            submitSearchAssistRequest = { submitSearchAssistRequest(it) }
                        ),
                        searchBridge = { backStackEntry ->
                            MainSearchAssistBridge(backStackEntry) { values ->
                                submitted.apply(values)
                            }
                        }
                    )
                )

                    if (touchBlock.blockTouches || albumDetailExitInProgress) {
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

                MainDialogsHost(
                    showManualRjDialog = showManualRjDialog,
                    setShowManualRjDialog = { showManualRjDialog = it },
                    manualRjInput = manualRjInput,
                    setManualRjInput = { manualRjInput = it },
                    navBackStackEntry = navBackStackEntry,
                    currentRoute = currentRoute,
                    cloudSyncSelectionDialogState = cloudSyncSelectionDialogState,
                    bulkProgress = bulkProgress,
                    libraryViewModel = libraryViewModel
                )

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
                        pendingPrimaryNavigationRoute = primaryNav.pendingRoute.value,
                        miniPlayerVisible = miniPlayerVisible,
                        miniPlayerDisplayMode = miniPlayer.displayMode,
                        setMiniPlayerDisplayMode = { miniPlayer.displayMode = it },
                        miniPlayerPlayFeedbackSignal = miniPlayer.feedbackSignal,
                        bottomNavItems = bottomNavItems,
                        nowPlayingVisible = playerBackdropVisible,
                        openNowPlaying = openNowPlaying,
                        onShowQueue = onShowQueue,
                        currentPrimaryRoute = currentPrimaryRoute,
                        triggerPrimaryRouteScrollToTop = { scrollToTop.trigger(it) },
                        openPrimaryRoute = { primaryNav.openPrimaryRoute(it, primaryPagerRoutes) },
                        scope = scope,
                        settingsDataStore = settingsDataStore
                    )
                }

                if (nowPlaying.visible) {
                    MainNowPlayingOverlay(
                        windowSizeClass = windowSizeClass,
                        playerViewModel = playerViewModel,
                        scope = scope,
                        settingsDataStore = settingsDataStore,
                        activityViewModelStoreOwner = activityViewModelStoreOwner,
                        nowPlayingVisible = playerBackdropVisible,
                        nowPlayingPlaylistPickerRequest = nowPlayingPlaylistPickerRequest,
                        nowPlayingBackdropAlpha = nowPlayingBackdropAlpha,
                        colorScheme = colorScheme,
                        sharedPlayerItem = sharedBackdrop.item,
                        coverBackgroundEnabled = coverBackgroundEnabled,
                        coverBackgroundClarity = coverBackgroundClarity,
                        coverPreviewMode = coverPreviewMode,
                        sharedPlayerBackdropAlignment = sharedBackdrop.backdropAlignment,
                        sharedCoverDragPreviewState = sharedBackdrop.coverDragPreviewState,
                        nowPlayingVolumeEventTick = volume.nowPlayingEventTick,
                        setNowPlayingUsesInlineVolumeControl = { nowPlaying.usesInlineVolumeControl = it },
                        setNowPlayingEqualizerVisible = { nowPlaying.equalizerVisible = it },
                        setNowPlayingVideoFullscreen = { nowPlaying.videoFullscreen = it },
                        closeNowPlaying = closeNowPlaying,
                        setNowPlayingBackdropExitDurationMs = { nowPlaying.backdropExitDurationMs = it },
                        isPhone = isPhone,
                        isLandscape = isLandscape,
                        setNowPlayingPortraitExitPending = { nowPlaying.portraitExitPending = it },
                        setNowPlayingBackdropActive = { nowPlaying.backdropActive = it },
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
                    showBatchPicker = !nowPlaying.visible,
                    albumBatchPlaylistPickerRequest = albumBatchPlaylistPickerRequest,
                    setAlbumBatchPlaylistPickerRequest = { albumBatchPlaylistPickerRequest = it },
                    libraryGroupPickerAlbumId = libraryGroupPickerAlbumId,
                    setLibraryGroupPickerAlbumId = { libraryGroupPickerAlbumId = it },
                    windowSizeClass = windowSizeClass,
                    activityViewModelStoreOwner = activityViewModelStoreOwner
                )

                MainVolumeOverlayHost(
                    showHardwareVolumeOverlay = volume.showOverlay,
                    setShowHardwareVolumeOverlay = { volume.showOverlay = it },
                    hardwareVolumeOverlayBounds = volume.bounds,
                    setHardwareVolumeOverlayBounds = { volume.bounds = it },
                    appVolumePercent = appVolumePercent,
                    audioOutputRouteKind = audioOutputRouteKind,
                    playerViewModel = playerViewModel,
                    bumpVolumeOverlayHoldTick = { volume.holdTick += 1L },
                    setHardwareVolumeOverlayInteracting = { volume.interacting = it },
                    lastNonZeroAppVolumePercent = volume.lastNonZeroAppVolumePercent,
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
