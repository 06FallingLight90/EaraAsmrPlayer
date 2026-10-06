@file:androidx.annotation.OptIn(UnstableApi::class)

package com.asmr.player.service

import android.app.PendingIntent
import android.content.Intent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import androidx.core.app.TaskStackBuilder
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommands
import com.asmr.player.data.settings.EqualizerSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.media3.common.util.UnstableApi

internal fun PlaybackService.refreshMediaNotification() {
        serviceScope.launch(Dispatchers.Main.immediate) {
            runCatching {
                syncMediaNotificationControllerState()
                if (mediaSession != null) {
                    notificationProvider?.refreshNotification()
                }
            }.onFailure {
                Log.w("PlaybackService", "Failed to refresh media notification", it)
            }
        }
    }

internal fun PlaybackService.buildMediaSession(): MediaSession {
        return MediaSession.Builder(this, sessionPlayer)
            .setSessionActivity(createContentIntent())
            // Media3 默认每 3 秒把仅位置变化的 PLAYING 状态重新广播给所有系统控制器。
            // 系统本就能根据 position/speed/eventTime 外推位置；关闭这类周期广播可避免
            // MIUI 同期唤醒蓝牙、妙播、灵动岛和媒体面板，真实播放状态变化仍会立即通知。
            .setPeriodicPositionUpdateEnabled(false)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val isNotificationController =
                        isMediaNotificationController(session, controller)
                    if (sfwHideSystemControlsEnabled && isNotificationController) {
                        return MediaSession.ConnectionResult.reject()
                    }
                    val base = super.onConnect(session, controller)
                    val commands = if (isNotificationController) {
                        MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                    } else {
                        MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                            .buildUpon()
                            .add(androidx.media3.session.SessionCommand("GET_AUDIO_SESSION_ID", android.os.Bundle.EMPTY))
                            .add(androidx.media3.session.SessionCommand("UPDATE_SESSION_EQ", android.os.Bundle.EMPTY))
                            .add(androidx.media3.session.SessionCommand("RELOAD_LYRICS", android.os.Bundle.EMPTY))
                            .add(androidx.media3.session.SessionCommand("SET_VIDEO_OUTPUT_ENABLED", android.os.Bundle.EMPTY))
                            .build()
                    }
                    val playerCommands = if (
                        isNotificationController &&
                        sfwHideSystemControlsEnabled
                    ) {
                        Player.Commands.EMPTY
                    } else {
                        base.availablePlayerCommands
                    }
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailablePlayerCommands(playerCommands)
                        .setAvailableSessionCommands(commands)
                        .build()
                }

                override fun onPostConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ) {
                    if (isMediaNotificationController(session, controller)) {
                        syncMediaNotificationControllerState(session, controller)
                    }
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: androidx.media3.session.SessionCommand,
                    args: android.os.Bundle
                ): com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.SessionResult> {
                    // 安全修复（20261002 体检 Quick Win #5）：4 条自定义命令
                    // （GET_AUDIO_SESSION_ID/UPDATE_SESSION_EQ/RELOAD_LYRICS/SET_VIDEO_OUTPUT_ENABLED）
                    // 仅对本应用进程内控制器开放。第三方 App 即使绑定 MediaSession
                    // 也不得篡改音效/视频输出或读取 audioSessionId。
                    // 通知控制器只拿到 DEFAULT_SESSION_COMMANDS，到不了这里；
                    // 此闸挡的是经 onConnect 拿到自定义命令集的任意非本包控制器。
                    if (controller.packageName != applicationContext.packageName) {
                        return com.google.common.util.concurrent.Futures.immediateFuture(
                            androidx.media3.session.SessionResult(
                                androidx.media3.session.SessionResult.RESULT_ERROR_NOT_SUPPORTED
                            )
                        )
                    }
                    when (customCommand.customAction) {
                        "GET_AUDIO_SESSION_ID" -> {
                            val resultBundle = android.os.Bundle()
                            resultBundle.putInt("AUDIO_SESSION_ID", exoPlayer.audioSessionId)
                            return com.google.common.util.concurrent.Futures.immediateFuture(
                                androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS, resultBundle)
                            )
                        }

                        "UPDATE_SESSION_EQ" -> {
                            val prev = sessionSettings.value ?: EqualizerSettings()
                            val enabled = if (args.containsKey("enabled")) args.getBoolean("enabled") else prev.enabled
                            val levels = args.getIntArray("levels")?.toList() ?: prev.bandLevels
                            val virt = if (args.containsKey("virtualizer")) args.getInt("virtualizer") else prev.virtualizerStrength
                            val bal = if (args.containsKey("balance")) args.getFloat("balance") else prev.balance
                            val preset = args.getString("preset") ?: prev.presetName
                            val gain = if (args.containsKey("gain")) args.getFloat("gain") else prev.originalGain
                            val reverbEnabled = if (args.containsKey("reverbEnabled")) args.getBoolean("reverbEnabled") else prev.reverbEnabled
                            val reverbPreset = args.getString("reverbPreset") ?: prev.reverbPreset
                            val reverbWet = if (args.containsKey("reverbWet")) args.getInt("reverbWet") else prev.reverbWet
                            val stereoEnabled = if (args.containsKey("stereoEnabled")) args.getBoolean("stereoEnabled") else prev.stereoEnabled
                            val orbitEnabled = if (args.containsKey("orbitEnabled")) args.getBoolean("orbitEnabled") else prev.orbitEnabled
                            val orbitSpeed = if (args.containsKey("orbitSpeed")) args.getFloat("orbitSpeed") else prev.orbitSpeed
                            val orbitDistance = if (args.containsKey("orbitDistance")) args.getFloat("orbitDistance") else prev.orbitDistance
                            val channelMode = if (args.containsKey("channelMode")) args.getInt("channelMode") else prev.channelMode
                            val orbitAzimuthDeg = if (args.containsKey("orbitAzimuthDeg")) args.getFloat("orbitAzimuthDeg") else prev.orbitAzimuthDeg
                            val channelEnabled = if (args.containsKey("channelEnabled")) args.getBoolean("channelEnabled") else prev.channelEnabled
                            val vtEnabled = if (args.containsKey("volumeThresholdEnabled")) args.getBoolean("volumeThresholdEnabled") else prev.volumeThresholdEnabled
                            val vtMode = if (args.containsKey("volumeThresholdMode")) args.getInt("volumeThresholdMode") else prev.volumeThresholdMode
                            val vtMinDb = if (args.containsKey("volumeThresholdMinDb")) args.getFloat("volumeThresholdMinDb") else prev.volumeThresholdMinDb
                            val vtMaxDb = if (args.containsKey("volumeThresholdMaxDb")) args.getFloat("volumeThresholdMaxDb") else prev.volumeThresholdMaxDb
                            val loudnessTargetDb = if (args.containsKey("volumeLoudnessTargetDb")) args.getFloat("volumeLoudnessTargetDb") else prev.volumeLoudnessTargetDb
                            val sceneEffectEnabled = if (args.containsKey("sceneEffectEnabled")) args.getBoolean("sceneEffectEnabled") else prev.sceneEffectEnabled
                            val sceneEffectPresetId = args.getString("sceneEffectPresetId") ?: prev.sceneEffectPresetId
                            val sceneEffectAmount = if (args.containsKey("sceneEffectAmount")) args.getInt("sceneEffectAmount") else prev.sceneEffectAmount
                            sessionSettings.value = prev.copy(
                                enabled = enabled,
                                bandLevels = levels,
                                virtualizerStrength = virt,
                                balance = bal,
                                presetName = preset,
                                originalGain = gain,
                                reverbEnabled = reverbEnabled,
                                reverbPreset = reverbPreset,
                                reverbWet = reverbWet,
                                stereoEnabled = stereoEnabled,
                                orbitEnabled = orbitEnabled,
                                orbitSpeed = orbitSpeed,
                                orbitDistance = orbitDistance,
                                orbitAzimuthDeg = orbitAzimuthDeg,
                                channelEnabled = channelEnabled,
                                channelMode = channelMode,
                                volumeThresholdEnabled = vtEnabled,
                                volumeThresholdMode = vtMode,
                                volumeThresholdMinDb = vtMinDb,
                                volumeThresholdMaxDb = vtMaxDb,
                                volumeLoudnessTargetDb = loudnessTargetDb,
                                sceneEffectEnabled = sceneEffectEnabled,
                                sceneEffectPresetId = sceneEffectPresetId,
                                sceneEffectAmount = sceneEffectAmount
                            )
                            return com.google.common.util.concurrent.Futures.immediateFuture(
                                androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS, android.os.Bundle.EMPTY)
                            )
                        }

                        "RELOAD_LYRICS" -> {
                            serviceScope.launch { loadLyricsForCurrentMedia() }
                            return com.google.common.util.concurrent.Futures.immediateFuture(
                                androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS, android.os.Bundle.EMPTY)
                            )
                        }

                        "SET_VIDEO_OUTPUT_ENABLED" -> {
                            setVideoOutputEnabled(args.getBoolean("enabled", false))
                            return com.google.common.util.concurrent.Futures.immediateFuture(
                                androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS, android.os.Bundle.EMPTY)
                            )
                        }
                    }
                    return super.onCustomCommand(session, controller, customCommand, args)
                }
            })
            .build()
    }

internal fun PlaybackService.setVideoOutputEnabled(enabled: Boolean) {
        if (videoOutputEnabled == enabled) return
        videoOutputEnabled = enabled
        applyVideoOutputEnabled()
    }

internal fun PlaybackService.applyVideoOutputEnabled() {
        val item = exoPlayer.currentMediaItem
        val videoActive = videoOutputEnabled && item.isVideoMediaItem()
        val params = exoPlayer.trackSelectionParameters
        val updated = params.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !videoActive)
            .build()
        if (updated != params) {
            exoPlayer.trackSelectionParameters = updated
        }
    }

private fun MediaItem?.isVideoMediaItem(): Boolean {
        val item = this ?: return false
        val uriString = item.localConfiguration?.uri?.toString().orEmpty()
        val mime = item.localConfiguration?.mimeType.orEmpty()
        val ext = uriString
            .substringBefore('#')
            .substringBefore('?')
            .substringAfterLast('.', "")
            .lowercase()
        return item.mediaMetadata.extras?.getBoolean("is_video") == true ||
            mime.startsWith("video/") ||
            ext in setOf("mp4", "m4v", "webm", "mkv", "mov")
    }

internal fun PlaybackService.refreshMediaNotificationControllerRegistration() {
        val session = mediaSession ?: return
        runCatching {
            if (isSessionAdded(session)) {
                removeSession(session)
            }
            addSession(session)
        }.onFailure {
            Log.w(
                "PlaybackService",
                "Failed to refresh media notification controller registration",
                it
            )
        }
    }

internal fun PlaybackService.syncMediaNotificationControllerState() {
        val session = mediaSession ?: return
        val controller = session.getMediaNotificationControllerInfo() ?: return
        syncMediaNotificationControllerState(session, controller)
    }

internal fun PlaybackService.isMediaNotificationController(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): Boolean {
        return session.isMediaNotificationController(controller) ||
            controller.connectionHints.getBoolean(PlaybackService.MEDIA_NOTIFICATION_CONTROLLER_HINT, false)
    }

internal fun PlaybackService.syncMediaNotificationControllerState(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ) {
        val sessionCommands = if (sfwHideSystemControlsEnabled) {
            SessionCommands.EMPTY
        } else {
            MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
        }
        val playerCommands = if (sfwHideSystemControlsEnabled) {
            Player.Commands.EMPTY
        } else {
            session.player.availableCommands
        }
        session.setAvailableCommands(
            controller,
            sessionCommands,
            playerCommands
        )
        session.setCustomLayout(controller, emptyList())
    }

internal fun PlaybackService.ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val existing = manager.getNotificationChannel(PlaybackService.LYRICS_CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            PlaybackService.LYRICS_CHANNEL_ID,
            "播放控制",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
            setSound(null, null)
            description = "用于后台播放控制与锁屏媒体面板"
        }
        manager.createNotificationChannel(channel)
    }

internal fun PlaybackService.createContentIntent(): PendingIntent {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: Intent(Intent.ACTION_MAIN)
            .setPackage(packageName)
            .addCategory(Intent.CATEGORY_LAUNCHER)
        return TaskStackBuilder.create(this)
            .addNextIntentWithParentStack(intent)
            .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            ?: PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }

internal fun PlaybackService.releaseMediaSession() {
        val session = mediaSession ?: return
        mediaSession = null
        session.player.release()
        session.release()
    }
