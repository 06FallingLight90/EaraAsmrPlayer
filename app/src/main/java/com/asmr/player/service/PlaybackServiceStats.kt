package com.asmr.player.service

import android.os.SystemClock
import com.asmr.player.data.local.db.entities.TrackPlaybackProgressEntity
import com.asmr.player.data.repository.ListeningTrackContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun PlaybackService.markCurrentAlbumPlayed() {
        val item = exoPlayer.currentMediaItem ?: return
        val extras = item.mediaMetadata.extras ?: return
        val albumId = extras.getLong("album_id", -1L)
        if (albumId <= 0L) return

        val mediaId = item.mediaId
        val nowElapsed = SystemClock.elapsedRealtime()
        if (mediaId == lastMarkedMediaId && nowElapsed - lastMarkedElapsedMs < 5_000L) return
        lastMarkedMediaId = mediaId
        lastMarkedElapsedMs = nowElapsed

        val playedAt = System.currentTimeMillis()
        serviceScope.launch(Dispatchers.IO) {
            runCatching { database.playStatDao().markAlbumPlayed(albumId, playedAt) }
        }
    }

    /**
     * 采集当前播放项的作品上下文快照（供会话级收听记录使用）。
     * 必须在主线程调用（[serviceScope] 使用 Main.immediate）。
     * 无有效作品标识（albumId / rjCode 均为空）时返回 null。
     */
internal fun PlaybackService.currentListeningTrackContext(): ListeningTrackContext? {
        val item = exoPlayer.currentMediaItem ?: return null
        val metadata = item.mediaMetadata
        val extras = metadata.extras
        val albumId = extras?.getLong("album_id", -1L) ?: -1L
        val rjCode = extras?.getString("rj_code").orEmpty()
        if (albumId <= 0L && rjCode.isBlank()) return null
        return ListeningTrackContext(
            albumId = albumId,
            rjCode = rjCode,
            title = metadata.title?.toString().orEmpty(),
            artist = metadata.artist?.toString().orEmpty(),
            albumTitle = metadata.albumTitle?.toString().orEmpty(),
            artworkUri = metadata.artworkUri?.toString()
        )
    }

internal suspend fun PlaybackService.flushPendingNetworkTraffic() {
        val bytes = pendingNetworkTrafficBytes.getAndSet(0L)
        if (bytes <= 0L) return
        statisticsRepository.addNetworkTraffic(bytes)
        // 音频流量归入当前收听会话（若存在）。
        listeningRecordRepository.addTraffic(bytes)
    }

internal suspend fun PlaybackService.persistCurrentTrackProgressIfNeeded(force: Boolean) {
        data class Snapshot(
            val mediaId: String,
            val albumId: Long,
            val trackId: Long,
            val positionMs: Long,
            val durationMs: Long
        )

        val snapshot = withContext(Dispatchers.Main.immediate) {
            val item = exoPlayer.currentMediaItem
            val extras = item?.mediaMetadata?.extras
            Snapshot(
                mediaId = item?.mediaId.orEmpty(),
                albumId = extras?.getLong("album_id", -1L) ?: -1L,
                trackId = extras?.getLong("track_id", -1L) ?: -1L,
                positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                durationMs = exoPlayer.duration.takeIf { it > 0L } ?: 0L
            )
        }

        if (snapshot.mediaId.isBlank()) return
        if (snapshot.albumId <= 0L) return

        val durationMs = snapshot.durationMs
        val isCompleted = if (durationMs > 0L) {
            val remaining = (durationMs - snapshot.positionMs).coerceAtLeast(0L)
            remaining <= 10_000L || snapshot.positionMs.toDouble() / durationMs.toDouble() >= 0.95
        } else {
            false
        }

        val nowElapsed = SystemClock.elapsedRealtime()
        val shouldPersist = force || isCompleted || nowElapsed - lastProgressPersistElapsedMs >= 10_000L
        if (!shouldPersist) return
        lastProgressPersistElapsedMs = nowElapsed

        withContext(Dispatchers.IO) {
            val dao = database.trackPlaybackProgressDao()
            val now = System.currentTimeMillis()
            val existing = dao.getByMediaId(snapshot.mediaId)
            val mergedDurationMs = when {
                snapshot.durationMs > 0L -> snapshot.durationMs
                existing != null && existing.durationMs > 0L -> existing.durationMs
                else -> 0L
            }
            val mergedCompleted = existing?.completed == true || isCompleted
            val mergedPositionMs = when {
                mergedDurationMs > 0L -> snapshot.positionMs.coerceIn(0L, mergedDurationMs)
                else -> snapshot.positionMs
            }

            dao.upsert(
                TrackPlaybackProgressEntity(
                    mediaId = snapshot.mediaId,
                    albumId = snapshot.albumId,
                    trackId = snapshot.trackId.takeIf { it > 0L },
                    positionMs = mergedPositionMs,
                    durationMs = mergedDurationMs,
                    completed = mergedCompleted,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now
                )
            )
        }
    }
