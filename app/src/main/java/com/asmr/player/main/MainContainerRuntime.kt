package com.asmr.player.main

import android.app.Activity
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.geometry.Rect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.navigation.NavHostController
import androidx.activity.compose.BackHandler
import com.asmr.player.data.local.datastore.SettingsDataStore
import com.asmr.player.domain.model.AppVolume
import com.asmr.player.hotlistening.ListeningTracker
import com.asmr.player.service.PlaybackService
import com.asmr.player.ui.library.AlbumDetailViewModel
import com.asmr.player.util.BulkProgress
import com.asmr.player.ui.library.CloudSyncSelectionDialog
import com.asmr.player.ui.library.CloudSyncSelectionDialogState
import com.asmr.player.ui.library.LibraryViewModel
import com.asmr.player.ui.nav.Routes
import com.asmr.player.ui.player.CoverDragPreviewState
import com.asmr.player.ui.player.CoverMotionState
import com.asmr.player.ui.player.MiniPlayerDisplayMode
import com.asmr.player.ui.player.PlayerViewModel
import com.asmr.player.ui.player.rememberCoverDragPreviewState
import com.asmr.player.ui.player.rememberCoverMotionState
import com.asmr.player.ui.search.SearchAssistSearchRequest
import com.asmr.player.ui.common.dialog.FlatTextFieldDialog
import com.asmr.player.data.settings.CoverPreviewMode
import com.asmr.player.util.MessageManager
import com.asmr.player.util.isVideoPlaybackItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// ——————————————————————————————————————————————
// 硬件音量浮层状态
// ——————————————————————————————————————————————

@Stable
internal class HardwareVolumeOverlayState(
    val showOverlayState: MutableState<Boolean>,
    val interactingState: MutableState<Boolean>,
    val holdTickState: MutableLongState,
    val lastHandledKeyTickState: MutableLongState,
    val nowPlayingEventTickState: MutableLongState,
    val lastNonZeroAppVolumePercentState: MutableIntState,
    val boundsState: MutableState<Rect?>
) {
    var showOverlay by showOverlayState
    var interacting by interactingState
    var holdTick by holdTickState
    var lastHandledKeyTick by lastHandledKeyTickState
    var nowPlayingEventTick by nowPlayingEventTickState
    var lastNonZeroAppVolumePercent by lastNonZeroAppVolumePercentState
    var bounds by boundsState
}

@Composable
internal fun rememberHardwareVolumeOverlayState(): HardwareVolumeOverlayState {
    val showOverlay = remember { mutableStateOf(false) }
    val interacting = remember { mutableStateOf(false) }
    val holdTick = remember { mutableLongStateOf(0L) }
    val lastHandledKeyTick = remember { mutableLongStateOf(0L) }
    val nowPlayingEventTick = remember { mutableLongStateOf(0L) }
    val lastNonZeroAppVolumePercent = rememberSaveable { mutableIntStateOf(AppVolume.DefaultPercent) }
    val bounds = remember { mutableStateOf<Rect?>(null) }
    return remember {
        HardwareVolumeOverlayState(
            showOverlayState = showOverlay,
            interactingState = interacting,
            holdTickState = holdTick,
            lastHandledKeyTickState = lastHandledKeyTick,
            nowPlayingEventTickState = nowPlayingEventTick,
            lastNonZeroAppVolumePercentState = lastNonZeroAppVolumePercent,
            boundsState = bounds
        )
    }
}

@Composable
internal fun HardwareVolumeOverlayEffects(
    state: HardwareVolumeOverlayState,
    volumeKeyEventTick: Long,
    appVolumePercent: Int,
    nowPlaying: NowPlayingPresentationState
) {
    LaunchedEffect(appVolumePercent) {
        if (appVolumePercent > 0) {
            state.lastNonZeroAppVolumePercent = appVolumePercent
        }
    }

    LaunchedEffect(volumeKeyEventTick) {
        if (volumeKeyEventTick <= 0L) return@LaunchedEffect
        if (volumeKeyEventTick == state.lastHandledKeyTick) return@LaunchedEffect
        state.lastHandledKeyTick = volumeKeyEventTick
        if (nowPlaying.usesInlineVolumeControl && !nowPlaying.equalizerVisible) {
            state.showOverlay = false
            state.nowPlayingEventTick = volumeKeyEventTick
            return@LaunchedEffect
        }
        state.showOverlay = true
        state.holdTick = volumeKeyEventTick
    }

    LaunchedEffect(state.showOverlay, state.holdTick, state.interacting, nowPlaying.usesInlineVolumeControl, nowPlaying.equalizerVisible) {
        if (!state.showOverlay) return@LaunchedEffect
        if (nowPlaying.usesInlineVolumeControl && !nowPlaying.equalizerVisible) {
            state.showOverlay = false
            state.bounds = null
            return@LaunchedEffect
        }
        if (state.interacting) return@LaunchedEffect
        val snapshot = state.holdTick
        delay(2_000)
        if (!state.interacting && state.holdTick == snapshot) {
            state.showOverlay = false
            state.bounds = null
        }
    }

    LaunchedEffect(nowPlaying.visible) {
        if (!nowPlaying.visible) {
            nowPlaying.usesInlineVolumeControl = false
            nowPlaying.equalizerVisible = false
            return@LaunchedEffect
        }
        state.showOverlay = false
        state.interacting = false
        state.bounds = null
        state.nowPlayingEventTick = 0L
    }
}

// ——————————————————————————————————————————————
// 启动副作用：首帧就绪回调、常听统计、冷启动直达路由
// ——————————————————————————————————————————————

@Composable
internal fun MainStartupEffects(
    navController: NavHostController,
    startRoute: String,
    initialDestination: String,
    onContentReady: () -> Unit,
    listeningTracker: ListeningTracker,
    playerViewModel: PlayerViewModel
) {
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        onContentReady()
    }
    LaunchedEffect(Unit) {
        listeningTracker.start(this, playerViewModel.playback)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, listeningTracker) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                listeningTracker.flushNow()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    LaunchedEffect(navController, startRoute, initialDestination) {
        if (startRoute.isBlank() || startRoute == initialDestination) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        if (isPrimaryRoute(startRoute)) {
            navController.navigatePrimaryRoute(startRoute)
        } else {
            navController.navigateSingleTop(startRoute)
        }
    }
}

// ——————————————————————————————————————————————
// mini 播放条展示模式 + 播放反馈信号
// ——————————————————————————————————————————————

@Stable
internal class MiniPlayerDisplayModeState(
    val displayModeState: MutableState<MiniPlayerDisplayMode>,
    val feedbackSignalState: MutableLongState
) {
    var displayMode by displayModeState
    var feedbackSignal by feedbackSignalState

    fun requestPlayFeedback() {
        feedbackSignal += 1L
    }
}

@Composable
internal fun rememberMiniPlayerDisplayModeState(
    settingsDataStore: SettingsDataStore
): MiniPlayerDisplayModeState {
    val stored by settingsDataStore.miniPlayerDisplayMode.collectAsStateWithLifecycle(
        initialValue = MiniPlayerDisplayMode.CoverOnly.name
    )
    val displayMode = rememberSaveable { mutableStateOf(MiniPlayerDisplayMode.CoverOnly) }
    LaunchedEffect(stored) {
        displayMode.value = runCatching {
            MiniPlayerDisplayMode.valueOf(stored)
        }.getOrElse {
            MiniPlayerDisplayMode.CoverOnly
        }
    }
    val feedbackSignal = remember { mutableLongStateOf(0L) }
    return remember { MiniPlayerDisplayModeState(displayMode, feedbackSignal) }
}

// ——————————————————————————————————————————————
// 共享播放器背景（迷你条/播放页封面拖拽与动效预览）
// ——————————————————————————————————————————————

@Stable
internal class SharedPlayerBackdropState(
    hasCurrentMediaItemState: State<Boolean>,
    itemState: State<MediaItem?>,
    private val coverMotionState: CoverMotionState,
    internal val coverDragPreviewState: CoverDragPreviewState,
    private val useDragPreview: Boolean,
    private val useMotionPreview: Boolean
) {
    val hasCurrentMediaItem by hasCurrentMediaItemState
    val item by itemState

    val backdropAlignment: Alignment
        get() = when {
            useDragPreview -> BiasAlignment(
                horizontalBias = coverDragPreviewState.horizontalBias,
                verticalBias = coverDragPreviewState.verticalBias
            )
            useMotionPreview -> BiasAlignment(
                horizontalBias = coverMotionState.horizontalBias,
                verticalBias = coverMotionState.verticalBias
            )
            else -> Alignment.Center
        }
}

@Composable
internal fun rememberSharedPlayerBackdropState(
    playerViewModel: PlayerViewModel,
    playerBackdropVisible: Boolean,
    coverBackgroundEnabled: Boolean,
    coverPreviewMode: CoverPreviewMode
): SharedPlayerBackdropState {
    val hasCurrentMediaItem = remember(playerViewModel) {
        playerViewModel.playback
            .map { it.currentMediaItem != null }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    val sharedPlayerItem = remember(playerViewModel) {
        playerViewModel.playback
            .map { it.currentMediaItem }
            .distinctUntilChanged { old, new ->
                old?.mediaId == new?.mediaId &&
                    old?.localConfiguration?.uri == new?.localConfiguration?.uri &&
                    old?.mediaMetadata?.artworkUri == new?.mediaMetadata?.artworkUri
            }
    }.collectAsStateWithLifecycle(initialValue = null)
    val sharedPlayerIsVideo = sharedPlayerItem.value.isVideoPlaybackItem()
    val videoOutputEnabled = shouldKeepVideoOutputEnabled(
        currentItemIsVideo = sharedPlayerIsVideo,
        miniPlayerEnabled = playerBackdropVisible,
        nowPlayingVisible = playerBackdropVisible
    )
    DisposableEffect(playerViewModel, videoOutputEnabled) {
        playerViewModel.setVideoOutputEnabled(videoOutputEnabled)
        onDispose {
            if (videoOutputEnabled) playerViewModel.setVideoOutputEnabled(false)
        }
    }
    val sharedUseDragPreview = playerBackdropVisible &&
        coverBackgroundEnabled &&
        coverPreviewMode == CoverPreviewMode.Drag &&
        !sharedPlayerIsVideo
    val sharedUseMotionPreview = playerBackdropVisible &&
        coverBackgroundEnabled &&
        coverPreviewMode == CoverPreviewMode.Motion &&
        !sharedPlayerIsVideo
    val sharedCoverMotionState = rememberCoverMotionState(
        enabled = sharedUseMotionPreview,
        resetKey = sharedPlayerItem.value?.mediaId
    )
    val sharedCoverDragPreviewState = rememberCoverDragPreviewState(
        enabled = sharedUseDragPreview,
        resetKey = sharedPlayerItem.value?.mediaId
    )
    return remember(
        hasCurrentMediaItem,
        sharedPlayerItem,
        sharedCoverMotionState,
        sharedCoverDragPreviewState,
        sharedUseDragPreview,
        sharedUseMotionPreview
    ) {
        SharedPlayerBackdropState(
            hasCurrentMediaItemState = hasCurrentMediaItem,
            itemState = sharedPlayerItem,
            coverMotionState = sharedCoverMotionState,
            coverDragPreviewState = sharedCoverDragPreviewState,
            useDragPreview = sharedUseDragPreview,
            useMotionPreview = sharedUseMotionPreview
        )
    }
}

// ——————————————————————————————————————————————
// 返回键处理：关抽屉 / 取消详情导航 / 库页双击退出
// ——————————————————————————————————————————————

@Composable
internal fun MainBackHandlers(
    drawerState: DrawerState,
    scope: CoroutineScope,
    touchBlock: NavTouchBlockState,
    currentRoute: String?,
    currentPrimaryRoute: String?,
    hasPreviousBackStackEntry: Boolean,
    nowPlayingVisible: Boolean,
    activity: Activity?,
    messageManager: MessageManager
) {
    val lastLibraryBackPressElapsedRealtime = remember { mutableLongStateOf(0L) }

    LaunchedEffect(currentPrimaryRoute, hasPreviousBackStackEntry, nowPlayingVisible, drawerState.isOpen) {
        if (currentPrimaryRoute != Routes.Library || hasPreviousBackStackEntry || nowPlayingVisible || drawerState.isOpen) {
            lastLibraryBackPressElapsedRealtime.longValue = 0L
        }
    }

    BackHandler(drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    BackHandler(touchBlock.pendingDetail && currentRoute == Routes.Search) {
        touchBlock.pendingDetail = false
        touchBlock.cancelPendingDetail = true
    }

    BackHandler(
        enabled = currentPrimaryRoute == Routes.Library &&
            !hasPreviousBackStackEntry &&
            !drawerState.isOpen &&
            !touchBlock.pendingDetail &&
            !nowPlayingVisible
    ) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastLibraryBackPressElapsedRealtime.longValue <= 2_000L) {
            activity?.let { currentActivity ->
                PlaybackService.requestShutdownForAppExit(currentActivity)
                currentActivity.finishAndRemoveTask()
            }
        } else {
            lastLibraryBackPressElapsedRealtime.longValue = now
            messageManager.showInfo("再按一次返回退出应用")
        }
    }
}

// ——————————————————————————————————————————————
// 手动 RJ 同步对话框 + 云同步选择对话框
// ——————————————————————————————————————————————

@Composable
internal fun MainDialogsHost(
    showManualRjDialog: Boolean,
    setShowManualRjDialog: (Boolean) -> Unit,
    manualRjInput: String,
    setManualRjInput: (String) -> Unit,
    navBackStackEntry: androidx.navigation.NavBackStackEntry?,
    currentRoute: String?,
    cloudSyncSelectionDialogState: CloudSyncSelectionDialogState?,
    bulkProgress: BulkProgress?,
    libraryViewModel: LibraryViewModel
) {
    if (showManualRjDialog && navBackStackEntry != null &&
        (currentRoute?.startsWith("album_detail/{albumId}") == true || currentRoute?.startsWith("album_detail/") == true)
    ) {
        val albumDetailViewModel: AlbumDetailViewModel = hiltViewModel(navBackStackEntry)
        FlatTextFieldDialog(
            onDismissRequest = { setShowManualRjDialog(false) },
            message = "请输入 DLsite 作品编号，支持 RJ、BJ、VJ；保存后将自动执行云同步。",
            value = manualRjInput,
            onValueChange = { setManualRjInput(it) },
            placeholder = "作品编号（如 BJ02370869）",
            confirmText = "同步",
            confirmEnabled = manualRjInput.trim().isNotBlank(),
            onConfirm = {
                setShowManualRjDialog(false)
                albumDetailViewModel.manualSetRjAndSync(manualRjInput.trim())
            },
        )
    }

    cloudSyncSelectionDialogState?.let { dialogState ->
        val ignoreAllHandler = if (bulkProgress != null) {
            { libraryViewModel.ignoreAllCloudSyncSelections() }
        } else {
            null
        }
        CloudSyncSelectionDialog(
            state = dialogState,
            onSelect = libraryViewModel::confirmCloudSyncSelection,
            onCancel = libraryViewModel::cancelCloudSyncSelection,
            onIgnoreAll = ignoreAllHandler
        )
    }
}
