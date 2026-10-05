package com.asmr.player.ui.library.albumdetail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.asmr.player.ui.common.cover.AsmrShimmerPlaceholder
import com.asmr.player.ui.theme.AsmrTheme

@Composable
internal fun DlsiteRecommendationsLoadingBlocks() {
    val placeholders = remember { listOf(0, 1, 2) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AlbumDetailHorizontalPadding, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        placeholders.forEach { sectionIndex ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DlsiteStaticPlaceholderLine(
                    widthFraction = when (sectionIndex) {
                        0 -> 0.34f
                        1 -> 0.28f
                        else -> 0.52f
                    },
                    height = 18.dp
                )
                DlsiteRecommendationLoadingCards()
            }
        }
    }
}

@Composable
internal fun DlsiteRecommendationLoadingCards() {
    val placeholders = remember { listOf(0, 1, 2, 3) }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(
            items = placeholders,
            key = { it },
            contentType = { "dlsiteRecommendationLoadingCard" }
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 1.dp,
                color = AsmrTheme.colorScheme.surface.copy(alpha = 0.35f),
                modifier = Modifier.width(132.dp)
            ) {
                Column(
                    modifier = Modifier.padding(bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AsmrShimmerPlaceholder(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        cornerRadius = 14,
                        animateHighlight = false,
                    )
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DlsiteStaticPlaceholderLine(widthFraction = 0.88f, height = 12.dp)
                        DlsiteStaticPlaceholderLine(widthFraction = 0.46f, height = 10.dp)
                    }
                }
            }
        }
    }
}

private val DlsiteSectionPlacementTweenSpec = tween<IntOffset>(
    durationMillis = 280,
    easing = FastOutSlowInEasing
)

private const val DirectoryTreeRevealFadeInMillis = 800

internal enum class DirectoryTreePanelState {
    Loading,
    Content,
    Empty,
    MissingRj
}

internal enum class DlsiteContentKind {
    Loading,
    Content,
    Empty
}

internal data class DlsiteContentPanel<T>(
    val kind: DlsiteContentKind,
    val value: T? = null
)

@Stable
internal class DlsiteContentFadeState<T>(
    initialPanel: DlsiteContentPanel<T>,
    private val fadeInMillis: Int = 180
) {
    var panel by mutableStateOf(initialPanel)
        private set

    val alpha = Animatable(1f)

    suspend fun update(
        targetPanel: DlsiteContentPanel<T>,
        showLoadingImmediately: Boolean = false
    ) {
        if (showLoadingImmediately && targetPanel.kind == DlsiteContentKind.Loading) {
            panel = targetPanel
            alpha.snapTo(1f)
            return
        }
        if (panel.kind != targetPanel.kind) {
            alpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 90)
            )
        }
        panel = targetPanel
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = fadeInMillis, easing = FastOutSlowInEasing)
        )
    }
}

@Composable
internal fun <T> rememberDlsiteContentFadeState(
    targetPanel: DlsiteContentPanel<T>,
    stateKey: Any,
    fadeInMillis: Int = 180,
    showLoadingImmediately: Boolean = false
): DlsiteContentFadeState<T> {
    val state = remember(stateKey) { DlsiteContentFadeState(targetPanel, fadeInMillis) }
    LaunchedEffect(state, targetPanel, showLoadingImmediately) {
        state.update(
            targetPanel = targetPanel,
            showLoadingImmediately = showLoadingImmediately
        )
    }
    return state
}

internal fun <T> Modifier.dlsiteContentFade(state: DlsiteContentFadeState<T>): Modifier {
    return graphicsLayer {
        alpha = state.alpha.value
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }
}

@OptIn(ExperimentalFoundationApi::class)
internal fun LazyItemScope.dlsiteAnimatedSectionModifier(
    modifier: Modifier = Modifier,
    animateIntro: Boolean = true
): Modifier {
    if (!animateIntro) return modifier
    return modifier.animateItem(
        fadeInSpec = null,
        placementSpec = DlsiteSectionPlacementTweenSpec,
        fadeOutSpec = null,
    )
}

@Composable
internal fun StableOneDirectoryTreeContent(
    targetState: DirectoryTreePanelState,
    stateKey: Any,
    modifier: Modifier = Modifier,
    content: @Composable (DirectoryTreePanelState) -> Unit
) {
    val targetPanel = DlsiteContentPanel(
        kind = when (targetState) {
            DirectoryTreePanelState.Loading -> DlsiteContentKind.Loading
            DirectoryTreePanelState.Content -> DlsiteContentKind.Content
            DirectoryTreePanelState.Empty,
            DirectoryTreePanelState.MissingRj -> DlsiteContentKind.Empty
        },
        value = targetState
    )
    val fadeState = rememberDlsiteContentFadeState(
        targetPanel = targetPanel,
        stateKey = stateKey,
        fadeInMillis = DirectoryTreeRevealFadeInMillis,
        showLoadingImmediately = true
    )
    val displayedState = if (targetState == DirectoryTreePanelState.Loading) {
        DirectoryTreePanelState.Loading
    } else {
        fadeState.panel.value ?: targetState
    }
    Box(
        modifier = modifier
            .height(rememberStableOneDirectoryContainerHeight())
            .clipToBounds()
            .dlsiteContentFade(fadeState)
    ) {
        content(displayedState)
    }
}

@Composable
internal fun DirectoryTreeAnimatedContent(
    targetState: DirectoryTreePanelState,
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable (DirectoryTreePanelState) -> Unit
) {
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            (
                fadeIn(animationSpec = tween(durationMillis = DirectoryTreeRevealFadeInMillis, delayMillis = 60)) +
                    slideInVertically(
                        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                        initialOffsetY = { height -> (height * 0.06f).toInt() }
                    )
                ).togetherWith(
                fadeOut(animationSpec = tween(durationMillis = 120)) +
                    slideOutVertically(
                        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
                        targetOffsetY = { height -> -(height * 0.03f).toInt() }
                    )
            ).using(SizeTransform(clip = false))
        },
        label = label,
        content = { state -> content(state) }
    )
}

internal fun shouldShowAsmrOneDirectoryLoading(
    isAwaitingAsmrOneLoad: Boolean,
    hasResolvedAsmrOneContent: Boolean,
    isLoadingAsmrOne: Boolean,
    hasAsmrOneTree: Boolean,
    hasDirectoryBrowser: Boolean
): Boolean {
    if (hasDirectoryBrowser && hasAsmrOneTree) return false
    return isAwaitingAsmrOneLoad ||
        !hasResolvedAsmrOneContent ||
        isLoadingAsmrOne ||
        hasAsmrOneTree
}

internal fun shouldShowDlsitePlayDirectoryLoading(
    isAwaitingInitialTarget: Boolean,
    hasResolvedDlsitePlayContent: Boolean,
    isLoadingDlsitePlay: Boolean,
    hasDlsitePlayTree: Boolean,
    hasDirectoryBrowser: Boolean
): Boolean {
    return !hasDirectoryBrowser && (
        isAwaitingInitialTarget ||
            !hasResolvedDlsitePlayContent ||
            isLoadingDlsitePlay ||
            hasDlsitePlayTree
        )
}