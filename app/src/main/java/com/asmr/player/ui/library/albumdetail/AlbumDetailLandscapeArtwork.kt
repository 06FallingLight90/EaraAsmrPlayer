package com.asmr.player.ui.library

import android.graphics.PathMeasure as AndroidPathMeasure

import com.asmr.player.translation.translatedPageText
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asmr.player.data.remote.scraper.DlsiteRecommendedWork
import com.asmr.player.domain.model.Album
import com.asmr.player.ui.common.audio.HorizontalStereoSpectrum
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import com.asmr.player.ui.common.cover.DiscPlaceholder
import com.asmr.player.ui.common.cover.AsmrAsyncImage
import com.asmr.player.ui.common.cover.AsmrImageLoadingPlaceholder
import com.asmr.player.ui.common.cover.NoImageLoadingIndicator
import com.asmr.player.ui.common.list.LocalBottomOverlayPadding
import com.asmr.player.ui.player.PlayerViewModel
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.util.MessageManager





internal data class AlbumHeroBlurSource(
    val painter: BitmapPainter,
    val alpha: State<Float>
)

class AlbumHeroBlurLayerCache(
    val layer: GraphicsLayer
) {
    private var contentKey: Any? = null
    private var layerSize: IntSize = IntSize.Zero
    private var fullHeroSize: IntSize = IntSize.Zero

    fun matches(
        contentKey: Any?,
        layerSize: IntSize,
        fullHeroSize: IntSize
    ): Boolean =
        this.contentKey == contentKey &&
            this.layerSize == layerSize &&
            this.fullHeroSize == fullHeroSize

    fun markRecorded(
        contentKey: Any?,
        layerSize: IntSize,
        fullHeroSize: IntSize
    ) {
        this.contentKey = contentKey
        this.layerSize = layerSize
        this.fullHeroSize = fullHeroSize
    }
}

@Composable
internal fun rememberAlbumLandscapeContentShape(
    waveDepth: Dp
): Shape {
    val density = LocalDensity.current
    val waveDepthPx = with(density) { waveDepth.toPx() }
    return remember(waveDepthPx) {
        GenericShape { size, _ ->
            val depth = waveDepthPx.coerceIn(0f, size.height * 0.40f)
            addAlbumLandscapeTopCurve(size = size, depth = depth)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
    }
}

private fun Path.addAlbumLandscapeTopCurve(size: Size, depth: Float) {
    addCatmullRomSpline(
        points = listOf(
            Offset(0f, depth * 1.06f),
            Offset(size.width * 0.14f, depth * 1.08f),
            Offset(size.width * 0.25f, depth * 0.98f),
            Offset(size.width * 0.34f, depth * 0.62f),
            Offset(size.width * 0.44f, depth * 0.28f),
            Offset(size.width * 0.60f, depth * 0.12f),
            Offset(size.width * 0.80f, depth * 0.13f),
            Offset(size.width, depth * 0.18f)
        ),
        smoothness = 0.84f
    )
}

@Composable
internal fun AlbumLandscapeCurvePlaybackProgress(
    waveDepth: Dp,
    modifier: Modifier = Modifier,
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val playbackIndicator by remember(playerViewModel) {
        playerViewModel.playback
            .map { playback ->
                AlbumLandscapePlaybackIndicator(
                    progress = albumLandscapePlaybackProgress(
                        playback.positionMs,
                        playback.durationMs
                    ),
                    isPlaying = playback.isPlaying
                )
            }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = AlbumLandscapePlaybackIndicator())
    val progressState = rememberUpdatedState(playbackIndicator.progress)
    val pulseEnabled = albumLandscapePulseEnabled(
        isPlaying = playbackIndicator.isPlaying,
        progress = playbackIndicator.progress
    )
    val pulseEnabledState = rememberUpdatedState(pulseEnabled)
    val pulsePhase = remember { Animatable(0f) }
    LaunchedEffect(pulseEnabled) {
        if (!pulseEnabled) {
            pulsePhase.snapTo(0f)
            return@LaunchedEffect
        }
        while (true) {
            pulsePhase.snapTo(0f)
            pulsePhase.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 3_200, easing = LinearEasing)
            )
            delay(260L)
        }
    }
    val colorScheme = AsmrTheme.colorScheme
    val trackColor = colorScheme.primary.copy(
        alpha = if (colorScheme.isDark) 0.24f else 0.18f
    )
    val activeColor = colorScheme.primaryStrong.copy(alpha = 0.92f)
    val pulseColor = colorScheme.primaryStrong

    Box(
        modifier = modifier.drawWithCache {
            val depth = waveDepth.toPx().coerceIn(0f, size.height * 0.40f)
            val curvePath = Path().apply {
                addAlbumLandscapeTopCurve(size = size, depth = depth)
            }
            val pathMeasure = AndroidPathMeasure(curvePath.asAndroidPath(), false)
            val activePath = Path()
            val pulsePath = Path()
            val pulsePosition = FloatArray(2)
            val trackStroke = Stroke(width = 0.75.dp.toPx(), cap = StrokeCap.Round)
            val activeStroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
            val pulseGlowStroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
            val pulseCoreStroke = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
            val maximumPulseLength = 58.dp.toPx()

            onDrawBehind {
                drawPath(path = curvePath, color = trackColor, style = trackStroke)
                val fraction = progressState.value.coerceIn(0f, 1f)
                if (fraction > 0f && pathMeasure.length > 0f) {
                    activePath.reset()
                    pathMeasure.getSegment(
                        0f,
                        pathMeasure.length * fraction,
                        activePath.asAndroidPath(),
                        true
                    )
                    drawPath(path = activePath, color = activeColor, style = activeStroke)

                    if (pulseEnabledState.value) {
                        val activeLength = pathMeasure.length * fraction
                        val rawPulsePhase = pulsePhase.value.coerceIn(0f, 1f)
                        val pulseEnd = activeLength *
                            albumLandscapePulseSweepFraction(rawPulsePhase)
                        val pulseLength = minOf(maximumPulseLength, activeLength * 0.24f)
                        val pulseStart = (pulseEnd - pulseLength).coerceAtLeast(0f)
                        val pulseEnvelope = kotlin.math.sin(Math.PI * rawPulsePhase)
                            .toFloat()
                            .coerceIn(0f, 1f)
                        if (pulseEnvelope > 0f && pulseEnd > pulseStart) {
                            pulsePath.reset()
                            pathMeasure.getSegment(
                                pulseStart,
                                pulseEnd,
                                pulsePath.asAndroidPath(),
                                true
                            )
                            drawPath(
                                path = pulsePath,
                                color = pulseColor.copy(alpha = 0.20f * pulseEnvelope),
                                style = pulseGlowStroke
                            )
                            drawPath(
                                path = pulsePath,
                                color = pulseColor.copy(alpha = 0.96f * pulseEnvelope),
                                style = pulseCoreStroke
                            )
                            if (pathMeasure.getPosTan(pulseEnd, pulsePosition, null)) {
                                drawCircle(
                                    color = pulseColor.copy(alpha = 0.88f * pulseEnvelope),
                                    radius = 2.8.dp.toPx(),
                                    center = Offset(pulsePosition[0], pulsePosition[1])
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

private data class AlbumLandscapePlaybackIndicator(
    val progress: Float = 0f,
    val isPlaying: Boolean = false
)

private fun Path.addCatmullRomSpline(
    points: List<Offset>,
    smoothness: Float
) {
    if (points.isEmpty()) return
    moveTo(points.first().x, points.first().y)
    if (points.size == 1) return

    val tangentScale = smoothness.coerceIn(0f, 1f) / 6f
    for (index in 0 until points.lastIndex) {
        val p0 = points[(index - 1).coerceAtLeast(0)]
        val p1 = points[index]
        val p2 = points[index + 1]
        val p3 = points[(index + 2).coerceAtMost(points.lastIndex)]
        cubicTo(
            p1.x + (p2.x - p0.x) * tangentScale,
            p1.y + (p2.y - p0.y) * tangentScale,
            p2.x - (p3.x - p1.x) * tangentScale,
            p2.y - (p3.y - p1.y) * tangentScale,
            p2.x,
            p2.y
        )
    }
}

private val AlbumLandscapeArtworkRibbonShape = GenericShape { size, _ ->
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width, size.height * 0.88f)
    cubicTo(
        size.width * 0.82f,
        size.height * 0.98f,
        size.width * 0.66f,
        size.height * 0.84f,
        size.width * 0.48f,
        size.height * 0.94f
    )
    cubicTo(
        size.width * 0.30f,
        size.height,
        size.width * 0.14f,
        size.height * 0.88f,
        0f,
        size.height * 0.92f
    )
    close()
}

@Composable
private fun rememberAlbumLandscapeArtworkRibbonCoreShape(edgeInset: Dp): Shape {
    val density = LocalDensity.current
    val edgeInsetPx = with(density) { edgeInset.toPx() }
    return remember(edgeInsetPx) {
        GenericShape { size, _ ->
            val inset = edgeInsetPx.coerceAtMost(size.height * 0.08f)
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height * 0.88f - inset)
            cubicTo(
                size.width * 0.82f,
                size.height * 0.98f - inset,
                size.width * 0.66f,
                size.height * 0.84f - inset,
                size.width * 0.48f,
                size.height * 0.94f - inset
            )
            cubicTo(
                size.width * 0.30f,
                size.height - inset,
                size.width * 0.14f,
                size.height * 0.88f - inset,
                0f,
                size.height * 0.92f - inset
            )
            close()
        }
    }
}

@Composable
internal fun AlbumDetailLandscapeArtworkBackdrop(
    album: Album,
    coverSessionKey: String,
    artworkSize: Dp,
    pageContainerColor: Color,
    collapsePx: () -> Float,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val coverSource = rememberStableAlbumHeroCoverSource(album, coverSessionKey)
    val imageModel = rememberAlbumCoverImageModel(coverSource)
    val clearRibbonShape = rememberAlbumLandscapeArtworkRibbonCoreShape(edgeInset = 10.dp)

    val ribbonHeight = (AlbumLandscapeArtworkTopPadding + artworkSize * 0.92f)
        .coerceAtLeast(340.dp)

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ribbonHeight)
        ) {
            // 先对裁剪后的副本做模糊，让模糊只从曲线边缘向外扩散。
            AsmrAsyncImage(
                model = imageModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                alpha = if (colorScheme.isDark) 0.30f else 0.22f,
                peekAnySizeForInitial = true,
                loadAtOriginalSize = true,
                fadeInMillis = AlbumDetailHeroIntroDurationMs,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(
                        radius = 18.dp,
                        edgeTreatment = BlurredEdgeTreatment.Unbounded
                    )
                    .clip(AlbumLandscapeArtworkRibbonShape),
                placeholder = { _ -> },
                loading = { _ -> },
                empty = { _ -> }
            )

            // 清晰副本覆盖在模糊副本中央，保留封面内容细节。
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(clearRibbonShape)
            ) {
                AsmrAsyncImage(
                    model = imageModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    alpha = if (colorScheme.isDark) 0.30f else 0.22f,
                    peekAnySizeForInitial = true,
                    loadAtOriginalSize = true,
                    fadeInMillis = AlbumDetailHeroIntroDurationMs,
                    modifier = Modifier.fillMaxSize(),
                    placeholder = { _ -> },
                    loading = { _ -> },
                    empty = { _ -> }
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0f to pageContainerColor.copy(alpha = 0.36f),
                                    0.16f to Color.Transparent,
                                    0.72f to pageContainerColor.copy(alpha = 0.16f),
                                    1f to pageContainerColor.copy(alpha = 0.70f)
                                )
                            )
                        )
                )
            }
        }

        HorizontalStereoSpectrum(
            lineColor = colorScheme.primaryStrong,
            modifier = Modifier
                .fillMaxWidth()
                .height(94.dp)
                .offset(y = albumLandscapeSpectrumOffsetY(artworkSize))
                .graphicsLayer {
                    translationY = albumLandscapeSpectrumTranslationY(collapsePx())
                }
        )
    }
}

@Composable
internal fun AlbumDetailLandscapeArtworkCover(
    album: Album,
    coverSessionKey: String,
    introSessionKey: String,
    animateIntro: Boolean,
    artworkSize: Dp,
    showCoverLoadingState: Boolean,
    collapsePx: () -> Float,
    collapseMaxPx: Float,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val coverSource = rememberStableAlbumHeroCoverSource(album, coverSessionKey)
    val imageModel = rememberAlbumCoverImageModel(coverSource)
    val heroIntroProgress = remember(introSessionKey) {
        Animatable(if (animateIntro) 0f else 1f)
    }
    var coverPainterAlphaState by remember(imageModel, coverSessionKey) {
        mutableStateOf<State<Float>?>(null)
    }
    var shouldRenderCoverShadow by remember(imageModel, coverSessionKey) {
        mutableStateOf(coverSource.isBlank())
    }
    LaunchedEffect(coverSource, coverPainterAlphaState) {
        if (coverSource.isBlank()) {
            shouldRenderCoverShadow = true
            return@LaunchedEffect
        }
        shouldRenderCoverShadow = false
        val painterAlphaState = coverPainterAlphaState ?: return@LaunchedEffect
        snapshotFlow { painterAlphaState.value }
            .first { imageAlpha ->
                imageAlpha > AlbumLandscapeCoverShadowStartAlpha
            }
        // 至少让已经开始渐入的图片先完整绘制一帧，再把阴影节点插入组合树。
        withFrameNanos { }
        shouldRenderCoverShadow = true
    }
    LaunchedEffect(introSessionKey, animateIntro) {
        if (!animateIntro) {
            heroIntroProgress.snapTo(1f)
            return@LaunchedEffect
        }
        heroIntroProgress.snapTo(0f)
        withFrameNanos { }
        heroIntroProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = AlbumDetailHeroIntroDurationMs,
                easing = FastOutSlowInEasing
            )
        )
    }

    val artworkShape = RoundedCornerShape(26.dp)

    Box(
        modifier = modifier.graphicsLayer {
            alpha = heroIntroProgress.value.coerceIn(0f, 1f)
            compositingStrategy = CompositingStrategy.ModulateAlpha
        }
    ) {
        Box(
            modifier = Modifier
                .offset(
                    x = AlbumLandscapeArtworkStartPadding,
                    y = AlbumLandscapeArtworkTopPadding
                )
                .size(artworkSize)
                .graphicsLayer {
                    val currentCollapsePx = collapsePx()
                    val collapseProgress = albumLandscapeCollapseProgress(
                        collapsePx = currentCollapsePx,
                        collapseMaxPx = collapseMaxPx
                    )
                    val scale = albumLandscapeCoverScale(
                        collapsePx = currentCollapsePx,
                        collapseMaxPx = collapseMaxPx
                    )
                    scaleX = scale
                    scaleY = scale
                    translationX = AlbumLandscapeCollapsedArtworkShiftX.toPx() * collapseProgress
                    translationY = AlbumLandscapeCollapsedArtworkShiftY.toPx() * collapseProgress
                    transformOrigin = TransformOrigin(0f, 0f)
                }
                .graphicsLayer {
                    val intro = heroIntroProgress.value.coerceIn(0f, 1f)
                    val scale = 0.96f + intro * 0.04f
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin.Center
                }
        ) {
            if (shouldRenderCoverShadow) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            val imageAlpha = coverPainterAlphaState?.value ?: 1f
                            alpha = albumLandscapeCoverShadowAlpha(imageAlpha)
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                        }
                        .shadow(
                            elevation = if (colorScheme.isDark) 6.dp else 14.dp,
                            shape = artworkShape,
                            clip = false
                        )
                        .background(colorScheme.surfaceVariant, artworkShape)
                )
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(artworkShape)
            ) {
                AsmrAsyncImage(
                    model = imageModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    placeholderCornerRadius = 26,
                    peekAnySizeForInitial = true,
                    loadAtOriginalSize = true,
                    fadeInMillis = AlbumDetailHeroIntroDurationMs,
                    onBitmapPainterState = { painter, alphaState ->
                        coverPainterAlphaState = if (painter != null) alphaState else null
                    },
                    modifier = Modifier.fillMaxSize(),
                    placeholder = { m -> DiscPlaceholder(modifier = m, cornerRadius = 26) },
                    // 加载阶段保持封面槽为空，避免占位底色被误认为先出现的黑框。
                    loading = { _ -> },
                    empty = { m ->
                        if (showCoverLoadingState) {
                            AsmrImageLoadingPlaceholder(
                                modifier = m,
                                cornerRadius = 26,
                                indicatorSize = 34.dp
                            )
                        } else {
                            DiscPlaceholder(modifier = m, cornerRadius = 26)
                        }
                    }
                )
            }
        }
    }
}

@Composable
internal fun AlbumDetailLandscapeIdentity(
    album: Album,
    introSessionKey: String,
    animateIntro: Boolean,
    pageContainerColor: Color,
    listenTogetherRjListenerCount: Int?,
    messageManager: MessageManager,
    onMetaLongClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val identity = rememberStableAlbumHeroIdentity(album, introSessionKey)
    val copyMeta = rememberAlbumMetaCopyAction(messageManager)
    val introProgress = remember(introSessionKey) {
        Animatable(if (animateIntro) 0f else 1f)
    }
    LaunchedEffect(introSessionKey, animateIntro) {
        if (!animateIntro) {
            introProgress.snapTo(1f)
            return@LaunchedEffect
        }
        introProgress.snapTo(0f)
        withFrameNanos { }
        introProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = AlbumDetailHeroIntroDurationMs,
                easing = FastOutSlowInEasing
            )
        )
    }

    val titleShadow = Shadow(
        color = if (colorScheme.isDark) {
            Color.Black.copy(alpha = 0.48f)
        } else {
            pageContainerColor.copy(alpha = 0.92f)
        },
        offset = Offset(0f, 2f),
        blurRadius = 11f
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = introProgress.value.coerceIn(0f, 1f)
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .padding(horizontal = AlbumDetailHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = translatedPageText(identity.title),
            modifier = Modifier.clickable { copyMeta("标题", identity.title) },
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                lineHeight = 30.sp,
                shadow = titleShadow
            ),
            color = colorScheme.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumHeroPrimaryMetaLightweight(
                rjCode = identity.rj,
                circle = identity.circle,
                emphasized = true,
                modifier = Modifier.weight(1f),
                rjOnClick = { copyMeta("作品编号", identity.rj) },
                circleOnClick = { copyMeta("社团", identity.circle) },
                circleOnLongClick = { onMetaLongClick(identity.circle) }
            )
            AlbumOnlineListenerInfo(
                listenerCount = listenTogetherRjListenerCount,
                visible = identity.rj.isNotBlank(),
                emphasized = true,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
internal fun AlbumDetailLandscapeSimilarWorksPane(
    seedRjCode: String,
    seedMetadata: Album,
    isRouteReady: Boolean,
    onOpenAlbumByRj: (String, DlsiteRecommendedWork?) -> Unit,
    viewModel: AlbumDetailViewModel,
    modifier: Modifier = Modifier,
) {
    val colorScheme = AsmrTheme.colorScheme
    val state by viewModel.similarWorksState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val seedFeatures = remember(
        seedRjCode,
        seedMetadata.circle,
        seedMetadata.cv,
        seedMetadata.tags
    ) {
        buildAlbumDetailRecommendationSeedFeatures(seedRjCode, seedMetadata)
    }

    LaunchedEffect(seedRjCode, seedFeatures, isRouteReady, viewModel) {
        if (isRouteReady) {
            viewModel.ensureSimilarWorksLoaded(
                seedRjCode = seedRjCode,
                seedFeatures = seedFeatures
            )
        }
    }
    DisposableEffect(seedRjCode, viewModel) {
        onDispose(viewModel::cancelSimilarWorksLoad)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "相似作品",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = colorScheme.textPrimary
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when {
                (!isRouteReady && state.works.isEmpty()) ||
                    (state.isLoading && state.works.isEmpty()) -> {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(22.dp),
                        color = colorScheme.primaryStrong,
                        strokeWidth = 2.dp
                    )
                }

                state.works.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (state.failed) "相似作品加载失败" else "暂时没有相似作品推荐",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.textSecondary,
                            textAlign = TextAlign.Center
                        )
                        if (state.failed) {
                            TextButton(onClick = {
                                viewModel.ensureSimilarWorksLoaded(
                                    seedRjCode = seedRjCode,
                                    seedFeatures = seedFeatures,
                                    force = true
                                )
                            }) {
                                Text("重试")
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize(),
                        contentPadding = PaddingValues(
                            bottom = LocalBottomOverlayPadding.current + 12.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = state.works,
                            key = AlbumDetailSimilarWork::rjCode
                        ) { work ->
                            AlbumDetailSimilarWorkCard(
                                work = work,
                                onClick = {
                                    onOpenAlbumByRj(
                                        work.rjCode,
                                        DlsiteRecommendedWork(
                                            rjCode = work.rjCode,
                                            title = work.title,
                                            coverUrl = work.coverUrl
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AlbumDetailPortraitSimilarWorksRow(
    seedRjCode: String,
    seedMetadata: Album,
    isRouteReady: Boolean,
    onOpenAlbumByRj: (String, DlsiteRecommendedWork?) -> Unit,
    viewModel: AlbumDetailViewModel,
    modifier: Modifier = Modifier,
) {
    val colorScheme = AsmrTheme.colorScheme
    val state by viewModel.similarWorksState.collectAsStateWithLifecycle()
    val seedFeatures = remember(
        seedRjCode,
        seedMetadata.circle,
        seedMetadata.cv,
        seedMetadata.tags
    ) {
        buildAlbumDetailRecommendationSeedFeatures(seedRjCode, seedMetadata)
    }

    LaunchedEffect(seedRjCode, seedFeatures, isRouteReady, viewModel) {
        if (isRouteReady) {
            viewModel.ensureSimilarWorksLoaded(
                seedRjCode = seedRjCode,
                seedFeatures = seedFeatures
            )
        }
    }
    DisposableEffect(seedRjCode, viewModel) {
        onDispose(viewModel::cancelSimilarWorksLoad)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AlbumDetailHorizontalPadding, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AlbumDetailSectionHeading(title = "相似作品推荐")
        when {
            (!isRouteReady && state.works.isEmpty()) ||
                (state.isLoading && state.works.isEmpty()) -> {
                DlsiteRecommendationLoadingCards()
            }

            state.works.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 76.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (state.failed) "相似作品加载失败" else "暂时没有相似作品推荐",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.textSecondary,
                        textAlign = TextAlign.Center
                    )
                    if (state.failed) {
                        TextButton(
                            onClick = {
                                viewModel.ensureSimilarWorksLoaded(
                                    seedRjCode = seedRjCode,
                                    seedFeatures = seedFeatures,
                                    force = true
                                )
                            }
                        ) {
                            Text("重试")
                        }
                    }
                }
            }

            else -> {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = state.works,
                        key = AlbumDetailSimilarWork::rjCode
                    ) { work ->
                        val recommendedWork = DlsiteRecommendedWork(
                            rjCode = work.rjCode,
                            title = work.title,
                            coverUrl = work.coverUrl
                        )
                        DlsiteRecommendedWorkCard(
                            work = recommendedWork,
                            displayRj = work.rjCode,
                            onClick = {
                                onOpenAlbumByRj(
                                    work.rjCode,
                                    recommendedWork
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumDetailSimilarWorkCard(
    work: AlbumDetailSimilarWork,
    onClick: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    val imageModel = rememberAlbumCoverImageModel(work.coverUrl)
    val containerColor = colorScheme.surface.copy(
        alpha = if (colorScheme.isDark) 0.72f else 0.84f
    ).compositeOver(colorScheme.background)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(shape)
            .background(containerColor)
            .border(
                width = 0.5.dp,
                color = colorScheme.primaryStrong.copy(
                    alpha = if (colorScheme.isDark) 0.22f else 0.14f
                ),
                shape = shape
            )
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsmrAsyncImage(
            model = imageModel,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholderCornerRadius = 0,
            peekAnySizeForInitial = true,
            loading = NoImageLoadingIndicator,
            modifier = Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp))
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = translatedPageText(work.title),
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = work.cv.ifBlank { work.rjCode },
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
