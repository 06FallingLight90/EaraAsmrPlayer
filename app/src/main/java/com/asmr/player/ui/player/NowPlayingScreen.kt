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
    val homeLayoutHintDismissedInSession = rememberSaveable { mutableStateOf(false) }
    val homeLayoutHintShownInSession = rememberSaveable { mutableStateOf(false) }
    val homeLayoutHintMediaId = remember { mutableStateOf<String?>(null) }
    val homeLayoutLyricsVisible = remember { mutableStateOf(true) }
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
                if (homeLayoutHintMediaId.value != null && !homeLayoutHintDismissedInSession.value) {
                    homeLayoutHintScope.launch {
                        delay(NowPlayingHomeLayoutAnimationDurationMillis.toLong())
                        homeLayoutHintDismissedInSession.value = true
                        homeLayoutHintMediaId.value = null
                    }
                }
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                homeLayoutChangeJob = homeLayoutHintScope.launch {
                    homeLayoutLyricsVisible.value = false
                    delay(NowPlayingHomeLyricsFadeOutDurationMillis.toLong())
                    onNowPlayingHomeLayoutModeChange(mode)
                    delay(NowPlayingHomeLayoutAnimationDurationMillis.toLong())
                    homeLayoutLyricsVisible.value = true
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

            if (split || phoneLandscape) {
            NowPlayingLandscapeLayout(
                split = split,
                phoneLandscape = phoneLandscape,
                motionLayout = motionLayout,
                renderVideoSurface = renderVideoSurface,
                routeTransition = routeTransition,
                landscapeLayoutMetrics = landscapeLayoutMetrics,
                playback = playback,
                item = item,
                metadata = metadata,
                viewModel = viewModel,
                videoPlayerCoordinator = videoPlayerCoordinator,
                videoFullscreen = videoFullscreen,
                onOpenVideoFullscreen = { videoFullscreen = true },
                onOpenLyrics = showLyricsSurface,
                playerArtworkBackdropEnabled = playerArtworkBackdropEnabled,
                playerThemeColors = playerThemeColors,
                colorScheme = colorScheme,
                videoBackdropColor = videoBackdropColor,
                coverPreviewAlignment = coverPreviewAlignment,
                useDragPreview = useDragPreview,
                coverDragPreviewState = coverDragPreviewState,
                videoAspectRatio = videoAspectRatio,
                tabletQueueExpanded = tabletQueueExpanded,
                onTabletQueueExpandedChange = { tabletQueueExpanded = it },
                sliceUiState = sliceUiState,
                progressDurationMs = progressDurationMs,
                accentColor = accentColor,
                onAccentColor = onAccentColor,
                setShowSliceSheet = { showSliceSheet = it },
                playerHeaderTitle = playerHeaderTitle,
                playerArtistMeta = playerArtistMeta,
                listenTogetherUiState = listenTogetherUiState,
                pageEntranceSettled = pageEntranceSettled,
                landscapeArtistBottom = landscapeArtistBottom,
                onLandscapeArtistBottomChanged = { bottom ->
                    if (!landscapeArtistBottom.isFinite() || abs(landscapeArtistBottom - bottom) > 0.5f) {
                        landscapeArtistBottom = bottom
                    }
                },
                landscapeCurrentLyricAnchorTop = landscapeCurrentLyricAnchorTop,
                onLandscapeCurrentLyricAnchorChanged = { top ->
                    if (!landscapeCurrentLyricAnchorTop.isFinite() || abs(landscapeCurrentLyricAnchorTop - top) > 0.5f) {
                        landscapeCurrentLyricAnchorTop = top
                    }
                },
                lyricsState = lyricsState,
                lyricColors = lyricColors,
                nowPlayingLyricsSettings = nowPlayingLyricsSettings,
                isFavorite = isFavorite,
                onOpenPlaylistPicker = onOpenPlaylistPicker,
                isVideo = isVideo,
                playerPageAccentColor = playerPageAccentColor,
                onShowEqualizer = { showEqualizer = true },
                tagViewModel = tagViewModel
            )
        } else {
            NowPlayingPortraitLayout(
                motionLayout = motionLayout,
                routeTransition = routeTransition,
                isVideo = isVideo,
                widthClass = widthClass,
                pendingRouteExit = pendingRouteExit,
                nowPlayingHomeLayoutMode = nowPlayingHomeLayoutMode,
                lyricsPageSettings = lyricsPageSettings,
                nowPlayingHomeLayoutHintDismissed = nowPlayingHomeLayoutHintDismissed,
                homeLayoutHintDismissedInSessionState = homeLayoutHintDismissedInSession,
                homeLayoutHintShownInSessionState = homeLayoutHintShownInSession,
                homeLayoutHintMediaIdState = homeLayoutHintMediaId,
                homeLayoutLyricsVisibleState = homeLayoutLyricsVisible,
                changeNowPlayingHomeLayoutMode = changeNowPlayingHomeLayoutMode,
                onNowPlayingHomeLayoutHintShown = onNowPlayingHomeLayoutHintShown,
                artworkModel = artworkModel,
                renderVideoSurface = renderVideoSurface,
                playback = playback,
                item = item,
                metadata = metadata,
                viewModel = viewModel,
                videoPlayerCoordinator = videoPlayerCoordinator,
                videoFullscreen = videoFullscreen,
                onOpenVideoFullscreen = { videoFullscreen = true },
                onOpenLyrics = showLyricsSurface,
                playerArtworkBackdropEnabled = playerArtworkBackdropEnabled,
                playerThemeColors = playerThemeColors,
                colorScheme = colorScheme,
                videoBackdropColor = videoBackdropColor,
                coverPreviewAlignment = coverPreviewAlignment,
                useDragPreview = useDragPreview,
                coverDragPreviewState = coverDragPreviewState,
                videoAspectRatio = videoAspectRatio,
                playerPageAccentColor = playerPageAccentColor,
                playerHeaderTitle = playerHeaderTitle,
                playerArtistMeta = playerArtistMeta,
                listenTogetherUiState = listenTogetherUiState,
                pageEntranceSettled = pageEntranceSettled,
                nowPlayingLyricsSettings = nowPlayingLyricsSettings,
                accentColor = accentColor,
                onAccentColor = onAccentColor,
                lyricsState = lyricsState,
                lyricColors = lyricColors,
                openManualLyricsAction = openManualLyricsAction,
                sliceUiState = sliceUiState,
                progressDurationMs = progressDurationMs,
                setShowSliceSheet = { showSliceSheet = it },
                isFavorite = isFavorite,
                onShowEqualizer = { showEqualizer = true },
                onOpenPlaylistPicker = onOpenPlaylistPicker,
                tagViewModel = tagViewModel,
                hardwareVolumeEventTick = hardwareVolumeEventTick,
                audioOutputRouteKind = audioOutputRouteKind,
                warningSessionState = warningSessionState,
                volumeControlExpanded = volumeControlExpanded,
                onVolumeControlExpandedChange = { volumeControlExpanded = it },
                setVolumeControlBounds = { volumeControlBounds = it }
            )
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


        NowPlayingTagDialogHost(
            dialog = tagDialog,
            availableTags = availableTags,
            tagViewModel = tagViewModel
        )

        NowPlayingSliceOverlaysHost(
            viewModel = viewModel,
            sliceUiState = sliceUiState,
            progressDurationMs = progressDurationMs,
            accentColor = accentColor,
            isVideo = isVideo,
            showSliceSheet = showSliceSheet,
            dismissSliceSheet = dismissSliceSheet,
            toggleSelectedSlice = toggleSelectedSlice,
            timeEditTarget = timeEditTarget,
            setTimeEditTarget = { timeEditTarget = it }
        )

        NowPlayingEqualizerSheetHost(
            viewModel = viewModel,
            showEqualizer = showEqualizer,
            onDismiss = { showEqualizer = false },
            playback = playback,
            audioOutputRouteKind = audioOutputRouteKind,
            warningSessionState = warningSessionState
        )

        if (volumeControlExpanded) {
            DismissOutsideBoundsOverlay(
                targetBoundsInRoot = volumeControlBounds,
                onDismiss = { volumeControlExpanded = false }
            )
        }
    }
}
