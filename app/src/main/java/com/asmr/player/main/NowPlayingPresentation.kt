package com.asmr.player.main

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.asmr.player.ui.player.NowPlayingMotionLayout
import com.asmr.player.ui.player.NowPlayingMotionSpec

/**
 * MainContainer 的播放页表面呈现状态簇：可见性、视频全屏、内联音量/均衡器、背景层、
 * 竖屏退出收尾等。visible/backdropActive/backdropExitDurationMs 需跨进程重建保存。
 */
@Stable
internal class NowPlayingPresentationState(
    visible: MutableState<Boolean>,
    videoFullscreen: MutableState<Boolean>,
    usesInlineVolumeControl: MutableState<Boolean>,
    equalizerVisible: MutableState<Boolean>,
    backdropActive: MutableState<Boolean>,
    portraitExitPending: MutableState<Boolean>,
    routeExitFinished: MutableState<Boolean>,
    backdropExitDurationMs: MutableIntState
) {
    var visible by visible
    var videoFullscreen by videoFullscreen
    var usesInlineVolumeControl by usesInlineVolumeControl
    var equalizerVisible by equalizerVisible
    var backdropActive by backdropActive
    var portraitExitPending by portraitExitPending
    var routeExitFinished by routeExitFinished
    var backdropExitDurationMs by backdropExitDurationMs
}

@Composable
internal fun rememberNowPlayingPresentationState(): NowPlayingPresentationState {
    val visible = rememberSaveable { mutableStateOf(false) }
    val videoFullscreen = remember { mutableStateOf(false) }
    val usesInlineVolumeControl = remember { mutableStateOf(false) }
    val equalizerVisible = remember { mutableStateOf(false) }
    val backdropActive = rememberSaveable { mutableStateOf(false) }
    val portraitExitPending = remember { mutableStateOf(false) }
    val routeExitFinished = remember { mutableStateOf(false) }
    val backdropExitDurationMs = rememberSaveable {
        mutableIntStateOf(NowPlayingMotionSpec.totalExitDurationMs(NowPlayingMotionLayout.PORTRAIT))
    }
    return remember {
        NowPlayingPresentationState(
            visible = visible,
            videoFullscreen = videoFullscreen,
            usesInlineVolumeControl = usesInlineVolumeControl,
            equalizerVisible = equalizerVisible,
            backdropActive = backdropActive,
            portraitExitPending = portraitExitPending,
            routeExitFinished = routeExitFinished,
            backdropExitDurationMs = backdropExitDurationMs
        )
    }
}

/** 播放页背景层透明度：进入 360ms 淡入，退出按 NowPlayingMotionSpec 的时间轴关键帧淡出。 */
@Composable
internal fun rememberNowPlayingBackdropAlpha(state: NowPlayingPresentationState): Float =
    animateFloatAsState(
        targetValue = if (state.backdropActive) 1f else 0f,
        animationSpec = if (state.backdropActive) {
            tween(
                durationMillis = 360,
                easing = LinearOutSlowInEasing
            )
        } else {
            keyframes {
                durationMillis = state.backdropExitDurationMs
                1f at 0
                1f at (state.backdropExitDurationMs * 0.58f).toInt()
                0f at state.backdropExitDurationMs using FastOutLinearInEasing
            }
        },
        label = "nowPlayingBackdropAlpha"
    ).value

/** 竖屏（手机横屏）下播放页退出动画完成后延迟收尾关闭。 */
@Composable
internal fun NowPlayingPortraitExitEffect(
    state: NowPlayingPresentationState,
    isPhone: Boolean,
    isLandscape: Boolean,
    finalizeNowPlayingClose: () -> Unit
) {
    LaunchedEffect(
        state.portraitExitPending,
        state.routeExitFinished,
        isPhone,
        isLandscape
    ) {
        if (
            state.portraitExitPending &&
            state.routeExitFinished &&
            (!isPhone || !isLandscape)
        ) {
            finalizeNowPlayingClose()
        }
    }
}
