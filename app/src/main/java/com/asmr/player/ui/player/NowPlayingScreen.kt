package com.asmr.player.ui.player

import android.content.Intent
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import com.asmr.player.ui.common.core.isCompactWidth
import com.asmr.player.ui.common.core.isLandscape
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import com.asmr.player.R
import com.asmr.player.main.HardwareVolumeOverlay
import com.asmr.player.cache.CachePolicy
import com.asmr.player.cache.ImageCacheEntryPoint
import com.asmr.player.data.lyrics.lyricsTargetContextFromMediaItem
import com.asmr.player.data.settings.CoverPreviewMode
import com.asmr.player.data.settings.LyricsPageSettings
import com.asmr.player.data.settings.NowPlayingHomeLayoutMode
import com.asmr.player.data.settings.NowPlayingLyricsSettings
import com.asmr.player.ui.common.cover.AsmrAsyncImage
import com.asmr.player.ui.common.audio.AudioOutputRouteIcon
import com.asmr.player.ui.common.audio.HorizontalStereoSpectrum
import com.asmr.player.ui.common.list.rememberCalmScrollableFlingBehavior
import com.asmr.player.ui.common.dialog.DismissOutsideBoundsOverlay
import com.asmr.player.ui.common.audio.AppVolumeHearingWarningDialog
import com.asmr.player.ui.common.audio.AppVolumeSlider
import com.asmr.player.ui.common.audio.AppVolumeWarningSessionState
import com.asmr.player.domain.model.AppVolume
import com.asmr.player.playback.PlaybackSnapshot
import com.asmr.player.ui.common.audio.EqualizerPanel
import com.asmr.player.ui.common.dialog.PlayerModalSheet
import com.asmr.player.ui.common.audio.rememberProtectedAppVolumeChangeState
import com.asmr.player.ui.common.cover.DiscPlaceholder
import com.asmr.player.ui.common.list.smoothScrollToIndex
import com.asmr.player.ui.common.dialog.TagAssignDialog
import com.asmr.player.service.AudioOutputRouteKind
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.util.Formatting
import com.asmr.player.util.SubtitleEntry
import com.asmr.player.util.SubtitleIndexFinder
import com.asmr.player.listentogether.ListenTogetherStatus
import com.asmr.player.listentogether.ListenTogetherUiState
import dagger.hilt.android.EntryPointAccessors
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.asmr.player.ui.player.nowplaying.ArtworkBox
import com.asmr.player.ui.player.nowplaying.currentSliceIdForPosition
import com.asmr.player.ui.player.nowplaying.multilineLyricsReserveHeight
import com.asmr.player.ui.player.nowplaying.NowPlayingFullscreenVideo
import com.asmr.player.ui.player.nowplaying.NowPlayingLyricsPreview
import com.asmr.player.ui.player.nowplaying.NowPlayingLyricsSurface
import com.asmr.player.ui.player.nowplaying.nowPlayingLyricTypographyMetrics
import com.asmr.player.ui.player.nowplaying.NowPlayingVideoPlayerCoordinator
import com.asmr.player.ui.player.nowplaying.PlaybackControls
import com.asmr.player.ui.player.nowplaying.PlayerProgress
import com.asmr.player.ui.player.nowplaying.PlayerSurfaceHeader
import com.asmr.player.ui.player.nowplaying.rememberPlayerVideoAspectRatio
import com.asmr.player.ui.player.nowplaying.SliceOverviewBar
import com.asmr.player.ui.player.nowplaying.SliceTimeEditDialog
import com.asmr.player.ui.player.nowplaying.TabletLandscapeQueuePanel
import com.asmr.player.ui.player.nowplaying.VolumeControl

@Composable
@androidx.media3.common.util.UnstableApi
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
internal fun NowPlayingScreen(
    windowSizeClass: WindowSizeClass,
    hardwareVolumeEventTick: Long,
    onInlineVolumeControlVisibilityChanged: (Boolean) -> Unit = {},
    onEqualizerVisibilityChanged: (Boolean) -> Unit = {},
    onVideoFullscreenChanged: (Boolean) -> Unit = {},
    onBack: () -> Unit,
    onRouteExitStarted: (exitDurationMs: Int) -> Unit = {},
    onShowQueue: () -> Unit,
    onShowSleepTimer: () -> Unit,
    onOpenPlaylistPicker: (MediaItem) -> Unit,
    viewModel: PlayerViewModel,
    coverBackgroundEnabled: Boolean,
    coverBackgroundClarity: Float,
    coverPreviewMode: CoverPreviewMode,
    nowPlayingHomeLayoutMode: NowPlayingHomeLayoutMode,
    nowPlayingHomeLayoutHintDismissed: Boolean?,
    onNowPlayingHomeLayoutHintShown: () -> Unit,
    onNowPlayingHomeLayoutModeChange: (NowPlayingHomeLayoutMode) -> Unit,
    nowPlayingLyricsSettings: NowPlayingLyricsSettings,
    lyricsPageSettings: LyricsPageSettings,
    audioOutputRouteKind: AudioOutputRouteKind,
    warningSessionState: AppVolumeWarningSessionState,
    renderBackdrop: Boolean = true,
    sharedArtworkAlignment: Alignment? = null,
    sharedCoverDragPreviewState: CoverDragPreviewState? = null,
    enableStaggeredRouteEntry: Boolean = true,
    lyricsViewModel: LyricsViewModel = hiltViewModel()
) {
    val staticPlayback by remember(viewModel) {
        viewModel.playback
            .map { it.toStaticPlayback() }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = PlaybackSnapshot().toStaticPlayback())
    val playback = staticPlayback.toSnapshot(positionMs = 0L)
    val resolvedDurationMs by viewModel.resolvedDurationMs.collectAsStateWithLifecycle()
    val sliceUiState by viewModel.sliceUiState.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val listenTogetherUiState by viewModel.listenTogetherUiState.collectAsStateWithLifecycle()
    val lyricsState by lyricsViewModel.uiState.collectAsStateWithLifecycle()
    val item = playback.currentMediaItem
    val metadata = item?.mediaMetadata
    val canBindManualLyrics = lyricsTargetContextFromMediaItem(item) != null
    val colorScheme = AsmrTheme.colorScheme
    val uriText = item?.localConfiguration?.uri?.toString().orEmpty()
    val isOnlineMedia = remember(uriText, item?.mediaId) { item.isOnlineMedia() }
    val artworkModel = remember(metadata?.artworkUri) {
        sanitizeBackdropArtworkModel(metadata?.artworkUri)
    }
    val mimeType = item?.localConfiguration?.mimeType.orEmpty()
    val ext = uriText.substringBefore('#').substringBefore('?').substringAfterLast('.', "").lowercase()
    val isVideo = metadata?.extras?.getBoolean("is_video") == true ||
        mimeType.startsWith("video/") ||
        ext in setOf("mp4", "m4v", "webm", "mkv", "mov")
    
    var showEqualizer by remember { mutableStateOf(false) }
    val tagViewModel: NowPlayingTagViewModel = hiltViewModel()
    val tagDialog by tagViewModel.dialogState.collectAsStateWithLifecycle()
    val availableTags by tagViewModel.availableTags.collectAsStateWithLifecycle()
    val playerArtworkBackdropEnabled = coverBackgroundEnabled && !isVideo
    val playerThemeColors = rememberPlayerThemeColors(
        mediaItem = item,
        colorScheme = colorScheme,
        coverBackgroundEnabled = coverBackgroundEnabled,
        artworkBackdropEnabled = playerArtworkBackdropEnabled
    )
    val accentColor = playerThemeColors.accentColor
    val playerPageAccentColor = playerThemeColors.coverAccentColor
    val lyricColors = rememberLyricReadableColors(
        accentColor = accentColor
    )
    val lyricsPageColors = rememberLyricReadableColors(
        accentColor = accentColor,
        useReadablePageInactiveText = true
    )
    val onAccentColor = playerThemeColors.onAccentColor
    val videoBackdropColor = if (isVideo) playerThemeColors.videoBackdropColor else Color.Transparent
    val progressDurationMs = when {
        playback.durationMs > 0L && resolvedDurationMs > 0L -> maxOf(playback.durationMs, resolvedDurationMs)
        playback.durationMs > 0L -> playback.durationMs
        else -> resolvedDurationMs
    }

    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    var homeLayoutHintDismissedInSession by rememberSaveable { mutableStateOf(false) }
    var homeLayoutHintShownInSession by rememberSaveable { mutableStateOf(false) }
    var homeLayoutHintMediaId by remember { mutableStateOf<String?>(null) }
    var homeLayoutLyricsVisible by remember { mutableStateOf(true) }
    var homeLayoutChangeJob by remember { mutableStateOf<Job?>(null) }
    val homeLayoutHintScope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        onDispose {
            homeLayoutChangeJob?.cancel()
        }
    }
    val changeNowPlayingHomeLayoutMode = remember(
        haptic,
        nowPlayingHomeLayoutMode,
        homeLayoutHintDismissedInSession,
        homeLayoutHintMediaId,
        homeLayoutHintScope,
        onNowPlayingHomeLayoutModeChange
    ) {
        { mode: NowPlayingHomeLayoutMode ->
            if (mode != nowPlayingHomeLayoutMode) {
                homeLayoutChangeJob?.cancel()
                if (homeLayoutHintMediaId != null && !homeLayoutHintDismissedInSession) {
                    homeLayoutHintScope.launch {
                        delay(NowPlayingHomeLayoutAnimationDurationMillis.toLong())
                        homeLayoutHintDismissedInSession = true
                        homeLayoutHintMediaId = null
                    }
                }
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                homeLayoutChangeJob = homeLayoutHintScope.launch {
                    homeLayoutLyricsVisible = false
                    delay(NowPlayingHomeLyricsFadeOutDurationMillis.toLong())
                    onNowPlayingHomeLayoutModeChange(mode)
                    delay(NowPlayingHomeLayoutAnimationDurationMillis.toLong())
                    homeLayoutLyricsVisible = true
                }
            }
        }
    }
    val lyricsPickerMimeTypes = remember {
        arrayOf(
            "*/*",
            "text/*",
            "application/octet-stream",
            "application/x-subrip",
            "application/lrc",
            "audio/x-lrc"
        )
    }
    val lyricsPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val displayName = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull().orEmpty()
        val extension = displayName.ifBlank { uri.lastPathSegment.orEmpty() }
            .substringAfterLast('.', "")
            .lowercase()
        if (extension !in setOf("lrc", "srt", "vtt")) {
            viewModel.showUnsupportedLyricsFileMessage()
            return@rememberLauncherForActivityResult
        }
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        viewModel.bindManualLyrics(uri.toString()) {
            lyricsViewModel.refreshCurrentLyrics()
        }
    }
    val openManualLyricsAction: (() -> Unit)? = if (canBindManualLyrics) {
        {
            if (isOnlineMedia) {
                viewModel.showOnlineManualLyricsUnsupported()
            } else {
                lyricsPicker.launch(lyricsPickerMimeTypes)
            }
        }
    } else {
        null
    }
    LaunchedEffect(Unit) {
        viewModel.sliceUiEvents.collect { event ->
            when (event) {
                SliceUiEvent.CutStartMarked -> haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                SliceUiEvent.CutSliceCreated -> haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                SliceUiEvent.CutInvalidRange -> haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    var showSliceSheet by remember { mutableStateOf(false) }
    var timeEditTarget by remember { mutableStateOf<Pair<Long, Boolean>?>(null) }
    val dismissSliceSheet = {
        showSliceSheet = false
        viewModel.selectSlice(null)
    }
    val toggleSelectedSlice = { sliceId: Long ->
        viewModel.selectSlice(if (sliceUiState.selectedSliceId == sliceId) null else sliceId)
    }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.isLandscape
    val widthClass = windowSizeClass.widthSizeClass
    val heightClass = windowSizeClass.heightSizeClass
    
    // 手机横屏：高度为 Compact
    val isPhoneLandscape = isLandscape && heightClass == WindowHeightSizeClass.Compact
    // 平板横屏：高度不为 Compact 且处于横屏状态
    val useSplitLayout = heightClass != WindowHeightSizeClass.Compact && isLandscape
    val landscapeLayoutMetrics = remember(configuration.screenHeightDp, useSplitLayout) {
        nowPlayingLandscapeLayoutMetrics(
            screenHeight = configuration.screenHeightDp.dp,
            tabletLayout = useSplitLayout
        )
    }
    val player = viewModel.playerOrNull()
    val videoPlayerCoordinator = remember { NowPlayingVideoPlayerCoordinator() }
    DisposableEffect(videoPlayerCoordinator) {
        onDispose { videoPlayerCoordinator.release() }
    }
    val videoAspectRatio = rememberPlayerVideoAspectRatio(player)
    val useDragPreview = coverPreviewMode == CoverPreviewMode.Drag && !isVideo
    val useMotionPreview = coverPreviewMode == CoverPreviewMode.Motion && !isVideo
    val ownsMotionPreview = sharedArtworkAlignment == null
    val ownsDragPreview = sharedCoverDragPreviewState == null
    val localCoverMotionState = rememberCoverMotionState(
        enabled = ownsMotionPreview && useMotionPreview,
        resetKey = item?.mediaId
    )
    val localCoverDragPreviewState = rememberCoverDragPreviewState(
        enabled = ownsDragPreview && useDragPreview,
        resetKey = item?.mediaId
    )
    val coverDragPreviewState = sharedCoverDragPreviewState ?: localCoverDragPreviewState
    val coverPreviewAlignment = sharedArtworkAlignment ?: when {
        useDragPreview -> coverDragPreviewState.toAlignment()
        useMotionPreview -> localCoverMotionState.toAlignment()
        else -> Alignment.Center
    }
    var surfaceMode by rememberSaveable { mutableStateOf(NowPlayingSurfaceMode.PLAYER) }
    var tabletQueueExpanded by rememberSaveable { mutableStateOf(true) }
    var tabletQueueInteractionTick by remember { mutableIntStateOf(0) }
    var landscapeArtistBottom by remember { mutableFloatStateOf(Float.NaN) }
    var landscapeCurrentLyricAnchorTop by remember { mutableFloatStateOf(Float.NaN) }
    var videoFullscreen by rememberSaveable(item?.mediaId) { mutableStateOf(false) }
    val activeVideoFullscreen = videoFullscreen && isVideo
    val latestOnVideoFullscreenChanged = rememberUpdatedState(onVideoFullscreenChanged)
    LaunchedEffect(activeVideoFullscreen) {
        latestOnVideoFullscreenChanged.value(activeVideoFullscreen)
    }
    DisposableEffect(Unit) {
        onDispose { latestOnVideoFullscreenChanged.value(false) }
    }
    val currentMotionLayout = when {
        useSplitLayout -> NowPlayingMotionLayout.SPLIT_LANDSCAPE
        isPhoneLandscape -> NowPlayingMotionLayout.PHONE_LANDSCAPE
        else -> NowPlayingMotionLayout.PORTRAIT
    }
    var routeVisible by remember(enableStaggeredRouteEntry) { mutableStateOf(!enableStaggeredRouteEntry) }
    var pendingRouteExit by remember { mutableStateOf(false) }
    var exitMotionLayout by remember { mutableStateOf<NowPlayingMotionLayout?>(null) }
    val latestOnBack = rememberUpdatedState(onBack)
    val latestOnRouteExitStarted = rememberUpdatedState(onRouteExitStarted)
    val routeTransition = updateTransition(targetState = routeVisible, label = "nowPlayingRouteVisibility")
    val pageEntranceSettled by remember {
        derivedStateOf {
            routeTransition.currentState && routeTransition.targetState && !routeTransition.isRunning
        }
    }
    val requestClose = remember(pendingRouteExit, currentMotionLayout) {
        {
            if (!pendingRouteExit) {
                exitMotionLayout = currentMotionLayout
                pendingRouteExit = true
                latestOnRouteExitStarted.value(
                    NowPlayingMotionSpec.totalExitDurationMs(currentMotionLayout)
                )
                routeVisible = false
            }
        }
    }

    LaunchedEffect(enableStaggeredRouteEntry) {
        routeVisible = true
    }

    LaunchedEffect(pendingRouteExit, exitMotionLayout) {
        val layout = exitMotionLayout ?: return@LaunchedEffect
        if (!pendingRouteExit) return@LaunchedEffect
        delay(NowPlayingMotionSpec.totalExitDurationMs(layout).toLong())
        latestOnBack.value()
    }

    val showLyricsSurface = remember(isVideo) {
        {
            if (!isVideo) {
                surfaceMode = NowPlayingSurfaceMode.LYRICS
            }
        }
    }
    LaunchedEffect(
        useSplitLayout,
        isVideo,
        surfaceMode,
        tabletQueueExpanded,
        tabletQueueInteractionTick
    ) {
        if (
            useSplitLayout &&
            !isVideo &&
            surfaceMode == NowPlayingSurfaceMode.PLAYER &&
            tabletQueueExpanded
        ) {
            delay(TabletLandscapeQueueAutoCollapseMillis)
            tabletQueueExpanded = false
        }
    }
    val handleNavigateUp = {
        if (surfaceMode == NowPlayingSurfaceMode.LYRICS) {
            surfaceMode = NowPlayingSurfaceMode.PLAYER
        } else {
            requestClose()
        }
    }

    BackHandler(enabled = !pendingRouteExit && !videoFullscreen) {
        handleNavigateUp()
    }
    val playerHeaderTitle = metadata?.title?.toString().orEmpty().ifBlank {
        lyricsState.title.ifBlank { "未播放" }
    }
    val playerArtistText = metadata?.artist?.toString().orEmpty()
    val playerArtistMeta = remember(playerArtistText) {
        parseNowPlayingArtistMeta(playerArtistText)
    }
    val lyricsHeaderTitle = lyricsState.title.ifBlank {
        metadata?.title?.toString().orEmpty().ifBlank { "歌词" }
    }

    val sharedHeaderTitle = if (surfaceMode == NowPlayingSurfaceMode.LYRICS) {
        lyricsHeaderTitle
    } else {
        playerHeaderTitle
    }
    val showSharedHeaderTitle = surfaceMode == NowPlayingSurfaceMode.LYRICS
    val sharedHeaderMotion = routeTransition.nowPlayingMotionModifier(
        currentMotionLayout,
        NowPlayingMotionSlot.HEADER
    )
    val sharedHeaderHorizontalPadding = if (isLandscape) 4.dp else 12.dp
    var volumeControlExpanded by remember { mutableStateOf(false) }
    var volumeControlBounds by remember { mutableStateOf<Rect?>(null) }
    // Only the portrait player layout renders the inline volume control.
    // Split landscape, phone landscape, and the dedicated lyrics surface should
    // all fall back to the floating hardware volume overlay.
    val usesInlineVolumeControl =
        surfaceMode == NowPlayingSurfaceMode.PLAYER &&
            !useSplitLayout &&
            !isPhoneLandscape
    val latestOnInlineVolumeControlVisibilityChanged by rememberUpdatedState(onInlineVolumeControlVisibilityChanged)
    val latestOnEqualizerVisibilityChanged by rememberUpdatedState(onEqualizerVisibilityChanged)

    SideEffect {
        latestOnInlineVolumeControlVisibilityChanged(usesInlineVolumeControl)
    }

    SideEffect {
        latestOnEqualizerVisibilityChanged(showEqualizer)
    }

    LaunchedEffect(showEqualizer) {
        if (showEqualizer) {
            volumeControlExpanded = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            latestOnInlineVolumeControlVisibilityChanged(false)
            latestOnEqualizerVisibilityChanged(false)
        }
    }

    val tabletQueueInteractionModifier = if (
        useSplitLayout &&
        !isVideo &&
        surfaceMode == NowPlayingSurfaceMode.PLAYER &&
        tabletQueueExpanded
    ) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Final
                )
                tabletQueueInteractionTick++
            }
        }
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(tabletQueueInteractionModifier)
    ) {
        if (renderBackdrop && !isVideo) {
            CoverArtworkBackground(
                artworkModel = artworkModel,
                enabled = coverBackgroundEnabled,
                clarity = coverBackgroundClarity,
                overlayBaseColor = colorScheme.background,
                tintBaseColor = playerThemeColors.backdropTintColor,
                artworkAlignment = coverPreviewAlignment,
                isDark = colorScheme.isDark
            )
        }
        if (isLandscape && surfaceMode == NowPlayingSurfaceMode.PLAYER && !isVideo) {
            val spectrumMotion = routeTransition.nowPlayingMotionModifier(
                currentMotionLayout,
                NowPlayingMotionSlot.SPECTRUM
            )
            val spectrumHeight = landscapeLayoutMetrics.spectrumHeight
            val density = LocalDensity.current
            val fallbackCenterY = with(density) {
                configuration.screenHeightDp.dp.toPx() *
                    if (landscapeLayoutMetrics.tabletLayout) 0.28f else 0.34f
            }
            val spectrumCenterY = landscapeSpectrumCenterY(
                artistInfoBottom = landscapeArtistBottom,
                currentLyricAnchorTop = landscapeCurrentLyricAnchorTop,
                fallbackCenterY = fallbackCenterY
            )
            HorizontalStereoSpectrum(
                lineColor = playerPageAccentColor,
                intensity = if (landscapeLayoutMetrics.tabletLayout) 0.78f else 0.72f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(spectrumHeight)
                    .align(Alignment.TopCenter)
                    .offset {
                        IntOffset(
                            x = 0,
                            y = (spectrumCenterY - spectrumHeight.toPx() / 2f).roundToInt()
                        )
                    }
                    .then(spectrumMotion)
            )
        }
        Column(modifier = Modifier.fillMaxSize()) {
            PlayerSurfaceHeader(
                title = sharedHeaderTitle,
                isLandscape = isLandscape,
                onNavigateUp = handleNavigateUp,
                onShowSleepTimer = onShowSleepTimer,
                onShowQueue = onShowQueue,
                onManualBindLyrics = if (surfaceMode == NowPlayingSurfaceMode.LYRICS) openManualLyricsAction else null,
                navigationEnabled = !pendingRouteExit,
                showTitle = showSharedHeaderTitle,
                showDivider = !(isLandscape && surfaceMode == NowPlayingSurfaceMode.PLAYER),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = sharedHeaderHorizontalPadding)
                    .then(sharedHeaderMotion)
            )
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = surfaceMode,
                    transitionSpec = { nowPlayingSurfaceTransform() },
                    label = "nowPlayingSurfaceMode"
                ) { activeSurfaceMode ->
            if (activeSurfaceMode == NowPlayingSurfaceMode.PLAYER) {
                val layoutState = remember(useSplitLayout, isPhoneLandscape) { useSplitLayout to isPhoneLandscape }

                AnimatedContent(
                    targetState = layoutState,
                    transitionSpec = {
                        if (initialState == targetState) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            val enter = fadeIn(animationSpec = tween(durationMillis = 220, delayMillis = 60)) +
                                scaleIn(animationSpec = tween(durationMillis = 220, delayMillis = 60), initialScale = 0.98f)
                            val exit = fadeOut(animationSpec = tween(durationMillis = 160)) +
                                scaleOut(animationSpec = tween(durationMillis = 160), targetScale = 1.02f)
                            enter togetherWith exit
                        }
                    },
                    label = "nowPlayingLayout"
                ) { (split, phoneLandscape) ->
            val renderVideoSurface = split == layoutState.first &&
                phoneLandscape == layoutState.second
            val motionLayout = when {
                split -> NowPlayingMotionLayout.SPLIT_LANDSCAPE
                phoneLandscape -> NowPlayingMotionLayout.PHONE_LANDSCAPE
                else -> NowPlayingMotionLayout.PORTRAIT
            }

            if (split) {
                val coverMotion = routeTransition.nowPlayingMotionModifier(motionLayout, NowPlayingMotionSlot.COVER)
                val queueMotion = routeTransition.nowPlayingMotionModifier(motionLayout, NowPlayingMotionSlot.QUEUE)
                val progressMotion = routeTransition.nowPlayingMotionModifier(motionLayout, NowPlayingMotionSlot.PROGRESS)
                val infoMotion = routeTransition.nowPlayingMotionModifier(motionLayout, NowPlayingMotionSlot.INFO_PANEL)
                val controlsMotion = routeTransition.nowPlayingMotionModifier(motionLayout, NowPlayingMotionSlot.CONTROLS)
            // 平板横屏沿用与手机一致的封面优先结构，仅放大留白与歌词容量。
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = landscapeLayoutMetrics.horizontalPadding,
                        top = landscapeLayoutMetrics.topPadding,
                        end = landscapeLayoutMetrics.horizontalPadding,
                        bottom = landscapeLayoutMetrics.bottomPadding
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(landscapeLayoutMetrics.contentSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(landscapeLayoutMetrics.artworkWeight)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(
                            landscapeLayoutMetrics.sectionSpacing,
                            Alignment.CenterVertically
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .then(coverMotion),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = if (isVideo) {
                                    Modifier.fitVideoPreviewAspectRatio(
                                        aspectRatio = videoAspectRatio,
                                        maxWidth = landscapeLayoutMetrics.artworkMaxSize
                                    )
                                } else {
                                    Modifier
                                        .widthIn(max = landscapeLayoutMetrics.artworkMaxSize)
                                        .aspectRatio(1f)
                                }
                            ) {
                                ArtworkBox(
                                    isVideo = isVideo,
                                    metadata = metadata,
                                    viewModel = viewModel,
                                    videoPlayerCoordinator = videoPlayerCoordinator,
                                    renderVideoSurface = renderVideoSurface,
                                    videoFullscreen = videoFullscreen,
                                    onOpenVideoFullscreen = { videoFullscreen = true },
                                    onOpenLyrics = showLyricsSurface,
                                    edgeBlendEnabled = false,
                                    edgeBlendColor = if (playerArtworkBackdropEnabled) playerThemeColors.backdropTintColor else colorScheme.background,
                                    videoBackdropColor = videoBackdropColor,
                                    artworkAlignment = coverPreviewAlignment,
                                    artworkCornerRadius = landscapeLayoutMetrics.artworkCornerRadius,
                                    dragPreviewEnabled = useDragPreview,
                                    dragPreviewState = coverDragPreviewState
                                )
                            }
                        }

                        if (!isVideo) {
                            TabletLandscapeQueuePanel(
                                viewModel = viewModel,
                                currentMediaId = item?.mediaId.orEmpty(),
                                isPlaying = playback.isPlaying,
                                activeColor = playerPageAccentColor,
                                expanded = tabletQueueExpanded,
                                onExpandedChange = { tabletQueueExpanded = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = landscapeLayoutMetrics.progressMaxWidth)
                                    .then(queueMotion)
                            )
                        }
                        
                        key(item?.mediaId) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = landscapeLayoutMetrics.progressMaxWidth)
                                    .height(landscapeLayoutMetrics.progressHeight)
                                    .then(progressMotion),
                                contentAlignment = Alignment.Center
                            ) {
                                PlaybackProgressContent(viewModel, isVideo) { progress ->
                                    PlayerProgress(
                                        positionMs = progress.positionMs,
                                        durationMs = progressDurationMs,
                                        sliceUiState = sliceUiState,
                                        onSeekTo = { viewModel.seekTo(it) },
                                        onScrubbingChanged = { viewModel.setUserScrubbing(it) },
                                        onSelectSlice = { viewModel.selectSlice(it) },
                                        onLongPressSlice = {
                                            viewModel.selectSlice(it)
                                            showSliceSheet = true
                                        },
                                        onUpdateSliceRange = { sliceId, startMs, endMs ->
                                            viewModel.updateSliceRange(sliceId, startMs, endMs, progressDurationMs)
                                        },
                                        activeColor = accentColor,
                                        inactiveColor = accentColor.copy(alpha = 0.2f)
                                    )
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(landscapeLayoutMetrics.contentWeight)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(landscapeLayoutMetrics.sectionSpacing)
                    ) {
                        LandscapePlayerIdentity(
                            title = playerHeaderTitle,
                            artistMeta = playerArtistMeta,
                            listenTogetherState = listenTogetherUiState,
                            accentColor = accentColor,
                            pageEntranceSettled = pageEntranceSettled,
                            compactHeight = false,
                            tabletLayout = true,
                            onArtistBottomChanged = { bottom ->
                                if (!landscapeArtistBottom.isFinite() || abs(landscapeArtistBottom - bottom) > 0.5f) {
                                    landscapeArtistBottom = bottom
                                }
                            },
                            modifier = Modifier
                                // 两行标题会超过基准高度，让身份区按实际文本高度增长，避免裁掉社团/CV。
                                .heightIn(min = landscapeLayoutMetrics.identityMinHeight)
                                .then(infoMotion)
                        )

                        if (!isVideo) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .then(infoMotion),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                PlaybackProgressContent(viewModel, isVideo) { progress ->
                                    NowPlayingLyricsPreview(
                                        lyrics = lyricsState.lyrics,
                                        currentPosition = progress.positionMs,
                                        onOpenLyrics = showLyricsSurface,
                                        colors = lyricColors,
                                        interactionEnabled = !lyricsState.isLoading,
                                        highlightFontSizeSp = nowPlayingLyricsSettings.highlightFontSizeSp,
                                        tabletLayout = true,
                                        contentTopPadding = landscapeLayoutMetrics.lyricsTopPadding,
                                        onCurrentLineAnchorChanged = { top ->
                                            if (!landscapeCurrentLyricAnchorTop.isFinite() || abs(landscapeCurrentLyricAnchorTop - top) > 0.5f) {
                                                landscapeCurrentLyricAnchorTop = top
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f).then(infoMotion))
                        }

                        PlaybackControls(
                            playback = playback,
                            isFavorite = isFavorite,
                            viewModel = viewModel,
                            onShowPlaylistPicker = {
                                val current = playback.currentMediaItem ?: return@PlaybackControls
                                onOpenPlaylistPicker(current)
                            },
                            onShowEqualizer = { showEqualizer = true },
                            onManageTags = {
                                val mediaId = item?.mediaId.orEmpty()
                                val fallback = metadata?.title?.toString().orEmpty()
                                tagViewModel.openForMediaId(mediaId, fallback)
                            },
                            sliceUiState = sliceUiState,
                            modifier = Modifier.height(landscapeLayoutMetrics.controlsHeight),
                            showActionRow = !isVideo,
                            landscapeControls = true,
                            actionRowModifier = controlsMotion,
                            coreControlsModifier = controlsMotion,
                            primaryColor = accentColor,
                            onPrimaryColor = onAccentColor
                        )
                    }
                }
            }
        } else if (phoneLandscape) {
            val coverMotion = routeTransition.nowPlayingMotionModifier(motionLayout, NowPlayingMotionSlot.COVER)
            val progressMotion = routeTransition.nowPlayingMotionModifier(motionLayout, NowPlayingMotionSlot.PROGRESS)
            val lyricsMotion = routeTransition.nowPlayingMotionModifier(motionLayout, NowPlayingMotionSlot.LYRICS)
            val controlsMotion = routeTransition.nowPlayingMotionModifier(motionLayout, NowPlayingMotionSlot.CONTROLS)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = landscapeLayoutMetrics.horizontalPadding,
                        top = landscapeLayoutMetrics.topPadding,
                        end = landscapeLayoutMetrics.horizontalPadding,
                        bottom = landscapeLayoutMetrics.bottomPadding
                    ),
                horizontalArrangement = Arrangement.spacedBy(landscapeLayoutMetrics.contentSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(landscapeLayoutMetrics.artworkWeight)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(landscapeLayoutMetrics.sectionSpacing)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .then(coverMotion),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = if (isVideo) {
                                Modifier.fitVideoPreviewAspectRatio(
                                    aspectRatio = videoAspectRatio,
                                    maxWidth = landscapeLayoutMetrics.artworkMaxSize
                                )
                            } else {
                                Modifier
                                    .widthIn(max = landscapeLayoutMetrics.artworkMaxSize)
                                    .aspectRatio(1f)
                            }
                        ) {
                            ArtworkBox(
                                isVideo = isVideo,
                                metadata = metadata,
                                viewModel = viewModel,
                                videoPlayerCoordinator = videoPlayerCoordinator,
                                renderVideoSurface = renderVideoSurface,
                                videoFullscreen = videoFullscreen,
                                onOpenVideoFullscreen = { videoFullscreen = true },
                                onOpenLyrics = showLyricsSurface,
                                edgeBlendEnabled = false,
                                edgeBlendColor = playerThemeColors.backdropTintColor,
                                videoBackdropColor = videoBackdropColor,
                                artworkAlignment = coverPreviewAlignment,
                                artworkCornerRadius = landscapeLayoutMetrics.artworkCornerRadius,
                                dragPreviewEnabled = useDragPreview,
                                dragPreviewState = coverDragPreviewState
                            )
                        }
                    }

                    key(item?.mediaId) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = landscapeLayoutMetrics.progressMaxWidth)
                                .height(landscapeLayoutMetrics.progressHeight)
                                .then(progressMotion),
                            contentAlignment = Alignment.Center
                        ) {
                            PlaybackProgressContent(viewModel, isVideo) { progress ->
                                PlayerProgress(
                                    positionMs = progress.positionMs,
                                    durationMs = progressDurationMs,
                                    sliceUiState = sliceUiState,
                                    onSeekTo = { viewModel.seekTo(it) },
                                    onScrubbingChanged = { viewModel.setUserScrubbing(it) },
                                    onSelectSlice = { viewModel.selectSlice(it) },
                                    onLongPressSlice = {
                                        viewModel.selectSlice(it)
                                        showSliceSheet = true
                                    },
                                    onUpdateSliceRange = { sliceId, startMs, endMs ->
                                        viewModel.updateSliceRange(sliceId, startMs, endMs, progressDurationMs)
                                    },
                                    activeColor = accentColor,
                                    inactiveColor = accentColor.copy(alpha = 0.2f),
                                    compactLayout = true
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(landscapeLayoutMetrics.contentWeight)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(landscapeLayoutMetrics.sectionSpacing)
                ) {
                    LandscapePlayerIdentity(
                        title = playerHeaderTitle,
                        artistMeta = playerArtistMeta,
                        listenTogetherState = listenTogetherUiState,
                        accentColor = accentColor,
                        pageEntranceSettled = pageEntranceSettled,
                        compactHeight = landscapeLayoutMetrics.compactHeight,
                        tabletLayout = false,
                        onArtistBottomChanged = { bottom ->
                            if (!landscapeArtistBottom.isFinite() || abs(landscapeArtistBottom - bottom) > 0.5f) {
                                landscapeArtistBottom = bottom
                            }
                        },
                        modifier = Modifier
                            // 两行标题会超过基准高度，让身份区按实际文本高度增长，避免裁掉社团/CV。
                            .heightIn(min = landscapeLayoutMetrics.identityMinHeight)
                            .then(lyricsMotion)
                    )

                    if (!isVideo) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .then(lyricsMotion),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            PlaybackProgressContent(viewModel, isVideo) { progress ->
                                NowPlayingLyricsPreview(
                                    lyrics = lyricsState.lyrics,
                                    currentPosition = progress.positionMs,
                                    onOpenLyrics = showLyricsSurface,
                                    colors = lyricColors,
                                    interactionEnabled = !lyricsState.isLoading,
                                    highlightFontSizeSp = nowPlayingLyricsSettings.highlightFontSizeSp,
                                    compactHeight = landscapeLayoutMetrics.compactHeight,
                                    contentTopPadding = landscapeLayoutMetrics.lyricsTopPadding,
                                    onCurrentLineAnchorChanged = { top ->
                                        if (!landscapeCurrentLyricAnchorTop.isFinite() || abs(landscapeCurrentLyricAnchorTop - top) > 0.5f) {
                                            landscapeCurrentLyricAnchorTop = top
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f).then(lyricsMotion))
                    }

                    PlaybackControls(
                        playback = playback,
                        isFavorite = isFavorite,
                        viewModel = viewModel,
                        onShowPlaylistPicker = {
                            val current = playback.currentMediaItem ?: return@PlaybackControls
                            onOpenPlaylistPicker(current)
                        },
                        onShowEqualizer = { showEqualizer = true },
                        onManageTags = {
                            val mediaId = item?.mediaId.orEmpty()
                            val fallback = metadata?.title?.toString().orEmpty()
                            tagViewModel.openForMediaId(mediaId, fallback)
                        },
                        sliceUiState = sliceUiState,
                        modifier = Modifier.height(landscapeLayoutMetrics.controlsHeight),
                        showActionRow = !isVideo,
                        landscapeControls = true,
                        compactLayout = true,
                        actionRowModifier = controlsMotion,
                        coreControlsModifier = controlsMotion,
                        primaryColor = accentColor,
                        onPrimaryColor = onAccentColor
                    )
                }
            }
        } else {
            val coverMotion = routeTransition.nowPlayingMotionModifier(motionLayout, NowPlayingMotionSlot.COVER)
            val lyricsMotion = routeTransition.nowPlayingMotionModifier(motionLayout, NowPlayingMotionSlot.LYRICS)
            val progressMotion = routeTransition.nowPlayingMotionModifier(motionLayout, NowPlayingMotionSlot.PROGRESS)
            val actionRowMotion = routeTransition.nowPlayingMotionModifier(motionLayout, NowPlayingMotionSlot.ACTION_ROW)
            val controlsMotion = routeTransition.nowPlayingMotionModifier(motionLayout, NowPlayingMotionSlot.CONTROLS)
            val volumeMotion = routeTransition.nowPlayingMotionModifier(motionLayout, NowPlayingMotionSlot.VOLUME)
            val expandedHomeLayout = nowPlayingHomeLayoutMode == NowPlayingHomeLayoutMode.Expanded && !isVideo
            val portraitScreenHeight = configuration.screenHeightDp.dp
            val portraitLayoutMetrics = remember(portraitScreenHeight, widthClass) {
                nowPlayingPortraitLayoutMetrics(
                    screenHeight = portraitScreenHeight,
                    widthClass = widthClass
                )
            }
            val classicTrackInfoTargetHeight = nowPlayingClassicTrackInfoHeight(
                metrics = portraitLayoutMetrics
            )
            val hintMediaId = item?.mediaId
            val markHomeLayoutHintShown by rememberUpdatedState(onNowPlayingHomeLayoutHintShown)
            LaunchedEffect(hintMediaId, nowPlayingHomeLayoutHintDismissed, expandedHomeLayout, isVideo) {
                if (homeLayoutHintMediaId != null && homeLayoutHintMediaId != hintMediaId) {
                    homeLayoutHintMediaId = null
                }
                if (hintMediaId != null && !isVideo && !expandedHomeLayout &&
                    nowPlayingHomeLayoutHintDismissed == false &&
                    !homeLayoutHintShownInSession && !homeLayoutHintDismissedInSession
                ) {
                    homeLayoutHintShownInSession = true
                    homeLayoutHintMediaId = hintMediaId
                    markHomeLayoutHintShown()
                }
            }
            val homeLayoutSwipeHintAllowed = hintMediaId != null &&
                homeLayoutHintMediaId == hintMediaId &&
                !homeLayoutHintDismissedInSession && !isVideo
            val portraitContentHorizontalPadding = portraitLayoutMetrics.contentHorizontalPadding
            val homeBezier = remember { CubicBezierEasing(0.20f, 0f, 0f, 1f) }
            val homeLayoutDurationMillis = NowPlayingHomeLayoutAnimationDurationMillis
            val homeFadeInDurationMillis = NowPlayingHomeLyricsFadeInDurationMillis
            val homeFadeOutDurationMillis = NowPlayingHomeLyricsFadeOutDurationMillis
            val expandedHomeLyricsSettings = remember(lyricsPageSettings) {
                lyricsPageSettings.copy(displayAreaMode = 0)
            }
            val portraitContentWidthModifier = if (widthClass.isCompactWidth) {
                Modifier.fillMaxWidth()
            } else {
                Modifier
                    .widthIn(max = NowPlayingPortraitMaxContentWidth)
                    .fillMaxWidth()
            }
            val artworkAspectRatio = rememberArtworkAspectRatio(artworkModel)
            val homeLayoutTransition = updateTransition(
                targetState = expandedHomeLayout,
                label = "nowPlayingHomeLayoutMode"
            )
            val showHomeLayoutSwipeHint = homeLayoutSwipeHintAllowed &&
                !homeLayoutTransition.targetState
            val classicAudienceHeight by homeLayoutTransition.animateDp(
                transitionSpec = {
                    tween(durationMillis = homeLayoutDurationMillis, easing = homeBezier)
                },
                label = "nowPlayingHomeClassicAudienceHeight"
            ) { expanded ->
                if (expanded) 0.dp else portraitLayoutMetrics.audienceHeight
            }
            val classicTrackInfoHeight by homeLayoutTransition.animateDp(
                transitionSpec = {
                    tween(durationMillis = homeLayoutDurationMillis, easing = homeBezier)
                },
                label = "nowPlayingHomeClassicTrackInfoHeight"
            ) { expanded ->
                if (expanded) 0.dp else classicTrackInfoTargetHeight
            }
            val classicIdentityAlpha by homeLayoutTransition.animateFloat(
                transitionSpec = {
                    tween(
                        durationMillis = if (targetState) homeFadeOutDurationMillis else homeFadeInDurationMillis,
                        easing = if (targetState) FastOutLinearInEasing else LinearOutSlowInEasing
                    )
                },
                label = "nowPlayingHomeClassicIdentityAlpha"
            ) { expanded ->
                if (expanded) 0f else 1f
            }
            val expandedIdentityAlpha by homeLayoutTransition.animateFloat(
                transitionSpec = {
                    tween(
                        durationMillis = if (targetState) homeFadeInDurationMillis else homeFadeOutDurationMillis,
                        easing = if (targetState) LinearOutSlowInEasing else FastOutLinearInEasing
                    )
                },
                label = "nowPlayingHomeExpandedIdentityAlpha"
            ) { expanded ->
                if (expanded) 1f else 0f
            }
            val portraitTopPadding by homeLayoutTransition.animateDp(
                transitionSpec = {
                    tween(durationMillis = homeLayoutDurationMillis, easing = homeBezier)
                },
                label = "nowPlayingHomeTopPadding"
            ) { expanded ->
                if (expanded) 0.dp else portraitLayoutMetrics.topPadding
            }
            val coverVerticalPadding by homeLayoutTransition.animateDp(
                transitionSpec = {
                    tween(durationMillis = homeLayoutDurationMillis, easing = homeBezier)
                },
                label = "nowPlayingHomeCoverVerticalPadding"
            ) { expanded ->
                if (expanded) 0.dp else portraitLayoutMetrics.coverVerticalPadding
            }
            val expandedLyricsTopPadding = portraitLayoutMetrics.expandedLyricsTopPadding
            val homeCoverAspectRatio by homeLayoutTransition.animateFloat(
                transitionSpec = {
                    tween(durationMillis = homeLayoutDurationMillis, easing = homeBezier)
                },
                label = "nowPlayingHomeCoverAspectRatio"
            ) { expanded ->
                if (expanded) artworkAspectRatio else 1f
            }
            val homeCoverCornerRadius by homeLayoutTransition.animateDp(
                transitionSpec = {
                    tween(durationMillis = homeLayoutDurationMillis, easing = homeBezier)
                },
                label = "nowPlayingHomeCoverCornerRadius"
            ) { expanded ->
                if (expanded) 0.dp else NowPlayingPortraitArtworkCornerRadius
            }
            val homeLayoutSettled = homeLayoutTransition.currentState == homeLayoutTransition.targetState
            LaunchedEffect(expandedHomeLayout) {
                if (!homeLayoutSettled) {
                    homeLayoutLyricsVisible = false
                }
            }
            LaunchedEffect(homeLayoutSettled) {
                if (homeLayoutSettled) {
                    homeLayoutLyricsVisible = true
                }
            }
            val expandedLyricsActive = expandedHomeLayout && homeLayoutSettled && homeLayoutLyricsVisible
            val classicLyricsActive = !expandedHomeLayout && homeLayoutSettled && homeLayoutLyricsVisible
            val expandedLyricsAlpha by animateFloatAsState(
                targetValue = if (expandedLyricsActive) 1f else 0f,
                animationSpec = tween(
                    durationMillis = if (expandedLyricsActive) homeFadeInDurationMillis else homeFadeOutDurationMillis,
                    easing = if (expandedLyricsActive) LinearOutSlowInEasing else FastOutLinearInEasing
                ),
                label = "nowPlayingHomeExpandedLyricsAlpha"
            )
            val classicLyricsAlpha by animateFloatAsState(
                targetValue = if (classicLyricsActive) 1f else 0f,
                animationSpec = tween(
                    durationMillis = if (classicLyricsActive) homeFadeInDurationMillis else homeFadeOutDurationMillis,
                    easing = if (classicLyricsActive) LinearOutSlowInEasing else FastOutLinearInEasing
                ),
                label = "nowPlayingHomeClassicLyricsAlpha"
            )
            val lyricsExpandedInteractionEnabled = expandedLyricsAlpha > 0.5f
            val lyricsClassicInteractionEnabled = classicLyricsAlpha > 0.5f
            // --- 垂直布局 (手机 或 平板竖屏) ---
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clipToBounds()
                    ) {
                        val portraitTopContentMaxHeight = maxHeight
                        val portraitDensity = LocalDensity.current
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = portraitTopPadding),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = portraitContentWidthModifier
                                    .height(classicAudienceHeight)
                                    .clipToBounds()
                                    .graphicsLayer { alpha = classicIdentityAlpha }
                                    .then(coverMotion),
                                contentAlignment = Alignment.Center
                            ) {
                                ListenTogetherAudienceLine(
                                    state = listenTogetherUiState,
                                    modifier = Modifier.fillMaxWidth(),
                                    accentColor = accentColor,
                                    textColor = colorScheme.textTertiary,
                                    pageEntranceSettled = pageEntranceSettled
                                )
                            }

                            // 封面
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = coverVerticalPadding)
                                    .then(coverMotion)
                                    .nowPlayingHomeLayoutSwipeGesture(
                                        enabled = !isVideo && !pendingRouteExit,
                                        currentMode = nowPlayingHomeLayoutMode,
                                        onModeChange = changeNowPlayingHomeLayoutMode
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                BoxWithConstraints(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val homeCoverWidth by homeLayoutTransition.animateDp(
                                        transitionSpec = {
                                            tween(durationMillis = homeLayoutDurationMillis, easing = homeBezier)
                                        },
                                        label = "nowPlayingHomeCoverWidth"
                                    ) { expanded ->
                                        nowPlayingHomeCoverWidth(
                                            expanded = expanded,
                                            availableWidth = maxWidth,
                                            availableHeight = portraitTopContentMaxHeight,
                                            widthClass = widthClass,
                                            contentHorizontalPadding = portraitContentHorizontalPadding,
                                            coverAspectRatio = if (expanded) artworkAspectRatio else 1f,
                                            topPadding = if (expanded) 0.dp else portraitLayoutMetrics.topPadding,
                                            coverVerticalPadding = if (expanded) 0.dp else portraitLayoutMetrics.coverVerticalPadding,
                                            identityHeight = if (expanded) {
                                                0.dp
                                            } else {
                                                portraitLayoutMetrics.audienceHeight + classicTrackInfoTargetHeight
                                            },
                                            lyricsReserveHeight = if (expanded) {
                                                portraitLayoutMetrics.expandedLyricsReserveHeight
                                            } else {
                                                if (nowPlayingLyricsSettings.multilineEnabled && !isVideo) {
                                                    multilineLyricsReserveHeight(
                                                        availableHeight = portraitTopContentMaxHeight,
                                                        lineHeight = with(portraitDensity) {
                                                            nowPlayingLyricTypographyMetrics(
                                                                largeTypography = !widthClass.isCompactWidth,
                                                                highlightFontSizeSp = nowPlayingLyricsSettings.highlightFontSizeSp
                                                            ).currentLineHeightSp.sp.toDp()
                                                        }
                                                    )
                                                } else portraitLayoutMetrics.classicLyricsReserveHeight
                                            },
                                            minimumCoverWidth = if (!expanded && nowPlayingLyricsSettings.multilineEnabled && !isVideo) {
                                                1.dp
                                            } else portraitLayoutMetrics.minimumCoverWidth
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .then(
                                                if (isVideo) {
                                                    Modifier
                                                        .widthIn(max = if (widthClass.isCompactWidth) 1000.dp else 400.dp)
                                                        .fitVideoPreviewAspectRatio(videoAspectRatio)
                                                } else {
                                                    Modifier
                                                        .width(homeCoverWidth)
                                                        .aspectRatio(homeCoverAspectRatio)
                                                }
                                            )
                                            .clip(RoundedCornerShape(homeCoverCornerRadius))
                                    ) {
                                        ArtworkBox(
                                            isVideo = isVideo,
                                            metadata = metadata,
                                            viewModel = viewModel,
                                            videoPlayerCoordinator = videoPlayerCoordinator,
                                            renderVideoSurface = renderVideoSurface,
                                            videoFullscreen = videoFullscreen,
                                            onOpenVideoFullscreen = { videoFullscreen = true },
                                            onOpenLyrics = showLyricsSurface,
                                            edgeBlendEnabled = false,
                                            edgeBlendColor = if (playerArtworkBackdropEnabled) playerThemeColors.backdropTintColor else colorScheme.background,
                                            videoBackdropColor = videoBackdropColor,
                                            artworkAlignment = coverPreviewAlignment,
                                            artworkContentScale = ContentScale.Crop,
                                            artworkCornerRadius = homeCoverCornerRadius,
                                            artworkLoadAtOriginalSize = true,
                                            dragPreviewEnabled = useDragPreview,
                                            dragPreviewState = coverDragPreviewState
                                        )
                                        if (showHomeLayoutSwipeHint) {
                                            NowPlayingHomeLayoutSwipeHint(
                                                modifier = Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .padding(bottom = 16.dp)
                                            )
                                        }
                                        ExpandedPlayerIdentityOverlay(
                                            title = playerHeaderTitle,
                                            artistMeta = playerArtistMeta,
                                            listenTogetherState = listenTogetherUiState,
                                            pageEntranceSettled = pageEntranceSettled,
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .graphicsLayer { alpha = expandedIdentityAlpha }
                                        )
                                    }
                                }
                            }

                            ClassicPlayerIdentity(
                                title = playerHeaderTitle,
                                artistMeta = playerArtistMeta,
                                compactLayout = portraitLayoutMetrics.compact,
                                modifier = portraitContentWidthModifier
                                    .height(classicTrackInfoHeight)
                                    .graphicsLayer { alpha = classicIdentityAlpha }
                                    .then(coverMotion)
                            )

                            if (!isVideo) {
                                BoxWithConstraints(
                                    modifier = portraitContentWidthModifier
                                        .weight(1f)
                                        .then(lyricsMotion),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (expandedLyricsActive || expandedLyricsAlpha > 0.001f) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(top = expandedLyricsTopPadding)
                                                .graphicsLayer { alpha = expandedLyricsAlpha }
                                        ) {
                                            PlaybackProgressContent(viewModel, isVideo) { progress ->
                                                NowPlayingLyricsSurface(
                                                    isLandscape = false,
                                                    playbackPositionMs = progress.positionMs,
                                                    lyrics = lyricsState.lyrics,
                                                    lyricColors = lyricColors,
                                                    accentColor = accentColor,
                                                    spectrumColor = playerPageAccentColor,
                                                    onAccentColor = onAccentColor,
                                                    lyricsPageSettings = expandedHomeLyricsSettings,
                                                    onSeekTo = { viewModel.seekTo(it) },
                                                    onTimelinePlay = { targetMs ->
                                                        viewModel.seekTo(targetMs)
                                                        viewModel.play()
                                                    },
                                                    onAddLyrics = openManualLyricsAction,
                                                    interactionEnabled = lyricsExpandedInteractionEnabled,
                                                    stableFocusAnchor = true,
                                                    expandedHomeVisualEffects = true,
                                                    lyricItemOuterHorizontalPadding = 6.dp,
                                                    lyricItemInnerHorizontalPadding = 8.dp,
                                                    contentKey = lyricsState.contentKey,
                                                    contentVisible = !lyricsState.isLoading,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }
                                    }
                                    if (classicLyricsActive || classicLyricsAlpha > 0.001f) {
                                        val upcomingCount = portraitClassicLyricsUpcomingCount(maxHeight)
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = portraitContentHorizontalPadding)
                                                .graphicsLayer { alpha = classicLyricsAlpha },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            PlaybackProgressContent(viewModel, isVideo) { progress ->
                                                NowPlayingLyricsPreview(
                                                    lyrics = lyricsState.lyrics,
                                                    currentPosition = progress.positionMs,
                                                    onOpenLyrics = showLyricsSurface,
                                                    colors = lyricColors,
                                                    interactionEnabled = lyricsClassicInteractionEnabled,
                                                    highlightFontSizeSp = nowPlayingLyricsSettings.highlightFontSizeSp,
                                                    multilineEnabled = nowPlayingLyricsSettings.multilineEnabled,
                                                    compactHeight = portraitLayoutMetrics.compact,
                                                    largeTypography = !widthClass.isCompactWidth,
                                                    upcomingCount = upcomingCount,
                                                    centered = true,
                                                    currentFontWeight = FontWeight.ExtraBold,
                                                    currentMaxLinesOverride = 1,
                                                    upcomingMaxLinesOverride = 1,
                                                    marqueeCurrentLine = true,
                                                    emptyText = "暂无歌词",
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    Column(
                        modifier = portraitContentWidthModifier
                            .padding(bottom = portraitLayoutMetrics.bottomPadding),
                        verticalArrangement = Arrangement.spacedBy(portraitLayoutMetrics.bottomSectionSpacing)
                    ) {
                        key(item?.mediaId) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = portraitContentHorizontalPadding)
                                    .then(progressMotion)
                            ) {
                                PlaybackProgressContent(viewModel, isVideo) { progress ->
                                    PlayerProgress(
                                        positionMs = progress.positionMs,
                                        durationMs = progressDurationMs,
                                        sliceUiState = sliceUiState,
                                        onSeekTo = { viewModel.seekTo(it) },
                                        onCutPressed = { viewModel.onCutPressed(progressDurationMs) },
                                        onScrubbingChanged = { viewModel.setUserScrubbing(it) },
                                        onSelectSlice = { viewModel.selectSlice(it) },
                                        onLongPressSlice = {
                                            viewModel.selectSlice(it)
                                            showSliceSheet = true
                                        },
                                        onUpdateSliceRange = { sliceId, startMs, endMs ->
                                            viewModel.updateSliceRange(sliceId, startMs, endMs, progressDurationMs)
                                        },
                                        activeColor = accentColor,
                                        inactiveColor = accentColor.copy(alpha = 0.2f),
                                        compactLayout = portraitLayoutMetrics.compact
                                    )
                                }
                            }
                        }

                        PlaybackControls(
                            playback = playback,
                            isFavorite = isFavorite,
                            viewModel = viewModel,
                            onShowPlaylistPicker = {
                                val current = playback.currentMediaItem ?: return@PlaybackControls
                                onOpenPlaylistPicker(current)
                            },
                            onShowEqualizer = { showEqualizer = true },
                            onManageTags = {
                                val mediaId = item?.mediaId.orEmpty()
                                val fallback = metadata?.title?.toString().orEmpty()
                                tagViewModel.openForMediaId(mediaId, fallback)
                            },
                            sliceUiState = sliceUiState,
                            modifier = Modifier.padding(horizontal = portraitContentHorizontalPadding),
                            actionRowModifier = actionRowMotion,
                            coreControlsModifier = controlsMotion,
                            primaryColor = accentColor,
                            onPrimaryColor = onAccentColor,
                            compactLayout = portraitLayoutMetrics.compact
                        )

                        VolumeControl(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = portraitContentHorizontalPadding)
                                .then(volumeMotion)
                                .onGloballyPositioned { coordinates ->
                                    volumeControlBounds = coordinates.boundsInRoot()
                                },
                            accentColor = accentColor,
                            viewModel = viewModel,
                            hardwareVolumeEventTick = hardwareVolumeEventTick,
                            audioOutputRouteKind = audioOutputRouteKind,
                            warningSessionState = warningSessionState,
                            expanded = volumeControlExpanded,
                            onExpandedChange = { volumeControlExpanded = it },
                            compactLayout = portraitLayoutMetrics.compact
                        )
                    }
                }
            }
            }
            }
            } else {
                PlaybackProgressContent(viewModel, isVideo) { progress ->
                    NowPlayingLyricsSurface(
                        isLandscape = isLandscape,
                        playbackPositionMs = progress.positionMs,
                        lyrics = lyricsState.lyrics,
                        lyricColors = lyricsPageColors,
                        accentColor = accentColor,
                        spectrumColor = playerPageAccentColor,
                        onAccentColor = onAccentColor,
                        lyricsPageSettings = lyricsPageSettings,
                        onSeekTo = { viewModel.seekTo(it) },
                        onTimelinePlay = { targetMs ->
                            viewModel.seekTo(targetMs)
                            viewModel.play()
                        },
                        onAddLyrics = openManualLyricsAction,
                        contentKey = lyricsState.contentKey,
                        contentVisible = !lyricsState.isLoading,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(routeTransition.nowPlayingMotionModifier(currentMotionLayout, NowPlayingMotionSlot.COVER))
                    )
                }
            }
                }
            }
        }

        if (videoFullscreen && isVideo) {
            BackHandler { videoFullscreen = false }
            NowPlayingFullscreenVideo(
                player = player,
                coordinator = videoPlayerCoordinator,
                onDismiss = { videoFullscreen = false },
                modifier = Modifier.fillMaxSize()
            )
        }

        val dialog = tagDialog
        if (dialog != null) {
            TagAssignDialog(
                title = dialog.title,
                allTags = availableTags,
                inheritedTags = dialog.inheritedTags,
                userTags = dialog.userTags,
                onDismiss = { tagViewModel.dismiss() },
                onApplyUserTags = { tagViewModel.applyUserTags(it) }
            )
        }

        if (showSliceSheet) {
            PlayerModalSheet(onDismissRequest = dismissSliceSheet) { sheetMaxHeight ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = sheetMaxHeight)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "切片管理",
                            style = MaterialTheme.typography.titleMedium,
                            color = colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.clearSlicesForCurrentTrack() }) {
                            Text("清空")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    PlaybackProgressContent(viewModel, isVideo) { progress ->
                        val highlightedPlaybackSliceId = currentSliceIdForPosition(
                            positionMs = progress.positionMs,
                            slices = sliceUiState.slices,
                            sliceModeEnabled = sliceUiState.sliceModeEnabled
                        )
                        SliceOverviewBar(
                            positionMs = progress.positionMs,
                            durationMs = progressDurationMs,
                            slices = sliceUiState.slices,
                            highlightedSliceId = highlightedPlaybackSliceId,
                            selectedSliceId = sliceUiState.selectedSliceId,
                            activeColor = accentColor,
                            inactiveColor = accentColor.copy(alpha = 0.18f),
                            onSeekTo = { viewModel.seekTo(it) },
                            onSelectSlice = { id ->
                                if (id == null) viewModel.selectSlice(null) else toggleSelectedSlice(id)
                            },
                            onLongPressSlice = { id ->
                                toggleSelectedSlice(id)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (sliceUiState.slices.isEmpty()) {
                        Text(
                            text = "暂无切片",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.textTertiary,
                            modifier = Modifier.padding(vertical = 18.dp)
                        )
                    } else {
                        val sliceListState = rememberLazyListState()
                        LazyColumn(
                            state = sliceListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = true),
                            flingBehavior = rememberCalmScrollableFlingBehavior(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(sliceUiState.slices, key = { _, s -> s.id }) { index, slice ->
                                val selected = slice.id == sliceUiState.selectedSliceId
                                val bg = if (selected) accentColor.copy(alpha = 0.12f) else colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = bg,
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { toggleSelectedSlice(slice.id) }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = (index + 1).toString(),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = colorScheme.textTertiary,
                                            modifier = Modifier.widthIn(min = 18.dp)
                                        )

                                        TextButton(
                                            onClick = { timeEditTarget = slice.id to true },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text(Formatting.formatTrackTime(slice.startMs))
                                        }

                                        Text(
                                            text = "→",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.textTertiary
                                        )

                                        TextButton(
                                            onClick = { timeEditTarget = slice.id to false },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text(Formatting.formatTrackTime(slice.endMs))
                                        }

                                        Spacer(modifier = Modifier.weight(1f))

                                        IconButton(onClick = { viewModel.playSlicePreview(slice) }) {
                                            Icon(
                                                imageVector = Icons.Rounded.PlayArrow,
                                                contentDescription = "播放切片",
                                                tint = colorScheme.onSurface
                                            )
                                        }

                                        IconButton(onClick = { viewModel.deleteSlice(slice.id) }) {
                                            Icon(
                                                imageVector = Icons.Outlined.DeleteOutline,
                                                contentDescription = "删除切片",
                                                tint = colorScheme.onSurface.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
                            }
                            item { Spacer(modifier = Modifier.height(18.dp)) }
                        }
                    }
                }
            }
        }

        val edit = timeEditTarget
        if (edit != null) {
            val slice = sliceUiState.slices.firstOrNull { it.id == edit.first }
            if (slice != null) {
                PlaybackProgressContent(viewModel, isVideo) { progress ->
                    SliceTimeEditDialog(
                        title = if (edit.second) "修改起点" else "修改终点",
                        durationMs = progressDurationMs,
                        currentMs = progress.positionMs,
                        initialMs = if (edit.second) slice.startMs else slice.endMs,
                        onDismiss = { timeEditTarget = null },
                        onConfirm = { newMs ->
                            if (edit.second) {
                                viewModel.updateSliceRange(slice.id, newMs, slice.endMs, progressDurationMs)
                            } else {
                                viewModel.updateSliceRange(slice.id, slice.startMs, newMs, progressDurationMs)
                            }
                            timeEditTarget = null
                        }
                    )
                }
            } else {
                timeEditTarget = null
            }
        }

        if (showEqualizer) {
            val eqSettings by viewModel.sessionEqualizer.collectAsStateWithLifecycle()
            val customPresets by viewModel.customPresets.collectAsStateWithLifecycle()
            val appVolumePercent by viewModel.appVolumePercent.collectAsStateWithLifecycle()
            val equalizerFocusRequester = remember { FocusRequester() }
            var showEqualizerVolumeOverlay by remember { mutableStateOf(false) }
            var equalizerVolumeOverlayInteracting by remember { mutableStateOf(false) }
            var equalizerVolumeOverlayHoldTick by remember { mutableLongStateOf(0L) }
            var equalizerVolumeOverlayBounds by remember { mutableStateOf<Rect?>(null) }
            var lastNonZeroEqualizerVolume by remember { mutableIntStateOf(AppVolume.DefaultPercent) }
            LaunchedEffect(equalizerFocusRequester) {
                equalizerFocusRequester.requestFocus()
            }
            LaunchedEffect(appVolumePercent) {
                if (appVolumePercent > 0) {
                    lastNonZeroEqualizerVolume = appVolumePercent
                }
            }
            LaunchedEffect(showEqualizerVolumeOverlay, equalizerVolumeOverlayHoldTick, equalizerVolumeOverlayInteracting) {
                if (!showEqualizerVolumeOverlay) return@LaunchedEffect
                if (equalizerVolumeOverlayInteracting) return@LaunchedEffect
                val snapshot = equalizerVolumeOverlayHoldTick
                delay(2_000)
                if (!equalizerVolumeOverlayInteracting && equalizerVolumeOverlayHoldTick == snapshot) {
                    showEqualizerVolumeOverlay = false
                    equalizerVolumeOverlayBounds = null
                }
            }
            PlayerModalSheet(onDismissRequest = { showEqualizer = false }) { sheetMaxHeight ->
                val scrollState = rememberScrollState()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = sheetMaxHeight)
                        .focusRequester(equalizerFocusRequester)
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.nativeKeyEvent.keyCode) {
                                AndroidKeyEvent.KEYCODE_VOLUME_UP -> {
                                    viewModel.adjustAppVolumePercent(AppVolume.StepPercent)
                                    showEqualizerVolumeOverlay = true
                                    equalizerVolumeOverlayHoldTick += 1L
                                    true
                                }
                                AndroidKeyEvent.KEYCODE_VOLUME_DOWN -> {
                                    viewModel.adjustAppVolumePercent(-AppVolume.StepPercent)
                                    showEqualizerVolumeOverlay = true
                                    equalizerVolumeOverlayHoldTick += 1L
                                    true
                                }
                                else -> false
                            }
                        }
                ) {
                    EqualizerPanel(
                        settings = eqSettings,
                        customPresets = customPresets,
                        onSettingsChanged = { viewModel.updateSessionEqualizer(it) },
                        onSavePreset = { name -> viewModel.saveCustomPreset(name, eqSettings) },
                        onDeletePreset = { viewModel.deleteCustomPreset(it) },
                        playbackSpeed = playback.playbackSpeed,
                        playbackPitch = playback.playbackPitch,
                        onPlaybackSpeedChanged = { viewModel.setPlaybackParameters(it, playback.playbackPitch) },
                        onPlaybackPitchChanged = { viewModel.setPlaybackParameters(playback.playbackSpeed, it) },
                        onPlaybackParametersChanged = { speed, pitch -> viewModel.setPlaybackParameters(speed, pitch) },
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(
                                state = scrollState,
                                flingBehavior = rememberCalmScrollableFlingBehavior()
                            )
                            .padding(bottom = 32.dp)
                    )
                    if (showEqualizerVolumeOverlay) {
                        DismissOutsideBoundsOverlay(
                            targetBoundsInRoot = equalizerVolumeOverlayBounds,
                            onDismiss = {
                                showEqualizerVolumeOverlay = false
                                equalizerVolumeOverlayBounds = null
                            }
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 18.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showEqualizerVolumeOverlay,
                            enter = fadeIn(animationSpec = tween(140)) + slideInHorizontally(animationSpec = tween(180)) { it / 3 },
                            exit = fadeOut(animationSpec = tween(160)) + slideOutHorizontally(animationSpec = tween(180)) { it / 3 }
                        ) {
                            HardwareVolumeOverlay(
                                modifier = Modifier.onGloballyPositioned { coordinates ->
                                    equalizerVolumeOverlayBounds = coordinates.boundsInRoot()
                                },
                                volumePercent = appVolumePercent,
                                audioOutputRouteKind = audioOutputRouteKind,
                                onVolumeChange = {
                                    viewModel.setAppVolumePercent(it)
                                    equalizerVolumeOverlayHoldTick += 1L
                                },
                                onToggleMute = {
                                    if (appVolumePercent > 0) {
                                        viewModel.setAppVolumePercent(0)
                                    } else {
                                        viewModel.setAppVolumePercent(
                                            lastNonZeroEqualizerVolume.coerceAtLeast(AppVolume.StepPercent)
                                        )
                                    }
                                    equalizerVolumeOverlayHoldTick += 1L
                                },
                                onInteractionActiveChanged = { active ->
                                    equalizerVolumeOverlayInteracting = active
                                    if (!active) {
                                        equalizerVolumeOverlayHoldTick += 1L
                                    }
                                },
                                warningSessionState = warningSessionState
                            )
                        }
                    }
                }
            }
        }

        if (volumeControlExpanded) {
            DismissOutsideBoundsOverlay(
                targetBoundsInRoot = volumeControlBounds,
                onDismiss = { volumeControlExpanded = false }
            )
        }
    }
}
