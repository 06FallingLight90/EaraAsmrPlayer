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

internal fun resolveListenTogetherAudiencePresentation(
    state: ListenTogetherUiState
): ListenTogetherAudiencePresentation? = when {
    !state.available && state.status == ListenTogetherStatus.Unsupported ->
        ListenTogetherAudiencePresentation.Status("当前音频无法参与一起听")
    state.listenerCount != null ->
        ListenTogetherAudiencePresentation.Audience(
            companionCount = (state.listenerCount - 1).coerceAtLeast(0)
        )
    state.available ->
        ListenTogetherAudiencePresentation.Audience(companionCount = 0)
    else ->
        null
}

internal fun <S> AnimatedContentTransitionScope<S>.listenTogetherInlineTransform(): ContentTransform {
    val enter = fadeIn(
        animationSpec = tween(
            durationMillis = NowPlayingMotionSpec.PlayerForegroundEnterDurationMs,
            easing = LinearOutSlowInEasing
        )
    ) + slideInVertically(
        initialOffsetY = { fullHeight -> fullHeight / 3 },
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
        targetOffsetY = { fullHeight -> -(fullHeight / 3) },
        animationSpec = tween(
            durationMillis = NowPlayingMotionSpec.PlayerForegroundExitDurationMs,
            easing = FastOutLinearInEasing
        )
    )
    return enter togetherWith exit using SizeTransform(clip = false)
}

internal fun AnimatedContentTransitionScope<Int>.listenTogetherCounterTransform(): ContentTransform {
    val direction = if (targetState >= initialState) 1 else -1
    val enter = fadeIn(
        animationSpec = tween(durationMillis = 220, easing = LinearOutSlowInEasing)
    ) + slideInVertically(
        initialOffsetY = { fullHeight -> direction * fullHeight },
        animationSpec = tween(durationMillis = 220, easing = LinearOutSlowInEasing)
    )
    val exit = fadeOut(
        animationSpec = tween(durationMillis = 140, easing = FastOutLinearInEasing)
    ) + slideOutVertically(
        targetOffsetY = { fullHeight -> -direction * fullHeight },
        animationSpec = tween(durationMillis = 140, easing = FastOutLinearInEasing)
    )
    return enter togetherWith exit using SizeTransform(clip = false)
}

@Composable
internal fun ListenTogetherAudienceCountText(
    companionCount: Int,
    color: Color,
    textShadow: Shadow?,
    modifier: Modifier = Modifier
) {
    val textStyle = MaterialTheme.typography.labelSmall.copy(shadow = textShadow)
    val displayCount = companionCount.coerceAtLeast(0)

    AnimatedContent(
        targetState = displayCount > 0,
        transitionSpec = { listenTogetherInlineTransform() },
        label = "listenTogetherAudienceMode"
    ) { hasCompanions ->
        if (hasCompanions) {
            Row(
                modifier = modifier,
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedContent(
                    targetState = displayCount,
                    transitionSpec = { listenTogetherCounterTransform() },
                    label = "listenTogetherAudienceCounter"
                ) { value ->
                    Text(
                        text = value.toString(),
                        style = textStyle.copy(fontWeight = FontWeight.SemiBold),
                        color = color,
                        maxLines = 1
                    )
                }
                Text(
                    text = " 人正在和你一起听",
                    style = textStyle,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Text(
                text = "孤独赏鉴中",
                modifier = modifier,
                style = textStyle,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun ListenTogetherAudienceLine(
    state: ListenTogetherUiState,
    modifier: Modifier = Modifier,
    accentColor: Color = AsmrTheme.colorScheme.primary,
    textColor: Color = AsmrTheme.colorScheme.textTertiary,
    textShadow: Shadow? = null,
    contentAlignment: Alignment = Alignment.Center,
    pageEntranceSettled: Boolean = true
) {
    val presentation = resolveListenTogetherAudiencePresentation(state)

    val displayTarget = if (pageEntranceSettled) presentation else null

    Box(
        modifier = modifier.height(18.dp),
        contentAlignment = contentAlignment
    ) {
        Row(
            modifier = Modifier.animateContentSize(
                animationSpec = tween(
                    durationMillis = NowPlayingMotionSpec.PlayerForegroundEnterDurationMs,
                    easing = LinearOutSlowInEasing
                )
            ),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_users_round),
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = accentColor.copy(alpha = 0.82f)
            )
            AnimatedContent(
                targetState = displayTarget,
                transitionSpec = {
                    fadeIn(tween(300, easing = LinearOutSlowInEasing)) togetherWith
                        fadeOut(tween(200, easing = FastOutLinearInEasing))
                },
                label = "listenTogetherAudienceText"
            ) { target ->
                when (target) {
                    is ListenTogetherAudiencePresentation.Status -> Text(
                        text = target.text,
                        style = MaterialTheme.typography.labelSmall.copy(shadow = textShadow),
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    is ListenTogetherAudiencePresentation.Audience -> ListenTogetherAudienceCountText(
                        companionCount = target.companionCount,
                        color = textColor,
                        textShadow = textShadow
                    )
                    null -> {}
                }
            }
        }
    }
}

