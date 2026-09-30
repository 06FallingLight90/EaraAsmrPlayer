package com.asmr.player.ui.library

import com.asmr.player.translation.translatedPageText

import android.content.Intent
import android.graphics.PathMeasure as AndroidPathMeasure
import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.CompositingStrategy as LayerCompositingStrategy
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.asmr.player.data.local.db.AppDatabaseProvider
import com.asmr.player.data.local.db.entities.LocalTreeCacheEntity
import com.asmr.player.data.remote.auth.DlsiteAuthStore
import com.asmr.player.data.remote.api.AsmrOneTrackNodeResponse
import com.asmr.player.data.remote.scraper.DLSITE_DOMAIN
import com.asmr.player.data.remote.scraper.DlsiteRecommendedWork
import com.asmr.player.data.remote.scraper.DlsiteRecommendations
import com.asmr.player.data.remote.scraper.storeSegment
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.Track
import com.asmr.player.playback.MediaItemFactory
import com.asmr.player.ui.common.audio.HorizontalStereoSpectrum
import com.asmr.player.ui.common.core.isCompactWidth
import com.asmr.player.data.remote.NetworkHeaders
import com.asmr.player.cache.CacheImageModel
import com.asmr.player.data.remote.dlsite.DlsiteLanguageEdition
import com.asmr.player.ui.dlsite.DlsitePlayViewModel
import com.asmr.player.util.DlsiteAntiHotlink
import com.asmr.player.util.SmartSortKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import com.asmr.player.data.lyrics.deriveLyricsRelativePathNoExt
import com.asmr.player.ui.common.audio.SubtitleStamp
import com.asmr.player.ui.common.cover.DiscPlaceholder
import com.asmr.player.ui.common.cover.AsmrAsyncImage
import com.asmr.player.ui.common.cover.AsmrImageLoadingPlaceholder
import com.asmr.player.ui.common.cover.EaraLogoLoadingIndicator
import com.asmr.player.ui.common.cover.NoImageLoadingIndicator
import com.asmr.player.ui.common.cover.ImagePreviewDialog
import com.asmr.player.ui.common.cover.ImagePreviewRequest
import com.asmr.player.ui.common.list.LocalBottomOverlayPadding
import com.asmr.player.ui.common.core.consumeTapThrough
import com.asmr.player.ui.groups.AlbumGroupsViewModel
import com.asmr.player.ui.common.dialog.RoundedTopSheet
import com.asmr.player.ui.groups.AlbumGroupPickerScreen
import com.asmr.player.ui.playlists.PlaylistPickerScreen
import com.asmr.player.ui.playlists.PlaylistsViewModel
import com.asmr.player.ui.player.PlayerViewModel
import com.asmr.player.ui.settings.SettingsViewModel
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.theme.AsmrPlayerTheme
import com.asmr.player.ui.theme.dynamicPageContainerColor
import com.asmr.player.util.Formatting
import com.asmr.player.util.MessageManager
import com.asmr.player.util.RemoteSubtitleSource
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AlbumDetailScreen(
    windowSizeClass: WindowSizeClass,
    albumId: Long? = null,
    rjCode: String? = null,
    onPlayTracks: (Album, List<Track>, Track) -> Unit,
    onPlayMediaItems: (List<MediaItem>, Int) -> Unit = { _, _ -> },
    onAddToQueue: (Album, Track) -> Boolean = { _, _ -> false },
    onAddMediaItemsToQueue: (List<MediaItem>) -> Unit = {},
    onAddMediaItemsToFavorites: (List<MediaItem>) -> Unit = {},
    onOpenPlaylistPicker: (MediaItem) -> Unit = {},
    onOpenDlsiteLogin: () -> Unit = {},
    onOpenAlbumByRj: (String, DlsiteRecommendedWork?) -> Unit = { _, _ -> },
    onSearchKeyword: (String) -> Unit = {},
    initialTab: Int? = null,
    playlistsViewModel: PlaylistsViewModel = hiltViewModel(),
    albumGroupsViewModel: AlbumGroupsViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    heroBlurLayerCache: AlbumHeroBlurLayerCache,
    viewModel: AlbumDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cloudSyncSelectionDialogState by viewModel.cloudSyncSelectionDialogState.collectAsStateWithLifecycle()
    val colorScheme = AsmrTheme.colorScheme
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val authStore = remember(context) { DlsiteAuthStore(context) }
    var dlsitePlayAuthSnapshot by remember(authStore) {
        mutableStateOf(readDlsitePlayAuthSnapshot(authStore))
    }
    val hasDlsitePlayCredentials = dlsitePlayAuthSnapshot.canAuthenticate
    val actionScope = rememberCoroutineScope()
    val screenKey = remember(albumId, rjCode) {
        val idPart = albumId?.takeIf { it > 0 }?.toString().orEmpty()
        val rjPart = rjCode?.trim().orEmpty().uppercase()
        if (rjPart.isNotBlank()) "album:$rjPart" else "albumId:$idPart"
    }
    val introSessionKey = remember(screenKey) { "intro:${UUID.randomUUID()}" }
    // 入口决定固定展示的二级页面：本地库->本地，在线/搜索->DL，preferDlsitePlay->DL Play。
    // 不再提供页内 tab 切换与左右滑动。
    val selectedTab = remember(albumId, initialTab) {
        initialTab?.coerceIn(0, 2) ?: if (albumId != null && albumId > 0) 0 else 1
    }
    var isInitialRouteReady by remember(screenKey) {
        mutableStateOf(viewModel.hasCachedAlbum(albumId, rjCode))
    }
    var showAsmrDownloadDialog by remember { mutableStateOf(false) }
    var showOnlineSaveDialog by remember { mutableStateOf(false) }
    var pendingOnlineSaveSelection by remember { mutableStateOf<PendingOnlineSaveSelection?>(null) }
    var batchPlaylistItems by remember { mutableStateOf<List<MediaItem>?>(null) }
    var groupPickerAlbumId by remember { mutableStateOf<Long?>(null) }
    var downloadSource by remember { mutableStateOf(OnlineDownloadSource.AsmrOne) }
    var onlineSaveSource by remember { mutableStateOf(OnlineDownloadSource.AsmrOne) }
    var downloadDisabledPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var saveDisabledPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var incrementalPreparationJob by remember { mutableStateOf<Job?>(null) }
    var metaActionKeyword by rememberSaveable { mutableStateOf<String?>(null) }

    fun openMetaActions(value: String) {
        val keyword = value.trim()
        if (keyword.isNotBlank()) metaActionKeyword = keyword
    }

    DisposableEffect(lifecycleOwner, authStore, selectedTab, viewModel) {
        fun refreshAuthSnapshot() {
            val updated = readDlsitePlayAuthSnapshot(authStore)
            actionScope.launch {
                val didAuthStateChange = updated != dlsitePlayAuthSnapshot
                dlsitePlayAuthSnapshot = updated
                if (didAuthStateChange && selectedTab == 0) {
                    incrementalPreparationJob?.cancel()
                    showAsmrDownloadDialog = false
                    showOnlineSaveDialog = false
                    viewModel.invalidateDlsitePlayAccess()
                }
            }
        }
        val preferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            refreshAuthSnapshot()
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshAuthSnapshot()
            }
        }
        authStore.registerListener(preferenceListener)
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            authStore.unregisterListener(preferenceListener)
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.setListenTogetherRjSummaryPollingEnabled(true)
    }
    LaunchedEffect(albumId, rjCode) {
        val hasCachedAlbum = viewModel.hasCachedAlbum(albumId, rjCode)
        isInitialRouteReady = hasCachedAlbum
        if (!hasCachedAlbum) withFrameNanos { }
        viewModel.loadAlbumAndAwait(albumId, rjCode, force = false)
        isInitialRouteReady = true
    }
    DisposableEffect(screenKey, viewModel) {
        onDispose {
            viewModel.setListenTogetherRjSummaryPollingEnabled(false)
            viewModel.cancelActiveLoads()
            incrementalPreparationJob?.cancel()
        }
    }
    LaunchedEffect(pendingOnlineSaveSelection) {
        val selected = pendingOnlineSaveSelection ?: return@LaunchedEffect
        pendingOnlineSaveSelection = null
        viewModel.saveOnlineSelectedToLibrary(
            selectedLeafPaths = selected.paths,
            useDlsitePlayTree = selected.useDlsitePlayTree
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AsmrTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        val isCompact = windowSizeClass.widthSizeClass.isCompactWidth
        val configuration = LocalConfiguration.current
        val useLandscapeArtworkTide = shouldUseAlbumDetailLandscapeLayout(
            compactWidth = isCompact,
            screenWidthDp = configuration.screenWidthDp,
            screenHeightDp = configuration.screenHeightDp
        )

        Column(
            modifier = when {
                isCompact || useLandscapeArtworkTide -> Modifier.fillMaxSize()
                else -> Modifier
                    .fillMaxHeight()
                    .widthIn(max = 800.dp)
                    .fillMaxWidth()
            }
        ) {
            when (val state = uiState) {
                is AlbumDetailUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EaraLogoLoadingIndicator(tint = AsmrTheme.colorScheme.primary)
                    }
                }
                is AlbumDetailUiState.Removed -> Unit
                is AlbumDetailUiState.Success -> {
                    LaunchedEffect(screenKey) {
                        if (viewModel.isInitialIntroSettled()) return@LaunchedEffect
                        delay(AlbumDetailInitialIntroDurationMs)
                        // 这只是供之后新到数据判断是否还需入场动画的生命周期标记。
                        // 已经在树上的动画会自行完整收尾，计时结束时无需强制整页重组。
                        viewModel.markInitialIntroSettled()
                    }
                    val model = state.model
                    val album = model.displayAlbum
                    val asmrOneTree = model.asmrOneTree
                    val localActionRj = remember(
                        model.baseRjCode,
                        model.localAlbum?.rjCode,
                        model.localAlbum?.workId,
                        model.localAlbum?.title,
                        model.localAlbum?.path
                    ) {
                        resolveAlbumDetailRj(model.baseRjCode, model.localAlbum)
                    }
                    val hasValidLocalRj = localActionRj.isNotBlank()
                    val localOnlineSource = when {
                        asmrOneTree.isNotEmpty() -> OnlineDownloadSource.AsmrOne
                        hasDlsitePlayCredentials && model.dlsitePlayTree.isNotEmpty() -> OnlineDownloadSource.DlsitePlay
                        else -> null
                    }
                    fun prepareIncrementalAlbumAction(
                        action: IncrementalAlbumAction,
                        source: OnlineDownloadSource,
                        tree: List<AsmrOneTrackNodeResponse>
                    ) {
                        if (tree.isEmpty()) return

                        incrementalPreparationJob?.cancel()
                        incrementalPreparationJob = actionScope.launch {
                            val existing = try {
                                viewModel.resolveLocalIncrementalSelectionPaths(tree)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: Exception) {
                                viewModel.messageManager.showError("读取本地文件状态失败，请稍后重试")
                                return@launch
                            }
                            when (action) {
                                IncrementalAlbumAction.Download -> {
                                    downloadSource = source
                                    downloadDisabledPaths = existing.downloadedPaths
                                    showAsmrDownloadDialog = true
                                }
                                IncrementalAlbumAction.Save -> {
                                    onlineSaveSource = source
                                    saveDisabledPaths = existing.savedPaths
                                    showOnlineSaveDialog = true
                                }
                            }
                        }
                    }
                    val trialDownloadTree = remember(model.dlsiteTrialTracks) {
                        buildDlsiteTrialDownloadTree(model.dlsiteTrialTracks)
                    }
                    val shouldPlayInitialAnimations = !viewModel.isInitialIntroSettled()
                    val shouldAnimateHeaderIntro = true
                    var showTagManager by remember { mutableStateOf(false) }
                    var tagManageTrack by remember { mutableStateOf<Track?>(null) }
                    var localPreviewFile by remember { mutableStateOf<LocalTreeUiEntry.File?>(null) }
                    var onlinePreviewFile by remember { mutableStateOf<AsmrTreeUiEntry.File?>(null) }
                    var imagePreviewRequest by remember { mutableStateOf<ImagePreviewRequest?>(null) }
                    var landscapeActiveListState by remember(screenKey, useLandscapeArtworkTide) {
                        mutableStateOf<LazyListState?>(null)
                    }
                    var landscapeFixedHeaderHeightPx by remember(screenKey, useLandscapeArtworkTide) {
                        mutableIntStateOf(0)
                    }
                    // 横竖屏使用的详情组合树差异很大。按布局模式隔离整个子树，旋转时先
                    // 完整移除旧树再插入新树，避免复用旧 SlotTable 位置造成结构错位。
                    key(useLandscapeArtworkTide) {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val heroDensity = LocalDensity.current
                        val landscapePageWidth = maxWidth
                        val pageContainerColor = dynamicPageContainerColor(colorScheme)
                        val landscapeArtworkSize = minOf(
                            maxWidth * 0.30f,
                            maxHeight * 0.56f
                        ).coerceIn(300.dp, 460.dp)
                        val landscapeContentTop = AlbumLandscapeArtworkTopPadding +
                            landscapeArtworkSize * 0.50f
                        val landscapeContentWaveDepth = (landscapeArtworkSize * 0.50f)
                            .coerceIn(166.dp, 220.dp)
                        val landscapeHeaderFallbackHeight = (landscapeContentWaveDepth + 36.dp)
                            .coerceIn(202.dp, 256.dp)
                        val landscapeHeaderMeasuredHeight = with(heroDensity) {
                            landscapeFixedHeaderHeightPx.toDp()
                        }
                        val landscapeDirectoryTop = if (landscapeFixedHeaderHeightPx > 0) {
                            albumLandscapeDirectoryTop(
                                headerHeight = landscapeHeaderMeasuredHeight,
                                headerLift = AlbumLandscapeHeaderLift
                            )
                        } else {
                            albumLandscapeDirectoryTop(
                                headerHeight = landscapeHeaderFallbackHeight,
                                headerLift = AlbumLandscapeHeaderLift
                            )
                        }
                        val landscapeHeaderStart = albumLandscapeHeaderStart(landscapeArtworkSize)
                        val landscapeHeaderAvailableWidth = (
                            maxWidth - landscapeHeaderStart - AlbumLandscapeHeaderEndPadding
                            ).coerceAtLeast(320.dp)
                        val heroHeightLimit = if (isCompact) {
                            maxHeight * 0.62f
                        } else {
                            maxHeight * 0.64f
                        }
                        val heroMinHeight = if (isCompact) 280.dp else 360.dp
                        val heroPreferredHeight = if (isCompact) {
                            maxWidth * 0.88f
                        } else {
                            maxWidth * 0.78f
                        }
                        val heroHeight = if (useLandscapeArtworkTide) {
                            maxHeight
                        } else {
                            heroPreferredHeight
                                .coerceAtLeast(heroMinHeight)
                                .coerceAtMost(heroHeightLimit.coerceAtLeast(heroMinHeight))
                        }
                        val contentViewportTop = if (useLandscapeArtworkTide) {
                            landscapeContentTop
                        } else {
                            heroHeight + AlbumDetailHeroContentGap
                        }
                        val contentViewportHeight = (maxHeight - contentViewportTop).coerceAtLeast(0.dp)
                        val contentFadeStartY = 0.dp
                        val contentFadeEndY = AlbumDetailScrolledContentFadeSpan

                        // 随滑动自适应缩放 hero：布局边界仍是 0%~50% 折叠。
                        // 只有展开端允许封面图继续放大，松手后缓慢回落；折叠端到 50% 后直接交给列表滚动。
                        val heroCollapseMaxPx = with(heroDensity) {
                            if (useLandscapeArtworkTide) {
                                albumLandscapeCollapseDistance(landscapeArtworkSize).toPx()
                            } else {
                                (heroHeight * 0.5f).toPx()
                            }
                        }
                        val heroVisualOvershootMaxPx = with(heroDensity) {
                            if (useLandscapeArtworkTide) 0f else (heroHeight * 0.10f).toPx()
                        }
                        val contentViewportTopPx = with(heroDensity) { contentViewportTop.toPx() }
                        val heroMotion = remember(screenKey) { AlbumDetailHeroMotionState() }
                        val scope = rememberCoroutineScope()
                        LaunchedEffect(heroCollapseMaxPx, heroVisualOvershootMaxPx) {
                            heroMotion.collapsePx = heroMotion.collapsePx.coerceIn(0f, heroCollapseMaxPx)
                            heroMotion.visualOvershootPx = heroMotion.visualOvershootPx.coerceIn(
                                -heroVisualOvershootMaxPx,
                                0f
                            )
                        }
                        DisposableEffect(heroMotion) {
                            onDispose { heroMotion.cancelVisualOvershootAnimation() }
                        }
                        val heroNestedScroll = remember(
                            heroCollapseMaxPx,
                            heroVisualOvershootMaxPx,
                            heroMotion,
                            scope
                        ) {
                            object : NestedScrollConnection {
                                private fun settleVisualOvershoot(initialVelocity: Float = 0f): Boolean {
                                    val start = heroMotion.visualOvershootPx
                                    if (abs(start) < 0.5f) return false
                                    heroMotion.cancelVisualOvershootAnimation()
                                    heroMotion.visualOvershootJob = scope.launch {
                                        animate(
                                            initialValue = start,
                                            targetValue = 0f,
                                            initialVelocity = initialVelocity,
                                            animationSpec = AlbumDetailHeroBounceBackSpec
                                        ) { value, _ ->
                                            heroMotion.visualOvershootPx = value
                                        }
                                    }
                                    return true
                                }

                                private fun dragOvershootDelta(delta: Float): Float {
                                    val progress = (-heroMotion.visualOvershootPx / heroVisualOvershootMaxPx)
                                        .coerceIn(0f, 1f)
                                    val resistance = AlbumDetailHeroOvershootResistance * (1f - progress * progress * 0.62f)
                                    return delta * resistance
                                }

                                private fun applyCollapseDelta(delta: Float): Float {
                                    if (delta == 0f) return 0f
                                    heroMotion.cancelVisualOvershootAnimation()
                                    val current = heroMotion.collapsePx.coerceIn(0f, heroCollapseMaxPx)
                                    var remaining = delta
                                    var consumed = 0f

                                    if (remaining > 0f && heroMotion.visualOvershootPx < 0f) {
                                        val visualRelease = (remaining * AlbumDetailHeroOvershootReleaseMultiplier)
                                            .coerceAtMost(-heroMotion.visualOvershootPx)
                                        if (visualRelease > 0f) {
                                            heroMotion.visualOvershootPx += visualRelease
                                            remaining -= visualRelease / AlbumDetailHeroOvershootReleaseMultiplier
                                            consumed += visualRelease / AlbumDetailHeroOvershootReleaseMultiplier
                                        }
                                    }

                                    if (remaining != 0f) {
                                        val collapseTarget = (current + remaining).coerceIn(0f, heroCollapseMaxPx)
                                        val collapseApplied = collapseTarget - current
                                        if (collapseApplied != 0f) {
                                            heroMotion.collapsePx = collapseTarget
                                            remaining -= collapseApplied
                                            consumed += collapseApplied
                                        }
                                    }

                                    if (remaining < 0f && heroVisualOvershootMaxPx > 0f) {
                                        val visualDelta = dragOvershootDelta(remaining)
                                        val visualTarget = (heroMotion.visualOvershootPx + visualDelta)
                                            .coerceIn(-heroVisualOvershootMaxPx, 0f)
                                        heroMotion.visualOvershootPx = visualTarget
                                        consumed += remaining
                                    }

                                    return consumed
                                }

                                private fun flingOvershootTarget(velocityY: Float): Float {
                                    if (velocityY <= AlbumDetailHeroFlingVelocityMin) return 0f
                                    val velocityProgress = ((velocityY - AlbumDetailHeroFlingVelocityMin) /
                                        (AlbumDetailHeroFlingVelocityMax - AlbumDetailHeroFlingVelocityMin))
                                        .coerceIn(0f, 1f)
                                    val eased = velocityProgress * velocityProgress
                                    val target = heroVisualOvershootMaxPx * AlbumDetailHeroFlingOvershootPortion * eased
                                    val cappedTarget = target.coerceAtMost(
                                        heroVisualOvershootMaxPx * AlbumDetailHeroFlingOvershootMaxPortion
                                    )
                                    return -cappedTarget
                                }

                                private fun absorbFlingOvershoot(velocityY: Float): Boolean {
                                    val target = flingOvershootTarget(velocityY)
                                    if (target >= -0.5f) return settleVisualOvershoot()
                                    heroMotion.cancelVisualOvershootAnimation()
                                    heroMotion.visualOvershootJob = scope.launch {
                                        if (target < heroMotion.visualOvershootPx) {
                                            animate(
                                                initialValue = heroMotion.visualOvershootPx,
                                                targetValue = target,
                                                animationSpec = tween(
                                                    durationMillis = AlbumDetailHeroFlingApproachMillis,
                                                    easing = FastOutSlowInEasing
                                                )
                                            ) { value, _ -> heroMotion.visualOvershootPx = value }
                                        }
                                        animate(
                                            initialValue = heroMotion.visualOvershootPx,
                                            targetValue = 0f,
                                            animationSpec = tween(
                                                durationMillis = AlbumDetailHeroFlingSettleMillis,
                                                easing = FastOutSlowInEasing
                                            )
                                        ) { value, _ -> heroMotion.visualOvershootPx = value }
                                    }
                                    return true
                                }

                                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                                    val dy = available.y
                                    // 向上浏览（手指上滑，dy<0）：先把滚动用于折叠 hero，再交给列表。
                                    if (dy < 0f && (
                                            heroMotion.collapsePx < heroCollapseMaxPx ||
                                                heroMotion.visualOvershootPx < 0f
                                            )
                                    ) {
                                        val applied = applyCollapseDelta(-dy)
                                        val consumed = if (applied != 0f) -applied else dy
                                        return Offset(0f, consumed)
                                    }
                                    return Offset.Zero
                                }

                                override fun onPostScroll(
                                    consumed: Offset,
                                    available: Offset,
                                    source: NestedScrollSource
                                ): Offset {
                                    val dy = available.y
                                    // 列表已到顶仍有下滑剩余（dy>0）：把剩余滚动用于展开 hero。
                                    if (dy > 0f && (
                                            heroMotion.collapsePx > 0f ||
                                                heroMotion.visualOvershootPx > -heroVisualOvershootMaxPx
                                            )
                                    ) {
                                        val applied = applyCollapseDelta(-dy)
                                        val released = if (applied != 0f) -applied else dy
                                        return Offset(0f, released)
                                    }
                                    return Offset.Zero
                                }

                                override suspend fun onPreFling(available: Velocity): Velocity {
                                    settleVisualOvershoot()
                                    return Velocity.Zero
                                }

                                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                                    if (available.y > 0f && heroMotion.collapsePx <= 0.5f) {
                                        absorbFlingOvershoot(available.y)
                                    } else {
                                        settleVisualOvershoot()
                                    }
                                    return Velocity.Zero
                                }
                            }
                        }

                        fun headerAlbumForTab(tab: Int): Album {
                            return if (tab == 0) (model.localAlbum ?: album) else album
                        }

                        fun shouldShowCoverLoading(tab: Int, headerAlbum: Album): Boolean {
                            val headerHasCover = headerAlbum.coverPath.trim().isNotBlank() ||
                                headerAlbum.coverUrl.trim().isNotBlank()
                            return !headerHasCover && when (tab) {
                                0 -> false
                                1 -> model.isLoadingDlsite ||
                                    model.isLoadingAsmrOne ||
                                    !model.hasResolvedInitialDlsiteTarget
                                else -> model.isLoadingDlsite ||
                                    model.isLoadingDlsitePlay ||
                                    !model.hasResolvedInitialDlsiteTarget
                            }
                        }

                        val activeHeroAlbum = headerAlbumForTab(selectedTab)
                        val showHeroCoverLoadingState = shouldShowCoverLoading(selectedTab, activeHeroAlbum)

                        val headerContent: @Composable (Int) -> Unit = { tab ->
                            val isLocalTab = tab == 0
                            val resolvedInitialTarget = model.hasResolvedInitialDlsiteTarget
                            val canUseAsmrOneTreeActions = canUseAsmrOneOnlineTreeActions(
                                selectedTab = tab,
                                hasAsmrOneTree = asmrOneTree.isNotEmpty()
                            )
                            val headerDownloadEnabled = albumHeaderDownloadEnabled(
                                selectedTab = tab,
                                hasAsmrOneTree = asmrOneTree.isNotEmpty(),
                                hasDlsitePlayTree = model.dlsitePlayTree.isNotEmpty(),
                                hasResolvedInitialDlsiteTarget = resolvedInitialTarget,
                                hasValidLocalRj = hasValidLocalRj,
                                hasDlsitePlayCredentials = hasDlsitePlayCredentials
                            )
                            val headerAlbum = headerAlbumForTab(tab)
                            val incrementalSource = when (tab) {
                                0 -> localOnlineSource
                                1 -> OnlineDownloadSource.AsmrOne
                                2 -> OnlineDownloadSource.DlsitePlay
                                else -> null
                            }
                            val incrementalTree = when (incrementalSource) {
                                OnlineDownloadSource.AsmrOne -> asmrOneTree
                                OnlineDownloadSource.DlsitePlay -> model.dlsitePlayTree
                                else -> emptyList()
                            }
                            val headerDlsiteEditions = if (isLocalTab) {
                                emptyList()
                            } else {
                                model.dlsiteEditions.ifEmpty {
                                    listOf(
                                        DlsiteLanguageEdition(
                                            workno = model.baseRjCode.ifBlank { model.rjCode },
                                            lang = "JPN",
                                            label = "日本語",
                                            displayOrder = 1
                                        )
                                    )
                                }
                            }
                            AlbumHeader(
                                album = headerAlbum,
                                dlsiteUrl = model.dlsiteWorkno.takeIf { it.isNotBlank() }?.let { "$DLSITE_DOMAIN${storeSegment()}/work/=/product_id/$it.html" }.orEmpty(),
                                asmrOneUrl = model.asmrOneWorkId?.takeIf { it.isNotBlank() }?.let { "https://asmr.one/work/$it" }.orEmpty(),
                                dlsiteEditions = headerDlsiteEditions,
                                dlsiteSelectedLang = model.dlsiteSelectedLang,
                                onDlsiteLangSelected = { viewModel.selectDlsiteLanguage(it) },
                                showSaveAction = tab != 2,
                                onDownloadClick = {
                                    incrementalSource?.let { source ->
                                        prepareIncrementalAlbumAction(
                                            action = IncrementalAlbumAction.Download,
                                            source = source,
                                            tree = incrementalTree
                                        )
                                    }
                                },
                                showDlsitePlayLossless = tab == 2,
                                onLosslessDownloadClick = {
                                    viewModel.downloadDlsitePlayLosslessArchive()
                                },
                                onSaveClick = {
                                    incrementalSource?.let { source ->
                                        prepareIncrementalAlbumAction(
                                            action = IncrementalAlbumAction.Save,
                                            source = source,
                                            tree = incrementalTree
                                        )
                                    }
                                },
                                downloadEnabled = headerDownloadEnabled,
                                losslessDownloadEnabled = tab == 2 && resolvedInitialTarget && model.dlsitePlayTree.isNotEmpty(),
                                saveEnabled = if (isLocalTab) headerDownloadEnabled else canUseAsmrOneTreeActions,
                                showGroupButton = isLocalTab && model.localAlbum != null,
                                onOpenGroupPicker = { id -> groupPickerAlbumId = id },
                                introSessionKey = introSessionKey,
                                animateIntro = shouldAnimateHeaderIntro,
                                availableWidth = if (useLandscapeArtworkTide) {
                                    landscapeHeaderAvailableWidth
                                } else {
                                    (maxWidth - AlbumDetailHorizontalPadding * 2).coerceAtLeast(0.dp)
                                },
                                messageManager = viewModel.messageManager,
                                onMetaLongClick = ::openMetaActions,
                                landscapeFloatingActions = useLandscapeArtworkTide
                            )
                        }

                        val listHeaderContent: @Composable (Int) -> Unit = { tab ->
                            if (!useLandscapeArtworkTide) {
                                headerContent(tab)
                            }
                        }

                        val landscapeFixedHeaderContent: @Composable () -> Unit = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AlbumDetailLandscapeIdentity(
                                    album = headerAlbumForTab(selectedTab),
                                    introSessionKey = introSessionKey,
                                    animateIntro = shouldPlayInitialAnimations,
                                    pageContainerColor = pageContainerColor,
                                    listenTogetherRjListenerCount = model.listenTogetherRjListenerCount,
                                    messageManager = viewModel.messageManager,
                                    onMetaLongClick = ::openMetaActions
                                )
                                headerContent(selectedTab)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(pageContainerColor)
                                .clipToBounds()
                                .zIndex(0f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .consumeTapThrough()
                            )

                            if (useLandscapeArtworkTide) {
                                AlbumDetailLandscapeArtworkBackdrop(
                                    album = activeHeroAlbum,
                                    coverSessionKey = screenKey,
                                    artworkSize = landscapeArtworkSize,
                                    pageContainerColor = pageContainerColor,
                                    collapsePx = { heroMotion.collapsePx },
                                    modifier = Modifier
                                        .matchParentSize()
                                        .zIndex(0f)
                                )
                                AlbumDetailLandscapeArtworkCover(
                                    album = activeHeroAlbum,
                                    coverSessionKey = screenKey,
                                    introSessionKey = introSessionKey,
                                    animateIntro = shouldAnimateHeaderIntro,
                                    artworkSize = landscapeArtworkSize,
                                    showCoverLoadingState = showHeroCoverLoadingState,
                                    collapsePx = { heroMotion.collapsePx },
                                    collapseMaxPx = heroCollapseMaxPx,
                                    modifier = Modifier
                                        .matchParentSize()
                                        .zIndex(1f)
                                )
                            } else {
                                AlbumDetailHeroBackground(
                                    album = activeHeroAlbum,
                                    coverSessionKey = screenKey,
                                    introSessionKey = introSessionKey,
                                    animateIntro = shouldAnimateHeaderIntro,
                                    height = heroHeight,
                                    pageContainerColor = pageContainerColor,
                                    listenTogetherRjListenerCount = model.listenTogetherRjListenerCount,
                                    showCoverLoadingState = showHeroCoverLoadingState,
                                    messageManager = viewModel.messageManager,
                                    onMetaLongClick = ::openMetaActions,
                                    blurLayerCache = heroBlurLayerCache,
                                    collapsePx = { heroMotion.collapsePx },
                                    collapseMaxPx = heroCollapseMaxPx,
                                    visualOvershootPx = { heroMotion.visualOvershootPx },
                                    visualOvershootMaxPx = heroVisualOvershootMaxPx,
                                    modifier = Modifier.align(Alignment.TopCenter)
                                )
                            }

                            LaunchedEffect(
                                selectedTab,
                                model.rjCode,
                                model.dlsiteWorkno,
                                model.hasResolvedInitialDlsiteTarget,
                                model.hasResolvedAsmrOneContent,
                                asmrOneTree.isNotEmpty(),
                                hasDlsitePlayCredentials,
                                dlsitePlayAuthSnapshot.fingerprint,
                                localActionRj,
                                isInitialRouteReady
                            ) {
                                val loadPlan = albumDetailOnlineLoadPlan(
                                    selectedTab = selectedTab,
                                    hasResolvedInitialDlsiteTarget = model.hasResolvedInitialDlsiteTarget,
                                    isInitialRouteReady = isInitialRouteReady,
                                    hasValidLocalRj = hasValidLocalRj,
                                    hasResolvedAsmrOneContent = model.hasResolvedAsmrOneContent,
                                    hasAsmrOneTree = asmrOneTree.isNotEmpty(),
                                    hasDlsitePlayCredentials = hasDlsitePlayCredentials
                                )
                                if (loadPlan.loadDlsite) {
                                    viewModel.ensureDlsiteLoaded()
                                }
                                if (loadPlan.loadAsmrOne) {
                                    viewModel.ensureAsmrOneLoaded()
                                }
                                if (loadPlan.loadDlsitePlay) {
                                    viewModel.ensureDlsitePlayLoaded(showFailureMessage = selectedTab != 0)
                                }
                            }

                            val asmrOneTreeStateKey = asmrOneDirectoryTreeStateKey(
                                currentRj = model.rjCode,
                                baseRj = model.baseRjCode
                            )
                            val asmrOneScrollStateKey = "scroll:$asmrOneTreeStateKey"
                            val landscapeContentShape = rememberAlbumLandscapeContentShape(
                                waveDepth = landscapeContentWaveDepth
                            )
                            val contentSurfaceModifier = if (useLandscapeArtworkTide) {
                                Modifier
                                    .nestedScroll(heroNestedScroll)
                                    .shadow(
                                        elevation = if (colorScheme.isDark) 3.dp else 8.dp,
                                        shape = landscapeContentShape,
                                        clip = false
                                    )
                                    .clip(landscapeContentShape)
                                    .background(
                                        colorScheme.surface.copy(
                                            alpha = if (colorScheme.isDark) 0.92f else 0.94f
                                        )
                                    )
                                    .border(
                                        width = AlbumLandscapeSurfaceBorderWidth,
                                        color = colorScheme.primary.copy(
                                            alpha = if (colorScheme.isDark) 0.20f else 0.12f
                                        ),
                                        shape = landscapeContentShape
                                    )
                            } else {
                                Modifier
                                    .nestedScroll(heroNestedScroll)
                                    .clipToBounds()
                                    .background(pageContainerColor)
                                    .albumDetailScrolledContentFade(
                                        fadeStartY = contentFadeStartY,
                                        fadeEndY = contentFadeEndY,
                                        fadeColor = pageContainerColor
                                    )
                            }

                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth()
                                    .height(
                                        if (useLandscapeArtworkTide) {
                                            albumLandscapeSurfaceHeight(
                                                contentViewportHeight = contentViewportHeight,
                                                artworkSize = landscapeArtworkSize
                                            )
                                        } else {
                                            contentViewportHeight + heroHeight * 0.5f
                                        }
                                    )
                                    .offset {
                                        IntOffset(
                                            0,
                                            (contentViewportTopPx - heroMotion.collapsePx).roundToInt()
                                        )
                                    }
                                    .then(contentSurfaceModifier)
                                    .zIndex(if (useLandscapeArtworkTide) 2f else 0f)
                            ) {
                                if (useLandscapeArtworkTide) {
                                    AlbumLandscapeCurvePlaybackProgress(
                                        waveDepth = landscapeContentWaveDepth,
                                        modifier = Modifier
                                            .matchParentSize()
                                            .zIndex(1f)
                                    )
                                }

                                Box(
                                    modifier = if (useLandscapeArtworkTide) {
                                        Modifier
                                            .align(Alignment.TopEnd)
                                            .fillMaxHeight()
                                            .width(
                                                (landscapePageWidth - landscapeHeaderStart)
                                                    .coerceAtLeast(320.dp)
                                            )
                                            .padding(top = landscapeDirectoryTop)
                                    } else {
                                        Modifier.fillMaxSize()
                                    }
                                ) {
                                    when (selectedTab) {
                                    0 -> {
                                        val local = model.localAlbum
                                        if (local != null) {
                                            val localTreeStateKey = remember(albumId, rjCode, local.id) {
                                                val rjNorm = rjCode?.trim().orEmpty().uppercase()
                                                when {
                                                    albumId != null && albumId > 0 -> "localTree:id:$albumId"
                                                    rjNorm.isNotBlank() -> "localTree:rj:$rjNorm"
                                                    else -> "localTree:localId:${local.id}"
                                                }
                                            }
                                            AlbumLocalBreadcrumbTabV2(
                                                stateKey = localTreeStateKey,
                                                initialCurrentPath = viewModel.getPreferredTreeCurrentPath(localTreeStateKey)
                                                    .ifBlank { viewModel.getTreeCurrentPath(localTreeStateKey) },
                                                onPersistCurrentPath = { path ->
                                                    viewModel.persistTreeCurrentPath(localTreeStateKey, path)
                                                },
                                                initialScroll = viewModel.getListScrollPosition("scroll:$localTreeStateKey"),
                                                onPersistScroll = { index, offset ->
                                                    viewModel.persistListScrollPosition("scroll:$localTreeStateKey", index, offset)
                                                },
                                                topContentPadding = 0.dp,
                                                album = local,
                                                header = { listHeaderContent(0) },
                                                onPlayMediaItems = onPlayMediaItems,
                                                onAddToQueue = { track ->
                                                    onAddToQueue(local, track)
                                                },
                                                onAddMediaItemsToQueue = onAddMediaItemsToQueue,
                                                onAddMediaItemsToFavorites = onAddMediaItemsToFavorites,
                                                onOpenBatchPlaylistPicker = { items -> batchPlaylistItems = items },
                                                preferredCurrentPath = viewModel.getPreferredTreeCurrentPath(localTreeStateKey),
                                                onTogglePreferredCurrentPath = { path, enabled ->
                                                    if (enabled) {
                                                        viewModel.persistPreferredTreeCurrentPath(localTreeStateKey, path)
                                                        viewModel.messageManager.showSuccess("已设为默认打开目录")
                                                    } else {
                                                        viewModel.clearPreferredTreeCurrentPath(localTreeStateKey)
                                                    }
                                                },
                                                onAddToPlaylist = { track ->
                                                    val target = PlaylistAddTarget.fromTrack(local, track)
                                                    onOpenPlaylistPicker(target.toMediaItem())
                                                },
                                                onDownloadOnlineTrack = { track, relativePath ->
                                                    viewModel.downloadSavedOnlineTrack(track, relativePath)
                                                },
                                                onManageTrackTags = { track ->
                                                    tagManageTrack = track
                                                },
                                                onRemoveTrack = { track ->
                                                    if (track.id > 0L) libraryViewModel.removeTrackFromAlbum(track.id)
                                                },
                                                onDeleteTreeEntry = { target, onComplete ->
                                                    libraryViewModel.deleteAlbumTreeEntry(
                                                        album = local,
                                                        target = target,
                                                        onComplete = onComplete,
                                                    )
                                                },
                                                onSetCoverFromImage = { pathOrUri ->
                                                    viewModel.setLocalCoverPath(pathOrUri)
                                                },
                                                onPreviewImages = { request -> imagePreviewRequest = request },
                                                onPreviewFile = { localPreviewFile = it },
                                                onSubtitleGenerationError = viewModel.messageManager::showError,
                                                onSubtitleGenerationUnavailable = viewModel.messageManager::showWarning,
                                                onSubtitleGenerationQueued = viewModel.messageManager::showInfo,
                                                onListStateAvailable = { landscapeActiveListState = it },
                                                animateIntro = shouldPlayInitialAnimations
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (albumId != null && albumId > 0) {
                                                    EaraLogoLoadingIndicator(tint = AsmrTheme.colorScheme.primary)
                                                } else {
                                                    Text("未下载到本地")
                                                }
                                            }
                                        }
                                    }
                                    1 -> AlbumDlsiteInfoBreadcrumbTabV2(
                                        album = album,
                                        header = { listHeaderContent(1) },
                                        galleryUrls = model.dlsiteGalleryUrls,
                                        trialTracks = model.dlsiteTrialTracks,
                                        trialDownloadEnabled = trialDownloadTree.isNotEmpty(),
                                        isLoading = model.isLoadingDlsite,
                                        isAwaitingInitialLoad = !model.hasLoadedInitialDlsiteContent,
                                        isAwaitingAsmrOneLoad = !model.hasResolvedAsmrOneContent &&
                                            asmrOneTree.isEmpty(),
                                        hasResolvedAsmrOneContent = model.hasResolvedAsmrOneContent,
                                        asmrOneTree = asmrOneTree,
                                        isLoadingAsmrOne = model.isLoadingAsmrOne,
                                        isLoadingTrial = model.isLoadingDlsiteTrial,
                                        onRefreshAsmrOne = { viewModel.refreshAsmrOneSection() },
                                        onRefreshTrial = { viewModel.refreshDlsiteTrialSection() },
                                        onDownloadTrial = {
                                            downloadDisabledPaths = emptySet()
                                            downloadSource = OnlineDownloadSource.DlsiteTrial
                                            showAsmrDownloadDialog = true
                                        },
                                        onPlayTracks = onPlayTracks,
                                        onPlayMediaItems = onPlayMediaItems,
                                        onAddToQueue = { track ->
                                            onAddToQueue(album, track)
                                        },
                                        onAddMediaItemsToQueue = onAddMediaItemsToQueue,
                                        onAddMediaItemsToFavorites = onAddMediaItemsToFavorites,
                                        onOpenBatchPlaylistPicker = { items -> batchPlaylistItems = items },
                                        onDownloadOne = { relPath ->
                                            viewModel.downloadAsmrOneSelected(setOf(relPath))
                                        },
                                        onAddToPlaylistOne = { relPath ->
                                            val target = PlaylistAddTarget.fromAsmrOne(album, asmrOneTree, relPath) ?: return@AlbumDlsiteInfoBreadcrumbTabV2
                                            onOpenPlaylistPicker(target.toMediaItem())
                                        },
                                        onAddToPlaylist = { track ->
                                            val target = PlaylistAddTarget.fromTrack(album, track)
                                            onOpenPlaylistPicker(target.toMediaItem())
                                        },
                                        onPreviewImages = { request -> imagePreviewRequest = request },
                                        onPreviewFile = { onlinePreviewFile = it },
                                        treeStateKey = asmrOneTreeStateKey,
                                        initialCurrentPath = viewModel.getTreeCurrentPath(asmrOneTreeStateKey),
                                        topContentPadding = 0.dp,
                                        animateIntro = shouldPlayInitialAnimations,
                                        onPersistCurrentPath = { path ->
                                            viewModel.persistTreeCurrentPath(asmrOneTreeStateKey, path)
                                        },
                                        initialScroll = viewModel.getListScrollPosition(asmrOneScrollStateKey),
                                        onPersistScroll = { index, offset ->
                                            viewModel.persistListScrollPosition(asmrOneScrollStateKey, index, offset)
                                        },
                                        onListStateAvailable = { landscapeActiveListState = it },
                                        showPortraitSimilarWorks = !useLandscapeArtworkTide,
                                        portraitSimilarWorksContent = {
                                            AlbumDetailPortraitSimilarWorksRow(
                                                seedRjCode = model.baseRjCode.ifBlank { model.rjCode },
                                                seedMetadata = model.dlsiteInfo ?: model.displayAlbum,
                                                isRouteReady = isInitialRouteReady,
                                                onOpenAlbumByRj = onOpenAlbumByRj,
                                                viewModel = viewModel
                                            )
                                        },
                                        dlsiteRecommendations = model.dlsiteRecommendations,
                                        onOpenAlbumByRj = onOpenAlbumByRj,
                                        loadRemoteFileSize = { viewModel.loadRemoteFileSize(it) }
                                    )
                                    else -> AlbumDlsitePlayBreadcrumbTabV2(
                                        header = { listHeaderContent(2) },
                                        album = album,
                                        rjCode = model.rjCode,
                                        tree = model.dlsitePlayTree,
                                        isLoading = model.isLoadingDlsitePlay,
                                        shouldAutoLoad = selectedTab == 2 && model.hasResolvedInitialDlsiteTarget,
                                        isAwaitingInitialTarget = selectedTab == 2 && !model.hasResolvedInitialDlsiteTarget,
                                        hasResolvedDlsitePlayContent = model.hasResolvedDlsitePlayContent,
                                        onOpenLogin = onOpenDlsiteLogin,
                                        onEnsureLoaded = { viewModel.ensureDlsitePlayLoaded() },
                                        onPlayMediaItems = onPlayMediaItems,
                                        onAddToQueue = { track ->
                                            onAddToQueue(album, track)
                                        },
                                        onAddMediaItemsToQueue = onAddMediaItemsToQueue,
                                        onAddMediaItemsToFavorites = onAddMediaItemsToFavorites,
                                        onOpenBatchPlaylistPicker = { items -> batchPlaylistItems = items },
                                        onDownloadOne = { relPath ->
                                            viewModel.downloadDlsitePlaySelected(setOf(relPath))
                                        },
                                        onPreviewImages = { request -> imagePreviewRequest = request },
                                        onPreviewFile = { onlinePreviewFile = it },
                                        prepareImagePreview = viewModel::prepareDlsitePlayImagePreview,
                                        treeStateKey = "tree:dlsitePlay:${model.baseRjCode.ifBlank { model.rjCode }.trim().uppercase()}",
                                        initialCurrentPath = viewModel.getTreeCurrentPath("tree:dlsitePlay:${model.baseRjCode.ifBlank { model.rjCode }.trim().uppercase()}"),
                                        topContentPadding = 0.dp,
                                        animateIntro = shouldPlayInitialAnimations,
                                        onPersistCurrentPath = { path ->
                                            val rj = model.baseRjCode.ifBlank { model.rjCode }.trim().uppercase()
                                            viewModel.persistTreeCurrentPath("tree:dlsitePlay:$rj", path)
                                        },
                                        initialScroll = viewModel.getListScrollPosition("scroll:tree:dlsitePlay:${model.baseRjCode.ifBlank { model.rjCode }.trim().uppercase()}"),
                                        onPersistScroll = { index, offset ->
                                            viewModel.persistListScrollPosition("scroll:tree:dlsitePlay:${model.baseRjCode.ifBlank { model.rjCode }.trim().uppercase()}", index, offset)
                                        },
                                        onListStateAvailable = { landscapeActiveListState = it },
                                        loadRemoteFileSize = { viewModel.loadRemoteFileSize(it) }
                                    )
                                    }
                                }

                                if (useLandscapeArtworkTide) {
                                    AlbumDetailLandscapeSimilarWorksPane(
                                        seedRjCode = model.baseRjCode.ifBlank { model.rjCode },
                                        seedMetadata = model.dlsiteInfo ?: model.displayAlbum,
                                        isRouteReady = isInitialRouteReady,
                                        onOpenAlbumByRj = onOpenAlbumByRj,
                                        viewModel = viewModel,
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .width(landscapeHeaderStart)
                                            .albumLandscapePaneViewportHeight(
                                                collapsePx = { heroMotion.collapsePx },
                                                collapseMaxPx = heroCollapseMaxPx
                                            )
                                            .padding(
                                                start = 20.dp,
                                                end = 12.dp,
                                                top = landscapeContentWaveDepth * 1.10f
                                            )
                                    )

                                }
                            }

                            if (useLandscapeArtworkTide) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .width(
                                            (landscapePageWidth - landscapeHeaderStart)
                                                .coerceAtLeast(320.dp)
                                        )
                                        .wrapContentHeight()
                                        .offset {
                                            IntOffset(
                                                0,
                                                (
                                                    contentViewportTopPx -
                                                        AlbumLandscapeHeaderLift.toPx() -
                                                        heroMotion.collapsePx
                                                    ).roundToInt()
                                            )
                                        }
                                        .padding(end = AlbumLandscapeHeaderEndPadding)
                                        .onSizeChanged { size ->
                                            landscapeFixedHeaderHeightPx = size.height
                                        }
                                        .pointerInput(
                                            heroCollapseMaxPx,
                                            landscapeActiveListState
                                        ) {
                                            detectVerticalDragGestures { change, dragAmount ->
                                                val requestedScroll = -dragAmount
                                                var consumedScroll = 0f

                                                if (requestedScroll > 0f) {
                                                    val currentCollapse = heroMotion.collapsePx
                                                    val targetCollapse = (currentCollapse + requestedScroll)
                                                        .coerceIn(0f, heroCollapseMaxPx)
                                                    val appliedCollapse = targetCollapse - currentCollapse
                                                    if (appliedCollapse != 0f) {
                                                        heroMotion.cancelVisualOvershootAnimation()
                                                        heroMotion.collapsePx = targetCollapse
                                                        consumedScroll += appliedCollapse
                                                    }
                                                    val listDelta = requestedScroll - appliedCollapse
                                                    if (listDelta > 0f) {
                                                        consumedScroll += landscapeActiveListState
                                                            ?.dispatchRawDelta(listDelta)
                                                            ?: 0f
                                                    }
                                                } else if (requestedScroll < 0f) {
                                                    val listConsumed = landscapeActiveListState
                                                        ?.dispatchRawDelta(requestedScroll)
                                                        ?: 0f
                                                    consumedScroll += listConsumed
                                                    val collapseDelta = requestedScroll - listConsumed
                                                    if (collapseDelta < 0f) {
                                                        val currentCollapse = heroMotion.collapsePx
                                                        val targetCollapse = (currentCollapse + collapseDelta)
                                                            .coerceIn(0f, heroCollapseMaxPx)
                                                        val appliedCollapse = targetCollapse - currentCollapse
                                                        if (appliedCollapse != 0f) {
                                                            heroMotion.cancelVisualOvershootAnimation()
                                                            heroMotion.collapsePx = targetCollapse
                                                            consumedScroll += appliedCollapse
                                                        }
                                                    }
                                                }

                                                if (consumedScroll != 0f) change.consume()
                                            }
                                        }
                                        .zIndex(3f)
                                ) {
                                    landscapeFixedHeaderContent()
                                }
                            }

                        }
                    }
                }

                val canSaveOnline = if (selectedTab == 0) {
                    hasValidLocalRj && localOnlineSource != null
                } else {
                    canUseAsmrOneOnlineTreeActions(
                        selectedTab = selectedTab,
                        hasAsmrOneTree = asmrOneTree.isNotEmpty()
                    )
                }
                if (showAsmrDownloadDialog) {
                    val downloadTree = when (downloadSource) {
                        OnlineDownloadSource.AsmrOne -> asmrOneTree
                        OnlineDownloadSource.DlsitePlay -> model.dlsitePlayTree
                        OnlineDownloadSource.DlsiteTrial -> trialDownloadTree
                    }
                    AsmrOneDownloadDialog(
                        albumTitle = album.title,
                        trackTree = downloadTree,
                        disabledPaths = downloadDisabledPaths,
                        onDismiss = { showAsmrDownloadDialog = false },
                        onConfirm = { selected ->
                            when (downloadSource) {
                                OnlineDownloadSource.AsmrOne -> viewModel.downloadAsmrOneSelected(selected)
                                OnlineDownloadSource.DlsitePlay -> viewModel.downloadDlsitePlaySelected(selected)
                                OnlineDownloadSource.DlsiteTrial -> viewModel.downloadDlsiteTrialSelected(selected)
                            }
                            showAsmrDownloadDialog = false
                        }
                    )
                }

                if (showOnlineSaveDialog && canSaveOnline) {
                    val saveTree = when (onlineSaveSource) {
                        OnlineDownloadSource.DlsitePlay -> model.dlsitePlayTree
                        else -> asmrOneTree
                    }
                    OnlineSaveDialog(
                        albumTitle = album.title,
                        trackTree = saveTree,
                        disabledPaths = saveDisabledPaths,
                        onDismiss = { showOnlineSaveDialog = false },
                        onConfirm = { selected ->
                            pendingOnlineSaveSelection = PendingOnlineSaveSelection(
                                paths = selected,
                                useDlsitePlayTree = onlineSaveSource == OnlineDownloadSource.DlsitePlay
                            )
                            showOnlineSaveDialog = false
                        }
                    )
                }

                groupPickerAlbumId?.let { targetAlbumId ->
                    RoundedTopSheet(
                        onDismissRequest = { groupPickerAlbumId = null },
                        color = MaterialTheme.colorScheme.background,
                        contentColor = colorScheme.textPrimary
                    ) {
                        AlbumGroupPickerScreen(
                            windowSizeClass = windowSizeClass,
                            albumId = targetAlbumId,
                            onBack = { groupPickerAlbumId = null },
                            embeddedInDialog = true
                        )
                    }
                }

                batchPlaylistItems?.let { items ->
                    RoundedTopSheet(
                        onDismissRequest = { batchPlaylistItems = null },
                        color = MaterialTheme.colorScheme.background,
                        contentColor = colorScheme.textPrimary
                    ) {
                        PlaylistPickerScreen(
                            windowSizeClass = windowSizeClass,
                            items = items,
                            onBack = { batchPlaylistItems = null },
                            embeddedInDialog = true
                        )
                    }
                }

                if (localPreviewFile != null) {
                    FilePreviewDialog(
                        title = localPreviewFile!!.title,
                        absolutePath = localPreviewFile!!.absolutePath,
                        fileType = localPreviewFile!!.fileType,
                        messageManager = viewModel.messageManager,
                        loadOnlineText = viewModel::loadOnlineTextPreview,
                        onDismiss = { localPreviewFile = null }
                    )
                }

                if (onlinePreviewFile != null) {
                    FilePreviewDialog(
                        title = onlinePreviewFile!!.title,
                        absolutePath = onlinePreviewFile!!.url ?: "",
                        fileType = onlinePreviewFile!!.fileType,
                        messageManager = viewModel.messageManager,
                        loadOnlineText = viewModel::loadOnlineTextPreview,
                        onDismiss = { onlinePreviewFile = null }
                    )
                }

                imagePreviewRequest?.let { request ->
                    ImagePreviewDialog(
                        request = request,
                        messageManager = viewModel.messageManager,
                        onDismiss = { imagePreviewRequest = null }
                    )
                }

                metaActionKeyword?.let { keyword ->
                    val searchBlockedKeywords by settingsViewModel.searchBlockedKeywords.collectAsStateWithLifecycle()
                    AlbumMetaActionDialog(
                        keyword = keyword,
                        onDismissRequest = { metaActionKeyword = null },
                        onSearch = onSearchKeyword,
                        onCreatePlaylist = playlistsViewModel::createPlaylist,
                        onCreateGroup = albumGroupsViewModel::createGroup,
                        onAddBlockedKeyword = { value ->
                            val normalized = value.trim()
                            if (normalized.isNotBlank()) {
                                val exists = searchBlockedKeywords.any {
                                    it.equals(normalized, ignoreCase = true)
                                }
                                settingsViewModel.addSearchBlockedKeyword(normalized)
                                if (exists) {
                                    viewModel.messageManager.showInfo("屏蔽词已存在：$normalized")
                                } else {
                                    viewModel.messageManager.showSuccess("已添加屏蔽词：$normalized")
                                }
                            }
                        },
                    )
                }

                val track = tagManageTrack
                if ((track != null && track.id > 0L) || showTagManager) {
                    val availableTags by viewModel.availableTags.collectAsStateWithLifecycle()
                    val userTagsByTrackId by viewModel.userTagsByTrackId.collectAsStateWithLifecycle()
                    if (track != null && track.id > 0L) {
                        TagAssignDialog(
                            title = track.title,
                            inheritedTags = album.tags,
                            userTags = userTagsByTrackId[track.id].orEmpty(),
                            allTags = availableTags,
                            onApplyUserTags = { list ->
                                viewModel.setUserTagsForTrack(track.id, list)
                                tagManageTrack = null
                            },
                            onDismiss = { tagManageTrack = null },
                            onOpenTagManager = { showTagManager = true }
                        )
                    }

                    if (showTagManager) {
                        Dialog(
                            onDismissRequest = { showTagManager = false },
                            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                        ) {
                            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                                TagManagerSheet(
                                    tags = availableTags,
                                    onRename = { tagId, newName -> libraryViewModel.renameUserTag(tagId, newName) },
                                    onDelete = { tagId -> libraryViewModel.deleteUserTag(tagId) },
                                    onClose = { showTagManager = false }
                                )
                            }
                        }
                    }
                }
            }
                is AlbumDetailUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        cloudSyncSelectionDialogState?.let { dialogState ->
            CloudSyncSelectionDialog(
                state = dialogState,
                onSelect = viewModel::confirmCloudSyncSelection,
                onCancel = viewModel::cancelCloudSyncSelection
            )
        }
    }
}

