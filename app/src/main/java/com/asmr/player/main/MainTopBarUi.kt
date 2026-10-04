package com.asmr.player.main

import com.asmr.player.BuildConfig
import com.asmr.player.R
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
import kotlinx.coroutines.flow.asStateFlow
import com.asmr.player.translation.LocalPageTranslationHeader
import com.asmr.player.translation.PageTranslationAction
import com.asmr.player.translation.PageTranslationHeaderAction
import com.asmr.player.translation.PageTranslationHeaderState
import com.asmr.player.translation.PageTranslationHost
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
import com.asmr.player.service.PlaybackService
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map


    // Main top bar content extracted from MainContainer (R2-C1b), behavior preserved.
    // Bulk scan progress is re-collected locally with the same StateFlow source.
@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
internal fun MainTopBarContent(
    navController: NavHostController,
    navBackStackEntry: NavBackStackEntry?,
    currentRoute: String?,
    visualPrimaryRoute: String?,
    currentScreenIsPrimary: Boolean,
    showBackButton: Boolean,
    showPrimaryBrand: Boolean,
    hasPreviousBackStackEntry: Boolean,
    albumDetailTransitionActive: Boolean,
    settingsDetailPageVisible: Boolean,
    activityViewModelStoreOwner: androidx.lifecycle.ViewModelStoreOwner,
    downloadsViewModel: com.asmr.player.ui.downloads.DownloadsViewModel,
    libraryViewModel: com.asmr.player.ui.library.LibraryViewModel,
    topBarContentColor: Color,
    colorScheme: com.asmr.player.ui.theme.AsmrColorScheme,
    materialColorScheme: androidx.compose.material3.ColorScheme,
    dynamicContainerColor: Color
) {
    val bulkProgress by libraryViewModel.bulkProgress.collectAsStateWithLifecycle()
                            Box {
                                EaraTopBarContainer {
                                    Column {
                                        Spacer(modifier = Modifier.windowInsetsTopHeight(StableWindowInsets.statusBars))
                                        CenterAlignedTopAppBar(
                                            modifier = Modifier.height(EaraMainTopBarHeight),
                                            title = {
                                                val entry = navBackStackEntry
                                                val resolvedTitleRoute = if (currentScreenIsPrimary || albumDetailTransitionActive) {
                                                    visualPrimaryRoute
                                                } else {
                                                    currentRoute
                                                }
                                                val groupName = if (resolvedTitleRoute == "group/{groupId}/{groupName}") {
                                                    decodeRouteArg(entry?.arguments?.getString("groupName").orEmpty())
                                                } else ""
                                                val playlistName = if (resolvedTitleRoute == "playlist/{playlistId}/{playlistName}") {
                                                    decodeRouteArg(entry?.arguments?.getString("playlistName").orEmpty())
                                                } else ""
                                                val systemPlaylistType = if (resolvedTitleRoute == "playlist_system/{type}") {
                                                    entry?.arguments?.getString("type").orEmpty()
                                                } else ""
                                                val appName = stringResource(R.string.app_name)
                                                val titleText = when {
                                                    resolvedTitleRoute == "library" -> "本地库"
                                                    resolvedTitleRoute == "library_filter" -> "筛选"
                                                    resolvedTitleRoute == "search" -> "在线搜索"
                                                    resolvedTitleRoute == Routes.SearchAssist -> "在线搜索"
                                                    resolvedTitleRoute == Routes.SearchAssistPattern -> "在线搜索"
                                                    resolvedTitleRoute == Routes.HotListening -> "热门收听"
                                                    resolvedTitleRoute == "playlists" -> "我的列表"
                                                    resolvedTitleRoute == "playlist/{playlistId}/{playlistName}" ->
                                                        playlistName.ifBlank { "我的列表" }
                                                    resolvedTitleRoute == "playlist_system/favorites" -> "我的收藏"
                                                    resolvedTitleRoute == "playlist_system/{type}" -> when (systemPlaylistType) {
                                                        "favorites" -> "我的收藏"
                                                        else -> "我的收藏"
                                                    }
                                                    resolvedTitleRoute == "groups" -> "我的分组"
                                                    resolvedTitleRoute == "group/{groupId}/{groupName}" ->
                                                        groupName.ifBlank { "我的分组" }
                                                    resolvedTitleRoute == "settings" -> "设置"
                                                    resolvedTitleRoute == "downloads" -> "任务管理"
                                                    resolvedTitleRoute == "listening_calendar" -> "ASMR 看板"
                                                    resolvedTitleRoute == "dlsite_login" -> "DLsite 登录"
                                                    resolvedTitleRoute?.startsWith("playlist_picker") == true -> "添加到我的列表"
                                                    resolvedTitleRoute?.startsWith("album_detail") == true -> "专辑详情"
                                                    else -> appName
                                                }
                                                AnimatedContent(
                                                    targetState = titleText,
                                                    modifier = Modifier
                                                        .height(40.dp)
                                                        .offset(y = 4.dp),
                                                    contentAlignment = Alignment.Center,
                                                    transitionSpec = {
                                                        (fadeIn(animationSpec = tween(220, easing = LinearOutSlowInEasing))
                                                            + slideInHorizontally(animationSpec = tween(220, easing = LinearOutSlowInEasing)) { it / 4 })
                                                            .togetherWith(
                                                                fadeOut(animationSpec = tween(180, easing = FastOutLinearInEasing))
                                                                    + slideOutHorizontally(animationSpec = tween(180, easing = FastOutLinearInEasing)) { -it / 4 }
                                                            )
                                                    },
                                                    label = "headerTitle"
                                                ) { targetText ->
                                                    Text(
                                                        text = targetText,
                                                        color = if (albumDetailTransitionActive) {
                                                            colorScheme.onSurface
                                                        } else {
                                                            topBarContentColor
                                                        },
                                                        style = MaterialTheme.typography.titleMedium.copy(
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    )
                                                }
                                            },
                                            windowInsets = WindowInsets(0, 0, 0, 0),
                                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                                containerColor = Color.Transparent,
                                                titleContentColor = topBarContentColor,
                                                navigationIconContentColor = topBarContentColor,
                                                actionIconContentColor = if (albumDetailTransitionActive) {
                                                    colorScheme.onSurface
                                                } else {
                                                    topBarContentColor
                                                }
                                            ),
                                            navigationIcon = {
                                                Box {
                                                    if (showPrimaryBrand || albumDetailTransitionActive) {
                                                        PrimaryTopBarBrand(
                                                            appName = stringResource(R.string.app_name),
                                                            tint = colorScheme.primaryStrong
                                                        )
                                                    }
                                                    if (showBackButton &&
                                                        !albumDetailTransitionActive &&
                                                        hasPreviousBackStackEntry
                                                    ) {
                                                        EaraTopBarIconButton(
                                                            onClick = { navController.popBackStack() }
                                                        ) {
                                                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                                                        }
                                                    }
                                                }
                                            },
                                            actions = {
                                                val headerActionRoute = if (albumDetailTransitionActive) {
                                                    visualPrimaryRoute
                                                } else {
                                                    currentRoute
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    if (headerActionRoute != null &&
                                                        (isPrimaryRoute(headerActionRoute) || headerActionRoute == "playlist_system/{type}") &&
                                                        !(headerActionRoute == "settings" && settingsDetailPageVisible)
                                                    ) {
                                                        val downloadTasks by downloadsViewModel.tasks.collectAsStateWithLifecycle()
                                                        val activeSubtitleTaskCount by downloadsViewModel.activeSubtitleTaskCount.collectAsStateWithLifecycle()
                                                        val activeDownloadCount = remember(downloadTasks) {
                                                            downloadTasks.sumOf { task ->
                                                                task.items.count {
                                                                    it.state == DownloadItemState.RUNNING || it.state == DownloadItemState.ENQUEUED
                                                                }
                                                            }
                                                        }
                                                        val activeTaskCount = activeDownloadCount + activeSubtitleTaskCount
                                                        PageTranslationHeaderAction(headerActionRoute, Modifier.padding(end = 4.dp))
                                                        Box {
                                                            EaraTopBarIconButton(
                                                                onClick = { navController.navigate("downloads") },
                                                                modifier = Modifier.padding(end = 4.dp)
                                                            ) {
                                                                Icon(Icons.Rounded.Inbox, contentDescription = "任务管理")
                                                            }
                                                            if (activeTaskCount > 0) {
                                                                Badge(
                                                                    modifier = Modifier
                                                                        .align(Alignment.TopEnd)
                                                                ) {
                                                                    Text(activeTaskCount.toString())
                                                                }
                                                            }
                                                        }
                                                    }
                                                    if (headerActionRoute == "library") {
                                                        val viewMode by libraryViewModel.libraryViewMode.collectAsStateWithLifecycle()
                                                        if (viewMode != null) {
                                                            var viewMenuExpanded by remember { mutableStateOf(false) }
                                                            Box {
                                                                val normalized = (viewMode ?: 0).coerceIn(0, 2)
                                                                val icon = when (normalized) {
                                                                    1 -> Icons.Rounded.GridView
                                                                    2 -> Icons.Rounded.Audiotrack
                                                                    else -> Icons.AutoMirrored.Rounded.ViewList
                                                                }
                                                                EaraTopBarIconButton(
                                                                    onClick = { viewMenuExpanded = true },
                                                                    modifier = Modifier.padding(end = 4.dp)
                                                                ) {
                                                                    Icon(imageVector = icon, contentDescription = "切换视图")
                                                                }
                                                                MaterialTheme(
                                                                    colorScheme = materialColorScheme.copy(
                                                                        surface = dynamicContainerColor,
                                                                        surfaceContainer = dynamicContainerColor
                                                                    )
                                                                ) {
                                                                    DropdownMenu(
                                                                        expanded = viewMenuExpanded,
                                                                        onDismissRequest = { viewMenuExpanded = false },
                                                                        modifier = Modifier.background(dynamicContainerColor)
                                                                    ) {
                                                                        DropdownMenuItem(
                                                                            text = { Text("专辑列表") },
                                                                            leadingIcon = {
                                                                                Icon(Icons.AutoMirrored.Rounded.ViewList, contentDescription = null)
                                                                            },
                                                                            onClick = {
                                                                                viewMenuExpanded = false
                                                                                libraryViewModel.setLibraryViewMode(0)
                                                                            }
                                                                        )
                                                                        HorizontalDivider(
                                                                            modifier = Modifier.padding(horizontal = 8.dp),
                                                                            thickness = 0.5.dp,
                                                                            color = materialColorScheme.outlineVariant.copy(alpha = 0.3f)
                                                                        )
                                                                        DropdownMenuItem(
                                                                            text = { Text("专辑卡片") },
                                                                            leadingIcon = {
                                                                                Icon(Icons.Rounded.GridView, contentDescription = null)
                                                                            },
                                                                            onClick = {
                                                                                viewMenuExpanded = false
                                                                                libraryViewModel.setLibraryViewMode(1)
                                                                            }
                                                                        )
                                                                        HorizontalDivider(
                                                                            modifier = Modifier.padding(horizontal = 8.dp),
                                                                            thickness = 0.5.dp,
                                                                            color = materialColorScheme.outlineVariant.copy(alpha = 0.3f)
                                                                        )
                                                                        DropdownMenuItem(
                                                                            text = { Text("音轨列表") },
                                                                            leadingIcon = {
                                                                                Icon(Icons.Rounded.Audiotrack, contentDescription = null)
                                                                            },
                                                                            onClick = {
                                                                                viewMenuExpanded = false
                                                                                libraryViewModel.setLibraryViewMode(2)
                                                                            }
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    } else if (headerActionRoute == "search") {
                                                        val searchViewModel: SearchViewModel = hiltViewModel(activityViewModelStoreOwner)
                                                        val viewMode by searchViewModel.viewMode.collectAsStateWithLifecycle()
                                                        EaraTopBarIconButton(
                                                            onClick = { searchViewModel.setViewMode(if (viewMode == 1) 0 else 1) },
                                                            modifier = Modifier.padding(end = 4.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = if (viewMode == 1) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.ViewModule,
                                                                contentDescription = if (viewMode == 1) "切换为列表视图" else "切换为卡片视图"
                                                            )
                                                        }
                                                    } else if (headerActionRoute == Routes.HotListening) {
                                                        val hotListeningViewModel: HotListeningViewModel = hiltViewModel(activityViewModelStoreOwner)
                                                        val viewMode by hotListeningViewModel.viewMode.collectAsStateWithLifecycle()
                                                        EaraTopBarIconButton(
                                                            onClick = { hotListeningViewModel.setViewMode(if (viewMode == 1) 0 else 1) },
                                                            modifier = Modifier.padding(end = 4.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = if (viewMode == 1) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.ViewModule,
                                                                contentDescription = if (viewMode == 1) "切换为列表视图" else "切换为卡片视图"
                                                            )
                                                        }
                                                    } else if (headerActionRoute == "downloads") {
                                                        val tasks by downloadsViewModel.tasks.collectAsStateWithLifecycle()
                                                        val hasActiveDownloads = remember(tasks) {
                                                            tasks.any { task ->
                                                                task.items.any { it.state == DownloadItemState.RUNNING || it.state == DownloadItemState.ENQUEUED }
                                                            }
                                                        }
                                                        val hasPausedDownloads = remember(tasks) {
                                                            tasks.any { task ->
                                                                task.items.any { it.state == DownloadItemState.PAUSED }
                                                            }
                                                        }

                                                        if (hasActiveDownloads) {
                                                            TextButton(
                                                                onClick = { downloadsViewModel.pauseAll() },
                                                                colors = ButtonDefaults.textButtonColors(contentColor = topBarContentColor)
                                                            ) { Text("全部暂停") }
                                                        } else if (hasPausedDownloads) {
                                                            TextButton(
                                                                onClick = { downloadsViewModel.resumeAll() },
                                                                colors = ButtonDefaults.textButtonColors(contentColor = topBarContentColor)
                                                            ) { Text("全部继续") }
                                                        }
                                                    }
                                                }
                                            }
                                        )

                                        val p = bulkProgress
                                        if (currentRoute == "library" && p?.phase == BulkPhase.ScanningLocal) {
                                            if (p.total > 0) {
                                                LinearProgressIndicator(
                                                    progress = { p.fraction },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            } else {
                                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                            }
                                        }
                                    }
                                }
                            }
}
