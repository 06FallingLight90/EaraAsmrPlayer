@file:androidx.annotation.OptIn(UnstableApi::class)

package com.asmr.player.service

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource
import com.asmr.player.playback.isRecoverableRemotePlaybackFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.media3.common.util.UnstableApi

internal fun PlaybackService.requestPlaybackAudioFocus(): Boolean {
        if (!pauseOnOtherAudioEnabled) return true
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = (audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
                .also { audioFocusRequest = it })
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasAudioFocus
    }

internal fun PlaybackService.schedulePlaybackRecovery(error: PlaybackException) {
        val item = exoPlayer.currentMediaItem ?: return
        if (!exoPlayer.playWhenReady) return

        val mediaItemIndex = exoPlayer.currentMediaItemIndex
        val uri = item.localConfiguration?.uri?.toString().orEmpty()
        if (
            !isRecoverableRemotePlaybackFailure(
                uriText = uri,
                errorCode = error.errorCode,
                httpStatusCode = error.findHttpStatusCode()
            )
        ) {
            return
        }

        val mediaKey = "$mediaItemIndex:${item.mediaId}:$uri"
        val attempt = playbackRecoveryPolicy.nextAttempt(mediaKey)
        if (attempt == null) {
            Log.e(
                "PlaybackService",
                "Playback recovery exhausted for mediaId=${item.mediaId} error=${error.errorCodeName}"
            )
            return
        }

        playbackRecoveryJob?.cancel()
        playbackRecoveryJob = serviceScope.launch(Dispatchers.Main.immediate) {
            Log.w(
                "PlaybackService",
                "Scheduling playback recovery attempt=${attempt.number} delayMs=${attempt.delayMs} " +
                    "mediaId=${item.mediaId} error=${error.errorCodeName}"
            )
            delay(attempt.delayMs)
            if (!exoPlayer.playWhenReady) return@launch
            val currentItem = exoPlayer.currentMediaItem ?: return@launch
            val currentUri = currentItem.localConfiguration?.uri?.toString().orEmpty()
            if (
                exoPlayer.currentMediaItemIndex != mediaItemIndex ||
                currentItem.mediaId != item.mediaId ||
                currentUri != uri
            ) {
                return@launch
            }
            if (exoPlayer.playerError !== error || exoPlayer.playbackState != Player.STATE_IDLE) return@launch

            val resumePositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            exoPlayer.seekTo(resumePositionMs)
            exoPlayer.prepare()
        }
    }

internal fun PlaybackService.cancelPlaybackRecovery(resetPolicy: Boolean) {
        playbackRecoveryJob?.cancel()
        playbackRecoveryJob = null
        if (resetPolicy) {
            playbackRecoveryPolicy.reset()
        }
    }

private fun PlaybackException.findHttpStatusCode(): Int? {
        var current: Throwable? = this
        while (current != null) {
            if (current is HttpDataSource.InvalidResponseCodeException) {
                return current.responseCode
            }
            current = current.cause
        }
        return null
    }

internal fun PlaybackService.abandonPlaybackAudioFocus() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
        hasAudioFocus = false
    }

internal fun PlaybackService.registerPlaybackRouteListeners() {
        val intentFilter = IntentFilter().apply {
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        registerReceiver(outputBroadcastReceiver, intentFilter)
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
    }

internal fun PlaybackService.unregisterPlaybackRouteListeners() {
        runCatching { unregisterReceiver(outputBroadcastReceiver) }
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        runCatching { audioManager.unregisterAudioDeviceCallback(audioDeviceCallback) }
    }

internal fun PlaybackService.hasResumeEligibleOutputDevice(): Boolean {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .any { it.isResumeEligibleOutputDevice() }
    }
