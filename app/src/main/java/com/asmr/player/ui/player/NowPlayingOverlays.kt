package com.asmr.player.ui.player

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asmr.player.domain.model.AppVolume
import com.asmr.player.main.HardwareVolumeOverlay
import com.asmr.player.playback.PlaybackSnapshot
import com.asmr.player.service.AudioOutputRouteKind
import com.asmr.player.ui.common.audio.AppVolumeWarningSessionState
import com.asmr.player.ui.common.audio.EqualizerPanel
import com.asmr.player.ui.common.dialog.DismissOutsideBoundsOverlay
import com.asmr.player.ui.common.dialog.PlayerModalSheet
import com.asmr.player.ui.common.dialog.TagAssignDialog
import com.asmr.player.ui.common.list.rememberCalmScrollableFlingBehavior
import com.asmr.player.ui.player.nowplaying.SliceOverviewBar
import com.asmr.player.ui.player.nowplaying.SliceTimeEditDialog
import com.asmr.player.ui.player.nowplaying.currentSliceIdForPosition
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.util.Formatting
import kotlinx.coroutines.delay

/**
 * NowPlayingScreen 的覆盖层宿主族：标签指派对话框、切片管理 sheet + 时间编辑、
 * 均衡器 sheet（含硬件音量浮层）。内容逐块自 NowPlayingScreen 主体搬移。
 */

@Composable
internal fun NowPlayingTagDialogHost(
    dialog: NowPlayingTagViewModel.DialogState?,
    availableTags: List<com.asmr.player.domain.model.TagWithCount>,
    tagViewModel: NowPlayingTagViewModel
) {
    if (dialog != null) {
        TagAssignDialog(
            title = dialog.title,
            allTags = availableTags,
            inheritedTags = dialog.inheritedTags,
            userTags = dialog.userTags,
            onDismiss = { tagViewModel.dismiss() },
            onApplyUserTags = { tagViewModel.applyUserTags(it) }
        )
    }
}

@Composable
internal fun NowPlayingSliceOverlaysHost(
    viewModel: PlayerViewModel,
    sliceUiState: SliceUiState,
    progressDurationMs: Long,
    accentColor: Color,
    isVideo: Boolean,
    showSliceSheet: Boolean,
    dismissSliceSheet: () -> Unit,
    toggleSelectedSlice: (Long) -> Unit,
    timeEditTarget: Pair<Long, Boolean>?,
    setTimeEditTarget: (Pair<Long, Boolean>?) -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    if (showSliceSheet) {
        PlayerModalSheet(onDismissRequest = dismissSliceSheet) { sheetMaxHeight ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = sheetMaxHeight)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "切片管理",
                        style = MaterialTheme.typography.titleMedium,
                        color = colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.clearSlicesForCurrentTrack() }) {
                        Text("清空")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                PlaybackProgressContent(viewModel, isVideo) { progress ->
                    val highlightedPlaybackSliceId = currentSliceIdForPosition(
                        positionMs = progress.positionMs,
                        slices = sliceUiState.slices,
                        sliceModeEnabled = sliceUiState.sliceModeEnabled
                    )
                    SliceOverviewBar(
                        positionMs = progress.positionMs,
                        durationMs = progressDurationMs,
                        slices = sliceUiState.slices,
                        highlightedSliceId = highlightedPlaybackSliceId,
                        selectedSliceId = sliceUiState.selectedSliceId,
                        activeColor = accentColor,
                        inactiveColor = accentColor.copy(alpha = 0.18f),
                        onSeekTo = { viewModel.seekTo(it) },
                        onSelectSlice = { id ->
                            if (id == null) viewModel.selectSlice(null) else toggleSelectedSlice(id)
                        },
                        onLongPressSlice = { id ->
                            toggleSelectedSlice(id)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (sliceUiState.slices.isEmpty()) {
                    Text(
                        text = "暂无切片",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.textTertiary,
                        modifier = Modifier.padding(vertical = 18.dp)
                    )
                } else {
                    val sliceListState = rememberLazyListState()
                    LazyColumn(
                        state = sliceListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = true),
                        flingBehavior = rememberCalmScrollableFlingBehavior(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(sliceUiState.slices, key = { _, s -> s.id }) { index, slice ->
                            val selected = slice.id == sliceUiState.selectedSliceId
                            val bg = if (selected) accentColor.copy(alpha = 0.12f) else colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = bg,
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { toggleSelectedSlice(slice.id) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = (index + 1).toString(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colorScheme.textTertiary,
                                        modifier = Modifier.widthIn(min = 18.dp)
                                    )

                                    TextButton(
                                        onClick = { setTimeEditTarget(slice.id to true) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(Formatting.formatTrackTime(slice.startMs))
                                    }

                                    Text(
                                        text = "→",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.textTertiary
                                    )

                                    TextButton(
                                        onClick = { setTimeEditTarget(slice.id to false) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(Formatting.formatTrackTime(slice.endMs))
                                    }

                                    Spacer(modifier = Modifier.weight(1f))

                                    IconButton(onClick = { viewModel.playSlicePreview(slice) }) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = "播放切片",
                                            tint = colorScheme.onSurface
                                        )
                                    }

                                    IconButton(onClick = { viewModel.deleteSlice(slice.id) }) {
                                        Icon(
                                            imageVector = Icons.Outlined.DeleteOutline,
                                            contentDescription = "删除切片",
                                            tint = colorScheme.onSurface.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(18.dp)) }
                    }
                }
            }
        }
    }

    val edit = timeEditTarget
    if (edit != null) {
        val slice = sliceUiState.slices.firstOrNull { it.id == edit.first }
        if (slice != null) {
            PlaybackProgressContent(viewModel, isVideo) { progress ->
                SliceTimeEditDialog(
                    title = if (edit.second) "修改起点" else "修改终点",
                    durationMs = progressDurationMs,
                    currentMs = progress.positionMs,
                    initialMs = if (edit.second) slice.startMs else slice.endMs,
                    onDismiss = { setTimeEditTarget(null) },
                    onConfirm = { newMs ->
                        if (edit.second) {
                            viewModel.updateSliceRange(slice.id, newMs, slice.endMs, progressDurationMs)
                        } else {
                            viewModel.updateSliceRange(slice.id, slice.startMs, newMs, progressDurationMs)
                        }
                        setTimeEditTarget(null)
                    }
                )
            }
        } else {
            setTimeEditTarget(null)
        }
    }
}

@Composable
internal fun NowPlayingEqualizerSheetHost(
    viewModel: PlayerViewModel,
    showEqualizer: Boolean,
    onDismiss: () -> Unit,
    playback: PlaybackSnapshot,
    audioOutputRouteKind: AudioOutputRouteKind,
    warningSessionState: AppVolumeWarningSessionState
) {
    if (!showEqualizer) return
    val eqSettings by viewModel.sessionEqualizer.collectAsStateWithLifecycle()
    val customPresets by viewModel.customPresets.collectAsStateWithLifecycle()
    val appVolumePercent by viewModel.appVolumePercent.collectAsStateWithLifecycle()
    val equalizerFocusRequester = remember { FocusRequester() }
    var showEqualizerVolumeOverlay by remember { mutableStateOf(false) }
    var equalizerVolumeOverlayInteracting by remember { mutableStateOf(false) }
    var equalizerVolumeOverlayHoldTick by remember { mutableLongStateOf(0L) }
    var equalizerVolumeOverlayBounds by remember { mutableStateOf<Rect?>(null) }
    var lastNonZeroEqualizerVolume by remember { mutableIntStateOf(AppVolume.DefaultPercent) }
    LaunchedEffect(equalizerFocusRequester) {
        equalizerFocusRequester.requestFocus()
    }
    LaunchedEffect(appVolumePercent) {
        if (appVolumePercent > 0) {
            lastNonZeroEqualizerVolume = appVolumePercent
        }
    }
    LaunchedEffect(showEqualizerVolumeOverlay, equalizerVolumeOverlayHoldTick, equalizerVolumeOverlayInteracting) {
        if (!showEqualizerVolumeOverlay) return@LaunchedEffect
        if (equalizerVolumeOverlayInteracting) return@LaunchedEffect
        val snapshot = equalizerVolumeOverlayHoldTick
        delay(2_000)
        if (!equalizerVolumeOverlayInteracting && equalizerVolumeOverlayHoldTick == snapshot) {
            showEqualizerVolumeOverlay = false
            equalizerVolumeOverlayBounds = null
        }
    }
    PlayerModalSheet(onDismissRequest = onDismiss) { sheetMaxHeight ->
        val scrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = sheetMaxHeight)
                .focusRequester(equalizerFocusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.nativeKeyEvent.keyCode) {
                        AndroidKeyEvent.KEYCODE_VOLUME_UP -> {
                            viewModel.adjustAppVolumePercent(AppVolume.StepPercent)
                            showEqualizerVolumeOverlay = true
                            equalizerVolumeOverlayHoldTick += 1L
                            true
                        }
                        AndroidKeyEvent.KEYCODE_VOLUME_DOWN -> {
                            viewModel.adjustAppVolumePercent(-AppVolume.StepPercent)
                            showEqualizerVolumeOverlay = true
                            equalizerVolumeOverlayHoldTick += 1L
                            true
                        }
                        else -> false
                    }
                }
        ) {
            EqualizerPanel(
                settings = eqSettings,
                customPresets = customPresets,
                onSettingsChanged = { viewModel.updateSessionEqualizer(it) },
                onSavePreset = { name -> viewModel.saveCustomPreset(name, eqSettings) },
                onDeletePreset = { viewModel.deleteCustomPreset(it) },
                playbackSpeed = playback.playbackSpeed,
                playbackPitch = playback.playbackPitch,
                onPlaybackSpeedChanged = { viewModel.setPlaybackParameters(it, playback.playbackPitch) },
                onPlaybackPitchChanged = { viewModel.setPlaybackParameters(playback.playbackSpeed, it) },
                onPlaybackParametersChanged = { speed, pitch -> viewModel.setPlaybackParameters(speed, pitch) },
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(
                        state = scrollState,
                        flingBehavior = rememberCalmScrollableFlingBehavior()
                    )
                    .padding(bottom = 32.dp)
            )
            if (showEqualizerVolumeOverlay) {
                DismissOutsideBoundsOverlay(
                    targetBoundsInRoot = equalizerVolumeOverlayBounds,
                    onDismiss = {
                        showEqualizerVolumeOverlay = false
                        equalizerVolumeOverlayBounds = null
                    }
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 18.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                AnimatedVisibility(
                    visible = showEqualizerVolumeOverlay,
                    enter = fadeIn(animationSpec = tween(140)) + slideInHorizontally(animationSpec = tween(180)) { it / 3 },
                    exit = fadeOut(animationSpec = tween(160)) + slideOutHorizontally(animationSpec = tween(180)) { it / 3 }
                ) {
                    HardwareVolumeOverlay(
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            equalizerVolumeOverlayBounds = coordinates.boundsInRoot()
                        },
                        volumePercent = appVolumePercent,
                        audioOutputRouteKind = audioOutputRouteKind,
                        onVolumeChange = {
                            viewModel.setAppVolumePercent(it)
                            equalizerVolumeOverlayHoldTick += 1L
                        },
                        onToggleMute = {
                            if (appVolumePercent > 0) {
                                viewModel.setAppVolumePercent(0)
                            } else {
                                viewModel.setAppVolumePercent(
                                    lastNonZeroEqualizerVolume.coerceAtLeast(AppVolume.StepPercent)
                                )
                            }
                            equalizerVolumeOverlayHoldTick += 1L
                        },
                        onInteractionActiveChanged = { active ->
                            equalizerVolumeOverlayInteracting = active
                            if (!active) {
                                equalizerVolumeOverlayHoldTick += 1L
                            }
                        },
                        warningSessionState = warningSessionState
                    )
                }
            }
        }
    }
}
