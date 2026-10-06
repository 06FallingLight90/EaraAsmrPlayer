package com.asmr.player.service

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

internal fun PlaybackService.startEffectLoops(startupAppVolumeSyncJob: Job) {
        effectApplyJob?.cancel()
        effectApplyJob = serviceScope.launch {
            combine(
                audioEffectController.equalizerSettings,
                sessionSettings
            ) { global, session ->
                session ?: global
            }
                .distinctUntilChanged()
                .collect { settings ->
                lastEffectiveSettings = settings
                graphicEqualizerAudioProcessor.setEnabled(settings.enabled)
                graphicEqualizerAudioProcessor.setBandLevels(settings.bandLevels)
                val stereoEnabled = settings.stereoEnabled
                val panActive = stereoEnabled && (settings.orbitEnabled || settings.orbitAzimuthDeg != 0f)
                balanceAudioProcessor.setBalance(if (stereoEnabled && !panActive) settings.balance else 0f)
                channelModeAudioProcessor.setMode(if (stereoEnabled) settings.channelMode else 0)
                volumeThresholdAudioProcessor.setEnabled(settings.volumeThresholdEnabled)
                volumeThresholdAudioProcessor.setMode(settings.volumeThresholdMode)
                volumeThresholdAudioProcessor.setThresholds(settings.volumeThresholdMinDb, settings.volumeThresholdMaxDb)
                volumeThresholdAudioProcessor.setLoudnessTargetDb(settings.volumeLoudnessTargetDb)
                sceneEffectAudioProcessor.setEnabled(settings.sceneEffectEnabled)
                sceneEffectAudioProcessor.setPreset(settings.sceneEffectPresetId)
                sceneEffectAudioProcessor.setAmount(settings.sceneEffectAmount)
                stereoOrbitAudioProcessor.setEnabled(settings.stereoEnabled)
                stereoOrbitAudioProcessor.setAutoOrbitEnabled(settings.orbitEnabled)
                stereoOrbitAudioProcessor.setOrbitSpeedDegPerSec(settings.orbitSpeed)
                stereoOrbitAudioProcessor.setDistance(settings.orbitDistance)
                stereoOrbitAudioProcessor.setAzimuthDeg(settings.orbitAzimuthDeg)
            }
        }
        serviceScope.launch {
            startupAppVolumeSyncJob.join()
            var skipStartupApplyPercent = startupAppVolumePercent
            settingsRepository.appVolumePercent
                .distinctUntilChanged()
                .collect { appVolumePercent ->
                    if (
                        skipStartupApplyPercent == appVolumePercent ||
                        settingsRepository.consumePendingSystemVolumeSync(appVolumePercent)
                    ) {
                        skipStartupApplyPercent = null
                        sessionPlayer.setBaseVolume(1f)
                        return@collect
                    }
                    skipStartupApplyPercent = null
                    sessionPlayer.setBaseVolume(
                        appVolumeBoostController.applyVolumePercent(appVolumePercent)
                    )
                }
        }
    }
