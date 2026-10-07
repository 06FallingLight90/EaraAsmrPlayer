package com.asmr.player.ui.search

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed as lazyItemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.asmr.player.domain.model.Album
import com.asmr.player.ui.common.cover.EaraLogoLoadingIndicator
import com.asmr.player.ui.common.cover.LazyListPreloader
import com.asmr.player.ui.common.cover.LazyStaggeredGridPreloader
import com.asmr.player.ui.common.cover.albumCoverImageModel
import com.asmr.player.ui.common.cover.albumStableKey
import com.asmr.player.ui.common.cover.rememberAppImageCacheManager
import com.asmr.player.ui.common.cover.shouldFadeInCover
import com.asmr.player.ui.common.list.LocalBottomOverlayPadding
import com.asmr.player.ui.common.list.rememberCalmScrollableFlingBehavior
import com.asmr.player.ui.common.list.withAddedBottomPadding
import com.asmr.player.ui.common.status.EaraBrandedEmptyState
import com.asmr.player.ui.library.AlbumGridItem
import com.asmr.player.ui.library.AlbumGridItemSpacing
import com.asmr.player.ui.library.AlbumItem
import com.asmr.player.ui.theme.AsmrTheme

private val SearchResultPlacementSpring = spring<IntOffset>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow
)

private fun searchResultItemKey(album: Album): String {
    return "search-result:${albumStableKey(album)}"
}

private fun onlineDetailLoadingFor(album: Album, state: SearchUiState.Success): Boolean {
    if (state.collectedOnly && !state.purchasedOnly) {
        val workId = album.asmrOneWorkId ?: return false
        return workId in state.resolvingCollectedWorkIds
    }
    if (!state.isEnriching || state.purchasedOnly) return false
    val rj = album.rjCode.ifBlank { album.workId }.trim().uppercase()
    return rj.isNotBlank() && rj in state.enrichingRjCodes
}

internal enum class SearchResultSkeletonMode {
    None,
    DetailMetadata,
    LocalizedText
}

internal fun searchResultSkeletonMode(
    onlineDetailLoading: Boolean,
    isRefreshingLocalizedText: Boolean
): SearchResultSkeletonMode {
    if (!onlineDetailLoading) return SearchResultSkeletonMode.None
    return if (isRefreshingLocalizedText) {
        SearchResultSkeletonMode.LocalizedText
    } else {
        SearchResultSkeletonMode.DetailMetadata
    }
}

@Composable
internal fun SearchResultsContent(
    state: SearchUiState,
    topPadding: Dp,
    viewMode: Int,
    isActive: Boolean,
    isCompact: Boolean,
    listState: LazyListState,
    gridState: LazyStaggeredGridState,
    chromeNestedScrollConnection: NestedScrollConnection,
    onAlbumClick: (Album, Boolean, Boolean) -> Unit,
    onMetaAction: (String) -> Unit,
    onRetry: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    when (state) {
        is SearchUiState.Loading -> Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(
                    state = rememberScrollState(),
                    flingBehavior = rememberCalmScrollableFlingBehavior()
                )
                .padding(top = topPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            EaraLogoLoadingIndicator(tint = colorScheme.primary)
        }

        is SearchUiState.Success -> {
            if (state.results.isEmpty()) {
                EaraBrandedEmptyState(
                    sectionTitle = "在线搜索",
                    headline = if (state.keyword.isBlank()) "还没有搜索结果" else "没有找到匹配结果",
                    sectionIcon = Icons.Rounded.Search,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = topPadding,
                        bottom = LocalBottomOverlayPadding.current + 24.dp
                    )
                )
            } else if (viewMode == 0) {
                SearchResultListContent(
                    state = state,
                    topPadding = topPadding,
                    isActive = isActive,
                    listState = listState,
                    nestedScrollConnection = chromeNestedScrollConnection,
                    onAlbumClick = onAlbumClick,
                    onMetaAction = onMetaAction
                )
            } else {
                SearchResultGridContent(
                    state = state,
                    topPadding = topPadding,
                    isActive = isActive,
                    isCompact = isCompact,
                    gridState = gridState,
                    nestedScrollConnection = chromeNestedScrollConnection,
                    onAlbumClick = onAlbumClick,
                    onMetaAction = onMetaAction
                )
            }
        }

        is SearchUiState.Error -> EaraBrandedEmptyState(
            sectionTitle = "在线搜索",
            headline = "网络连接出了点问题",
            sectionIcon = Icons.Rounded.WifiOff,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = topPadding,
                bottom = LocalBottomOverlayPadding.current + 24.dp
            ),
            footer = {
                FilledTonalButton(
                    onClick = onRetry,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = colorScheme.primaryContainer,
                        contentColor = colorScheme.onPrimaryContainer
                    )
                ) {
                    Text("重试")
                }
            }
        )

        else -> Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(
                    state = rememberScrollState(),
                    flingBehavior = rememberCalmScrollableFlingBehavior()
                )
        ) {}
    }
}

@Composable
private fun SearchResultListContent(
    state: SearchUiState.Success,
    topPadding: Dp,
    isActive: Boolean,
    listState: LazyListState,
    nestedScrollConnection: NestedScrollConnection,
    onAlbumClick: (Album, Boolean, Boolean) -> Unit,
    onMetaAction: (String) -> Unit
) {
    val cacheManager = rememberAppImageCacheManager()
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val listItemHeight = (screenWidthDp.dp * 0.24f).coerceIn(112.dp, 140.dp)
    val coverPx = remember(listItemHeight, density) { with(density) { listItemHeight.roundToPx() } }
    val preloadSize = remember(coverPx) { IntSize(coverPx, coverPx) }
    val coverFadeInState = remember(listState) {
        derivedStateOf {
            shouldFadeInCover(listState.isScrollInProgress)
        }
    }
    LazyListPreloader(
        state = listState,
        itemCount = state.results.size,
        enabled = isActive,
        preloadNext = 24,
        preloadSize = preloadSize,
        cacheManagerProvider = { cacheManager },
        modelAt = { idx ->
            state.results.getOrNull(idx)?.let { albumCoverImageModel(it) }
        }
    )
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
        flingBehavior = rememberCalmScrollableFlingBehavior(),
        contentPadding = PaddingValues(top = topPadding, bottom = 8.dp)
            .withAddedBottomPadding(LocalBottomOverlayPadding.current)
    ) {
        lazyItemsIndexed(
            items = state.results,
            key = { _, album -> searchResultItemKey(album) },
            contentType = { _, _ -> "album" }
        ) { _, album ->
            val onlineDetailLoading = onlineDetailLoadingFor(album, state)
            val skeletonMode = searchResultSkeletonMode(
                onlineDetailLoading = onlineDetailLoading,
                isRefreshingLocalizedText = state.isRefreshingLocalizedText
            )
            val rj = album.rjCode.ifBlank { album.workId }.trim().uppercase()
            val hasResolvedDetail = rj.isNotBlank() && rj in state.enrichedDetailRjCodes
            AlbumItem(
                album = album,
                onClick = { onAlbumClick(album, state.purchasedOnly, hasResolvedDetail) },
                modifier = Modifier.animateItem(
                    fadeInSpec = null,
                    placementSpec = SearchResultPlacementSpring,
                    fadeOutSpec = null,
                ),
                onlineDetailLoading =
                    skeletonMode == SearchResultSkeletonMode.DetailMetadata,
                onlineTitleLoading =
                    skeletonMode == SearchResultSkeletonMode.LocalizedText,
                onlineCvLoading =
                    skeletonMode == SearchResultSkeletonMode.DetailMetadata,
                onlineTagsLoading = skeletonMode != SearchResultSkeletonMode.None,
                showCollectedIndicator = !state.collectedOnly,
                showStatsPlaceholders = true,
                coverFadeInState = coverFadeInState,
                coverReloadKey = state.resultRevision,
                onRjLongClick = onMetaAction,
                onCircleLongClick = onMetaAction,
                onCvLongClick = onMetaAction,
                onTagLongClick = onMetaAction,
            )
        }
    }
}

@Composable
private fun SearchResultGridContent(
    state: SearchUiState.Success,
    topPadding: Dp,
    isActive: Boolean,
    isCompact: Boolean,
    gridState: LazyStaggeredGridState,
    nestedScrollConnection: NestedScrollConnection,
    onAlbumClick: (Album, Boolean, Boolean) -> Unit,
    onMetaAction: (String) -> Unit
) {
    val cacheManager = rememberAppImageCacheManager()
    val density = LocalDensity.current
    val gridCellSize = if (isCompact) 150.dp else 200.dp
    val gridCoverPx = remember(gridCellSize, density) { with(density) { gridCellSize.roundToPx() } }
    val gridPreloadSize = remember(gridCoverPx) { IntSize(gridCoverPx, gridCoverPx) }
    val coverFadeInState = remember(gridState) {
        derivedStateOf {
            shouldFadeInCover(gridState.isScrollInProgress)
        }
    }
    LazyStaggeredGridPreloader(
        state = gridState,
        itemCount = state.results.size,
        enabled = isActive,
        preloadNext = 24,
        preloadSize = gridPreloadSize,
        cacheManagerProvider = { cacheManager },
        modelAt = { idx ->
            state.results.getOrNull(idx)?.let { albumCoverImageModel(it) }
        }
    )
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(gridCellSize),
        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
        flingBehavior = rememberCalmScrollableFlingBehavior(),
        contentPadding = PaddingValues(
            top = topPadding,
            start = SearchPageHorizontalPadding,
            end = SearchPageHorizontalPadding,
            bottom = 16.dp
        ).withAddedBottomPadding(LocalBottomOverlayPadding.current),
        horizontalArrangement = Arrangement.spacedBy(AlbumGridItemSpacing),
        verticalItemSpacing = AlbumGridItemSpacing
    ) {
        items(
            state.results.size,
            key = { index -> searchResultItemKey(state.results[index]) },
            contentType = { "albumGrid" }
        ) { index ->
            val album = state.results[index]
            val onlineDetailLoading = onlineDetailLoadingFor(album, state)
            val skeletonMode = searchResultSkeletonMode(
                onlineDetailLoading = onlineDetailLoading,
                isRefreshingLocalizedText = state.isRefreshingLocalizedText
            )
            val rj = album.rjCode.ifBlank { album.workId }.trim().uppercase()
            val hasResolvedDetail = rj.isNotBlank() && rj in state.enrichedDetailRjCodes
            AlbumGridItem(
                album = album,
                onClick = { onAlbumClick(album, state.purchasedOnly, hasResolvedDetail) },
                modifier = Modifier.animateItem(
                    fadeInSpec = null,
                    placementSpec = SearchResultPlacementSpring,
                    fadeOutSpec = null,
                ),
                onlineDetailLoading =
                    skeletonMode == SearchResultSkeletonMode.DetailMetadata,
                onlineTitleLoading =
                    skeletonMode == SearchResultSkeletonMode.LocalizedText,
                onlineCvLoading =
                    skeletonMode == SearchResultSkeletonMode.DetailMetadata,
                onlineTagsLoading = skeletonMode != SearchResultSkeletonMode.None,
                showCollectedIndicator = !state.collectedOnly,
                showStatsPlaceholders = true,
                coverFadeInState = coverFadeInState,
                coverReloadKey = state.resultRevision,
                onRjLongClick = onMetaAction,
                onCircleLongClick = onMetaAction,
                onCvLongClick = onMetaAction,
                onTagLongClick = onMetaAction,
            )
        }
    }
}
