package com.asmr.player.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.automirrored.rounded.FormatAlignRight
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FormatAlignCenter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asmr.player.BuildConfig
import com.asmr.player.data.settings.CoverPreviewMode
import com.asmr.player.data.settings.FloatingLyricsSettings
import com.asmr.player.data.settings.LyricsPageSettings
import com.asmr.player.data.settings.NowPlayingLyricsSettings
import com.asmr.player.ui.common.cover.EaraLogoLoadingIndicator
import com.asmr.player.ui.theme.AsmrColorScheme
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.update.launchDownloadedApkInstall

private const val MONOCHROME_THEME_SENTINEL = 0x01000000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsAppearanceSection(
    themeMode: String,
    staticHueArgbLight: Int?,
    staticHueArgbDark: Int?,
    dynamicPlayerHueEnabled: Boolean,
    coverBackgroundEnabled: Boolean,
    coverBackgroundClarity: Float,
    coverPreviewMode: CoverPreviewMode,
    segmentedButtonColors: SegmentedButtonColors,
    activeTipKeyState: MutableState<String?>,
    onHorizontalControlInteractionChanged: (Boolean) -> Unit,
    viewModel: SettingsViewModel,
    colorScheme: AsmrColorScheme,
) {
    var activeTipKey by activeTipKeyState
                            Text("主题模式", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                ThemeModeChip(
                                    label = "系统",
                                    selected = themeMode == "system",
                                    onClick = { viewModel.setThemeMode("system") }
                                )
                                ThemeModeChip(
                                    label = "浅色",
                                    selected = themeMode == "light",
                                    onClick = { viewModel.setThemeMode("light") }
                                )
                                ThemeModeChip(
                                    label = "深色",
                                    selected = themeMode == "dark",
                                    onClick = { viewModel.setThemeMode("dark") }
                                )
                                ThemeModeChip(
                                    label = "柔和深色",
                                    selected = themeMode == "soft_dark",
                                    onClick = { viewModel.setThemeMode("soft_dark") }
                                )
                            }

                            Text("主题色", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val currentHueArgb = if (themeMode == "light") staticHueArgbLight else staticHueArgbDark
                                ThemeColorDot(
                                    color = null,
                                    selected = currentHueArgb == null,
                                    onClick = { viewModel.setStaticHueArgb(null) }
                                )
                                ThemeMonochromeDot(
                                    selected = currentHueArgb == MONOCHROME_THEME_SENTINEL,
                                    onClick = { viewModel.setStaticHueArgb(MONOCHROME_THEME_SENTINEL) }
                                )
                                // 浅色主题用深色调（深红、深蓝、墨綠等），深色/柔和深色主题用高饱和亮色
                                val presets = if (themeMode == "light") {
                                    listOf(
                                        Color(0xFF0B3D2E), // 墨綠
                                        Color(0xFF0D47A1), // 深蓝
                                        Color(0xFF880E4F), // 深玫红
                                        Color(0xFF4A148C), // 深紫
                                        Color(0xFF7B1A1A), // 深砖红
                                        Color(0xFF004D40)  // 深青綠
                                    )
                                } else {
                                    // dark / soft_dark：饱和度稍高的亮色，在暗背景上清晰醒目
                                    listOf(
                                        Color(0xFF29B6F6), // 亮天蓝
                                        Color(0xFF26C17A), // 亮翠綠
                                        Color(0xFF7C4DFF), // 亮紫罗兰
                                        Color(0xFFFF5252), // 亮珊瑚红
                                        Color(0xFFFFCA28), // 亮琥珀黄
                                        Color(0xFF26C7C7)  // 亮青色
                                    )
                                }
                                presets.forEach { c ->
                                    ThemeColorDot(
                                        color = c,
                                        selected = currentHueArgb == c.toArgb(),
                                        onClick = { viewModel.setStaticHueArgb(c.toArgb()) }
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
                        SettingsToggleRow(
                            text = "封面动态主题（全局）",
                            checked = dynamicPlayerHueEnabled,
                            onCheckedChange = viewModel::setDynamicPlayerHueEnabled
                        )

                        SettingsToggleRow(
                            text = "播放页/歌词页封面背景",
                            checked = coverBackgroundEnabled,
                            onCheckedChange = viewModel::setCoverBackgroundEnabled
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("背景封面预览方式", style = MaterialTheme.typography.bodyMedium)
                            PreviewModeInfoTip(
                                active = activeTipKey == "cover_preview_mode",
                                onToggle = {
                                    activeTipKey = if (activeTipKey == "cover_preview_mode") null else "cover_preview_mode"
                                }
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            SingleChoiceSegmentedButtonRow {
                                SegmentedButton(
                                    selected = coverPreviewMode == CoverPreviewMode.Disabled,
                                    onClick = { viewModel.setCoverPreviewMode(CoverPreviewMode.Disabled) },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                                    colors = segmentedButtonColors,
                                    icon = {},
                                    label = { Text("关闭") }
                                )
                                SegmentedButton(
                                    selected = coverPreviewMode == CoverPreviewMode.Drag,
                                    onClick = { viewModel.setCoverPreviewMode(CoverPreviewMode.Drag) },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                                    colors = segmentedButtonColors,
                                    icon = {},
                                    label = { Text("滑动") }
                                )
                                SegmentedButton(
                                    selected = coverPreviewMode == CoverPreviewMode.Motion,
                                    onClick = { viewModel.setCoverPreviewMode(CoverPreviewMode.Motion) },
                                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                                    colors = segmentedButtonColors,
                                    icon = {},
                                    label = { Text("转动") }
                                )
                            }
                        }
                        if (coverBackgroundEnabled) {
                            key("cover_background_clarity_slider") {
                                DeferredCommitSettingsSliderRow(
                                    committedValue = coverBackgroundClarity,
                                    range = 0f..1f,
                                    stepSize = 0.05f,
                                    textForValue = { value ->
                                        "封面背景清晰度：${(value.coerceIn(0f, 1f) * 100).toInt()}%"
                                    },
                                    onValueCommitted = viewModel::setCoverBackgroundClarity,
                                    onHorizontalControlInteractionChanged = onHorizontalControlInteractionChanged
                                )
                            }
                        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsPlaybackSection(
    showMiniPlayerBar: Boolean,
    sfwHideSystemControls: Boolean,
    pauseOnOutputDisconnect: Boolean,
    resumeOnOutputConnect: Boolean,
    playFadeInMs: Int,
    pauseFadeOutMs: Int,
    pauseOnOtherAudio: Boolean,
    activeTipKeyState: MutableState<String?>,
    onHorizontalControlInteractionChanged: (Boolean) -> Unit,
    viewModel: SettingsViewModel,
    colorScheme: AsmrColorScheme,
) {
    var activeTipKey by activeTipKeyState
                            SettingsToggleRow(
                                text = "迷你播放栏开关",
                                checked = showMiniPlayerBar,
                                onCheckedChange = viewModel::setShowMiniPlayerBar,
                                infoKey = "show_mini_player_bar",
                                infoTitle = "迷你播放栏",
                                infoText = "关闭后，应用底部的迷你播放栏会隐藏，同时页面底部不会再为它预留空白。",
                                activeTipKey = activeTipKey,
                                onToggleTip = { key -> activeTipKey = if (activeTipKey == key) null else key }
                            )
                            SettingsToggleRow(
                                text = "SFW开关",
                                checked = sfwHideSystemControls,
                                onCheckedChange = viewModel::setSfwHideSystemControls,
                                infoKey = "sfw_hide_system_controls",
                                infoTitle = "SFW",
                                infoText = "开启后会尽量隐藏系统锁屏和通知栏里的媒体控制按钮，但仍保留后台播放所需的前台通知。",
                                activeTipKey = activeTipKey,
                                onToggleTip = { key -> activeTipKey = if (activeTipKey == key) null else key }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    role = androidx.compose.ui.semantics.Role.Button,
                                    onClick = viewModel::openSystemAudioEffects
                                )
                                .heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "系统音效：部分系统会默认开启杜比全景声效果，可自行选择是否开启",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = colorScheme.textSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        SettingsToggleRow(
                            text = "断开扬声器、有线/蓝牙耳机或蓝牙关闭时立刻暂停播放",
                            checked = pauseOnOutputDisconnect,
                            onCheckedChange = viewModel::setPauseOnOutputDisconnect,
                            infoKey = "pause_on_output_disconnect",
                            infoTitle = "输出断开自动暂停",
                            infoText = "播放中如果外放、耳机或蓝牙输出被移除，会立刻暂停，避免声音突然外放。",
                            activeTipKey = activeTipKey,
                            onToggleTip = { key -> activeTipKey = if (activeTipKey == key) null else key }
                        )
                        SettingsToggleRow(
                            text = "连接有线/蓝牙耳机或其他外接输出时继续播放",
                            checked = resumeOnOutputConnect,
                            onCheckedChange = viewModel::setResumeOnOutputConnect,
                            infoKey = "resume_on_output_connect",
                            infoTitle = "输出接入自动恢复",
                            infoText = "检测到耳机、蓝牙耳机、USB 音频、HDMI 或 AUX 等外接输出接入时，如果播放器当前处于暂停，会自动尝试恢复播放；手机扬声器不触发。",
                            activeTipKey = activeTipKey,
                            onToggleTip = { key -> activeTipKey = if (activeTipKey == key) null else key }
                        )
                        DeferredCommitSettingsSliderRow(
                            committedValue = playFadeInMs.toFloat(),
                            range = 0f..3000f,
                            stepSize = 100f,
                            textForValue = { value -> "播放时逐渐增强音量: ${value.toInt()}ms" },
                            onValueCommitted = { viewModel.setPlayFadeInMs(it.toInt()) },
                            infoKey = "play_fade_in",
                            infoTitle = "播放淡入",
                            infoText = "点击播放时，音量会在设定时长内从低到高平滑升到正常值。",
                            activeTipKey = activeTipKey,
                            onToggleTip = { key -> activeTipKey = if (activeTipKey == key) null else key },
                            onHorizontalControlInteractionChanged = onHorizontalControlInteractionChanged
                        )
                        DeferredCommitSettingsSliderRow(
                            committedValue = pauseFadeOutMs.toFloat(),
                            range = 0f..3000f,
                            stepSize = 100f,
                            textForValue = { value -> "暂停时逐渐降低音量: ${value.toInt()}ms" },
                            onValueCommitted = { viewModel.setPauseFadeOutMs(it.toInt()) },
                            infoKey = "pause_fade_out",
                            infoTitle = "暂停淡出",
                            infoText = "点击暂停时，音量会在设定时长内逐渐降到 0，然后再真正暂停。",
                            activeTipKey = activeTipKey,
                            onToggleTip = { key -> activeTipKey = if (activeTipKey == key) null else key },
                            onHorizontalControlInteractionChanged = onHorizontalControlInteractionChanged
                        )
                        SettingsToggleRow(
                            text = "其他应用播放音/视频时暂停",
                            checked = pauseOnOtherAudio,
                            onCheckedChange = viewModel::setPauseOnOtherAudio,
                            infoKey = "pause_on_other_audio",
                            infoTitle = "音频焦点暂停",
                            infoText = "当其他音乐或视频应用抢占音频焦点时暂停播放；普通通知提示音不会触发。",
                            activeTipKey = activeTipKey,
                            onToggleTip = { key -> activeTipKey = if (activeTipKey == key) null else key }
                        )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsLyricsSection(
    floatingLyricsEnabled: Boolean,
    floatingSettings: FloatingLyricsSettings,
    nowPlayingLyricsSettings: NowPlayingLyricsSettings,
    lyricsPageSettings: LyricsPageSettings,
    overlayGranted: Boolean,
    overlayLauncher: ActivityResultLauncher<Intent>,
    segmentedButtonColors: SegmentedButtonColors,
    onHorizontalControlInteractionChanged: (Boolean) -> Unit,
    viewModel: SettingsViewModel,
    colorScheme: AsmrColorScheme,
    context: Context,
) {
                            SettingsToggleRow(
                                text = "开启悬浮歌词",
                                checked = floatingLyricsEnabled,
                                onCheckedChange = { viewModel.setFloatingLyricsEnabled(it) }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
                            NowPlayingLyricsSettingsSection(
                                settings = nowPlayingLyricsSettings,
                                onSettingsChange = viewModel::updateNowPlayingLyricsSettings,
                                onHorizontalControlInteractionChanged = onHorizontalControlInteractionChanged
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))

                            LyricsPageSettingsSection(
                                settings = lyricsPageSettings,
                                segmentedButtonColors = segmentedButtonColors,
                                onSettingsChange = { next -> viewModel.updateLyricsPageSettings(next) },
                                onHorizontalControlInteractionChanged = onHorizontalControlInteractionChanged
                            )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                        Text("悬浮歌词细节", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

                        if (!overlayGranted && floatingLyricsEnabled) {
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    overlayLauncher.launch(intent)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors()
                            ) {
                                Text("授权悬浮窗权限")
                            }
                        }

                        if (floatingLyricsEnabled && overlayGranted) {
                            SettingsSliderRow(
                                text = "字体大小: ${floatingSettings.size.toInt()}",
                                value = floatingSettings.size,
                                range = 12f..32f,
                                onValueChange = { viewModel.updateFloatingLyricsSettings(floatingSettings.copy(size = it)) },
                                onHorizontalControlInteractionChanged = onHorizontalControlInteractionChanged
                            )

                            SettingsSliderRow(
                                text = "垂直位置 (Y轴)",
                                value = floatingSettings.yOffset.toFloat(),
                                range = 0f..2000f,
                                onValueChange = { viewModel.updateFloatingLyricsSettings(floatingSettings.copy(yOffset = it.toInt())) },
                                onHorizontalControlInteractionChanged = onHorizontalControlInteractionChanged
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("对齐方式", style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.weight(1f))
                                SingleChoiceSegmentedButtonRow {
                                    SegmentedButton(
                                        selected = floatingSettings.align == 0,
                                        onClick = { viewModel.updateFloatingLyricsSettings(floatingSettings.copy(align = 0)) },
                                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                                        colors = segmentedButtonColors,
                                        icon = {},
                                        label = { Icon(Icons.AutoMirrored.Rounded.FormatAlignLeft, null) }
                                    )
                                    SegmentedButton(
                                        selected = floatingSettings.align == 1,
                                        onClick = { viewModel.updateFloatingLyricsSettings(floatingSettings.copy(align = 1)) },
                                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                                        colors = segmentedButtonColors,
                                        icon = {},
                                        label = { Icon(Icons.Rounded.FormatAlignCenter, null) }
                                    )
                                    SegmentedButton(
                                        selected = floatingSettings.align == 2,
                                        onClick = { viewModel.updateFloatingLyricsSettings(floatingSettings.copy(align = 2)) },
                                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                                        colors = segmentedButtonColors,
                                        icon = {},
                                        label = { Icon(Icons.AutoMirrored.Rounded.FormatAlignRight, null) }
                                    )
                                }
                            }

                            val presetColors = remember {
                                listOf(
                                    0xFFFFFFFF.toInt(),
                                    0xFFFFE14D.toInt(),
                                    0xFF39D5FF.toInt(),
                                    0xFF5CFF95.toInt(),
                                    0xFFFF5FA2.toInt()
                                )
                            }
                            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("歌词颜色", style = MaterialTheme.typography.bodyMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    presetColors.forEach { c ->
                                        val selected = floatingSettings.color == c
                                        Box(
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(CircleShape)
                                                .background(Color(c))
                                                .border(
                                                    width = if (selected) 2.dp else 1.dp,
                                                    color = if (selected) colorScheme.primary else colorScheme.onSurface.copy(alpha = 0.25f),
                                                    shape = CircleShape
                                                )
                                                .clickable { viewModel.updateFloatingLyricsSettings(floatingSettings.copy(color = c)) }
                                        )
                                    }
                                }
                            }

                            SettingsToggleRow(
                                text = "点击穿透(锁定位置)",
                                checked = !floatingSettings.touchable,
                                onCheckedChange = { viewModel.updateFloatingLyricsSettings(floatingSettings.copy(touchable = !it)) }
                            )
                        }
}

@Composable
internal fun SettingsAboutSection(
    updateState: AppUpdateState,
    autoUpdateCheckEnabled: Boolean,
    viewModel: SettingsViewModel,
    colorScheme: AsmrColorScheme,
    context: Context,
) {
                        val isDark = AsmrTheme.colorScheme.isDark
                        val buttonColors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = colorScheme.primarySoft,
                            contentColor = if (isDark) colorScheme.onPrimaryContainer else colorScheme.primaryStrong
                        )

                        Text(
                            text = "当前版本：${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        SettingsToggleRow(
                            text = "启动时自动检查更新",
                            checked = autoUpdateCheckEnabled,
                            onCheckedChange = viewModel::setAutoUpdateCheckEnabled
                        )

                        val busy = updateState is AppUpdateState.Checking || updateState is AppUpdateState.Downloading
                        FilledTonalButton(
                            onClick = { viewModel.checkUpdate() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = buttonColors,
                            enabled = !busy
                        ) {
                            if (updateState is AppUpdateState.Checking) {
                                EaraLogoLoadingIndicator(size = 18.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("检查中…")
                            } else {
                                Text("检查更新")
                            }
                        }

                        when (val s = updateState) {
                            is AppUpdateState.UpToDate -> {
                                Text(
                                    text = "已是最新：${s.latestVersionName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.textSecondary
                                )
                            }
                            is AppUpdateState.UpdateAvailable -> {
                                Text(
                                    text = "发现新版本：${s.release.tagName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.textSecondary
                                )
                                FilledTonalButton(
                                    onClick = { viewModel.downloadLatestApk() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = buttonColors,
                                    enabled = !busy
                                ) {
                                    Text("下载并安装")
                                }
                            }
                            is AppUpdateState.Downloading -> {
                                val total = s.totalBytes
                                val downloaded = s.downloadedBytes
                                val progress = if (total > 0L) (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) else null
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "正在下载：${s.release.apkName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (progress != null) {
                                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                                    } else {
                                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    }
                                }
                            }
                            is AppUpdateState.ReadyToInstall -> {
                                Text(
                                    text = "下载完成：${s.release.tagName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.textSecondary
                                )
                                FilledTonalButton(
                                    onClick = {
                                        launchDownloadedApkInstall(context, s.apkPath)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = buttonColors
                                ) {
                                    Text("安装更新")
                                }
                            }
                            is AppUpdateState.Failed -> {
                                Text(
                                    text = s.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Box(Modifier.fillMaxWidth()) {
                                    TextButton(
                                        onClick = { viewModel.resetUpdateState() },
                                        modifier = Modifier.align(Alignment.CenterEnd)
                                    ) {
                                        Text("关闭")
                                    }
                                }
                            }
                            else -> {}
                        }
}
