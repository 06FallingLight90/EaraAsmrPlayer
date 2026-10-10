package com.asmr.player.ui.library

import com.asmr.player.ui.translation.PageTranslationHost

import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.fillMaxHeight
import com.asmr.player.domain.model.LibrarySort
import com.asmr.player.util.isOnlineTrackPath
import com.asmr.player.data.local.db.entities.titleForDisplay
import com.asmr.player.ui.common.list.LocalBottomOverlayPadding
import com.asmr.player.ui.common.status.EaraBrandedEmptyState
import com.asmr.player.ui.common.cover.EaraLogoLoadingIndicator
import com.asmr.player.ui.common.dialog.FlatActionDialog
import com.asmr.player.ui.common.dialog.FlatDialogAction
import com.asmr.player.ui.common.dialog.FlatDialogActionTone
import com.asmr.player.ui.common.list.interruptScrollableFlingOnPointerDown
import com.asmr.player.ui.common.list.lightweightVerticalStretchOverscroll
import com.asmr.player.ui.common.audio.rememberAudioMeta
import com.asmr.player.ui.common.list.rememberCalmScrollableFlingBehavior
import com.asmr.player.ui.common.list.withAddedBottomPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import com.asmr.player.ui.common.core.isCompactWidth
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.Track
import androidx.media3.common.MediaItem
import com.asmr.player.ui.player.PlayerViewModel
import com.asmr.player.ui.library.LibraryUiState

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.sidepanel.LandscapeRightPanelHost
import com.asmr.player.ui.sidepanel.RecentAlbumsPanel
import com.asmr.player.ui.common.cover.LazyListPreloader
import com.asmr.player.ui.common.cover.LazyStaggeredGridPreloader
import com.asmr.player.ui.common.cover.rememberAppImageCacheManager
import com.asmr.player.ui.groups.AlbumGroupsViewModel
import com.asmr.player.ui.playlists.PlaylistsViewModel
import com.asmr.player.ui.common.core.SearchBlockedKeywordsViewModel

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.Label
import androidx.compose.ui.text.style.TextOverflow
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.asmr.player.ui.common.core.clearFocusOnTapOutside
import com.asmr.player.ui.common.cover.albumCoverImageModel
import com.asmr.player.ui.common.cover.shouldFadeInCover
import com.asmr.player.ui.common.list.rememberCollapsibleHeaderState
import com.asmr.player.ui.common.list.rememberSaveablePrefetchedLazyListState
import com.asmr.player.ui.common.list.collectAsStateWhileActive
import com.asmr.player.ui.common.list.StableWindowInsets
import com.asmr.player.playback.MediaItemFactory
import com.asmr.player.ui.common.dialog.TagAssignDialog

private val LibraryChromeContentGap = 20.dp
internal val LibraryPageHorizontalPadding = 8.dp
private const val LibraryTrackPagingHintDistance = 10

private fun Album.withUserTags(userTags: List<String>): Album {
    if (userTags.isEmpty()) return this
    return copy(tags = (tags + userTags).distinct())
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    windowSizeClass: WindowSizeClass,
    isActive: Boolean = true,
    isDataActive: Boolean = isActive,
    onAlbumClick: (Album) -> Unit,
    onPlayTracks: (Album, List<Track>, Track) -> Unit,
    onOpenPlaylistPicker: (MediaItem) -> Unit = {},
    onOpenGroupPicker: (albumId: Long) -> Unit = { _ -> },
    onOpenFilterScreen: () -> Unit = {},
    onOpenAllSongs: () -> Unit = {},
    onSearchKeyword: (String) -> Unit = {},
    scrollToTopSignal: Long = 0L,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    PageTranslationHost(active = isActive && isDataActive, headerKey = "library") {
        LibraryScreenContent(
            windowSizeClass = windowSizeClass,
            isActive = isActive,
            isDataActive = isDataActive,
            onAlbumClick = onAlbumClick,
            onPlayTracks = onPlayTracks,
            onOpenPlaylistPicker = onOpenPlaylistPicker,
            onOpenGroupPicker = onOpenGroupPicker,
            onOpenFilterScreen = onOpenFilterScreen,
            onOpenAllSongs = onOpenAllSongs,
            onSearchKeyword = onSearchKeyword,
            scrollToTopSignal = scrollToTopSignal,
            viewModel = viewModel,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
private fun LibraryScreenContent(
    windowSizeClass: WindowSizeClass,
    isActive: Boolean = true,
    isDataActive: Boolean = isActive,
    onAlbumClick: (Album) -> Unit,
    onPlayTracks: (Album, List<Track>, Track) -> Unit,
    onOpenPlaylistPicker: (MediaItem) -> Unit = {},
    onOpenGroupPicker: (albumId: Long) -> Unit = { _ -> },
    onOpenFilterScreen: () -> Unit = {},
    onOpenAllSongs: () -> Unit = {},
    onSearchKeyword: (String) -> Unit = {},
    scrollToTopSignal: Long = 0L,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val colorScheme = AsmrTheme.colorScheme
    val materialColorScheme = MaterialTheme.colorScheme
    val uiState by viewModel.uiState.collectAsStateWhileActive(isDataActive)
    val viewMode by viewModel.libraryViewMode.collectAsStateWhileActive(isDataActive)
    val querySpec by viewModel.querySpec.collectAsStateWhileActive(isDataActive)
    val hasActiveFilters by viewModel.hasActiveFilters.collectAsStateWhileActive(isDataActive)
    val tags by viewModel.availableTags.collectAsStateWhileActive(isDataActive)
    val userTagsByAlbumId by viewModel.userTagsByAlbumId.collectAsStateWhileActive(isDataActive)
    val userTagsByTrackId by viewModel.userTagsByTrackId.collectAsStateWhileActive(isDataActive)
    val isGlobalSyncRunning by viewModel.isGlobalSyncRunning.collectAsStateWhileActive(isDataActive)
    val copyMeta = rememberAlbumMetaCopyAction(viewModel.messageManager)
    val playlistsViewModel: PlaylistsViewModel = hiltViewModel()
    val albumGroupsViewModel: AlbumGroupsViewModel = hiltViewModel()
    val blockedKeywordsViewModel: SearchBlockedKeywordsViewModel = hiltViewModel()
    val searchBlockedKeywords by blockedKeywordsViewModel.searchBlockedKeywords.collectAsStateWhileActive(isDataActive)
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()
    var searchText by rememberSaveable { mutableStateOf(querySpec.textQuery.orEmpty()) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var showTagManager by remember { mutableStateOf(false) }
    var tagAssignTarget by remember { mutableStateOf<TagAssignTarget?>(null) }
    var metaActionKeyword by rememberSaveable { mutableStateOf<String?>(null) }

    fun openMetaActions(value: String) {
        val keyword = value.trim()
        if (keyword.isNotBlank()) metaActionKeyword = keyword
    }

    fun addMetaBlockedKeyword(value: String) {
        val keyword = value.trim()
        if (keyword.isBlank()) return
        val exists = searchBlockedKeywords.any { it.equals(keyword, ignoreCase = true) }
        blockedKeywordsViewModel.addSearchBlockedKeyword(keyword)
        if (exists) {
            viewModel.messageManager.showInfo("屏蔽词已存在：$keyword")
        } else {
            viewModel.messageManager.showSuccess("已添加屏蔽词：$keyword")
        }
    }

    LaunchedEffect(querySpec.textQuery) {
        val newText = querySpec.textQuery.orEmpty()
        if (newText != searchText) searchText = newText
    }

    val listState = rememberSaveablePrefetchedLazyListState(stateKey = "library")
    val gridState = rememberSaveable(saver = androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState.Saver) {
        androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState()
    }

    val mode = (viewMode ?: 0).coerceIn(0, 2)
    val isGrid = mode == 1
    val isTrackList = mode == 2
    var lastChromeResetMode by rememberSaveable { mutableStateOf(mode) }
    val pagedAlbums = viewModel.pagedAlbums.collectAsLazyPagingItems()
    val pagedTrackAlbumHeaders = viewModel.pagedTrackAlbumHeaders.collectAsLazyPagingItems()
    val pagedAlbumSnapshot = pagedAlbums.itemSnapshotList
    val pagedAlbumIndices = remember(pagedAlbumSnapshot.items.size) { List(pagedAlbumSnapshot.items.size) { it } }
    val loadedTrackAlbumHeaders = pagedTrackAlbumHeaders.itemSnapshotList.items
    val expandedAlbumTracks by (if (isTrackList) viewModel.expandedTrackAlbumTracks else flowOf(emptyMap()))
        .collectAsStateWithLifecycle(initialValue = emptyMap())
    val expandedAlbumIds by viewModel.expandedTrackAlbumIds.collectAsStateWhileActive(isDataActive)
    var actionAlbum by remember { mutableStateOf<Album?>(null) }
    var showAlbumActions by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val chromeState = rememberCollapsibleHeaderState()
    fun stopActiveScroll() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            when (mode) {
                1 -> runCatching { gridState.stopScroll(MutatePriority.UserInput) }
                else -> runCatching { listState.stopScroll(MutatePriority.UserInput) }
            }
        }
    }
    val chromeReservedHeightPx = if (chromeState.heightPx > 0f) {
        chromeState.heightPx
    } else {
        with(LocalDensity.current) { 80.dp.toPx() }
    }
    val topPadding = with(LocalDensity.current) { chromeReservedHeightPx.toDp() } + LibraryChromeContentGap

    LaunchedEffect(isTrackList) {
        if (!isTrackList) {
            viewModel.clearExpandedTrackAlbums()
        }
    }
    LaunchedEffect(showDeleteConfirm, actionAlbum) {
        if (showDeleteConfirm && (actionAlbum == null || actionAlbum?.id?.let { it <= 0L } == true)) {
            showDeleteConfirm = false
        }
    }
    LaunchedEffect(mode) {
        if (lastChromeResetMode != mode) {
            chromeState.expand()
            lastChromeResetMode = mode
        }
    }
    LaunchedEffect(listState, mode) {
        if (mode != 0 && mode != 2) return@LaunchedEffect
        snapshotFlow {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
            .distinctUntilChanged()
            .collect { atTop ->
                if (atTop) chromeState.expand()
            }
    }
    LaunchedEffect(gridState, mode) {
        if (mode != 1) return@LaunchedEffect
        snapshotFlow {
            gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
        }
            .distinctUntilChanged()
            .collect { atTop ->
                if (atTop) chromeState.expand()
            }
    }
    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal == 0L) return@LaunchedEffect
        when (mode) {
            1 -> gridState.stopScroll(MutatePriority.PreventUserInput)
            else -> listState.stopScroll(MutatePriority.PreventUserInput)
        }
        when (mode) {
            1 -> gridState.scrollToItem(0)
            else -> listState.scrollToItem(0)
        }
        chromeState.expand()
    }
    LaunchedEffect(isActive, mode) {
        if (isActive) return@LaunchedEffect
        when (mode) {
            1 -> gridState.stopScroll(MutatePriority.PreventUserInput)
            else -> listState.stopScroll(MutatePriority.PreventUserInput)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent,
        contentColor = colorScheme.onBackground,
        // TopAppBar is now handled by MainActivity for better consistency
    ) { padding ->
        // 屏幕尺寸判断
        val isCompact = windowSizeClass.widthSizeClass.isCompactWidth

        LandscapeRightPanelHost(
            windowSizeClass = windowSizeClass,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            topPanel = {
                RecentAlbumsPanel(
                    onOpenAlbum = { a ->
                        onAlbumClick(
                            Album(
                                id = a.id,
                                title = a.titleForDisplay,
                                path = a.path,
                                localPath = a.localPath,
                                downloadPath = a.downloadPath,
                                circle = a.circle,
                                cv = a.cv,
                                coverUrl = a.coverUrl,
                                coverPath = a.coverPath,
                                coverThumbPath = a.coverThumbPath,
                                workId = a.workId,
                                rjCode = a.rjCode,
                                description = a.description
                            )
                        )
                    },
                    modifier = Modifier.fillMaxHeight()
                )
            },
        ) { contentModifier, hasRightPanel, rightPanelToggle ->
            Box(
                modifier = contentModifier,
                contentAlignment = if (hasRightPanel) Alignment.TopStart else Alignment.TopCenter
            ) {
                Column(
                    modifier = if (isCompact) {
                        Modifier.fillMaxSize()
                    } else if (hasRightPanel) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier
                            .fillMaxHeight()
                            .widthIn(max = 760.dp)
                            .fillMaxWidth()
                    }
                ) {
                    when (val state = uiState) {
                    is LibraryUiState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clearFocusOnTapOutside(),
                            contentAlignment = Alignment.Center
                        ) {
                            EaraLogoLoadingIndicator(tint = colorScheme.primary)
                        }
                    }
                    is LibraryUiState.BulkInProgress -> {
                        val progress = state.progress
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = when (progress.phase) {
                                    com.asmr.player.util.BulkPhase.ScanningLocal -> "正在扫描本地库"
                                    com.asmr.player.util.BulkPhase.SyncingCloud -> "正在云同步"
                                },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = colorScheme.textPrimary
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            if (progress.total > 0) {
                                LinearProgressIndicator(
                                    progress = { progress.fraction },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "进度 ${progress.current}/${progress.total}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.textSecondary
                            )
                            if (progress.currentAlbumTitle.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = progress.currentAlbumTitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colorScheme.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (progress.currentFile.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "正在扫描：${progress.currentFile}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(18.dp))
                            Button(onClick = { viewModel.cancelBulkTask() }) { Text("取消") }
                        }
                    }
                    is LibraryUiState.Success -> {
                        if (viewMode == null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clearFocusOnTapOutside(),
                                contentAlignment = Alignment.Center
                            ) {
                                EaraLogoLoadingIndicator(tint = colorScheme.primary)
                            }
                        } else {
                            // Main content area
                            CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .interruptScrollableFlingOnPointerDown { stopActiveScroll() }
                                ) {
                                val isTrackListLoading = isTrackList &&
                                    (pagedTrackAlbumHeaders.loadState.refresh is LoadState.Loading) &&
                                    pagedTrackAlbumHeaders.itemCount == 0
                                val isAlbumListLoading = !isTrackList &&
                                    (pagedAlbums.loadState.refresh is LoadState.Loading) &&
                                    pagedAlbums.itemCount == 0
                                val isLoading = isTrackListLoading || isAlbumListLoading
                                val isEmpty = if (isTrackList) {
                                    (pagedTrackAlbumHeaders.loadState.refresh is LoadState.NotLoading) && pagedTrackAlbumHeaders.itemCount == 0
                                } else {
                                    (pagedAlbums.loadState.refresh is LoadState.NotLoading) && pagedAlbums.itemCount == 0
                                }
                                if (isLoading) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clearFocusOnTapOutside(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        EaraLogoLoadingIndicator(tint = colorScheme.primary)
                                    }
                                } else if (isEmpty) {
                                    val hasAnyQuery =
                                        !querySpec.textQuery.isNullOrBlank() ||
                                            querySpec.includeTagIds.isNotEmpty() ||
                                            querySpec.excludeTagIds.isNotEmpty() ||
                                            querySpec.circles.isNotEmpty() ||
                                            querySpec.cvs.isNotEmpty() ||
                                            querySpec.source != null

                                    EaraBrandedEmptyState(
                                        sectionTitle = if (hasAnyQuery) "本地库结果" else "本地库",
                                        headline = if (hasAnyQuery) "没有匹配的本地内容" else "还没有扫描到本地专辑",
                                        sectionIcon = if (hasAnyQuery) Icons.Rounded.Search else Icons.Rounded.FolderOpen,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clearFocusOnTapOutside(),
                                        contentPadding = PaddingValues(
                                            top = topPadding,
                                            bottom = LocalBottomOverlayPadding.current + 24.dp
                                        ),
                                        footer = if (hasAnyQuery) {
                                            {
                                                FilledTonalButton(
                                                    onClick = {
                                                        searchText = ""
                                                        viewModel.setSearchQuery("")
                                                        viewModel.clearFilters()
                                                    },
                                                    colors = ButtonDefaults.filledTonalButtonColors(
                                                        containerColor = colorScheme.primaryContainer,
                                                        contentColor = colorScheme.onPrimaryContainer
                                                    )
                                                ) {
                                                    Text("重置筛选")
                                                }
                                            }
                                        } else {
                                            null
                                        }
                                    )
                                } else if (isTrackList) {
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .lightweightVerticalStretchOverscroll(
                                                isAtStart = { !listState.canScrollBackward },
                                                isAtEnd = { !listState.canScrollForward },
                                            )
                                            .clearFocusOnTapOutside()
                                            .nestedScroll(chromeState.nestedScrollConnection),
                                        flingBehavior = rememberCalmScrollableFlingBehavior(),
                                        contentPadding = PaddingValues(top = topPadding, bottom = 8.dp)
                                            .withAddedBottomPadding(LocalBottomOverlayPadding.current)
                                    ) {
                                        val headerCount = loadedTrackAlbumHeaders.size
                                        val lastLoadedHeader = loadedTrackAlbumHeaders.lastOrNull()
                                        val appendState = pagedTrackAlbumHeaders.loadState.append
                                        val pagingHintHeaderIndex =
                                            (headerCount - LibraryTrackPagingHintDistance).coerceAtLeast(0)
                                        val pagingHintHeader = lastLoadedHeader?.takeIf {
                                            appendState is LoadState.NotLoading &&
                                                !appendState.endOfPaginationReached
                                        }
                                        for (headerIndex in 0 until headerCount) {
                                            if (pagingHintHeader != null && headerIndex == pagingHintHeaderIndex) {
                                                item(
                                                    key = "trackPagingHint:${pagingHintHeader.albumId}:$headerCount",
                                                    contentType = "trackPagingHint"
                                                ) {
                                                    LaunchedEffect(pagingHintHeader.albumId, headerCount, querySpec) {
                                                        val hintIndex = headerCount - 1
                                                        if (hintIndex in 0 until pagedTrackAlbumHeaders.itemCount) {
                                                            pagedTrackAlbumHeaders[hintIndex]
                                                        }
                                                    }
                                                    Spacer(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(1.dp)
                                                    )
                                                }
                                            }
                                            val header = loadedTrackAlbumHeaders[headerIndex]

                                            val albumId = header.albumId
                                            val expanded = expandedAlbumIds.contains(albumId)
                                            val rows = expandedAlbumTracks[albumId].orEmpty()
                                            val isFirstAlbumHeader = headerIndex == 0
                                            val isLastAlbumHeader = headerIndex == headerCount - 1

                                            stickyHeader(key = "album:$albumId") {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(colorScheme.background)
                                                ) {
                                                    if (headerIndex > 0) {
                                                        HorizontalDivider(
                                                            modifier = Modifier.padding(horizontal = LibraryPageHorizontalPadding),
                                                            thickness = 0.5.dp,
                                                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                                                        )
                                                    }
                                                    TrackAlbumHeader(
                                                        albumTitle = header.albumTitle,
                                                        rjCode = header.rjCode.ifBlank { header.workId },
                                                        trackCount = header.trackCount,
                                                        totalDurationSeconds = header.totalDuration,
                                                        totalSizeBytes = header.totalSizeBytes.takeIf { it > 0L }
                                                            ?: rememberAlbumTrackListTotalSizeBytes(
                                                                rows = rows,
                                                                loadFileSizes = !listState.isScrollInProgress
                                                            ),
                                                        coverModel = albumCoverImageModel(
                                                            coverThumbPath = "",
                                                            coverPath = header.coverPath.takeIf { it != "null" }.orEmpty(),
                                                            coverUrl = header.coverUrl
                                                        ),
                                                        expanded = expanded,
                                                        isFirstInList = isFirstAlbumHeader,
                                                        isLastInList = isLastAlbumHeader,
                                                        onToggle = { viewModel.toggleExpandedTrackAlbum(albumId) }
                                                    )
                                                }
                                            }

                                            if (expanded) {
                                                val album = Album(
                                                    id = header.albumId,
                                                    title = header.albumTitle,
                                                    path = "",
                                                    circle = header.circle,
                                                    cv = header.cv,
                                                    tags = emptyList(),
                                                    coverUrl = header.coverUrl,
                                                    coverPath = header.coverPath,
                                                    workId = header.workId,
                                                    rjCode = header.rjCode.ifBlank { header.workId }
                                                )
                                                itemsIndexed(
                                                    items = rows,
                                                    key = { _, row -> row.trackId },
                                                    contentType = { _, _ -> "albumTrackRow" }
                                                ) { index, row ->
                                                    val track = remember(
                                                        row.trackId,
                                                        row.albumId,
                                                        row.trackTitle,
                                                        row.trackPath,
                                                        row.duration,
                                                        row.trackGroup
                                                    ) {
                                                        Track(
                                                            id = row.trackId,
                                                            albumId = row.albumId,
                                                            title = row.trackTitle,
                                                            path = row.trackPath,
                                                            duration = row.duration,
                                                            group = row.trackGroup
                                                        )
                                                    }
                                                    val meta = rememberAudioMeta(
                                                        sourcePath = row.trackPath,
                                                        durationSeconds = row.duration,
                                                        prefixSegments = listOf(row.cv),
                                                        loadSize = !listState.isScrollInProgress
                                                    )

                                                    Column {
                                                        TrackListRow(
                                                            title = track.title,
                                                            subtitle = meta.leadingText,
                                                            fixedTrailingSubtitle = meta.trailingText,
                                                            showSubtitleStamp = row.hasSubtitles,
                                                            isLastInSection = index == rows.lastIndex,
                                                            onClick = {
                                                                scope.launch {
                                                                    val tracksForAlbum = withContext(Dispatchers.Default) {
                                                                        rows.map { r ->
                                                                            Track(
                                                                                id = r.trackId,
                                                                                albumId = r.albumId,
                                                                                title = r.trackTitle,
                                                                                path = r.trackPath,
                                                                                duration = r.duration,
                                                                                group = r.trackGroup
                                                                            )
                                                                        }
                                                                    }
                                                                    onPlayTracks(album, tracksForAlbum, track)
                                                                }
                                                            },
                                                            onAddToQueue = {
                                                                playerViewModel.addTrackToQueue(album, track)
                                                            },
                                                            onAddToPlaylist = {
                                                                onOpenPlaylistPicker(MediaItemFactory.fromTrack(album, track))
                                                            },
                                                            onManageTags = if (!isOnlineTrackPath(track.path)) {{
                                                                scope.launch {
                                                                    val inherited = withContext(Dispatchers.IO) {
                                                                        viewModel.loadInheritedTagsForAlbum(album.id)
                                                                    }
                                                                    val user = userTagsByTrackId[track.id].orEmpty()
                                                                    tagAssignTarget = TagAssignTarget.Track(
                                                                        trackId = track.id,
                                                                        title = track.title,
                                                                        inheritedTags = inherited,
                                                                        userTags = user
                                                                    )
                                                                }
                                                            }} else null,
                                                            onRemove = {
                                                                viewModel.removeTrackFromAlbum(track.id)
                                                            }
                                                        )
                                                        if (index < rows.size - 1) {
                                                            HorizontalDivider(
                                                                modifier = Modifier.padding(horizontal = LibraryPageHorizontalPadding),
                                                                thickness = 0.5.dp,
                                                                color = colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else if (isGrid) {
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
                                        itemCount = pagedAlbums.itemCount,
                                        enabled = isActive,
                                        preloadNext = 24,
                                        preloadSize = gridPreloadSize,
                                        cacheManagerProvider = { cacheManager },
                                        modelAt = { idx ->
                                            pagedAlbums.itemSnapshotList.getOrNull(idx)?.let { albumCoverImageModel(it) }
                                        }
                                    )
                                    LazyVerticalStaggeredGrid(
                                        columns = StaggeredGridCells.Adaptive(gridCellSize),
                                        state = gridState,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .lightweightVerticalStretchOverscroll(
                                                isAtStart = { !gridState.canScrollBackward },
                                                isAtEnd = { !gridState.canScrollForward },
                                            )
                                            .clearFocusOnTapOutside()
                                            .nestedScroll(chromeState.nestedScrollConnection),
                                        flingBehavior = rememberCalmScrollableFlingBehavior(),
                                        contentPadding = PaddingValues(top = topPadding, start = LibraryPageHorizontalPadding, end = LibraryPageHorizontalPadding, bottom = 16.dp)
                                            .withAddedBottomPadding(LocalBottomOverlayPadding.current),
                                        verticalItemSpacing = AlbumGridItemSpacing,
                                        horizontalArrangement = Arrangement.spacedBy(AlbumGridItemSpacing)
                                    ) {
                                        staggeredItems(
                                            pagedAlbumIndices,
                                            key = { idx -> pagedAlbumSnapshot.items.getOrNull(idx)?.id?.takeIf { it > 0L } ?: idx },
                                            contentType = { "albumGridItem" },
                                        ) { idx ->
                                            val album = pagedAlbums[idx] ?: return@staggeredItems
                                            val userTags = userTagsByAlbumId[album.id].orEmpty()
                                            val mergedAlbum = remember(album, userTags) {
                                                album.withUserTags(userTags)
                                            }
                                            AlbumGridItem(
                                                album = mergedAlbum,
                                                onClick = { onAlbumClick(mergedAlbum) },
                                                onLongClick = {
                                                    actionAlbum = mergedAlbum
                                                    showAlbumActions = true
                                                },
                                                onRjLongClick = ::openMetaActions,
                                                onCircleLongClick = ::openMetaActions,
                                                onCvLongClick = ::openMetaActions,
                                                onTagLongClick = ::openMetaActions,
                                                coverFadeInState = coverFadeInState,
                                                showCollectedIndicator = false,
                                                coverOverlay = {
                                                    AlbumSyncStatusOverlay(
                                                        syncStatus = state.syncingAlbums[album.id] ?: SyncStatus.Idle,
                                                        indicatorSize = 24.dp,
                                                        blurRadius = 4.dp,
                                                    )
                                                },
                                            )
                                        }
                                    }
                                } else {
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
                                        itemCount = pagedAlbums.itemCount,
                                        enabled = isActive,
                                        preloadNext = 24,
                                        preloadSize = preloadSize,
                                        cacheManagerProvider = { cacheManager },
                                        modelAt = { idx ->
                                            pagedAlbums.itemSnapshotList.getOrNull(idx)?.let { albumCoverImageModel(it) }
                                        }
                                    )
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .lightweightVerticalStretchOverscroll(
                                                isAtStart = { !listState.canScrollBackward },
                                                isAtEnd = { !listState.canScrollForward },
                                            )
                                            .clearFocusOnTapOutside()
                                            .nestedScroll(chromeState.nestedScrollConnection),
                                        flingBehavior = rememberCalmScrollableFlingBehavior(),
                                        contentPadding = PaddingValues(top = topPadding, bottom = 8.dp)
                                            .withAddedBottomPadding(LocalBottomOverlayPadding.current)
                                    ) {
                                        items(
                                            count = pagedAlbums.itemCount,
                                            key = { idx -> pagedAlbums.itemSnapshotList.getOrNull(idx)?.id?.takeIf { it > 0L } ?: idx },
                                            contentType = { "albumListItem" }
                                        ) { idx ->
                                            val album = pagedAlbums[idx] ?: return@items
                                            val userTags = userTagsByAlbumId[album.id].orEmpty()
                                            val mergedAlbum = remember(album, userTags) {
                                                album.withUserTags(userTags)
                                            }
                                            AlbumItem(
                                                album = mergedAlbum,
                                                onClick = { onAlbumClick(mergedAlbum) },
                                                onLongClick = {
                                                    actionAlbum = mergedAlbum
                                                    showAlbumActions = true
                                                },
                                                onRjLongClick = ::openMetaActions,
                                                onCircleLongClick = ::openMetaActions,
                                                onCvLongClick = ::openMetaActions,
                                                onTagLongClick = ::openMetaActions,
                                                coverFadeInState = coverFadeInState,
                                                showCollectedIndicator = false,
                                                coverOverlay = {
                                                    AlbumSyncStatusOverlay(
                                                        syncStatus = state.syncingAlbums[album.id] ?: SyncStatus.Idle,
                                                        indicatorSize = 16.dp,
                                                        blurRadius = 2.dp,
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }

                                LibraryChrome(
                                    modifier = Modifier.align(Alignment.TopCenter),
                                    searchText = searchText,
                                    onSearchTextChange = {
                                        searchText = it
                                        viewModel.setSearchQuery(it)
                                    },
                                    onClearSearch = {
                                        searchText = ""
                                        viewModel.setSearchQuery("")
                                    },
                                    currentSort = querySpec.sort,
                                    sortMenuExpanded = sortMenuExpanded,
                                    onSortMenuExpandedChange = { sortMenuExpanded = it },
                                    onSortLastPlayed = { viewModel.setSort(LibrarySort.LastPlayedDesc) },
                                    onSortAdded = { viewModel.setSort(LibrarySort.AddedDesc) },
                                    onSortTitle = { viewModel.setSort(LibrarySort.TitleAsc) },
                                    onOpenFilterScreen = onOpenFilterScreen,
                                    filterActive = hasActiveFilters,
                                    onOpenAllSongs = onOpenAllSongs,
                                    rightPanelToggle = rightPanelToggle,
                                    materialColorScheme = materialColorScheme,
                                    chromeState = chromeState,
                                    onMeasured = { chromeState.updateHeight(it.height.toFloat()) }
                                )
                            }
                            }
                        }
                    }
                }
            }
            }
        }
    }

    if (showTagManager) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showTagManager = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                TagManagerSheet(
                    tags = tags,
                    onRename = { tagId, newName -> viewModel.renameUserTag(tagId, newName) },
                    onDelete = { tagId -> viewModel.deleteUserTag(tagId) },
                    onClose = { showTagManager = false }
                )
            }
        }
    }
    
    // ... rest of the file (ModalBottomSheet, dialogs)


    if (showAlbumActions) {
        val album = actionAlbum
        ModalBottomSheet(
            onDismissRequest = { showAlbumActions = false },
            sheetState = sheetState,
            windowInsets = WindowInsets(0, 0, 0, 0)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(StableWindowInsets.navigationBars)
            ) {
                if (album != null) {
                val syncStatus = (uiState as? LibraryUiState.Success)?.syncingAlbums?.get(album.id) ?: SyncStatus.Idle
                val isSyncing = syncStatus is SyncStatus.Syncing
                val hasLocalPaths = remember(album) { album.getAllLocalPaths().isNotEmpty() }
                ListItem(
                    headlineContent = { Text("删除") },
                    leadingContent = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clickable(enabled = !isSyncing && !isGlobalSyncRunning) {
                            showAlbumActions = false
                            showDeleteConfirm = true
                        }
                )
                ListItem(
                    headlineContent = { Text("标签管理") },
                    leadingContent = { Icon(Icons.AutoMirrored.Rounded.Label, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clickable {
                            showAlbumActions = false
                            val user = userTagsByAlbumId[album.id].orEmpty()
                            val inherited = album.tags.filterNot { user.contains(it) }
                            tagAssignTarget = TagAssignTarget.Album(
                                albumId = album.id,
                                title = album.title,
                                inheritedTags = inherited,
                                userTags = user
                            )
                        }
                )
                ListItem(
                    headlineContent = { Text("添加到分组") },
                    leadingContent = { Icon(Icons.Rounded.CreateNewFolder, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clickable(enabled = album.id > 0L) {
                            showAlbumActions = false
                            onOpenGroupPicker(album.id)
                        }
                )
                ListItem(
                    headlineContent = { Text("本地同步") },
                    leadingContent = { Icon(Icons.Rounded.Sync, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clickable(enabled = hasLocalPaths && !isSyncing && !isGlobalSyncRunning) {
                            showAlbumActions = false
                            viewModel.rescanAlbum(album)
                        }
                )
                ListItem(
                    headlineContent = { Text("云同步") },
                    leadingContent = { Icon(Icons.Rounded.CloudSync, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clickable(enabled = !isSyncing && !isGlobalSyncRunning) {
                            showAlbumActions = false
                            viewModel.syncAlbumMetadata(album)
                        }
                )
                if (isSyncing) {
                    ListItem(
                        headlineContent = { Text("取消同步") },
                        leadingContent = { Icon(Icons.Rounded.Close, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .clickable {
                                showAlbumActions = false
                                viewModel.cancelAlbumTask(album.id)
                            }
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                } else {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    if (showDeleteConfirm) {
        val album = actionAlbum
        if (album != null) {
            FlatActionDialog(
                onDismissRequest = { showDeleteConfirm = false },
                message = "将从本地库中移除该专辑，并尝试删除本地文件。",
                actions = listOf(
                    FlatDialogAction("取消", onClick = { showDeleteConfirm = false }),
                    FlatDialogAction(
                        text = "删除",
                        tone = FlatDialogActionTone.Danger,
                        onClick = {
                            showDeleteConfirm = false
                            viewModel.deleteAlbum(album)
                        }
                    )
                )
            )
        }
    }
    val target = tagAssignTarget
    if (target != null) {
        when (target) {
            is TagAssignTarget.Album -> {
                TagAssignDialog(
                    title = target.title,
                    inheritedTags = target.inheritedTags,
                    userTags = target.userTags,
                    allTags = tags,
                    onApplyUserTags = { list ->
                        viewModel.setUserTagsForAlbum(target.albumId, list.joinToString(","))
                        tagAssignTarget = null
                    },
                    onDismiss = { tagAssignTarget = null },
                    onOpenTagManager = { showTagManager = true }
                )
            }
            is TagAssignTarget.Track -> {
                TagAssignDialog(
                    title = target.title,
                    inheritedTags = target.inheritedTags,
                    userTags = target.userTags,
                    allTags = tags,
                    onApplyUserTags = { list ->
                        viewModel.setUserTagsForTrack(target.trackId, list.joinToString(","))
                        tagAssignTarget = null
                    },
                    onDismiss = { tagAssignTarget = null },
                    onOpenTagManager = { showTagManager = true }
                )
            }
        }
    }

    metaActionKeyword?.let { keyword ->
        AlbumMetaActionDialog(
            keyword = keyword,
            onDismissRequest = { metaActionKeyword = null },
            onSearch = onSearchKeyword,
            onCreatePlaylist = playlistsViewModel::createPlaylist,
            onCreateGroup = albumGroupsViewModel::createGroup,
            onAddBlockedKeyword = ::addMetaBlockedKeyword,
            onCopy = { copyMeta("内容", it) },
        )
    }

}

private sealed class TagAssignTarget {
    data class Album(
        val albumId: Long,
        val title: String,
        val inheritedTags: List<String>,
        val userTags: List<String>
    ) : TagAssignTarget()

    data class Track(
        val trackId: Long,
        val title: String,
        val inheritedTags: List<String>,
        val userTags: List<String>
    ) : TagAssignTarget()
}
