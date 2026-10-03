package com.asmr.player.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.asmr.player.listentogether.ListenTogetherUiState
import com.asmr.player.ui.theme.AsmrColorScheme
import com.asmr.player.data.settings.NowPlayingHomeLayoutMode
import com.asmr.player.data.settings.NowPlayingLyricsSettings
import com.asmr.player.playback.PlaybackSnapshot
import com.asmr.player.ui.player.nowplaying.ArtworkBox
import com.asmr.player.ui.player.nowplaying.NowPlayingVideoPlayerCoordinator
import com.asmr.player.ui.player.nowplaying.PlaybackControls
import com.asmr.player.ui.player.nowplaying.PlayerProgress
import com.asmr.player.ui.player.nowplaying.TabletLandscapeQueuePanel
import com.asmr.player.ui.player.nowplaying.NowPlayingLyricsPreview
import kotlin.math.abs

/**
 * 鎾斁椤垫í灞忓弻甯冨眬锛氬钩鏉?split锛堝皝闈?闃熷垪+姝岃瘝棰勮+鎺у埗琛岋級涓庢墜鏈烘í灞?compact銆? * 鍒嗘敮浣撻€愬潡鑷?NowPlayingScreen 涓讳綋鎼Щ锛沵otion 淇グ绗﹀湪鍚勫垎鏀唴鑷鍙栬嚜 routeTransition銆? */
@Composable
internal fun NowPlayingLandscapeLayout(
    split: Boolean,
    phoneLandscape: Boolean,
    motionLayout: NowPlayingMotionLayout,
    renderVideoSurface: Boolean,
    routeTransition: Transition<Boolean>,
    landscapeLayoutMetrics: NowPlayingLandscapeLayoutMetrics,
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
    tabletQueueExpanded: Boolean,
    onTabletQueueExpandedChange: (Boolean) -> Unit,
    sliceUiState: SliceUiState,
    progressDurationMs: Long,
    accentColor: Color,
    onAccentColor: Color,
    setShowSliceSheet: (Boolean) -> Unit,
    playerHeaderTitle: String,
    playerArtistMeta: NowPlayingArtistMeta,
    listenTogetherUiState: ListenTogetherUiState,
    pageEntranceSettled: Boolean,
    landscapeArtistBottom: Float,
    onLandscapeArtistBottomChanged: (Float) -> Unit,
    landscapeCurrentLyricAnchorTop: Float,
    onLandscapeCurrentLyricAnchorChanged: (Float) -> Unit,
    lyricsState: LyricsUiState,
    lyricColors: LyricReadableColors,
    nowPlayingLyricsSettings: NowPlayingLyricsSettings,
    isFavorite: Boolean,
    onOpenPlaylistPicker: (MediaItem) -> Unit,
    onShowEqualizer: () -> Unit,
    isVideo: Boolean,
    playerPageAccentColor: Color,
    tagViewModel: NowPlayingTagViewModel
) {
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
                                    onOpenVideoFullscreen = onOpenVideoFullscreen,
                                    onOpenLyrics = onOpenLyrics,
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
                                onExpandedChange = onTabletQueueExpandedChange,
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
                                            setShowSliceSheet(true)
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
                            onArtistBottomChanged = onLandscapeArtistBottomChanged,
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
                                        onOpenLyrics = onOpenLyrics,
                                        colors = lyricColors,
                                        interactionEnabled = !lyricsState.isLoading,
                                        highlightFontSizeSp = nowPlayingLyricsSettings.highlightFontSizeSp,
                                        tabletLayout = true,
                                        contentTopPadding = landscapeLayoutMetrics.lyricsTopPadding,
                                    onCurrentLineAnchorChanged = onLandscapeCurrentLyricAnchorChanged,
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
                            onShowEqualizer = onShowEqualizer,
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
        }
        else if (phoneLandscape) {
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
                                onOpenVideoFullscreen = onOpenVideoFullscreen,
                                onOpenLyrics = onOpenLyrics,
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
                                        setShowSliceSheet(true)
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
                            onArtistBottomChanged = onLandscapeArtistBottomChanged,
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
                                    onOpenLyrics = onOpenLyrics,
                                    colors = lyricColors,
                                    interactionEnabled = !lyricsState.isLoading,
                                    highlightFontSizeSp = nowPlayingLyricsSettings.highlightFontSizeSp,
                                    compactHeight = landscapeLayoutMetrics.compactHeight,
                                    contentTopPadding = landscapeLayoutMetrics.lyricsTopPadding,
                                    onCurrentLineAnchorChanged = onLandscapeCurrentLyricAnchorChanged,
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
                        onShowEqualizer = onShowEqualizer,
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
    }
}
