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
import com.asmr.player.util.AppCacheLimits
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
import kotlin.math.roundToInt

@Composable
internal fun NowPlayingLyricsSettingsSection(
    settings: NowPlayingLyricsSettings,
    onSettingsChange: (NowPlayingLyricsSettings) -> Unit,
    onHorizontalControlInteractionChanged: (Boolean) -> Unit = {}
) {
    Text("播放页歌词", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    SettingsToggleRow(
        text = "多行完整显示",
        checked = settings.multilineEnabled,
        onCheckedChange = { onSettingsChange(settings.copy(multilineEnabled = it)) }
    )
    Text(
        text = "竖屏经典布局使用固定字幕区，悬浮歌词按实际行数调整高度。超长字幕可上下滑动阅读；悬浮歌词可拖动字幕区域边缘调整位置，开启点击穿透后无法滑动。",
        style = MaterialTheme.typography.bodySmall,
        color = AsmrTheme.colorScheme.textSecondary
    )
    SettingsSliderRow(
        text = "高亮字体大小: ${settings.highlightFontSizeSp.toInt()}sp",
        value = settings.highlightFontSizeSp,
        range = 18f..36f,
        stepSize = 1f,
        onValueChange = { onSettingsChange(settings.copy(highlightFontSizeSp = it)) },
        onHorizontalControlInteractionChanged = onHorizontalControlInteractionChanged
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeepSeekTranslationSettingsSection(
    state: DeepSeekApiKeyUiState,
    accountState: DeepSeekAccountState = DeepSeekAccountState(),
    settings: DeepSeekTranslationSettings,
    apiKeyInput: String,
    compact: Boolean,
    segmentedButtonColors: SegmentedButtonColors,
    onApiKeyInputChanged: (String) -> Unit,
    onSave: () -> Unit,
    onThinkingEnabledChanged: (Boolean) -> Unit,
    onReasoningEffortChanged: (DeepSeekReasoningEffort) -> Unit,
    onFinalPolishEnabledChanged: (Boolean) -> Unit,
    activeTipKey: String? = null,
    onToggleTip: ((String) -> Unit)? = null
) {
    val colorScheme = AsmrTheme.colorScheme
    val actionButtonColors = settingsPrimaryTonalButtonColors()

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = DEEPSEEK_SUBTITLE_MODEL,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .testTag("deepseek_model_name")
        )
        if (state.configured) {
            Row(
                modifier = Modifier.width(if (compact) 208.dp else 248.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Token ${formatDeepSeekTokenTotal(accountState.totalTokens)} · 余额 ${formatDeepSeekBalances(accountState.balances)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (accountState.balanceAvailable == false) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("deepseek_account_summary")
                )
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "API Key 已配置",
                    tint = Color(0xFF3E9B63),
                    modifier = Modifier
                        .size(20.dp)
                        .testTag("deepseek_api_key_configured")
                )
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val inputModifier = if (compact) {
            Modifier.weight(1f)
        } else {
            Modifier.widthIn(max = 280.dp)
        }
        OutlinedTextField(
            value = apiKeyInput,
            onValueChange = onApiKeyInputChanged,
            modifier = inputModifier
                .height(48.dp)
                .testTag("deepseek_api_key_input"),
            placeholder = {
                Text(
                    text = "API Key（sk-…）",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            enabled = !state.saving,
            isError = state.errorMessage != null
        )
        FilledTonalButton(
            onClick = onSave,
            enabled = apiKeyInput.isNotBlank() && !state.saving,
            modifier = Modifier
                .height(48.dp)
                .testTag("deepseek_api_key_action"),
            colors = actionButtonColors,
            shape = RoundedCornerShape(14.dp)
        ) {
            if (state.saving) {
                EaraLogoLoadingIndicator(size = 18.dp)
            } else {
                Text(if (state.configured) "替换" else "保存")
            }
        }
    }
    state.errorMessage?.let { message ->
        Text(
            text = message,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error
        )
    }

    SettingsToggleRow(
        text = "思考模式",
        checked = settings.thinkingEnabled,
        onCheckedChange = onThinkingEnabledChanged
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "思考等级",
            style = MaterialTheme.typography.bodyMedium,
            color = if (settings.thinkingEnabled) colorScheme.textPrimary else colorScheme.textTertiary
        )
        Spacer(modifier = Modifier.weight(1f))
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .widthIn(max = 220.dp)
                .testTag("deepseek_reasoning_effort")
        ) {
            DeepSeekReasoningEffort.entries.forEachIndexed { index, effort ->
                SegmentedButton(
                    selected = settings.reasoningEffort == effort,
                    onClick = { onReasoningEffortChanged(effort) },
                    enabled = settings.thinkingEnabled,
                    modifier = Modifier.testTag("deepseek_reasoning_${effort.wireValue}"),
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = DeepSeekReasoningEffort.entries.size
                    ),
                    colors = segmentedButtonColors,
                    icon = {},
                    label = {
                        Text(
                            when (effort) {
                                DeepSeekReasoningEffort.LOW -> "Low"
                                DeepSeekReasoningEffort.HIGH -> "High"
                                DeepSeekReasoningEffort.MAX -> "Max"
                            }
                        )
                    }
                )
            }
        }
    }

    SettingsToggleRow(
        text = "最终润色",
        checked = settings.finalPolishEnabled,
        onCheckedChange = onFinalPolishEnabledChanged,
        infoKey = "final_polish",
        infoTitle = "最终润色",
        infoText = "翻译完成后，可在任务管理中左滑作品卡片，对现有中文字幕进行整体润色。此操作会额外消耗 Token。",
        activeTipKey = activeTipKey,
        onToggleTip = onToggleTip
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubtitleModelSettingsSection(
    state: SubtitleModelState,
    selectedSourceIds: Map<String, String>,
    deviceSupported: Boolean,
    segmentedButtonColors: SegmentedButtonColors,
    onSourceSelected: (String, SubtitleModelDownloadSource) -> Unit,
    onDownload: (String, SubtitleModelDownloadSource) -> Unit,
    onCancelDownload: () -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClearFailure: (String) -> Unit
) {
    var selectedModelId by rememberSaveable {
        mutableStateOf(
            state.operation?.modelId ?: SubtitleTranscriptionModels.SENSE_VOICE_SMALL_INT8.id
        )
    }
    LaunchedEffect(state.operation?.modelId) {
        state.operation?.modelId?.let { selectedModelId = it }
    }
    val model = SubtitleTranscriptionModels.fromId(selectedModelId)
        ?: SubtitleTranscriptionModels.default
    val installation = state.installation(model.id)
    val installed = installation is SubtitleModelInstallationState.Available
    val isActive = state.activeModelId == model.id
    val operation = state.operation?.takeIf { it.modelId == model.id }
    val running = operation is SubtitleModelOperation.Queued ||
        operation is SubtitleModelOperation.Downloading ||
        operation is SubtitleModelOperation.Verifying
    val anotherOperationRunning = state.operation != null &&
        state.operation.modelId != model.id &&
        state.operation !is SubtitleModelOperation.Failed
    val availableSources = configuredSubtitleModelDownloadSources(model)
    val selectedSource = operation?.source
        ?: SubtitleModelDownloadSource.fromId(selectedSourceIds[model.id])
        ?: availableSources.firstOrNull()
        ?: SubtitleModelDownloadSource.HuggingFace
    val colors = AsmrTheme.colorScheme

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SubtitleTranscriptionModels.all.forEachIndexed { index, candidate ->
                SegmentedButton(
                    selected = model.id == candidate.id,
                    onClick = { selectedModelId = candidate.id },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = SubtitleTranscriptionModels.all.size
                    ),
                    colors = segmentedButtonColors,
                    icon = {},
                    modifier = Modifier.testTag("subtitle_model_choice_${candidate.id}"),
                    label = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (state.activeModelId == candidate.id) {
                                    "${candidate.optionName} · 当前"
                                } else {
                                    candidate.optionName
                                },
                                maxLines = 1
                            )
                            Text(
                                text = Formatting.formatFileSize(candidate.artifactBytes),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        }
                    }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = model.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = when {
                    isActive && installed -> "当前使用"
                    isActive -> "当前（未安装）"
                    installed -> "已安装"
                    else -> "未安装"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (isActive) colors.primaryStrong else colors.textSecondary,
                modifier = Modifier.testTag("subtitle_model_status_${model.id}")
            )
        }

        if (!installed && availableSources.size > 1) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                availableSources.forEachIndexed { index, source ->
                    SegmentedButton(
                        selected = selectedSource == source,
                        onClick = {
                            onClearFailure(model.id)
                            onSourceSelected(model.id, source)
                        },
                        enabled = !running && !anotherOperationRunning,
                        shape = SegmentedButtonDefaults.itemShape(index, availableSources.size),
                        colors = segmentedButtonColors,
                        icon = {},
                        label = { Text(source.displayName) }
                    )
                }
            }
        }

        when (operation) {
            null -> Unit
            is SubtitleModelOperation.Queued -> {
                Text("等待下载字幕组件", style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            is SubtitleModelOperation.Downloading -> {
                Text(operation.stage.displayName, style = MaterialTheme.typography.bodySmall)
                if (operation.totalBytes > 0L) {
                    val progress = (operation.downloadedBytes.toFloat() / operation.totalBytes)
                        .coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            is SubtitleModelOperation.Verifying -> {
                Text(operation.stage.displayName, style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            is SubtitleModelOperation.Failed -> Text(
                text = operation.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        when {
            running -> FilledTonalButton(
                onClick = onCancelDownload,
                modifier = Modifier.fillMaxWidth(),
                colors = settingsPrimaryTonalButtonColors()
            ) {
                Text("取消下载")
            }
            installed && !isActive -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = { onSelect(model.id) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("subtitle_model_select_${model.id}"),
                    colors = settingsPrimaryTonalButtonColors()
                ) {
                    Text("设为当前")
                }
                FilledTonalButton(
                    onClick = { onDelete(model.id) },
                    colors = subtitleModelDeleteButtonColors()
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = "删除模型")
                }
            }
            installed -> FilledTonalButton(
                onClick = { onDelete(model.id) },
                modifier = Modifier.fillMaxWidth(),
                colors = subtitleModelDeleteButtonColors()
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("删除模型")
            }
            else -> FilledTonalButton(
                onClick = { onDownload(model.id, selectedSource) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("subtitle_model_download_${model.id}"),
                enabled = deviceSupported && availableSources.contains(selectedSource) &&
                    !anotherOperationRunning,
                colors = settingsPrimaryTonalButtonColors()
            ) {
                Text(
                    when {
                        !deviceSupported -> "设备不支持"
                        availableSources.isEmpty() -> "来源不可用"
                        anotherOperationRunning -> "其他模型正在下载"
                        operation is SubtitleModelOperation.Failed -> "重新下载"
                        else -> "下载模型"
                    }
                )
            }
        }
    }
}

@Composable
internal fun subtitleModelDeleteButtonColors(): ButtonColors =
    ButtonDefaults.filledTonalButtonColors(
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        disabledContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.48f),
        disabledContentColor = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.48f)
    )

@Composable
internal fun settingsPrimaryTonalButtonColors(): ButtonColors {
    val colorScheme = AsmrTheme.colorScheme
    val contentColor = if (colorScheme.isDark) {
        colorScheme.onPrimaryContainer
    } else {
        colorScheme.primaryStrong
    }
    return ButtonDefaults.filledTonalButtonColors(
        containerColor = colorScheme.primarySoft,
        contentColor = contentColor,
        disabledContainerColor = colorScheme.primarySoft.copy(alpha = 0.48f),
        disabledContentColor = contentColor.copy(alpha = 0.48f)
    )
}

@Composable
internal fun AppCacheSettingsSection(
    state: AppCacheState,
    onMaxSizeChanged: (Int) -> Unit,
    onClearClick: () -> Unit,
    onHorizontalControlInteractionChanged: (Boolean) -> Unit,
) {
    val colorScheme = AsmrTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isDragging by interactionSource.collectIsDraggedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isInteracting = isDragging || isPressed
    var draftSizeMb by remember { mutableFloatStateOf(state.maxSizeMb.toFloat()) }

    LaunchedEffect(state.maxSizeMb, isInteracting) {
        if (!isInteracting) draftSizeMb = state.maxSizeMb.toFloat()
    }

    Text(
        text = "当前占用：${formatCacheSize(state.usedSizeBytes)}",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = "空间由网络图片、在线音频播放和在线预览缓存共享。缓存满后会优先清理较早使用的资源。",
        style = MaterialTheme.typography.bodySmall,
        color = colorScheme.textSecondary,
    )
    SettingsSliderRow(
        text = "缓存空间上限：${draftSizeMb.roundToInt()} MB",
        value = draftSizeMb,
        range = AppCacheLimits.MinSizeMb.toFloat()..AppCacheLimits.MaxSizeMb.toFloat(),
        stepSize = AppCacheLimits.SizeStepMb.toFloat(),
        onValueChange = { draftSizeMb = it },
        onValueChangeFinished = { onMaxSizeChanged(draftSizeMb.roundToInt()) },
        interactionSource = interactionSource,
        onHorizontalControlInteractionChanged = onHorizontalControlInteractionChanged,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "最小 ${AppCacheLimits.MinSizeMb} MB",
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.textSecondary,
        )
        Text(
            text = "最大 ${AppCacheLimits.MaxSizeMb} MB",
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.textSecondary,
        )
    }
    FilledTonalButton(
        onClick = onClearClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("clearAppCacheButton"),
        enabled = !state.isClearing,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = colorScheme.primarySoft,
            contentColor = if (colorScheme.isDark) colorScheme.onPrimaryContainer else colorScheme.primaryStrong,
        ),
    ) {
        if (state.isClearing) {
            EaraLogoLoadingIndicator(size = 18.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Text("正在清理…")
        } else {
            Icon(Icons.Rounded.Delete, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("清理 APP 缓存")
        }
    }
}

internal fun formatCacheSize(sizeBytes: Long): String {
    val safeBytes = sizeBytes.coerceAtLeast(0L)
    val megabytes = safeBytes / (1024.0 * 1024.0)
    return if (megabytes < 0.1) {
        "0 MB"
    } else if (megabytes < 10.0) {
        String.format(java.util.Locale.ROOT, "%.1f MB", megabytes)
    } else {
        "${megabytes.roundToInt()} MB"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LyricsPageSettingsSection(
    settings: LyricsPageSettings,
    segmentedButtonColors: SegmentedButtonColors,
    onSettingsChange: (LyricsPageSettings) -> Unit,
    onHorizontalControlInteractionChanged: (Boolean) -> Unit = {}
) {
    Text("歌词页", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    SettingsSliderRow(
        text = "字体大小: ${settings.fontSizeSp.toInt()}sp",
        value = settings.fontSizeSp,
        range = 18f..36f,
        stepSize = 1f,
        onValueChange = { onSettingsChange(settings.copy(fontSizeSp = it)) },
        onHorizontalControlInteractionChanged = onHorizontalControlInteractionChanged
    )
    SettingsSliderRow(
        text = "字体阴影: ${"%.1f".format(settings.strokeWidthSp)}sp",
        value = settings.strokeWidthSp,
        range = 0f..3f,
        stepSize = 0.1f,
        onValueChange = { onSettingsChange(settings.copy(strokeWidthSp = it)) },
        onHorizontalControlInteractionChanged = onHorizontalControlInteractionChanged
    )
    SettingsSliderRow(
        text = "行间距: ${"%.2f".format(settings.lineHeightMultiplier)}x",
        value = settings.lineHeightMultiplier,
        range = 0.1f..3.0f,
        stepSize = 0.1f,
        onValueChange = { onSettingsChange(settings.copy(lineHeightMultiplier = it)) },
        onHorizontalControlInteractionChanged = onHorizontalControlInteractionChanged
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("显示区域", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.weight(1f))
        SingleChoiceSegmentedButtonRow {
            SegmentedButton(
                selected = settings.displayAreaMode == 0,
                onClick = { onSettingsChange(settings.copy(displayAreaMode = 0)) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 4),
                colors = segmentedButtonColors,
                icon = {},
                label = { Text("全屏") }
            )
            SegmentedButton(
                selected = settings.displayAreaMode == 1,
                onClick = { onSettingsChange(settings.copy(displayAreaMode = 1)) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 4),
                colors = segmentedButtonColors,
                icon = {},
                label = { Text("上1/4") }
            )
            SegmentedButton(
                selected = settings.displayAreaMode == 2,
                onClick = { onSettingsChange(settings.copy(displayAreaMode = 2)) },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 4),
                colors = segmentedButtonColors,
                icon = {},
                label = { Text("中1/4") }
            )
            SegmentedButton(
                selected = settings.displayAreaMode == 3,
                onClick = { onSettingsChange(settings.copy(displayAreaMode = 3)) },
                shape = SegmentedButtonDefaults.itemShape(index = 3, count = 4),
                colors = segmentedButtonColors,
                icon = {},
                label = { Text("下1/4") }
            )
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("对齐方式", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.weight(1f))
        SingleChoiceSegmentedButtonRow {
            SegmentedButton(
                selected = settings.align == 0,
                onClick = { onSettingsChange(settings.copy(align = 0)) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                colors = segmentedButtonColors,
                icon = {},
                label = { Icon(Icons.AutoMirrored.Rounded.FormatAlignLeft, null) }
            )
            SegmentedButton(
                selected = settings.align == 1,
                onClick = { onSettingsChange(settings.copy(align = 1)) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                colors = segmentedButtonColors,
                icon = {},
                label = { Icon(Icons.Rounded.FormatAlignCenter, null) }
            )
            SegmentedButton(
                selected = settings.align == 2,
                onClick = { onSettingsChange(settings.copy(align = 2)) },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                colors = segmentedButtonColors,
                icon = {},
                label = { Icon(Icons.AutoMirrored.Rounded.FormatAlignRight, null) }
            )
        }
    }
}

internal fun formatTreeRootLabel(uriString: String): String {
    val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return uriString
    val treeId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull().orEmpty()
    if (treeId.isBlank()) return uriString
    val doc = treeId.substringAfterLast(':', treeId)
    return doc.ifBlank { treeId }
}

@Composable
internal fun SearchBlockedKeywordsSection(
    input: String,
    keywords: List<String>,
    onInputChange: (String) -> Unit,
    onAddKeyword: () -> Unit,
    onRemoveKeyword: (String) -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val isDark = colorScheme.isDark
    val addButtonColors = ButtonDefaults.filledTonalButtonColors(
        containerColor = colorScheme.primarySoft,
        contentColor = if (isDark) colorScheme.onPrimaryContainer else colorScheme.primaryStrong
    )
    var showHelp by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "屏蔽关键词",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.textPrimary,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { showHelp = true },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = "搜索高级用法",
                    tint = colorScheme.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SearchBlockedKeywordInputField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            )
            FilledTonalButton(
                onClick = onAddKeyword,
                enabled = input.isNotBlank(),
                colors = addButtonColors,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
            ) {
                Text("添加")
            }
        }

        if (keywords.isEmpty()) {
            Text(
                text = "暂无屏蔽关键词",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.textSecondary
            )
        } else {
            SearchBlockedKeywordsChips(
                keywords = keywords,
                onRemoveKeyword = onRemoveKeyword
            )
        }
    }
    if (showHelp) {
        SearchBlockedKeywordsHelpDialog(onDismissRequest = { showHelp = false })
    }
}

@Composable
internal fun SearchBlockedKeywordsHelpDialog(
    onDismissRequest: () -> Unit
) {
    FlatActionDialog(
        message = "搜索高级用法",
        onDismissRequest = onDismissRequest,
        actions = listOf(
            FlatDialogAction(
                text = "知道了",
                tone = FlatDialogActionTone.Primary,
                onClick = onDismissRequest
            )
        )
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchHelpText("和 搜索(空格分割)：「雨声 助眠」，表示同时包含指定关键词")
            SearchHelpText("或 搜索(英文竖线分割)：「雨声|助眠」，表示包含一个或多个指定关键词均可")
            SearchHelpText("排除 搜索(空格与减号)：「雨声 -助眠」，表示排除指定关键词")
            SearchHelpText("完整 搜索(英文双引号包裹)：「\"【简体中文】 雨声\"」，表示将多个词当做完整词组搜索")
        }
    }
}

@Composable
internal fun SearchHelpText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = AsmrTheme.colorScheme.textSecondary
    )
}

@Composable
internal fun SearchBlockedKeywordsChips(
    keywords: List<String>,
    onRemoveKeyword: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val horizontalSpacing = 8.dp
    val verticalSpacing = 4.dp
    SubcomposeLayout(modifier = modifier.fillMaxWidth()) { constraints ->
        val itemSpacingPx = horizontalSpacing.roundToPx()
        val lineSpacingPx = verticalSpacing.roundToPx()
        val looseConstraints = Constraints()

        val keywordPlaceables = keywords.mapIndexed { index, keyword ->
            subcompose("keyword:$index:$keyword") {
                SearchBlockedKeywordChip(
                    keyword = keyword,
                    onRemoveKeyword = onRemoveKeyword
                )
            }.first().measure(looseConstraints)
        }

        val naturalSingleLineWidth = keywordPlaceables.sumOf { it.width } +
            itemSpacingPx * (keywordPlaceables.size - 1).coerceAtLeast(0)

        val contentFitsSingleLine = naturalSingleLineWidth <= constraints.maxWidth
        if (contentFitsSingleLine) {
            val rowHeight = keywordPlaceables.maxOfOrNull { it.height } ?: 0
            return@SubcomposeLayout layout(constraints.maxWidth, rowHeight) {
                var x = 0
                keywordPlaceables.forEachIndexed { index, placeable ->
                    placeable.placeRelative(
                        x,
                        (rowHeight - placeable.height) / 2
                    )
                    x += placeable.width
                    if (index < keywordPlaceables.lastIndex) {
                        x += itemSpacingPx
                    }
                }
            }
        }
        val lines = mutableListOf<MutableList<Int>>()
        val lineHeights = mutableListOf<Int>()
        var currentLine = mutableListOf<Int>()
        var currentWidth = 0
        var currentHeight = 0

        fun commitLine() {
            if (currentLine.isEmpty()) return
            lines += currentLine
            lineHeights += currentHeight
            currentLine = mutableListOf()
            currentWidth = 0
            currentHeight = 0
        }

        keywordPlaceables.forEachIndexed { index, placeable ->
            val nextWidth = if (currentLine.isEmpty()) {
                placeable.width
            } else {
                currentWidth + itemSpacingPx + placeable.width
            }
            if (currentLine.isNotEmpty() && nextWidth > constraints.maxWidth) {
                commitLine()
            }
            currentWidth = if (currentLine.isEmpty()) {
                placeable.width
            } else {
                currentWidth + itemSpacingPx + placeable.width
            }
            currentHeight = maxOf(currentHeight, placeable.height)
            currentLine += index
        }
        commitLine()

        val layoutHeight = lineHeights.sum() +
            lineSpacingPx * (lineHeights.size - 1).coerceAtLeast(0)

        layout(constraints.maxWidth, layoutHeight) {
            var y = 0
            lines.forEachIndexed { lineIndex, line ->
                val lineHeight = lineHeights[lineIndex]
                var x = 0
                line.forEachIndexed { itemIndex, placeableIndex ->
                    val placeable = keywordPlaceables[placeableIndex]
                    placeable.placeRelative(
                        x,
                        y + (lineHeight - placeable.height) / 2
                    )
                    x += placeable.width
                    if (itemIndex < line.lastIndex) {
                        x += itemSpacingPx
                    }
                }
                y += lineHeight + lineSpacingPx
            }
        }
    }
}

@Composable
internal fun SearchBlockedKeywordInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colorScheme.textPrimary),
        cursorBrush = SolidColor(colorScheme.primary),
        decorationBox = { innerTextField ->
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = shape,
                color = colorScheme.surface.copy(alpha = 0.38f),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.32f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VisibilityOff,
                        contentDescription = null,
                        tint = colorScheme.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = "屏蔽关键词，例如：同人",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.textTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                }
            }
        }
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SearchBlockedKeywordChip(
    keyword: String,
    onRemoveKeyword: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
        InputChip(
            selected = false,
            onClick = { onRemoveKeyword(keyword) },
            modifier = modifier,
            label = {
                Text(
                    text = keyword,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "删除 $keyword",
                    modifier = Modifier.size(16.dp)
                )
            }
        )
    }
}

