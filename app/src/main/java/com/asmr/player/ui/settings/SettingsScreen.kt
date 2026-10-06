package com.asmr.player.ui.settings

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import com.asmr.player.ui.common.core.isCompactWidth
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.asmr.player.data.download.DownloadDestination
import com.asmr.player.subtitle.SubtitleDeviceCapability
import com.asmr.player.subtitle.SubtitleModelDownloadSource
import com.asmr.player.subtitle.SubtitleTranscriptionModels
import com.asmr.player.ui.common.core.SearchBlockedKeywordsViewModel
import com.asmr.player.ui.library.LibraryViewModel
import com.asmr.player.ui.common.status.AppSupportStatusSection
import com.asmr.player.ui.common.dialog.FlatActionDialog
import com.asmr.player.ui.common.dialog.FlatDialogAction
import com.asmr.player.ui.common.dialog.FlatDialogActionTone
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.common.list.LocalBottomOverlayPadding
import com.asmr.player.ui.common.list.rememberCalmScrollableFlingBehavior
import com.asmr.player.ui.common.list.smoothScrollToTop
import com.asmr.player.ui.common.list.withAddedBottomPadding
import com.asmr.player.ui.common.list.collectAsStateWhileActive

private val SettingsPageHorizontalPadding = 8.dp
private const val SettingsDetailEnterDurationMs = 440
private const val SettingsDetailExitDurationMs = 420
private val SettingsDetailSlideEasing = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)

internal enum class SettingsSection(
    val title: String,
    val description: String,
    val icon: ImageVector,
) {
    LocalLibrary("本地库", "管理扫描目录、刷新本地内容与同步元数据", Icons.Rounded.Folder),
    BlockedKeywords("屏蔽词", "过滤搜索结果中不想看到的关键词", Icons.Rounded.Block),
    Appearance("外观", "调整主题、主题色与播放页背景", Icons.Rounded.Palette),
    Playback("播放设置", "管理迷你播放栏、音频输出与淡入淡出", Icons.Rounded.Headphones),
    Lyrics("歌词", "配置歌词页与悬浮歌词的显示效果", Icons.Rounded.Lyrics),
    Translation("翻译配置", "管理页面翻译、字幕模型与 DeepSeek 翻译", Icons.Rounded.Translate),
    SupportStatus("服务状态与代理", "测试服务连通性并配置代理与 DNS", Icons.Rounded.Router),
    AppCache("APP 缓存", "设置缓存容量上限并清理缓存", Icons.Rounded.Storage),
    About("关于", "查看版本信息并检查应用更新", Icons.Rounded.Info),
}

private fun settingsDetailEnterTransition() = slideInHorizontally(
    animationSpec = tween(
        durationMillis = SettingsDetailEnterDurationMs,
        easing = SettingsDetailSlideEasing,
    ),
    initialOffsetX = { fullWidth -> fullWidth },
)

private fun settingsDetailExitTransition() = slideOutHorizontally(
    animationSpec = tween(
        durationMillis = SettingsDetailExitDurationMs,
        easing = SettingsDetailSlideEasing,
    ),
    targetOffsetX = { fullWidth -> fullWidth },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    windowSizeClass: WindowSizeClass,
    isActive: Boolean = true,
    isDataActive: Boolean = isActive,
    viewModel: SettingsViewModel = hiltViewModel(),
    blockedKeywordsViewModel: SearchBlockedKeywordsViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    scrollToTopSignal: Long = 0L,
    onHorizontalControlInteractionChanged: (Boolean) -> Unit = {},
    onDetailPageChanged: (Boolean) -> Unit = {},
) {
    var selectedSection by rememberSaveable { mutableStateOf<SettingsSection?>(null) }
    var retainedSection by remember { mutableStateOf(selectedSection) }
    val currentOnDetailPageChanged by rememberUpdatedState(onDetailPageChanged)
    val localLibraryDataActive = isDataActive && selectedSection == SettingsSection.LocalLibrary
    val blockedKeywordsDataActive = isDataActive && selectedSection == SettingsSection.BlockedKeywords
    val appearanceDataActive = isDataActive && selectedSection == SettingsSection.Appearance
    val playbackDataActive = isDataActive && selectedSection == SettingsSection.Playback
    val lyricsDataActive = isDataActive && selectedSection == SettingsSection.Lyrics
    val translationDataActive = isDataActive && selectedSection == SettingsSection.Translation
    val supportStatusDataActive = isDataActive && selectedSection == SettingsSection.SupportStatus
    val aboutDataActive = isDataActive && selectedSection == SettingsSection.About
    val appCacheDataActive = isDataActive && selectedSection == SettingsSection.AppCache

    LaunchedEffect(selectedSection) {
        currentOnDetailPageChanged(selectedSection != null)
    }
    DisposableEffect(Unit) {
        onDispose { currentOnDetailPageChanged(false) }
    }
    LaunchedEffect(translationDataActive, viewModel) {
        if (translationDataActive) viewModel.prepareSettingsData()
    }
    val floatingLyricsEnabled by viewModel.floatingLyricsEnabled.collectAsStateWhileActive(lyricsDataActive)
    val floatingSettings by viewModel.floatingLyricsSettings.collectAsStateWhileActive(lyricsDataActive)
    val nowPlayingLyricsSettings by viewModel.nowPlayingLyricsSettings.collectAsStateWhileActive(lyricsDataActive)
    val lyricsPageSettings by viewModel.lyricsPageSettings.collectAsStateWhileActive(lyricsDataActive)
    val dynamicPlayerHueEnabled by viewModel.dynamicPlayerHueEnabled.collectAsStateWhileActive(appearanceDataActive)
    val themeMode by viewModel.themeMode.collectAsStateWhileActive(appearanceDataActive)
    val staticHueArgbLight by viewModel.staticHueArgbLight.collectAsStateWhileActive(appearanceDataActive)
    val staticHueArgbDark by viewModel.staticHueArgbDark.collectAsStateWhileActive(appearanceDataActive)
    val coverBackgroundEnabled by viewModel.coverBackgroundEnabled.collectAsStateWhileActive(appearanceDataActive)
    val coverBackgroundClarity by viewModel.coverBackgroundClarity.collectAsStateWhileActive(appearanceDataActive)
    val coverPreviewMode by viewModel.coverPreviewMode.collectAsStateWhileActive(appearanceDataActive)
    val pauseOnOutputDisconnect by viewModel.pauseOnOutputDisconnect.collectAsStateWhileActive(playbackDataActive)
    val resumeOnOutputConnect by viewModel.resumeOnOutputConnect.collectAsStateWhileActive(playbackDataActive)
    val pauseOnOtherAudio by viewModel.pauseOnOtherAudio.collectAsStateWhileActive(playbackDataActive)
    val playFadeInMs by viewModel.playFadeInMs.collectAsStateWhileActive(playbackDataActive)
    val pauseFadeOutMs by viewModel.pauseFadeOutMs.collectAsStateWhileActive(playbackDataActive)
    val sfwHideSystemControls by viewModel.sfwHideSystemControls.collectAsStateWhileActive(playbackDataActive)
    val showMiniPlayerBar by viewModel.showMiniPlayerBar.collectAsStateWhileActive(playbackDataActive)
    val searchBlockedKeywords by blockedKeywordsViewModel.searchBlockedKeywords.collectAsStateWhileActive(blockedKeywordsDataActive)
    val networkRouteSettings by viewModel.networkRouteSettings.collectAsStateWhileActive(supportStatusDataActive)
    val appCacheState by viewModel.appCacheState.collectAsStateWhileActive(appCacheDataActive)
    val subtitleModelState by viewModel.subtitleModelState.collectAsStateWhileActive(translationDataActive)
    val deepSeekApiKeyState by viewModel.deepSeekApiKeyState.collectAsStateWhileActive(translationDataActive)
    val deepSeekAccountState by viewModel.deepSeekAccountState.collectAsStateWhileActive(translationDataActive)
    val deepSeekTranslationSettings by viewModel.deepSeekTranslationSettings.collectAsStateWhileActive(translationDataActive)
    val updateState by viewModel.updateState.collectAsStateWhileActive(aboutDataActive)
    val autoUpdateCheckEnabled by viewModel.autoUpdateCheckEnabled.collectAsStateWhileActive(aboutDataActive)
    val scanRoots by libraryViewModel.scanRoots.collectAsStateWhileActive(localLibraryDataActive)
    val downloadDestination by viewModel.downloadDestination.collectAsStateWhileActive(localLibraryDataActive)
    val bulkProgress by libraryViewModel.bulkProgress.collectAsStateWhileActive(localLibraryDataActive)
    val isGlobalSyncRunning by libraryViewModel.isGlobalSyncRunning.collectAsStateWhileActive(localLibraryDataActive)
    val context = LocalContext.current
    val colorScheme = AsmrTheme.colorScheme
    val rootListState = rememberLazyListState()
    val detailListState = rememberLazyListState()
    val listState = if (selectedSection == null) rootListState else detailListState
    val segmentedButtonColors = SegmentedButtonDefaults.colors(
        activeContainerColor = colorScheme.primarySoft,
        activeContentColor = if (colorScheme.isDark) colorScheme.onPrimaryContainer else colorScheme.primaryStrong,
        activeBorderColor = colorScheme.primaryStrong,
        inactiveContainerColor = Color.Transparent,
        inactiveContentColor = colorScheme.onSurfaceVariant,
        inactiveBorderColor = colorScheme.primaryStrong.copy(alpha = 0.4f),
        disabledActiveContainerColor = colorScheme.primarySoft.copy(alpha = 0.48f),
        disabledActiveContentColor = if (colorScheme.isDark) {
            colorScheme.onPrimaryContainer.copy(alpha = 0.48f)
        } else {
            colorScheme.primaryStrong.copy(alpha = 0.48f)
        },
        disabledActiveBorderColor = colorScheme.primaryStrong.copy(alpha = 0.24f),
        disabledInactiveContainerColor = Color.Transparent,
        disabledInactiveContentColor = colorScheme.primaryStrong.copy(alpha = 0.38f),
        disabledInactiveBorderColor = colorScheme.primaryStrong.copy(alpha = 0.2f)
    )
    
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    val activeTipKeyState = remember { mutableStateOf<String?>(null) }
    var activeTipKey by activeTipKeyState
    var searchBlockedKeywordInput by rememberSaveable { mutableStateOf("") }
    var showClearAppCacheConfirmation by remember { mutableStateOf(false) }
    var pendingDeleteSubtitleModelId by remember { mutableStateOf<String?>(null) }
    val subtitleModelSourceIds = remember {
        mutableStateMapOf<String, String>().apply {
            SubtitleTranscriptionModels.all.forEach { model ->
                this[model.id] = SubtitleModelDownloadSource.HuggingFace.id
            }
        }
    }
    var deepSeekApiKeyInput by remember { mutableStateOf("") }
    LaunchedEffect(deepSeekApiKeyState.saveVersion) {
        if (deepSeekApiKeyState.saveVersion > 0L) deepSeekApiKeyInput = ""
    }
    DisposableEffect(onHorizontalControlInteractionChanged) {
        onDispose { onHorizontalControlInteractionChanged(false) }
    }
    val overlayLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        overlayGranted = Settings.canDrawOverlays(context)
    }
    val pickRootLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri != null) {
                val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                val ok = runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }.isSuccess
                if (ok) {
                    val uriString = uri.toString()
                    val added = libraryViewModel.addScanRoot(uriString)
                    if (added) {
                        libraryViewModel.scanSingleRoot(uriString)
                    }
                }
            }
        }
    )
    val pendingDownloadDestinationState = remember { mutableStateOf<DownloadDestination?>(null) }
    var pendingDownloadDestination by pendingDownloadDestinationState
    val pickDownloadRootLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri != null) {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                val granted = runCatching {
                    context.contentResolver.takePersistableUriPermission(uri, flags)
                    true
                }.getOrDefault(false)
                if (granted) {
                    pendingDownloadDestination = DownloadDestination.DocumentTree(
                        root = uri.toString(),
                        label = formatTreeRootLabel(uri.toString()),
                    )
                } else {
                    libraryViewModel.messageManager.showError("无法获取下载目录读写权限")
                }
            }
        },
    )
    val pendingRemoveRootState = remember { mutableStateOf<String?>(null) }
    var pendingRemoveRoot by pendingRemoveRootState

    // 屏幕尺寸判断
    val isCompact = windowSizeClass.widthSizeClass.isCompactWidth
    BackHandler(enabled = isActive && selectedSection != null) {
        selectedSection = null
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent,
        contentColor = colorScheme.onBackground
    ) { padding ->
        LaunchedEffect(isActive) {
            if (isActive) return@LaunchedEffect
            deepSeekApiKeyInput = ""
            rootListState.stopScroll(MutatePriority.PreventUserInput)
            detailListState.stopScroll(MutatePriority.PreventUserInput)
        }
        LaunchedEffect(appCacheDataActive) {
            if (appCacheDataActive) viewModel.refreshAppCacheSize()
        }
        LaunchedEffect(selectedSection) {
            if (selectedSection != null) detailListState.scrollToItem(0)
        }
        LaunchedEffect(scrollToTopSignal) {
            if (scrollToTopSignal == 0L) return@LaunchedEffect
            listState.smoothScrollToTop()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            val contentModifier = if (isCompact) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxHeight()
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
            }
            LazyColumn(
                state = rootListState,
                modifier = contentModifier,
                flingBehavior = rememberCalmScrollableFlingBehavior(),
                contentPadding = PaddingValues(horizontal = SettingsPageHorizontalPadding, vertical = 10.dp)
                    .withAddedBottomPadding(LocalBottomOverlayPadding.current),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "settings_sections") {
                    SettingsSectionsPanel(
                        onSectionClick = { section ->
                            retainedSection = section
                            selectedSection = section
                        },
                    )
                }
                item(key = "root_bottom_spacer") {
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }

            AnimatedVisibility(
                visible = selectedSection != null,
                modifier = contentModifier,
                enter = settingsDetailEnterTransition(),
                exit = settingsDetailExitTransition(),
                label = "settingsDetailTransition",
            ) {
                val currentSection = retainedSection ?: return@AnimatedVisibility
                LazyColumn(
                    state = detailListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colorScheme.background),
                    flingBehavior = rememberCalmScrollableFlingBehavior(),
                    contentPadding = PaddingValues(horizontal = SettingsPageHorizontalPadding, vertical = 10.dp)
                        .withAddedBottomPadding(LocalBottomOverlayPadding.current),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item(key = "detail_header:${currentSection.name}") {
                        SettingsDetailHeader(
                            section = currentSection,
                            onBack = { selectedSection = null },
                        )
                    }

                if (currentSection == SettingsSection.LocalLibrary) {
                    item(key = "group:local") {
                        SettingsDetailCard {
                            SettingsLocalLibrarySection(
                                scanRoots = scanRoots,
                                downloadDestination = downloadDestination,
                                bulkProgress = bulkProgress,
                                isGlobalSyncRunning = isGlobalSyncRunning,
                                viewModel = viewModel,
                                libraryViewModel = libraryViewModel,
                                pickRootLauncher = pickRootLauncher,
                                pickDownloadRootLauncher = pickDownloadRootLauncher,
                                pendingDownloadDestinationState = pendingDownloadDestinationState,
                                pendingRemoveRootState = pendingRemoveRootState,
                                colorScheme = colorScheme,
                                context = context,
                            )
                        }
                    }
                }

                if (currentSection == SettingsSection.BlockedKeywords) {
                    item(key = "group:block_words") {
                        SettingsDetailCard {
                        SearchBlockedKeywordsSection(
                            input = searchBlockedKeywordInput,
                            keywords = searchBlockedKeywords,
                            onInputChange = { searchBlockedKeywordInput = it },
                            onAddKeyword = {
                                val keyword = searchBlockedKeywordInput.trim()
                                if (keyword.isNotBlank()) {
                                    blockedKeywordsViewModel.addSearchBlockedKeyword(keyword)
                                    searchBlockedKeywordInput = ""
                                }
                            },
                            onRemoveKeyword = blockedKeywordsViewModel::removeSearchBlockedKeyword
                        )
                    }
                }
                }

                if (currentSection == SettingsSection.Appearance) {
                    item(key = "group:appearance") {
                        SettingsDetailCard {
                            SettingsAppearanceSection(
                                themeMode = themeMode,
                                staticHueArgbLight = staticHueArgbLight,
                                staticHueArgbDark = staticHueArgbDark,
                                dynamicPlayerHueEnabled = dynamicPlayerHueEnabled,
                                coverBackgroundEnabled = coverBackgroundEnabled,
                                coverBackgroundClarity = coverBackgroundClarity,
                                coverPreviewMode = coverPreviewMode,
                                segmentedButtonColors = segmentedButtonColors,
                                activeTipKeyState = activeTipKeyState,
                                onHorizontalControlInteractionChanged = onHorizontalControlInteractionChanged,
                                viewModel = viewModel,
                                colorScheme = colorScheme,
                            )
                        }
                    }
                }

                if (currentSection == SettingsSection.Playback) {
                    item(key = "group:playback") {
                        SettingsDetailCard {
                            SettingsPlaybackSection(
                                showMiniPlayerBar = showMiniPlayerBar,
                                sfwHideSystemControls = sfwHideSystemControls,
                                pauseOnOutputDisconnect = pauseOnOutputDisconnect,
                                resumeOnOutputConnect = resumeOnOutputConnect,
                                playFadeInMs = playFadeInMs,
                                pauseFadeOutMs = pauseFadeOutMs,
                                pauseOnOtherAudio = pauseOnOtherAudio,
                                activeTipKeyState = activeTipKeyState,
                                onHorizontalControlInteractionChanged = onHorizontalControlInteractionChanged,
                                viewModel = viewModel,
                                colorScheme = colorScheme,
                            )
                        }
                    }
                }

                // 歌词
                if (currentSection == SettingsSection.Lyrics) {
                    item(key = "group:lyrics") {
                        SettingsDetailCard {
                            SettingsLyricsSection(
                                floatingLyricsEnabled = floatingLyricsEnabled,
                                floatingSettings = floatingSettings,
                                nowPlayingLyricsSettings = nowPlayingLyricsSettings,
                                lyricsPageSettings = lyricsPageSettings,
                                overlayGranted = overlayGranted,
                                overlayLauncher = overlayLauncher,
                                segmentedButtonColors = segmentedButtonColors,
                                onHorizontalControlInteractionChanged = onHorizontalControlInteractionChanged,
                                viewModel = viewModel,
                                colorScheme = colorScheme,
                                context = context,
                            )
                        }
                    }
                }
                if (currentSection == SettingsSection.Translation) {
                    item(key = "group:page_translation") {
                        SettingsDetailCard { PageTranslationSettingsSection(isActive = translationDataActive) }
                    }
                    item(key = "group:translation_config") {
                        SettingsDetailCard {
                        Text(
                            text = "字幕翻译",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.textPrimary,
                        )
                        SubtitleModelSettingsSection(
                            state = subtitleModelState,
                            selectedSourceIds = subtitleModelSourceIds,
                            deviceSupported = remember(context) {
                                SubtitleDeviceCapability.evaluate(context).supported
                            },
                            segmentedButtonColors = segmentedButtonColors,
                            onSourceSelected = { modelId, source ->
                                subtitleModelSourceIds[modelId] = source.id
                            },
                            onDownload = viewModel::downloadSubtitleModel,
                            onCancelDownload = viewModel::cancelSubtitleModelDownload,
                            onSelect = viewModel::selectSubtitleModel,
                            onDelete = { modelId -> pendingDeleteSubtitleModelId = modelId },
                            onClearFailure = viewModel::clearSubtitleModelFailure
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
                        DeepSeekTranslationSettingsSection(
                            state = deepSeekApiKeyState,
                            accountState = deepSeekAccountState,
                            settings = deepSeekTranslationSettings,
                            apiKeyInput = deepSeekApiKeyInput,
                            compact = isCompact,
                            segmentedButtonColors = segmentedButtonColors,
                            onApiKeyInputChanged = { deepSeekApiKeyInput = it },
                            onSave = { viewModel.saveDeepSeekApiKey(deepSeekApiKeyInput) },
                            onThinkingEnabledChanged = viewModel::setDeepSeekThinkingEnabled,
                            onReasoningEffortChanged = viewModel::setDeepSeekReasoningEffort,
                            onFinalPolishEnabledChanged = viewModel::setDeepSeekFinalPolishEnabled,
                            activeTipKey = activeTipKey,
                            onToggleTip = { key -> activeTipKey = if (activeTipKey == key) null else key }
                        )
                    }
                }
                }
                if (currentSection == SettingsSection.About) {
                    item(key = "group:about_update") {
                        SettingsDetailCard {
                            SettingsAboutSection(
                                updateState = updateState,
                                autoUpdateCheckEnabled = autoUpdateCheckEnabled,
                                viewModel = viewModel,
                                colorScheme = colorScheme,
                                context = context,
                            )
                        }
                    }
                }

                if (currentSection == SettingsSection.SupportStatus) {
                    item(key = "group:support_status") {
                        SettingsDetailCard {
                        AppSupportStatusSection(
                            networkSettings = networkRouteSettings,
                            onUseSystemProxy = viewModel::useSystemProxy,
                            onAdvancedProxyApplied = viewModel::setAdvancedProxy,
                            onUseSystemDns = viewModel::useSystemDns,
                            onCustomDnsServerApplied = viewModel::setCustomDnsServer,
                        )
                    }
                }
                }

                if (currentSection == SettingsSection.AppCache) {
                    item(key = "group:app_cache") {
                        SettingsDetailCard {
                        AppCacheSettingsSection(
                            state = appCacheState,
                            onMaxSizeChanged = viewModel::setAppCacheMaxSizeMb,
                            onClearClick = { showClearAppCacheConfirmation = true },
                            onHorizontalControlInteractionChanged = onHorizontalControlInteractionChanged,
                        )
                    }
                }
                }

                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
            }
        }
    }

    val removeRoot = pendingRemoveRoot
    if (removeRoot != null) {
        FlatActionDialog(
            onDismissRequest = { pendingRemoveRoot = null },
            message = "将从列表中移除该目录，后续不会再扫描它。",
            actions = listOf(
                FlatDialogAction("取消", onClick = { pendingRemoveRoot = null }),
                FlatDialogAction(
                    text = "移除",
                    tone = FlatDialogActionTone.Danger,
                    onClick = {
                        val uri = runCatching { Uri.parse(removeRoot) }.getOrNull()
                        if (uri != null) {
                            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            runCatching { context.contentResolver.releasePersistableUriPermission(uri, flags) }
                        }
                        libraryViewModel.removeScanRootAndDeleteAlbums(removeRoot)
                        pendingRemoveRoot = null
                    }
                )
            )
        )
    }
    val nextDownloadDestination = pendingDownloadDestination
    if (nextDownloadDestination != null) {
        FlatActionDialog(
            onDismissRequest = { pendingDownloadDestination = null },
            message = "切换后将清理旧目录中由 App 下载的本地库、下载任务和字幕翻译任务记录，但不会删除或移动物理文件；同一目录内手动导入的其他作品会保留。切换成功后会自动扫描新的目标目录。是否继续？",
            actions = listOf(
                FlatDialogAction("取消", onClick = { pendingDownloadDestination = null }),
                FlatDialogAction(
                    text = if (nextDownloadDestination is DownloadDestination.Default) "重置" else "切换",
                    tone = FlatDialogActionTone.Danger,
                    onClick = {
                        pendingDownloadDestination = null
                        viewModel.changeDownloadDirectory(nextDownloadDestination) {
                            libraryViewModel.scanCurrentDownloadDestinationAsImport()
                        }
                    },
                ),
            ),
        )
    }
    if (showClearAppCacheConfirmation) {
        FlatActionDialog(
            onDismissRequest = { showClearAppCacheConfirmation = false },
            message = "将清理网络图片、在线音频播放和在线预览产生的缓存，不会删除下载内容、本地媒体或收藏数据。",
            actions = listOf(
                FlatDialogAction("取消", onClick = { showClearAppCacheConfirmation = false }),
                FlatDialogAction(
                    text = "清理",
                    tone = FlatDialogActionTone.Danger,
                    onClick = {
                        showClearAppCacheConfirmation = false
                        viewModel.clearAppCache()
                    }
                )
            )
        )
    }
    pendingDeleteSubtitleModelId?.let { modelId ->
        val modelName = SubtitleTranscriptionModels.fromId(modelId)?.optionName ?: "字幕"
        FlatActionDialog(
            onDismissRequest = { pendingDeleteSubtitleModelId = null },
            message = "确定删除“$modelName”模型？约 29 MiB 的公共运行时会保留，之后下载任一模型时无需重复安装。",
            actions = listOf(
                FlatDialogAction("取消", onClick = { pendingDeleteSubtitleModelId = null }),
                FlatDialogAction(
                    text = "删除模型",
                    tone = FlatDialogActionTone.Danger,
                    onClick = {
                        pendingDeleteSubtitleModelId = null
                        viewModel.deleteSubtitleModel(modelId)
                    }
                )
            )
        )
    }
}
