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
import com.asmr.player.ui.common.audio.HardwareVolumeOverlay
import com.asmr.player.util.CachePolicy
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

internal enum class NowPlayingSurfaceMode {
    PLAYER,
    LYRICS
}

internal const val VideoProgressUiTickMs = 1_000L
internal const val NowPlayingHomeLayoutAnimationDurationMillis = 620
internal const val NowPlayingHomeLyricsFadeInDurationMillis = 240
internal const val NowPlayingHomeLyricsFadeOutDurationMillis = 160
internal const val TabletLandscapeQueueAutoCollapseMillis = 10_000L
internal val NowPlayingPortraitMaxContentWidth = 600.dp
internal val NowPlayingCompactShortScreenHeight = 700.dp
internal val NowPlayingClassicAudienceHeight = 18.dp
internal val NowPlayingClassicTrackInfoSingleLineHeight = 88.dp
internal val NowPlayingHomeClassicLyricsReserveHeight = 56.dp
internal val NowPlayingHomeExpandedLyricsReserveHeight = 118.dp
internal val NowPlayingHomeCompactMinCoverWidth = 180.dp
internal val NowPlayingHomeRegularMinCoverWidth = 240.dp
internal val NowPlayingHomeClassicRegularMaxCoverWidth = 360.dp
internal val NowPlayingPortraitIdentityMaxWidth = 320.dp
internal val NowPlayingPhoneLandscapeCompactHeight = 360.dp
internal val NowPlayingPortraitArtworkCornerRadius = 16.dp
internal val NowPlayingCompactLandscapeArtworkCornerRadius = 10.dp
internal val NowPlayingPhoneLandscapeArtworkCornerRadius = 12.dp
internal val NowPlayingTabletLandscapeArtworkCornerRadius = 14.dp
internal const val NowPlayingHomeClassicCompactCoverScale = 0.92f

internal data class NowPlayingPortraitLayoutMetrics(
    val compact: Boolean,
    val contentHorizontalPadding: Dp,
    val topPadding: Dp,
    val coverVerticalPadding: Dp,
    val audienceHeight: Dp,
    val trackInfoSingleLineHeight: Dp,
    val classicLyricsReserveHeight: Dp,
    val expandedLyricsReserveHeight: Dp,
    val minimumCoverWidth: Dp,
    val expandedLyricsTopPadding: Dp,
    val bottomPadding: Dp,
    val bottomSectionSpacing: Dp
)

internal data class NowPlayingLandscapeLayoutMetrics(
    val compactHeight: Boolean,
    val tabletLayout: Boolean,
    val horizontalPadding: Dp,
    val topPadding: Dp,
    val bottomPadding: Dp,
    val contentSpacing: Dp,
    val sectionSpacing: Dp,
    val artworkWeight: Float,
    val contentWeight: Float,
    val identityMinHeight: Dp,
    val lyricsTopPadding: Dp,
    val progressHeight: Dp,
    val controlsHeight: Dp,
    val artworkCornerRadius: Dp,
    val artworkMaxSize: Dp,
    val progressMaxWidth: Dp,
    val spectrumHeight: Dp
)

internal fun nowPlayingLandscapeLayoutMetrics(
    screenHeight: Dp,
    tabletLayout: Boolean
): NowPlayingLandscapeLayoutMetrics {
    if (tabletLayout) {
        return NowPlayingLandscapeLayoutMetrics(
            compactHeight = false,
            tabletLayout = true,
            horizontalPadding = 32.dp,
            topPadding = 20.dp,
            bottomPadding = 20.dp,
            contentSpacing = 40.dp,
            sectionSpacing = 12.dp,
            artworkWeight = 0.45f,
            contentWeight = 0.55f,
            identityMinHeight = 90.dp,
            lyricsTopPadding = 76.dp,
            progressHeight = 64.dp,
            controlsHeight = 80.dp,
            artworkCornerRadius = NowPlayingTabletLandscapeArtworkCornerRadius,
            artworkMaxSize = 336.dp,
            progressMaxWidth = 380.dp,
            spectrumHeight = 112.dp
        )
    }
    val compactHeight = screenHeight.isFiniteDp() &&
        screenHeight <= NowPlayingPhoneLandscapeCompactHeight
    return if (compactHeight) {
        NowPlayingLandscapeLayoutMetrics(
            compactHeight = true,
            tabletLayout = false,
            horizontalPadding = 12.dp,
            topPadding = 4.dp,
            bottomPadding = 14.dp,
            contentSpacing = 16.dp,
            sectionSpacing = 2.dp,
            artworkWeight = 0.39f,
            contentWeight = 0.61f,
            identityMinHeight = 64.dp,
            lyricsTopPadding = 48.dp,
            progressHeight = 60.dp,
            controlsHeight = 72.dp,
            artworkCornerRadius = NowPlayingCompactLandscapeArtworkCornerRadius,
            artworkMaxSize = 260.dp,
            progressMaxWidth = 300.dp,
            spectrumHeight = 88.dp
        )
    } else {
        NowPlayingLandscapeLayoutMetrics(
            compactHeight = false,
            tabletLayout = false,
            horizontalPadding = 20.dp,
            topPadding = 8.dp,
            bottomPadding = 16.dp,
            contentSpacing = 24.dp,
            sectionSpacing = 6.dp,
            artworkWeight = 0.40f,
            contentWeight = 0.60f,
            identityMinHeight = 76.dp,
            lyricsTopPadding = 56.dp,
            progressHeight = 62.dp,
            controlsHeight = 80.dp,
            artworkCornerRadius = NowPlayingPhoneLandscapeArtworkCornerRadius,
            artworkMaxSize = 292.dp,
            progressMaxWidth = 340.dp,
            spectrumHeight = 88.dp
        )
    }
}

internal fun landscapeSpectrumCenterY(
    artistInfoBottom: Float,
    currentLyricAnchorTop: Float,
    fallbackCenterY: Float
): Float {
    return if (
        artistInfoBottom.isFinite() &&
        currentLyricAnchorTop.isFinite() &&
        currentLyricAnchorTop > artistInfoBottom
    ) {
        (artistInfoBottom + currentLyricAnchorTop) / 2f
    } else {
        fallbackCenterY
    }
}

internal fun nowPlayingPortraitLayoutMetrics(
    screenHeight: Dp,
    widthClass: WindowWidthSizeClass
): NowPlayingPortraitLayoutMetrics {
    val compact = widthClass.isCompactWidth &&
        screenHeight.isFiniteDp() &&
        screenHeight <= NowPlayingCompactShortScreenHeight
    if (compact) {
        return NowPlayingPortraitLayoutMetrics(
            compact = true,
            contentHorizontalPadding = 20.dp,
            topPadding = 8.dp,
            coverVerticalPadding = 4.dp,
            audienceHeight = 16.dp,
            trackInfoSingleLineHeight = 65.dp,
            classicLyricsReserveHeight = 46.dp,
            expandedLyricsReserveHeight = 96.dp,
            minimumCoverWidth = 148.dp,
            expandedLyricsTopPadding = 8.dp,
            bottomPadding = 8.dp,
            bottomSectionSpacing = 2.dp
        )
    }
    return NowPlayingPortraitLayoutMetrics(
        compact = false,
        contentHorizontalPadding = 24.dp,
        topPadding = 24.dp,
        coverVerticalPadding = if (widthClass.isCompactWidth) 16.dp else 32.dp,
        audienceHeight = NowPlayingClassicAudienceHeight,
        trackInfoSingleLineHeight = NowPlayingClassicTrackInfoSingleLineHeight,
        classicLyricsReserveHeight = NowPlayingHomeClassicLyricsReserveHeight,
        expandedLyricsReserveHeight = NowPlayingHomeExpandedLyricsReserveHeight,
        minimumCoverWidth = nowPlayingHomeMinCoverWidth(widthClass),
        expandedLyricsTopPadding = 14.dp,
        bottomPadding = 16.dp,
        bottomSectionSpacing = 4.dp
    )
}

internal fun nowPlayingClassicTrackInfoHeight(
    metrics: NowPlayingPortraitLayoutMetrics? = null
): Dp = metrics?.trackInfoSingleLineHeight ?: NowPlayingClassicTrackInfoSingleLineHeight

internal fun nowPlayingHomeCoverWidth(
    expanded: Boolean,
    availableWidth: Dp,
    availableHeight: Dp = Dp.Unspecified,
    widthClass: WindowWidthSizeClass,
    contentHorizontalPadding: Dp,
    coverAspectRatio: Float = 1f,
    topPadding: Dp = if (expanded) 0.dp else 24.dp,
    coverVerticalPadding: Dp = if (expanded) 0.dp else if (widthClass.isCompactWidth) 16.dp else 32.dp,
    identityHeight: Dp = if (expanded) {
        0.dp
    } else {
        NowPlayingClassicAudienceHeight + nowPlayingClassicTrackInfoHeight()
    },
    lyricsReserveHeight: Dp = if (expanded) NowPlayingHomeExpandedLyricsReserveHeight else NowPlayingHomeClassicLyricsReserveHeight,
    minimumCoverWidth: Dp = nowPlayingHomeMinCoverWidth(widthClass)
): Dp {
    val fullWidth = availableWidth.coerceAtLeast(1.dp)
    val widthBound = if (expanded) {
        fullWidth
    } else {
        val paddedWidth = (fullWidth - contentHorizontalPadding * 2).coerceAtLeast(1.dp)
        if (widthClass.isCompactWidth) {
            paddedWidth * NowPlayingHomeClassicCompactCoverScale
        } else {
            paddedWidth.coerceAtMost(NowPlayingHomeClassicRegularMaxCoverWidth)
        }
    }
    if (!availableHeight.isFiniteDp()) return widthBound

    val safeAspectRatio = coverAspectRatio
        .takeIf { it.isFinite() && it > 0f }
        ?.coerceIn(0.5f, 3f)
        ?: 1f
    val reservedHeight = topPadding +
        coverVerticalPadding * 2 +
        identityHeight +
        lyricsReserveHeight
    val heightLimitedWidth = ((availableHeight - reservedHeight).coerceAtLeast(1.dp) * safeAspectRatio)
        .coerceAtLeast(minimumCoverWidth.coerceAtMost(widthBound))
    return widthBound.coerceAtMost(heightLimitedWidth)
}

internal fun nowPlayingHomeMinCoverWidth(widthClass: WindowWidthSizeClass): Dp {
    return if (widthClass.isCompactWidth) {
        NowPlayingHomeCompactMinCoverWidth
    } else {
        NowPlayingHomeRegularMinCoverWidth
    }
}

internal fun portraitClassicLyricsUpcomingCount(availableHeight: Dp): Int = when {
    !availableHeight.isFiniteDp() -> 0
    availableHeight >= 112.dp -> 2
    availableHeight >= 76.dp -> 1
    else -> 0
}

internal fun Dp.isFiniteDp(): Boolean = value.isFinite()

internal data class NowPlayingStaticPlayback(
    val isConnected: Boolean,
    val startupRestoreResolved: Boolean,
    val isPlaying: Boolean,
    val playWhenReady: Boolean,
    val playbackState: Int,
    val repeatMode: Int,
    val shuffleEnabled: Boolean,
    val playbackSpeed: Float,
    val playbackPitch: Float,
    val currentMediaItem: MediaItem?,
    val durationMs: Long,
    val audioSessionId: Int
) {
    fun toSnapshot(positionMs: Long): PlaybackSnapshot {
        return PlaybackSnapshot(
            isConnected = isConnected,
            startupRestoreResolved = startupRestoreResolved,
            isPlaying = isPlaying,
            playWhenReady = playWhenReady,
            playbackState = playbackState,
            repeatMode = repeatMode,
            shuffleEnabled = shuffleEnabled,
            playbackSpeed = playbackSpeed,
            playbackPitch = playbackPitch,
            currentMediaItem = currentMediaItem,
            positionMs = positionMs,
            durationMs = durationMs,
            audioSessionId = audioSessionId
        )
    }
}

internal data class NowPlayingProgressState(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

internal fun PlaybackSnapshot.toStaticPlayback(): NowPlayingStaticPlayback {
    return NowPlayingStaticPlayback(
        isConnected = isConnected,
        startupRestoreResolved = startupRestoreResolved,
        isPlaying = isPlaying,
        playWhenReady = playWhenReady,
        playbackState = playbackState,
        repeatMode = repeatMode,
        shuffleEnabled = shuffleEnabled,
        playbackSpeed = playbackSpeed,
        playbackPitch = playbackPitch,
        currentMediaItem = currentMediaItem,
        durationMs = durationMs,
        audioSessionId = audioSessionId
    )
}

