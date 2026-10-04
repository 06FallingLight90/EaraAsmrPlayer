package com.asmr.player.main

import com.asmr.player.BuildConfig
import com.asmr.player.R
import com.asmr.player.ui.translation.LocalPageTranslationHeader
import com.asmr.player.ui.translation.PageTranslationAction
import com.asmr.player.ui.translation.PageTranslationHeaderAction
import com.asmr.player.ui.translation.PageTranslationHeaderState
import com.asmr.player.ui.translation.PageTranslationHost
import android.os.Bundle
import android.view.KeyEvent
import android.view.Choreographer
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.CloudDownload
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import com.asmr.player.ui.common.core.isCompactWidth
import com.asmr.player.ui.common.core.isLandscape
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.asmr.player.ui.library.AlbumDetailScreen
import com.asmr.player.ui.library.albumdetail.AlbumHeroBlurLayerCache
import com.asmr.player.ui.library.albumdetail.AlbumDetailUiState
import com.asmr.player.ui.library.AlbumDetailViewModel
import com.asmr.player.ui.library.CloudSyncSelectionDialog
import com.asmr.player.ui.library.LibraryFilterScreen
import com.asmr.player.ui.library.LibraryScreen
import com.asmr.player.ui.library.LibraryViewModel
import com.asmr.player.ui.library.BulkPhase
import com.asmr.player.data.remote.scraper.resolveRecommendedWorkHeroCoverUrl
import com.asmr.player.performance.UiFrameWorkCoordinator
import com.asmr.player.ui.player.MiniPlayer
import com.asmr.player.ui.player.NowPlayingMotionLayout
import com.asmr.player.ui.player.NowPlayingMotionSpec
import com.asmr.player.ui.player.NowPlayingScreen
import com.asmr.player.ui.player.PlayerSharedBackdrop
import com.asmr.player.ui.player.PlayerViewModel
import com.asmr.player.ui.player.rememberCoverDragPreviewState
import com.asmr.player.ui.player.rememberCoverMotionState
import com.asmr.player.ui.sidepanel.LocalRightPanelExpandedState
import com.asmr.player.ui.downloads.DownloadsScreen
import com.asmr.player.ui.downloads.DownloadsViewModel
import com.asmr.player.ui.downloads.DownloadItemState
import com.asmr.player.ui.dlsite.DlsiteLoginScreen
import com.asmr.player.ui.dlsite.DlsiteLoginViewModel
import com.asmr.player.ui.hotlistening.HotListeningScreen
import com.asmr.player.ui.hotlistening.HotListeningViewModel
import com.asmr.player.hotlistening.ListeningTracker
import com.asmr.player.ui.groups.AlbumGroupsViewModel
import com.asmr.player.ui.playlists.PlaylistDetailScreen
import com.asmr.player.ui.playlists.PlaylistPickerScreen
import com.asmr.player.ui.playlists.PlaylistsScreen
import com.asmr.player.ui.playlists.PlaylistsViewModel
import com.asmr.player.ui.playlists.SystemPlaylistScreen
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
import com.asmr.player.ui.search.SearchAssistScreen
import com.asmr.player.ui.search.SearchScreen
import com.asmr.player.ui.search.SearchViewModel
import com.asmr.player.ui.settings.AppUpdateState
import com.asmr.player.ui.settings.SettingsScreen
import com.asmr.player.ui.settings.SettingsViewModel
import com.asmr.player.ui.settings.UpdateCheckSource
import com.asmr.player.ui.common.dialog.FlatActionDialog
import com.asmr.player.ui.common.dialog.FlatDialogAction
import com.asmr.player.ui.common.dialog.FlatDialogActionTone
import com.asmr.player.ui.common.dialog.FlatTextFieldDialog
import com.asmr.player.ui.common.dialog.RoundedTopSheet
import com.asmr.player.ui.common.core.EaraTopBarContainer
import com.asmr.player.ui.common.core.EaraMainTopBarHeight
import com.asmr.player.ui.common.core.EaraTopBarIconButton
import com.asmr.player.ui.common.core.resolveMainPageBackgroundColor
import com.asmr.player.ui.common.core.glassMenu
import com.asmr.player.ui.drawer.DrawerStatusViewModel
import com.asmr.player.ui.drawer.SiteStatus
import com.asmr.player.ui.drawer.SiteStatusType
import com.asmr.player.ui.nav.AlbumCoverHintStore
import com.asmr.player.ui.nav.AppNavigator
import com.asmr.player.ui.nav.BottomChrome
import com.asmr.player.ui.nav.BottomChromeNavItem
import com.asmr.player.ui.nav.Routes
import com.asmr.player.ui.nav.bottomChromeNavItems
import com.asmr.player.ui.nav.bottomChromeOverlayHeight
import com.asmr.player.ui.nav.isPrimaryRoute
import com.asmr.player.ui.nav.resolvePrimaryRoute
import com.asmr.player.ui.common.list.LocalBottomOverlayPadding
import com.asmr.player.ui.common.list.rememberCalmScrollableFlingBehavior
import com.asmr.player.ui.splash.EaraSplashOverlay
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.net.URLEncoder
import androidx.compose.material.icons.automirrored.rounded.ArrowBack

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.asmr.player.ui.theme.AsmrPlayerTheme
import com.asmr.player.ui.theme.AsmrTheme
import androidx.compose.ui.draw.blur
import android.os.Build
import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.animation.*
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import com.asmr.player.ui.player.QueueSheetContent
import com.asmr.player.ui.player.SleepTimerSheetContent
import com.asmr.player.ui.player.MiniPlayerDisplayMode

import com.asmr.player.data.local.datastore.SettingsDataStore
import com.asmr.player.data.settings.CoverPreviewMode
import com.asmr.player.data.settings.LyricsPageSettings
import com.asmr.player.data.settings.NowPlayingHomeLayoutMode
import com.asmr.player.data.settings.NowPlayingLyricsSettings
import com.asmr.player.util.MessageManager
import com.asmr.player.ui.common.list.StableWindowInsets
import com.asmr.player.ui.theme.HuePalette
import com.asmr.player.ui.theme.PlayerTheme
import com.asmr.player.ui.theme.ThemeMode
import com.asmr.player.ui.theme.DefaultBrandPrimaryDark
import com.asmr.player.ui.theme.DefaultBrandPrimaryLight
import com.asmr.player.ui.theme.deriveHuePalette
import kotlin.math.roundToInt
import com.asmr.player.ui.theme.neutralPaletteForMode
import com.asmr.player.ui.theme.rememberDynamicHuePalette
import com.asmr.player.ui.theme.rememberDynamicHuePaletteFromVideoFrame
import com.asmr.player.ui.theme.dynamicPageContainerColor
import com.asmr.player.ui.update.AppUpdateInstallResult
import com.asmr.player.ui.update.launchDownloadedApkInstall
import com.asmr.player.ui.update.openUpdateReleasePage
import com.asmr.player.ui.common.audio.AppVolumeHearingWarningDialog
import com.asmr.player.ui.common.audio.AppVolumeWarningSessionState
import com.asmr.player.ui.common.audio.rememberAppVolumeWarningSessionState
import com.asmr.player.ui.common.audio.rememberCurrentAudioOutputRouteKind
import com.asmr.player.ui.common.audio.rememberProtectedAppVolumeChangeState
import com.asmr.player.ui.common.audio.AudioOutputRouteIcon
import com.asmr.player.ui.common.dialog.DismissOutsideBoundsOverlay
import com.asmr.player.util.AudioOutputRouteKind
import com.asmr.player.ui.common.audio.HardwareVolumeOverlay
import com.asmr.player.service.PlaybackService
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.media3.common.MediaItem
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.asmr.player.data.settings.SettingsRepository
import com.asmr.player.domain.model.AppVolume
import com.asmr.player.ui.common.audio.AppVolumeVerticalSlider
import kotlinx.coroutines.flow.MutableStateFlow

// Overlay-level UI hosts extracted from MainContainer (R2-C1b), behavior preserved.
// Cross-surface state flows only through parameters; no new data dependencies.

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
internal fun MainBottomChromeContent(
    windowSizeClass: androidx.compose.material3.windowsizeclass.WindowSizeClass,
    isPhone: Boolean,
    isLandscape: Boolean,
    currentRoute: String?,
    bottomChromeVisible: Boolean,
    useLargeBottomChrome: Boolean,
    bottomChromeBottomPadding: androidx.compose.ui.unit.Dp,
    rightPanelExpandedState: PersistedBooleanState,
    visualPrimaryRoute: String,
    primaryPagerState: androidx.compose.foundation.pager.PagerState,
    primaryPagerRoutes: List<String>,
    activePrimaryRoute: String,
    pendingPrimaryNavigationRoute: String?,
    miniPlayerVisible: Boolean,
    miniPlayerDisplayMode: com.asmr.player.ui.player.MiniPlayerDisplayMode,
    setMiniPlayerDisplayMode: (com.asmr.player.ui.player.MiniPlayerDisplayMode) -> Unit,
    miniPlayerPlayFeedbackSignal: Long,
    bottomNavItems: List<com.asmr.player.ui.nav.BottomChromeNavItem>,
    nowPlayingVisible: Boolean,
    openNowPlaying: () -> Unit,
    onShowQueue: () -> Unit,
    currentPrimaryRoute: String?,
    triggerPrimaryRouteScrollToTop: (String) -> Unit,
    openPrimaryRoute: (String) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
    settingsDataStore: com.asmr.player.data.local.datastore.SettingsDataStore
) {
        if (bottomChromeVisible) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
            ) {
                val bottomChromeHorizontalPadding = if (useLargeBottomChrome) 16.dp else 12.dp
                val isCompactWidth = windowSizeClass.widthSizeClass.isCompactWidth
                val canUseRightPanel = !isCompactWidth &&
                    !isPhone &&
                    isLandscape &&
                    (currentRoute == "library" || currentRoute == "search")
                val rightPanelExpanded = rightPanelExpandedState.value
                val rightPanelWidth = (maxWidth - 560.dp).coerceAtMost(420.dp)
                val showRightPanel = canUseRightPanel && rightPanelWidth >= 300.dp
                val reservedRightTarget = if (!showRightPanel) {
                    0.dp
                } else if (rightPanelExpanded) {
                    rightPanelWidth + 12.dp
                } else {
                    36.dp + 12.dp
                }
                val reservedRight by animateDpAsState(
                    targetValue = reservedRightTarget,
                    animationSpec = tween(durationMillis = if (rightPanelExpanded) 220 else 180),
                    label = "miniPlayerReservedRight"
                )
                val chromeWidth = (maxWidth - reservedRight - (bottomChromeHorizontalPadding * 2)).coerceAtLeast(0.dp)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .graphicsLayer { clip = false }
                        .padding(start = bottomChromeHorizontalPadding, bottom = bottomChromeBottomPadding)
                        .width(chromeWidth)
                ) {
                    PrimaryBottomChrome(
                        activeRoute = visualPrimaryRoute,
                        pagerState = primaryPagerState,
                        pagerRoutes = primaryPagerRoutes,
                        fallbackRoute = activePrimaryRoute,
                        lockedRoute = pendingPrimaryNavigationRoute,
                        miniPlayerVisible = miniPlayerVisible,
                        miniPlayerDisplayMode = miniPlayerDisplayMode,
                        miniPlayerPlayFeedbackSignal = miniPlayerPlayFeedbackSignal,
                        largeLayout = useLargeBottomChrome,
                        navItems = bottomNavItems,
                        onMiniPlayerDisplayModeChange = { nextMode ->
                            setMiniPlayerDisplayMode(nextMode)
                            scope.launch { settingsDataStore.setMiniPlayerDisplayMode(nextMode.name) }
                        },
                        onOpenNowPlaying = {
                            if (!nowPlayingVisible) {
                                openNowPlaying()
                            }
                        },
                        onOpenQueue = onShowQueue,
                        onNavigate = { route ->
                            if (pendingPrimaryNavigationRoute == null && shouldTriggerPrimaryRouteScrollToTop(
                                    requestedRoute = route,
                                    visualPrimaryRoute = visualPrimaryRoute,
                                    activePrimaryRoute = activePrimaryRoute,
                                    currentPrimaryRoute = currentPrimaryRoute
                                )) {
                                triggerPrimaryRouteScrollToTop(route)
                                return@PrimaryBottomChrome
                            }
                            openPrimaryRoute(route)
                        }
                    )
                }
            }
        }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun MainNowPlayingOverlay(
    windowSizeClass: androidx.compose.material3.windowsizeclass.WindowSizeClass,
    playerViewModel: com.asmr.player.ui.player.PlayerViewModel,
    scope: kotlinx.coroutines.CoroutineScope,
    settingsDataStore: com.asmr.player.data.local.datastore.SettingsDataStore,
    activityViewModelStoreOwner: androidx.lifecycle.ViewModelStoreOwner,
    nowPlayingVisible: Boolean,
    nowPlayingPlaylistPickerRequest: PlaylistPickerRequest?,
    nowPlayingBackdropAlpha: Float,
    colorScheme: com.asmr.player.ui.theme.AsmrColorScheme,
    sharedPlayerItem: androidx.media3.common.MediaItem?,
    coverBackgroundEnabled: Boolean,
    coverBackgroundClarity: Float,
    coverPreviewMode: com.asmr.player.data.settings.CoverPreviewMode,
    sharedPlayerBackdropAlignment: androidx.compose.ui.Alignment,
    sharedCoverDragPreviewState: com.asmr.player.ui.player.CoverDragPreviewState,
    nowPlayingVolumeEventTick: Long,
    setNowPlayingUsesInlineVolumeControl: (Boolean) -> Unit,
    setNowPlayingEqualizerVisible: (Boolean) -> Unit,
    setNowPlayingVideoFullscreen: (Boolean) -> Unit,
    closeNowPlaying: () -> Unit,
    setNowPlayingBackdropExitDurationMs: (Int) -> Unit,
    isPhone: Boolean,
    isLandscape: Boolean,
    setNowPlayingPortraitExitPending: (Boolean) -> Unit,
    setNowPlayingBackdropActive: (Boolean) -> Unit,
    onShowQueue: () -> Unit,
    onShowSleepTimer: () -> Unit,
    setNowPlayingPlaylistPickerRequest: (PlaylistPickerRequest?) -> Unit,
    nowPlayingHomeLayoutMode: com.asmr.player.data.settings.NowPlayingHomeLayoutMode,
    nowPlayingHomeLayoutHintDismissed: Boolean?,
    nowPlayingLyricsSettings: com.asmr.player.data.settings.NowPlayingLyricsSettings,
    lyricsPageSettings: com.asmr.player.data.settings.LyricsPageSettings,
    audioOutputRouteKind: com.asmr.player.util.AudioOutputRouteKind,
    appVolumeWarningSessionState: com.asmr.player.ui.common.audio.AppVolumeWarningSessionState,
    albumBatchPlaylistPickerRequest: BatchPlaylistPickerRequest?,
    setAlbumBatchPlaylistPickerRequest: (BatchPlaylistPickerRequest?) -> Unit
) {
        if (nowPlayingVisible) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInteropFilter { true }
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = nowPlayingBackdropAlpha }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colorScheme.background)
                    )
                    if (!colorScheme.isDark) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(colorScheme.primarySoft.copy(alpha = 0.14f))
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = nowPlayingBackdropAlpha }
                ) {
                    PlayerSharedBackdrop(
                        mediaItem = sharedPlayerItem,
                        enabled = coverBackgroundEnabled,
                        clarity = coverBackgroundClarity,
                        artworkAlignment = sharedPlayerBackdropAlignment
                    )
                }
                NowPlayingScreen(
                    windowSizeClass = windowSizeClass,
                    hardwareVolumeEventTick = nowPlayingVolumeEventTick,
                    onInlineVolumeControlVisibilityChanged = { setNowPlayingUsesInlineVolumeControl(it) },
                    onEqualizerVisibilityChanged = { setNowPlayingEqualizerVisible(it) },
                    onVideoFullscreenChanged = { setNowPlayingVideoFullscreen(it) },
                    onBack = closeNowPlaying,
                    onRouteExitStarted = { exitDurationMs ->
                        setNowPlayingBackdropExitDurationMs(exitDurationMs)
                        if (isPhone && isLandscape) {
                            setNowPlayingPortraitExitPending(true)
                            setNowPlayingBackdropActive(true)
                        } else {
                            setNowPlayingBackdropActive(false)
                        }
                    },
                    onShowQueue = onShowQueue,
                    onShowSleepTimer = onShowSleepTimer,
                    onOpenPlaylistPicker = { item ->
                        setNowPlayingPlaylistPickerRequest(PlaylistPickerRequest(items = listOf(item)))
                    },
                    viewModel = playerViewModel,
                    coverBackgroundEnabled = coverBackgroundEnabled,
                    coverBackgroundClarity = coverBackgroundClarity,
                    coverPreviewMode = coverPreviewMode,
                    nowPlayingHomeLayoutMode = nowPlayingHomeLayoutMode,
                    nowPlayingHomeLayoutHintDismissed = nowPlayingHomeLayoutHintDismissed,
                    onNowPlayingHomeLayoutHintShown = {
                        scope.launch { settingsDataStore.setNowPlayingHomeLayoutHintDismissed() }
                    },
                    onNowPlayingHomeLayoutModeChange = { mode ->
                        scope.launch {
                            settingsDataStore.setNowPlayingHomeLayoutMode(mode, dismissHint = true)
                        }
                    },
                    nowPlayingLyricsSettings = nowPlayingLyricsSettings,
                    lyricsPageSettings = lyricsPageSettings,
                    audioOutputRouteKind = audioOutputRouteKind,
                    warningSessionState = appVolumeWarningSessionState,
                    renderBackdrop = false,
                    sharedArtworkAlignment = sharedPlayerBackdropAlignment,
                    sharedCoverDragPreviewState = sharedCoverDragPreviewState
                )
                nowPlayingPlaylistPickerRequest?.let { request ->
                    val playlistsViewModel: PlaylistsViewModel = hiltViewModel(activityViewModelStoreOwner)
                    RoundedTopSheet(onDismissRequest = { setNowPlayingPlaylistPickerRequest(null) }) {
                        PlaylistPickerScreen(
                            windowSizeClass = windowSizeClass,
                            items = request.items,
                            onBack = { setNowPlayingPlaylistPickerRequest(null) },
                            embeddedInDialog = true,
                            viewModel = playlistsViewModel
                        )
                    }
                }
                albumBatchPlaylistPickerRequest?.let { request ->
                    val playlistsViewModel: PlaylistsViewModel = hiltViewModel(activityViewModelStoreOwner)
                    RoundedTopSheet(onDismissRequest = { setAlbumBatchPlaylistPickerRequest(null) }) {
                        PlaylistPickerScreen(
                            windowSizeClass = windowSizeClass,
                            items = request.items,
                            onBack = { setAlbumBatchPlaylistPickerRequest(null) },
                            embeddedInDialog = true,
                            viewModel = playlistsViewModel
                        )
                    }
                }
            }
        }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
internal fun MainOverlayPickers(
    showBatchPicker: Boolean,
    albumBatchPlaylistPickerRequest: BatchPlaylistPickerRequest?,
    setAlbumBatchPlaylistPickerRequest: (BatchPlaylistPickerRequest?) -> Unit,
    libraryGroupPickerAlbumId: Long?,
    setLibraryGroupPickerAlbumId: (Long?) -> Unit,
    windowSizeClass: androidx.compose.material3.windowsizeclass.WindowSizeClass,
    activityViewModelStoreOwner: androidx.lifecycle.ViewModelStoreOwner
) {
        if (showBatchPicker) {
            albumBatchPlaylistPickerRequest?.let { request ->
                val playlistsViewModel: PlaylistsViewModel = hiltViewModel(activityViewModelStoreOwner)
                RoundedTopSheet(onDismissRequest = { setAlbumBatchPlaylistPickerRequest(null) }) {
                    PlaylistPickerScreen(
                        windowSizeClass = windowSizeClass,
                        items = request.items,
                        onBack = { setAlbumBatchPlaylistPickerRequest(null) },
                        embeddedInDialog = true,
                        viewModel = playlistsViewModel
                    )
                }
            }
        }

        libraryGroupPickerAlbumId?.let { albumId ->
            val albumGroupsViewModel: AlbumGroupsViewModel = hiltViewModel(activityViewModelStoreOwner)
            RoundedTopSheet(onDismissRequest = { setLibraryGroupPickerAlbumId(null) }) {
                com.asmr.player.ui.groups.AlbumGroupPickerScreen(
                    windowSizeClass = windowSizeClass,
                    albumId = albumId,
                    onBack = { setLibraryGroupPickerAlbumId(null) },
                    embeddedInDialog = true,
                    viewModel = albumGroupsViewModel
                )
            }
        }
}

@Composable
internal fun MainVolumeOverlayHost(
    showHardwareVolumeOverlay: Boolean,
    setShowHardwareVolumeOverlay: (Boolean) -> Unit,
    hardwareVolumeOverlayBounds: androidx.compose.ui.geometry.Rect?,
    setHardwareVolumeOverlayBounds: (androidx.compose.ui.geometry.Rect?) -> Unit,
    appVolumePercent: Int,
    audioOutputRouteKind: com.asmr.player.util.AudioOutputRouteKind,
    playerViewModel: com.asmr.player.ui.player.PlayerViewModel,
    bumpVolumeOverlayHoldTick: () -> Unit,
    setHardwareVolumeOverlayInteracting: (Boolean) -> Unit,
    lastNonZeroAppVolumePercent: Int,
    appVolumeWarningSessionState: com.asmr.player.ui.common.audio.AppVolumeWarningSessionState
) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(3f),
            contentAlignment = Alignment.CenterEnd
        ) {
            if (showHardwareVolumeOverlay) {
                DismissOutsideBoundsOverlay(
                    targetBoundsInRoot = hardwareVolumeOverlayBounds,
                    onDismiss = {
                        setShowHardwareVolumeOverlay(false)
                        setHardwareVolumeOverlayBounds(null)
                    }
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 18.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                AnimatedVisibility(
                    visible = showHardwareVolumeOverlay,
                    enter = fadeIn(animationSpec = tween(140)) + slideInHorizontally(animationSpec = tween(180)) { it / 3 },
                    exit = fadeOut(animationSpec = tween(160)) + slideOutHorizontally(animationSpec = tween(180)) { it / 3 }
                ) {
                    HardwareVolumeOverlay(
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            setHardwareVolumeOverlayBounds(coordinates.boundsInRoot())
                        },
                        volumePercent = appVolumePercent,
                        audioOutputRouteKind = audioOutputRouteKind,
                        onVolumeChange = {
                            playerViewModel.setAppVolumePercent(it)
                            bumpVolumeOverlayHoldTick()
                        },
                        onToggleMute = {
                            if (appVolumePercent > 0) {
                                playerViewModel.setAppVolumePercent(0)
                            } else {
                                playerViewModel.setAppVolumePercent(
                                    lastNonZeroAppVolumePercent.coerceAtLeast(AppVolume.StepPercent)
                                )
                            }
                            bumpVolumeOverlayHoldTick()
                        },
                        onInteractionActiveChanged = { active ->
                            setHardwareVolumeOverlayInteracting(active)
                            if (!active) {
                                bumpVolumeOverlayHoldTick()
                            }
                        },
                        warningSessionState = appVolumeWarningSessionState
                    )
                }
            }
        }
}

@Composable
internal fun MainUpdateDialogsHost(
    updateState: com.asmr.player.ui.settings.AppUpdateState,
    automaticUpdateDialogDismissed: Boolean,
    setAutomaticUpdateDialogDismissed: () -> Unit,
    setAutomaticUpdateInstallRequested: () -> Unit,
    settingsViewModel: com.asmr.player.ui.settings.SettingsViewModel,
    messageManager: com.asmr.player.util.MessageManager,
    context: Context,
    colorScheme: com.asmr.player.ui.theme.AsmrColorScheme,
    forceImmersive: Boolean,
    settingsDataStore: com.asmr.player.data.local.datastore.SettingsDataStore,
    closeNowPlaying: () -> Unit,
    navigator: AppNavigator
) {
        val automaticUpdateAvailable = (updateState as? AppUpdateState.UpdateAvailable)
            ?.takeIf { it.source == UpdateCheckSource.Automatic && !automaticUpdateDialogDismissed }

        ClipboardRjNavigationPrompt(
            enabled = !forceImmersive && automaticUpdateAvailable == null,
            settingsDataStore = settingsDataStore,
            onNavigate = { rjCode ->
                closeNowPlaying()
                navigator.openAlbumDetailByRjStacked(rjCode)
            }
        )

        automaticUpdateAvailable?.let { available ->
            val release = available.release
            FlatActionDialog(
                message = "发现新版本：${release.tagName}",
                onDismissRequest = { setAutomaticUpdateDialogDismissed() },
                actions = listOf(
                    FlatDialogAction(
                        text = "立即更新",
                        tone = FlatDialogActionTone.Primary,
                        onClick = {
                            setAutomaticUpdateDialogDismissed()
                            setAutomaticUpdateInstallRequested()
                            settingsViewModel.downloadLatestApk()
                            messageManager.showInfo("开始下载更新…")
                        }
                    ),
                    FlatDialogAction(
                        text = "不再提醒",
                        tone = FlatDialogActionTone.Danger,
                        onClick = {
                            setAutomaticUpdateDialogDismissed()
                            settingsViewModel.disableAutoUpdateCheck()
                            messageManager.showInfo("已关闭启动时自动检查更新")
                        }
                    ),
                    FlatDialogAction(
                        text = "详情",
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_github),
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                        },
                        onClick = {
                            setAutomaticUpdateDialogDismissed()
                            if (!openUpdateReleasePage(context, release)) {
                                messageManager.showError("无法打开 GitHub 发布页")
                            }
                        }
                    )
                )
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "当前版本：${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.textSecondary
                    )
                    if (release.title.isNotBlank() && release.title != release.tagName) {
                        Text(
                            text = release.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.textSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (release.apkName.isNotBlank()) {
                        Text(
                            text = "安装包：${release.apkName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
}
