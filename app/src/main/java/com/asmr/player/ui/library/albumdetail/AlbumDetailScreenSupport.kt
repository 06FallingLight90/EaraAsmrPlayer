package com.asmr.player.ui.library.albumdetail

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import com.asmr.player.data.remote.auth.DlsiteAuthStore
import com.asmr.player.domain.model.Track
import com.asmr.player.domain.model.Album
import com.asmr.player.data.local.db.entities.TrackEntity
import com.asmr.player.data.remote.api.AsmrOneTrackNodeResponse
import com.asmr.player.data.lyrics.deriveLyricsRelativePathNoExt
import com.asmr.player.playback.MediaItemFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.map
import com.asmr.player.domain.model.RemoteSubtitleSource
import kotlin.math.roundToInt





internal enum class AlbumHeaderButtonGroupState {
    DownloadOnly,
    Save,
    Lossless
}

internal enum class OnlineDownloadSource {
    AsmrOne,
    DlsitePlay,
    DlsiteTrial
}

internal enum class IncrementalAlbumAction {
    Download,
    Save
}

internal data class PendingOnlineSaveSelection(
    val paths: Set<String>,
    val useDlsitePlayTree: Boolean
)

internal data class DlsitePlayAuthSnapshot(
    val canAuthenticate: Boolean,
    val fingerprint: Int
)

internal fun readDlsitePlayAuthSnapshot(authStore: DlsiteAuthStore): DlsitePlayAuthSnapshot {
    val cookie = authStore.getPlayCookie().trim()
    val expiresAt = authStore.getPlayCookieExpiresAtMs()
    return DlsitePlayAuthSnapshot(
        canAuthenticate = cookie.isNotBlank() && (expiresAt == null || expiresAt > System.currentTimeMillis()),
        fingerprint = 31 * cookie.hashCode() + (expiresAt?.hashCode() ?: 0)
    )
}

internal data class PreparedTrackPlayback(
    val tracks: List<Track>,
    val startTrack: Track,
    val onlineLyrics: Map<String, List<RemoteSubtitleSource>> = emptyMap()
)

internal data class PreparedMediaPlayback(
    val items: List<MediaItem>,
    val startIndex: Int
)

internal val AlbumDetailHeroContentGap = 8.dp
internal val AlbumDetailHeroTransitionHeight = 96.dp
internal val AlbumDetailHeroBlurRampHeight = 188.dp
internal val AlbumDetailHeroBlurRadius = 32.dp
internal val AlbumDetailScrolledContentFadeSpan = 10.dp
internal const val AlbumDetailInitialIntroDurationMs = 1200L
internal const val AlbumDetailHeroIntroDurationMs = 520
private const val AlbumDetailHeaderEnterDurationMs = 320
internal const val AlbumDetailHeroIntroStartScale = 1.35f
internal const val AlbumDetailHeroBlurRadiusMaxPx = 96f
internal const val AlbumDetailHeroBlurSampleMarginMultiplier = 3f
internal const val AlbumDetailHeroOvershootResistance = 0.30f
internal const val AlbumDetailHeroOvershootReleaseMultiplier = 0.72f
internal const val AlbumDetailHeroExpandOvershootScale = 0.16f
internal const val AlbumDetailHeroFlingVelocityMin = 2400f
internal const val AlbumDetailHeroFlingVelocityMax = 12_000f
internal const val AlbumDetailHeroFlingOvershootPortion = 0.24f
internal const val AlbumDetailHeroFlingOvershootMaxPortion = 0.14f
internal const val AlbumDetailHeroFlingApproachMillis = 560
internal const val AlbumDetailHeroFlingSettleMillis = 980
internal const val AlbumDetailCvRevealDelayMs = 0
internal const val AlbumDetailTagsRevealDelayMs = 90
internal const val AlbumHeaderActionStateTransitionMillis = 800
internal val AlbumDetailHorizontalPadding = 8.dp
internal val AlbumLandscapeArtworkStartPadding = 68.dp
internal val AlbumLandscapeArtworkTopPadding = 36.dp
private val AlbumLandscapeSpectrumTopPadding = 12.dp
internal val AlbumLandscapeHeaderEndPadding = 32.dp
internal val AlbumLandscapeHeaderLift = 52.dp
internal const val AlbumLandscapeCoverShadowStartAlpha = 0.16f
private val AlbumLandscapeArtworkContentGap = 12.dp
internal val AlbumLandscapeSurfaceBorderWidth = 0.5.dp
internal val AlbumLandscapeCollapsedArtworkShiftX = 24.dp
internal val AlbumLandscapeCollapsedArtworkShiftY = 18.dp
private const val AlbumLandscapeSpectrumArtworkOffsetFraction = 0.15f
private const val AlbumLandscapeSpectrumCollapseFollowFraction = 0.82f

internal fun shouldUseAlbumDetailLandscapeLayout(
    compactWidth: Boolean,
    screenWidthDp: Int,
    screenHeightDp: Int
): Boolean {
    return !compactWidth && screenWidthDp > screenHeightDp
}

internal fun albumLandscapeHeaderStart(artworkSize: Dp): Dp {
    return AlbumLandscapeArtworkStartPadding + artworkSize + AlbumLandscapeArtworkContentGap
}

internal fun albumLandscapeArtworkRight(artworkSize: Dp): Dp {
    return AlbumLandscapeArtworkStartPadding + artworkSize
}

internal fun albumLandscapeCollapseDistance(artworkSize: Dp): Dp {
    return (artworkSize * 0.28f).coerceAtMost(128.dp)
}

internal fun albumLandscapeCoverScale(collapsePx: Float, collapseMaxPx: Float): Float {
    val progress = albumLandscapeCollapseProgress(collapsePx, collapseMaxPx)
    return 1f - progress * 0.30f
}

internal fun albumLandscapeCollapseProgress(collapsePx: Float, collapseMaxPx: Float): Float {
    if (collapseMaxPx <= 0f) return 0f
    return (collapsePx / collapseMaxPx).coerceIn(0f, 1f)
}

internal fun albumLandscapePlaybackProgress(positionMs: Long, durationMs: Long): Float {
    if (durationMs <= 0L) return 0f
    return (positionMs.toDouble() / durationMs.toDouble()).toFloat().coerceIn(0f, 1f)
}

internal fun albumLandscapePulseSweepFraction(phase: Float): Float {
    val clamped = phase.coerceIn(0f, 1f)
    val remaining = 1f - clamped
    return 1f - remaining * remaining
}

internal fun albumLandscapeCoverShadowAlpha(imageAlpha: Float): Float {
    val normalizedImageAlpha = imageAlpha.coerceIn(0f, 1f)
    return (
        (normalizedImageAlpha - AlbumLandscapeCoverShadowStartAlpha) /
            (1f - AlbumLandscapeCoverShadowStartAlpha)
        ).coerceIn(0f, normalizedImageAlpha)
}

internal fun albumLandscapeDirectoryTop(
    headerHeight: Dp,
    headerLift: Dp
): Dp {
    return (headerHeight - headerLift + 4.dp).coerceAtLeast(0.dp)
}

internal fun albumLandscapePulseEnabled(
    isPlaying: Boolean,
    progress: Float
): Boolean {
    return isPlaying && progress > 0f
}

internal fun albumLandscapeSurfaceHeight(contentViewportHeight: Dp, artworkSize: Dp): Dp {
    return contentViewportHeight + albumLandscapeCollapseDistance(artworkSize)
}

internal fun albumLandscapePaneViewportHeightPx(
    surfaceHeightPx: Int,
    collapsePx: Float,
    collapseMaxPx: Float
): Int {
    if (surfaceHeightPx <= 0) return 0
    val safeCollapseMaxPx = collapseMaxPx.coerceAtLeast(0f)
    val hiddenBottomPx = (
        safeCollapseMaxPx - collapsePx.coerceIn(0f, safeCollapseMaxPx)
        ).roundToInt()
    return (surfaceHeightPx - hiddenBottomPx).coerceIn(0, surfaceHeightPx)
}

internal fun albumLandscapeSpectrumOffsetY(artworkSize: Dp): Dp {
    return AlbumLandscapeSpectrumTopPadding +
        artworkSize * AlbumLandscapeSpectrumArtworkOffsetFraction
}

internal fun albumLandscapeSpectrumTranslationY(collapsePx: Float): Float {
    return -collapsePx.coerceAtLeast(0f) * AlbumLandscapeSpectrumCollapseFollowFraction
}

internal fun Modifier.albumLandscapePaneViewportHeight(
    collapsePx: () -> Float,
    collapseMaxPx: Float
): Modifier = layout { measurable, constraints ->
    if (!constraints.hasBoundedHeight) {
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            placeable.placeRelative(0, 0)
        }
    } else {
        val viewportHeight = albumLandscapePaneViewportHeightPx(
            surfaceHeightPx = constraints.maxHeight,
            collapsePx = collapsePx(),
            collapseMaxPx = collapseMaxPx
        ).coerceAtLeast(constraints.minHeight)
        val placeable = measurable.measure(
            constraints.copy(
                minHeight = viewportHeight,
                maxHeight = viewportHeight
            )
        )
        layout(placeable.width, viewportHeight) {
            placeable.placeRelative(0, 0)
        }
    }
}

internal val AlbumDetailHeroBounceBackSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessLow
)

internal val AlbumHeaderEnterTweenSpec = tween<Float>(
    durationMillis = AlbumDetailHeaderEnterDurationMs,
    easing = FastOutLinearInEasing
)

internal val AlbumHeaderExpandTweenSpec = tween<IntSize>(
    durationMillis = 320,
    easing = FastOutLinearInEasing
)

private val DlsiteSectionResizeTweenSpec = tween<IntSize>(
    durationMillis = 280,
    easing = FastOutSlowInEasing
)

internal class AlbumDetailHeroMotionState {
    var collapsePx by mutableFloatStateOf(0f)
    var visualOvershootPx by mutableFloatStateOf(0f)
    var visualOvershootJob: Job? = null

    fun cancelVisualOvershootAnimation() {
        visualOvershootJob?.cancel()
        visualOvershootJob = null
    }
}

internal fun dlsiteSectionRevealModifier(
    modifier: Modifier = Modifier,
    enabled: Boolean = true
): Modifier {
    return if (enabled) {
        modifier.animateContentSize(animationSpec = DlsiteSectionResizeTweenSpec)
    } else {
        modifier
    }
}

internal fun shouldAnimateAlbumHeaderMetaReveal(
    presentInitially: Boolean,
    hasContent: Boolean,
    animationsEnabled: Boolean
): Boolean {
    return animationsEnabled && !presentInitially && hasContent
}

internal data class AlbumDetailOnlineLoadPlan(
    val loadDlsite: Boolean = false,
    val loadAsmrOne: Boolean = false,
    val loadDlsitePlay: Boolean = false
)

internal fun albumDetailOnlineLoadPlan(
    selectedTab: Int,
    hasResolvedInitialDlsiteTarget: Boolean,
    isInitialRouteReady: Boolean,
    hasValidLocalRj: Boolean = false,
    hasResolvedAsmrOneContent: Boolean = false,
    hasAsmrOneTree: Boolean = false,
    hasDlsitePlayCredentials: Boolean = false
): AlbumDetailOnlineLoadPlan {
    if (!isInitialRouteReady) return AlbumDetailOnlineLoadPlan()
    return when (selectedTab) {
        0 -> AlbumDetailOnlineLoadPlan(
            loadAsmrOne = hasValidLocalRj,
            loadDlsitePlay = hasValidLocalRj &&
                hasResolvedAsmrOneContent &&
                !hasAsmrOneTree &&
                hasDlsitePlayCredentials
        )
        1 -> AlbumDetailOnlineLoadPlan(
            loadDlsite = true,
            loadAsmrOne = true
        )
        2 -> AlbumDetailOnlineLoadPlan(
            loadDlsite = true,
            loadDlsitePlay = hasResolvedInitialDlsiteTarget
        )
        else -> AlbumDetailOnlineLoadPlan()
    }
}

internal fun canUseAsmrOneOnlineTreeActions(
    selectedTab: Int,
    hasAsmrOneTree: Boolean
): Boolean {
    return selectedTab == 1 && hasAsmrOneTree
}

internal fun asmrOneDirectoryTreeStateKey(
    currentRj: String,
    baseRj: String
): String {
    val targetRj = currentRj.trim().uppercase()
        .ifBlank { baseRj.trim().uppercase() }
    return "tree:asmrOne:$targetRj"
}

internal fun albumHeaderDownloadEnabled(
    selectedTab: Int,
    hasAsmrOneTree: Boolean,
    hasDlsitePlayTree: Boolean,
    hasResolvedInitialDlsiteTarget: Boolean,
    hasValidLocalRj: Boolean = false,
    hasDlsitePlayCredentials: Boolean = true
): Boolean {
    return when (selectedTab) {
        0 -> hasValidLocalRj && (hasAsmrOneTree || (hasDlsitePlayCredentials && hasDlsitePlayTree))
        1 -> canUseAsmrOneOnlineTreeActions(selectedTab, hasAsmrOneTree)
        2 -> hasResolvedInitialDlsiteTarget && hasDlsitePlayTree
        else -> false
    }
}

@OptIn(ExperimentalLayoutApi::class)
internal fun isVideoPreviewUrl(url: String): Boolean {
    val u = url.substringBefore('#').substringBefore('?').lowercase()
    return u.endsWith(".mp4") || u.endsWith(".mkv") || u.endsWith(".webm") || u.endsWith(".m3u8")
}

internal data class PlaylistAddTarget(
    val mediaId: String,
    val uri: String,
    val title: String,
    val artist: String,
    val artworkUri: String,
    val albumTitle: String = "",
    val albumId: Long = 0L,
    val trackId: Long = 0L,
    val rjCode: String = "",
    val albumWorkId: String = "",
    val trackGroup: String = "",
    val lyricsRelativePathNoExt: String = "",
    val remoteSubtitleSources: List<RemoteSubtitleSource> = emptyList(),
    val mimeType: String? = null,
    val isVideo: Boolean = false
) {
    fun toMediaItem(): MediaItem {
        return MediaItemFactory.fromDetails(
            mediaId = mediaId,
            uri = uri,
            title = title,
            artist = artist,
            albumTitle = albumTitle,
            artworkUri = artworkUri,
            albumId = albumId,
            trackId = trackId,
            rjCode = rjCode,
            albumWorkId = albumWorkId,
            trackGroup = trackGroup,
            lyricsRelativePathNoExt = lyricsRelativePathNoExt,
            remoteSubtitleSources = remoteSubtitleSources,
            mimeType = mimeType,
            isVideo = isVideo
        )
    }

    companion object {
        fun fromTrack(album: Album, track: Track): PlaylistAddTarget {
            val rj = album.rjCode.ifBlank { album.workId }
            val artist = albumArtistLabel(album).ifBlank { rj }
            val artwork = albumArtworkLabel(album)
            val title = track.title.ifBlank { track.path.substringAfterLast('/').substringAfterLast('\\') }
            return PlaylistAddTarget(
                mediaId = track.path,
                uri = track.path,
                title = title,
                artist = artist.orEmpty(),
                artworkUri = artwork,
                albumTitle = album.title,
                albumId = album.id,
                trackId = track.id,
                rjCode = rj,
                albumWorkId = album.workId,
                trackGroup = track.group,
                lyricsRelativePathNoExt = deriveLyricsRelativePathNoExt(track.path, album.getAllLocalPaths())
            )
        }

        fun fromVideo(
            album: Album,
            title: String,
            uriOrPath: String
        ): PlaylistAddTarget? {
            val trimmed = uriOrPath.trim()
            if (trimmed.isBlank()) return null
            return PlaylistAddTarget(
                mediaId = trimmed,
                uri = trimmed,
                title = title.ifBlank { trimmed.substringAfterLast('/').substringAfterLast('\\') },
                artist = albumArtistLabel(album),
                artworkUri = albumArtworkLabel(album),
                albumTitle = album.title,
                albumId = album.id,
                rjCode = album.rjCode.ifBlank { album.workId },
                albumWorkId = album.workId,
                mimeType = MediaItemFactory.guessMimeType(trimmed),
                isVideo = true
            )
        }

        fun fromAsmrOne(album: Album, tree: List<AsmrOneTrackNodeResponse>, relativePath: String): PlaylistAddTarget? {
            val leaf = flattenAsmrOneTracksForUi(tree).firstOrNull { it.relativePath == relativePath } ?: return null
            return fromTrack(album, leaf.toTrack())
        }
    }
}

