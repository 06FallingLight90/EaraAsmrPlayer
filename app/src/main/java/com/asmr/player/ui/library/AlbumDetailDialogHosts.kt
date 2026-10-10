package com.asmr.player.ui.library

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import com.asmr.player.data.remote.api.AsmrOneTrackNodeResponse
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.Track
import com.asmr.player.ui.common.cover.ImagePreviewDialog
import com.asmr.player.ui.common.cover.ImagePreviewRequest
import com.asmr.player.ui.common.dialog.RoundedTopSheet
import com.asmr.player.ui.common.dialog.TagAssignDialog
import com.asmr.player.ui.groups.AlbumGroupPickerScreen
import com.asmr.player.ui.common.core.SearchBlockedKeywordsViewModel
import com.asmr.player.ui.groups.AlbumGroupsViewModel
import com.asmr.player.ui.library.albumdetail.AsmrOneDownloadDialog
import com.asmr.player.ui.library.albumdetail.AsmrTreeUiEntry
import com.asmr.player.ui.library.albumdetail.FilePreviewDialog
import com.asmr.player.ui.library.albumdetail.LocalTreeUiEntry
import com.asmr.player.ui.library.albumdetail.OnlineDownloadSource
import com.asmr.player.ui.library.albumdetail.OnlineSaveDialog
import com.asmr.player.ui.library.albumdetail.PendingOnlineSaveSelection
import com.asmr.player.ui.library.albumdetail.canUseAsmrOneOnlineTreeActions
import com.asmr.player.ui.playlists.PlaylistPickerScreen
import com.asmr.player.ui.playlists.PlaylistsViewModel
import com.asmr.player.ui.theme.AsmrTheme

/** 详情页对话框/浮层宿主。自 AlbumDetailScreen Success 分支尾部抽出，行为逐字保持；
 *  原页面 remember 状态经 MutableState 参数注入，本函数内以同名 by 委托零改动引用。 */
@Composable
internal fun AlbumDetailDialogHosts(
    viewModel: AlbumDetailViewModel,
    libraryViewModel: LibraryViewModel,
    playlistsViewModel: PlaylistsViewModel,
    albumGroupsViewModel: AlbumGroupsViewModel,
    blockedKeywordsViewModel: SearchBlockedKeywordsViewModel,
    windowSizeClass: WindowSizeClass,
    album: Album,
    asmrOneTree: List<AsmrOneTrackNodeResponse>,
    dlsitePlayTree: List<AsmrOneTrackNodeResponse>,
    trialDownloadTree: List<AsmrOneTrackNodeResponse>,
    selectedTab: Int,
    hasValidLocalRj: Boolean,
    localOnlineSource: OnlineDownloadSource?,
    onSearchKeyword: (String) -> Unit,
    showAsmrDownloadDialogState: MutableState<Boolean>,
    showOnlineSaveDialogState: MutableState<Boolean>,
    pendingOnlineSaveSelectionState: MutableState<PendingOnlineSaveSelection?>,
    batchPlaylistItemsState: MutableState<List<MediaItem>?>,
    groupPickerAlbumIdState: MutableState<Long?>,
    downloadSourceState: MutableState<OnlineDownloadSource>,
    onlineSaveSourceState: MutableState<OnlineDownloadSource>,
    downloadDisabledPathsState: MutableState<Set<String>>,
    saveDisabledPathsState: MutableState<Set<String>>,
    metaActionKeywordState: MutableState<String?>,
    tagManageTrackState: MutableState<Track?>,
    showTagManagerState: MutableState<Boolean>,
    localPreviewFileState: MutableState<LocalTreeUiEntry.File?>,
    onlinePreviewFileState: MutableState<AsmrTreeUiEntry.File?>,
    imagePreviewRequestState: MutableState<ImagePreviewRequest?>,
) {
    val colorScheme = AsmrTheme.colorScheme
    var showAsmrDownloadDialog by showAsmrDownloadDialogState
    var showOnlineSaveDialog by showOnlineSaveDialogState
    var pendingOnlineSaveSelection by pendingOnlineSaveSelectionState
    var batchPlaylistItems by batchPlaylistItemsState
    var groupPickerAlbumId by groupPickerAlbumIdState
    var downloadSource by downloadSourceState
    var onlineSaveSource by onlineSaveSourceState
    var downloadDisabledPaths by downloadDisabledPathsState
    var saveDisabledPaths by saveDisabledPathsState
    var metaActionKeyword by metaActionKeywordState
    var tagManageTrack by tagManageTrackState
    var showTagManager by showTagManagerState
    var localPreviewFile by localPreviewFileState
    var onlinePreviewFile by onlinePreviewFileState
    var imagePreviewRequest by imagePreviewRequestState

    val canSaveOnline = if (selectedTab == 0) {
        hasValidLocalRj && localOnlineSource != null
    } else {
        canUseAsmrOneOnlineTreeActions(
            selectedTab = selectedTab,
            hasAsmrOneTree = asmrOneTree.isNotEmpty()
        )
    }
    if (showAsmrDownloadDialog) {
        val downloadTree = when (downloadSource) {
            OnlineDownloadSource.AsmrOne -> asmrOneTree
            OnlineDownloadSource.DlsitePlay -> dlsitePlayTree
            OnlineDownloadSource.DlsiteTrial -> trialDownloadTree
        }
        AsmrOneDownloadDialog(
            albumTitle = album.title,
            trackTree = downloadTree,
            disabledPaths = downloadDisabledPaths,
            onDismiss = { showAsmrDownloadDialog = false },
            onConfirm = { selected ->
                when (downloadSource) {
                    OnlineDownloadSource.AsmrOne -> viewModel.downloadAsmrOneSelected(selected)
                    OnlineDownloadSource.DlsitePlay -> viewModel.downloadDlsitePlaySelected(selected)
                    OnlineDownloadSource.DlsiteTrial -> viewModel.downloadDlsiteTrialSelected(selected)
                }
                showAsmrDownloadDialog = false
            }
        )
    }

    if (showOnlineSaveDialog && canSaveOnline) {
        val saveTree = when (onlineSaveSource) {
            OnlineDownloadSource.DlsitePlay -> dlsitePlayTree
            else -> asmrOneTree
        }
        OnlineSaveDialog(
            albumTitle = album.title,
            trackTree = saveTree,
            disabledPaths = saveDisabledPaths,
            onDismiss = { showOnlineSaveDialog = false },
            onConfirm = { selected ->
                pendingOnlineSaveSelection = PendingOnlineSaveSelection(
                    paths = selected,
                    useDlsitePlayTree = onlineSaveSource == OnlineDownloadSource.DlsitePlay
                )
                showOnlineSaveDialog = false
            }
        )
    }

    groupPickerAlbumId?.let { targetAlbumId ->
        RoundedTopSheet(
            onDismissRequest = { groupPickerAlbumId = null },
            color = MaterialTheme.colorScheme.background,
            contentColor = colorScheme.textPrimary
        ) {
            AlbumGroupPickerScreen(
                windowSizeClass = windowSizeClass,
                albumId = targetAlbumId,
                onBack = { groupPickerAlbumId = null },
                embeddedInDialog = true
            )
        }
    }

    batchPlaylistItems?.let { items ->
        RoundedTopSheet(
            onDismissRequest = { batchPlaylistItems = null },
            color = MaterialTheme.colorScheme.background,
            contentColor = colorScheme.textPrimary
        ) {
            // T8：专辑详情批量入口顺势升级双目标（歌单 | 合集）；初始 tab 保持歌单。
            PlaylistPickerScreen(
                windowSizeClass = windowSizeClass,
                items = items,
                onBack = { batchPlaylistItems = null },
                embeddedInDialog = true,
                groupTarget = albumGroupsViewModel.asBatchAddGroupTarget()
            )
        }
    }

    if (localPreviewFile != null) {
        FilePreviewDialog(
            title = localPreviewFile!!.title,
            absolutePath = localPreviewFile!!.absolutePath,
            fileType = localPreviewFile!!.fileType,
            messageManager = viewModel.messageManager,
            loadOnlineText = viewModel::loadOnlineTextPreview,
            onDismiss = { localPreviewFile = null }
        )
    }

    if (onlinePreviewFile != null) {
        FilePreviewDialog(
            title = onlinePreviewFile!!.title,
            absolutePath = onlinePreviewFile!!.url ?: "",
            fileType = onlinePreviewFile!!.fileType,
            messageManager = viewModel.messageManager,
            loadOnlineText = viewModel::loadOnlineTextPreview,
            onDismiss = { onlinePreviewFile = null }
        )
    }

    imagePreviewRequest?.let { request ->
        ImagePreviewDialog(
            request = request,
            messageManager = viewModel.messageManager,
            onDismiss = { imagePreviewRequest = null }
        )
    }

    metaActionKeyword?.let { keyword ->
        val searchBlockedKeywords by blockedKeywordsViewModel.searchBlockedKeywords.collectAsStateWithLifecycle()
        AlbumMetaActionDialog(
            keyword = keyword,
            onDismissRequest = { metaActionKeyword = null },
            onSearch = onSearchKeyword,
            onCreatePlaylist = playlistsViewModel::createPlaylist,
            onCreateGroup = albumGroupsViewModel::createGroup,
            onAddBlockedKeyword = { value ->
                val normalized = value.trim()
                if (normalized.isNotBlank()) {
                    val exists = searchBlockedKeywords.any {
                        it.equals(normalized, ignoreCase = true)
                    }
                    blockedKeywordsViewModel.addSearchBlockedKeyword(normalized)
                    if (exists) {
                        viewModel.messageManager.showInfo("屏蔽词已存在：$normalized")
                    } else {
                        viewModel.messageManager.showSuccess("已添加屏蔽词：$normalized")
                    }
                }
            },
        )
    }

    val track = tagManageTrack
    if ((track != null && track.id > 0L) || showTagManager) {
        val availableTags by viewModel.availableTags.collectAsStateWithLifecycle()
        val userTagsByTrackId by viewModel.userTagsByTrackId.collectAsStateWithLifecycle()
        if (track != null && track.id > 0L) {
            TagAssignDialog(
                title = track.title,
                inheritedTags = album.tags,
                userTags = userTagsByTrackId[track.id].orEmpty(),
                allTags = availableTags,
                onApplyUserTags = { list ->
                    viewModel.setUserTagsForTrack(track.id, list)
                    tagManageTrack = null
                },
                onDismiss = { tagManageTrack = null },
                onOpenTagManager = { showTagManager = true }
            )
        }

        if (showTagManager) {
            Dialog(
                onDismissRequest = { showTagManager = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    TagManagerSheet(
                        tags = availableTags,
                        onRename = { tagId, newName -> libraryViewModel.renameUserTag(tagId, newName) },
                        onDelete = { tagId -> libraryViewModel.deleteUserTag(tagId) },
                        onClose = { showTagManager = false }
                    )
                }
            }
        }
    }
}
