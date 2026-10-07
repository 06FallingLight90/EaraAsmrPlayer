@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.asmr.player.ui.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.asmr.player.ui.common.cover.EaraLogoLoadingIndicator
import com.asmr.player.ui.common.list.LocalBottomOverlayPadding
import com.asmr.player.ui.theme.AsmrTheme
import kotlin.math.absoluteValue
import kotlinx.coroutines.awaitCancellation

private const val SearchPullRefreshFollowRatio = 0.86f
private val SearchPullRefreshSettleDistance = 68.dp
private val SearchPullRefreshMaxDistance = 112.dp
internal const val SearchPullRefreshMinFeedbackMillis = 420L
private val SearchPullActionHintHeight = 58.dp
private const val SearchPullNextPageDragResistance = 0.82f
private const val SearchPullNextPageFollowRatio = 0.84f
private const val SearchPullStretchExtraRatio = 0.28f
private const val SearchPullNextPageVerticalBias = 1.25f
private val SearchPullNextPageTriggerDistance = 96.dp
private val SearchPullNextPageMaxDistance = 172.dp
private val SearchPullNextPageMaxLift = 108.dp
private val SearchPullNextPageReturnSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow
)

private fun searchRubberBandOffset(
    dragPx: Float,
    triggerPx: Float,
    maxOffsetPx: Float,
    followRatio: Float
): Float {
    val clampedDrag = dragPx.coerceAtLeast(0f)
    val safeTrigger = triggerPx.coerceAtLeast(1f)
    val base = clampedDrag.coerceAtMost(safeTrigger) * followRatio
    val extra = (clampedDrag - safeTrigger).coerceAtLeast(0f) * SearchPullStretchExtraRatio
    return (base + extra).coerceIn(0f, maxOffsetPx)
}

/**
 * 搜索页下拉刷新 / 上拉翻页手势状态机（原 SearchScreenContent 内联状态与动画的收敛体）。
 *
 * 持久状态随 rememberSearchPullGestureState(resultScrollKey, viewMode) 的键重建，
 * 与原先各局部 var 的 remember(resultScrollKey, viewMode) 生命周期一致；
 * 每次重组由工厂回填 latest 快照（enabled/列表状态/回调/像素度量），
 * 读侧语义与原 rememberUpdatedState 包装等价。
 */
internal class SearchPullGestureState(
    val pullToRefreshState: PullToRefreshState
) {
    // —— 持久手势状态 ——
    var pullNextPageDragPx by mutableFloatStateOf(0f)
    var pullNextPageGestureActive by mutableStateOf(false)
    var pullNextPageReturnInProgress by mutableStateOf(false)
    var pullNextPageRequestAfterReturn by mutableStateOf(false)
    var searchPointerPressed by mutableStateOf(false)

    // —— 每次重组回填的 latest 快照 ——
    internal lateinit var listState: LazyListState
    internal lateinit var gridState: LazyStaggeredGridState
    internal var currentViewMode: Int = 0
    internal var pullNextPageEnabledBase: Boolean = false
    internal var topPaddingPx: Float = 0f
    internal var pullNextPageTriggerDistancePx: Float = 0f
    internal var pullNextPageMaxDistancePx: Float = 0f
    internal var pullNextPageMaxLiftPx: Float = 0f
    internal var pullActionHintHeightPx: Float = 0f
    internal var pullRefreshSettleDistancePx: Float = 0f
    internal var pullRefreshMaxDistancePx: Float = 0f
    internal var requestNextPage: () -> Unit = {}
    internal var onScrollLockChanged: (Boolean) -> Unit = {}

    // —— 派生读侧（等价于原 rememberUpdatedState + 派生 val）——
    val isPullNextPageEnabled: Boolean
        get() = pullNextPageEnabledBase && !pullToRefreshState.isRefreshing
    // 原版 pullNextPageGestureEnabled：翻页回落动画窗口内禁止再次拉起/触发（门禁审查 P1 补回）。
    val isPullNextPageGestureEnabled: Boolean
        get() = isPullNextPageEnabled && !pullNextPageReturnInProgress
    val isAtBottom: Boolean
        get() = if (currentViewMode == 0) !listState.canScrollForward else !gridState.canScrollForward
    val pullNextPageArmed: Boolean
        get() = pullNextPageDragPx >= pullNextPageTriggerDistancePx
    val pullNextPageVisualTargetPx: Float
        get() = searchRubberBandOffset(
            dragPx = pullNextPageDragPx,
            triggerPx = pullNextPageTriggerDistancePx,
            maxOffsetPx = pullNextPageMaxLiftPx,
            followRatio = SearchPullNextPageFollowRatio
        )
    internal var pullNextPageVisualOffsetPxState: State<Float> = mutableStateOf(0f)
    val pullNextPageVisualOffsetPx: Float
        get() = pullNextPageVisualOffsetPxState.value
    val pullNextPageProgress: Float
        get() = (pullNextPageVisualOffsetPx / pullNextPageMaxLiftPx).coerceIn(0f, 1f)
    val pullContentOffsetTargetPx: Float
        get() = (
            if (pullToRefreshState.isRefreshing) {
                pullRefreshSettleDistancePx
            } else {
                searchRubberBandOffset(
                    dragPx = pullToRefreshState.verticalOffset,
                    triggerPx = pullToRefreshState.positionalThreshold
                        .takeIf { it > 0f }
                        ?: pullRefreshSettleDistancePx,
                    maxOffsetPx = pullRefreshMaxDistancePx,
                    followRatio = SearchPullRefreshFollowRatio
                )
            }
            ).coerceIn(
            minimumValue = 0f,
            maximumValue = pullRefreshMaxDistancePx
        )
    internal var pullContentOffsetPxState: State<Float> = mutableStateOf(0f)
    val pullContentOffsetPx: Float
        get() = pullContentOffsetPxState.value
    val pullRefreshProgress: Float
        get() = if (pullToRefreshState.isRefreshing) {
            1f
        } else {
            val threshold = pullToRefreshState.positionalThreshold
                .takeIf { it > 0f }
                ?: pullRefreshSettleDistancePx
            (pullContentOffsetPx / threshold).coerceIn(0f, 1f)
        }
    val pullRefreshArmed: Boolean
        get() = pullToRefreshState.progress >= 1f
    val pullRefreshHintVisible: Boolean
        get() = pullContentOffsetPx > 1f || pullToRefreshState.isRefreshing
    val pullNextPageHintVisible: Boolean
        get() = pullNextPageVisualOffsetPx > 1f
    val listStretchOffsetPx: Float
        get() = pullContentOffsetPx - pullNextPageVisualOffsetPx
    val pullRefreshHintHeightPx: Float
        get() = pullContentOffsetPx.coerceIn(0f, pullActionHintHeightPx)
    val pullRefreshHintEdgeOffsetPx: Float
        get() = topPaddingPx + pullContentOffsetPx - pullRefreshHintHeightPx

    fun reportScrollLock(locked: Boolean) {
        onScrollLockChanged(locked)
    }

    fun finishPullNextPageGesture() {
        if (
            pullNextPageReturnInProgress &&
                !pullNextPageGestureActive &&
                pullNextPageDragPx <= 0f
        ) {
            return
        }
        val hasPullOffset = pullNextPageDragPx > 0f
        val shouldTrigger =
            hasPullOffset &&
            isPullNextPageGestureEnabled &&
                pullNextPageDragPx >= pullNextPageTriggerDistancePx
        pullNextPageGestureActive = false
        pullNextPageRequestAfterReturn = shouldTrigger
        pullNextPageReturnInProgress = hasPullOffset
        pullNextPageDragPx = 0f
    }

    fun dragModifier(resultScrollKey: String, viewMode: Int): Modifier = Modifier
        .pointerInput(resultScrollKey, viewMode) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                searchPointerPressed = true
                var trackedPointerId = down.id
                var previousPosition = down.position
                var dragFromDown = Offset.Zero
                var pullNextGestureActive = false
                var horizontalGestureActive = false
                val touchSlop = viewConfiguration.touchSlop
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change =
                        event.changes.firstOrNull { it.id == trackedPointerId }
                            ?: event.changes.firstOrNull()
                    if (change != null) {
                        trackedPointerId = change.id
                        val positionDelta = change.position - previousPosition
                        previousPosition = change.position
                        if (!pullNextGestureActive && !horizontalGestureActive) {
                            dragFromDown += positionDelta
                            val isPastTouchSlop = dragFromDown.getDistance() > touchSlop
                            if (isPastTouchSlop) {
                                horizontalGestureActive =
                                    dragFromDown.x.absoluteValue >=
                                        dragFromDown.y.absoluteValue * SearchPullNextPageVerticalBias
                                pullNextGestureActive =
                                    !horizontalGestureActive &&
                                        dragFromDown.y < 0f &&
                                        dragFromDown.y.absoluteValue >=
                                        dragFromDown.x.absoluteValue * SearchPullNextPageVerticalBias &&
                                        isAtBottom &&
                                        isPullNextPageGestureEnabled
                                if (pullNextGestureActive) {
                                    pullNextPageGestureActive = true
                                    reportScrollLock(true)
                                }
                            }
                        }
                        val deltaY = positionDelta.y
                        when {
                            horizontalGestureActive -> Unit

                            !isPullNextPageGestureEnabled -> {
                                if (pullNextPageDragPx != 0f) {
                                    pullNextPageDragPx = 0f
                                }
                                pullNextPageGestureActive = false
                            }

                            deltaY < 0f && isAtBottom && pullNextGestureActive -> {
                                val delta = (-deltaY) * SearchPullNextPageDragResistance
                                pullNextPageDragPx =
                                    (pullNextPageDragPx + delta)
                                        .coerceIn(0f, pullNextPageMaxDistancePx)
                                reportScrollLock(true)
                                change.consume()
                            }

                            deltaY > 0f && (pullNextPageDragPx > 0f || pullNextGestureActive) -> {
                                pullNextPageDragPx =
                                    (pullNextPageDragPx - deltaY).coerceAtLeast(0f)
                                if (pullNextPageDragPx == 0f) {
                                    pullNextGestureActive = false
                                    pullNextPageGestureActive = false
                                    reportScrollLock(false)
                                }
                                change.consume()
                            }

                            pullNextGestureActive -> {
                                change.consume()
                            }

                            !isAtBottom && pullNextPageDragPx > 0f -> {
                                pullNextPageDragPx = 0f
                                pullNextPageGestureActive = false
                            }
                        }
                    }
                } while (event.changes.any { it.pressed })
                searchPointerPressed = false
                finishPullNextPageGesture()
            }
        }
        .then(
            if (!pullToRefreshState.isRefreshing) {
                Modifier.nestedScroll(pullToRefreshState.nestedScrollConnection)
            } else {
                Modifier
            }
        )
        .clipToBounds()
}

@Composable
internal fun rememberSearchPullGestureState(
    resultScrollKey: String,
    viewMode: Int,
    listState: LazyListState,
    gridState: LazyStaggeredGridState,
    pullNextPageEnabledBase: Boolean,
    topPadding: Dp,
    requestNextPage: () -> Unit,
    onHorizontalPagerScrollLockChanged: (Boolean) -> Unit
): SearchPullGestureState {
    val pullToRefreshState = rememberPullToRefreshState()
    val state = remember(resultScrollKey, viewMode) { SearchPullGestureState(pullToRefreshState) }
    val density = LocalDensity.current
    state.listState = listState
    state.gridState = gridState
    state.currentViewMode = viewMode
    state.pullNextPageEnabledBase = pullNextPageEnabledBase
    state.topPaddingPx = with(density) { topPadding.toPx() }
    state.requestNextPage = requestNextPage
    state.onScrollLockChanged = onHorizontalPagerScrollLockChanged
    state.pullNextPageTriggerDistancePx = with(density) { SearchPullNextPageTriggerDistance.toPx() }
    state.pullNextPageMaxDistancePx = with(density) { SearchPullNextPageMaxDistance.toPx() }
    state.pullNextPageMaxLiftPx = with(density) { SearchPullNextPageMaxLift.toPx() }
    state.pullActionHintHeightPx = with(density) { SearchPullActionHintHeight.toPx() }
    state.pullRefreshSettleDistancePx = with(density) { SearchPullRefreshSettleDistance.toPx() }
    state.pullRefreshMaxDistancePx = with(density) { SearchPullRefreshMaxDistance.toPx() }

    state.pullNextPageVisualOffsetPxState = animateFloatAsState(
        targetValue = state.pullNextPageVisualTargetPx,
        animationSpec = if (state.pullNextPageGestureActive) {
            snap()
        } else {
            SearchPullNextPageReturnSpring
        },
        finishedListener = { settledOffset ->
            // 翻页请求必须等待回落动画完整结束，避免松手瞬间跳页。
            if (settledOffset <= 0.5f && state.pullNextPageReturnInProgress) {
                val shouldRequestNextPage = state.pullNextPageRequestAfterReturn
                state.pullNextPageRequestAfterReturn = false
                state.pullNextPageReturnInProgress = false
                if (shouldRequestNextPage) {
                    state.requestNextPage()
                }
            }
        },
        label = "searchPullNextPageOffset"
    )
    state.pullContentOffsetPxState = animateFloatAsState(
        targetValue = state.pullContentOffsetTargetPx,
        animationSpec = if (
            state.searchPointerPressed &&
                pullToRefreshState.progress > 0f &&
                !pullToRefreshState.isRefreshing
        ) {
            snap()
        } else {
            spring(
                dampingRatio = 0.72f,
                stiffness = Spring.StiffnessMediumLow
            )
        },
        label = "searchPullContentOffset"
    )

    // —— 纯手势副作用（自 SearchScreenContent 随迁）——
    LaunchedEffect(resultScrollKey, state.isPullNextPageEnabled) {
        if (!state.isPullNextPageEnabled) {
            state.pullNextPageDragPx = 0f
            state.pullNextPageGestureActive = false
            state.pullNextPageRequestAfterReturn = false
            state.pullNextPageReturnInProgress = false
            state.reportScrollLock(false)
        }
    }
    LaunchedEffect(
        state.pullNextPageDragPx > 0f,
        state.pullNextPageGestureActive,
        state.pullNextPageReturnInProgress
    ) {
        state.reportScrollLock(
            state.pullNextPageDragPx > 0f ||
                state.pullNextPageGestureActive ||
                state.pullNextPageReturnInProgress
        )
    }
    LaunchedEffect(Unit) {
        try {
            awaitCancellation()
        } finally {
            state.reportScrollLock(false)
        }
    }
    return state
}

@Composable
internal fun SearchPullRefreshHintOverlay(
    state: SearchPullGestureState,
    modifier: Modifier = Modifier
) {
    if (!state.pullRefreshHintVisible) return
    val density = LocalDensity.current
    SearchPullActionHint(
        progress = state.pullRefreshProgress,
        active = state.pullToRefreshState.isRefreshing,
        armed = state.pullRefreshArmed,
        direction = if (state.pullRefreshArmed) {
            SearchPullActionDirection.Up
        } else {
            SearchPullActionDirection.Down
        },
        idleText = "下拉刷新",
        armedText = "松手刷新",
        activeText = "正在刷新",
        height = with(density) { state.pullRefreshHintHeightPx.toDp() },
        modifier = modifier
            .graphicsLayer {
                alpha = state.pullRefreshProgress.coerceIn(0f, 1f)
                translationY = state.pullRefreshHintEdgeOffsetPx
            }
    )
}

@Composable
internal fun SearchPullNextPageHintOverlay(
    state: SearchPullGestureState,
    modifier: Modifier = Modifier
) {
    if (!state.pullNextPageHintVisible) return
    val density = LocalDensity.current
    Box(
        modifier = modifier
            .padding(bottom = LocalBottomOverlayPadding.current)
            .fillMaxWidth()
            .height(with(density) { state.pullNextPageVisualOffsetPx.toDp() })
            .clipToBounds()
            .graphicsLayer {
                alpha = state.pullNextPageProgress.coerceIn(0f, 1f)
            },
        contentAlignment = Alignment.Center
    ) {
        SearchPullActionHint(
            progress = state.pullNextPageProgress,
            active = state.pullNextPageRequestAfterReturn,
            armed = state.pullNextPageArmed,
            direction = if (state.pullNextPageArmed) {
                SearchPullActionDirection.Down
            } else {
                SearchPullActionDirection.Up
            },
            idleText = "上拉下一页",
            armedText = "松手翻页",
            activeText = "正在翻页"
        )
    }
}

private enum class SearchPullActionDirection {
    Down,
    Up
}

@Composable
private fun SearchPullActionHint(
    progress: Float,
    active: Boolean,
    armed: Boolean,
    direction: SearchPullActionDirection,
    idleText: String,
    armedText: String,
    activeText: String,
    height: Dp = SearchPullActionHintHeight,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val resolvedProgress = progress.coerceIn(0f, 1f)
    val iconScale by animateFloatAsState(
        targetValue = if (armed || active) 1.08f else 0.88f + resolvedProgress * 0.12f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "search_pull_action_icon_scale"
    )
    val tint = if (armed || active) colorScheme.primary else colorScheme.textSecondary
    val label = when {
        active -> activeText
        armed -> armedText
        else -> idleText
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (active) {
                EaraLogoLoadingIndicator(
                    size = 18.dp,
                    tint = colorScheme.primary,
                    glowColor = colorScheme.primarySoft,
                    showGlow = false
                )
            } else {
                val icon = when (direction) {
                    SearchPullActionDirection.Down -> Icons.Rounded.KeyboardArrowDown
                    SearchPullActionDirection.Up -> Icons.Rounded.KeyboardArrowUp
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        }
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = tint
            )
        }
    }
}
