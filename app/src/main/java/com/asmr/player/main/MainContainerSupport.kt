package com.asmr.player.main

import com.asmr.player.translation.PageTranslationAction
import com.asmr.player.translation.PageTranslationHost
import android.view.Choreographer
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.composable
import com.asmr.player.ui.library.AlbumHeroBlurLayerCache
import com.asmr.player.ui.library.AlbumDetailUiState
import com.asmr.player.ui.library.AlbumDetailViewModel
import com.asmr.player.performance.UiFrameWorkCoordinator
import com.asmr.player.ui.common.core.EaraTopBarIconButton
import com.asmr.player.ui.common.core.resolveMainPageBackgroundColor
import com.asmr.player.ui.nav.BottomChrome
import com.asmr.player.ui.nav.BottomChromeNavItem
import com.asmr.player.ui.nav.bottomChromeNavItems
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import com.asmr.player.ui.theme.AsmrTheme
import android.os.Build
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.animation.*
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.asmr.player.ui.player.MiniPlayerDisplayMode
import com.asmr.player.ui.common.list.StableWindowInsets
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.border
import androidx.media3.common.MediaItem
import androidx.lifecycle.compose.collectAsStateWithLifecycle







internal data class PlaylistPickerRequest(
    val items: List<MediaItem>
)

internal data class BatchPlaylistPickerRequest(
    val items: List<MediaItem>
)

internal const val SecondaryPageEnterDurationMs = 440
internal const val SecondaryPageExitDurationMs = 420
internal const val SecondaryPageTouchBlockDurationMs = 320
private const val AlbumDetailPresentedStateKey = "album_detail_presented"
internal const val PrimaryPagerSnapThreshold = 0.16f
internal const val PrimaryPageSwitchDurationMs = 320
internal const val PrimaryPageSwitchQuietTailMs = 120L
internal val SecondaryPageSlideEasing = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)
internal val PrimaryPageSwitchEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
internal val PrimaryPageParallaxOffset = 120.dp
private val AlbumDetailTopBarButtonShape = CircleShape
internal val AlbumDetailBackTouchPassThroughWidth = 88.dp
internal val AlbumDetailTopBarTouchPassThroughHeight = 64.dp

/**
 * 把过渡期的可见区域裁剪交给 RenderNode。边界只改变图层属性，不会让页面内容的
 * display list 每帧重新录制；矩形 outline 同时保留原有的精确裁剪范围。
 */
internal class HorizontalRectClipShape(
    private val leftPx: Float,
    private val rightPx: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val left = leftPx.coerceIn(0f, size.width)
        val right = rightPx.coerceIn(left, size.width)
        return Outline.Rectangle(Rect(left, 0f, right, size.height))
    }
}

internal fun NavBackStackEntry.usesSecondaryPageSlideTransition(): Boolean {
    if (isAlbumDetailRoute(destination.route)) return false
    return resolveCurrentPrimaryDestinationRoute(
        currentRoute = destination.route,
        playlistSystemType = arguments?.getString("type")
    ) == null
}

internal fun secondaryPageEnterTransition(): EnterTransition {
    return slideInHorizontally(
        animationSpec = tween(
            durationMillis = SecondaryPageEnterDurationMs,
            easing = SecondaryPageSlideEasing
        ),
        initialOffsetX = { fullWidth -> fullWidth }
    )
}

internal fun secondaryPagePopExitTransition(): ExitTransition {
    return slideOutHorizontally(
        animationSpec = tween(
            durationMillis = SecondaryPageExitDurationMs,
            easing = SecondaryPageSlideEasing
        ),
        targetOffsetX = { fullWidth -> fullWidth }
    )
}

private fun Modifier.albumDetailTopBarButtonSurface(
    enabled: Boolean,
    shape: Shape = AlbumDetailTopBarButtonShape
): Modifier {
    return if (!enabled) {
        this
    } else {
        this
            .background(Color.Black.copy(alpha = 0.42f), shape)
            .border(0.5.dp, Color.White.copy(alpha = 0.24f), shape)
            .clip(shape)
    }
}

/**
 * 以命令式令牌管理系统栏 insets 动画的子树分发。这个状态不进入
 * Compose，回调到期时不会让整个 MainContainer 因不可见的标记而重组。
 */
internal class InsetsAnimationDispatchSuppressor(
    private val target: View
) {
    private var nextToken = 0L
    private val activeTokens = mutableSetOf<Long>()
    private val callback = object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {
        override fun onProgress(
            insets: WindowInsetsCompat,
            runningAnimations: MutableList<WindowInsetsAnimationCompat>
        ): WindowInsetsCompat = insets
    }

    fun acquire(): Long {
        val token = ++nextToken
        if (activeTokens.add(token) && activeTokens.size == 1) {
            ViewCompat.setWindowInsetsAnimationCallback(target, callback)
        }
        return token
    }

    fun release(token: Long) {
        if (activeTokens.remove(token) && activeTokens.isEmpty()) {
            ViewCompat.setWindowInsetsAnimationCallback(target, null)
        }
    }

    fun clear() {
        activeTokens.clear()
        ViewCompat.setWindowInsetsAnimationCallback(target, null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlbumDetailRouteFrame(
    backStackEntry: NavBackStackEntry,
    previousBackStackEntry: NavBackStackEntry?,
    stackPopTargetEntryId: String?,
    onPopBackStack: (String?) -> Unit,
    onPageOffsetReader: (() -> Float) -> Unit,
    onExitStateChanged: (Boolean) -> Unit,
    onLocalAlbumRemoved: (AlbumDetailUiState.Removed) -> Unit = {},
    onEditRj: (String) -> Unit,
    content: @Composable (AlbumDetailViewModel, AlbumHeroBlurLayerCache) -> Unit
) {
    val viewModel = hiltViewModel<AlbumDetailViewModel>(backStackEntry)
    val previousAlbumDetailEntryId = previousBackStackEntry
        ?.takeIf { isAlbumDetailRoute(it.destination.route) }
        ?.id
    val usesStackTransition = previousAlbumDetailEntryId != null
    val alreadyPresented = backStackEntry.savedStateHandle
        .get<Boolean>(AlbumDetailPresentedStateKey) == true
    val skipEnterAnimation = alreadyPresented ||
        usesStackTransition ||
        stackPopTargetEntryId == backStackEntry.id
    val heroBlurGraphicsLayer = rememberGraphicsLayer()
    val heroBlurLayerCache = remember(heroBlurGraphicsLayer) {
        AlbumHeroBlurLayerCache(heroBlurGraphicsLayer)
    }
    val rootView = LocalView.current
    val pageOffsetProgress = remember(backStackEntry.id) {
        Animatable(if (skipEnterAnimation) 0f else 1f)
    }
    val pageOffsetReader = remember(pageOffsetProgress, rootView) {
        {
            pageOffsetProgress.value * rootView.width.toFloat().coerceAtLeast(1f)
        }
    }
    SideEffect {
        onPageOffsetReader(pageOffsetReader)
    }
    var exitRequested by remember(backStackEntry.id) { mutableStateOf(false) }
    val currentPopBackStack by rememberUpdatedState(onPopBackStack)
    val currentExitStateChanged by rememberUpdatedState(onExitStateChanged)
    val currentLocalAlbumRemoved by rememberUpdatedState(onLocalAlbumRemoved)
    val closeAlbumDetail = {
        if (!exitRequested) {
            UiFrameWorkCoordinator.markFrameCritical(
                SecondaryPageExitDurationMs.toLong()
            )
            // 返回输入本来就在帧与帧之间处理。在同一个 snapshot 中提交
            // 退出标记和底层页 active 状态，避免下一帧再追加一次整树重组。
            if (!usesStackTransition) {
                currentExitStateChanged(true)
            }
            viewModel.cancelOnlineLoadsForExit()
            exitRequested = true
        }
    }
    LaunchedEffect(viewModel) {
        val removed = viewModel.uiState
            .filter { it is AlbumDetailUiState.Removed }
            .first() as AlbumDetailUiState.Removed
        currentLocalAlbumRemoved(removed)
        closeAlbumDetail()
    }
    BackHandler(enabled = !exitRequested, onBack = closeAlbumDetail)

    LaunchedEffect(backStackEntry.id) {
        // 方向切换可能让当前目的地短暂退出组合。把已展示状态保存在返回栈项中，
        // 重建后直接恢复到屏内，避免重新执行一次入场并与随后的退出动画叠加。
        backStackEntry.savedStateHandle[AlbumDetailPresentedStateKey] = true
        if (skipEnterAnimation) {
            pageOffsetProgress.snapTo(0f)
        } else {
            pageOffsetProgress.snapTo(1f)
            // 详情页先在屏幕外完成一次组合与绘制，再启动可见位移。导航提前使用原本的第二个
            // 准备帧，因此不会改变用户看到的动画起点、时长或缓动。
            withFrameNanos { }
            UiFrameWorkCoordinator.markFrameCritical(
                SecondaryPageEnterDurationMs.toLong()
            )
            pageOffsetProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = SecondaryPageEnterDurationMs,
                    easing = SecondaryPageSlideEasing
                )
            )
        }
        snapshotFlow { exitRequested }.filter { it }.first()

        if (usesStackTransition) {
            rootView.post { currentPopBackStack(previousAlbumDetailEntryId) }
            return@LaunchedEffect
        }

        // 退出前先让底层主页面恢复 active；此时详情页仍完全覆盖屏幕，不产生视觉变化。
        withFrameNanos { }
        UiFrameWorkCoordinator.markFrameCritical(
            SecondaryPageExitDurationMs.toLong()
        )
        pageOffsetProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = SecondaryPageExitDurationMs,
                easing = SecondaryPageSlideEasing
            )
        )
        // animateTo 在 Choreographer 的 animation 阶段恢复协程。如果在这里直接
        // pop，导航销毁会被计入最后一帧动画回调。页面已完全在屏外，
        // 立即投递到当前帧结束后的主线程队列空隙，避免二者叠在同一帧。
        rootView.post { currentPopBackStack(null) }
    }
    DisposableEffect(backStackEntry.id) {
        onDispose {
            currentExitStateChanged(false)
        }
    }

    PageTranslationHost(active = !exitRequested) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    val offset = width * pageOffsetProgress.value.coerceIn(0f, 1f)
                    val visibleRight = if (offset >= width - 0.5f) {
                        // 保留屏外预绘制帧，避免动画起点改变。
                        width
                    } else {
                        (width - offset).coerceIn(0f, width)
                    }
                    // 位移和裁剪都只更新 RenderNode 属性，避免逐帧重新录制整张详情页。
                    translationX = offset
                    shape = HorizontalRectClipShape(0f, visibleRight)
                    clip = true
                }
        ) {
            content(viewModel, heroBlurLayerCache)
            AlbumDetailRouteTopBar(
                viewModel = viewModel,
                onBack = closeAlbumDetail,
                onEditRj = onEditRj,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(2f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlbumDetailRouteTopBar(
    viewModel: AlbumDetailViewModel,
    onBack: () -> Unit,
    onEditRj: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val detailState by viewModel.uiState.collectAsStateWithLifecycle()
    val detailModel = (detailState as? AlbumDetailUiState.Success)?.model
    val showManualBind = detailModel?.localAlbum?.id?.let { it > 0L } == true

    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.windowInsetsTopHeight(StableWindowInsets.statusBars))
        CenterAlignedTopAppBar(
            modifier = Modifier.height(56.dp),
            title = {},
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = Color.Transparent,
                navigationIconContentColor = Color.White,
                actionIconContentColor = Color.White
            ),
            navigationIcon = {
                EaraTopBarIconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .albumDetailTopBarButtonSurface(true)
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            },
            actions = {
                PageTranslationAction(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .albumDetailTopBarButtonSurface(true),
                    contentColor = Color.White,
                    activeContentColor = Color.White,
                )
                if (showManualBind) {
                    EaraTopBarIconButton(
                        onClick = {
                            val local = detailModel?.localAlbum
                            val currentRj = detailModel?.rjCode?.trim().orEmpty()
                                .ifBlank { local?.rjCode?.trim().orEmpty() }
                                .ifBlank { local?.workId?.trim().orEmpty() }
                            onEditRj(currentRj)
                        },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .albumDetailTopBarButtonSurface(true)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "手动输入作品编号",
                            tint = Color.White
                        )
                    }
                }
            }
        )
    }
}

@Composable
internal fun SecondaryPageBackground(
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val pageBackgroundColor = resolveMainPageBackgroundColor(colorScheme)

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(top = topPadding)
            .background(pageBackgroundColor)
    ) {
        content()
    }
}

@Suppress("DEPRECATION")
internal fun applyMainContainerSystemUi(
    window: android.view.Window,
    forceImmersive: Boolean,
    hideStatusBarForImmersivePage: Boolean,
    nowPlayingVisible: Boolean,
    isDark: Boolean
) {
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    WindowCompat.setDecorFitsSystemWindows(window, false)
    if (
        controller.systemBarsBehavior !=
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    ) {
        // 提前固定系统栏手势行为，避免首次进入沉浸页面时再修改 Window 参数并触发 relayout。
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        if (window.isStatusBarContrastEnforced) {
            window.isStatusBarContrastEnforced = false
        }
        if (window.isNavigationBarContrastEnforced) {
            window.isNavigationBarContrastEnforced = false
        }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        // Edge-to-edge 内容始终按照短边刘海区域布局，路由切换时只改变系统栏可见性。
        // 若在专辑转场开始时同步修改 Window 属性，WindowManager 会触发一次昂贵的 relayout，
        // 与 Compose 转场争抢同一帧的主线程和 RenderThread 预算。
        val targetCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        val attributes = window.attributes
        if (attributes.layoutInDisplayCutoutMode != targetCutoutMode) {
            attributes.layoutInDisplayCutoutMode = targetCutoutMode
            window.attributes = attributes
        }
    }

    when {
        forceImmersive -> {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            if (window.statusBarColor != android.graphics.Color.TRANSPARENT) {
                window.statusBarColor = android.graphics.Color.TRANSPARENT
            }
            if (window.navigationBarColor != android.graphics.Color.TRANSPARENT) {
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
            }
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
        nowPlayingVisible -> {
            controller.hide(WindowInsetsCompat.Type.statusBars())
            controller.hide(WindowInsetsCompat.Type.navigationBars())
            if (window.statusBarColor != android.graphics.Color.TRANSPARENT) {
                window.statusBarColor = android.graphics.Color.TRANSPARENT
            }
            if (window.navigationBarColor != android.graphics.Color.TRANSPARENT) {
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
            }
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
        hideStatusBarForImmersivePage -> {
            controller.hide(WindowInsetsCompat.Type.statusBars())
            controller.show(WindowInsetsCompat.Type.navigationBars())
            if (window.statusBarColor != android.graphics.Color.TRANSPARENT) {
                window.statusBarColor = android.graphics.Color.TRANSPARENT
            }
            if (window.navigationBarColor != android.graphics.Color.TRANSPARENT) {
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
            }
            // 状态栏已经不可见，不切换图标明暗标志；该 Window 参数变化会额外触发 relayout。
            controller.isAppearanceLightNavigationBars = !isDark
        }
        else -> {
            controller.show(WindowInsetsCompat.Type.systemBars())
            if (window.statusBarColor != android.graphics.Color.TRANSPARENT) {
                window.statusBarColor = android.graphics.Color.TRANSPARENT
            }
            if (window.navigationBarColor != android.graphics.Color.TRANSPARENT) {
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
            }
            controller.isAppearanceLightStatusBars = !isDark
            controller.isAppearanceLightNavigationBars = !isDark
        }
    }
}

@Suppress("DEPRECATION")
internal fun restoreMainContainerSystemUi(
    window: android.view.Window,
    defaultSystemUi: DefaultSystemUiState?
) {
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    WindowCompat.setDecorFitsSystemWindows(window, false)
    controller.show(WindowInsetsCompat.Type.systemBars())
    defaultSystemUi?.let { ui ->
        window.statusBarColor = ui.statusBarColor
        window.navigationBarColor = ui.navigationBarColor
        controller.isAppearanceLightStatusBars = ui.lightStatusBars
        controller.isAppearanceLightNavigationBars = ui.lightNavigationBars
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ui.statusBarContrastEnforced?.let { window.isStatusBarContrastEnforced = it }
            ui.navigationBarContrastEnforced?.let { window.isNavigationBarContrastEnforced = it }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ui.layoutInDisplayCutoutMode?.let { mode ->
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = mode
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
internal fun captureDefaultSystemUiState(window: android.view.Window): DefaultSystemUiState {
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    return DefaultSystemUiState(
        statusBarColor = window.statusBarColor,
        navigationBarColor = window.navigationBarColor,
        lightStatusBars = controller.isAppearanceLightStatusBars,
        lightNavigationBars = controller.isAppearanceLightNavigationBars,
        statusBarContrastEnforced = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced
        } else {
            null
        },
        navigationBarContrastEnforced = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced
        } else {
            null
        },
        layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode
        } else {
            null
        }
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun PrimaryBottomChrome(
    activeRoute: String,
    pagerState: PagerState,
    pagerRoutes: List<String>,
    fallbackRoute: String,
    lockedRoute: String?,
    miniPlayerVisible: Boolean,
    miniPlayerDisplayMode: MiniPlayerDisplayMode,
    miniPlayerPlayFeedbackSignal: Long,
    onMiniPlayerDisplayModeChange: (MiniPlayerDisplayMode) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenQueue: () -> Unit,
    onNavigate: (String) -> Unit,
    largeLayout: Boolean = false,
    modifier: Modifier = Modifier,
    navItems: List<BottomChromeNavItem> = bottomChromeNavItems()
) {
    val selectionProgresses by remember(
        pagerState,
        pagerRoutes,
        fallbackRoute,
        lockedRoute
    ) {
        derivedStateOf {
            computePrimaryNavSelectionProgresses(
                pagerRoutes = pagerRoutes,
                currentPage = pagerState.currentPage,
                currentPageOffsetFraction = pagerState.currentPageOffsetFraction,
                fallbackRoute = fallbackRoute,
                lockedRoute = lockedRoute
            )
        }
    }

    BottomChrome(
        activeRoute = activeRoute,
        selectionProgresses = selectionProgresses,
        miniPlayerVisible = miniPlayerVisible,
        miniPlayerDisplayMode = miniPlayerDisplayMode,
        miniPlayerPlayFeedbackSignal = miniPlayerPlayFeedbackSignal,
        onMiniPlayerDisplayModeChange = onMiniPlayerDisplayModeChange,
        onOpenNowPlaying = onOpenNowPlaying,
        onOpenQueue = onOpenQueue,
        onNavigate = onNavigate,
        largeLayout = largeLayout,
        modifier = modifier,
        navItems = navItems
    )
}

