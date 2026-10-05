package com.asmr.player.ui.library.albumdetail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import com.asmr.player.data.remote.api.AsmrOneTrackNodeResponse
import com.asmr.player.data.remote.auth.DlsiteAuthStore
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.Track
import com.asmr.player.domain.model.TreeFileType
import com.asmr.player.domain.model.isDownloadableTreeFileType
import com.asmr.player.playback.MediaItemFactory
import com.asmr.player.ui.common.cover.EaraLogoLoadingIndicator
import com.asmr.player.ui.common.cover.ImagePreviewItem
import com.asmr.player.ui.common.cover.ImagePreviewPreparedItem
import com.asmr.player.ui.common.cover.ImagePreviewRequest
import com.asmr.player.ui.common.list.LocalBottomOverlayPadding
import com.asmr.player.ui.common.list.rememberCalmScrollableFlingBehavior
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.util.SmartSortKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AlbumDlsitePlayBreadcrumbTabV2(
    header: @Composable () -> Unit,
    album: Album,
    rjCode: String,
    tree: List<AsmrOneTrackNodeResponse>,
    isLoading: Boolean,
    shouldAutoLoad: Boolean,
    isAwaitingInitialTarget: Boolean,
    hasResolvedDlsitePlayContent: Boolean,
    onOpenLogin: () -> Unit,
    onEnsureLoaded: () -> Unit,
    onPlayMediaItems: (List<MediaItem>, Int) -> Unit,
    onAddToQueue: (Track) -> Boolean,
    onAddMediaItemsToQueue: (List<MediaItem>) -> Unit,
    onAddMediaItemsToFavorites: (List<MediaItem>) -> Unit,
    onOpenBatchPlaylistPicker: (List<MediaItem>) -> Unit,
    onDownloadOne: (String) -> Unit,
    onPreviewImages: (ImagePreviewRequest) -> Unit,
    onPreviewFile: (AsmrTreeUiEntry.File) -> Unit,
    treeStateKey: String,
    initialCurrentPath: String,
    topContentPadding: Dp,
    animateIntro: Boolean,
    onPersistCurrentPath: (String) -> Unit,
    initialScroll: Pair<Int, Int>,
    onPersistScroll: (Int, Int) -> Unit,
    loadRemoteFileSize: suspend (String) -> Long?,
    prepareImagePreview: suspend (String, String?, Boolean, Int?, Int?) -> String?,
    onListStateAvailable: (LazyListState?) -> Unit = {},
    authStore: DlsiteAuthStore
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var loggedIn by remember { mutableStateOf(authStore.isPlayLoggedIn()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                loggedIn = authStore.isPlayLoggedIn()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!loggedIn) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("需要先登录 DLsite 才能使用已购播放/下载")
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onOpenLogin) { Text("去登录") }
            }
        }
        return
    }

    var autoLoadDispatched by remember(treeStateKey) { mutableStateOf(false) }
    LaunchedEffect(loggedIn, rjCode, shouldAutoLoad) {
        if (loggedIn && shouldAutoLoad && !autoLoadDispatched) {
            autoLoadDispatched = true
            onEnsureLoaded()
        }
    }

    val headerItemCount = 2
    val restoredIndex = if (initialScroll.first <= 0) 0 else initialScroll.first + headerItemCount
    val listState = rememberSaveable("scroll:$treeStateKey", saver = LazyListState.Saver) {
        LazyListState(restoredIndex, initialScroll.second)
    }
    DisposableEffect(listState) {
        onListStateAvailable(listState)
        onDispose { onListStateAvailable(null) }
    }
    PersistAlbumDetailListScroll(
        listState = listState,
        stateKey = treeStateKey,
        indexOffset = headerItemCount,
        onPersistScroll = onPersistScroll
    )

    val rj = rjCode.trim().uppercase()
    var currentPath by rememberSaveable(treeStateKey) { mutableStateOf(initialCurrentPath.trim().trim('/')) }
    val leafTracks by produceState(initialValue = emptyList<AsmrOneLeafUi>(), key1 = tree) {
        value = withContext(Dispatchers.Default) { flattenAsmrOneTracksForUi(tree) }
    }
    val leafByRelPath by produceState(initialValue = emptyMap<String, AsmrOneLeafUi>(), key1 = leafTracks) {
        value = withContext(Dispatchers.Default) { leafTracks.associateBy { it.relativePath } }
    }
    val remoteIndex by produceState<RemoteTreeIndex?>(
        initialValue = null,
        tree,
        album.id,
        album.coverPath,
        album.coverUrl,
    ) {
        value = withContext(Dispatchers.Default) { buildRemoteTreeIndex(tree, album) }
    }
    val browser by produceState<DirectoryBrowserResult?>(initialValue = null, key1 = remoteIndex, key2 = currentPath) {
        value = remoteIndex?.let { index ->
            withContext(Dispatchers.Default) { buildRemoteDirectoryBrowser(index, currentPath) }
        }
    }
    val isDirectoryPending = shouldShowDlsitePlayDirectoryLoading(
        isAwaitingInitialTarget = isAwaitingInitialTarget,
        hasResolvedDlsitePlayContent = hasResolvedDlsitePlayContent,
        isLoadingDlsitePlay = isLoading,
        hasDlsitePlayTree = tree.isNotEmpty(),
        hasDirectoryBrowser = browser != null
    )
    LaunchedEffect(currentPath, treeStateKey) {
        onPersistCurrentPath(currentPath)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        state = listState,
        flingBehavior = rememberCalmScrollableFlingBehavior(),
        contentPadding = PaddingValues(top = topContentPadding, bottom = LocalBottomOverlayPadding.current)
    ) {
        item(key = "dlplay-header:$treeStateKey") { header() }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = AlbumDetailHorizontalPadding, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "已购内容",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onOpenLogin) { Text("登录 / 切换账号") }
            }
        }

        val dlsitePlayPanelState = when {
            rj.isBlank() -> DirectoryTreePanelState.MissingRj
            tree.isEmpty() && isDirectoryPending -> DirectoryTreePanelState.Loading
            tree.isEmpty() -> DirectoryTreePanelState.Empty
            isDirectoryPending || browser == null -> DirectoryTreePanelState.Loading
            else -> DirectoryTreePanelState.Content
        }
        item(key = "dlplay-content:$treeStateKey") {
            DirectoryTreeAnimatedContent(
                targetState = dlsitePlayPanelState,
                label = "dlsitePlayDirectoryTree",
                modifier = Modifier.fillMaxWidth()
            ) { panelState ->
                when (panelState) {
                    DirectoryTreePanelState.MissingRj -> Box(
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("缺少作品编号，无法加载")
                    }
                    DirectoryTreePanelState.Empty -> Box(
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无可播放资源")
                    }
                    DirectoryTreePanelState.Loading -> Box(
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        EaraLogoLoadingIndicator(tint = AsmrTheme.colorScheme.primary)
                    }
                    DirectoryTreePanelState.Content -> {
                        val browserValue = browser
                        if (browserValue == null) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(220.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                EaraLogoLoadingIndicator(tint = AsmrTheme.colorScheme.primary)
                            }
                        } else {
                            DirectoryBrowserPanel(
                                panelKey = treeStateKey,
                                currentPath = currentPath,
                                breadcrumbs = browserValue.breadcrumbs,
                                batchTargets = browserValue.batchTargets,
                                folders = browserValue.folders,
                                files = browserValue.files,
                                onNavigate = { path -> currentPath = path },
                                onAddToFavorites = onAddMediaItemsToFavorites,
                                onOpenBatchPlaylistPicker = onOpenBatchPlaylistPicker,
                                onAddMediaItemsToQueue = onAddMediaItemsToQueue,
                                animateIntro = animateIntro,
                                folderKeyPrefix = "dlplay-folder",
                                fileKeyPrefix = "dlplay-file",
                                fileContent = { file, selectionMode, selected, selectedPosition, enterSelectionMode, onSelectedChange ->
                                    val leaf = leafByRelPath[file.path]
                                    DirectoryFileRow(
                                        file = file.copy(showSubtitleStamp = file.subtitleSources.isNotEmpty()),
                                        loadRemoteFileSize = loadRemoteFileSize,
                                        onPrimary = {
                                            when (file.fileType) {
                                                TreeFileType.Audio, TreeFileType.Video -> {
                                                    scope.launch {
                                                        val prepared = withContext(Dispatchers.Default) {
                                                            val folderPath = file.path.substringBeforeLast('/', "")
                                                            val siblings = browserValue.files
                                                                .filter { sibling ->
                                                                    sibling.path.substringBeforeLast('/', "") == folderPath &&
                                                                        (sibling.fileType == TreeFileType.Audio || sibling.fileType == TreeFileType.Video) &&
                                                                        sibling.playlistTarget != null
                                                                }
                                                                .sortedBy { SmartSortKey.of(it.title) }
                                                            val items = siblings.mapNotNull { it.playlistTarget?.toMediaItem() }
                                                            if (items.isEmpty()) return@withContext null
                                                            val clickedId = file.playlistTarget?.mediaId.orEmpty()
                                                            val startIndex = items.indexOfFirst { it.mediaId == clickedId }
                                                                .takeIf { it >= 0 } ?: 0
                                                            PreparedMediaPlayback(items, startIndex)
                                                        }
                                                        if (prepared != null) {
                                                            if (leaf != null) {
                                                                com.asmr.player.util.OnlineLyricsStore.set(leaf.url, leaf.subtitles)
                                                            }
                                                            onPlayMediaItems(prepared.items, prepared.startIndex)
                                                        } else {
                                                            onPreviewFile(
                                                                AsmrTreeUiEntry.File(
                                                                    path = file.path,
                                                                    title = file.title,
                                                                    depth = 0,
                                                                    fileType = file.fileType,
                                                                    isPlayable = false,
                                                                    url = file.url
                                                                )
                                                            )
                                                        }
                                                    }
                                                }
                                                TreeFileType.Image -> {
                                                    val request = buildDirectoryImagePreviewRequest(
                                                        files = browserValue.files,
                                                        clickedPath = file.path,
                                                        toPreviewItem = { imageFile ->
                                                            val imageUrl = imageFile.url.takeIf { it.isNotBlank() } ?: return@buildDirectoryImagePreviewRequest null
                                                            ImagePreviewItem(
                                                                key = imageFile.path,
                                                                title = imageFile.title,
                                                                openPathOrUrl = imageUrl,
                                                                prepareImage = {
                                                                    val prepared = prepareImagePreview(
                                                                        imageUrl,
                                                                        imageFile.dlsitePlayOptimizedName,
                                                                        imageFile.dlsitePlayImageCrypt,
                                                                        imageFile.dlsitePlayImageWidth,
                                                                        imageFile.dlsitePlayImageHeight
                                                                    ) ?: imageUrl
                                                                    ImagePreviewPreparedItem(
                                                                        imageModel = prepared,
                                                                        openPathOrUrl = prepared
                                                                    )
                                                                }
                                                            )
                                                        }
                                                    )
                                                    if (request != null) {
                                                        onPreviewImages(request)
                                                    } else {
                                                        onPreviewFile(
                                                            AsmrTreeUiEntry.File(
                                                                path = file.path,
                                                                title = file.title,
                                                                depth = 0,
                                                                fileType = file.fileType,
                                                                isPlayable = false,
                                                                url = file.url
                                                            )
                                                        )
                                                    }
                                                }
                                                else -> onPreviewFile(
                                                    AsmrTreeUiEntry.File(
                                                        path = file.path,
                                                        title = file.title,
                                                        depth = 0,
                                                        fileType = file.fileType,
                                                        isPlayable = false,
                                                        url = file.url
                                                    )
                                                )
                                            }
                                        },
                                        selectionMode = selectionMode,
                                        selected = selected,
                                        selectedPosition = selectedPosition,
                                        onEnterSelectionMode = enterSelectionMode,
                                        onSelectedChange = onSelectedChange,
                                        onDownload = if (isDownloadableTreeFileType(file.fileType)) ({ onDownloadOne(file.path) }) else null,
                                        onAddToQueue = if (leaf != null) ({
                                            com.asmr.player.util.OnlineLyricsStore.set(leaf.url, leaf.subtitles)
                                            onAddToQueue(leaf.toTrack())
                                        }) else null,
                                        onAddToPlaylist = null
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
