package com.asmr.player.main

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.asmr.player.ui.library.AlbumDetailScreen
import com.asmr.player.ui.library.albumdetail.AlbumDetailUiState
import com.asmr.player.ui.library.AlbumDetailViewModel
import com.asmr.player.ui.library.CloudSyncSelectionDialog
import com.asmr.player.ui.library.LibraryFilterScreen
import com.asmr.player.ui.library.LibraryScreen
import com.asmr.player.ui.library.LibraryViewModel
import com.asmr.player.ui.library.BulkPhase
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
import com.asmr.player.ui.groups.AlbumGroupsViewModel
import com.asmr.player.ui.playlists.PlaylistDetailScreen
import com.asmr.player.ui.playlists.PlaylistPickerScreen
import com.asmr.player.ui.playlists.PlaylistsScreen
import com.asmr.player.ui.playlists.PlaylistsViewModel
import com.asmr.player.ui.playlists.SystemPlaylistScreen
import com.asmr.player.ui.search.SearchScreen
import com.asmr.player.ui.search.SearchViewModel
import com.asmr.player.ui.settings.SettingsScreen
import com.asmr.player.ui.settings.SettingsViewModel
import com.asmr.player.ui.common.core.glassMenu
import com.asmr.player.ui.common.status.AsmrOneSiteSelector
import com.asmr.player.ui.common.status.SiteStatusTestRow
import com.asmr.player.ui.drawer.DrawerStatusViewModel
import com.asmr.player.ui.nav.AppNavigator
import com.asmr.player.ui.nav.BottomChrome
import com.asmr.player.ui.nav.Routes
import com.asmr.player.ui.nav.bottomChromeNavItems
import com.asmr.player.ui.nav.bottomChromeOverlayHeight
import com.asmr.player.ui.nav.isPrimaryRoute
import com.asmr.player.ui.nav.resolvePrimaryPagerRoutes
import com.asmr.player.ui.nav.resolvePrimaryRoute
import com.asmr.player.ui.common.list.LocalBottomOverlayPadding
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.navigation.NavBackStackEntry
import com.asmr.player.ui.common.list.rememberCalmScrollableFlingBehavior
import com.asmr.player.ui.theme.AsmrColorScheme
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.animation.*
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

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import com.asmr.player.ui.player.QueueSheetContent
import com.asmr.player.ui.player.SleepTimerSheetContent
import com.asmr.player.ui.player.MiniPlayerDisplayMode

import com.asmr.player.data.local.datastore.SettingsDataStore
import com.asmr.player.data.settings.CoverPreviewMode
import com.asmr.player.data.settings.LyricsPageSettings
import com.asmr.player.util.MessageManager
import com.asmr.player.ui.common.status.NonTouchableAppMessageOverlay
import com.asmr.player.ui.common.list.StableWindowInsets
import com.asmr.player.ui.common.status.VisibleAppMessage
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
import com.asmr.player.ui.common.audio.AppVolumeHearingWarningDialog
import com.asmr.player.ui.common.audio.AppVolumeWarningSessionState
import com.asmr.player.ui.common.audio.rememberAppVolumeWarningSessionState
import com.asmr.player.ui.common.audio.rememberCurrentAudioOutputRouteKind
import com.asmr.player.ui.common.audio.rememberProtectedAppVolumeChangeState
import com.asmr.player.ui.common.audio.AudioOutputRouteIcon
import com.asmr.player.ui.common.dialog.DismissOutsideBoundsOverlay
import com.asmr.player.util.AudioOutputRouteKind
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.media3.common.MediaItem
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asmr.player.data.settings.SettingsRepository
import com.asmr.player.domain.model.AppVolume
import com.asmr.player.ui.common.audio.AppVolumeVerticalSlider
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Stable
internal class PersistedBooleanState(
    initial: Boolean,
    private val save: (Boolean) -> Unit
) : MutableState<Boolean> {
    private var backing by mutableStateOf(initial)

    override var value: Boolean
        get() = backing
        set(value) {
            if (backing == value) return
            backing = value
            save(value)
        }

    override fun component1(): Boolean = value

    override fun component2(): (Boolean) -> Unit = { value = it }

    fun updateFromStore(value: Boolean) {
        backing = value
    }
}

@Composable
internal fun DrawerNavCardItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val isDark = colorScheme.isDark
    val unselectedColor = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF3F4F6)
    val selectedColor = if (isDark) Color(0xFF2A2A2A) else Color.White
    val containerColor = if (selected) selectedColor else unselectedColor
    val selectedContentColor = colorScheme.primaryStrong
    val contentColor = if (selected) selectedContentColor else colorScheme.textPrimary
    val elevation = if (selected || isDark) 0.dp else 2.dp
    val shape = RoundedCornerShape(18.dp)

    ElevatedCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isDark) {
                    Modifier.border(
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        shape = shape
                    )
                } else Modifier
            ),
        shape = shape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = elevation)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val iconTint = if (selected) selectedContentColor else colorScheme.textSecondary
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(containerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = iconTint
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun DrawerSiteStatusFooter(
    viewModel: DrawerStatusViewModel,
    modifier: Modifier = Modifier
) {
    val dlsite by viewModel.dlsite.collectAsStateWithLifecycle()
    val asmr by viewModel.asmr.collectAsStateWithLifecycle()
    val site by viewModel.asmrOneSite.collectAsStateWithLifecycle()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SiteStatusTestRow(
            name = "dlsite.com",
            status = dlsite,
            onTest = { viewModel.testDlsite() }
        )

        SiteStatusTestRow(
            status = asmr,
            onTest = { viewModel.testAsmrOne() },
            nameContent = {
                AsmrOneSiteSelector(
                    selectedSite = site,
                    onSiteSelected = viewModel::setAsmrOneSite
                )
            }
        )
    }
}

/**
 * 主侧边抽屉内容（导航项列表 + 渐隐遮罩 + 站点状态脚注）。
 * 从 MainContainer 提取（R2-C1b），行为保持不变。
 */
@Composable
internal fun MainDrawerContent(
    currentRoute: String?,
    navBackStackEntry: NavBackStackEntry?,
    drawerContainerColor: Color,
    colorScheme: AsmrColorScheme,
    drawerStatusViewModel: DrawerStatusViewModel,
    onOpenPrimaryRoute: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(300.dp)
            .glassMenu(
                shape = RoundedCornerShape(topEnd = 26.dp, bottomEnd = 26.dp),
                baseColor = drawerContainerColor,
                elevation = if (colorScheme.isDark) 0.dp else 6.dp,
                isDark = colorScheme.isDark
            )
    ) {
        ModalDrawerSheet(
            drawerContainerColor = Color.Transparent,
            drawerContentColor = colorScheme.onSurface,
            modifier = Modifier.fillMaxSize()
        ) {
            val navItems = listOf(
                Triple(Icons.Rounded.Home, "本地库", "library"),
                Triple(Icons.Rounded.Search, "在线搜索", "search"),
                Triple(Icons.Rounded.Favorite, "我的收藏", "playlist_system/favorites"),
                Triple(Icons.AutoMirrored.Rounded.QueueMusic, "我的列表", "playlists"),
                Triple(Icons.Rounded.Folder, "我的分组", "groups"),
                Triple(Icons.Rounded.Sync, "任务管理", "downloads"),
                Triple(Icons.Rounded.Route, "ASMR 看板", "listening_calendar"),
                Triple(Icons.Rounded.Settings, "设置", "settings")
            )

            Column(modifier = Modifier.fillMaxSize()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(com.asmr.player.R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        tint = colorScheme.onSurface,
                        modifier = Modifier.size(46.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        stringResource(com.asmr.player.R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        flingBehavior = rememberCalmScrollableFlingBehavior(),
                        contentPadding = PaddingValues(top = 10.dp, bottom = 32.dp)
                    ) {
                        items(navItems, key = { it.third }) { (icon, label, route) ->
                            val isAlbumDetailFromSearch =
                                currentRoute?.startsWith("album_detail_rj") == true
                            val isAlbumDetailFromLibrary =
                                currentRoute?.startsWith("album_detail/") == true &&
                                    !currentRoute.startsWith("album_detail_rj")
                            val isSelected = when (route) {
                                "library" -> currentRoute == route || isAlbumDetailFromLibrary
                                "search" -> currentRoute == route || isAlbumDetailFromSearch
                                "groups" -> currentRoute == route ||
                                    currentRoute?.startsWith("group/") == true
                                "playlist_system/favorites" -> {
                                    currentRoute == "playlist_system/{type}" &&
                                        navBackStackEntry?.arguments?.getString("type") == "favorites"
                                }
                                else -> currentRoute == route
                            }
                            DrawerNavCardItem(
                                icon = icon,
                                label = label,
                                selected = isSelected,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                onClick = { onOpenPrimaryRoute(route) }
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(18.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(drawerContainerColor, Color.Transparent)
                                )
                            )
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(28.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, drawerContainerColor)
                                )
                            )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                DrawerSiteStatusFooter(drawerStatusViewModel, modifier = Modifier.padding(horizontal = 18.dp))
                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }
}
