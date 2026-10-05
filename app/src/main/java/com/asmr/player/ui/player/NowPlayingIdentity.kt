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

@Composable
internal fun LandscapePlayerIdentity(
    title: String,
    artistMeta: NowPlayingArtistMeta,
    listenTogetherState: ListenTogetherUiState,
    accentColor: Color,
    pageEntranceSettled: Boolean,
    compactHeight: Boolean,
    tabletLayout: Boolean,
    onArtistBottomChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val typography = remember(compactHeight, tabletLayout) {
        nowPlayingLandscapeIdentityTypography(
            compactHeight = compactHeight,
            tabletLayout = tabletLayout
        )
    }
    val artistSummary = remember(artistMeta) { formatClassicArtistSummary(artistMeta) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds(),
        verticalArrangement = Arrangement.spacedBy(if (compactHeight) 1.dp else 3.dp)
    ) {
        ListenTogetherAudienceLine(
            state = listenTogetherState,
            modifier = Modifier.fillMaxWidth(),
            accentColor = accentColor,
            textColor = colorScheme.textTertiary,
            contentAlignment = Alignment.CenterStart,
            pageEntranceSettled = pageEntranceSettled
        )
        Text(
            text = title.ifBlank { "未播放" },
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = typography.titleFontSizeSp.sp,
                lineHeight = typography.titleLineHeightSp.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = colorScheme.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = artistSummary,
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    onArtistBottomChanged(coordinates.boundsInRoot().bottom)
                },
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = typography.artistInfoFontSizeSp.sp,
                lineHeight = typography.artistInfoLineHeightSp.sp,
                fontWeight = FontWeight.Medium
            ),
            color = colorScheme.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

internal data class NowPlayingLandscapeIdentityTypography(
    val titleFontSizeSp: Int,
    val titleLineHeightSp: Int,
    val artistInfoFontSizeSp: Int,
    val artistInfoLineHeightSp: Int
)

internal fun nowPlayingLandscapeIdentityTypography(
    compactHeight: Boolean,
    tabletLayout: Boolean
): NowPlayingLandscapeIdentityTypography {
    return when {
        tabletLayout -> NowPlayingLandscapeIdentityTypography(
            titleFontSizeSp = 20,
            titleLineHeightSp = 24,
            artistInfoFontSizeSp = 13,
            artistInfoLineHeightSp = 17
        )
        compactHeight -> NowPlayingLandscapeIdentityTypography(
            titleFontSizeSp = 16,
            titleLineHeightSp = 18,
            artistInfoFontSizeSp = 11,
            artistInfoLineHeightSp = 13
        )
        else -> NowPlayingLandscapeIdentityTypography(
            titleFontSizeSp = 18,
            titleLineHeightSp = 21,
            artistInfoFontSizeSp = 12,
            artistInfoLineHeightSp = 15
        )
    }
}

@Composable
internal fun ArtistWithListenTogetherInfo(
    artist: String,
    listenTogetherState: ListenTogetherUiState,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    badgeAlignment: Alignment = Alignment.TopStart,
    textAlignment: Alignment = Alignment.CenterStart,
    accentColor: Color = AsmrTheme.colorScheme.primary,
    pageEntranceSettled: Boolean = true
) {
    Box(modifier = modifier.fillMaxWidth()) {
        ListenTogetherAudienceLine(
            state = listenTogetherState,
            modifier = Modifier
                .align(badgeAlignment)
                .offset(y = (-18).dp),
            accentColor = accentColor,
            pageEntranceSettled = pageEntranceSettled
        )
        Text(
            text = artist,
            modifier = Modifier.align(textAlignment),
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign
        )
    }
}

internal data class NowPlayingArtistMeta(
    val circle: String,
    val cvNames: List<String>
)

internal fun parseNowPlayingArtistMeta(artist: String): NowPlayingArtistMeta {
    val normalized = artist.trim()
    if (normalized.isBlank()) return NowPlayingArtistMeta(circle = "", cvNames = emptyList())

    val parts = normalized.split(" / ", limit = 2).map { it.trim() }
    val circle = parts.takeIf { it.size == 2 }?.first().orEmpty()
    val cvText = if (parts.size == 2) parts[1] else normalized
    val cvNames = cvText
        .split(',', '，', '、', '/', '\n', ';', '；', '|')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
    return NowPlayingArtistMeta(circle = circle, cvNames = cvNames)
}

internal fun formatExpandedArtistSummary(artistMeta: NowPlayingArtistMeta): String {
    val cvSummary = artistMeta.cvNames.joinToString("、")
    return listOf(artistMeta.circle, cvSummary)
        .filter { it.isNotBlank() }
        .joinToString(" | ")
}

internal fun formatClassicArtistSummary(artistMeta: NowPlayingArtistMeta): String {
    val circle = artistMeta.circle.takeIf { it.isNotBlank() }?.let { "社团 $it" }
    val cv = artistMeta.cvNames
        .joinToString("、")
        .takeIf { it.isNotBlank() }
        ?.let { "CV $it" }
    return listOfNotNull(circle, cv).joinToString(" / ")
}

@Composable
internal fun ClassicPlayerIdentity(
    title: String,
    artistMeta: NowPlayingArtistMeta,
    compactLayout: Boolean,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val textShadow = remember(colorScheme.isDark) {
        if (colorScheme.isDark) {
            Shadow(
                color = Color.Black.copy(alpha = 0.4f),
                offset = Offset(0f, 1f),
                blurRadius = 2f
            )
        } else {
            Shadow(
                color = Color.Black.copy(alpha = 0.12f),
                offset = Offset(0f, 0.5f),
                blurRadius = 1.5f
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .padding(horizontal = if (compactLayout) 20.dp else 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            space = if (compactLayout) 4.dp else 6.dp,
            alignment = Alignment.CenterVertically
        )
    ) {
        Text(
            text = title.ifBlank { "未播放" },
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = if (compactLayout) 13.sp else 18.sp,
                lineHeight = if (compactLayout) 15.sp else 20.sp,
                shadow = textShadow
            ),
            color = colorScheme.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        val artistSummary = remember(artistMeta) { formatClassicArtistSummary(artistMeta) }
        if (artistSummary.isNotBlank()) {
            Text(
                text = artistSummary,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = if (compactLayout) 11.sp else 13.sp,
                    lineHeight = if (compactLayout) 14.sp else 17.sp,
                    fontWeight = FontWeight.Medium,
                    shadow = textShadow
                ),
                color = colorScheme.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
internal fun ExpandedPlayerIdentityOverlay(
    title: String,
    artistMeta: NowPlayingArtistMeta,
    listenTogetherState: ListenTogetherUiState,
    pageEntranceSettled: Boolean,
    modifier: Modifier = Modifier
) {
    val artistSummary = remember(artistMeta) { formatExpandedArtistSummary(artistMeta) }
    val overlayShadow = remember {
        Shadow(
            color = Color.Black.copy(alpha = 0.72f),
            offset = Offset(0f, 1f),
            blurRadius = 4f
        )
    }
    val scrim = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.Black.copy(alpha = 0.44f)
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(scrim)
            .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 12.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier.widthIn(max = NowPlayingPortraitIdentityMaxWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            ListenTogetherAudienceLine(
                state = listenTogetherState,
                modifier = Modifier.fillMaxWidth(),
                accentColor = Color.White.copy(alpha = 0.68f),
                textColor = Color.White.copy(alpha = 0.68f),
                textShadow = overlayShadow,
                pageEntranceSettled = pageEntranceSettled
            )
            Text(
                text = title.ifBlank { "未播放" },
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 18.sp,
                    shadow = overlayShadow
                ),
                color = Color.White.copy(alpha = 0.86f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (artistSummary.isNotBlank()) {
                Text(
                    text = artistSummary,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall.copy(
                        lineHeight = 16.sp,
                        shadow = overlayShadow
                    ),
                    color = Color.White.copy(alpha = 0.66f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
internal fun NowPlayingHomeLayoutSwipeHint(
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "nowPlayingHomeLayoutSwipeHint")
    val waveProgress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "nowPlayingHomeLayoutSwipeHintProgress"
    )
    val textShadow = remember {
        Shadow(
            color = Color.Black.copy(alpha = 0.72f),
            offset = Offset(0f, 1.2f),
            blurRadius = 4f
        )
    }

    Column(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Canvas(
            modifier = Modifier
                .width(44.dp)
                .height(24.dp)
        ) {
            repeat(4) { index ->
                val phase = (waveProgress.value + index * 0.25f) % 1f
                val edgeFade = when {
                    phase < 0.22f -> phase / 0.22f
                    phase > 0.78f -> (1f - phase) / 0.22f
                    else -> 1f
                }.coerceIn(0f, 1f)
                val pulse = edgeFade * edgeFade * (3f - 2f * edgeFade)
                val centerX = size.width / 2f
                val centerY = size.height * (0.88f - phase * 0.70f)
                val halfWidth = size.width * 0.15f
                val halfHeight = size.height * 0.14f
                val color = Color.White.copy(alpha = pulse * 0.66f)
                val shadowColor = Color.Black.copy(alpha = pulse * 0.24f)
                val strokeWidth = 1.45.dp.toPx()
                drawLine(
                    color = shadowColor,
                    start = Offset(centerX - halfWidth, centerY + halfHeight + 1.2f),
                    end = Offset(centerX, centerY - halfHeight + 1.2f),
                    strokeWidth = strokeWidth + 1.2f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = shadowColor,
                    start = Offset(centerX + halfWidth, centerY + halfHeight + 1.2f),
                    end = Offset(centerX, centerY - halfHeight + 1.2f),
                    strokeWidth = strokeWidth + 1.2f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = color,
                    start = Offset(centerX - halfWidth, centerY + halfHeight),
                    end = Offset(centerX, centerY - halfHeight),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = color,
                    start = Offset(centerX + halfWidth, centerY + halfHeight),
                    end = Offset(centerX, centerY - halfHeight),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }
        Text(
            text = "上滑切换封面排布",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                shadow = textShadow
            ),
            color = Color.White.copy(alpha = 0.86f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
