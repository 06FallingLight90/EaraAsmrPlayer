package com.asmr.player.ui.common.audio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

@Composable
internal fun InfoTip(
    key: String,
    title: String,
    text: String,
    activeTipKeyState: MutableState<String?>
) {
    var activeTipKey by activeTipKeyState
    val density = LocalDensity.current
    val expanded = activeTipKey == key
    val offset = with(density) { IntOffset(0, 26.dp.roundToPx()) }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    ) {
        Box {
            IconButton(
                onClick = { activeTipKey = if (expanded) null else key },
                modifier = Modifier.size(22.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
            if (expanded) {
                Popup(
                    alignment = Alignment.TopStart,
                    offset = offset,
                    onDismissRequest = { activeTipKey = null },
                    properties = PopupProperties(
                        focusable = true,
                        dismissOnBackPress = true,
                        dismissOnClickOutside = true
                    )
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        tonalElevation = 6.dp,
                        shadowElevation = 10.dp,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier.widthIn(max = 280.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ModuleCard(
    title: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onReset: () -> Unit,
    resetEnabled: Boolean = enabled,
    moduleCardContainerColor: Color,
    moduleCardElevation: CardElevation,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = moduleCardContainerColor),
        elevation = moduleCardElevation
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    modifier = Modifier.scale(0.85f)
                )
                TextButton(onClick = onReset, enabled = resetEnabled) { Text("重置") }
                IconButton(onClick = { onExpandedChange(!expanded) }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.KeyboardArrowDown else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null
                    )
                }
            }
            if (expanded) {
                Column(
                    modifier = Modifier.alpha(if (enabled) 1f else 0.45f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content
                )
            }
        }
    }
}