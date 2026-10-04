package com.asmr.player.ui.library.albumdetail

import com.asmr.player.translation.translatedPageText

import android.content.Intent
import android.graphics.PathMeasure as AndroidPathMeasure
import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.CompositingStrategy as LayerCompositingStrategy
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.asmr.player.data.local.db.AppDatabaseProvider
import com.asmr.player.data.local.db.entities.LocalTreeCacheEntity
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.Track
import com.asmr.player.playback.MediaItemFactory
import com.asmr.player.ui.common.audio.HorizontalStereoSpectrum
import com.asmr.player.util.CacheImageModel
import com.asmr.player.ui.dlsite.DlsitePlayViewModel
import com.asmr.player.util.DlsiteAntiHotlink
import com.asmr.player.util.SmartSortKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import com.asmr.player.data.lyrics.deriveLyricsRelativePathNoExt
import com.asmr.player.ui.common.audio.SubtitleStamp
import com.asmr.player.ui.common.cover.DiscPlaceholder
import com.asmr.player.ui.common.cover.AsmrAsyncImage
import com.asmr.player.ui.common.cover.AsmrImageLoadingPlaceholder
import com.asmr.player.ui.common.cover.EaraLogoLoadingIndicator
import com.asmr.player.ui.common.cover.NoImageLoadingIndicator
import com.asmr.player.ui.common.cover.ImagePreviewDialog
import com.asmr.player.ui.common.cover.ImagePreviewRequest
import com.asmr.player.ui.common.list.LocalBottomOverlayPadding
import com.asmr.player.ui.common.core.consumeTapThrough
import com.asmr.player.ui.groups.AlbumGroupsViewModel
import com.asmr.player.ui.common.dialog.RoundedTopSheet
import com.asmr.player.ui.groups.AlbumGroupPickerScreen
import com.asmr.player.ui.playlists.PlaylistPickerScreen
import com.asmr.player.ui.playlists.PlaylistsViewModel
import com.asmr.player.ui.player.PlayerViewModel
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.theme.AsmrPlayerTheme
import com.asmr.player.ui.theme.dynamicPageContainerColor
import com.asmr.player.util.Formatting
import com.asmr.player.util.MessageManager
import com.asmr.player.util.RemoteSubtitleSource
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt
import com.asmr.player.ui.library.AlbumHeroPrimaryMetaLightweight
import com.asmr.player.ui.library.rememberAlbumMetaCopyAction

@Composable
internal fun AlbumDetailHeroBackground(
    album: Album,
    coverSessionKey: String,
    introSessionKey: String,
    animateIntro: Boolean,
    height: Dp,
    pageContainerColor: Color,
    listenTogetherRjListenerCount: Int?,
    showCoverLoadingState: Boolean,
    messageManager: MessageManager,
    onMetaLongClick: (String) -> Unit,
    blurLayerCache: AlbumHeroBlurLayerCache,
    modifier: Modifier = Modifier,
    collapsePx: () -> Float = { 0f },
    collapseMaxPx: Float = 0f,
    visualOvershootPx: () -> Float = { 0f },
    visualOvershootMaxPx: Float = 1f
) {
    val coverSource = rememberStableAlbumHeroCoverSource(album, coverSessionKey)
    val imageModel = rememberAlbumCoverImageModel(coverSource)
    var blurSource by remember(imageModel) {
        mutableStateOf<AlbumHeroBlurSource?>(null)
    }
    val density = LocalDensity.current
    val fullHeightPx = with(density) { height.toPx() }
    val heroIntroProgress = remember(introSessionKey) {
        Animatable(if (animateIntro) 0f else 1f)
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
    val blurRadiusPx = with(density) {
        AlbumDetailHeroBlurRadius.toPx().coerceAtMost(AlbumDetailHeroBlurRadiusMaxPx)
    }
    val blurRampHeightPx = with(density) {
        AlbumDetailHeroBlurRampHeight.toPx().coerceAtMost(fullHeightPx * 0.52f)
    }
    // Gaussian blur 在可见渐变上方只需要保留完整的 3σ 采样范围。把透明区域也放进
    // 离屏 RenderNode 会让 GPU 每帧处理整张 hero，虽然那些像素最终都会被蒙版丢弃。
    val blurLayerHeightPx = (
        blurRampHeightPx + blurRadiusPx * AlbumDetailHeroBlurSampleMarginMultiplier
        ).coerceAtMost(fullHeightPx)
    val blurLayerHeight = with(density) { blurLayerHeightPx.toDp() }
    val blurRenderEffect = remember(blurRadiusPx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            RenderEffect
                .createBlurEffect(blurRadiusPx, blurRadiusPx, Shader.TileMode.CLAMP)
                .asComposeRenderEffect()
        } else {
            null
        }
    }
    val legacyBlurModifier = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        Modifier.blur(AlbumDetailHeroBlurRadius)
    } else {
        Modifier
    }

    // hero 的可见高度跟随手势变化，但内部始终按完整高度测量。这样封面、毛玻璃和文字不必逐帧
    // 重测；底部元素只通过图层位移跟随折叠，模糊缓存也能在滚动期间持续复用。
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .layout { measurable, constraints ->
                val measuredFullHeight = fullHeightPx
                    .roundToInt()
                    .coerceAtLeast(1)
                    .coerceIn(constraints.minHeight, constraints.maxHeight)
                val collapse = collapsePx().coerceIn(0f, collapseMaxPx)
                val targetHeight = (measuredFullHeight - collapse)
                    .coerceAtLeast(1f)
                    .roundToInt()
                val placeable = measurable.measure(
                    constraints.copy(
                        minHeight = measuredFullHeight,
                        maxHeight = measuredFullHeight
                    )
                )
                layout(placeable.width, targetHeight) {
                    placeable.place(0, 0)
                }
            }
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .consumeTapThrough()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    val intro = heroIntroProgress.value.coerceIn(0f, 1f)
                    val introScale = AlbumDetailHeroIntroStartScale -
                        (AlbumDetailHeroIntroStartScale - 1f) * intro
                    val overshootProgress = (
                        -visualOvershootPx() / visualOvershootMaxPx.coerceAtLeast(1f)
                        ).coerceIn(0f, 1f)
                    val scale = introScale * (
                        1f + overshootProgress * AlbumDetailHeroExpandOvershootScale
                        )
                    alpha = intro
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
        ) {
            AsmrAsyncImage(
                model = imageModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                placeholderCornerRadius = 0,
                peekAnySizeForInitial = true,
                loadAtOriginalSize = true,
                onBitmapPainterState = { painter, alpha ->
                    blurSource = painter?.let { AlbumHeroBlurSource(it, alpha) }
                },
                modifier = Modifier.fillMaxSize(),
                placeholder = { m -> DiscPlaceholder(modifier = m, cornerRadius = 0) },
                loading = { m ->
                    AsmrImageLoadingPlaceholder(modifier = m, cornerRadius = 0, indicatorSize = 36.dp)
                },
                empty = { m ->
                    if (showCoverLoadingState) {
                        AsmrImageLoadingPlaceholder(modifier = m, cornerRadius = 0, indicatorSize = 36.dp)
                    } else {
                        DiscPlaceholder(modifier = m, cornerRadius = 0)
                    }
                },
            )
            // 渐进式毛玻璃：从标题区域开始叠加模糊副本，让标题和元信息下方仍保留封面纹理。
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(blurLayerHeight)
                    .graphicsLayer {
                        translationY = -collapsePx().coerceIn(0f, collapseMaxPx)
                    }
                    .clipToBounds()
                    .then(legacyBlurModifier)
                    .drawWithCache {
                        val rampStartY = (size.height - blurRampHeightPx).coerceAtLeast(0f)
                        val stops = (0..6).map { i ->
                            val t = i / 6f
                            val eased = t * t * (3f - 2f * t)
                            t to Color.White.copy(alpha = 0.18f + eased * 0.82f)
                        }.toTypedArray()
                        val mask = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                *stops,
                                1f to Color.White
                            ),
                            startY = rampStartY,
                            endY = size.height
                        )
                        val layerSize = IntSize(
                            width = size.width.roundToInt().coerceAtLeast(1),
                            height = size.height.roundToInt().coerceAtLeast(1)
                        )
                        val fullHeroSize = Size(size.width, fullHeightPx)
                        val fullHeroIntSize = IntSize(
                            width = fullHeroSize.width.roundToInt().coerceAtLeast(1),
                            height = fullHeroSize.height.roundToInt().coerceAtLeast(1)
                        )
                        val sliceTop = (fullHeightPx - size.height).coerceAtLeast(0f)
                        val source = blurSource
                        if (source == null) {
                            onDrawBehind {
                                if (
                                    blurLayerCache.matches(
                                        contentKey = imageModel,
                                        layerSize = layerSize,
                                        fullHeroSize = fullHeroIntSize
                                    )
                                ) {
                                    blurLayerCache.layer.alpha = 1f
                                    drawLayer(blurLayerCache.layer)
                                }
                            }
                        } else {
                            val intrinsicSize = source.painter.intrinsicSize
                            if (intrinsicSize.width <= 0f || intrinsicSize.height <= 0f) {
                                onDrawBehind {}
                            } else {
                                val scaleFactor = ContentScale.Crop.computeScaleFactor(
                                    srcSize = intrinsicSize,
                                    dstSize = fullHeroSize
                                )
                                val scaledSize = Size(
                                    width = intrinsicSize.width * scaleFactor.scaleX,
                                    height = intrinsicSize.height * scaleFactor.scaleY
                                )
                                val alignedOffset = Alignment.TopCenter.align(
                                    size = IntSize(
                                        width = scaledSize.width.roundToInt(),
                                        height = scaledSize.height.roundToInt()
                                    ),
                                    space = fullHeroIntSize,
                                    layoutDirection = layoutDirection
                                )
                                if (
                                    !blurLayerCache.matches(
                                        contentKey = imageModel,
                                        layerSize = layerSize,
                                        fullHeroSize = fullHeroIntSize
                                    )
                                ) {
                                    blurLayerCache.layer.renderEffect = blurRenderEffect
                                    blurLayerCache.layer.compositingStrategy =
                                        LayerCompositingStrategy.Offscreen
                                    blurLayerCache.layer.record(
                                        density = this,
                                        layoutDirection = layoutDirection,
                                        size = layerSize
                                    ) {
                                        translate(
                                            left = alignedOffset.x.toFloat(),
                                            top = alignedOffset.y.toFloat() - sliceTop
                                        ) {
                                            with(source.painter) {
                                                // 毛玻璃内容只录制一次；淡入 alpha 在合成属性上更新，
                                                // 避免每帧重做大面积高斯模糊。
                                                draw(size = scaledSize)
                                            }
                                        }
                                        drawRect(brush = mask, blendMode = BlendMode.DstIn)
                                    }
                                    blurLayerCache.markRecorded(
                                        contentKey = imageModel,
                                        layerSize = layerSize,
                                        fullHeroSize = fullHeroIntSize
                                    )
                                }
                                onDrawBehind {
                                    blurLayerCache.layer.alpha = source.alpha.value
                                    drawLayer(blurLayerCache.layer)
                                }
                            }
                        }
                    }
            )
        }
        // 顶部深色蒙版，保证返回按钮等控件的可读性
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val topMask = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Black.copy(alpha = 0.44f),
                            0.42f to Color.Black.copy(alpha = 0.16f),
                            0.70f to Color.Transparent
                        )
                    )
                    onDrawBehind {
                        drawRect(
                            brush = topMask,
                            alpha = heroIntroProgress.value.coerceIn(0f, 1f)
                        )
                    }
                }
        )
        // 只在封面容器内部做底缘融色，让封面边缘轻轻透出页面背景。
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(AlbumDetailHeroTransitionHeight * 1.7f)
                .graphicsLayer {
                    translationY = -collapsePx().coerceIn(0f, collapseMaxPx)
                }
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.28f to pageContainerColor.copy(alpha = 0.08f),
                            0.52f to pageContainerColor.copy(alpha = 0.30f),
                            0.74f to pageContainerColor.copy(alpha = 0.70f),
                            0.88f to pageContainerColor,
                            1f to pageContainerColor
                        )
                    )
                )
        )
        AlbumHeroIdentityOverlay(
            album = album,
            introSessionKey = introSessionKey,
            listenTogetherRjListenerCount = listenTogetherRjListenerCount,
            messageManager = messageManager,
            onMetaLongClick = onMetaLongClick,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .graphicsLayer {
                    translationY = -collapsePx().coerceIn(0f, collapseMaxPx)
                }
        )
    }
}

@Composable
private fun AlbumHeroIdentityOverlay(
    album: Album,
    introSessionKey: String,
    listenTogetherRjListenerCount: Int?,
    messageManager: MessageManager,
    onMetaLongClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val copyMeta = rememberAlbumMetaCopyAction(messageManager)
    val identity = rememberStableAlbumHeroIdentity(album, introSessionKey)
    val rj = identity.rj
    val circle = identity.circle
    val showMetaRow = rj.isNotBlank() || circle.isNotBlank() ||
        (listenTogetherRjListenerCount != null && rj.isNotBlank())

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = AlbumDetailHorizontalPadding,
                end = AlbumDetailHorizontalPadding,
                bottom = 4.dp
            ),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Text(
            text = translatedPageText(identity.title),
            modifier = Modifier.clickable { copyMeta("标题", identity.title) },
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                shadow = Shadow(
                    color = if (colorScheme.isDark) Color.White.copy(alpha = 0.58f) else Color.Black.copy(alpha = 0.58f),
                    offset = Offset(0f, 2f),
                    blurRadius = 8f
                )
            ),
            color = if (colorScheme.isDark) Color.White else Color.Black,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )

        if (showMetaRow) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AlbumHeroPrimaryMetaLightweight(
                    rjCode = rj,
                    circle = circle,
                    modifier = Modifier.weight(1f),
                    rjOnClick = { copyMeta("作品编号", rj) },
                    circleOnClick = { copyMeta("社团", circle) },
                    circleOnLongClick = { onMetaLongClick(circle) },
                )
                AlbumOnlineListenerInfo(
                    listenerCount = listenTogetherRjListenerCount,
                    visible = rj.isNotBlank(),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
internal fun AlbumOnlineListenerInfo(
    listenerCount: Int?,
    visible: Boolean,
    emphasized: Boolean = false,
    modifier: Modifier = Modifier
) {
    val count = listenerCount?.coerceAtLeast(0)
    val colorScheme = AsmrTheme.colorScheme
    val contentColor = if (colorScheme.isDark) {
        Color.White.copy(alpha = 0.96f)
    } else {
        colorScheme.textPrimary.copy(alpha = 0.90f)
    }
    val textShadow = Shadow(
        color = if (colorScheme.isDark) Color.Black.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.58f),
        offset = Offset(0f, 1f),
        blurRadius = 5f
    )

    AnimatedVisibility(
        visible = visible && count != null,
        enter = fadeIn(animationSpec = AlbumHeaderEnterTweenSpec) + expandHorizontally(
            animationSpec = AlbumHeaderExpandTweenSpec,
            expandFrom = Alignment.Start
        ),
        exit = fadeOut(animationSpec = tween(durationMillis = 120)) + shrinkHorizontally(
            animationSpec = tween(durationMillis = 160, easing = FastOutLinearInEasing),
            shrinkTowards = Alignment.Start
        )
    ) {
        if (count != null) {
            Row(
                modifier = modifier,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = com.asmr.player.R.drawable.ic_users_round),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(if (emphasized) 14.dp else 12.dp)
                )
                Text(
                    text = "$count 人正在听",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = if (emphasized) 12.sp else MaterialTheme.typography.labelSmall.fontSize,
                        shadow = textShadow
                    ),
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

internal data class StableAlbumHeroIdentity(
    val title: String,
    val rj: String,
    val circle: String
)

internal fun resolveStableAlbumHeroIdentity(
    stable: StableAlbumHeroIdentity,
    current: StableAlbumHeroIdentity
): StableAlbumHeroIdentity {
    val stableTitleIsPlaceholder = stable.title.isBlank() ||
        stable.title == "专辑" ||
        stable.title.equals(stable.rj, ignoreCase = true)
    val currentTitleIsResolved = current.title.isNotBlank() &&
        current.title != "专辑" &&
        !current.title.equals(current.rj, ignoreCase = true)
    return StableAlbumHeroIdentity(
        title = if (stableTitleIsPlaceholder && currentTitleIsResolved) current.title else stable.title,
        rj = stable.rj.ifBlank { current.rj },
        circle = stable.circle.ifBlank { current.circle }
    )
}

@Composable
internal fun rememberStableAlbumHeroIdentity(album: Album, identitySessionKey: String): StableAlbumHeroIdentity {
    val current = StableAlbumHeroIdentity(
        title = album.title.trim().ifBlank { "专辑" },
        rj = album.rjCode.ifBlank { album.workId }.trim(),
        circle = album.circle.trim()
    )
    var stable by remember(identitySessionKey) { mutableStateOf(current) }
    LaunchedEffect(current) {
        stable = resolveStableAlbumHeroIdentity(stable, current)
    }
    return stable
}

@Composable
internal fun rememberStableAlbumHeroCoverSource(album: Album, coverSessionKey: String): String {
    val currentLocal = album.coverPath.trim()
    val current = currentLocal.ifEmpty { album.coverUrl.trim() }
    var stable by remember(coverSessionKey) { mutableStateOf(current) }
    LaunchedEffect(currentLocal, current) {
        stable = resolveStableAlbumHeroCoverSource(
            stable = stable,
            currentLocal = currentLocal,
            current = current
        )
    }
    return stable
}

@Composable
internal fun rememberAlbumCoverImageModel(data: String): Any {
    return remember(data) {
        val headers = if (data.startsWith("http", ignoreCase = true)) {
            DlsiteAntiHotlink.headersForImageUrl(data)
        } else {
            emptyMap()
        }
        if (headers.isEmpty()) {
            data
        } else {
            CacheImageModel(data = data, headers = headers, keyTag = "dlsite")
        }
    }
}

internal fun Modifier.albumDetailScrolledContentFade(
    fadeStartY: Dp,
    fadeEndY: Dp,
    fadeColor: Color
): Modifier {
    return drawWithCache {
            val fadeStartPx = fadeStartY.toPx().coerceAtLeast(0f)
            val fadeEndPx = fadeEndY.toPx().coerceAtLeast(fadeStartPx + 1f)
            val rampStart = (fadeStartPx / fadeEndPx).coerceIn(0f, 1f)
            val rampSpan = (1f - rampStart).coerceAtLeast(0.0001f)
            fun stopAt(t: Float): Pair<Float, Color> {
                val eased = t * t * (3f - 2f * t)
                return (rampStart + rampSpan * t) to fadeColor.copy(alpha = 1f - eased)
            }
            val fadeBrush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to fadeColor,
                    rampStart to fadeColor,
                    stopAt(0.2f),
                    stopAt(0.4f),
                    stopAt(0.6f),
                    stopAt(0.8f),
                    1f to Color.Transparent
                ),
                startY = 0f,
                endY = fadeEndPx
            )
            // 页面背景是纯色，用缓存的覆盖渐变即可得到同样的溶解效果；不再为整块长列表
            // 建立离屏缓冲区，也不在每个滚动帧重新创建 Brush 和色标数组。
            onDrawWithContent {
                drawContent()
                drawRect(
                    brush = fadeBrush,
                    size = androidx.compose.ui.geometry.Size(size.width, fadeEndPx)
                )
            }
        }
}

