package com.asmr.player.ui.library


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import com.asmr.player.data.remote.api.AsmrOneTrackNodeResponse
import com.asmr.player.data.remote.scraper.DLSITE_DOMAIN
import com.asmr.player.data.remote.scraper.DlsiteRecommendedWork
import com.asmr.player.data.remote.scraper.storeSegment
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.Track
import com.asmr.player.ui.common.core.isCompactWidth
import com.asmr.player.data.remote.dlsite.DlsiteLanguageEdition
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import com.asmr.player.ui.common.cover.EaraLogoLoadingIndicator
import com.asmr.player.ui.common.cover.ImagePreviewRequest
import com.asmr.player.ui.common.core.consumeTapThrough
import com.asmr.player.ui.groups.AlbumGroupsViewModel
import com.asmr.player.ui.playlists.PlaylistsViewModel
import com.asmr.player.ui.common.core.SearchBlockedKeywordsViewModel
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.theme.dynamicPageContainerColor
import java.util.UUID
import kotlin.math.roundToInt
import com.asmr.player.ui.library.albumdetail.AlbumDetailHeroBackground
import com.asmr.player.ui.library.albumdetail.AlbumDetailHeroContentGap
import com.asmr.player.ui.library.albumdetail.AlbumDetailHeroMotionState
import com.asmr.player.ui.library.albumdetail.AlbumDetailHorizontalPadding
import com.asmr.player.ui.library.albumdetail.AlbumDetailLandscapeArtworkBackdrop
import com.asmr.player.ui.library.albumdetail.AlbumDetailLandscapeArtworkCover
import com.asmr.player.ui.library.albumdetail.AlbumDetailLandscapeIdentity
import com.asmr.player.ui.library.albumdetail.AlbumDetailLandscapeSimilarWorksPane
import com.asmr.player.ui.library.albumdetail.albumDetailOnlineLoadPlan
import com.asmr.player.ui.library.albumdetail.AlbumDetailPortraitSimilarWorksRow
import com.asmr.player.ui.library.albumdetail.albumDetailScrolledContentFade
import com.asmr.player.ui.library.albumdetail.AlbumDetailScrolledContentFadeSpan
import com.asmr.player.ui.library.albumdetail.AlbumDetailUiState
import com.asmr.player.ui.library.albumdetail.AlbumDlsiteInfoBreadcrumbTabV2
import com.asmr.player.ui.library.albumdetail.AlbumDlsitePlayBreadcrumbTabV2
import com.asmr.player.ui.library.albumdetail.AlbumHeader
import com.asmr.player.ui.library.albumdetail.albumHeaderDownloadEnabled
import com.asmr.player.ui.library.albumdetail.AlbumHeroBlurLayerCache
import com.asmr.player.ui.library.albumdetail.AlbumLandscapeArtworkTopPadding
import com.asmr.player.ui.library.albumdetail.albumLandscapeCollapseDistance
import com.asmr.player.ui.library.albumdetail.AlbumLandscapeCurvePlaybackProgress
import com.asmr.player.ui.library.albumdetail.albumLandscapeDirectoryTop
import com.asmr.player.ui.library.albumdetail.AlbumLandscapeHeaderEndPadding
import com.asmr.player.ui.library.albumdetail.AlbumLandscapeHeaderLift
import com.asmr.player.ui.library.albumdetail.albumLandscapeHeaderStart
import com.asmr.player.ui.library.albumdetail.albumLandscapePaneViewportHeight
import com.asmr.player.ui.library.albumdetail.AlbumLandscapeSurfaceBorderWidth
import com.asmr.player.ui.library.albumdetail.albumLandscapeSurfaceHeight
import com.asmr.player.ui.library.albumdetail.AlbumLocalBreadcrumbTabV2
import com.asmr.player.ui.library.albumdetail.asmrOneDirectoryTreeStateKey
import com.asmr.player.ui.library.albumdetail.AsmrTreeUiEntry
import com.asmr.player.ui.library.albumdetail.buildDlsiteTrialDownloadTree
import com.asmr.player.ui.library.albumdetail.canUseAsmrOneOnlineTreeActions
import com.asmr.player.ui.library.albumdetail.IncrementalAlbumAction
import com.asmr.player.ui.library.albumdetail.LocalTreeUiEntry
import com.asmr.player.ui.library.albumdetail.OnlineDownloadSource
import com.asmr.player.ui.library.albumdetail.PendingOnlineSaveSelection
import com.asmr.player.ui.library.albumdetail.PlaylistAddTarget
import com.asmr.player.ui.library.albumdetail.readDlsitePlayAuthSnapshot
import com.asmr.player.ui.library.albumdetail.rememberAlbumLandscapeContentShape
import com.asmr.player.ui.library.albumdetail.resolveAlbumDetailRj
import com.asmr.player.ui.library.albumdetail.shouldUseAlbumDetailLandscapeLayout
import com.asmr.player.ui.library.albumdetail.AlbumDetailInitialIntroDurationMs


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AlbumDetailScreen(
    windowSizeClass: WindowSizeClass,
    albumId: Long? = null,
    rjCode: String? = null,
    onPlayTracks: (Album, List<Track>, Track) -> Unit,
    onPlayMediaItems: (List<MediaItem>, Int) -> Unit = { _, _ -> },
    onAddToQueue: (Album, Track) -> Boolean = { _, _ -> false },
    onAddMediaItemsToQueue: (List<MediaItem>) -> Unit = {},
    onAddMediaItemsToFavorites: (List<MediaItem>) -> Unit = {},
    onOpenPlaylistPicker: (MediaItem) -> Unit = {},
    onOpenDlsiteLogin: () -> Unit = {},
    onOpenAlbumByRj: (String, DlsiteRecommendedWork?) -> Unit = { _, _ -> },
    onSearchKeyword: (String) -> Unit = {},
    initialTab: Int? = null,
    playlistsViewModel: PlaylistsViewModel = hiltViewModel(),
    albumGroupsViewModel: AlbumGroupsViewModel = hiltViewModel(),
    blockedKeywordsViewModel: SearchBlockedKeywordsViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    heroBlurLayerCache: AlbumHeroBlurLayerCache,
    viewModel: AlbumDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cloudSyncSelectionDialogState by viewModel.cloudSyncSelectionDialogState.collectAsStateWithLifecycle()
    val colorScheme = AsmrTheme.colorScheme
    val lifecycleOwner = LocalLifecycleOwner.current
    val authStore = viewModel.dlsiteAuthStore
    var dlsitePlayAuthSnapshot by remember(authStore) {
        mutableStateOf(readDlsitePlayAuthSnapshot(authStore))
    }
    val hasDlsitePlayCredentials = dlsitePlayAuthSnapshot.canAuthenticate
    val actionScope = rememberCoroutineScope()
    val screenKey = remember(albumId, rjCode) {
        val idPart = albumId?.takeIf { it > 0 }?.toString().orEmpty()
        val rjPart = rjCode?.trim().orEmpty().uppercase()
        if (rjPart.isNotBlank()) "album:$rjPart" else "albumId:$idPart"
    }
    val introSessionKey = remember(screenKey) { "intro:${UUID.randomUUID()}" }
    // 入口决定固定展示的二级页面：本地库->本地，在线/搜索->DL，preferDlsitePlay->DL Play。
    // 不再提供页内 tab 切换与左右滑动。
    val selectedTab = remember(albumId, initialTab) {
        initialTab?.coerceIn(0, 2) ?: if (albumId != null && albumId > 0) 0 else 1
    }
    var isInitialRouteReady by remember(screenKey) {
        mutableStateOf(viewModel.hasCachedAlbum(albumId, rjCode))
    }
    val showAsmrDownloadDialogState = remember { mutableStateOf(false) }
    var showAsmrDownloadDialog by showAsmrDownloadDialogState
    val showOnlineSaveDialogState = remember { mutableStateOf(false) }
    var showOnlineSaveDialog by showOnlineSaveDialogState
    val pendingOnlineSaveSelectionState = remember { mutableStateOf<PendingOnlineSaveSelection?>(null) }
    var pendingOnlineSaveSelection by pendingOnlineSaveSelectionState
    val batchPlaylistItemsState = remember { mutableStateOf<List<MediaItem>?>(null) }
    var batchPlaylistItems by batchPlaylistItemsState
    val groupPickerAlbumIdState = remember { mutableStateOf<Long?>(null) }
    var groupPickerAlbumId by groupPickerAlbumIdState
    val downloadSourceState = remember { mutableStateOf(OnlineDownloadSource.AsmrOne) }
    var downloadSource by downloadSourceState
    val onlineSaveSourceState = remember { mutableStateOf(OnlineDownloadSource.AsmrOne) }
    var onlineSaveSource by onlineSaveSourceState
    val downloadDisabledPathsState = remember { mutableStateOf<Set<String>>(emptySet()) }
    var downloadDisabledPaths by downloadDisabledPathsState
    val saveDisabledPathsState = remember { mutableStateOf<Set<String>>(emptySet()) }
    var saveDisabledPaths by saveDisabledPathsState
    var incrementalPreparationJob by remember { mutableStateOf<Job?>(null) }
    val metaActionKeywordState = rememberSaveable { mutableStateOf<String?>(null) }
    var metaActionKeyword by metaActionKeywordState

    fun openMetaActions(value: String) {
        val keyword = value.trim()
        if (keyword.isNotBlank()) metaActionKeyword = keyword
    }

    DisposableEffect(lifecycleOwner, authStore, selectedTab, viewModel) {
        fun refreshAuthSnapshot() {
            val updated = readDlsitePlayAuthSnapshot(authStore)
            actionScope.launch {
                val didAuthStateChange = updated != dlsitePlayAuthSnapshot
                dlsitePlayAuthSnapshot = updated
                if (didAuthStateChange && selectedTab == 0) {
                    incrementalPreparationJob?.cancel()
                    showAsmrDownloadDialog = false
                    showOnlineSaveDialog = false
                    viewModel.invalidateDlsitePlayAccess()
                }
            }
        }
        val preferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            refreshAuthSnapshot()
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshAuthSnapshot()
            }
        }
        authStore.registerListener(preferenceListener)
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            authStore.unregisterListener(preferenceListener)
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.setListenTogetherRjSummaryPollingEnabled(true)
    }
    LaunchedEffect(albumId, rjCode) {
        val hasCachedAlbum = viewModel.hasCachedAlbum(albumId, rjCode)
        isInitialRouteReady = hasCachedAlbum
        if (!hasCachedAlbum) withFrameNanos { }
        viewModel.loadAlbumAndAwait(albumId, rjCode, force = false)
        isInitialRouteReady = true
    }
    DisposableEffect(screenKey, viewModel) {
        onDispose {
            viewModel.setListenTogetherRjSummaryPollingEnabled(false)
            viewModel.cancelActiveLoads()
            incrementalPreparationJob?.cancel()
        }
    }
    LaunchedEffect(pendingOnlineSaveSelection) {
        val selected = pendingOnlineSaveSelection ?: return@LaunchedEffect
        pendingOnlineSaveSelection = null
        viewModel.saveOnlineSelectedToLibrary(
            selectedLeafPaths = selected.paths,
            useDlsitePlayTree = selected.useDlsitePlayTree
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AsmrTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        val isCompact = windowSizeClass.widthSizeClass.isCompactWidth
        val configuration = LocalConfiguration.current
        val useLandscapeArtworkTide = shouldUseAlbumDetailLandscapeLayout(
            compactWidth = isCompact,
            screenWidthDp = configuration.screenWidthDp,
            screenHeightDp = configuration.screenHeightDp
        )

        Column(
            modifier = when {
                isCompact || useLandscapeArtworkTide -> Modifier.fillMaxSize()
                else -> Modifier
                    .fillMaxHeight()
                    .widthIn(max = 800.dp)
                    .fillMaxWidth()
            }
        ) {
            when (val state = uiState) {
                is AlbumDetailUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EaraLogoLoadingIndicator(tint = AsmrTheme.colorScheme.primary)
                    }
                }
                is AlbumDetailUiState.Removed -> Unit
                is AlbumDetailUiState.Success -> {
                    LaunchedEffect(screenKey) {
                        if (viewModel.isInitialIntroSettled()) return@LaunchedEffect
                        delay(AlbumDetailInitialIntroDurationMs)
                        // 这只是供之后新到数据判断是否还需入场动画的生命周期标记。
                        // 已经在树上的动画会自行完整收尾，计时结束时无需强制整页重组。
                        viewModel.markInitialIntroSettled()
                    }
                    val model = state.model
                    val album = model.displayAlbum
                    val asmrOneTree = model.asmrOneTree
                    val localActionRj = remember(
                        model.baseRjCode,
                        model.localAlbum?.rjCode,
                        model.localAlbum?.workId,
                        model.localAlbum?.title,
                        model.localAlbum?.path
                    ) {
                        resolveAlbumDetailRj(model.baseRjCode, model.localAlbum)
                    }
                    val hasValidLocalRj = localActionRj.isNotBlank()
                    val localOnlineSource = when {
                        asmrOneTree.isNotEmpty() -> OnlineDownloadSource.AsmrOne
                        hasDlsitePlayCredentials && model.dlsitePlayTree.isNotEmpty() -> OnlineDownloadSource.DlsitePlay
                        else -> null
                    }
                    fun prepareIncrementalAlbumAction(
                        action: IncrementalAlbumAction,
                        source: OnlineDownloadSource,
                        tree: List<AsmrOneTrackNodeResponse>
                    ) {
                        if (tree.isEmpty()) return

                        incrementalPreparationJob?.cancel()
                        incrementalPreparationJob = actionScope.launch {
                            val existing = try {
                                viewModel.resolveLocalIncrementalSelectionPaths(tree)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: Exception) {
                                viewModel.messageManager.showError("读取本地文件状态失败，请稍后重试")
                                return@launch
                            }
                            when (action) {
                                IncrementalAlbumAction.Download -> {
                                    downloadSource = source
                                    downloadDisabledPaths = existing.downloadedPaths
                                    showAsmrDownloadDialog = true
                                }
                                IncrementalAlbumAction.Save -> {
                                    onlineSaveSource = source
                                    saveDisabledPaths = existing.savedPaths
                                    showOnlineSaveDialog = true
                                }
                            }
                        }
                    }
                    val trialDownloadTree = remember(model.dlsiteTrialTracks) {
                        buildDlsiteTrialDownloadTree(model.dlsiteTrialTracks)
                    }
                    val shouldPlayInitialAnimations = !viewModel.isInitialIntroSettled()
                    val shouldAnimateHeaderIntro = true
                    val showTagManagerState = remember { mutableStateOf(false) }
                    var showTagManager by showTagManagerState
                    val tagManageTrackState = remember { mutableStateOf<Track?>(null) }
                    var tagManageTrack by tagManageTrackState
                    val localPreviewFileState = remember { mutableStateOf<LocalTreeUiEntry.File?>(null) }
                    var localPreviewFile by localPreviewFileState
                    val onlinePreviewFileState = remember { mutableStateOf<AsmrTreeUiEntry.File?>(null) }
                    var onlinePreviewFile by onlinePreviewFileState
                    val imagePreviewRequestState = remember { mutableStateOf<ImagePreviewRequest?>(null) }
                    var imagePreviewRequest by imagePreviewRequestState
                    var landscapeActiveListState by remember(screenKey, useLandscapeArtworkTide) {
                        mutableStateOf<LazyListState?>(null)
                    }
                    var landscapeFixedHeaderHeightPx by remember(screenKey, useLandscapeArtworkTide) {
                        mutableIntStateOf(0)
                    }
                    // 横竖屏使用的详情组合树差异很大。按布局模式隔离整个子树，旋转时先
                    // 完整移除旧树再插入新树，避免复用旧 SlotTable 位置造成结构错位。
                    key(useLandscapeArtworkTide) {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val heroDensity = LocalDensity.current
                        val landscapePageWidth = maxWidth
                        val pageContainerColor = dynamicPageContainerColor(colorScheme)
                        val landscapeArtworkSize = minOf(
                            maxWidth * 0.30f,
                            maxHeight * 0.56f
                        ).coerceIn(300.dp, 460.dp)
                        val landscapeContentTop = AlbumLandscapeArtworkTopPadding +
                            landscapeArtworkSize * 0.50f
                        val landscapeContentWaveDepth = (landscapeArtworkSize * 0.50f)
                            .coerceIn(166.dp, 220.dp)
                        val landscapeHeaderFallbackHeight = (landscapeContentWaveDepth + 36.dp)
                            .coerceIn(202.dp, 256.dp)
                        val landscapeHeaderMeasuredHeight = with(heroDensity) {
                            landscapeFixedHeaderHeightPx.toDp()
                        }
                        val landscapeDirectoryTop = if (landscapeFixedHeaderHeightPx > 0) {
                            albumLandscapeDirectoryTop(
                                headerHeight = landscapeHeaderMeasuredHeight,
                                headerLift = AlbumLandscapeHeaderLift
                            )
                        } else {
                            albumLandscapeDirectoryTop(
                                headerHeight = landscapeHeaderFallbackHeight,
                                headerLift = AlbumLandscapeHeaderLift
                            )
                        }
                        val landscapeHeaderStart = albumLandscapeHeaderStart(landscapeArtworkSize)
                        val landscapeHeaderAvailableWidth = (
                            maxWidth - landscapeHeaderStart - AlbumLandscapeHeaderEndPadding
                            ).coerceAtLeast(320.dp)
                        val heroHeightLimit = if (isCompact) {
                            maxHeight * 0.62f
                        } else {
                            maxHeight * 0.64f
                        }
                        val heroMinHeight = if (isCompact) 280.dp else 360.dp
                        val heroPreferredHeight = if (isCompact) {
                            maxWidth * 0.88f
                        } else {
                            maxWidth * 0.78f
                        }
                        val heroHeight = if (useLandscapeArtworkTide) {
                            maxHeight
                        } else {
                            heroPreferredHeight
                                .coerceAtLeast(heroMinHeight)
                                .coerceAtMost(heroHeightLimit.coerceAtLeast(heroMinHeight))
                        }
                        val contentViewportTop = if (useLandscapeArtworkTide) {
                            landscapeContentTop
                        } else {
                            heroHeight + AlbumDetailHeroContentGap
                        }
                        val contentViewportHeight = (maxHeight - contentViewportTop).coerceAtLeast(0.dp)
                        val contentFadeStartY = 0.dp
                        val contentFadeEndY = AlbumDetailScrolledContentFadeSpan

                        // 随滑动自适应缩放 hero：布局边界仍是 0%~50% 折叠。
                        // 只有展开端允许封面图继续放大，松手后缓慢回落；折叠端到 50% 后直接交给列表滚动。
                        val heroCollapseMaxPx = with(heroDensity) {
                            if (useLandscapeArtworkTide) {
                                albumLandscapeCollapseDistance(landscapeArtworkSize).toPx()
                            } else {
                                (heroHeight * 0.5f).toPx()
                            }
                        }
                        val heroVisualOvershootMaxPx = with(heroDensity) {
                            if (useLandscapeArtworkTide) 0f else (heroHeight * 0.10f).toPx()
                        }
                        val contentViewportTopPx = with(heroDensity) { contentViewportTop.toPx() }
                        val heroMotion = remember(screenKey) { AlbumDetailHeroMotionState() }
                        val scope = rememberCoroutineScope()
                        LaunchedEffect(heroCollapseMaxPx, heroVisualOvershootMaxPx) {
                            heroMotion.collapsePx = heroMotion.collapsePx.coerceIn(0f, heroCollapseMaxPx)
                            heroMotion.visualOvershootPx = heroMotion.visualOvershootPx.coerceIn(
                                -heroVisualOvershootMaxPx,
                                0f
                            )
                        }
                        DisposableEffect(heroMotion) {
                            onDispose { heroMotion.cancelVisualOvershootAnimation() }
                        }
                        val heroNestedScroll = rememberAlbumDetailHeroNestedScrollConnection(
                            heroMotion = heroMotion,
                            scope = scope,
                            heroCollapseMaxPx = heroCollapseMaxPx,
                            heroVisualOvershootMaxPx = heroVisualOvershootMaxPx
                        )

                        fun headerAlbumForTab(tab: Int): Album {
                            return if (tab == 0) (model.localAlbum ?: album) else album
                        }

                        fun shouldShowCoverLoading(tab: Int, headerAlbum: Album): Boolean {
                            val headerHasCover = headerAlbum.coverPath.trim().isNotBlank() ||
                                headerAlbum.coverUrl.trim().isNotBlank()
                            return !headerHasCover && when (tab) {
                                0 -> false
                                1 -> model.isLoadingDlsite ||
                                    model.isLoadingAsmrOne ||
                                    !model.hasResolvedInitialDlsiteTarget
                                else -> model.isLoadingDlsite ||
                                    model.isLoadingDlsitePlay ||
                                    !model.hasResolvedInitialDlsiteTarget
                            }
                        }

                        val activeHeroAlbum = headerAlbumForTab(selectedTab)
                        val showHeroCoverLoadingState = shouldShowCoverLoading(selectedTab, activeHeroAlbum)

                        val headerContent: @Composable (Int) -> Unit = { tab ->
                            val isLocalTab = tab == 0
                            val resolvedInitialTarget = model.hasResolvedInitialDlsiteTarget
                            val canUseAsmrOneTreeActions = canUseAsmrOneOnlineTreeActions(
                                selectedTab = tab,
                                hasAsmrOneTree = asmrOneTree.isNotEmpty()
                            )
                            val headerDownloadEnabled = albumHeaderDownloadEnabled(
                                selectedTab = tab,
                                hasAsmrOneTree = asmrOneTree.isNotEmpty(),
                                hasDlsitePlayTree = model.dlsitePlayTree.isNotEmpty(),
                                hasResolvedInitialDlsiteTarget = resolvedInitialTarget,
                                hasValidLocalRj = hasValidLocalRj,
                                hasDlsitePlayCredentials = hasDlsitePlayCredentials
                            )
                            val headerAlbum = headerAlbumForTab(tab)
                            val incrementalSource = when (tab) {
                                0 -> localOnlineSource
                                1 -> OnlineDownloadSource.AsmrOne
                                2 -> OnlineDownloadSource.DlsitePlay
                                else -> null
                            }
                            val incrementalTree = when (incrementalSource) {
                                OnlineDownloadSource.AsmrOne -> asmrOneTree
                                OnlineDownloadSource.DlsitePlay -> model.dlsitePlayTree
                                else -> emptyList()
                            }
                            val headerDlsiteEditions = if (isLocalTab) {
                                emptyList()
                            } else {
                                model.dlsiteEditions.ifEmpty {
                                    listOf(
                                        DlsiteLanguageEdition(
                                            workno = model.baseRjCode.ifBlank { model.rjCode },
                                            lang = "JPN",
                                            label = "日本語",
                                            displayOrder = 1
                                        )
                                    )
                                }
                            }
                            AlbumHeader(
                                album = headerAlbum,
                                dlsiteUrl = model.dlsiteWorkno.takeIf { it.isNotBlank() }?.let { "$DLSITE_DOMAIN${storeSegment()}/work/=/product_id/$it.html" }.orEmpty(),
                                asmrOneUrl = model.asmrOneWorkId?.takeIf { it.isNotBlank() }?.let { "https://asmr.one/work/$it" }.orEmpty(),
                                dlsiteEditions = headerDlsiteEditions,
                                dlsiteSelectedLang = model.dlsiteSelectedLang,
                                onDlsiteLangSelected = { viewModel.selectDlsiteLanguage(it) },
                                showSaveAction = tab != 2,
                                onDownloadClick = {
                                    incrementalSource?.let { source ->
                                        prepareIncrementalAlbumAction(
                                            action = IncrementalAlbumAction.Download,
                                            source = source,
                                            tree = incrementalTree
                                        )
                                    }
                                },
                                showDlsitePlayLossless = tab == 2,
                                onLosslessDownloadClick = {
                                    viewModel.downloadDlsitePlayLosslessArchive()
                                },
                                onSaveClick = {
                                    incrementalSource?.let { source ->
                                        prepareIncrementalAlbumAction(
                                            action = IncrementalAlbumAction.Save,
                                            source = source,
                                            tree = incrementalTree
                                        )
                                    }
                                },
                                downloadEnabled = headerDownloadEnabled,
                                losslessDownloadEnabled = tab == 2 && resolvedInitialTarget && model.dlsitePlayTree.isNotEmpty(),
                                saveEnabled = if (isLocalTab) headerDownloadEnabled else canUseAsmrOneTreeActions,
                                showGroupButton = isLocalTab && model.localAlbum != null,
                                onOpenGroupPicker = { id -> groupPickerAlbumId = id },
                                introSessionKey = introSessionKey,
                                animateIntro = shouldAnimateHeaderIntro,
                                availableWidth = if (useLandscapeArtworkTide) {
                                    landscapeHeaderAvailableWidth
                                } else {
                                    (maxWidth - AlbumDetailHorizontalPadding * 2).coerceAtLeast(0.dp)
                                },
                                messageManager = viewModel.messageManager,
                                onMetaLongClick = ::openMetaActions,
                                landscapeFloatingActions = useLandscapeArtworkTide
                            )
                        }

                        val listHeaderContent: @Composable (Int) -> Unit = { tab ->
                            if (!useLandscapeArtworkTide) {
                                headerContent(tab)
                            }
                        }

                        val landscapeFixedHeaderContent: @Composable () -> Unit = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AlbumDetailLandscapeIdentity(
                                    album = headerAlbumForTab(selectedTab),
                                    introSessionKey = introSessionKey,
                                    animateIntro = shouldPlayInitialAnimations,
                                    pageContainerColor = pageContainerColor,
                                    listenTogetherRjListenerCount = model.listenTogetherRjListenerCount,
                                    messageManager = viewModel.messageManager,
                                    onMetaLongClick = ::openMetaActions
                                )
                                headerContent(selectedTab)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(pageContainerColor)
                                .clipToBounds()
                                .zIndex(0f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .consumeTapThrough()
                            )

                            if (useLandscapeArtworkTide) {
                                AlbumDetailLandscapeArtworkBackdrop(
                                    album = activeHeroAlbum,
                                    coverSessionKey = screenKey,
                                    artworkSize = landscapeArtworkSize,
                                    pageContainerColor = pageContainerColor,
                                    collapsePx = { heroMotion.collapsePx },
                                    modifier = Modifier
                                        .matchParentSize()
                                        .zIndex(0f)
                                )
                                AlbumDetailLandscapeArtworkCover(
                                    album = activeHeroAlbum,
                                    coverSessionKey = screenKey,
                                    introSessionKey = introSessionKey,
                                    animateIntro = shouldAnimateHeaderIntro,
                                    artworkSize = landscapeArtworkSize,
                                    showCoverLoadingState = showHeroCoverLoadingState,
                                    collapsePx = { heroMotion.collapsePx },
                                    collapseMaxPx = heroCollapseMaxPx,
                                    modifier = Modifier
                                        .matchParentSize()
                                        .zIndex(1f)
                                )
                            } else {
                                AlbumDetailHeroBackground(
                                    album = activeHeroAlbum,
                                    coverSessionKey = screenKey,
                                    introSessionKey = introSessionKey,
                                    animateIntro = shouldAnimateHeaderIntro,
                                    height = heroHeight,
                                    pageContainerColor = pageContainerColor,
                                    listenTogetherRjListenerCount = model.listenTogetherRjListenerCount,
                                    showCoverLoadingState = showHeroCoverLoadingState,
                                    messageManager = viewModel.messageManager,
                                    onMetaLongClick = ::openMetaActions,
                                    blurLayerCache = heroBlurLayerCache,
                                    collapsePx = { heroMotion.collapsePx },
                                    collapseMaxPx = heroCollapseMaxPx,
                                    visualOvershootPx = { heroMotion.visualOvershootPx },
                                    visualOvershootMaxPx = heroVisualOvershootMaxPx,
                                    modifier = Modifier.align(Alignment.TopCenter)
                                )
                            }

                            LaunchedEffect(
                                selectedTab,
                                model.rjCode,
                                model.dlsiteWorkno,
                                model.hasResolvedInitialDlsiteTarget,
                                model.hasResolvedAsmrOneContent,
                                asmrOneTree.isNotEmpty(),
                                hasDlsitePlayCredentials,
                                dlsitePlayAuthSnapshot.fingerprint,
                                localActionRj,
                                isInitialRouteReady
                            ) {
                                val loadPlan = albumDetailOnlineLoadPlan(
                                    selectedTab = selectedTab,
                                    hasResolvedInitialDlsiteTarget = model.hasResolvedInitialDlsiteTarget,
                                    isInitialRouteReady = isInitialRouteReady,
                                    hasValidLocalRj = hasValidLocalRj,
                                    hasResolvedAsmrOneContent = model.hasResolvedAsmrOneContent,
                                    hasAsmrOneTree = asmrOneTree.isNotEmpty(),
                                    hasDlsitePlayCredentials = hasDlsitePlayCredentials
                                )
                                if (loadPlan.loadDlsite) {
                                    viewModel.ensureDlsiteLoaded()
                                }
                                if (loadPlan.loadAsmrOne) {
                                    viewModel.ensureAsmrOneLoaded()
                                }
                                if (loadPlan.loadDlsitePlay) {
                                    viewModel.ensureDlsitePlayLoaded(showFailureMessage = selectedTab != 0)
                                }
                            }

                            val asmrOneTreeStateKey = asmrOneDirectoryTreeStateKey(
                                currentRj = model.rjCode,
                                baseRj = model.baseRjCode
                            )
                            val asmrOneScrollStateKey = "scroll:$asmrOneTreeStateKey"
                            val landscapeContentShape = rememberAlbumLandscapeContentShape(
                                waveDepth = landscapeContentWaveDepth
                            )
                            val contentSurfaceModifier = if (useLandscapeArtworkTide) {
                                Modifier
                                    .nestedScroll(heroNestedScroll)
                                    .shadow(
                                        elevation = if (colorScheme.isDark) 3.dp else 8.dp,
                                        shape = landscapeContentShape,
                                        clip = false
                                    )
                                    .clip(landscapeContentShape)
                                    .background(
                                        colorScheme.surface.copy(
                                            alpha = if (colorScheme.isDark) 0.92f else 0.94f
                                        )
                                    )
                                    .border(
                                        width = AlbumLandscapeSurfaceBorderWidth,
                                        color = colorScheme.primary.copy(
                                            alpha = if (colorScheme.isDark) 0.20f else 0.12f
                                        ),
                                        shape = landscapeContentShape
                                    )
                            } else {
                                Modifier
                                    .nestedScroll(heroNestedScroll)
                                    .clipToBounds()
                                    .background(pageContainerColor)
                                    .albumDetailScrolledContentFade(
                                        fadeStartY = contentFadeStartY,
                                        fadeEndY = contentFadeEndY,
                                        fadeColor = pageContainerColor
                                    )
                            }

                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth()
                                    .height(
                                        if (useLandscapeArtworkTide) {
                                            albumLandscapeSurfaceHeight(
                                                contentViewportHeight = contentViewportHeight,
                                                artworkSize = landscapeArtworkSize
                                            )
                                        } else {
                                            contentViewportHeight + heroHeight * 0.5f
                                        }
                                    )
                                    .offset {
                                        IntOffset(
                                            0,
                                            (contentViewportTopPx - heroMotion.collapsePx).roundToInt()
                                        )
                                    }
                                    .then(contentSurfaceModifier)
                                    .zIndex(if (useLandscapeArtworkTide) 2f else 0f)
                            ) {
                                if (useLandscapeArtworkTide) {
                                    AlbumLandscapeCurvePlaybackProgress(
                                        waveDepth = landscapeContentWaveDepth,
                                        modifier = Modifier
                                            .matchParentSize()
                                            .zIndex(1f)
                                    )
                                }

                                Box(
                                    modifier = if (useLandscapeArtworkTide) {
                                        Modifier
                                            .align(Alignment.TopEnd)
                                            .fillMaxHeight()
                                            .width(
                                                (landscapePageWidth - landscapeHeaderStart)
                                                    .coerceAtLeast(320.dp)
                                            )
                                            .padding(top = landscapeDirectoryTop)
                                    } else {
                                        Modifier.fillMaxSize()
                                    }
                                ) {
                                    when (selectedTab) {
                                    0 -> {
                                        val local = model.localAlbum
                                        if (local != null) {
                                            val localTreeStateKey = remember(albumId, rjCode, local.id) {
                                                val rjNorm = rjCode?.trim().orEmpty().uppercase()
                                                when {
                                                    albumId != null && albumId > 0 -> "localTree:id:$albumId"
                                                    rjNorm.isNotBlank() -> "localTree:rj:$rjNorm"
                                                    else -> "localTree:localId:${local.id}"
                                                }
                                            }
                                            AlbumLocalBreadcrumbTabV2(
                                                stateKey = localTreeStateKey,
                                                initialCurrentPath = viewModel.getPreferredTreeCurrentPath(localTreeStateKey)
                                                    .ifBlank { viewModel.getTreeCurrentPath(localTreeStateKey) },
                                                onPersistCurrentPath = { path ->
                                                    viewModel.persistTreeCurrentPath(localTreeStateKey, path)
                                                },
                                                initialScroll = viewModel.getListScrollPosition("scroll:$localTreeStateKey"),
                                                onPersistScroll = { index, offset ->
                                                    viewModel.persistListScrollPosition("scroll:$localTreeStateKey", index, offset)
                                                },
                                                topContentPadding = 0.dp,
                                                album = local,
                                                header = { listHeaderContent(0) },
                                                onPlayMediaItems = onPlayMediaItems,
                                                onAddToQueue = { track ->
                                                    onAddToQueue(local, track)
                                                },
                                                onAddMediaItemsToQueue = onAddMediaItemsToQueue,
                                                onAddMediaItemsToFavorites = onAddMediaItemsToFavorites,
                                                onOpenBatchPlaylistPicker = { items -> batchPlaylistItems = items },
                                                preferredCurrentPath = viewModel.getPreferredTreeCurrentPath(localTreeStateKey),
                                                onTogglePreferredCurrentPath = { path, enabled ->
                                                    if (enabled) {
                                                        viewModel.persistPreferredTreeCurrentPath(localTreeStateKey, path)
                                                        viewModel.messageManager.showSuccess("已设为默认打开目录")
                                                    } else {
                                                        viewModel.clearPreferredTreeCurrentPath(localTreeStateKey)
                                                    }
                                                },
                                                onAddToPlaylist = { track ->
                                                    val target = PlaylistAddTarget.fromTrack(local, track)
                                                    onOpenPlaylistPicker(target.toMediaItem())
                                                },
                                                onDownloadOnlineTrack = { track, relativePath ->
                                                    viewModel.downloadSavedOnlineTrack(track, relativePath)
                                                },
                                                onManageTrackTags = { track ->
                                                    tagManageTrack = track
                                                },
                                                onRemoveTrack = { track ->
                                                    if (track.id > 0L) libraryViewModel.removeTrackFromAlbum(track.id)
                                                },
                                                onDeleteTreeEntry = { target, onComplete ->
                                                    libraryViewModel.deleteAlbumTreeEntry(
                                                        album = local,
                                                        target = target,
                                                        onComplete = onComplete,
                                                    )
                                                },
                                                onSetCoverFromImage = { pathOrUri ->
                                                    viewModel.setLocalCoverPath(pathOrUri)
                                                },
                                                onPreviewImages = { request -> imagePreviewRequest = request },
                                                onPreviewFile = { localPreviewFile = it },
                                                onSubtitleGenerationError = viewModel.messageManager::showError,
                                                onSubtitleGenerationUnavailable = viewModel.messageManager::showWarning,
                                                onSubtitleGenerationQueued = viewModel.messageManager::showInfo,
                                                onListStateAvailable = { landscapeActiveListState = it },
                                                animateIntro = shouldPlayInitialAnimations
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (albumId != null && albumId > 0) {
                                                    EaraLogoLoadingIndicator(tint = AsmrTheme.colorScheme.primary)
                                                } else {
                                                    Text("未下载到本地")
                                                }
                                            }
                                        }
                                    }
                                    1 -> AlbumDlsiteInfoBreadcrumbTabV2(
                                        album = album,
                                        header = { listHeaderContent(1) },
                                        galleryUrls = model.dlsiteGalleryUrls,
                                        trialTracks = model.dlsiteTrialTracks,
                                        trialDownloadEnabled = trialDownloadTree.isNotEmpty(),
                                        isLoading = model.isLoadingDlsite,
                                        isAwaitingInitialLoad = !model.hasLoadedInitialDlsiteContent,
                                        isAwaitingAsmrOneLoad = !model.hasResolvedAsmrOneContent &&
                                            asmrOneTree.isEmpty(),
                                        hasResolvedAsmrOneContent = model.hasResolvedAsmrOneContent,
                                        asmrOneTree = asmrOneTree,
                                        isLoadingAsmrOne = model.isLoadingAsmrOne,
                                        isLoadingTrial = model.isLoadingDlsiteTrial,
                                        onRefreshAsmrOne = { viewModel.refreshAsmrOneSection() },
                                        onRefreshTrial = { viewModel.refreshDlsiteTrialSection() },
                                        onDownloadTrial = {
                                            downloadDisabledPaths = emptySet()
                                            downloadSource = OnlineDownloadSource.DlsiteTrial
                                            showAsmrDownloadDialog = true
                                        },
                                        onPlayTracks = onPlayTracks,
                                        onPlayMediaItems = onPlayMediaItems,
                                        onAddToQueue = { track ->
                                            onAddToQueue(album, track)
                                        },
                                        onAddMediaItemsToQueue = onAddMediaItemsToQueue,
                                        onAddMediaItemsToFavorites = onAddMediaItemsToFavorites,
                                        onOpenBatchPlaylistPicker = { items -> batchPlaylistItems = items },
                                        onDownloadOne = { relPath ->
                                            viewModel.downloadAsmrOneSelected(setOf(relPath))
                                        },
                                        onAddToPlaylistOne = { relPath ->
                                            val target = PlaylistAddTarget.fromAsmrOne(album, asmrOneTree, relPath) ?: return@AlbumDlsiteInfoBreadcrumbTabV2
                                            onOpenPlaylistPicker(target.toMediaItem())
                                        },
                                        onAddToPlaylist = { track ->
                                            val target = PlaylistAddTarget.fromTrack(album, track)
                                            onOpenPlaylistPicker(target.toMediaItem())
                                        },
                                        onPreviewImages = { request -> imagePreviewRequest = request },
                                        onPreviewFile = { onlinePreviewFile = it },
                                        treeStateKey = asmrOneTreeStateKey,
                                        initialCurrentPath = viewModel.getTreeCurrentPath(asmrOneTreeStateKey),
                                        topContentPadding = 0.dp,
                                        animateIntro = shouldPlayInitialAnimations,
                                        onPersistCurrentPath = { path ->
                                            viewModel.persistTreeCurrentPath(asmrOneTreeStateKey, path)
                                        },
                                        initialScroll = viewModel.getListScrollPosition(asmrOneScrollStateKey),
                                        onPersistScroll = { index, offset ->
                                            viewModel.persistListScrollPosition(asmrOneScrollStateKey, index, offset)
                                        },
                                        onListStateAvailable = { landscapeActiveListState = it },
                                        showPortraitSimilarWorks = !useLandscapeArtworkTide,
                                        portraitSimilarWorksContent = {
                                            AlbumDetailPortraitSimilarWorksRow(
                                                seedRjCode = model.baseRjCode.ifBlank { model.rjCode },
                                                seedMetadata = model.dlsiteInfo ?: model.displayAlbum,
                                                isRouteReady = isInitialRouteReady,
                                                onOpenAlbumByRj = onOpenAlbumByRj,
                                                viewModel = viewModel
                                            )
                                        },
                                        dlsiteRecommendations = model.dlsiteRecommendations,
                                        onOpenAlbumByRj = onOpenAlbumByRj,
                                        loadRemoteFileSize = { viewModel.loadRemoteFileSize(it) }
                                    )
                                    else -> AlbumDlsitePlayBreadcrumbTabV2(
                                        header = { listHeaderContent(2) },
                                        authStore = authStore,
                                        album = album,
                                        rjCode = model.rjCode,
                                        tree = model.dlsitePlayTree,
                                        isLoading = model.isLoadingDlsitePlay,
                                        shouldAutoLoad = selectedTab == 2 && model.hasResolvedInitialDlsiteTarget,
                                        isAwaitingInitialTarget = selectedTab == 2 && !model.hasResolvedInitialDlsiteTarget,
                                        hasResolvedDlsitePlayContent = model.hasResolvedDlsitePlayContent,
                                        onOpenLogin = onOpenDlsiteLogin,
                                        onEnsureLoaded = { viewModel.ensureDlsitePlayLoaded() },
                                        onPlayMediaItems = onPlayMediaItems,
                                        onAddToQueue = { track ->
                                            onAddToQueue(album, track)
                                        },
                                        onAddMediaItemsToQueue = onAddMediaItemsToQueue,
                                        onAddMediaItemsToFavorites = onAddMediaItemsToFavorites,
                                        onOpenBatchPlaylistPicker = { items -> batchPlaylistItems = items },
                                        onDownloadOne = { relPath ->
                                            viewModel.downloadDlsitePlaySelected(setOf(relPath))
                                        },
                                        onPreviewImages = { request -> imagePreviewRequest = request },
                                        onPreviewFile = { onlinePreviewFile = it },
                                        prepareImagePreview = viewModel::prepareDlsitePlayImagePreview,
                                        treeStateKey = "tree:dlsitePlay:${model.baseRjCode.ifBlank { model.rjCode }.trim().uppercase()}",
                                        initialCurrentPath = viewModel.getTreeCurrentPath("tree:dlsitePlay:${model.baseRjCode.ifBlank { model.rjCode }.trim().uppercase()}"),
                                        topContentPadding = 0.dp,
                                        animateIntro = shouldPlayInitialAnimations,
                                        onPersistCurrentPath = { path ->
                                            val rj = model.baseRjCode.ifBlank { model.rjCode }.trim().uppercase()
                                            viewModel.persistTreeCurrentPath("tree:dlsitePlay:$rj", path)
                                        },
                                        initialScroll = viewModel.getListScrollPosition("scroll:tree:dlsitePlay:${model.baseRjCode.ifBlank { model.rjCode }.trim().uppercase()}"),
                                        onPersistScroll = { index, offset ->
                                            viewModel.persistListScrollPosition("scroll:tree:dlsitePlay:${model.baseRjCode.ifBlank { model.rjCode }.trim().uppercase()}", index, offset)
                                        },
                                        onListStateAvailable = { landscapeActiveListState = it },
                                        loadRemoteFileSize = { viewModel.loadRemoteFileSize(it) }
                                    )
                                    }
                                }

                                if (useLandscapeArtworkTide) {
                                    AlbumDetailLandscapeSimilarWorksPane(
                                        seedRjCode = model.baseRjCode.ifBlank { model.rjCode },
                                        seedMetadata = model.dlsiteInfo ?: model.displayAlbum,
                                        isRouteReady = isInitialRouteReady,
                                        onOpenAlbumByRj = onOpenAlbumByRj,
                                        viewModel = viewModel,
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .width(landscapeHeaderStart)
                                            .albumLandscapePaneViewportHeight(
                                                collapsePx = { heroMotion.collapsePx },
                                                collapseMaxPx = heroCollapseMaxPx
                                            )
                                            .padding(
                                                start = 20.dp,
                                                end = 12.dp,
                                                top = landscapeContentWaveDepth * 1.10f
                                            )
                                    )

                                }
                            }

                            if (useLandscapeArtworkTide) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .width(
                                            (landscapePageWidth - landscapeHeaderStart)
                                                .coerceAtLeast(320.dp)
                                        )
                                        .wrapContentHeight()
                                        .offset {
                                            IntOffset(
                                                0,
                                                (
                                                    contentViewportTopPx -
                                                        AlbumLandscapeHeaderLift.toPx() -
                                                        heroMotion.collapsePx
                                                    ).roundToInt()
                                            )
                                        }
                                        .padding(end = AlbumLandscapeHeaderEndPadding)
                                        .onSizeChanged { size ->
                                            landscapeFixedHeaderHeightPx = size.height
                                        }
                                        .pointerInput(
                                            heroCollapseMaxPx,
                                            landscapeActiveListState
                                        ) {
                                            detectVerticalDragGestures { change, dragAmount ->
                                                val requestedScroll = -dragAmount
                                                var consumedScroll = 0f

                                                if (requestedScroll > 0f) {
                                                    val currentCollapse = heroMotion.collapsePx
                                                    val targetCollapse = (currentCollapse + requestedScroll)
                                                        .coerceIn(0f, heroCollapseMaxPx)
                                                    val appliedCollapse = targetCollapse - currentCollapse
                                                    if (appliedCollapse != 0f) {
                                                        heroMotion.cancelVisualOvershootAnimation()
                                                        heroMotion.collapsePx = targetCollapse
                                                        consumedScroll += appliedCollapse
                                                    }
                                                    val listDelta = requestedScroll - appliedCollapse
                                                    if (listDelta > 0f) {
                                                        consumedScroll += landscapeActiveListState
                                                            ?.dispatchRawDelta(listDelta)
                                                            ?: 0f
                                                    }
                                                } else if (requestedScroll < 0f) {
                                                    val listConsumed = landscapeActiveListState
                                                        ?.dispatchRawDelta(requestedScroll)
                                                        ?: 0f
                                                    consumedScroll += listConsumed
                                                    val collapseDelta = requestedScroll - listConsumed
                                                    if (collapseDelta < 0f) {
                                                        val currentCollapse = heroMotion.collapsePx
                                                        val targetCollapse = (currentCollapse + collapseDelta)
                                                            .coerceIn(0f, heroCollapseMaxPx)
                                                        val appliedCollapse = targetCollapse - currentCollapse
                                                        if (appliedCollapse != 0f) {
                                                            heroMotion.cancelVisualOvershootAnimation()
                                                            heroMotion.collapsePx = targetCollapse
                                                            consumedScroll += appliedCollapse
                                                        }
                                                    }
                                                }

                                                if (consumedScroll != 0f) change.consume()
                                            }
                                        }
                                        .zIndex(3f)
                                ) {
                                    landscapeFixedHeaderContent()
                                }
                            }

                        }
                    }
                }

                AlbumDetailDialogHosts(
                    viewModel = viewModel,
                    libraryViewModel = libraryViewModel,
                    playlistsViewModel = playlistsViewModel,
                    albumGroupsViewModel = albumGroupsViewModel,
                    blockedKeywordsViewModel = blockedKeywordsViewModel,
                    windowSizeClass = windowSizeClass,
                    album = album,
                    asmrOneTree = asmrOneTree,
                    dlsitePlayTree = model.dlsitePlayTree,
                    trialDownloadTree = trialDownloadTree,
                    selectedTab = selectedTab,
                    hasValidLocalRj = hasValidLocalRj,
                    localOnlineSource = localOnlineSource,
                    onSearchKeyword = onSearchKeyword,
                    showAsmrDownloadDialogState = showAsmrDownloadDialogState,
                    showOnlineSaveDialogState = showOnlineSaveDialogState,
                    pendingOnlineSaveSelectionState = pendingOnlineSaveSelectionState,
                    batchPlaylistItemsState = batchPlaylistItemsState,
                    groupPickerAlbumIdState = groupPickerAlbumIdState,
                    downloadSourceState = downloadSourceState,
                    onlineSaveSourceState = onlineSaveSourceState,
                    downloadDisabledPathsState = downloadDisabledPathsState,
                    saveDisabledPathsState = saveDisabledPathsState,
                    metaActionKeywordState = metaActionKeywordState,
                    tagManageTrackState = tagManageTrackState,
                    showTagManagerState = showTagManagerState,
                    localPreviewFileState = localPreviewFileState,
                    onlinePreviewFileState = onlinePreviewFileState,
                    imagePreviewRequestState = imagePreviewRequestState
                )
            }
                is AlbumDetailUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        cloudSyncSelectionDialogState?.let { dialogState ->
            CloudSyncSelectionDialog(
                state = dialogState,
                onSelect = viewModel::confirmCloudSyncSelection,
                onCancel = viewModel::cancelCloudSyncSelection
            )
        }
    }
}

