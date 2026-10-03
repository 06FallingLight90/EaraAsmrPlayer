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
internal fun PlaybackProgressContent(
    viewModel: PlayerViewModel,
    isVideo: Boolean,
    content: @Composable (NowPlayingProgressState) -> Unit
) {
    val initialProgressState = remember(viewModel, isVideo) {
        val snapshot = viewModel.playback.value
        val positionMs = if (isVideo) {
            (snapshot.positionMs / VideoProgressUiTickMs) * VideoProgressUiTickMs
        } else {
            snapshot.positionMs
        }
        NowPlayingProgressState(positionMs, snapshot.durationMs)
    }
    val progressState by remember(viewModel, isVideo) {
        viewModel.playback
            .map { snapshot ->
                val positionMs = if (isVideo) {
                    (snapshot.positionMs / VideoProgressUiTickMs) * VideoProgressUiTickMs
                } else {
                    snapshot.positionMs
                }
                NowPlayingProgressState(positionMs, snapshot.durationMs)
            }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = initialProgressState)

    content(progressState)
}

internal fun Modifier.fitVideoPreviewAspectRatio(
    aspectRatio: Float,
    maxWidth: Dp = Dp.Unspecified
): Modifier {
    val boundedModifier = if (maxWidth != Dp.Unspecified) {
        this.widthIn(max = maxWidth)
    } else {
        this
    }
    return boundedModifier.layout { measurable, constraints ->
        val ratio = aspectRatio
            .takeIf { it.isFinite() && it > 0f }
            ?.coerceIn(0.5f, 3f)
            ?: (16f / 9f)
        val hasBoundedWidth = constraints.maxWidth != androidx.compose.ui.unit.Constraints.Infinity
        val hasBoundedHeight = constraints.maxHeight != androidx.compose.ui.unit.Constraints.Infinity
        if (!hasBoundedWidth && !hasBoundedHeight) {
            val placeable = measurable.measure(constraints)
            return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }

        val availableWidth = when {
            hasBoundedWidth -> constraints.maxWidth
            hasBoundedHeight -> (constraints.maxHeight * ratio).roundToInt()
            else -> constraints.minWidth
        }.coerceAtLeast(1)
        val availableHeight = when {
            hasBoundedHeight -> constraints.maxHeight
            hasBoundedWidth -> (constraints.maxWidth / ratio).roundToInt()
            else -> constraints.minHeight
        }.coerceAtLeast(1)

        var width = availableWidth
        var height = (width / ratio).roundToInt().coerceAtLeast(1)
        if (height > availableHeight) {
            height = availableHeight
            width = (height * ratio).roundToInt().coerceAtLeast(1)
        }

        val placeable = measurable.measure(
            androidx.compose.ui.unit.Constraints.fixed(
                width = width.coerceAtMost(availableWidth),
                height = height.coerceAtMost(availableHeight)
            )
        )
        layout(placeable.width, placeable.height) {
            placeable.place(0, 0)
        }
    }
}

@Composable
internal fun rememberArtworkAspectRatio(artworkModel: Any?): Float {
    val context = LocalContext.current.applicationContext
    val manager = remember(context) {
        EntryPointAccessors.fromApplication(context, ImageCacheEntryPoint::class.java)
            .imageCacheManager()
    }
    var aspectRatio by remember(artworkModel) { mutableFloatStateOf(1f) }

    LaunchedEffect(artworkModel, manager) {
        val model = artworkModel ?: run {
            aspectRatio = 1f
            return@LaunchedEffect
        }
        runCatching {
            manager.loadImage(model = model, size = null, cachePolicy = CachePolicy.DEFAULT)
        }.onSuccess { image ->
            val width = image.width
            val height = image.height
            if (width > 0 && height > 0) {
                aspectRatio = (width.toFloat() / height.toFloat()).coerceIn(0.25f, 4f)
            }
        }
    }

    return aspectRatio
}

internal fun AnimatedContentTransitionScope<NowPlayingSurfaceMode>.nowPlayingSurfaceTransform(): ContentTransform {
    val enter = fadeIn(
        animationSpec = tween(
            durationMillis = NowPlayingMotionSpec.PlayerForegroundEnterDurationMs,
            easing = LinearOutSlowInEasing
        )
    ) + slideInVertically(
        initialOffsetY = {
            (it * NowPlayingMotionSpec.PlayerForegroundFloatOffsetFraction).roundToInt()
        },
        animationSpec = tween(
            durationMillis = NowPlayingMotionSpec.PlayerForegroundEnterDurationMs,
            easing = LinearOutSlowInEasing
        )
    ) + scaleIn(
        initialScale = NowPlayingMotionSpec.PlayerForegroundInitialScale,
        animationSpec = tween(
            durationMillis = NowPlayingMotionSpec.PlayerForegroundEnterDurationMs,
            easing = LinearOutSlowInEasing
        )
    )
    val exit = fadeOut(
        animationSpec = tween(
            durationMillis = NowPlayingMotionSpec.PlayerForegroundExitDurationMs,
            easing = FastOutLinearInEasing
        )
    ) + slideOutVertically(
        targetOffsetY = {
            (it * NowPlayingMotionSpec.PlayerForegroundSinkOffsetFraction).roundToInt()
        },
        animationSpec = tween(
            durationMillis = NowPlayingMotionSpec.PlayerForegroundExitDurationMs,
            easing = FastOutLinearInEasing
        )
    ) + scaleOut(
        targetScale = NowPlayingMotionSpec.PlayerForegroundTargetScale,
        animationSpec = tween(
            durationMillis = NowPlayingMotionSpec.PlayerForegroundExitDurationMs,
            easing = FastOutLinearInEasing
        )
    )
    return enter togetherWith exit using SizeTransform(clip = false)
}

internal sealed interface ListenTogetherAudiencePresentation {
    data class Status(val text: String) : ListenTogetherAudiencePresentation

    data class Audience(val companionCount: Int) : ListenTogetherAudiencePresentation
}

