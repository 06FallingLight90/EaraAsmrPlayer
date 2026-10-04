package com.asmr.player.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.asmr.player.data.remote.scraper.resolveRecommendedWorkHeroCoverUrl
import com.asmr.player.ui.downloads.DownloadsScreen
import com.asmr.player.ui.downloads.DownloadsViewModel
import com.asmr.player.ui.dlsite.DlsiteLoginScreen
import com.asmr.player.ui.dlsite.DlsiteLoginViewModel
import com.asmr.player.ui.groups.AlbumGroupsViewModel
import com.asmr.player.ui.library.AlbumDetailScreen
import com.asmr.player.ui.library.LibraryFilterScreen
import com.asmr.player.ui.library.LibraryViewModel
import com.asmr.player.ui.nav.AlbumCoverHintStore
import com.asmr.player.ui.nav.AppNavigator
import com.asmr.player.ui.player.PlayerViewModel
import com.asmr.player.ui.playlists.PlaylistDetailScreen
import com.asmr.player.ui.playlists.PlaylistsViewModel
import com.asmr.player.ui.playlists.SystemPlaylistScreen
import com.asmr.player.ui.search.SearchAssistScreen
import com.asmr.player.ui.search.SearchAssistSearchRequest
import com.asmr.player.ui.common.core.SearchBlockedKeywordsViewModel
import com.asmr.player.util.isVideoPlaybackItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 二级路由屏幕装配所需的宿主依赖。跨屏状态只经此参数通道进出
 * MainContainer；路由注册与参数解析在 MainNavGraph.kt（Graph 层）。
 * 行为档案：docs/behavior-notes/maincontainer-routes.md
 */
internal class MainRouteHost(
    val navController: NavHostController,
    val navigator: AppNavigator,
    val windowSizeClass: WindowSizeClass,
    val activityViewModelStoreOwner: androidx.lifecycle.ViewModelStoreOwner,
    val playerViewModel: PlayerViewModel,
    val libraryViewModel: LibraryViewModel,
    val blockedKeywordsViewModel: SearchBlockedKeywordsViewModel,
    val downloadsViewModel: DownloadsViewModel,
    val scope: CoroutineScope,
    val secondaryPageTopPadding: Dp,
    val searchAssistInitialRequest: SearchAssistSearchRequest,
    val downloadsScrollToTopSignal: Long,
    val albumDetailStackPopTargetEntryId: String?,
    val setAlbumDetailStackPopTargetEntryId: (String?) -> Unit,
    val setAlbumDetailPageOffsetReader: ((() -> Float)?) -> Unit,
    val setAlbumDetailExitInProgress: (Boolean) -> Unit,
    val setManualRjInput: (String) -> Unit,
    val setShowManualRjDialog: () -> Unit,
    val setAlbumBatchPlaylistPickerRequest: (BatchPlaylistPickerRequest?) -> Unit,
    val openNowPlaying: () -> Unit,
    val requestMiniPlayerPlayFeedback: () -> Unit,
    val submitMetaSearchKeyword: (String) -> Unit,
    val submitSearchAssistRequest: (SearchAssistSearchRequest) -> Unit
)

/**
 * 组装二级路由屏幕装配回调。search 路由的 savedStateHandle 回传桥与
 * MainContainer 的 submittedSearch* 状态强耦合，由宿主自行提供（searchBridge）。
 * album_detail 两族保持独立装配：byRj 无 onLocalAlbumRemoved，byId 有
 * removeAlbumFromQueue 联动——这是有意的原状不对称。
 */
internal fun buildMainRouteContents(
    host: MainRouteHost,
    searchBridge: @Composable (NavBackStackEntry) -> Unit
): MainNavGraphContents {
    return MainNavGraphContents(
        libraryFilter = {
            SecondaryPageBackground(topPadding = host.secondaryPageTopPadding) {
                LibraryFilterScreen(
                    onClose = { host.navController.popBackStack() },
                    viewModel = host.libraryViewModel
                )
            }
        },
        search = searchBridge,
        searchAssist = { initialKeyword ->
            val resolvedInitialRequest = if (initialKeyword.isBlank()) {
                host.searchAssistInitialRequest
            } else {
                host.searchAssistInitialRequest.copy(keyword = initialKeyword)
            }
            SecondaryPageBackground(topPadding = host.secondaryPageTopPadding) {
                SearchAssistScreen(
                    windowSizeClass = host.windowSizeClass,
                    initialRequest = resolvedInitialRequest,
                    onSubmitSearch = host.submitSearchAssistRequest
                )
            }
        },
        albumDetailByRj = { entry, rj, initialTab ->
            val playlistsViewModel: PlaylistsViewModel = hiltViewModel(host.activityViewModelStoreOwner)
            val albumGroupsViewModel: AlbumGroupsViewModel = hiltViewModel(host.activityViewModelStoreOwner)
            AlbumDetailRouteFrame(
                backStackEntry = entry,
                previousBackStackEntry = host.navController.previousBackStackEntry,
                stackPopTargetEntryId = host.albumDetailStackPopTargetEntryId,
                onPopBackStack = { targetEntryId ->
                    host.setAlbumDetailStackPopTargetEntryId(targetEntryId)
                    host.navController.popBackStack()
                },
                onPageOffsetReader = { reader ->
                    if (host.navController.currentBackStackEntry?.id == entry.id) {
                        host.setAlbumDetailPageOffsetReader(reader)
                    }
                },
                onExitStateChanged = { host.setAlbumDetailExitInProgress(it) },
                onEditRj = { currentRj ->
                    host.setManualRjInput(currentRj)
                    host.setShowManualRjDialog()
                }
            ) { albumDetailViewModel, heroBlurLayerCache ->
                AlbumDetailScreen(
                    windowSizeClass = host.windowSizeClass,
                    rjCode = rj,
                    initialTab = initialTab,
                    onPlayTracks = { album, tracks, startTrack ->
                        host.scope.launch {
                            if (host.playerViewModel.playTracksPrepared(album, tracks, startTrack)) {
                                host.requestMiniPlayerPlayFeedback()
                            }
                        }
                    },
                    onPlayMediaItems = { items, startIndex ->
                        host.playerViewModel.playMediaItems(items, startIndex)
                        val startItem = items.getOrNull(startIndex)
                        if (startItem.isVideoPlaybackItem()) {
                            host.openNowPlaying()
                        } else if (startItem != null) {
                            host.requestMiniPlayerPlayFeedback()
                        }
                    },
                    onAddToQueue = { album, track ->
                        host.playerViewModel.addTrackToQueue(album, track)
                    },
                    onAddMediaItemsToQueue = { items ->
                        host.playerViewModel.addMediaItemsToQueue(items)
                    },
                    onAddMediaItemsToFavorites = { items ->
                        playlistsViewModel.addItemsToFavoritesInBackground(items)
                    },
                    onOpenPlaylistPicker = { item ->
                        host.setAlbumBatchPlaylistPickerRequest(BatchPlaylistPickerRequest(listOf(item)))
                    },
                    onOpenDlsiteLogin = { host.navController.navigateSingleTop("dlsite_login") },
                    onOpenAlbumByRj = { targetRj, work ->
                        AlbumCoverHintStore.record(
                            albumId = null,
                            rjCode = targetRj,
                            title = work?.title,
                            circle = null,
                            coverUrl = resolveRecommendedWorkHeroCoverUrl(targetRj, work?.coverUrl)
                        )
                        host.navigator.openAlbumDetailByRjStacked(targetRj)
                    },
                    onSearchKeyword = host.submitMetaSearchKeyword,
                    playlistsViewModel = playlistsViewModel,
                    albumGroupsViewModel = albumGroupsViewModel,
                    blockedKeywordsViewModel = host.blockedKeywordsViewModel,
                    libraryViewModel = host.libraryViewModel,
                    heroBlurLayerCache = heroBlurLayerCache,
                    viewModel = albumDetailViewModel
                )
            }
        },
        albumDetailById = { entry, albumId, rjCode, initialTab ->
            val playlistsViewModel: PlaylistsViewModel = hiltViewModel(host.activityViewModelStoreOwner)
            val albumGroupsViewModel: AlbumGroupsViewModel = hiltViewModel(host.activityViewModelStoreOwner)
            AlbumDetailRouteFrame(
                backStackEntry = entry,
                previousBackStackEntry = host.navController.previousBackStackEntry,
                stackPopTargetEntryId = host.albumDetailStackPopTargetEntryId,
                onPopBackStack = { targetEntryId ->
                    host.setAlbumDetailStackPopTargetEntryId(targetEntryId)
                    host.navController.popBackStack()
                },
                onPageOffsetReader = { reader ->
                    if (host.navController.currentBackStackEntry?.id == entry.id) {
                        host.setAlbumDetailPageOffsetReader(reader)
                    }
                },
                onExitStateChanged = { host.setAlbumDetailExitInProgress(it) },
                onLocalAlbumRemoved = { removed ->
                    host.playerViewModel.removeAlbumFromQueue(removed.albumId, removed.mediaIds)
                },
                onEditRj = { currentRj ->
                    host.setManualRjInput(currentRj)
                    host.setShowManualRjDialog()
                }
            ) { albumDetailViewModel, heroBlurLayerCache ->
                AlbumDetailScreen(
                    windowSizeClass = host.windowSizeClass,
                    albumId = albumId,
                    rjCode = rjCode,
                    initialTab = initialTab,
                    onPlayTracks = { album, tracks, startTrack ->
                        host.scope.launch {
                            if (host.playerViewModel.playTracksPrepared(album, tracks, startTrack)) {
                                host.requestMiniPlayerPlayFeedback()
                            }
                        }
                    },
                    onPlayMediaItems = { items, startIndex ->
                        host.playerViewModel.playMediaItems(items, startIndex)
                        val startItem = items.getOrNull(startIndex)
                        if (startItem.isVideoPlaybackItem()) {
                            host.openNowPlaying()
                        } else if (startItem != null) {
                            host.requestMiniPlayerPlayFeedback()
                        }
                    },
                    onAddToQueue = { album, track ->
                        host.playerViewModel.addTrackToQueue(album, track)
                    },
                    onAddMediaItemsToQueue = { items ->
                        host.playerViewModel.addMediaItemsToQueue(items)
                    },
                    onAddMediaItemsToFavorites = { items ->
                        playlistsViewModel.addItemsToFavoritesInBackground(items)
                    },
                    onOpenPlaylistPicker = { item ->
                        host.setAlbumBatchPlaylistPickerRequest(BatchPlaylistPickerRequest(listOf(item)))
                    },
                    onOpenDlsiteLogin = { host.navController.navigateSingleTop("dlsite_login") },
                    onOpenAlbumByRj = { targetRj, work ->
                        AlbumCoverHintStore.record(
                            albumId = null,
                            rjCode = targetRj,
                            title = work?.title,
                            circle = null,
                            coverUrl = resolveRecommendedWorkHeroCoverUrl(targetRj, work?.coverUrl)
                        )
                        host.navigator.openAlbumDetailByRjStacked(targetRj)
                    },
                    onSearchKeyword = host.submitMetaSearchKeyword,
                    playlistsViewModel = playlistsViewModel,
                    albumGroupsViewModel = albumGroupsViewModel,
                    blockedKeywordsViewModel = host.blockedKeywordsViewModel,
                    libraryViewModel = host.libraryViewModel,
                    heroBlurLayerCache = heroBlurLayerCache,
                    viewModel = albumDetailViewModel
                )
            }
        },
        groupDetail = { groupId, groupName ->
            SecondaryPageBackground(topPadding = host.secondaryPageTopPadding) {
                com.asmr.player.ui.groups.AlbumGroupDetailScreen(
                    windowSizeClass = host.windowSizeClass,
                    groupId = groupId,
                    title = groupName,
                    onPlayMediaItems = { items, startIndex ->
                        host.playerViewModel.playMediaItems(items, startIndex)
                        val startItem = items.getOrNull(startIndex)
                        if (startItem.isVideoPlaybackItem()) {
                            host.openNowPlaying()
                        } else if (startItem != null) {
                            host.requestMiniPlayerPlayFeedback()
                        }
                    }
                )
            }
        },
        playlistDetail = { playlistId, playlistName ->
            SecondaryPageBackground(topPadding = host.secondaryPageTopPadding) {
                PlaylistDetailScreen(
                    windowSizeClass = host.windowSizeClass,
                    playlistId = playlistId,
                    title = playlistName,
                    onPlayAll = { items, startItem ->
                        host.playerViewModel.playPlaylistItems(items, startItem)
                        if (startItem.isVideoPlaybackItem()) {
                            host.openNowPlaying()
                        } else {
                            host.requestMiniPlayerPlayFeedback()
                        }
                    }
                )
            }
        },
        playlistSystem = { type ->
            if (type == "favorites") {
                Box(modifier = Modifier.fillMaxSize())
            } else {
                val playlistsViewModel: PlaylistsViewModel = hiltViewModel(host.activityViewModelStoreOwner)
                SecondaryPageBackground(topPadding = host.secondaryPageTopPadding) {
                    SystemPlaylistScreen(
                        windowSizeClass = host.windowSizeClass,
                        onPlayAll = { items, startItem ->
                            host.playerViewModel.playPlaylistItems(items, startItem)
                            if (startItem.isVideoPlaybackItem()) {
                                host.openNowPlaying()
                            } else {
                                host.requestMiniPlayerPlayFeedback()
                            }
                        },
                        viewModel = playlistsViewModel
                    )
                }
            }
        },
        downloads = {
            SecondaryPageBackground(topPadding = host.secondaryPageTopPadding) {
                DownloadsScreen(
                    windowSizeClass = host.windowSizeClass,
                    scrollToTopSignal = host.downloadsScrollToTopSignal,
                    viewModel = host.downloadsViewModel
                )
            }
        },
        dlsiteLogin = {
            val dlsiteLoginViewModel: DlsiteLoginViewModel = hiltViewModel(host.activityViewModelStoreOwner)
            SecondaryPageBackground(topPadding = host.secondaryPageTopPadding) {
                DlsiteLoginScreen(
                    windowSizeClass = host.windowSizeClass,
                    onDone = { host.navController.popBackStack() },
                    viewModel = dlsiteLoginViewModel
                )
            }
        }
    )
}
