package com.asmr.player.main

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

/**
 * MainContainer 的系统 UI 副作用簇：默认系统栏状态捕获/恢复、沉浸式/状态栏显隐应用、
 * 播放页方向锁定（仅视频全屏锁横屏，普通播放页保持原方向策略）。
 */
@Composable
internal fun MainSystemUiEffects(
    activity: Activity?,
    forceImmersive: Boolean,
    hideStatusBarForImmersivePage: Boolean,
    nowPlayingVisible: Boolean,
    isDark: Boolean,
    isPhone: Boolean,
    nowPlayingVideoFullscreen: Boolean,
    nowPlayingPortraitExitPending: Boolean
) {
    val defaultSystemUi = remember(activity) {
        activity?.let { act -> captureDefaultSystemUiState(act.window) }
    }

    DisposableEffect(activity) {
        val act = activity ?: return@DisposableEffect onDispose { }
        onDispose {
            restoreMainContainerSystemUi(act.window, defaultSystemUi)
        }
    }

    DisposableEffect(
        activity,
        defaultSystemUi,
        forceImmersive,
        hideStatusBarForImmersivePage,
        nowPlayingVisible,
        isDark
    ) {
        val act = activity ?: return@DisposableEffect onDispose { }
        applyMainContainerSystemUi(
            window = act.window,
            forceImmersive = forceImmersive,
            hideStatusBarForImmersivePage = hideStatusBarForImmersivePage,
            nowPlayingVisible = nowPlayingVisible,
            isDark = isDark
        )
        onDispose { }
    }

    // 普通播放页保持原方向策略，仅视频全屏时锁定横屏。
    LaunchedEffect(
        nowPlayingVisible,
        isPhone,
        nowPlayingVideoFullscreen,
        nowPlayingPortraitExitPending
    ) {
        activity?.let { act ->
            act.requestedOrientation = resolveMainRequestedOrientation(
                isPhone = isPhone,
                nowPlayingVisible = nowPlayingVisible,
                videoFullscreen = nowPlayingVideoFullscreen,
                portraitExitPending = nowPlayingPortraitExitPending
            )
        }
    }
}
