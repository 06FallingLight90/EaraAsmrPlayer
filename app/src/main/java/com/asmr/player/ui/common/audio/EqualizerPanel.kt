package com.asmr.player.ui.common.audio

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.asmr.player.data.settings.AsmrPreset
import com.asmr.player.data.settings.EqualizerPresets
import com.asmr.player.data.settings.EqualizerSettings
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.common.dialog.FlatTextFieldDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EqualizerPanel(
    settings: EqualizerSettings,
    customPresets: List<AsmrPreset>,
    onSettingsChanged: (EqualizerSettings) -> Unit,
    onSavePreset: (String) -> Unit,
    onDeletePreset: (AsmrPreset) -> Unit,
    playbackSpeed: Float? = null,
    playbackPitch: Float? = null,
    onPlaybackSpeedChanged: ((Float) -> Unit)? = null,
    onPlaybackPitchChanged: ((Float) -> Unit)? = null,
    onPlaybackParametersChanged: ((Float, Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val materialColorScheme = MaterialTheme.colorScheme
    val sliderInactiveTrackColor = colorScheme.primarySoft.copy(alpha = if (colorScheme.isDark) 0.34f else 0.42f)
    val sliderDisabledActiveTrackColor = materialColorScheme.onSurfaceVariant.copy(alpha = if (colorScheme.isDark) 0.40f else 0.34f)
    val sliderDisabledInactiveTrackColor = materialColorScheme.onSurfaceVariant.copy(alpha = if (colorScheme.isDark) 0.30f else 0.24f)
    val sliderDisabledThumbColor = materialColorScheme.onSurfaceVariant.copy(alpha = if (colorScheme.isDark) 0.50f else 0.44f)
    val moduleCardElevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    val moduleCardContainerColor = colorScheme.surfaceVariant.copy(alpha = 0.45f).compositeOver(materialColorScheme.surface)
    val sliderColors = SliderDefaults.colors(
        thumbColor = colorScheme.primary,
        activeTrackColor = colorScheme.primary,
        inactiveTrackColor = sliderInactiveTrackColor,
        activeTickColor = colorScheme.primary.copy(alpha = 0f),
        inactiveTickColor = materialColorScheme.outlineVariant.copy(alpha = 0f),
        disabledActiveTrackColor = sliderDisabledActiveTrackColor,
        disabledInactiveTrackColor = sliderDisabledInactiveTrackColor,
        disabledThumbColor = sliderDisabledThumbColor
    )
    val allPresets = remember(customPresets) {
        EqualizerPresets.DefaultPresets + customPresets
    }

    val showSaveDialogState = remember { mutableStateOf(false) }
    var showSaveDialog by showSaveDialogState
    var newPresetName by remember { mutableStateOf("") }
    val activeTipKeyState = remember { mutableStateOf<String?>(null) }

    fun normalizedLevels(): List<Int> {
        val src = settings.bandLevels
        if (src.size == 10) return src
        val out = MutableList(10) { 0 }
        for (i in 0 until minOf(10, src.size)) out[i] = src[i]
        return out
    }

    // Local state to track editing values - completely independent from settings
    val editingLevelsState = remember { mutableStateOf(normalizedLevels()) }
    var editingLevels by editingLevelsState
    // Only sync when user explicitly selects a preset (not when we change to "自定义")
    val currentPresetName = settings.presetName
    LaunchedEffect(currentPresetName) {
        // When preset changes to something other than "自定义", update our local state
        if (currentPresetName != "自定义") {
            editingLevels = normalizedLevels()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("音效器", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        VolumeThresholdSection(
            settings = settings,
            onSettingsChanged = onSettingsChanged,
            colorScheme = colorScheme,
            sliderColors = sliderColors,
            moduleCardContainerColor = moduleCardContainerColor,
            moduleCardElevation = moduleCardElevation,
            activeTipKeyState = activeTipKeyState
        )

        SceneEffectSection(
            settings = settings,
            onSettingsChanged = onSettingsChanged,
            colorScheme = colorScheme,
            materialColorScheme = materialColorScheme,
            sliderColors = sliderColors,
            moduleCardContainerColor = moduleCardContainerColor,
            moduleCardElevation = moduleCardElevation,
            activeTipKeyState = activeTipKeyState
        )

        SpeedPitchSection(
            playbackSpeed = playbackSpeed,
            playbackPitch = playbackPitch,
            onPlaybackParametersChanged = onPlaybackParametersChanged,
            onPlaybackSpeedChanged = onPlaybackSpeedChanged,
            onPlaybackPitchChanged = onPlaybackPitchChanged,
            settings = settings,
            onSettingsChanged = onSettingsChanged,
            colorScheme = colorScheme,
            sliderColors = sliderColors,
            moduleCardContainerColor = moduleCardContainerColor,
            moduleCardElevation = moduleCardElevation,
            activeTipKeyState = activeTipKeyState
        )

        StereoSection(
            settings = settings,
            onSettingsChanged = onSettingsChanged,
            colorScheme = colorScheme,
            sliderColors = sliderColors,
            moduleCardContainerColor = moduleCardContainerColor,
            moduleCardElevation = moduleCardElevation,
            activeTipKeyState = activeTipKeyState
        )

        EqualizerSection(
            settings = settings,
            onSettingsChanged = onSettingsChanged,
            colorScheme = colorScheme,
            materialColorScheme = materialColorScheme,
            sliderColors = sliderColors,
            moduleCardContainerColor = moduleCardContainerColor,
            moduleCardElevation = moduleCardElevation,
            allPresets = allPresets,
            onDeletePreset = onDeletePreset,
            editingLevelsState = editingLevelsState,
            showSaveDialogState = showSaveDialogState,
            activeTipKeyState = activeTipKeyState,
            sliderInactiveTrackColor = sliderInactiveTrackColor,
            sliderDisabledActiveTrackColor = sliderDisabledActiveTrackColor,
            sliderDisabledInactiveTrackColor = sliderDisabledInactiveTrackColor,
            sliderDisabledThumbColor = sliderDisabledThumbColor
        )
    }

    if (showSaveDialog) {
        FlatTextFieldDialog(
            onDismissRequest = { showSaveDialog = false },
            message = "请输入自定义预设名称。",
            value = newPresetName,
            onValueChange = { newPresetName = it },
            placeholder = "预设名称",
            confirmText = "保存",
            confirmEnabled = newPresetName.trim().isNotBlank(),
            onConfirm = {
                if (newPresetName.isNotBlank()) {
                    onSavePreset(newPresetName.trim())
                    showSaveDialog = false
                    newPresetName = ""
                }
            },
        )
    }
}