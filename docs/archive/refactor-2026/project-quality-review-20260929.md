# 项目质量体检报告（2026-09-29）

> 体检范围：`v1.2.3`（commit `b76ebab`），单 module `:app`。
> 方法：机械检查脚本 + 7 维度并行审查（架构 / 需求实现度 / 质量属性 / 代码坏味道 / 目录结构 / 文档化 / 度量与演化），关键结论已人工复验。
> 用途：为二次开发前的重构提供依据，问题编号（P0-x / P1-x）在 issue 与后续 commit 中沿用。

## 1. 项目画像

| 项 | 值 |
|---|---|
| 技术栈 | Kotlin 1.9.22 + Compose (BOM 2024.02) + Media3 1.8.0 + Hilt 2.49 + Room 2.6.1，单 module `:app`（另有 `:baselineprofile`） |
| 规模 | main 423 个 kt / 约 10.4 万行；测试 173 (test) + 18 (androidTest)，占比约 31% |
| 演化 | 696 次提交 / 约 7 个月，AI 流水线风格（PR 已到 #311），仍在高频迭代 |
| 总体健康度 | **C（需专项治理）** |

健康度评级理由：功能兑现度高、测试密度高、缓存与音频链设计用心（值 B 的部分）；但巨石文件、分层穿透、成片冗余对二次开发构成实际阻碍。

## 2. 质量属性评估

| 属性 | 评级 | 依据 |
|---|---|---|
| 性能 | A- | cache/ 字节预算制（50-1000MB 钳制、图 60%/播放 35% 分账）、信号量限流预加载、`benchmark/` + `baselineprofile` 齐备；仅 PlaybackService 两处 runBlocking |
| 可靠性 | B+ | 下载断点续传校验（DownloadManager 470/762 行）、字幕任务显式状态机、前台服务生命周期齐全（含 API34 `onTimeout`）；大量 `catch (_: Exception)` 静默吞异常；源码零 GlobalScope |
| 可测试性 | B+ | 主体为纯 JVM 单测（Robolectric 仅约 5 个文件），androidTest 聚焦 DB 迁移等高价值场景；但 **CI 不跑测试** |
| 可修改性 | B- | 音频链扩展点干净（实现 `RuntimeAudioProcessor` 即可，现有 7 个实现类，注册点唯一）；但搜索源无抽象、UI 巨石文件、repository 层被绕开 |
| 安全性 | B+ | DeepSeek Key / 代理密码走 AndroidKeyStore AES/GCM（解密失败自清）；DLsite Cookie 明文存 SharedPreferences（`data/remote/auth/DlsiteAuthStore.kt:7`）为唯一例外 |

### 典型变更场景支撑度（对二次开发最重要）

| 场景 | 支撑度 | 需触碰 |
|---|---|---|
| 新增在线内容源 | **低** | 6-10 个文件：新 Api+Crawler+DI、enum 分支、SearchViewModel、MainContainer（3248 行）、UI |
| 播放界面加新控件 | 中高 | 3-5 个文件约 4 层（Screen → playerviewmodel → PlayerConnection → PlaybackService），但 NowPlayingScreen 定位成本高 |
| 新增音频效果 | 高 | 4-6 个文件，扩展点明确（`RuntimeAudioProcessor` 接口 + `AsmrRenderersFactory` 注册） |

## 3. 需求实现度（README 20 项声明）

**18/20 落地且有测试**。例外：

- **P1｜m3u8 声明为假**：README 与 `docs/landing_zh.md` 宣称"视频播放支持 m3u8 流"，但代码主动拦截报错——`ui/player/playerviewmodel.kt:692-694、711-714`、`playback/PlayerConnection.kt:292-293`（"当前不支持 m3u8 流媒体，请先下载音频文件"）。
- **P1｜CI 不跑测试**：`.github/workflows/release.yml` 仅 checkout→签名→assembleRelease→体积/onnx 运行时校验→发布，无 test 步骤；160+ 测试文件只是本地资产。
- P2｜一起听服务端 `earaasmr.com`（`LISTEN_TOGETHER_BASE_URL` 默认值，可构建配置覆盖）闭源不可审计，失败被 runCatching 静默吞掉；`listentogether/ListenTogetherApi.kt`（Retrofit 接口）全仓库零引用，为死代码。
- P2｜页面翻译依赖 Google 公共接口（`translate.googleapis.com`），文档已如实声明无 SLA。

## 4. 分级问题清单

### P0 结构性问题（阻碍演进）

**P0-1 版本化僵尸 UI 层：约 660 行死代码堆积在全库最大文件里**

- 证据（已复验调用图）：目录浏览面板从 V1 演进到 V5，外部只调用 `DirectoryBrowserPanelV4`（`albumdetail/AlbumDetailLocalTab.kt:334`、`albumdetail/AlbumDetailDlsiteTabs.kt:1280/1802`）；而 `albumdetail/AlbumDetailDirectorySupport.kt` 中以下函数零调用：
  - `DirectoryBrowserPanel`（L2339，内部引用 `CompactDirectoryBreadcrumbContent` L2285、`DirectoryBatchBarEmbeddedV2` L2405、`DirectoryFolderRow` L2444→L2896）
  - `DirectoryBrowserPanelV2`（L2745，引用 V2/V3 版本 L2803/2812/2853）
  - `DirectoryBatchBarEmbeddedV2/V3/V4`（L2487/2668/3085）
  - `DirectoryFolderRow` / `DirectoryFolderRowV2`（L2896/2601）
  - `CompactDirectoryBreadcrumbContent` / `V2`（L2285/2547）
- 为什么是问题：该文件本身 3977 行（全库第一），死代码与活代码混杂，改动极易改错版本；baseline-prof.txt 只含 V4/V5/V3 条目，可交叉验证死活。
- 改进：删除未使用的 V1-V4 链，将 `DirectoryBrowserPanelV4` / `DirectoryBatchBarEmbeddedV5` / `DirectoryFolderRowV3` / `CompactDirectoryBreadcrumbContentV3` 重命名去掉版本号。

**P0-2 巨石文件群：17 个文件 > 1200 行，9 个 > 2000 行**

- 证据（行数 × git 改动次数）：

| 文件 | 行数 | 改动次数 |
|---|---|---|
| `ui/library/albumdetail/AlbumDetailDirectorySupport.kt` | 3977 | 37 |
| `ui/library/AlbumDetailScreen.kt` | 3868 | **115（全库第一）** |
| `main/MainContainer.kt` | 3248 | 111 |
| `ui/library/libraryviewmodel.kt` | 2993 | — |
| `ui/library/AlbumDetailViewModel.kt` | 2985 | 63 |
| `ui/player/NowPlayingScreen.kt` | 2786 | 58 |
| `ui/settings/SettingsScreen.kt` | 2586 | 53 |
| `ui/downloads/DownloadsScreen.kt` | 2175 | 26 |
| `ui/search/SearchScreen.kt` | 2121 | 79 |
| `data/remote/download/DownloadManager.kt` | 1912 | — |

- 内部构成（已解剖）：多为 30-60 个顶层函数堆积而非单一职责类。例：AlbumDetailScreen 横跨 UI composable / 横屏动画数学（L261-366）/ 媒体类型判断（`isVideoPreviewUrl`）/ 封面身份解析；MainContainer 把导航骨架、SystemUi 显隐（L621-815 多条 apply/restore）、BottomChrome 揉进单文件；DownloadManager 内嵌 `DownloadQueueCoordinator`（L612）与 `DownloadWorker`（L861）两个独立协作类；PlaybackService 内聚 audio focus、路由监听、通知、错误恢复等 20+ 私有方法。
- 改进：按"改动频率 × 行数"优先拆 AlbumDetailScreen 与 MainContainer；沿 `albumdetail/` 子包既有拆分出口继续；**新功能一律新建文件，禁止向巨石追加**。

**P0-3 分层穿透：ViewModel 直连 DAO 与网络，repository 名存实亡**

- 证据：`ui/` 下 26 处直接 import `data.local.db.dao.*`、23 处直接 import `data.remote.*`；仅 9 处走 repository。典型：`ui/downloads/DownloadsViewModel.kt:12-14` 直连 AlbumDao/DownloadDao/TrackDao；`AlbumDetailViewModel` 构造器注入整个 `AppDatabase`；`ui/search/SearchViewModel.kt:8-13` 直接注入具体 `AsmrOneCrawler`/`DlsitePlayLibraryClient`/`DLSiteScraper`，搜索源无接口抽象。
- 为什么是问题：新增内容源要改 6-10 个文件且必须碰 MainContainer；UI 状态管理两种风格并存（sealed `LibraryUiState` vs 裸 DAO Flow stateIn 直出）。
- 改进：先抽 `ContentSource` 接口统一搜索源（目标：新增源改动面降到 3-4 个文件），再逐步把 DAO 访问下沉 repository；统一"sealed UiState + 单通道"约定。

**P0-4 零架构文档**

- 证据：无 ARCHITECTURE.md；AlbumDetail 家族实为 **10 文件 15889 行**，无分工说明；核心类（PlaybackService / PlayerConnection / DownloadManager / LibraryViewModel）类级 KDoc 为零；全库 `/**` 仅 114 处（约每 911 行 1 处）；`docs/landing_zh.md` 16 处截图引用全部失效、版本号停在 v0.2.2。
- 改进：补 ARCHITECTURE.md（约 150 行：包分层图 + AlbumDetail 家族 10 文件职责表 + 播放数据流 UI → PlayerConnection → MediaSession → PlaybackService）；README 补 Getting Started；修订失效截图。

### P1 维护性问题

| # | 问题 | 证据 | 改进 |
|---|---|---|---|
| P1-1 | 双 ViewModel 大面积同源重复：`buildTagsToken`、`parseAlbumTags`、`isLikelyPlaceholderCover` 等至少 3-8 组同名同逻辑私有方法逐字重复（已验证 3 组：`libraryviewmodel.kt:2071/2104/1487` ↔ `AlbumDetailViewModel.kt:1182/1190/2621`） | `ui/library/` 两 VM | 抽取共享 `AlbumTagRepository` / 封面持久化工具 |
| P1-2 | 文件名 sanitize 正则（`[\\/:*?"<>|]` + `ifEmpty "item"`）复制粘贴 **10 处** | `AlbumDetailDialogs.kt:285/318/351/777`、`AlbumDetailDirectorySupport.kt:1055/1370/1825/1915`、`AlbumDetailViewModel.kt:2800`、`AlbumDetailViewModelSupport.kt:805` | 收敛为单一 `sanitizeFolderName()` |
| P1-3 | 设备形态判断散布 **37 文件 154 处**（`WindowWidthSizeClass|isLandscape|screenWidthDp`），`val isLandscape = ...` 在 MainContainer:1036、LyricsPage:68、LyricsScreen:34、NowPlayingScreen:1375 各自手写 | NowPlayingScreen 24 处、MainContainer 9 处 | 抽统一 `EaraWindowSize` helper |
| P1-4 | 同名文件撞车：`data/settings/SettingsDataStore.kt`（83 行，纯 `SettingsKeys` 定义）与 `data/local/datastore/SettingsDataStore.kt`（242 行，@Singleton 读写类）；key 定义散布两处 | 两文件共用同一 `Context.settingsDataStore` | 改名 + 统一 key 位置 |
| P1-5 | 16 个非 PascalCase 文件：`libraryviewmodel.kt`、`playerviewmodel.kt`、`networkmodule.kt`、`playbackmanager.kt`、`appdatabase.kt`、`albumdao.kt`、entities 下 5 个、sidepanel 下 5 个；与同包 PascalCase 混用（libraryviewmodel.kt vs AlbumDetailViewModel.kt 同包） | Glob/Grep 可复现 | IDE 批量重命名 |
| P1-6 | `ui/common` 51 文件杂物抽屉：通用控件（SearchBar/FlatDialog/Modifiers）与业务组件（EqualizerPanel/NetworkRouteSettingsSection/TrackMetaSupport）混放 | 机械检查 wide_dirs | 按域拆子包 |
| P1-7 | DownloadManager 15+ 处 `catch (_: Exception)`，故障不可观测（L320/1379/1587-1660 等）；自建长生命周期 scope 与 WorkManager 双轨 | `DownloadManager.kt` | 至少打日志；统一任务取消路径 |
| P1-8 | DLsite Cookie 明文存 SharedPreferences（项目已有 Keystore 加密模式未复用） | `DlsiteAuthStore.kt:7` | 复用 `DeepSeekApiKeyStore` 模式 |

### P2 改进项

- URL/魔法值未收敛：`di/networkmodule.kt:88-182` 反复内联 asmr.one/dlsite Origin/Referer 组块，而 `data/remote/NetworkHeaders.kt:7` 已定义常量未被采用；DeepSeek URL 分散 `DeepSeekAccountRepository.kt:221` 与 `SubtitleTranslationClient.kt:661`。
- `Asmr100Api`/`Asmr200Api`/`Asmr300Api` 三个镜像 Retrofit 接口为复制粘贴式重复（共享 DTO，search/getWorkDetails 签名重复），新增端点字段需同步 3 处。
- 超长函数：`libraryviewmodel.kt` 的 `walkTree`（L3128）、`scanFromDocumentTree`（L2321，内嵌 parseBestSubtitle，>100 行）。
- PlaybackService 主线程 `runBlocking` 两处（L277 onCreate 读设置、L1375）。
- 组织范式混用：主体按层组织，但 hotlistening/listentogether/translation/playback/subtitle/service 为顶层 feature 包；`hotlistening/`（2 文件）与 `ui/hotlistening/`（2 文件）两处拆分。
- `main/` 与 `ui/nav` 的 "Chrome" 概念分居两包。
- `example_screen/` 8 张中文文件名 PNG 入库，.gitignore 全局忽略 `*.png` 再逐个豁免，规则脆弱。
- 提交信息风格不统一（conventional commits 与裸中文短句混用），影响 changelog 自动生成。

## 5. 技术债清单

| 类别 | 条目 | 优先级 / 建议偿还时机 |
|---|---|---|
| 代码债 | 版本化死代码 ~660 行（P0-1）；双 VM 重复方法（P1-1）；sanitize×10（P1-2）；ListenTogetherApi 死代码 | 高 / 二开动手前 |
| 设计债 | repository 被绕开、搜索源无抽象、DI 3 文件担全部绑定、UI 状态管理两种风格并存（P0-3） | 高 / 二开第一轮迭代 |
| 测试债 | CI 不跑测试；改动最频繁的 UI 文件无 androidTest 集成保障 | 高 / 立即（加 CI 步骤成本极小） |
| 文档债 | 零架构文档、README 无构建指引、landing_zh 失效截图、release.yml 的 4 个 secrets / 20MB 上限 / sherpa-onnx 排除等约束未文档化 | 中 / 二开上手前 |

## 6. Quick wins（投入小收益大）

1. **CI 加测试步骤**（`testReleaseUnitTest`）——160+ 现成测试立刻变成质量门禁。
2. **删除目录面板 V1-V4 死代码**（约 660 行，纯删除；3 个调用点 + baseline-prof 作安全清单）。
3. **写 ARCHITECTURE.md（约 150 行）**——半天可完成，直接解决上手最大障碍。
4. **批量重命名 16 个小写文件** + 修订 landing_zh.md 失效截图。
5. **README 补 Getting Started**（构建/测试/keystore/模型下载，素材在 landing_zh 与 release.yml，纯搬运）。

## 7. 重构路线图

1. **腐蚀最小化（动手前）**：Quick wins 1-3；约定新功能一律新建文件，禁止向 AlbumDetailScreen / MainContainer 追加。
2. **专项修补（第一轮迭代）**：抽 `ContentSource` 接口（P0-3）；抽取双 VM 共享逻辑与 `sanitizeFolderName`（P1-1/P1-2）；拆分 DownloadManager 内嵌类（P0-2 局部）。
3. **持续预防**：抽 `EaraWindowSize`（P1-3）；DI 按功能域拆 module；CI 守护（单文件 >1500 行禁入、import 方向 lint：禁止 data 层引用 playback/ui）；优先拆 AlbumDetailScreen（3868 → 目标 <1000 行/文件），拆前先补 ViewModel 层测试作安全网；每 1-2 个月重跑 top20 改动榜监测腐蚀速率。

## 附：事实与推断的区分

- 直接验证的事实：所有行数/引用计数/git 统计；m3u8 拦截文案；DeepSeek Key 加密实现；CI 步骤组成；死代码调用图；双 VM 重复方法逐字比对。
- 标注为推断的结论：PlayerConnection 扇入 19 是否构成分层穿透（需读内容定罪）；测试断言深度未逐文件验证；runBlocking 实际阻塞时长；一起听服务端可用性；模型下载源在目标网络可达性。

## 附：仓库级可移植性备注

- `local.properties` 已正确 gitignore（Android 标准做法）。
- `gradlew-local.bat`、`gradle-clean-local-cache.bat` 为本机辅助脚本但已入库，不涉及硬编码本机路径。
- `tools/` 7 个脚本判定合理入库：`encode_prompts.py` 将 gitignore 的 `prompts/` 明文库经 XOR+Base64 编码生成入库的 `subtitle/TranslationPromptsEncoded.kt`（无本机路径硬编码）；其余 6 个为性能测量与 sherpa 运行时打包/安装辅助。
