package com.asmr.player.ui.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import com.asmr.player.ui.common.core.isCompactWidth
import com.asmr.player.data.settings.NowPlayingHomeLayoutMode
import com.asmr.player.data.settings.LyricsPageSettings
import com.asmr.player.data.settings.NowPlayingLyricsSettings
import com.asmr.player.listentogether.ListenTogetherUiState
import com.asmr.player.playback.PlaybackSnapshot
import com.asmr.player.ui.player.nowplaying.ArtworkBox
import com.asmr.player.ui.player.nowplaying.NowPlayingLyricsPreview
import com.asmr.player.ui.player.nowplaying.NowPlayingLyricsSurface
import com.asmr.player.ui.player.nowplaying.NowPlayingVideoPlayerCoordinator
import com.asmr.player.ui.player.nowplaying.PlaybackControls
import com.asmr.player.ui.player.nowplaying.PlayerProgress
import com.asmr.player.ui.player.nowplaying.VolumeControl
import com.asmr.player.ui.player.nowplaying.multilineLyricsReserveHeight
import com.asmr.player.ui.player.nowplaying.nowPlayingLyricTypographyMetrics
import com.asmr.player.service.AudioOutputRouteKind
import com.asmr.player.ui.common.audio.AppVolumeWarningSessionState
import com.asmr.player.ui.theme.AsmrColorScheme
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

/**
 * 鎾斁椤电珫灞忥紙鎵嬫満/骞虫澘绔栧睆锛夊竷灞€锛歝lassic 涓?expanded 鍙屽舰鎬佸強杩囨浮鍔ㄧ敾銆? * 鍒嗘敮浣撻€愬潡鑷?NowPlayingScreen 涓讳綋鎼Щ锛沨omeLayout 鎻愮ず绨囩姸鎬佺粡 MutableState 浼犲叆銆? */
@Composable
internal fun NowPlayingPortraitLayout(
    motionLayout: NowPlayingMotionLayout,
    routeTransition: Transition<Boolean>,
    isVideo: Boolean,
    widthClass: WindowWidthSizeClass,
    pendingRouteExit: Boolean,
    nowPlayingHomeLayoutMode: NowPlayingHomeLayoutMode,
    lyricsPageSettings: LyricsPageSettings,
    nowPlayingHomeLayoutHintDismissed: Boolean?,
    homeLayoutHintDismissedInSessionState: MutableState<Boolean>,
    homeLayoutHintShownInSessionState: MutableState<Boolean>,
    homeLayoutHintMediaIdState: MutableState<String?>,
    homeLayoutLyricsVisibleState: MutableState<Boolean>,
    changeNowPlayingHomeLayoutMode: (NowPlayingHomeLayoutMode) -> Unit,
    onNowPlayingHomeLayoutHintShown: () -> Unit,
    artworkModel: Any?,
    renderVideoSurface: Boolean,
    playback: PlaybackSnapshot,
    item: MediaItem?,
    metadata: MediaMetadata?,
    viewModel: PlayerViewModel,
    videoPlayerCoordinator: NowPlayingVideoPlayerCoordinator,
    videoFullscreen: Boolean,
    onOpenVideoFullscreen: () -> Unit,
    onOpenLyrics: () -> Unit,
    playerArtworkBackdropEnabled: Boolean,
    playerThemeColors: PlayerThemeColors,
    colorScheme: AsmrColorScheme,
    videoBackdropColor: Color,
    coverPreviewAlignment: Alignment,
    useDragPreview: Boolean,
    coverDragPreviewState: CoverDragPreviewState,
    videoAspectRatio: Float,
    playerPageAccentColor: Color,
    playerHeaderTitle: String,
    playerArtistMeta: NowPlayingArtistMeta,
    listenTogetherUiState: ListenTogetherUiState,
    pageEntranceSettled: Boolean,
    nowPlayingLyricsSettings: NowPlayingLyricsSettings,
    accentColor: Color,
    onAccentColor: Color,
    lyricsState: LyricsUiState,
    lyricColors: LyricReadableColors,
    openManualLyricsAction: (() -> Unit)?,
    sliceUiState: SliceUiState,
    progressDurationMs: Long,
    setShowSliceSheet: (Boolean) -> Unit,
    isFavorite: Boolean,
    onShowEqualizer: () -> Unit,
    onOpenPlaylistPicker: (MediaItem) -> Unit,
    tagViewModel: NowPlayingTagViewModel,
    hardwareVolumeEventTick: Long,
    audioOutputRouteKind: AudioOutputRouteKind,
    warningSessionState: AppVolumeWarningSessionState,
    volumeControlExpanded: Boolean,
    onVolumeControlExpandedChange: (Boolean) -> Unit,
    setVolumeControlBounds: (androidx.compose.ui.geometry.Rect?) -> Unit
) {
            val coverMotion = routeTransition.nowPlayingMotionModifier(motionLayout, NowPlayingMotionSlot.COVER)
            val lyricsMotion = routeTransition.nowPlayingMotionModifier(motionLayout, NowPlayingMotionSlot.LYRICS)
            val progressMotion = routeTransition.nowPlayingMotionModifier(motionLayout, NowPlayingMotionSlot.PROGRESS)
            val actionRowMotion = routeTransition.nowPlayingMotionModifier(motionLayout, NowPlayingMotionSlot.ACTION_ROW)
            val controlsMotion = routeTransition.nowPlayingMotionModifier(motionLayout, NowPlayingMotionSlot.CONTROLS)
            val volumeMotion = routeTransition.nowPlayingMotionModifier(motionLayout, NowPlayingMotionSlot.VOLUME)
    val configuration = LocalConfiguration.current
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
                if (homeLayoutHintMediaIdState.value != null && homeLayoutHintMediaIdState.value != hintMediaId) {
                    homeLayoutHintMediaIdState.value = null
                }
                if (hintMediaId != null && !isVideo && !expandedHomeLayout &&
                    nowPlayingHomeLayoutHintDismissed == false &&
                    !homeLayoutHintShownInSessionState.value && !homeLayoutHintDismissedInSessionState.value
                ) {
                    homeLayoutHintShownInSessionState.value = true
                    homeLayoutHintMediaIdState.value = hintMediaId
                    markHomeLayoutHintShown()
                }
            }
            val homeLayoutSwipeHintAllowed = hintMediaId != null &&
                homeLayoutHintMediaIdState.value == hintMediaId &&
                !homeLayoutHintDismissedInSessionState.value && !isVideo
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
                    homeLayoutLyricsVisibleState.value = false
                }
            }
            LaunchedEffect(homeLayoutSettled) {
                if (homeLayoutSettled) {
                    homeLayoutLyricsVisibleState.value = true
                }
            }
            val expandedLyricsActive = expandedHomeLayout && homeLayoutSettled && homeLayoutLyricsVisibleState.value
            val classicLyricsActive = !expandedHomeLayout && homeLayoutSettled && homeLayoutLyricsVisibleState.value
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
                                            onOpenVideoFullscreen = onOpenVideoFullscreen,
                                            onOpenLyrics = onOpenLyrics,
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
                                                    onOpenLyrics = onOpenLyrics,
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
                                            setShowSliceSheet(true)
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
                            onShowEqualizer = onShowEqualizer,
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
                                    setVolumeControlBounds(coordinates.boundsInRoot())
                                },
                            accentColor = accentColor,
                            viewModel = viewModel,
                            hardwareVolumeEventTick = hardwareVolumeEventTick,
                            audioOutputRouteKind = audioOutputRouteKind,
                            warningSessionState = warningSessionState,
                            expanded = volumeControlExpanded,
                            onExpandedChange = onVolumeControlExpandedChange,
                            compactLayout = portraitLayoutMetrics.compact
                        )
                    }
                }
            }
        }
