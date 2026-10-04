package com.asmr.player.service

import com.asmr.player.util.SubtitleEntry

/**
 * 悬浮歌词滚动节拍（R3-A4 自 `PlaybackService.updateLyricsTick` 提取为纯函数，便于单测）。
 *
 * 依据"当前生效歌词行 + 播放位置"决定下一次轮询延迟：优先对齐下一行的起始时刻；
 * 无下一行时退回默认 2s；结果夹在 [200ms, 播放中 2000ms / 暂停 1500ms] 之间。
 */
internal const val LYRICS_TICK_MIN_DELAY_MS = 200L
internal const val LYRICS_TICK_DEFAULT_DELAY_MS = 2_000L
internal const val LYRICS_TICK_MAX_DELAY_PLAYING_MS = 2_000L
internal const val LYRICS_TICK_MAX_DELAY_PAUSED_MS = 1_500L

internal fun nextLyricsTickDelayMs(
    lyrics: List<SubtitleEntry>,
    activeIndex: Int,
    positionMs: Long,
    isPlaying: Boolean,
): Long {
    val nextStartMs = when {
        activeIndex + 1 in lyrics.indices -> lyrics[activeIndex + 1].startMs
        activeIndex < 0 && lyrics.isNotEmpty() -> lyrics.first().startMs
        else -> null
    }
    val rawDelay = nextStartMs?.let { it - positionMs } ?: LYRICS_TICK_DEFAULT_DELAY_MS
    val maxDelay = if (isPlaying) LYRICS_TICK_MAX_DELAY_PLAYING_MS else LYRICS_TICK_MAX_DELAY_PAUSED_MS
    return rawDelay.coerceIn(LYRICS_TICK_MIN_DELAY_MS, maxDelay)
}
