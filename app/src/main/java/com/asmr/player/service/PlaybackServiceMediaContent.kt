@file:androidx.annotation.OptIn(UnstableApi::class)

package com.asmr.player.service

import android.net.Uri
import com.asmr.player.util.EmbeddedMediaExtractor
import com.asmr.player.util.SubtitleIndexFinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.media3.common.util.UnstableApi

internal suspend fun PlaybackService.updateArtworkForCurrentMedia() {
        val (index, item) = withContext(Dispatchers.Main.immediate) {
            exoPlayer.currentMediaItemIndex to exoPlayer.currentMediaItem
        }
        if (index < 0) return
        if (item == null) return

        val uriString = item.localConfiguration?.uri?.toString().orEmpty().trim()
        if (uriString.isBlank()) return
        if (uriString.startsWith("http", ignoreCase = true)) return

        val mime = item.localConfiguration?.mimeType.orEmpty()
        val ext = uriString.substringBefore('#').substringBefore('?').substringAfterLast('.', "").lowercase()
        val isVideo = item.mediaMetadata.extras?.getBoolean("is_video") == true ||
            mime.startsWith("video/") ||
            ext in setOf("mp4", "m4v", "webm", "mkv", "mov")
        if (isVideo) return

        val cacheKey = "track:" + (item.mediaId.ifBlank { uriString })
        val file = EmbeddedMediaExtractor.getArtworkCacheFile(applicationContext, cacheKey)

        if (!file.exists() || file.length() <= 0L) {
            val bmp = EmbeddedMediaExtractor.extractArtwork(applicationContext, uriString) ?: return
            val saved = EmbeddedMediaExtractor.saveArtworkToCache(applicationContext, cacheKey, bmp) ?: return
            if (saved.isBlank()) return
        }
        if (!file.exists() || file.length() <= 0L) return

        val newUri = Uri.fromFile(file)
        val oldUri = item.mediaMetadata.artworkUri
        if (oldUri != null && oldUri.toString() == newUri.toString()) return

        val updatedMeta = item.mediaMetadata.buildUpon().setArtworkUri(newUri).build()
        val updatedItem = item.buildUpon().setMediaMetadata(updatedMeta).build()
        withContext(Dispatchers.Main.immediate) {
            if (exoPlayer.currentMediaItemIndex == index) {
                exoPlayer.replaceMediaItem(index, updatedItem)
            }
        }
    }

internal suspend fun PlaybackService.loadLyricsForCurrentMedia() {
        val item = withContext(Dispatchers.Main.immediate) { exoPlayer.currentMediaItem }
        val result = lyricsLoader.load(item)
        currentLyrics = result.lyrics
        lyricsIndexFinder = if (result.lyrics.isNotEmpty()) SubtitleIndexFinder(result.lyrics) else null
        lastLyricIndex = Int.MIN_VALUE
        refreshMediaNotification()
    }

internal suspend fun PlaybackService.updateLyricsTick(): Long {
        data class TickState(
            val overlayNeeded: Boolean,
            val positionMs: Long,
            val isPlaying: Boolean
        )

        val state = withContext(Dispatchers.Main.immediate) {
            val overlayReady = overlay?.canDraw() == true
            val need = floatingLyricsEnabled && overlayReady
            if (need && overlay?.isShown() != true) overlay?.show()
            if (!need && overlay?.isShown() == true) overlay?.hide()
            TickState(
                overlayNeeded = need,
                positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                isPlaying = exoPlayer.isPlaying
            )
        }
        val overlayNeeded = state.overlayNeeded
        val positionMs = state.positionMs
        val playing = state.isPlaying

        if (!overlayNeeded) return 1_000L
        val lyrics = currentLyrics
        if (lyrics.isEmpty()) {
            withContext(Dispatchers.Main.immediate) {
                if (overlayNeeded) overlay?.updateLine("暂无歌词")
            }
            return 2_000L
        }

        val idx = lyricsIndexFinder?.findActiveIndex(positionMs) ?: -1
        if (idx != lastLyricIndex) {
            lastLyricIndex = idx

            val current = lyrics.getOrNull(idx)?.text.orEmpty().ifBlank { " " }
            withContext(Dispatchers.Main.immediate) {
                if (overlayNeeded) overlay?.updateLine(current, lyrics.getOrNull(idx))
            }
        }

        return nextLyricsTickDelayMs(lyrics, idx, positionMs, playing)
    }
