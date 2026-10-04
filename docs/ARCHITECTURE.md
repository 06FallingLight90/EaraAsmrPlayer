# ARCHITECTURE — EaraAsmrPlayer 架构说明

> 范围：`refactor/architecture-cleanup` 分支（v1.2.3 之后：死代码清理、去版本号重命名、阶段 1–3 结构重构、第二轮重构 R2 阶段 A/B，见第 7 节偿还状态）。
> 文中包名、类名、行数均于 2026-10-02 直接从代码核实（readlines 口径，含文件末尾空行）；行数为约数。

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
              │ MainContainer（导航宿主，约798行） │
              │ MainContainerSupport（约623行：  │
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
- 数据访问边界（R2-B4/B5，2026-10-02）：两个 God VM（LibraryViewModel / AlbumDetailViewModel）的数据库读写已收进 `data/repository/LibraryWriteRepository`（标签/删除/扫描/在线保存事务族，平台接缝 lambda 注入）与 `LibraryReadRepository`（查询/流/PagingSource 出口），构造不再注入 `AppDatabase`/DAO。
- 已知穿透（存量入 `tools/import-direction-baseline.txt`，新增违规会被 CI 拦截）：R3-A1 起 ui 侧（含 main）不得 import `data.local.db.*`（含实体与查询类型，此前规则仅覆盖 `dao.`/`AppDatabaseProvider`）、`data.local.datastore.*`、`cache.*` 与 `work.*`；反向 `translation`/`hotlistening` 不得 import `ui.*`。存量欠账见 baseline，随 R3-B 分族收口。
- 反向耦合（`data → 上层`）已清零（R2-B1 模型下沉 domain/data，守卫规则锁死）。
- `domain` 基本纯净：仅 `Track.kt` 依赖 `util.RemoteSubtitleSource`。

## 3. AlbumDetail 家族职责表

详情页是全库最大的文件家族：14 个文件、合计约 14 821 行。除前两个位于 `ui/library/` 外，其余在 `ui/library/albumdetail/`（包 `com.asmr.player.ui.library.albumdetail`，目录与包名一致）。

| 文件 | 约行数 | 职责 |
|---|---|---|
| `AlbumDetailScreen.kt` | 1518 | 页面入口 Composable `AlbumDetailScreen`（S11 拆分后仅剩主 Composable 编排） |
| `AlbumDetailViewModel.kt` | 2510 | `@HiltViewModel`：详情页状态编排——asmr.one / DLsite / 本地三路数据加载合并、播放与下载意图、相似作品推荐（数据访问已经 LibraryRead/WriteRepository，R2-B5） |
| `albumdetail/AlbumDetailDirectorySupport.kt` | 2700 | 目录浏览面板：`DirectoryBrowserPanel`、`CompactDirectoryBreadcrumbContent`、`DirectoryFolderRow`、`DirectoryBatchBarEmbedded` |
| `albumdetail/AlbumDetailDlsiteTabs.kt` | 1932 | DLsite 页签 `AlbumDlsiteInfoBreadcrumbTabV2`：画廊预览、试听列表、目录树加载占位与空态插画 |
| `albumdetail/AlbumDetailLandscapeArtwork.kt` | 971 | 横屏封面渲染：模糊源/缓存、曲线形状、Ribbon、背景/封面/身份、相似作品（横竖屏） |
| `albumdetail/AlbumDetailHeader.kt` | 771 | 页头：`AlbumHeader`、动作栏、语言菜单、迟到元数据揭晓 |
| `albumdetail/AlbumDetailHero.kt` | 734 | Hero 区：背景模糊、身份覆盖层、在线听众信息、稳定身份/封面源记忆、滚动渐隐 |
| `albumdetail/AlbumDetailDialogs.kt` | 1093 | `AsmrOneDownloadDialog`、`OnlineSaveDialog`、`InlineVideoPlayer`、`FilePreviewDialog` 及保存树扁平化工具 |
| `albumdetail/AlbumDetailScreenSupport.kt` | 447 | 支撑层：枚举/数据类/动画 spec/`AlbumDetailHeroMotionState`/加载计划/`isVideoPreviewUrl`/`PlaylistAddTarget` |
| `albumdetail/AlbumDetailViewModelSupport.kt` | 773 | VM 纯函数支撑：`AlbumDetailModel`、相似作品推荐特征、头部专辑合并、DLSite 语言版本解析、asmr.one 轨道树扁平化、远程文件大小探测（含收敛后的 `collectSubtitleCandidates`） |
| `albumdetail/AlbumDetailSharedSections.kt` | 684 | 共享区块：`AlbumDescription`、`AlbumTracks` / `TrackItem` / `OnlineTrackRow`、DLSite 推荐卡、区块标题 |
| `albumdetail/AlbumDetailLocalTab.kt` | 590 | 本地目录页签 `AlbumLocalBreadcrumbTabV2` |
| `albumdetail/AlbumDetailLocalAvailability.kt` | 56 | 本地专辑物理来源枚举与缺失专辑清理判断 |
| `albumdetail/AlbumDetailScrollPersistence.kt` | 42 | `PersistAlbumDetailListScroll`：滚动停止或页面离开时保存/恢复列表位置 |

> 重构纪律（由 CI 强制）：单文件 >1500 行禁入（存量 8 个记录于 `tools/size-guard-baseline.txt`，修复后须收缩 baseline）；新功能一律新建文件。

## 4. 播放数据流

一条主线（各环节文件均已核实）：

```
UI（ui/player/PlayerViewModel.kt 等）
  → PlayerConnection（playback/PlayerConnection.kt，@Singleton，约 985 行）
      · 构建 SessionToken(context, ComponentName(PlaybackService)) → MediaController
      · 队列管理、倍速/音量、切片循环（SlicePlaybackController / SliceLoopEngine）
  → MediaController ↔ MediaSession
  → PlaybackService（service/PlaybackService.kt，: MediaSessionService()，约 1465 行）
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
- 测试：`./gradlew :app:testDebugUnitTest`（当前基线 **938** 个用例，改动后应保持全绿且只增不减）
- CI：`.github/workflows/ci.yml`（push/PR）：架构守护（`tools/ci_guard.py`：单文件行数 ratchet「路径:行数」pin + 17 条 import 方向规则 + 反例夹具自检）→ `:app:testDebugUnitTest`；`.github/workflows/release.yml` 由 `v*` tag 触发，先运行 `:app:testReleaseUnitTest` 再构建 Release 签名 APK。
- 签名配置与字幕模型按需下载说明见 README「Getting Started」一节。

## 7. 已知问题与重构状态

> 门禁报告索引：各阶段审查报告落 [docs/iteration/](iteration/)（`phase-1/2/3-review.md` 为第一轮，`r2-phase-A/B/C-review.md` 为第二轮），含审查发现（P0/P1/P2 分级）与实机走查证据。

### 7.1 第一轮重构（阶段 1–3，已完成）

对照 `docs/project-quality-review-20260929.md`（2026-09-29 体检；下述 P0/P1 编号均属该报告，仅作历史索引）：

- P0-1 已偿还：目录面板 V1–V4 死代码已删除，存活组件已去版本号重命名（见第 3 节括注）。
- P0-2 大幅偿还：DownloadManager 2020→1170（协调器/Worker 外提）；AlbumDetailScreen 4039→1415（Header/Hero/LandscapeArtwork/ScreenSupport 四文件外提，行为经钉测试与实机 smoke 验证；现状 1523）；MainContainer 3251→2631（Support 外提，主函数体路由编排仍在；现状 2702）。剩余超限文件见 `tools/size-guard-baseline.txt`。
- P0-3 部分偿还：镜像 Retrofit 接口四合一（`AsmrWorkApi`/`AsmrMirrorApi`）；死路由族 `album_detail_online` 与死枚举 `SearchSource` 已删；`ui` 直连 DAO 的收拢见 7.2 R2-B。搜索源抽象按证据评估后**不做**接口套壳——原因与未来路径见第 5 节。
- P0-4 文档债：本文件与 README「Getting Started」即其偿还；`docs/landing_zh.md` 失效截图已同步修订。
- P1-1/P1-2 已偿还：双 VM 共享支持函数与 `sanitizeFolderName` 收敛（钉测试）。
- P1-3 已偿还：设备形态判断收敛 `ui/common/core/EaraWindowSize`（三族断点语义钉测试；`isPhone` smallestScreenWidthDp 族独立保留）。
- P1-6 已偿还：ui/common 60 文件按域拆 7 子包（audio/core/cover/dialog/list/reorderable/status），测试同步移动。
- P1-8 已偿还：DLsite Cookie AndroidKeyStore AES/GCM 加密存储（`data/remote/auth/ValueCipher` seam + 注入式测试；明文惰性迁移，实机验证登录持久化）。

### 7.2 第二轮重构 R2（绞杀者局部重写，进行中）

计划 [docs/refactor-plan-r2.md](refactor-plan-r2.md)；依据 [2026-10-01 体检](project-quality-review-20261001.md)（总评 C）用户四决策：用户无感知 / 局部重写 / 隐性行为随改随文档化 / 双兜底（seam 测试先行 + 实机走查）。

- **阶段 A 已完成**（tag `refactor-r2/phase-A`）：目录=包名 22 文件统一；ci_guard 重写（真实包名匹配 + 全仓扫描 + 17 条方向规则 + 反例自检）；`collectSubtitleCandidates` 三份收敛；runBlocking 超时兜底 / OkHttp 显式超时 / DownloadWorker IO 重试 ≤2。
- **阶段 B 已完成**（tag `refactor-r2/phase-B` @ 9afcccb）：data→上层反向 import 清零（B1 模型下沉）；service 去 `MainActivity` import（B2，含**切片后台循环修复** `awaitFrameCommitOrTimeout`，上游 issue #322，实机验证通过）；`PlaybackController` 接口（B3）；两个 God VM 数据访问收进 `LibraryReadRepository`/`LibraryWriteRepository`（B4/B5，行为档案见 7.3）；ui→DAO 存量 19 处/15 文件入 baseline（B6）。测试 880→**906** 只增不减；门禁实机走查 5/6（下载全链被既有在线树请求取消问题阻塞，`git diff refactor-r2/phase-A..HEAD` 佐证非回归）。
- **阶段 C 已完成**（tag `refactor-r2/phase-C` @ 814627b；审查报告 [r2-phase-C-review.md](iteration/r2-phase-C-review.md)）：三个 God 组合函数拆分退出 pin（MainContainer 2375→796、NowPlayingScreen 2902→836、SettingsScreen 2675→1270）；消 3 组特征环（ui.player→ui.library、AlbumDetail↔Settings、ui.sidepanel→ui.library）；C 批次 G 补闸门（size ratchet 贴实测、`ui-to-data-remote` 规则入守护、CI 编译门禁）；C4 数据编排下沉 4 类新 repository——`OnlineContentRepository`（ASMR.ONE 解析缓存/云同步/封面补全/预览/文件体积/推荐富化）、`UpdateRepository`、`SearchRepository`、`DownloadQueueRepository`（AlbumDetailViewModel 2996→2510、LibraryViewModel 2641→2521，行为契约见 7.3 档案）；`DownloadManager` 迁 `data/download`、`LibraryQuerySpec` 族迁 `data/local/db/query`。门禁三件套：本机测试 906→**938** 全绿、子代理审查无 P0 且 P1 闭环（缓存并发安全 ConcurrentHashMap+Mutex、ci_guard main 包盲区补夹具）、实机走查通过（ASMR.ONE 解析端到端、云同步链路、DL Play 登录态，受限项如实记录）；CI 双绿。
- **C5 结论（阶段内闭环）**：size ratchet 持续还债后两 VM pin 贴实测（2510/2521）。1500→800 的进一步收紧需先做编排层 state holder 重构——云同步/删除/扫描族是 UI 状态机（`_syncStatus`/选择队列/消息/bulk 进度），直接下沉只是搬运代码+回调透传。列为后续方向，不在阶段 C 强行达成。

### 7.3 行为档案索引（隐性行为文档化）

- [docs/behavior-notes/library-delete-family.md](behavior-notes/library-delete-family.md) — 删除/标签/扫描/在线保存事务族的原状不对称（如 deleteAlbum 路径不清 track_tag/remote_subtitle_sources/local_tree_cache），R2-B4/B5 逐字下沉时钉住，勿"顺手"清理。
- [docs/behavior-notes/online-content-caching-scope.md](behavior-notes/online-content-caching-scope.md) — ASMR.ONE 解析/曲目缓存为进程级全局共享（切端点 invalidate 影响所有实例）+ ConcurrentHashMap/Mutex 并发语义，R2-C4b-3 下沉产生。

### 7.4 backlog

- 2026-10-02 体检新增（阶段 C 已偿部分见 7.2）：~~特征环 3 组~~（C1/C4b-4 已消）；剩余：编排层 state holder 重构（两 God VM 1500 收紧前提）、`LibraryWriteRepository` 1050 行拆族、根文档三缺（LICENSE/CHANGELOG/CONTRIBUTING）。
- 决策待定：`ensureAlbumCoverSaved` 双实现统一（LibraryViewModel 旧版仅网络/2048/ARGB_8888，repo 版支持本地来源/1280/RGB_565——统一属行为变更，见 r2-phase-C-review.md）。
- 沿用：`LibraryViewModel.walkTree` / `scanFromDocumentTree` 拆函数、Chrome 概念归包（main 与 ui/nav）、dao 包投影 DTO 归位（`LibraryTrackRow` 等）。

快速读懂本工程的建议顺序：`MainActivity` → `main/MainContainer`（导航骨架）→ `ui/library`（库页与详情家族）→ `playback/PlayerConnection` → `service/PlaybackService`（播放落地）。
