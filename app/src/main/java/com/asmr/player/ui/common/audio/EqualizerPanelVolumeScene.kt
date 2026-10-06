@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.asmr.player.ui.common.audio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardElevation
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asmr.player.data.settings.EqualizerSettings
import com.asmr.player.data.settings.SceneEffectPresets
import com.asmr.player.ui.theme.AsmrColorScheme

@Composable
internal fun VolumeThresholdSection(
    settings: EqualizerSettings,
    onSettingsChanged: (EqualizerSettings) -> Unit,
    colorScheme: AsmrColorScheme,
    sliderColors: SliderColors,
    moduleCardContainerColor: Color,
    moduleCardElevation: CardElevation,
    activeTipKeyState: MutableState<String?>
) {
    var activeTipKey by activeTipKeyState
    fun updateVolumeThreshold(
        enabled: Boolean? = null,
        mode: Int? = null,
        minDb: Float? = null,
        maxDb: Float? = null,
        loudnessTargetDb: Float? = null
    ) {
        val nextEnabled = enabled ?: settings.volumeThresholdEnabled
        val nextMode = (mode ?: settings.volumeThresholdMode).coerceIn(0, 1)
        var nextMin = minDb ?: settings.volumeThresholdMinDb
        var nextMax = maxDb ?: settings.volumeThresholdMaxDb
        val nextLoudnessTargetDb = (loudnessTargetDb ?: settings.volumeLoudnessTargetDb).coerceIn(-60f, 0f)
        nextMin = nextMin.coerceIn(-60f, 0f)
        nextMax = nextMax.coerceIn(-60f, 0f)
        if (nextMin >= nextMax) {
            if (minDb != null && maxDb == null) {
                nextMin = nextMax - 1f
            } else {
                nextMax = nextMin + 1f
            }
        }
        onSettingsChanged(
            settings.copy(
                volumeThresholdEnabled = nextEnabled,
                volumeThresholdMode = nextMode,
                volumeThresholdMinDb = nextMin,
                volumeThresholdMaxDb = nextMax,
                volumeLoudnessTargetDb = nextLoudnessTargetDb
            )
        )
    }

    val vtEnabled = settings.volumeThresholdEnabled
    val vtMode = settings.volumeThresholdMode
    val defaultVtMode = 1
    val defaultVtMinDb = -24f
    val defaultVtMaxDb = -6f
    val defaultVtTargetDb = -18f
    ModuleCard(moduleCardContainerColor = moduleCardContainerColor, moduleCardElevation = moduleCardElevation,
        title = "音量阈值",
        enabled = vtEnabled,
        onEnabledChange = { updateVolumeThreshold(enabled = it) },
        expanded = settings.volumeThresholdExpanded,
        onExpandedChange = { onSettingsChanged(settings.copy(volumeThresholdExpanded = it)) },
        onReset = { updateVolumeThreshold(mode = defaultVtMode, minDb = defaultVtMinDb, maxDb = defaultVtMaxDb, loudnessTargetDb = defaultVtTargetDb) },
        resetEnabled = vtEnabled ||
            settings.volumeThresholdMode != defaultVtMode ||
            settings.volumeThresholdMinDb != defaultVtMinDb ||
            settings.volumeThresholdMaxDb != defaultVtMaxDb ||
            settings.volumeLoudnessTargetDb != defaultVtTargetDb
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("模式", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            InfoTip(activeTipKeyState = activeTipKeyState,
                key = "vt_mode",
                title = "模式",
                text = "响度均衡：把整体响度拉向目标值，适合不同音频之间的音量统一；阈值：把响度限制在一个区间，更像自动增益+压制。"
            )
        }
        val modeChipColors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = colorScheme.primary.copy(alpha = 0.18f),
            selectedLabelColor = colorScheme.primary,
            selectedLeadingIconColor = colorScheme.primary
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1 to "响度均衡", 0 to "阈值").forEach { (m, label) ->
                FilterChip(
                    selected = vtMode == m,
                    onClick = { updateVolumeThreshold(mode = m) },
                    label = { Text(label) },
                    enabled = true,
                    colors = modeChipColors,
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = vtMode == m,
                        borderColor = colorScheme.primary.copy(alpha = 0.22f),
                        selectedBorderColor = colorScheme.primary
                    )
                )
            }
        }

        if (vtMode == 1) {
            Column {
                Row {
                    Text("目标响度", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    InfoTip(activeTipKeyState = activeTipKeyState,
                        key = "vt_target",
                        title = "目标响度",
                        text = "把播放响度逐步拉向该目标值（变化更平滑，更适合不同音频之间的统一）。建议范围：-24 到 -14。"
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(String.format("%.0f dBFS", settings.volumeLoudnessTargetDb), style = MaterialTheme.typography.labelSmall)
                }
                Slider(
                    value = settings.volumeLoudnessTargetDb,
                    onValueChange = { updateVolumeThreshold(loudnessTargetDb = it) },
                    valueRange = -36f..-6f,
                    enabled = vtEnabled,
                    colors = sliderColors
                )
            }
        } else {
            Column {
                Row {
                    Text("最小阈值", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    InfoTip(activeTipKeyState = activeTipKeyState,
                        key = "vt_min",
                        title = "最小阈值",
                        text = "当音量低于该值时，会自动放大到接近该阈值。"
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(String.format("%.0f dBFS", settings.volumeThresholdMinDb), style = MaterialTheme.typography.labelSmall)
                }
                Slider(
                    value = settings.volumeThresholdMinDb,
                    onValueChange = { updateVolumeThreshold(minDb = it) },
                    valueRange = -60f..0f,
                    enabled = vtEnabled,
                    colors = sliderColors
                )
            }

            Column {
                Row {
                    Text("最大阈值", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    InfoTip(activeTipKeyState = activeTipKeyState,
                        key = "vt_max",
                        title = "最大阈值",
                        text = "当音量高于该值时，会自动压制到不超过该阈值。"
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(String.format("%.0f dBFS", settings.volumeThresholdMaxDb), style = MaterialTheme.typography.labelSmall)
                }
                Slider(
                    value = settings.volumeThresholdMaxDb,
                    onValueChange = { updateVolumeThreshold(maxDb = it) },
                    valueRange = -60f..0f,
                    enabled = vtEnabled,
                    colors = sliderColors
                )
            }
        }
    }
}

@Composable
internal fun SceneEffectSection(
    settings: EqualizerSettings,
    onSettingsChanged: (EqualizerSettings) -> Unit,
    colorScheme: AsmrColorScheme,
    materialColorScheme: ColorScheme,
    sliderColors: SliderColors,
    moduleCardContainerColor: Color,
    moduleCardElevation: CardElevation,
    activeTipKeyState: MutableState<String?>
) {
    var activeTipKey by activeTipKeyState
    val scenePresets = remember { SceneEffectPresets.All }
    fun updateSceneEffect(
        enabled: Boolean? = null,
        presetId: String? = null,
        amount: Int? = null
    ) {
        onSettingsChanged(
            settings.copy(
                sceneEffectEnabled = enabled ?: settings.sceneEffectEnabled,
                sceneEffectPresetId = presetId ?: settings.sceneEffectPresetId,
                sceneEffectAmount = (amount ?: settings.sceneEffectAmount).coerceIn(0, 100)
            )
        )
    }

    val sceneEnabled = settings.sceneEffectEnabled
    val activeScenePreset = remember(settings.sceneEffectPresetId) {
        SceneEffectPresets.resolve(settings.sceneEffectPresetId)
    }
    ModuleCard(moduleCardContainerColor = moduleCardContainerColor, moduleCardElevation = moduleCardElevation,
        title = "场景效果",
        enabled = sceneEnabled,
        onEnabledChange = { updateSceneEffect(enabled = it) },
        expanded = settings.sceneEffectExpanded,
        onExpandedChange = { onSettingsChanged(settings.copy(sceneEffectExpanded = it)) },
        onReset = {
            updateSceneEffect(
                presetId = SceneEffectPresets.DefaultPresetId,
                amount = SceneEffectPresets.DefaultAmount
            )
        },
        resetEnabled = sceneEnabled ||
            settings.sceneEffectPresetId != SceneEffectPresets.DefaultPresetId ||
            settings.sceneEffectAmount != SceneEffectPresets.DefaultAmount
    ) {
        var sceneExpanded by remember { mutableStateOf(false) }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("场景预设", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                InfoTip(activeTipKeyState = activeTipKeyState,
                    key = "scene_preset",
                    title = "场景效果",
                    text = "通过带限、声场收窄和延迟反射来模拟常见空间或传输介质。推荐先选预设，再微调强度。"
                )
            }
            ExposedDropdownMenuBox(
                expanded = sceneExpanded,
                onExpandedChange = { if (sceneEnabled) sceneExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "预设",
                    style = MaterialTheme.typography.labelSmall,
                    color = materialColorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = materialColorScheme.surface.copy(alpha = 0.55f),
                    contentColor = materialColorScheme.onSurface,
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = materialColorScheme.outlineVariant.copy(alpha = 0.65f)
                    ),
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                        .height(44.dp)
                        .clickable(enabled = sceneEnabled) { sceneExpanded = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = activeScenePreset.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.widthIn(min = 0.dp, max = 124.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = activeScenePreset.description,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = materialColorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = sceneExpanded)
                    }
                }
                MaterialTheme(
                    colorScheme = materialColorScheme.copy(
                        surface = moduleCardContainerColor,
                        surfaceVariant = moduleCardContainerColor
                    )
                ) {
                    DropdownMenu(
                        expanded = sceneExpanded,
                        onDismissRequest = { sceneExpanded = false },
                        modifier = Modifier
                            .exposedDropdownSize(matchTextFieldWidth = true)
                            .background(moduleCardContainerColor, RoundedCornerShape(14.dp))
                    ) {
                        scenePresets.forEachIndexed { index, preset ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    thickness = 0.5.dp,
                                    color = materialColorScheme.outlineVariant.copy(alpha = 0.3f)
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = preset.label,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(min = 0.dp, max = 124.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = preset.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = materialColorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                                        )
                                    }
                                },
                                onClick = {
                                    updateSceneEffect(enabled = true, presetId = preset.id)
                                    sceneExpanded = false
                                },
                                enabled = sceneEnabled,
                                colors = MenuDefaults.itemColors(
                                    textColor = materialColorScheme.onSurface,
                                    leadingIconColor = materialColorScheme.onSurface,
                                    trailingIconColor = materialColorScheme.onSurface,
                                    disabledTextColor = materialColorScheme.onSurface.copy(alpha = 0.38f),
                                    disabledLeadingIconColor = materialColorScheme.onSurface.copy(alpha = 0.38f),
                                    disabledTrailingIconColor = materialColorScheme.onSurface.copy(alpha = 0.38f)
                                )
                            )
                        }
                    }
                }
            }
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("效果强度", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    InfoTip(activeTipKeyState = activeTipKeyState,
                        key = "scene_amount",
                        title = "效果强度",
                        text = "强度越高，场景特征越明显。像电话音、被窝闷声这类预设在高强度下会更有辨识度。"
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text("${settings.sceneEffectAmount}%", style = MaterialTheme.typography.labelSmall)
                }
                Slider(
                    value = settings.sceneEffectAmount.toFloat(),
                    onValueChange = { updateSceneEffect(enabled = true, amount = it.toInt()) },
                    valueRange = 0f..100f,
                    enabled = sceneEnabled,
                    colors = sliderColors
                )
            }
        }
    }
}