@file:OptIn(ExperimentalFoundationApi::class)

package com.asmr.player.ui.common.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import kotlin.math.abs
import kotlinx.coroutines.CancellationException

private const val MaxAnimatedScrollItems = 10

suspend fun LazyListState.smoothScrollToIndex(
    index: Int,
    anchorOffsetPx: Int = 0,
    maxAnimatedItems: Int = MaxAnimatedScrollItems
) {
    if (index < 0) return
    if (firstVisibleItemIndex == index && firstVisibleItemScrollOffset == anchorOffsetPx) return

    try {
        jumpNearTargetIfNeeded(
            targetIndex = index,
            anchorOffsetPx = anchorOffsetPx,
            maxAnimatedItems = maxAnimatedItems.coerceAtLeast(1)
        )
        animateScrollToItem(index, anchorOffsetPx)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        runCatching { scrollToItem(index, anchorOffsetPx) }
    }
}

suspend fun LazyListState.smoothScrollToTop(anchorOffsetPx: Int = 0) {
    smoothScrollToIndex(index = 0, anchorOffsetPx = anchorOffsetPx)
}

suspend fun LazyStaggeredGridState.smoothScrollToIndex(
    index: Int,
    anchorOffsetPx: Int = 0,
    maxAnimatedItems: Int = MaxAnimatedScrollItems
) {
    if (index < 0) return
    if (firstVisibleItemIndex == index && firstVisibleItemScrollOffset == anchorOffsetPx) return

    try {
        jumpNearTargetIfNeeded(
            targetIndex = index,
            anchorOffsetPx = anchorOffsetPx,
            maxAnimatedItems = maxAnimatedItems.coerceAtLeast(1)
        )
        animateScrollToItem(index, anchorOffsetPx)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        runCatching { scrollToItem(index, anchorOffsetPx) }
    }
}

suspend fun LazyStaggeredGridState.smoothScrollToTop(anchorOffsetPx: Int = 0) {
    smoothScrollToIndex(index = 0, anchorOffsetPx = anchorOffsetPx)
}

private suspend fun LazyListState.jumpNearTargetIfNeeded(
    targetIndex: Int,
    anchorOffsetPx: Int,
    maxAnimatedItems: Int
) {
    val currentIndex = firstVisibleItemIndex
    if (abs(currentIndex - targetIndex) <= maxAnimatedItems) return
    scrollToItem(currentIndex.nearTarget(targetIndex, maxAnimatedItems), anchorOffsetPx)
}

private suspend fun LazyStaggeredGridState.jumpNearTargetIfNeeded(
    targetIndex: Int,
    anchorOffsetPx: Int,
    maxAnimatedItems: Int
) {
    val currentIndex = firstVisibleItemIndex
    if (abs(currentIndex - targetIndex) <= maxAnimatedItems) return
    scrollToItem(currentIndex.nearTarget(targetIndex, maxAnimatedItems), anchorOffsetPx)
}

private fun Int.nearTarget(targetIndex: Int, maxAnimatedItems: Int): Int {
    return if (this > targetIndex) {
        targetIndex + maxAnimatedItems
    } else {
        (targetIndex - maxAnimatedItems).coerceAtLeast(this)
    }
}
