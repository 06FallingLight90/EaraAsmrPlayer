package com.asmr.player.service

import com.asmr.player.util.SubtitleEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsTickSchedulerTest {

    private fun entry(startMs: Long): SubtitleEntry =
        SubtitleEntry(startMs = startMs, endMs = startMs + 1_000, text = "line-$startMs")

    @Test
    fun alignsToNextLineStartWhenWithinWindow() {
        val lyrics = listOf(entry(0), entry(1_000), entry(2_000))
        assertEquals(
            1_000L,
            nextLyricsTickDelayMs(lyrics, activeIndex = 0, positionMs = 0, isPlaying = true),
        )
    }

    @Test
    fun clampsToMinimumWhenNextLineIsImminent() {
        val lyrics = listOf(entry(0), entry(1_000))
        assertEquals(
            LYRICS_TICK_MIN_DELAY_MS,
            nextLyricsTickDelayMs(lyrics, activeIndex = 0, positionMs = 990, isPlaying = true),
        )
    }

    @Test
    fun clampsToPlayingCapWhenNextLineIsFar() {
        val lyrics = listOf(entry(0), entry(60_000))
        assertEquals(
            LYRICS_TICK_MAX_DELAY_PLAYING_MS,
            nextLyricsTickDelayMs(lyrics, activeIndex = 0, positionMs = 0, isPlaying = true),
        )
    }

    @Test
    fun clampsToPausedCapWhichIsLowerThanPlayingCap() {
        val lyrics = listOf(entry(0), entry(60_000))
        assertEquals(
            LYRICS_TICK_MAX_DELAY_PAUSED_MS,
            nextLyricsTickDelayMs(lyrics, activeIndex = 0, positionMs = 0, isPlaying = false),
        )
    }

    @Test
    fun beforeFirstLineUsesFirstStart() {
        val lyrics = listOf(entry(800), entry(1_800))
        assertEquals(
            800L,
            nextLyricsTickDelayMs(lyrics, activeIndex = -1, positionMs = 0, isPlaying = true),
        )
    }

    @Test
    fun lastLineFallsBackToDefaultDelay() {
        val lyrics = listOf(entry(0), entry(1_000))
        assertEquals(
            LYRICS_TICK_DEFAULT_DELAY_MS,
            nextLyricsTickDelayMs(lyrics, activeIndex = 1, positionMs = 1_000, isPlaying = true),
        )
    }

    @Test
    fun emptyLyricsFallsBackToDefaultThenPausedCap() {
        val lyrics = emptyList<SubtitleEntry>()
        assertEquals(
            LYRICS_TICK_MAX_DELAY_PAUSED_MS,
            nextLyricsTickDelayMs(lyrics, activeIndex = -1, positionMs = 500, isPlaying = false),
        )
        assertEquals(
            LYRICS_TICK_DEFAULT_DELAY_MS,
            nextLyricsTickDelayMs(lyrics, activeIndex = -1, positionMs = 500, isPlaying = true),
        )
    }
}
