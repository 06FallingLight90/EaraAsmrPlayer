# ARCHITECTURE — EaraAsmrPlayer 架构说明

> 范围：`refactor/architecture-cleanup` 分支（v1.2.3 之后，含目录面板 V1–V4 死代码清理与去版本号重命名，commit `e002a43` / `b7603e4`）。
> 文中包名、类名、行数均于 2026-09-30 直接从代码核实；行数为约数（按换行切分统计，含文件末尾空行）。

## 1. 技术栈与模块

- Kotlin 1.9.22 + Jetpack Compose（BOM 2024.02）+ Material 3
- 播放：Media3 1.8.0（ExoPlayer + MediaSession）
- 依赖注入：Hilt 2.49；持久化：Room 2.6.1 + DataStore Preferences
- 网络：Retrofit 2.9 + OkHttp 4.12；抓取：Jsoup；任务：WorkManager 2.9；分页：Paging 3
- 单 module `:app`（另有 `:baselineprofile` 用于 Baseline Profile 采集）

## 2. 包分层图

源码根目录 `app/src/main/java/com/asmr/player`，顶层 16 包。主要依赖方向（箭头 = import 方向）：

```
              ┌───────────── main ──────────────┐
              │ MainContainer（导航宿主，约3345行）│
              └───────────────┬─────────────────┘
                              ▼
┌───────────────────────────── ui ─────────────────────────────┐
│ library（含 albumdetail/）· player · search · downloads      │
│ settings · playlists · groups · common · nav · sidepanel …   │
└──────────┬───────────────────────────────────┬───────────────┘
           │                                   │
           ▼                                   ▼
┌────── playback ──────┐              ┌─────── data ────────┐
│ PlayerConnection     │              │ local/（db、datastore）│
│ 32 个文件：音效链、  │              │ remote/（api、crawler、│
│ 频谱、切片循环、缓存 │              │ scraper、dlsite、auth、 │
└──────────┬───────────┘              │ download、repository、  │
           │                          │ settings、lyrics）      │
           ▼                          └──────────▲──────────────┘
┌────── service ───────┐                         │
│ PlaybackService      │─────────────────────────┘
│ : MediaSessionService│  （PlaybackService 亦回读 data 持久化进度/统计）
└──────────────────────┘

domain：Album / Track / Slice / SearchSource 纯模型（仅 Track 依赖 util）
di：CacheModule · DatabaseModule · networkmodule（全部 Hilt 绑定集中于此）
feature 服务包：subtitle · translation · cache · work · hotlistening · listentogether · benchmark · performance
```

分层规则与已知例外：

- 预期方向：`ui → (playback, data, domain)`；`playback → (data, domain)`；`service → (playback, data)`；`data → (domain, util)`。
- 已知穿透（P0-3，重构目标）：`ui` 下 16 个文件直接 import `data.local.db.dao.*`（如 DownloadsViewModel 直连 AlbumDao / DownloadDao / TrackDao；AlbumDetailViewModel 注入整个 AppDatabase）。
- 已知反向耦合（`data → 上层`，共 4 文件 5 处）：`data/settings/SettingsRepository → playback.AppVolume`；`data/lyrics/LyricsTargetContext → playback.MediaItemRequest`；`data/repository/PlaylistMediaItemMapper → playback.MediaItemFactory`；`data/lyrics/LyricsLoader → ui.library.LocalTreeLeafCacheEntry / TreeFileType`。
- `domain` 基本纯净：仅 `Track.kt` 依赖 `util.RemoteSubtitleSource`。

## 3. AlbumDetail 家族职责表

详情页是全库最大的文件家族：10 个文件、合计约 15 900 行。除前两个位于 `ui/library/` 外，其余在 `ui/library/albumdetail/`。

| 文件 | 约行数 | 职责 |
|---|---|---|
| `AlbumDetailScreen.kt` | 4039 | 页面入口 Composable `AlbumDetailScreen`；横屏布局数学（`albumLandscape*` 系列）、在线加载计划（`albumDetailOnlineLoadPlan`）、目录树状态 key、头部揭晓动画 |
| `AlbumDetailViewModel.kt` | 3198 | `@HiltViewModel`：详情页状态编排——asmr.one / DLsite / 本地三路数据加载合并、播放与下载意图、相似作品推荐 |
| `albumdetail/AlbumDetailDirectorySupport.kt` | 3388 | 目录浏览面板：`DirectoryBrowserPanel`、`CompactDirectoryBreadcrumbContent`、`DirectoryFolderRow`、`DirectoryBatchBarEmbedded`（死代码清理后已去 V3/V4/V5 版本号后缀） |
| `albumdetail/AlbumDetailDlsiteTabs.kt` | 1935 | DLsite 页签 `AlbumDlsiteInfoBreadcrumbTabV2`：画廊预览、试听列表、目录树加载占位与空态插画 |
| `albumdetail/AlbumDetailDialogs.kt` | 1100 | `AsmrOneDownloadDialog`、`OnlineSaveDialog`、`InlineVideoPlayer`、`FilePreviewDialog` 及保存树扁平化工具 |
| `albumdetail/AlbumDetailViewModelSupport.kt` | 840 | VM 纯函数支撑：`AlbumDetailModel`、相似作品推荐特征、头部专辑合并、DLSite 语言版本解析、asmr.one 轨道树扁平化、远程文件大小探测 |
| `albumdetail/AlbumDetailSharedSections.kt` | 688 | 共享区块：`AlbumDescription`、`AlbumTracks` / `TrackItem` / `OnlineTrackRow`、DLSite 推荐卡、区块标题 |
| `albumdetail/AlbumDetailLocalTab.kt` | 591 | 本地目录页签 `AlbumLocalBreadcrumbTabV2` |
| `albumdetail/AlbumDetailLocalAvailability.kt` | 57 | 本地专辑物理来源枚举与缺失专辑清理判断 |
| `albumdetail/AlbumDetailScrollPersistence.kt` | 43 | `PersistAlbumDetailListScroll`：滚动停止或页面离开时保存/恢复列表位置 |

> 重构纪律：新功能一律新建文件，禁止向 `AlbumDetailScreen.kt` / `MainContainer.kt` 追加。

## 4. 播放数据流

一条主线（各环节文件均已核实）：

```
UI（ui/player/playerviewmodel.kt 等）
  → PlayerConnection（playback/PlayerConnection.kt，@Singleton，约 959 行）
      · 构建 SessionToken(context, ComponentName(PlaybackService)) → MediaController
      · 队列管理、倍速/音量、切片循环（SlicePlaybackController / SliceLoopEngine）
  → MediaController ↔ MediaSession
  → PlaybackService（service/PlaybackService.kt，: MediaSessionService()，约 1449 行）
      · 持有 ExoPlayer 与 MediaSession；音频焦点、输出路由（AudioOutputDevicePolicy）、
        前台通知（LyricMediaNotificationProvider）、错误恢复（PlaybackRecoveryPolicy）、
        睡眠定时（SleepTimerManager）、进度/统计回写（PlaybackStateStore、data/repository）
```

数据源与缓存：`AsmrRenderersFactory` 组装音频链；`playback/RoutingPlaybackDataSource` + `playback/PlaybackMediaCache` 与 `cache/` 包（字节预算制，50–1000MB 钳制，图片 60% / 播放 35% 分账）负责在线流缓存与本地/远程路由。

音频链扩展点（改动成本最低的路径）：

- 接口：`playback/RuntimeAudioProcessor.kt`（`interface RuntimeAudioProcessor : AudioProcessor`）。
- 现有 **7 个实现类**：`StereoSpectrumTapAudioProcessor`、`GraphicEqualizerAudioProcessor`、`ChannelModeAudioProcessor`、`StereoOrbitAudioProcessor`、`SceneEffectAudioProcessor`、`VolumeThresholdAudioProcessor`、`BalanceAudioProcessor`。
- 注册点唯一：`playback/AsmrRenderersFactory.kt` 的 `buildAudioSink` 把全部处理器按固定顺序包进 `DynamicAudioProcessorChain`，由 `PlaybackService` 注入 ExoPlayer。
- 新增音效 = 实现 `RuntimeAudioProcessor` + 在 `AsmrRenderersFactory` 注册一处。

## 5. 搜索 / 内容源现状

- 双内容源：**asmr.one**（`data/remote/crawler/AsmrOneCrawler` + `data/remote/api/` 的 Retrofit 接口，含 Asmr100/200/300 镜像）与 **DLsite**（`data/remote/scraper/DLSiteScraper` 抓取 + `data/remote/dlsite/DlsitePlayLibraryClient` 已购曲库）。
- 已知问题（P0-3）：`ui/search/SearchViewModel.kt` 直接注入 `AsmrOneCrawler` / `DlsitePlayLibraryClient` / `DLSiteScraper`，搜索源无接口抽象——新增内容源需改 6–10 个文件。**重构计划将抽 `ContentSource` 接口**统一搜索入口。
- 辅助源：`hotlistening/`（热门收听）、`listentogether/`（一起听，服务端地址可由 `LISTEN_TOGETHER_BASE_URL` 构建配置覆盖）。

## 6. 构建与测试速查

- 环境：JDK 17；Windows 本机可用仓库自带的 `gradlew-local.bat` 辅助脚本（重定向 Gradle 本地缓存）。
- 构建：`./gradlew :app:assembleDebug`
- 测试：`./gradlew :app:testDebugUnitTest`（当前基线约 **840** 个用例，改动后应保持全绿）
- CI：`.github/workflows/release.yml` 由 `v*` tag 触发，先运行 `:app:testReleaseUnitTest` 再构建 Release 签名 APK。
- 签名配置与字幕模型按需下载说明见 README「Getting Started」一节。

## 7. 已知问题与重构线索

对照 `docs/project-quality-review-20260929.md`（2026-09-29 体检），当前状态：

- P0-1 已偿还：目录面板 V1–V4 死代码已删除，存活组件已去版本号重命名（见第 3 节括注）。
- P0-2 巨石文件群：17 个 >1200 行文件仍待拆，最甚者为 `AlbumDetailDirectorySupport.kt` / `AlbumDetailScreen.kt` / `MainContainer.kt`；纪律是"新功能一律新建文件"。
- P0-3 分层穿透：`ui` 直连 DAO 与搜索源无抽象仍在（见第 2、5 节），重构将先抽 `ContentSource` 接口。
- P0-4 文档债：本文件与 README「Getting Started」即其偿还；`docs/landing_zh.md` 失效截图已同步修订。

快速读懂本工程的建议顺序：`MainActivity` → `main/MainContainer`（导航骨架）→ `ui/library`（库页与详情家族）→ `playback/PlayerConnection` → `service/PlaybackService`（播放落地）。
