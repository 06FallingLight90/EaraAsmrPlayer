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
import com.asmr.player.util.BulkPhase
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
import com.asmr.player.ui.nav.Routes
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

import com.asmr.player.data.settings.CoverPreviewMode
import com.asmr.player.data.settings.LyricsPageSettings
import com.asmr.player.data.settings.NowPlayingHomeLayoutMode
import com.asmr.player.data.settings.NowPlayingLyricsSettings
import com.asmr.player.util.MessageManager
import com.asmr.player.util.isVideoPlaybackItem
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

// Primary pager host: Library / Search / HotListening / playlists / groups /
// settings / listening_calendar assembly. Extracted from MainContainer (R2-C1b),
// behavior preserved. Cross-surface state flows only through these parameters.
@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun MainPrimaryPagerContent(
    primaryContentStateHolder: androidx.compose.runtime.saveable.SaveableStateHolder,
    primaryPagerState: androidx.compose.foundation.pager.PagerState,
    primaryPagerBeyondBoundsPageCount: Int,
    primaryPagerFlingBehavior: androidx.compose.foundation.gestures.TargetedFlingBehavior,
    primaryPagerRoutes: List<String>,
    visualPrimaryRoute: String?,
    albumDetailTransitionActive: Boolean,
    hasOverlayRoute: Boolean,
    isAlbumDetailRoute: Boolean,
    primaryPageParallaxOffset: androidx.compose.runtime.State<androidx.compose.ui.unit.Dp>,
    primaryPagerScrollLocked: Boolean,
    setPrimaryPagerScrollLocked: (Boolean) -> Unit,
    setSettingsDetailPageVisible: (Boolean) -> Unit,
    openAlbumDetailFromSearch: (albumId: Long?, rj: String?, preferDlsitePlay: Boolean) -> Unit,
    windowSizeClass: androidx.compose.material3.windowsizeclass.WindowSizeClass,
    activityViewModelStoreOwner: androidx.lifecycle.ViewModelStoreOwner,
    navigator: AppNavigator,
    navController: NavHostController,
    libraryViewModel: com.asmr.player.ui.library.LibraryViewModel,
    playerViewModel: com.asmr.player.ui.player.PlayerViewModel,
    settingsViewModel: com.asmr.player.ui.settings.SettingsViewModel,
    scope: kotlinx.coroutines.CoroutineScope,
    libraryScrollToTopSignal: Long,
    searchScrollToTopSignal: Long,
    hotListeningScrollToTopSignal: Long,
    favoritesScrollToTopSignal: Long,
    playlistsScrollToTopSignal: Long,
    groupsScrollToTopSignal: Long,
    settingsScrollToTopSignal: Long,
    submittedSearchKeyword: String,
    submittedSearchOrderName: String,
    submittedSearchPurchasedOnly: Boolean,
    submittedSearchPresaleOnly: Boolean,
    submittedSearchChineseTranslatedOnly: Boolean,
    submittedSearchCollectedOnly: Boolean,
    submittedSearchHasSubtitle: Boolean,
    submittedSearchAllAges: Boolean,
    submittedSearchCollectedSortName: String,
    submittedSearchLocale: String,
    submittedSearchSignal: Long,
    searchAssistInitialRequest: com.asmr.player.ui.search.SearchAssistSearchRequest,
    setSearchAssistInitialRequest: (com.asmr.player.ui.search.SearchAssistSearchRequest) -> Unit,
    openNowPlaying: () -> Unit,
    requestMiniPlayerPlayFeedback: () -> Unit,
    submitMetaSearchKeyword: (String) -> Unit,
    setAlbumBatchPlaylistPickerRequest: (BatchPlaylistPickerRequest?) -> Unit,
    setLibraryGroupPickerAlbumId: (Long?) -> Unit,
    topContentPadding: androidx.compose.ui.unit.Dp
) {
                            primaryContentStateHolder.SaveableStateProvider(PRIMARY_PAGER_SAVEABLE_KEY) {
                                HorizontalPager(
                                    state = primaryPagerState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(top = topContentPadding)
                                        .graphicsLayer {
                                            translationX = if (albumDetailTransitionActive) {
                                                0f
                                            } else {
                                                primaryPageParallaxOffset.value.toPx()
                                            }
                                        },
                                    beyondViewportPageCount = primaryPagerBeyondBoundsPageCount,
                                    flingBehavior = primaryPagerFlingBehavior,
                                    userScrollEnabled = !primaryPagerScrollLocked && !hasOverlayRoute,
                                    key = { primaryPagerRoutes[it] }
                                ) { page ->
                                    val route = primaryPagerRoutes[page]
                                    val primaryRouteActive = visualPrimaryRoute == route
                                    val pagerRouteVisible = primaryPagerState.currentPage == page ||
                                        (
                                            primaryPagerState.isScrollInProgress &&
                                                primaryPagerState.targetPage == page
                                            )
                                    val primaryRouteImmediatelyActive = !hasOverlayRoute &&
                                        (primaryRouteActive || pagerRouteVisible)
                                    // ViewModel 的 StateFlow 已经持有最新页面数据；隐藏页面无需继续
                                    // 收集、排序和转换数据。目标页在横向手势开始时会立即恢复收集。
                                    // 详情页退出动画中使用已保留的主页面快照状态；等详情真正弹栈后再恢复
                                    // 数据流，避免在返回手势首帧同时启动查询、排序和列表状态转换。
                                    val primaryRouteDataActive = primaryRouteImmediatelyActive &&
                                        !isAlbumDetailRoute
                                    val primaryRouteDataActiveState = rememberUpdatedState(
                                        primaryRouteDataActive
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            // 页面内容使用独立 RenderNode 保留 display list；Pager 滚动时
                                            // 只更新图层位置，避免逐帧重录复杂列表和设置页的绘制命令。
                                            .graphicsLayer { clip = false }
                                    ) {
                                        primaryContentStateHolder.SaveableStateProvider(primaryRouteSaveableKey(route)) {
                                            when (route) {
                                        Routes.Library -> {
                                            LibraryScreen(
                                                windowSizeClass = windowSizeClass,
                                                isActive = primaryRouteActive,
                                                isDataActive = primaryRouteDataActive,
                                                scrollToTopSignal = libraryScrollToTopSignal,
                                                onAlbumClick = { album ->
                                                    AlbumCoverHintStore.recordLocalAlbum(album)
                                                    navigator.openAlbumDetail(
                                                        albumId = album.id,
                                                        rj = null
                                                    )
                                                },
                                                onPlayTracks = { album, tracks, startTrack ->
                                                    scope.launch {
                                                        if (playerViewModel.playTracksPrepared(album, tracks, startTrack)) {
                                                            requestMiniPlayerPlayFeedback()
                                                        }
                                                    }
                                                },
                                                onOpenPlaylistPicker = { item ->
                                                    setAlbumBatchPlaylistPickerRequest(BatchPlaylistPickerRequest(listOf(item)))
                                                },
                                                onOpenGroupPicker = { albumId ->
                                                    setLibraryGroupPickerAlbumId(albumId)
                                                },
                                                onOpenFilterScreen = { navController.navigateSingleTop("library_filter") },
                                                onOpenAllSongs = { navController.navigateSingleTop(Routes.AllSongs) },
                                                onSearchKeyword = submitMetaSearchKeyword,
                                                viewModel = libraryViewModel
                                            )
                                        }

                                        Routes.Search -> {
                                            val searchViewModel: SearchViewModel = hiltViewModel(activityViewModelStoreOwner)
                                            SearchScreen(
                                                windowSizeClass = windowSizeClass,
                                                isActive = primaryRouteActive,
                                                isDataActive = primaryRouteDataActive,
                                                scrollToTopSignal = searchScrollToTopSignal,
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
                                                onHorizontalPagerScrollLockChanged = { active ->
                                                    setPrimaryPagerScrollLocked(active)
                                                },
                                                onOpenSearchAssist = { request ->
                                                    setSearchAssistInitialRequest(request)
                                                    navController.navigateSingleTop(Routes.searchAssist(request.keyword))
                                                },
                                                onAlbumClick = searchAlbumClick@ { album, fromPurchasedOnly, hasResolvedDetail ->
                                                    val workNo = album.rjCode.ifBlank { album.workId }
                                                    if (workNo.isBlank()) return@searchAlbumClick
                                                    AlbumCoverHintStore.record(
                                                        albumId = album.id,
                                                        rjCode = workNo,
                                                        title = album.title,
                                                        circle = album.circle,
                                                        cv = album.cv,
                                                        coverUrl = album.coverUrl,
                                                        tags = album.tags,
                                                        ratingValue = album.ratingValue,
                                                        ratingCount = album.ratingCount,
                                                        releaseDate = album.releaseDate,
                                                        dlCount = album.dlCount,
                                                        priceJpy = album.priceJpy,
                                                        hasAsmrOne = album.hasAsmrOne,
                                                        description = album.description,
                                                        hasResolvedDlsiteInfo = hasResolvedDetail && !fromPurchasedOnly
                                                    )
                                                    openAlbumDetailFromSearch(
                                                        album.id,
                                                        workNo,
                                                        fromPurchasedOnly
                                                    )
                                                },
                                                viewModel = searchViewModel
                                            )
                                        }

                                        Routes.HotListening -> {
                                            val hotListeningViewModel: HotListeningViewModel = hiltViewModel(activityViewModelStoreOwner)
                                            HotListeningScreen(
                                                windowSizeClass = windowSizeClass,
                                                isActive = primaryRouteActive,
                                                isDataActive = primaryRouteDataActiveState,
                                                scrollToTopSignal = hotListeningScrollToTopSignal,
                                                onAlbumClick = { album ->
                                                    AlbumCoverHintStore.record(
                                                        albumId = album.id,
                                                        rjCode = album.rjCode.ifBlank { album.workId },
                                                        title = album.title,
                                                        circle = album.circle,
                                                        cv = album.cv,
                                                        coverUrl = album.coverUrl,
                                                        tags = album.tags,
                                                        ratingValue = album.ratingValue,
                                                        ratingCount = album.ratingCount,
                                                        releaseDate = album.releaseDate,
                                                        dlCount = album.dlCount,
                                                        priceJpy = album.priceJpy,
                                                        hasAsmrOne = album.hasAsmrOne,
                                                        description = album.description,
                                                        hasResolvedDlsiteInfo = true
                                                    )
                                                    navigator.openAlbumDetailByRj(album.rjCode.ifBlank { album.workId })
                                                },
                                                onSearchKeyword = submitMetaSearchKeyword,
                                                viewModel = hotListeningViewModel
                                            )
                                        }

                                        "playlist_system/favorites" -> {
                                            val playlistsViewModel: PlaylistsViewModel = hiltViewModel(activityViewModelStoreOwner)
                                            SystemPlaylistScreen(
                                                windowSizeClass = windowSizeClass,
                                                isActive = primaryRouteActive,
                                                isDataActive = primaryRouteDataActive,
                                                scrollToTopSignal = favoritesScrollToTopSignal,
                                                onPlayAll = { items, startItem ->
                                                    playerViewModel.playPlaylistItems(items, startItem)
                                                    if (startItem.isVideoPlaybackItem()) {
                                                        openNowPlaying()
                                                    } else {
                                                        requestMiniPlayerPlayFeedback()
                                                    }
                                                },
                                                viewModel = playlistsViewModel
                                            )
                                        }

                                        "playlists" -> {
                                            val playlistsViewModel: PlaylistsViewModel = hiltViewModel(activityViewModelStoreOwner)
                                            PlaylistsScreen(
                                                windowSizeClass = windowSizeClass,
                                                isActive = primaryRouteActive,
                                                isDataActive = primaryRouteDataActive,
                                                scrollToTopSignal = playlistsScrollToTopSignal,
                                                onPlaylistClick = { playlist ->
                                                    val encoded = URLEncoder.encode(playlist.name, "UTF-8")
                                                    navController.navigateSingleTop("playlist/${playlist.id}/$encoded")
                                                },
                                                viewModel = playlistsViewModel
                                            )
                                        }

                                        "groups" -> {
                                            val albumGroupsViewModel: AlbumGroupsViewModel = hiltViewModel(activityViewModelStoreOwner)
                                            com.asmr.player.ui.groups.AlbumGroupsScreen(
                                                windowSizeClass = windowSizeClass,
                                                isActive = primaryRouteActive,
                                                isDataActive = primaryRouteDataActive,
                                                scrollToTopSignal = groupsScrollToTopSignal,
                                                onGroupClick = { group ->
                                                    val encoded = encodeRouteArg(group.name)
                                                    navController.navigateSingleTop("group/${group.id}/$encoded")
                                                },
                                                viewModel = albumGroupsViewModel
                                            )
                                        }

                                        "settings" -> {
                                            SettingsScreen(
                                                windowSizeClass = windowSizeClass,
                                                isActive = primaryRouteActive,
                                                isDataActive = primaryRouteDataActive,
                                                viewModel = settingsViewModel,
                                                libraryViewModel = libraryViewModel,
                                                scrollToTopSignal = settingsScrollToTopSignal,
                                                onHorizontalControlInteractionChanged = { active ->
                                                    setPrimaryPagerScrollLocked(active)
                                                },
                                                onDetailPageChanged = { visible ->
                                                    setSettingsDetailPageVisible(visible)
                                                },
                                            )
                                        }

                                        "listening_calendar" -> {
                                            val listeningCalendarViewModel: com.asmr.player.ui.calendar.ListeningCalendarViewModel =
                                                hiltViewModel(activityViewModelStoreOwner)
                                            com.asmr.player.ui.calendar.ListeningCalendarScreen(
                                                windowSizeClass = windowSizeClass,
                                                isActive = primaryRouteActive,
                                                isDataActive = primaryRouteDataActive,
                                                onOpenDlsiteLogin = { navController.navigateSingleTop("dlsite_login") },
                                                onOpenAlbum = { session ->
                                                    AlbumCoverHintStore.record(
                                                        albumId = session.albumId.takeIf { it > 0L },
                                                        rjCode = session.rjCode,
                                                        title = session.title,
                                                        circle = session.circle,
                                                        cv = session.cv,
                                                        coverUrl = session.coverUrl,
                                                        tags = session.tags
                                                            .split(',')
                                                            .map { it.trim() }
                                                            .filter { it.isNotBlank() }
                                                    )
                                                    if (session.albumId > 0L) {
                                                        navigator.openAlbumDetail(albumId = session.albumId, rj = null)
                                                    } else if (session.rjCode.isNotBlank()) {
                                                        navigator.openAlbumDetailByRjStacked(session.rjCode)
                                                    }
                                                },
                                                viewModel = listeningCalendarViewModel
                                            )
                                        }

                                        }
                                    }
                                }
                            }
                            }
}
