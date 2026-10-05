package com.asmr.player.ui.downloads

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.asmr.player.ui.theme.AsmrTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

internal val SwipeActionButtonWidth = 56.dp
private val SwipeActionHeaderHeight = 68.dp
private val SwipeRevealSpringSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow
)

private enum class RevealAnchor { Closed, Open }

internal class SwipeRevealCloseController {
    private var owner: Any? = null
    private var closeAction: (() -> Unit)? = null

    fun register(owner: Any, closeAction: () -> Unit) {
        this.owner = owner
        this.closeAction = closeAction
    }

    fun unregister(owner: Any) {
        if (this.owner === owner) {
            this.owner = null
            closeAction = null
        }
    }

    fun requestClose() {
        closeAction?.invoke()
    }
}

/**
 * Swipe-left-to-reveal container for task-level (work number) cards. The trailing
 * [actions] are hidden behind the card by default; the card translates left while
 * dragging, exposing the action buttons. The [revealed] flag is the single source
 * of truth owned by the caller (so only one card is open at a time), and settles
 * are reported back through [onRevealedChange].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SwipeRevealActionsBox(
    modifier: Modifier = Modifier,
    revealed: Boolean,
    enabled: Boolean,
    closeController: SwipeRevealCloseController,
    onRevealedBoundsChanged: (Rect) -> Unit,
    onRevealedChange: (Boolean) -> Unit,
    actionWidth: Dp,
    actions: (@Composable RowScope.() -> Unit)?,
    content: @Composable () -> Unit
) {
    if (actions == null || actionWidth <= 0.dp) {
        Box(modifier = modifier) { content() }
        return
    }

    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val closeControllerOwner = remember { Any() }
    val currentOnRevealedChange by rememberUpdatedState(onRevealedChange)
    val currentOnRevealedBoundsChanged by rememberUpdatedState(onRevealedBoundsChanged)
    val actionWidthPx = with(density) { actionWidth.toPx() }
    val state = remember(actionWidthPx) {
        AnchoredDraggableState(
            initialValue = RevealAnchor.Closed,
            anchors = DraggableAnchors {
                RevealAnchor.Closed at 0f
                RevealAnchor.Open at -actionWidthPx
            },
            positionalThreshold = { distance -> distance * 0.35f },
            velocityThreshold = { with(density) { 125.dp.toPx() } },
            snapAnimationSpec = SwipeRevealSpringSpec,
            decayAnimationSpec = exponentialDecay()
        )
    }
    var internallyReportedRevealed by remember(state) { mutableStateOf(false) }
    LaunchedEffect(revealed, enabled, state) {
        val target = if (revealed && enabled) RevealAnchor.Open else RevealAnchor.Closed
        if (!enabled || revealed != internallyReportedRevealed || state.targetValue != target) {
            if (target == RevealAnchor.Closed) {
                state.animateToFromRest(target)
            } else {
                state.animateTo(target)
            }
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.currentValue == RevealAnchor.Open }
            .distinctUntilChanged()
            .collect { open ->
                internallyReportedRevealed = open
                currentOnRevealedChange(open)
            }
    }
    val interceptContentTap by remember(state, revealed, enabled) {
        derivedStateOf {
            enabled && (revealed || state.requireOffset() < 0f)
        }
    }
    val closeFromRest = remember(state, coroutineScope) {
        {
            coroutineScope.launch {
                state.animateToFromRest(RevealAnchor.Closed)
            }
            Unit
        }
    }
    SideEffect {
        if (revealed && enabled) {
            closeController.register(closeControllerOwner, closeFromRest)
        } else {
            closeController.unregister(closeControllerOwner)
        }
    }
    DisposableEffect(closeController, closeControllerOwner) {
        onDispose { closeController.unregister(closeControllerOwner) }
    }

    val colors = AsmrTheme.colorScheme
    var boundsInRoot by remember { mutableStateOf<Rect?>(null) }
    LaunchedEffect(revealed, boundsInRoot) {
        if (revealed) {
            boundsInRoot?.let(currentOnRevealedBoundsChanged)
        }
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .onGloballyPositioned { coordinates ->
                val currentBounds = coordinates.boundsInRoot()
                boundsInRoot = currentBounds
                if (revealed) {
                    currentOnRevealedBoundsChanged(currentBounds)
                }
            }
    ) {
        // 操作列只占用卡片 header 的高度，展开详情时不会被拉长。
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .width(actionWidth)
                .height(SwipeActionHeaderHeight)
                .clip(RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            actions()
        }
        // Foreground card content. The opaque background keeps the
        // semi-transparent card from ghosting the buttons underneath.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(state.requireOffset().roundToInt(), 0) }
                .background(colors.background)
                .then(
                    if (enabled) {
                        Modifier.anchoredDraggable(
                            state = state,
                            orientation = Orientation.Horizontal,
                            startDragImmediately = false
                        )
                    } else {
                        Modifier.pointerInput(Unit) {
                            detectHorizontalDragGestures { change, _ -> change.consume() }
                        }
                    }
                )
        ) {
            content()
        }
        // 收起动画完全归零前持续拦截卡片点击，避免快速再次点击穿透并展开详情。
        if (interceptContentTap) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(SwipeActionHeaderHeight)
                    .padding(end = actionWidth)
                    .anchoredDraggable(
                        state = state,
                        orientation = Orientation.Horizontal,
                        startDragImmediately = false
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        closeFromRest()
                    }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private suspend fun AnchoredDraggableState<RevealAnchor>.animateToFromRest(
    target: RevealAnchor
) {
    anchoredDrag(targetValue = target) { anchors, latestTarget ->
        val targetOffset = anchors.positionOf(latestTarget)
        if (targetOffset.isNaN()) return@anchoredDrag

        animate(
            initialValue = requireOffset(),
            targetValue = targetOffset,
            initialVelocity = 0f,
            animationSpec = SwipeRevealSpringSpec
        ) { value, velocity ->
            dragTo(value, velocity)
        }
    }
}

@Composable
internal fun RowScope.SwipeRevealAction(
    backgroundColor: Color,
    tint: Color,
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .background(backgroundColor.copy(alpha = if (enabled) 1f else 0.45f))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.55f),
            modifier = Modifier.size(20.dp)
        )
    }
}
