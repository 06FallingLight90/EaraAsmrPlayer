# ARCHITECTURE — EaraAsmrPlayer 架构说明

> 范围：`refactor/architecture-cleanup` 分支（v1.2.3 之后：死代码清理、去版本号重命名、阶段 1–3 结构重构，见第 7 节偿还状态）。
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
              │ MainContainer（导航宿主，约2630行）│
              │ MainContainerSupport（约590行：  │
              │  路由框/顶栏/系统栏/底部Chrome）  │
              └───────────────┬─────────────────┘
                              ▼
┌───────────────────────────── ui ─────────────────────────────┐
│ library（含 albumdetail/）· player · search · downloads      │
│ settings · playlists · groups · nav · sidepanel · calendar … │
│ common/：audio · core · cover · dialog · list · reorderable ·│
│          status 七个子包（按域拆分，P1-6 已偿还）              │
└──────────┬───────────────────────────────────┬───────────────┘
           │                                   │
           ▼                                   ▼
┌────── playback ──────┐              ┌─────── data ────────┐
│ PlayerConnection     │              │ local/（db、datastore）│
│ 31 个文件：音效链、  │              │ remote/（api、crawler、│
│ 频谱、切片循环、缓存 │              │ scraper、dlsite、auth、 │
└──────────┬───────────┘              │ download、repository、  │
           │                          │ settings、lyrics）      │
           ▼                          └──────────▲──────────────┘
┌────── service ───────┐                         │
│ PlaybackService      │─────────────────────────┘
│ : MediaSessionService│  （PlaybackService 亦回读 data 持久化进度/统计）
└──────────────────────┘

domain：Album / Track / Slice 纯模型（仅 Track 依赖 util）
di：CacheModule · DatabaseModule · NetworkModule（全部 Hilt 绑定集中于此）
feature 服务包：subtitle · translation · cache · work · hotlistening · listentogether · benchmark · performance
```

> 目录与包名已全面对齐（R2-A2，2026-10-01）：每个目录 = 同名子包，
> 包括 `main/`、`ui/player/nowplaying/`、`ui/library/albumdetail/` 与
> `ui/common/` 七子包。新文件一律放在与包名一致的目录下。

分层规则与已知例外：

- 预期方向：`ui → (playback, data, domain)`；`playback → (data, domain)`；`service → (playback, data)`；`data → (domain, util)`。
- 已知穿透（P0-3，重构目标）：`ui` 下 16 个文件直接 import `data.local.db.dao.*`（如 DownloadsViewModel 直连 AlbumDao / DownloadDao / TrackDao；AlbumDetailViewModel 注入整个 AppDatabase）。
- 已知反向耦合（`data → 上层`，共 4 文件 5 处，已记录于 `tools/import-direction-baseline.txt`，新增违规会被 CI 拦截；存量的跨层模型搬迁留待后续）：`data/settings/SettingsRepository → playback.AppVolume`；`data/lyrics/LyricsTargetContext → playback.MediaItemRequest`；`data/repository/PlaylistMediaItemMapper → playback.MediaItemFactory`；`data/lyrics/LyricsLoader → ui.library.LocalTreeLeafCacheEntry / TreeFileType`。
- `domain` 基本纯净：仅 `Track.kt` 依赖 `util.RemoteSubtitleSource`。

## 3. AlbumDetail 家族职责表

详情页是全库最大的文件家族：14 个文件、合计约 16 400 行。除前两个位于 `ui/library/` 外，其余在 `ui/library/albumdetail/`（包 `com.asmr.player.ui.library.albumdetail`，目录与包名一致）。

| 文件 | 约行数 | 职责 |
|---|---|---|
| `AlbumDetailScreen.kt` | 1415 | 页面入口 Composable `AlbumDetailScreen`（S11 拆分后仅剩主 Composable 编排） |
| `AlbumDetailViewModel.kt` | 2960 | `@HiltViewModel`：详情页状态编排——asmr.one / DLsite / 本地三路数据加载合并、播放与下载意图、相似作品推荐 |
| `albumdetail/AlbumDetailDirectorySupport.kt` | 3180 | 目录浏览面板：`DirectoryBrowserPanel`、`CompactDirectoryBreadcrumbContent`、`DirectoryFolderRow`、`DirectoryBatchBarEmbedded` |
| `albumdetail/AlbumDetailDlsiteTabs.kt` | 1870 | DLsite 页签 `AlbumDlsiteInfoBreadcrumbTabV2`：画廊预览、试听列表、目录树加载占位与空态插画 |
| `albumdetail/AlbumDetailLandscapeArtwork.kt` | 920 | 横屏封面渲染：模糊源/缓存、曲线形状、Ribbon、背景/封面/身份、相似作品（横竖屏） |
| `albumdetail/AlbumDetailHeader.kt` | 740 | 页头：`AlbumHeader`、动作栏、语言菜单、迟到元数据揭晓 |
| `albumdetail/AlbumDetailHero.kt` | 725 | Hero 区：背景模糊、身份覆盖层、在线听众信息、稳定身份/封面源记忆、滚动渐隐 |
| `albumdetail/AlbumDetailDialogs.kt` | 1070 | `AsmrOneDownloadDialog`、`OnlineSaveDialog`、`InlineVideoPlayer`、`FilePreviewDialog` 及保存树扁平化工具 |
| `albumdetail/AlbumDetailScreenSupport.kt` | 400 | 支撑层：枚举/数据类/动画 spec/`AlbumDetailHeroMotionState`/加载计划/`isVideoPreviewUrl`/`PlaylistAddTarget` |
| `albumdetail/AlbumDetailViewModelSupport.kt` | 780 | VM 纯函数支撑：`AlbumDetailModel`、相似作品推荐特征、头部专辑合并、DLSite 语言版本解析、asmr.one 轨道树扁平化、远程文件大小探测 |
| `albumdetail/AlbumDetailSharedSections.kt` | 670 | 共享区块：`AlbumDescription`、`AlbumTracks` / `TrackItem` / `OnlineTrackRow`、DLSite 推荐卡、区块标题 |
| `albumdetail/AlbumDetailLocalTab.kt` | 585 | 本地目录页签 `AlbumLocalBreadcrumbTabV2` |
| `albumdetail/AlbumDetailLocalAvailability.kt` | 50 | 本地专辑物理来源枚举与缺失专辑清理判断 |
| `albumdetail/AlbumDetailScrollPersistence.kt` | 37 | `PersistAlbumDetailListScroll`：滚动停止或页面离开时保存/恢复列表位置 |

> 重构纪律（由 CI 强制）：单文件 >1500 行禁入（存量 10 个记录于 `tools/size-guard-baseline.txt`，修复后须收缩 baseline）；新功能一律新建文件。

## 4. 播放数据流

一条主线（各环节文件均已核实）：

```
UI（ui/player/PlayerViewModel.kt 等）
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

- 双内容源：**asmr.one**（`data/remote/crawler/AsmrOneCrawler`；Retrofit 接口已收敛为 `AsmrOneApi` 主站 + `AsmrMirrorApi` 统一镜像接口，二者继承公共基接口 `AsmrWorkApi`，镜像实例由 NetworkModule 按 `@Named("asmr100"/"asmr200"/"asmr300")` 提供）与 **DLsite**（`data/remote/scraper/DLSiteScraper` 抓取 + `data/remote/dlsite/DlsitePlayLibraryClient` 已购曲库）。
- 搜索是**筛选驱动的编排**而非按源分发：`SearchViewModel.fetchPage` 四分支短路——purchasedOnly→已购库、collectedOnly→Eara 后端（`AsmrOneAvailabilityApi`，走 `LISTEN_TOGETHER_BASE_URL`，非 asmr.one 直连）、直接 RJ 号→DLsite 多级 locale 回退、默认→DLsite 网页搜索。
- 详情页分源：`AlbumDetailViewModel` 三个 `ensure*Loaded` 状态机（asmr.one 树 / DLsite 网页信息 / 已购曲库），由 `albumDetailOnlineLoadPlan`（纯函数，有锁定测试）按页签驱动。
- **新增内容源改动面现状（2026-09-30 实测）**：新 client（1 文件）+ `SearchViewModel.fetchPage` 分支与筛选映射 + `AlbumDetailViewModel` 的 ensure 编排与 `AlbumDetailModel` 状态字段 + UI 装配，约 6–8 文件。**未来收敛路径**：`AlbumDetailModel` 状态模型拆分（S11 级）后可引入 `OnlineWorkSource` 接口 + 注册表把改动面压到 ≤4；在出现真实的新源需求前不为三个 ensure 编排套接口（它们是状态机而非源 seam，硬套是仪式）。
- 辅助源：`hotlistening/`（热门收听）、`listentogether/`（一起听，服务端地址可由 `LISTEN_TOGETHER_BASE_URL` 构建配置覆盖）。

## 6. 构建与测试速查

- 环境：JDK 17；Windows 本机可用仓库自带的 `gradlew-local.bat` 辅助脚本（重定向 Gradle 本地缓存）。
- 构建：`./gradlew :app:assembleDebug`
- 测试：`./gradlew :app:testDebugUnitTest`（当前基线约 **875** 个用例，改动后应保持全绿且只增不减）
- CI：`.github/workflows/ci.yml`（push/PR）：架构守护（`tools/ci_guard.py`：单文件行数 ratchet + data 层 import 方向）→ `:app:testDebugUnitTest`；`.github/workflows/release.yml` 由 `v*` tag 触发，先运行 `:app:testReleaseUnitTest` 再构建 Release 签名 APK。
- 签名配置与字幕模型按需下载说明见 README「Getting Started」一节。

## 7. 已知问题与重构线索

对照 `docs/project-quality-review-20260929.md`（2026-09-29 体检），当前状态（阶段 1–3 重构后）：

- P0-1 已偿还：目录面板 V1–V4 死代码已删除，存活组件已去版本号重命名（见第 3 节括注）。
- P0-2 大幅偿还（阶段 2/3）：DownloadManager 2020→1170（协调器/Worker 外提）；AlbumDetailScreen 4039→1415（Header/Hero/LandscapeArtwork/ScreenSupport 四文件外提，行为经钉测试与实机 smoke 验证）；MainContainer 3251→2631（Support 外提，主函数体路由编排仍在，单文件行数已入 CI ratchet）；死代码删除与 SearchSource 死枚举清理见 P0-3。剩余超限文件见 `tools/size-guard-baseline.txt`。
- P0-3 部分偿还：镜像 Retrofit 接口四合一（`AsmrWorkApi`/`AsmrMirrorApi`）；死路由族 `album_detail_online` 与死枚举 `SearchSource` 已删；`ui` 直连 DAO 仍在（见第 2 节）。搜索源抽象按证据评估后**不做**接口套壳——原因与未来路径见第 5 节。
- P0-4 文档债：本文件与 README「Getting Started」即其偿还；`docs/landing_zh.md` 失效截图已同步修订。
- P1-1/P1-2 已偿还（阶段 2）：双 VM 共享支持函数与 `sanitizeFolderName` 收敛（钉测试）。
- P1-3 已偿还（阶段 3）：设备形态判断收敛 `ui/common/core/EaraWindowSize`（三族断点语义钉测试；`isPhone` smallestScreenWidthDp 族独立保留）。
- P1-6 已偿还（阶段 3）：ui/common 60 文件按域拆 7 子包（audio/core/cover/dialog/list/reorderable/status），测试同步移动。
- P1-8 已偿还（阶段 3）：DLsite Cookie AndroidKeyStore AES/GCM 加密存储（`data/remote/auth/ValueCipher` seam + 注入式测试；明文惰性迁移，实机验证登录持久化）。
- P2 部分偿还：networkmodule inline URL 收敛至 `NetworkHeaders`；nowplaying 死 import 清理；CI 架构守护（行数 ratchet + import 方向）。**backlog**：`LibraryViewModel.walkTree` / `scanFromDocumentTree` 拆函数、Chrome 概念归包（main 与 ui/nav）、`data → 上层`反向耦合的模型搬迁（见第 2 节 baseline）。

快速读懂本工程的建议顺序：`MainActivity` → `main/MainContainer`（导航骨架）→ `ui/library`（库页与详情家族）→ `playback/PlayerConnection` → `service/PlaybackService`（播放落地）。
