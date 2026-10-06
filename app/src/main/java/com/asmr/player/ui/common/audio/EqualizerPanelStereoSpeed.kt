@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.asmr.player.ui.common.audio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CardElevation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.asmr.player.data.settings.EqualizerSettings
import com.asmr.player.ui.theme.AsmrColorScheme

@Composable
internal fun SpeedPitchSection(
    playbackSpeed: Float?,
    playbackPitch: Float?,
    onPlaybackParametersChanged: ((Float, Float) -> Unit)?,
    onPlaybackSpeedChanged: ((Float) -> Unit)?,
    onPlaybackPitchChanged: ((Float) -> Unit)?,
    settings: EqualizerSettings,
    onSettingsChanged: (EqualizerSettings) -> Unit,
    colorScheme: AsmrColorScheme,
    sliderColors: SliderColors,
    moduleCardContainerColor: Color,
    moduleCardElevation: CardElevation,
    activeTipKeyState: MutableState<String?>
) {
    var activeTipKey by activeTipKeyState
    val updatePlaybackParameters: (Float, Float) -> Unit = remember(
        onPlaybackParametersChanged,
        onPlaybackSpeedChanged,
        onPlaybackPitchChanged
    ) {
        { speed: Float, pitch: Float ->
            if (onPlaybackParametersChanged != null) {
                onPlaybackParametersChanged(speed, pitch)
            } else {
                onPlaybackSpeedChanged?.invoke(speed)
                onPlaybackPitchChanged?.invoke(pitch)
            }
        }
    }
    val updatePlaybackSpeed: (Float) -> Unit = remember(
        onPlaybackParametersChanged,
        onPlaybackSpeedChanged,
        playbackPitch
    ) {
        { speed: Float ->
            if (onPlaybackParametersChanged != null && playbackPitch != null) {
                onPlaybackParametersChanged(speed, playbackPitch)
            } else {
                onPlaybackSpeedChanged?.invoke(speed)
            }
        }
    }
    val updatePlaybackPitch: (Float) -> Unit = remember(
        onPlaybackParametersChanged,
        onPlaybackPitchChanged,
        playbackSpeed
    ) {
        { pitch: Float ->
            if (onPlaybackParametersChanged != null && playbackSpeed != null) {
                onPlaybackParametersChanged(playbackSpeed, pitch)
            } else {
                onPlaybackPitchChanged?.invoke(pitch)
            }
        }
    }

    if (
        playbackSpeed != null &&
        playbackPitch != null &&
        (onPlaybackParametersChanged != null || (onPlaybackSpeedChanged != null && onPlaybackPitchChanged != null))
    ) {
        val speedPitchEnabled = settings.speedPitchEnabled
        ModuleCard(moduleCardContainerColor = moduleCardContainerColor, moduleCardElevation = moduleCardElevation,
            title = "变速变调",
            enabled = speedPitchEnabled,
            onEnabledChange = { enabled ->
                onSettingsChanged(settings.copy(speedPitchEnabled = enabled))
                if (!enabled) {
                    updatePlaybackParameters(1f, 1f)
                }
            },
            expanded = settings.speedPitchExpanded,
            onExpandedChange = { onSettingsChanged(settings.copy(speedPitchExpanded = it)) },
            onReset = {
                updatePlaybackParameters(1f, 1f)
            },
            resetEnabled = speedPitchEnabled
        ) {
            Column {
                Row {
                    Text("播放速度", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    InfoTip(activeTipKeyState = activeTipKeyState,
                        key = "playback_speed",
                        title = "播放速度",
                        text = "越大播放越快（影响节奏与时长）。"
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(String.format("%.2fx", playbackSpeed), style = MaterialTheme.typography.labelSmall)
                }
                Slider(
                    value = playbackSpeed,
                    onValueChange = updatePlaybackSpeed,
                    valueRange = 0.5f..2f,
                    enabled = speedPitchEnabled,
                    colors = sliderColors
                )
            }

            Column {
                Row {
                    Text("音调", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    InfoTip(activeTipKeyState = activeTipKeyState,
                        key = "playback_pitch",
                        title = "音调",
                        text = "越大音高越高、越小音高越低（不一定改变播放时长）。"
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(String.format("%.2fx", playbackPitch), style = MaterialTheme.typography.labelSmall)
                }
                Slider(
                    value = playbackPitch,
                    onValueChange = updatePlaybackPitch,
                    valueRange = 0.5f..2f,
                    enabled = speedPitchEnabled,
                    colors = sliderColors
                )
            }
        }
    }
}

@Composable
internal fun StereoSection(
    settings: EqualizerSettings,
    onSettingsChanged: (EqualizerSettings) -> Unit,
    colorScheme: AsmrColorScheme,
    sliderColors: SliderColors,
    moduleCardContainerColor: Color,
    moduleCardElevation: CardElevation,
    activeTipKeyState: MutableState<String?>
) {
    var activeTipKey by activeTipKeyState
    fun updateBalance(value: Float) {
        onSettingsChanged(
            settings.copy(
                stereoEnabled = true,
                balance = value.coerceIn(-1f, 1f),
                orbitEnabled = false,
                orbitAzimuthDeg = 0f
            )
        )
    }

    fun updateChannelMode(mode: Int) {
        onSettingsChanged(settings.copy(stereoEnabled = true, channelMode = mode.coerceIn(0, 3)))
    }

    val stereoEnabled = settings.stereoEnabled
    ModuleCard(moduleCardContainerColor = moduleCardContainerColor, moduleCardElevation = moduleCardElevation,
        title = "立体声",
        enabled = stereoEnabled,
        onEnabledChange = { onSettingsChanged(settings.copy(stereoEnabled = it)) },
        expanded = settings.stereoExpanded,
        onExpandedChange = { onSettingsChanged(settings.copy(stereoExpanded = it)) },
        onReset = {
            onSettingsChanged(
                settings.copy(
                    stereoEnabled = true,
                    orbitDistance = 5f,
                    orbitAzimuthDeg = 0f,
                    orbitEnabled = false,
                    orbitSpeed = 25f,
                    balance = 0f,
                    channelMode = 0
                )
            )
        },
        resetEnabled = stereoEnabled
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("声源距离", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                InfoTip(activeTipKeyState = activeTipKeyState,
                    key = "st_distance",
                    title = "声源距离",
                    text = "距离越远，声音会更柔和并略有衰减。"
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(String.format("%.1f", settings.orbitDistance), style = MaterialTheme.typography.labelSmall)
            }
            Slider(
                value = settings.orbitDistance,
                onValueChange = { onSettingsChanged(settings.copy(stereoEnabled = true, orbitDistance = it)) },
                valueRange = 0f..10f,
                enabled = stereoEnabled,
                colors = sliderColors
            )
        }

        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("声源方向", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                InfoTip(activeTipKeyState = activeTipKeyState,
                    key = "st_azimuth",
                    title = "声源方向",
                    text = "0°为居中，90°偏右，270°偏左。"
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(String.format("%.0f°", settings.orbitAzimuthDeg), style = MaterialTheme.typography.labelSmall)
            }
            Slider(
                value = settings.orbitAzimuthDeg.coerceIn(0f, 360f),
                onValueChange = {
                    val deg = if (it >= 360f) 0f else it
                    onSettingsChanged(
                        settings.copy(
                            stereoEnabled = true,
                            orbitAzimuthDeg = deg,
                            orbitEnabled = false,
                            balance = 0f
                        )
                    )
                },
                valueRange = 0f..360f,
                enabled = stereoEnabled && !settings.orbitEnabled,
                colors = sliderColors
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("声源自动环绕", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            InfoTip(activeTipKeyState = activeTipKeyState,
                key = "st_orbit",
                title = "声源自动环绕",
                text = "开启后，声像会按速度自动移动。"
            )
            Spacer(modifier = Modifier.weight(1f))
            Switch(
                checked = settings.orbitEnabled,
                onCheckedChange = { onSettingsChanged(settings.copy(stereoEnabled = true, orbitEnabled = it, balance = 0f)) },
                enabled = stereoEnabled,
                modifier = Modifier.scale(0.85f)
            )
        }

        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("环绕速度", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                InfoTip(activeTipKeyState = activeTipKeyState,
                    key = "st_speed",
                    title = "环绕速度",
                    text = "速度越大，声像移动越快。"
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(String.format("%.0f°/s", settings.orbitSpeed), style = MaterialTheme.typography.labelSmall)
            }
            Slider(
                value = settings.orbitSpeed,
                onValueChange = { onSettingsChanged(settings.copy(stereoEnabled = true, orbitSpeed = it)) },
                valueRange = 0f..50f,
                enabled = stereoEnabled && settings.orbitEnabled,
                colors = sliderColors
            )
        }

        val panActive = settings.orbitEnabled || settings.orbitAzimuthDeg != 0f
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("左右平衡", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                InfoTip(activeTipKeyState = activeTipKeyState,
                    key = "ch_balance",
                    title = "左右平衡",
                    text = "把整体声音偏向左或右声道。与“声源方向/自动环绕”互斥。"
                )
                Spacer(modifier = Modifier.weight(1f))
                val balText = when {
                    settings.balance < -0.05f -> "左偏 ${(settings.balance * -100).toInt()}%"
                    settings.balance > 0.05f -> "右偏 ${(settings.balance * 100).toInt()}%"
                    else -> "居中"
                }
                Text(balText, style = MaterialTheme.typography.labelSmall)
            }
            Slider(
                value = settings.balance,
                onValueChange = { updateBalance(it) },
                valueRange = -1f..1f,
                enabled = stereoEnabled && !panActive,
                colors = sliderColors
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("声道模式", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            InfoTip(activeTipKeyState = activeTipKeyState,
                key = "ch_mode",
                title = "声道模式",
                text = "反转：交换左右声道；克隆：把一侧声道复制到另一侧。"
            )
        }
        val channelModeChipColors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = colorScheme.primary.copy(alpha = 0.18f),
            selectedLabelColor = colorScheme.primary,
            selectedLeadingIconColor = colorScheme.primary
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                0 to "正常",
                1 to "反转",
                2 to "克隆 L→R",
                3 to "克隆 R→L"
            ).forEach { (mode, label) ->
                FilterChip(
                    selected = settings.channelMode == mode,
                    onClick = { updateChannelMode(mode) },
                    label = { Text(label) },
                    enabled = stereoEnabled,
                    colors = channelModeChipColors,
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = stereoEnabled,
                        selected = settings.channelMode == mode,
                        borderColor = colorScheme.primary.copy(alpha = 0.22f),
                        selectedBorderColor = colorScheme.primary
                    )
                )
            }
        }
    }
}