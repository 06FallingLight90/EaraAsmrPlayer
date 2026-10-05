package com.asmr.player.ui.library.albumdetail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import com.asmr.player.data.remote.api.AsmrOneTrackNodeResponse
import com.asmr.player.data.remote.scraper.DlsiteRecommendedWork
import com.asmr.player.data.remote.scraper.DlsiteRecommendations
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.Track
import com.asmr.player.domain.model.TreeFileType
import com.asmr.player.domain.model.isDownloadableTreeFileType
import com.asmr.player.playback.MediaItemFactory
import com.asmr.player.ui.common.cover.AsmrAsyncImage
import com.asmr.player.ui.common.cover.ImagePreviewItem
import com.asmr.player.ui.common.cover.ImagePreviewPreparedItem
import com.asmr.player.ui.common.cover.ImagePreviewRequest
import com.asmr.player.ui.common.cover.NoImageLoadingIndicator
import com.asmr.player.ui.common.list.LocalBottomOverlayPadding
import com.asmr.player.ui.common.list.rememberCalmScrollableFlingBehavior
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.translation.translatedPageText
import com.asmr.player.util.CacheImageModel
import com.asmr.player.util.DlsiteAntiHotlink
import com.asmr.player.util.SmartSortKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


private val DlsiteGalleryThumbWidth = 140.dp
private val DlsiteGalleryThumbHeight = 100.dp
private val DlsiteGalleryThumbGap = 10.dp
internal val DlsiteGallerySectionHeight = 120.dp
internal const val DlsiteGalleryThumbCornerRadius = 12

private enum class DlsiteGalleryImageSource {
    Dlsite,
    Directory
}

private data class DlsiteGalleryImage(
    val key: String,
    val title: String,
    val url: String,
    val source: DlsiteGalleryImageSource
) {
    fun imageModel(): Any {
        if (source == DlsiteGalleryImageSource.Directory) return url
        val headers = DlsiteAntiHotlink.headersForImageUrl(url)
        return if (headers.isEmpty()) url else CacheImageModel(data = url, headers = headers, keyTag = "dlsite")
    }

    fun toPreviewItem(): ImagePreviewItem {
        return ImagePreviewItem(
            key = key,
            title = title,
            imageModel = imageModel(),
            openPathOrUrl = url
        )
    }
}

private data class DlsiteInfoRemoteTreeData(
    val index: RemoteTreeIndex,
    val imageFiles: List<DirectoryFileItem>
)

private fun buildDlsiteGalleryImages(
    galleryUrls: List<String>,
    directoryImages: List<DirectoryFileItem>
): List<DlsiteGalleryImage> {
    val seenUrls = hashSetOf<String>()
    return buildList {
        galleryUrls.forEach { rawUrl ->
            val url = rawUrl.trim()
            if (url.isBlank() || !seenUrls.add(url)) return@forEach
            add(
                DlsiteGalleryImage(
                    key = "dlsite:$url",
                    title = url.substringBefore('?').substringAfterLast('/').ifBlank { "Gallery" },
                    url = url,
                    source = DlsiteGalleryImageSource.Dlsite
                )
            )
        }
        directoryImages.forEach { file ->
            val url = file.url.trim()
            if (url.isBlank() || !seenUrls.add(url)) return@forEach
            add(
                DlsiteGalleryImage(
                    key = "directory:${file.path}",
                    title = file.title.ifBlank { file.path.substringAfterLast('/') },
                    url = url,
                    source = DlsiteGalleryImageSource.Directory
                )
            )
        }
    }
}

@Composable
internal fun AlbumDlsiteInfoBreadcrumbTabV2(
    album: Album,
    header: @Composable () -> Unit,
    galleryUrls: List<String>,
    trialTracks: List<Track>,
    trialDownloadEnabled: Boolean,
    isLoading: Boolean,
    isAwaitingInitialLoad: Boolean,
    isAwaitingAsmrOneLoad: Boolean,
    hasResolvedAsmrOneContent: Boolean,
    asmrOneTree: List<AsmrOneTrackNodeResponse>,
    isLoadingAsmrOne: Boolean,
    isLoadingTrial: Boolean,
    onRefreshAsmrOne: () -> Unit,
    onRefreshTrial: () -> Unit,
    onDownloadTrial: () -> Unit,
    onPlayTracks: (Album, List<Track>, Track) -> Unit,
    onPlayMediaItems: (List<MediaItem>, Int) -> Unit,
    onAddToQueue: (Track) -> Boolean,
    onAddMediaItemsToQueue: (List<MediaItem>) -> Unit,
    onAddMediaItemsToFavorites: (List<MediaItem>) -> Unit,
    onOpenBatchPlaylistPicker: (List<MediaItem>) -> Unit,
    onDownloadOne: (String) -> Unit,
    onAddToPlaylistOne: (String) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onPreviewImages: (ImagePreviewRequest) -> Unit,
    onPreviewFile: (AsmrTreeUiEntry.File) -> Unit,
    treeStateKey: String,
    initialCurrentPath: String,
    topContentPadding: Dp,
    animateIntro: Boolean,
    onPersistCurrentPath: (String) -> Unit,
    initialScroll: Pair<Int, Int>,
    onPersistScroll: (Int, Int) -> Unit,
    showPortraitSimilarWorks: Boolean,
    portraitSimilarWorksContent: @Composable () -> Unit,
    dlsiteRecommendations: DlsiteRecommendations,
    onOpenAlbumByRj: (String, DlsiteRecommendedWork?) -> Unit,
    loadRemoteFileSize: suspend (String) -> Long?,
    onListStateAvailable: (LazyListState?) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val colorScheme = AsmrTheme.colorScheme
    val sectionActionIconColors = IconButtonDefaults.iconButtonColors(
        contentColor = colorScheme.textPrimary,
        disabledContentColor = colorScheme.textTertiary.copy(alpha = 0.7f)
    )
    var isGalleryPreviewRequested by rememberSaveable(treeStateKey) { mutableStateOf(false) }
    var currentPath by rememberSaveable(treeStateKey) { mutableStateOf(initialCurrentPath.trim().trim('/')) }
    val asmrLeafTracks by produceState(initialValue = emptyList<AsmrOneLeafUi>(), key1 = asmrOneTree) {
        value = withContext(Dispatchers.Default) { flattenAsmrOneTracksForUi(asmrOneTree) }
    }
    val asmrLeafByRelPath by produceState(initialValue = emptyMap<String, AsmrOneLeafUi>(), key1 = asmrLeafTracks) {
        value = withContext(Dispatchers.Default) { asmrLeafTracks.associateBy { it.relativePath } }
    }
    val remoteTreeData by produceState<DlsiteInfoRemoteTreeData?>(
        initialValue = null,
        asmrOneTree,
        album.id,
        album.coverPath,
        album.coverUrl,
    ) {
        value = withContext(Dispatchers.Default) {
            val index = buildRemoteTreeIndex(asmrOneTree, album)
            DlsiteInfoRemoteTreeData(
                index = index,
                imageFiles = collectRemoteTreeImageFiles(index)
            )
        }
    }
    val remoteIndex = remoteTreeData?.index
    val browser by produceState<DirectoryBrowserResult?>(initialValue = null, key1 = remoteIndex, key2 = currentPath) {
        value = remoteIndex?.let { index ->
            withContext(Dispatchers.Default) { buildRemoteDirectoryBrowser(index, currentPath) }
        }
    }
    val listState = rememberSaveable("scroll:$treeStateKey", saver = LazyListState.Saver) {
        LazyListState(initialScroll.first, initialScroll.second)
    }
    DisposableEffect(listState) {
        onListStateAvailable(listState)
        onDispose { onListStateAvailable(null) }
    }
    PersistAlbumDetailListScroll(
        listState = listState,
        stateKey = treeStateKey,
        onPersistScroll = onPersistScroll
    )
    LaunchedEffect(currentPath, treeStateKey) {
        onPersistCurrentPath(currentPath)
    }
    val isInitialDlsiteLoading = isLoading || isAwaitingInitialLoad
    val isAsmrOnePending = shouldShowAsmrOneDirectoryLoading(
        isAwaitingAsmrOneLoad = isAwaitingAsmrOneLoad,
        hasResolvedAsmrOneContent = hasResolvedAsmrOneContent,
        isLoadingAsmrOne = isLoadingAsmrOne,
        hasAsmrOneTree = asmrOneTree.isNotEmpty(),
        hasDirectoryBrowser = browser != null
    )
    val galleryImages = remember(galleryUrls, remoteTreeData) {
        buildDlsiteGalleryImages(galleryUrls, remoteTreeData?.imageFiles.orEmpty())
    }
    val galleryPanelTarget: DlsiteContentPanel<List<DlsiteGalleryImage>> = when {
        galleryImages.isNotEmpty() -> DlsiteContentPanel(DlsiteContentKind.Content, galleryImages)
        isInitialDlsiteLoading || isAsmrOnePending -> DlsiteContentPanel(DlsiteContentKind.Loading)
        else -> DlsiteContentPanel(DlsiteContentKind.Empty)
    }
    val galleryFadeState = rememberDlsiteContentFadeState(galleryPanelTarget, treeStateKey)
    val trialPanelTarget: DlsiteContentPanel<List<Track>> = when {
        trialTracks.isNotEmpty() -> DlsiteContentPanel(DlsiteContentKind.Content, trialTracks)
        isInitialDlsiteLoading || isLoadingTrial -> DlsiteContentPanel(DlsiteContentKind.Loading)
        else -> DlsiteContentPanel(DlsiteContentKind.Empty)
    }
    val trialFadeState = rememberDlsiteContentFadeState(trialPanelTarget, treeStateKey)
    val displayedTrialTracks = trialFadeState.panel.value.orEmpty()
    val videoTracks = remember(displayedTrialTracks) {
        displayedTrialTracks.filter { isVideoPreviewUrl(it.path) }
    }
    val audioTracks = remember(displayedTrialTracks) {
        displayedTrialTracks.filterNot { isVideoPreviewUrl(it.path) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        state = listState,
        flingBehavior = rememberCalmScrollableFlingBehavior(),
        contentPadding = PaddingValues(top = topContentPadding, bottom = LocalBottomOverlayPadding.current)
    ) {
        item(key = "dlsite-header") { header() }
        item(key = "dlsite-one-header") {
            AlbumDetailSectionHeading(
                title = if (asmrOneTree.isNotEmpty()) "ONE（已收录）" else "ONE",
                modifier = dlsiteAnimatedSectionModifier(
                    Modifier.fillMaxWidth().padding(start = AlbumDetailHorizontalPadding, end = AlbumDetailHorizontalPadding, top = 8.dp, bottom = 0.dp),
                    animateIntro = animateIntro
                ),
                actions = {
                    IconButton(
                        onClick = onRefreshAsmrOne,
                        enabled = !isLoadingAsmrOne,
                        colors = sectionActionIconColors
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
        val asmrOnePanelState = when {
            asmrOneTree.isNotEmpty() && browser != null -> DirectoryTreePanelState.Content
            isAsmrOnePending -> DirectoryTreePanelState.Loading
            else -> DirectoryTreePanelState.Empty
        }
        item(key = "dlsite-one-content") {
            Box(modifier = dlsiteAnimatedSectionModifier(Modifier.fillMaxWidth(), animateIntro)) {
                StableOneDirectoryTreeContent(
                    targetState = asmrOnePanelState,
                    stateKey = treeStateKey,
                    modifier = Modifier.fillMaxWidth()
                ) { panelState ->
                    when (panelState) {
                        DirectoryTreePanelState.Content -> {
                            val browserValue = browser
                            if (browserValue == null) {
                                DlsiteDirectoryLoadingPanel()
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
                                    animateIntro = false,
                                    folderKeyPrefix = "asmr-folder",
                                    fileKeyPrefix = "asmr-file",
                                    fileContent = { file, selectionMode, selected, selectedPosition, enterSelectionMode, onSelectedChange ->
                                        val leaf = asmrLeafByRelPath[file.path]
                                        DirectoryFileRow(
                                            file = file.copy(showSubtitleStamp = file.subtitleSources.isNotEmpty()),
                                            loadRemoteFileSize = loadRemoteFileSize,
                                            onPrimary = {
                                                when (file.fileType) {
                                                    TreeFileType.Audio -> {
                                                        scope.launch {
                                                            val prepared = withContext(Dispatchers.Default) {
                                                                val start = asmrLeafByRelPath[file.path] ?: return@withContext null
                                                                val folderPath = file.path.substringBeforeLast('/', "")
                                                                val siblingLeaves = asmrLeafTracks.filter {
                                                                    it.relativePath.substringBeforeLast('/', "") == folderPath
                                                                }
                                                                val queueLeaves = siblingLeaves.ifEmpty { listOf(start) }
                                                                PreparedTrackPlayback(
                                                                    tracks = queueLeaves.sortedBy { SmartSortKey.of(it.title) }.map { it.toTrack() },
                                                                    startTrack = start.toTrack(),
                                                                    onlineLyrics = queueLeaves.associate { it.url to it.subtitles }
                                                                )
                                                            } ?: return@launch
                                                            com.asmr.player.util.OnlineLyricsStore.replaceAll(prepared.onlineLyrics)
                                                            onPlayTracks(album, prepared.tracks, prepared.startTrack)
                                                        }
                                                    }
                                                    TreeFileType.Video -> {
                                                        val item = file.playlistTarget?.toMediaItem()
                                                        if (item != null) {
                                                            onPlayMediaItems(listOf(item), 0)
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
                                                    TreeFileType.Image -> {
                                                        buildDirectoryImagePreviewRequest(
                                                            files = browserValue.files,
                                                            clickedPath = file.path,
                                                            toPreviewItem = { imageFile ->
                                                                val imageUrl = imageFile.url.takeIf { it.isNotBlank() } ?: return@buildDirectoryImagePreviewRequest null
                                                                ImagePreviewItem(
                                                                    key = imageFile.path,
                                                                    title = imageFile.title,
                                                                    openPathOrUrl = imageUrl,
                                                                    prepareImage = {
                                                                        ImagePreviewPreparedItem(
                                                                            imageModel = imageUrl,
                                                                            openPathOrUrl = imageUrl
                                                                        )
                                                                    }
                                                                )
                                                            }
                                                        )?.let(onPreviewImages) ?: onPreviewFile(
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
                                            onAddToPlaylist = if (file.fileType == TreeFileType.Audio) ({ onAddToPlaylistOne(file.path) }) else null
                                        )
                                    }
                                )
                            }
                        }
                        DirectoryTreePanelState.Loading -> DlsiteDirectoryLoadingPanel()
                        DirectoryTreePanelState.Empty -> DlsiteSectionEmptyState(
                            text = "ONE 暂未收录",
                            artworkKind = DlsiteEmptyArtworkKind.One,
                            modifier = Modifier
                        )
                        DirectoryTreePanelState.MissingRj -> Unit
                    }
                }
            }
        }
        item(key = "dlsite-gallery-section") {
            Column(modifier = dlsiteAnimatedSectionModifier(Modifier.fillMaxWidth(), animateIntro)) {
                AlbumDetailSectionHeading(
                    title = "Gallery",
                    modifier = Modifier.padding(horizontal = AlbumDetailHorizontalPadding, vertical = 8.dp),
                    actions = {
                        OutlinedButton(
                            onClick = { isGalleryPreviewRequested = true },
                            enabled = galleryFadeState.panel.kind == DlsiteContentKind.Content &&
                                galleryFadeState.panel.value.orEmpty().isNotEmpty() &&
                                !isGalleryPreviewRequested,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = colorScheme.primary,
                                disabledContentColor = colorScheme.textTertiary.copy(alpha = 0.72f)
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (galleryFadeState.panel.kind == DlsiteContentKind.Content && !isGalleryPreviewRequested) {
                                    colorScheme.primary.copy(alpha = 0.52f)
                                } else {
                                    colorScheme.textTertiary.copy(alpha = 0.22f)
                                }
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("预览")
                        }
                    }
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(DlsiteGallerySectionHeight)
                        .dlsiteContentFade(galleryFadeState),
                    contentAlignment = Alignment.Center
                ) {
                    when (galleryFadeState.panel.kind) {
                        DlsiteContentKind.Loading -> DlsiteGalleryAwaitingPreview(galleryCount = null)
                        DlsiteContentKind.Empty -> {
                            DlsiteSectionEmptyState(
                                text = "暂无样图",
                                artworkKind = DlsiteEmptyArtworkKind.Gallery,
                                modifier = Modifier.then(dlsiteAnimatedSectionModifier(Modifier, animateIntro))
                            )
                        }
                        DlsiteContentKind.Content -> {
                            val displayedGalleryImages = galleryFadeState.panel.value.orEmpty()
                            if (!isGalleryPreviewRequested) {
                                DlsiteGalleryAwaitingPreview(galleryCount = displayedGalleryImages.size)
                            } else {
                                val galleryPreviewItems = remember(displayedGalleryImages) {
                                    displayedGalleryImages.map(DlsiteGalleryImage::toPreviewItem)
                                }
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = AlbumDetailHorizontalPadding),
                                    horizontalArrangement = Arrangement.spacedBy(DlsiteGalleryThumbGap),
                                    contentPadding = PaddingValues(vertical = 10.dp)
                                ) {
                                    items(items = galleryPreviewItems, key = { it.key }, contentType = { "galleryThumb" }) { image ->
                                        Card(
                                            modifier = Modifier.size(width = DlsiteGalleryThumbWidth, height = DlsiteGalleryThumbHeight).clickable {
                                                buildGalleryImagePreviewRequest(
                                                    galleryItems = galleryPreviewItems,
                                                    clickedKey = image.key
                                                )?.let(onPreviewImages)
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                                        ) {
                                            AsmrAsyncImage(
                                                model = image.imageModel,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                placeholderCornerRadius = DlsiteGalleryThumbCornerRadius,
                                                loading = NoImageLoadingIndicator,
                                                modifier = Modifier.fillMaxSize()
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
        item(key = "dlsite-trial-header") {
            AlbumDetailSectionHeading(
                title = "试听 / 试看",
                modifier = dlsiteAnimatedSectionModifier(
                    Modifier.fillMaxWidth().padding(horizontal = AlbumDetailHorizontalPadding, vertical = 8.dp),
                    animateIntro = animateIntro
                ),
                actions = {
                    IconButton(
                        onClick = onRefreshTrial,
                        enabled = !isLoading && !isLoadingTrial,
                        colors = sectionActionIconColors
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新")
                    }
                    IconButton(
                        onClick = onDownloadTrial,
                        enabled = trialDownloadEnabled,
                        colors = sectionActionIconColors
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = "下载")
                    }
                }
            )
        }
        when (trialFadeState.panel.kind) {
            DlsiteContentKind.Loading -> {
                item(key = "dlsite-trial-content") {
                    Box(
                        modifier = dlsiteAnimatedSectionModifier(
                            Modifier
                                .fillMaxWidth()
                                .dlsiteContentFade(trialFadeState),
                            animateIntro
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        DlsiteTrialLoadingList()
                    }
                }
            }
            DlsiteContentKind.Empty -> {
                item(key = "dlsite-trial-content") {
                    DlsiteSectionEmptyState(
                        text = "暂无试听 / 试看",
                        artworkKind = DlsiteEmptyArtworkKind.Trial,
                        modifier = dlsiteAnimatedSectionModifier(
                            Modifier.dlsiteContentFade(trialFadeState),
                            animateIntro
                        )
                    )
                }
            }
            DlsiteContentKind.Content -> {
                if (isLoadingTrial && trialPanelTarget.kind == DlsiteContentKind.Content) {
                    item(key = "dlsite-trial-progress") {
                        LinearProgressIndicator(
                            modifier = dlsiteAnimatedSectionModifier(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = AlbumDetailHorizontalPadding)
                                    .dlsiteContentFade(trialFadeState),
                                animateIntro = animateIntro
                            )
                        )
                    }
                }
                items(items = videoTracks, key = { track -> if (track.id > 0L) track.id else track.path }, contentType = { "trialVideo" }) { track ->
                    Column(
                        modifier = dlsiteAnimatedSectionModifier(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AlbumDetailHorizontalPadding, vertical = 8.dp)
                                .dlsiteContentFade(trialFadeState),
                            animateIntro = animateIntro
                        )
                    ) {
                        Text(
                            text = translatedPageText(track.title, fileName = true),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        InlineVideoPlayer(
                            url = track.path,
                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                        )
                    }
                }
                items(items = audioTracks, key = { track -> if (track.id > 0L) track.id else track.path }, contentType = { "trialAudioTrack" }) { track ->
                    Box(
                        modifier = dlsiteAnimatedSectionModifier(
                            Modifier.fillMaxWidth().dlsiteContentFade(trialFadeState),
                            animateIntro
                        )
                    ) {
                        DlsiteTrialAudioItem(
                            track = track,
                            onClick = { onPlayTracks(album, audioTracks, track) },
                            onAddToPlaylist = { onAddToPlaylist(track) }
                        )
                    }
                }
            }
        }
        if (showPortraitSimilarWorks) {
            item(key = "portrait-similar-works") {
                Box(modifier = dlsiteAnimatedSectionModifier(Modifier.fillMaxWidth(), animateIntro)) {
                    portraitSimilarWorksContent()
                }
            }
        }
        item(key = "dlsite-recommendations") {
            Box(modifier = dlsiteAnimatedSectionModifier(Modifier.fillMaxWidth(), animateIntro)) {
                if (isInitialDlsiteLoading) {
                    DlsiteRecommendationsLoadingBlocks()
                } else {
                    DlsiteRecommendationsBlocks(
                        recommendations = dlsiteRecommendations,
                        onOpenAlbumByRj = onOpenAlbumByRj
                    )
                }
            }
        }
    }
}