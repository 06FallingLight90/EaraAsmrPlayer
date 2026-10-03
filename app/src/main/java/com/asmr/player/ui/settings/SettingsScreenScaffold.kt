package com.asmr.player.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.automirrored.rounded.FormatAlignRight
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FormatAlignCenter
import androidx.compose.material.icons.rounded.FormatAlignLeft
import androidx.compose.material.icons.rounded.FormatAlignRight
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import com.asmr.player.ui.common.core.isCompactWidth
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.asmr.player.BuildConfig
import com.asmr.player.cache.AppCacheLimits
import com.asmr.player.cache.AppCacheState
import com.asmr.player.data.settings.CoverPreviewMode
import com.asmr.player.data.settings.DeepSeekReasoningEffort
import com.asmr.player.data.settings.DeepSeekTranslationSettings
import com.asmr.player.data.settings.FloatingLyricsSettings
import com.asmr.player.data.settings.LyricsPageSettings
import com.asmr.player.data.settings.NowPlayingLyricsSettings
import com.asmr.player.subtitle.SubtitleDeviceCapability
import com.asmr.player.subtitle.SubtitleModelDownloadSource
import com.asmr.player.subtitle.SubtitleModelInstallationState
import com.asmr.player.subtitle.SubtitleModelOperation
import com.asmr.player.subtitle.SubtitleModelState
import com.asmr.player.subtitle.SubtitleTranscriptionModels
import com.asmr.player.subtitle.configuredSubtitleModelDownloadSources
import com.asmr.player.subtitle.DEEPSEEK_SUBTITLE_MODEL
import com.asmr.player.subtitle.DeepSeekAccountState
import com.asmr.player.subtitle.formatDeepSeekBalances
import com.asmr.player.subtitle.formatDeepSeekTokenTotal
import com.asmr.player.util.documentTreeDisplayPath
import com.asmr.player.ui.common.status.AppSupportStatusSection
import com.asmr.player.ui.common.cover.EaraLogoLoadingIndicator
import com.asmr.player.ui.common.dialog.FlatActionDialog
import com.asmr.player.ui.common.dialog.FlatDialogAction
import com.asmr.player.ui.common.dialog.FlatDialogActionTone
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.common.list.LocalBottomOverlayPadding
import com.asmr.player.ui.common.list.rememberCalmScrollableFlingBehavior
import com.asmr.player.ui.common.list.smoothScrollToTop
import com.asmr.player.ui.common.list.withAddedBottomPadding
import com.asmr.player.ui.common.list.collectAsStateWhileActive
import com.asmr.player.util.Formatting
import java.io.File
import kotlin.math.abs

@Composable
internal fun SettingsSectionsPanel(
    onSectionClick: (SettingsSection) -> Unit,
) {
    val colorScheme = AsmrTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.surface.copy(alpha = 0.32f),
        contentColor = colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            SettingsSection.entries.forEachIndexed { index, section ->
                SettingsSectionOption(
                    section = section,
                    onClick = { onSectionClick(section) },
                )
                if (index < SettingsSection.entries.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 58.dp, end = 14.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                    )
                }
            }
        }
    }
}

@Composable
internal fun SettingsSectionOption(
    section: SettingsSection,
    onClick: () -> Unit,
) {
    val colorScheme = AsmrTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("settingsSection:${section.name}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = section.icon,
            contentDescription = null,
            tint = colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = section.description,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = "进入${section.title}",
            tint = colorScheme.textSecondary,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
internal fun SettingsDetailHeader(
    section: SettingsSection,
    onBack: () -> Unit,
) {
    val colorScheme = AsmrTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("settingsDetailBack"),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回设置",
                tint = colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colorScheme.textPrimary,
            )
            Text(
                text = section.description,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.textSecondary,
            )
        }
    }
}

@Composable
internal fun SettingsDetailCard(
    content: @Composable ColumnScope.() -> Unit,
) {
    val colorScheme = AsmrTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.surface.copy(alpha = 0.5f),
        contentColor = colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
internal fun SettingsToggleRow(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    infoKey: String? = null,
    infoTitle: String = text,
    infoText: String? = null,
    activeTipKey: String? = null,
    onToggleTip: ((String) -> Unit)? = null
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SettingsRowLabel(
            text = text,
            infoKey = infoKey,
            infoTitle = infoTitle,
            infoText = infoText,
            activeTipKey = activeTipKey,
            onToggleTip = onToggleTip,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
internal fun SettingsSliderRow(
    text: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    stepSize: Float? = null,
    infoKey: String? = null,
    infoTitle: String = text,
    infoText: String? = null,
    activeTipKey: String? = null,
    onToggleTip: ((String) -> Unit)? = null,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    onHorizontalControlInteractionChanged: (Boolean) -> Unit = {}
) {
    val sliderInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val isDragging by sliderInteractionSource.collectIsDraggedAsState()
    val isPressed by sliderInteractionSource.collectIsPressedAsState()
    val isInteracting = isDragging || isPressed
    val steps = stepSize
        ?.takeIf { it > 0f }
        ?.let { ((range.endInclusive - range.start) / it).toInt() - 1 }
        ?.coerceAtLeast(0)
        ?: 0
    LaunchedEffect(isInteracting, onHorizontalControlInteractionChanged) {
        onHorizontalControlInteractionChanged(isInteracting)
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsRowLabel(
            text = text,
            infoKey = infoKey,
            infoTitle = infoTitle,
            infoText = infoText,
            activeTipKey = activeTipKey,
            onToggleTip = onToggleTip
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            interactionSource = sliderInteractionSource,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun DeferredCommitSettingsSliderRow(
    committedValue: Float,
    range: ClosedFloatingPointRange<Float>,
    stepSize: Float? = null,
    textForValue: (Float) -> String,
    onValueCommitted: (Float) -> Unit,
    infoKey: String? = null,
    infoTitle: String = "",
    infoText: String? = null,
    activeTipKey: String? = null,
    onToggleTip: ((String) -> Unit)? = null,
    onHorizontalControlInteractionChanged: (Boolean) -> Unit = {}
) {
    SettingsSliderRow(
        text = textForValue(committedValue),
        value = committedValue.coerceIn(range.start, range.endInclusive),
        range = range,
        stepSize = stepSize,
        infoKey = infoKey,
        infoTitle = infoTitle.ifBlank { textForValue(committedValue) },
        infoText = infoText,
        activeTipKey = activeTipKey,
        onToggleTip = onToggleTip,
        onValueChange = onValueCommitted,
        onHorizontalControlInteractionChanged = onHorizontalControlInteractionChanged
    )
}

@Composable
internal fun SettingsRowLabel(
    text: String,
    infoKey: String? = null,
    infoTitle: String = text,
    infoText: String? = null,
    activeTipKey: String? = null,
    onToggleTip: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (infoKey != null && !infoText.isNullOrBlank() && onToggleTip != null) {
            SettingsInfoTip(
                active = activeTipKey == infoKey,
                title = infoTitle,
                text = infoText,
                onToggle = { onToggleTip(infoKey) }
            )
        }
    }
}

@Composable
internal fun SettingsInfoTip(active: Boolean, title: String, text: String, onToggle: () -> Unit) {
    val density = LocalDensity.current
    val offset = with(density) { IntOffset(0, 26.dp.roundToPx()) }

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    ) {
        Box {
            IconButton(
                onClick = onToggle,
                modifier = Modifier.size(22.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = "${title}说明",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
            if (active) {
                Popup(
                    alignment = Alignment.TopStart,
                    offset = offset,
                    onDismissRequest = onToggle,
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
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.widthIn(max = 260.dp).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ThemeModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colorScheme = AsmrTheme.colorScheme
    val isDark = colorScheme.isDark
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = colorScheme.primarySoft,
            selectedLabelColor = if (isDark) colorScheme.onPrimaryContainer else colorScheme.primaryStrong
        )
    )
}

@Composable
internal fun ThemeColorDot(color: Color?, selected: Boolean, onClick: () -> Unit) {
    val fill = color ?: AsmrTheme.colorScheme.primaryStrong
    val borderColor = if (selected) AsmrTheme.colorScheme.onSurface else AsmrTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val borderWidth = if (selected) 2.dp else 1.dp
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(fill)
            .border(borderWidth, borderColor, CircleShape)
            .clickable(onClick = onClick)
    )
}

@Composable
internal fun ThemeMonochromeDot(selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) AsmrTheme.colorScheme.onSurface else AsmrTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val borderWidth = if (selected) 2.dp else 1.dp
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(
                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                    colors = listOf(Color(0xFF68717C), Color(0xFFE1E7ED))
                )
            )
            .border(borderWidth, borderColor, CircleShape)
            .clickable(onClick = onClick)
    )
}

@Composable
internal fun PreviewModeInfoTip(active: Boolean, onToggle: () -> Unit) {
    val density = LocalDensity.current
    val offset = with(density) { IntOffset(0, 26.dp.roundToPx()) }

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    ) {
        Box {
            IconButton(
                onClick = onToggle,
                modifier = Modifier.size(22.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
            if (active) {
                Popup(
                    alignment = Alignment.TopStart,
                    offset = offset,
                    onDismissRequest = onToggle,
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
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.widthIn(max = 260.dp).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "背景封面预览方式",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "关闭：背景与封面保持居中静止",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "滑动：播放页封面与歌词页背景都使用双指拖动预览，且会临时屏蔽左侧菜单侧滑",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "转动：通过转动手机预览封面其他区域",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
