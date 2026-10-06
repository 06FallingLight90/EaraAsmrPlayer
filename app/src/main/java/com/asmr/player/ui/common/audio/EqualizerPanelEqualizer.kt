@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.asmr.player.ui.common.audio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CardElevation
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asmr.player.data.settings.AsmrPreset
import com.asmr.player.data.settings.EqualizerSettings
import com.asmr.player.ui.theme.AsmrColorScheme
import kotlin.math.abs

@Composable
internal fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    colorScheme: AsmrColorScheme,
    sliderInactiveTrackColor: Color,
    sliderDisabledActiveTrackColor: Color,
    sliderDisabledInactiveTrackColor: Color,
    sliderDisabledThumbColor: Color,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val min = valueRange.start
    val max = valueRange.endInclusive
    val range = (max - min).coerceAtLeast(1e-6f)
    val trackColor = if (enabled) sliderInactiveTrackColor.copy(alpha = 0.85f) else sliderDisabledInactiveTrackColor
    val activeColor = if (enabled) colorScheme.primary else sliderDisabledActiveTrackColor
    val thumbColor = if (enabled) colorScheme.primary else sliderDisabledThumbColor
    val centerColor = sliderInactiveTrackColor.copy(alpha = 0.65f)
    val trackWidthPx = with(density) { 6.dp.toPx() }
    val thumbRadiusPx = with(density) { 9.dp.toPx() }
    val centerMarkHalfPx = with(density) { 10.dp.toPx() }

    fun yToValue(y: Float, height: Float): Float {
        val h = height.coerceAtLeast(1f)
        val frac = (1f - (y / h)).coerceIn(0f, 1f)
        return (min + frac * range).coerceIn(min, max)
    }

    val gestureModifier = if (enabled) {
        modifier
            .pointerInput(min, max) {
                detectTapGestures { offset ->
                    onValueChange(yToValue(offset.y, size.height.toFloat()))
                }
            }
            .pointerInput(min, max) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        onValueChange(yToValue(change.position.y, size.height.toFloat()))
                    }
                )
            }
    } else {
        modifier
    }

    Canvas(modifier = gestureModifier) {
        val w = size.width
        val h = size.height
        val x = w / 2f
        val centerY = h / 2f
        val coerced = value.coerceIn(min, max)
        val frac = (coerced - min) / range
        val thumbY = (h - frac * h).coerceIn(0f, h)

        drawLine(
            color = trackColor,
            start = Offset(x, 0f),
            end = Offset(x, h),
            strokeWidth = trackWidthPx,
            cap = StrokeCap.Round
        )
        drawLine(
            color = centerColor,
            start = Offset((x - centerMarkHalfPx).coerceAtLeast(0f), centerY),
            end = Offset((x + centerMarkHalfPx).coerceAtMost(w), centerY),
            strokeWidth = trackWidthPx,
            cap = StrokeCap.Round
        )
        drawLine(
            color = activeColor,
            start = Offset(x, centerY),
            end = Offset(x, thumbY),
            strokeWidth = trackWidthPx,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = thumbColor,
            radius = thumbRadiusPx,
            center = Offset(x, thumbY)
        )
    }
}

@Composable
internal fun EqualizerSection(
    settings: EqualizerSettings,
    onSettingsChanged: (EqualizerSettings) -> Unit,
    colorScheme: AsmrColorScheme,
    materialColorScheme: ColorScheme,
    sliderColors: SliderColors,
    moduleCardContainerColor: Color,
    moduleCardElevation: CardElevation,
    allPresets: List<AsmrPreset>,
    onDeletePreset: (AsmrPreset) -> Unit,
    editingLevelsState: MutableState<List<Int>>,
    showSaveDialogState: MutableState<Boolean>,
    activeTipKeyState: MutableState<String?>,
    sliderInactiveTrackColor: Color,
    sliderDisabledActiveTrackColor: Color,
    sliderDisabledInactiveTrackColor: Color,
    sliderDisabledThumbColor: Color
) {
    var activeTipKey by activeTipKeyState
    var editingLevels by editingLevelsState
    var showSaveDialog by showSaveDialogState
    fun updateLevel(index: Int, value: Int) {
        val newLevels = editingLevels.toMutableList()
        newLevels[index] = value
        editingLevels = newLevels
        onSettingsChanged(settings.copy(enabled = true, bandLevels = newLevels, presetName = "自定义"))
    }
    val freqLabels = remember { listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k") }

    val eqEnabled = settings.enabled
    ModuleCard(moduleCardContainerColor = moduleCardContainerColor, moduleCardElevation = moduleCardElevation,
        title = "均衡器",
        enabled = eqEnabled,
        onEnabledChange = { onSettingsChanged(settings.copy(enabled = it, presetName = "自定义")) },
        expanded = settings.equalizerExpanded,
        onExpandedChange = { onSettingsChanged(settings.copy(equalizerExpanded = it)) },
        onReset = {
            onSettingsChanged(
                settings.copy(
                    enabled = true,
                    bandLevels = List(10) { 0 },
                    presetName = "默认"
                )
            )
        },
        resetEnabled = eqEnabled
    ) {
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { if (eqEnabled) expanded = it },
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
                    .clickable(enabled = eqEnabled) { expanded = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = settings.presetName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            }
            MaterialTheme(
                colorScheme = materialColorScheme.copy(
                    surface = moduleCardContainerColor,
                    surfaceVariant = moduleCardContainerColor
                )
            ) {
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .exposedDropdownSize(matchTextFieldWidth = true)
                        .background(moduleCardContainerColor, RoundedCornerShape(14.dp))
                ) {
                    allPresets.forEachIndexed { index, preset ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                thickness = 0.5.dp,
                                color = materialColorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(preset.name)
                                    if (preset.isCustom) {
                                        Spacer(modifier = Modifier.weight(1f))
                                        IconButton(onClick = { onDeletePreset(preset) }) {
                                            Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            },
                            onClick = {
                                val levels = preset.bandLevels.let { src ->
                                    val out = MutableList(10) { 0 }
                                    for (i in 0 until minOf(10, src.size)) out[i] = src[i]
                                    out
                                }
                                onSettingsChanged(
                                    settings.copy(
                                        enabled = true,
                                        bandLevels = levels,
                                        presetName = preset.name
                                    )
                                )
                                expanded = false
                            },
                            colors = MenuDefaults.itemColors(
                                textColor = materialColorScheme.onSurface,
                                leadingIconColor = materialColorScheme.onSurface,
                                trailingIconColor = materialColorScheme.onSurface,
                                disabledTextColor = materialColorScheme.onSurface.copy(alpha = 0.38f),
                                disabledLeadingIconColor = materialColorScheme.onSurface.copy(alpha = 0.38f),
                                disabledTrailingIconColor = materialColorScheme.onSurface.copy(alpha = 0.38f)
                            ),
                            enabled = eqEnabled
                        )
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("频段调节", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            InfoTip(activeTipKeyState = activeTipKeyState,
                key = "eq_bands",
                title = "频段调节",
                text = "每个滑条对应一个中心频段。向上提升、向下削减该频段的能量。"
            )
        }
        // val levels = normalizedLevels()  // 不再使用，改用 currentLevels
        val scrollState = rememberScrollState()
        val sliderHeight = 230.dp
        val sliderThickness = 44.dp
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                for (i in 0 until 10) {
                    val db = (editingLevels[i] / 100f)
                    val dbText = if (abs(db) < 0.01f) "0 dB" else "${db.toInt()} dB"
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(freqLabels[i], style = MaterialTheme.typography.labelSmall)
                        Box(
                            modifier = Modifier
                                .height(sliderHeight)
                                .width(sliderThickness),
                            contentAlignment = Alignment.Center
                        ) {
                            VerticalSlider(
                                value = editingLevels[i].toFloat(),
                                onValueChange = { updateLevel(i, it.toInt()) },
                                valueRange = -1500f..1500f,
                                enabled = eqEnabled,
                                colorScheme = colorScheme,
                                sliderInactiveTrackColor = sliderInactiveTrackColor,
                                sliderDisabledActiveTrackColor = sliderDisabledActiveTrackColor,
                                sliderDisabledInactiveTrackColor = sliderDisabledInactiveTrackColor,
                                sliderDisabledThumbColor = sliderDisabledThumbColor,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Text(dbText, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (scrollState.canScrollForward) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(36.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    moduleCardContainerColor.copy(alpha = 0f),
                                    moduleCardContainerColor
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = materialColorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    )
                }
            }
            if (scrollState.canScrollBackward) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(36.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    moduleCardContainerColor,
                                    moduleCardContainerColor.copy(alpha = 0f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        contentDescription = null,
                        tint = materialColorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    )
                }
            }
        }

        Button(
            onClick = { showSaveDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            enabled = eqEnabled
        ) {
            Icon(Icons.Rounded.Save, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("保存为自定义预设")
        }
    }
}