# 项目质量体检报告（2026-10-07）— EaraAsmrPlayer

- **体检范围**：整个仓库（分支 `refactor/architecture-cleanup` @ `e8a58a4`，工作树干净）；含源码、构建/CI 配置、文档、git 历史
- **体检日期**：2026-10-07
- **代码规模**：`app/src/main` 541 个 .kt / `app/src/test` 188 个 / `app/src/androidTest` 18 个；另有独立 module `:baselineprofile`
- **测试健康度**：`:app:testDebugUnitTest --rerun` 实跑（用户授权）——**1010 用例 / 0 失败 / 0 错误 / 4 跳过**，测试套件累计 152.5s，构建总耗时 5m03s。跳过 4 例 = 3 个需 `-Pasmr.latency` 显式开启的延迟基准（`AsmrOneLatencyBenchmarkTest`、`SearchCollectedLatencyBenchmarkTest`×2）+ 1 个 Windows 平台门控用例（`PageTranslationUiTest.kt:82` `assumeFalse(os.name contains "Windows")`）
- **方法**：7 个维度（架构设计 / 需求实现度 / 质量属性 / 代码坏味道 / 目录结构 / 文档化 / 度量与演化）独立子代理并行审查，主会话对 P0/P1 级声明逐条回源码复核（本次共 20 项复核，含 4-指标历史演化用 `git grep`/`git ls-tree` 独立复算）；只读分析，未修改任何代码（唯一写入是本报告）

## 总体评级

**C — 需要专项治理**（较 2026-10-04 体检有实质收敛，但未跨级）

判定依据：工程护栏在同规模项目中属领先水平（CI 架构守护 17 条 import 规则 + 行数 ratchet + 包级 SCC ratchet + 反例夹具自检；1010 用例全绿；Baseline Profile 8 场景；Release 体积/运行时校验），但**包级仍是一个 62 包中占 40 包的巨型强连通团**，且 **UI/主包反层穿透仍有 149 处被冻结在 baseline 中**——分层在账面上被声明、在依赖图上不成立，属 skill 判定口径中的 P0 级结构性问题。

| 维度 | 评级 | 摘要 |
|------|------|------|
| 架构设计 | C | 分层声明清晰、seam 意识强（SearchQueryPort/RuntimeAudioProcessor 是好例），但包级整体成环（SCC=40）、UI/main 反层穿透 149 处、`data/repository` 零接口 |
| 需求实现度 | B | README 约 25 条声明归并 13 组后基本兑现且证据链完整；m3u8 声明已与实现对齐（上次不符项已修）；扣分在 AI 翻译 Key、asmr.one 自建后端等隐含前置未声明 |
| 质量属性 | B | 性能/安全性/可靠性架构级支撑扎实（Baseline Profile、Range 续传、指数退避恢复、Keystore 加密、APK 校验）；短板在弱网无降级、无障碍无断言、关键链路缺测 |
| 代码坏味道 | B | 结构债显著偿还（行数 pin 8→2、import baseline 344→205、`centerCropSquare` 已收敛为唯一实现）；残留 5 个 595–996 行 God Composable 且不受文件级守卫约束 |
| 目录结构 | B | 包名一致性 100%（541 文件全量脚本校验）、忽略规则干净、测试包镜像 main；扣分在 benchmark harness 混入 main、宽目录与 docs 平铺 |
| 文档化 | B | README 的构建/版本/签名/模型 URL 逐条核实准确、测试基线 1010 一致、m3u8 与 landing compileSdk 已修；扣分在 ARCHITECTURE §3 家族表刚同步即漏 4 文件、根文档三缺 |
| 度量与演化 | C | 守护工具化领先，但核心指标"包级 SCC"相对 refactor 起点仅 41→40、包间边/包 6.58→7.40，耦合密度上升；ratchet 存在可绕过面 |

## 与上次体检（2026-10-04）的对比

| 上次 P0/P1 | 上次状态 | 本次实测 | 判定 |
|---|---|---|---|
| 依赖环 8 组 / 包级 SCC 50 | P0 | SCC **40**（`tools/package-scc-baseline.txt`），2/3 环消解，仍存巨型团 | 部分偿还 |
| God 文件约 20 个 ≥1000 行 | P0 | **13** 个（脚本实测）；size pin **8→2** | 大幅偿还 |
| ui 四类穿透（110 处 ui→db 等） | P0 | ui→db **51**、ui→net-stack **3**、ui→cache/work **8**、ui→service **2**、ui→datastore **4** | 大幅收敛，残余 149 处（含 main 13） |
| Room `exportSchema=false`（迁移漂移无拦截） | P1 | `AppDatabase.kt:93` **exportSchema = true** + `ksp room.schemaLocation` + `app/schemas/31.json` 入库 | 已偿还 |
| `centerCropSquare` 三份 | P1 | 唯一实现 `util/CoverSupport.kt:15`，3 处调用 | 已偿还 |
| README m3u8 声明与实现相反 | P1 | README:33 已写"暂不支持"，`PlayerConnection.kt:292-293` 拒绝 m3u8，依赖无 HLS | 已偿还 |
| ARCHITECTURE §2 MainContainer 行数漂移（2700 vs 798） | P1 | 实测 794 vs 文档 794–798 → **本项已修**；但 §3 出现新漂移（见 P1-6） | 部分偿还 |
| tutorial.md 游离且陈旧 | P1 | 仍被 `.git/info/exclude:9` 排除，内容仍陈旧 | 未偿还 |

## 项目画像

Kotlin 1.9.22 + Jetpack Compose（BOM 2024.02 / Material 3）+ Media3 1.8.0（ExoPlayer + MediaSessionService）+ Hilt 2.49 + Room 2.6.1（DB v31，schema 已导出）+ WorkManager 2.9 + Paging 3.2.1 + Retrofit 2.9 / OkHttp 4.12 / Jsoup / PDFBox。单 module `:app` + 独立 `:baselineprofile`（`com.android.test`，`targetProjectPath=":app"`）。`compileSdk 36 / targetSdk 34 / minSdk 24`，versionName 1.2.3（versionCode 10203），16 个 `v*` tag（v1.0.0→v1.2.3）。入口 `MainActivity` → `main/MainContainer`（794 行，路由宿主）→ `ui/**`；播放链 `ui/player/PlayerViewModel` → `playback/PlayerConnection`（986 行）→ `service/PlaybackService`（765 行）。CI 双工作流：`ci.yml`（架构守护 → androidTest/baselineprofile 编译 → 单测）、`release.yml`（`testReleaseUnitTest` → 签名 Release → APK ≤20MB 且禁含 sherpa-onnx 运行时）。git 885 commits（2026-03-10 起）。

## 质量属性评估

### 内部质量指标

| 指标 | 评级 | 证据 |
|------|------|------|
| 可维护性 | 中 | 行数 ratchet（`ci_guard.py:20 SIZE_LIMIT=1500`）+ import 方向守卫 + SCC ratchet 三项自动约束；但 `≥500` 行文件 68 个、5 个 595–996 行单函数不受守卫覆盖；`AlbumDetailViewModel.kt` 2255 行 |
| 可重用性 | 中 | 音效链扩展点良好（`playback/RuntimeAudioProcessor.kt` 7 实现 + `AsmrRenderersFactory` 单注册点）；搜索策略 `SearchQueryPort` + 16 seam 测是好接缝；但 `data/repository` **零接口**（目录内 20 个文件无 interface），伪造实现不可替换 |
| 可移植性 | 好 | `app/build.gradle.kts:16-87` 签名与全部 URL 走 env → Gradle property → 文件三级回退；`gradlew-local.bat`/`gradle-clean-local-cache.bat` 用 `%~dp0` 无机器硬编码，且按 AGENTS.md 纪律不入库 |
| 可集成性 | 中 | CI 完整；Room 已 `exportSchema=true` 且 `app/schemas/31.json` 入库 → 上次的主要集成缺口已闭合；但迁移测试仍是手写 SQL（见 P1-5） |
| 可测试性 | 中 | 1010 用例全绿、Robolectric + MockWebServer + 纯函数 seam 基建完备；分布失衡：`service` 14 主文件 / 2 测试、`ui/calendar`（主文件 1293 行）/ 2 测试、`SubtitleTranslationClient.kt`（1451 行）无同名测试；androidTest 18 文件 CI 只编译不运行 |

### 外部质量指标（运行期属性）

| 指标 | 架构支撑度 | 关键证据 / 场景 |
|------|-----------|----------------|
| 性能 | 强 | `:baselineprofile` 模块 8 场景（`BaselineProfileGenerator.kt`）+ `app/src/main/baseline-prof.txt`/`startup-prof.txt`；Paging3 `pageSize 40 / prefetch 10 / cachedIn`；`@Immutable/@Stable` 34 处；缓存字节预算制；冷启动 `setContent` 前无串行阻塞 IO |
| 可靠性 | 强 | 播放恢复指数退避（`PlaybackServiceAudioFocus.kt:68`）、Range 断点续传（`DownloadWorker.kt:270`）、字幕任务分块检查点 + 翻译重试、下载重试上限 2、睡眠定时器已 DataStore 持久化 |
| 可用性 | 中 | 大屏/横屏适配成体系（`ui/common/core/EaraWindowSize` + 横屏专用布局单测）；短板：弱网/计费网络仅 toast 提示（`PlayerConnection.kt:134`），无降级分支 |
| 安全性 | 强 | Release 校验（`release.yml` APK ≤20MB + 禁 sherpa-onnx so）；签名三级回退；DLsite Cookie AndroidKeyStore AES/GCM（`ValueCipher` seam）；`allowBackup="false"` |
| 易用性（运维） | 中 | 发布流程固定可复现；但无 CHANGELOG（16 个 tag 无变更记录）、无 CONTRIBUTING、无 LICENSE，对外演进无说明 |

### 关键场景支撑度

| 场景 | 支撑度 | 关键证据 |
|------|--------|----------|
| 冷启动/首屏 | 强 | startup-prof + Splash `contentReady` 门控 |
| 大列表滚动 | 强 | Paging3 + `key`/`contentType` + `cachedIn` |
| 播放长稳/中断恢复 | 强 | AudioFocus 全态 + 恢复策略 + 前台通知 + 耳机噪声监听 |
| 弱网/断网 | 中 | Range 续传 + LRU 缓存；无 metered/弱网降级分支 |
| 大屏/多形态 | 强 | `EaraWindowSize` 三族断点 + 横竖屏专用布局 |
| 后台长任务 | 中 | 前台 `dataSync` + wakelock + 可检查点；空载耗电未评估 |
| 持久化/迁移 | 中 | schema 已导出（`31.json`）；迁移测试为手写 SQL、无 `MigrationTestHelper`，v1–v31 全链无回归证 |
| 无障碍 | 弱 | `contentDescription` 散见 51 处；无 TalkBack/自动化断言，androidTest 不运行 |

- **敏感点**：`AlbumDetailModel` 三路加载状态模型（27 字段）、播放焦点/音量状态机、缓存字节预算与 Settings 的耦合
- **权衡点**：单 module（构建简单 ↔ 编译面 541 文件无边界）；缓存字节预算制（内存 ↔ 流量/速度）——均为有意识选择
- **风险决策**：`allowBackup=false` + 无导出路径（换机数据丢失，隐私向取舍）；AI 翻译要求用户自备 DeepSeek Key（可用性门槛未在 Features 言明）

## 需求实现度

- **需求声明来源**：`README.md`（Overview + Features 约 25 条）、`docs/landing_zh.md`、`app/build.gradle.kts`；**无独立需求文档**（无 REQUIREMENTS）

| 声明（分组） | 类型 | 状态 | 证据位置 / 缺口 |
|---|---|---|---|
| Media3 播放 + Compose/M3 UI | 功能 | 已实现 | `PlayerConnection.kt`、`PlaybackService.kt` |
| 响应式三形态（竖/横/平板） | 功能 | 已实现 | `ui/common/core/EaraWindowSize.kt` + 横竖屏布局文件 |
| 本地库管理 / 播放列表 / 分组 | 功能 | 已实现 | `LibraryScreen.kt`、`PlaylistRepository.kt`、`ui/groups/` |
| 在线发现（DLsite / asmr.one）/ 推荐 | 功能 | **部分实现** | `DLSiteScraper.kt`、`AsmrOneApi.kt`；asmr.one 搜索/推荐依赖**外部自建后端**（`AsmrOneAvailabilityApi.kt:222/260/319` "backend is not configured"，默认 `earaasmr.com`，可被 `LISTEN_TOGETHER_BASE_URL` 覆盖）——README 未声明 |
| 歌词（LRC/VTT/SRT）+ 字号 + 悬浮层 | 功能 | 已实现 | `util/SubtitleParser.kt:52-57`、`FloatingLyricsOverlay/View.kt` |
| 设备端字幕生成 + AI 翻译 | 功能 | **部分实现（前置未声明）** | `SubtitleTaskEngine.kt`、`SherpaOnnxRuntime.kt`；翻译需**用户自备 DeepSeek API Key**（`SubtitleTranslationClient.kt:121-129`、`DeepSeekApiKeyStore.kt`），README 仅说"模型按需下载" |
| 音效链 / 双声道频谱 / 切片 A–B 循环 | 功能 | 已实现 | `RuntimeAudioProcessor` 7 实现、`StereoFftAnalyzer`、`SliceLoopEngine` |
| 后台下载 / 离线 / 收听面板 / 一起听 | 功能 | 已实现 | `DownloadWorker.kt`、`ListeningCalendarScreen.kt`、`ListenTogetherRepository.kt` |
| 封面取色 + OLED 防烧屏 | 功能 | 已实现 | `DominantColor.kt`、`OledBurnInProtection.kt` |
| 视频播放（暂不支持 m3u8/HLS） | 功能 | 已实现且声明一致 | `VideoPlaybackSupport.kt`；`PlayerConnection.kt:292-293` 拒绝 m3u8；依赖无 `media3-exoplayer-hls` |
| 睡眠定时 / 通知后台控制 / 应用内更新 | 功能 | 已实现 | `SleepTimerManager.kt`、`PlaybackService.kt`、`UpdateRepository.kt` |
| Baseline Profile / 大屏适配（非功能） | 非功能 | 已实现（无量化指标） | 8 场景采集；无目标帧率/启动耗时数值锚点 |
| 无障碍（非功能） | 非功能 | 未声明、无断言 | 仅散见 `contentDescription`；无测试 |

- **双向脱节信号**：①实现未声明——**网页翻译**（`translation/PageTranslationClient.kt`、`docs/page-translation.md`）与**云同步选择**（`CloudSyncSelectionDialog.kt`）均未进 README Features；②声明与实现相反——**未发现**（上次的 m3u8 已修）；③全仓 `TODO/FIXME/HACK/XXX` = 0（机械扫描 + 抽验属实）

## P0 结构性问题

### P0-1 包级巨型循环依赖：62 包中 40 包同处一个强连通团

- **证据**：`tools/package-scc-baseline.txt`（`max_scc_size=40`，注释自述"R3-C9 时点实测收缩为 40 包"）；`python tools/ci_guard.py` 通过即意味着当前 SCC 恰好 40。团内包含 `com.asmr.player`（根包）、`main`、`ui.*`、`playback`、`service`、`data.*`、`subtitle`、`work`、`cache`，即**四层全部互相可达**。可见的 2-环：根包↔`main`/`ui.library`/`ui.search`、`subtitle`↔根包、`ui.theme`↔`ui.common.cover`、`ui.library`↔`ui.library.albumdetail`、`data.download`↔`data.remote.download`
- **独立复算的演化趋势**（`git grep -c "@Test"` / `git ls-tree -r -l` / `git show <tag>:tools/...`，本次主会话实测）：

  | ref | @Test | main .kt 文件 | import baseline | SCC |
  |---|---|---|---|---|
  | `refactor/start` | 840 | 423 | 无 | — |
  | `refactor/phase-3` | 880 | 431 | 5 | — |
  | `refactor-r2/phase-C` | 938 | 467 | 196 | — |
  | `refactor-r3/phase-A` | 945 | 468 | 344 | 50 |
  | `refactor-r3/phase-B` | 945 | 489 | 205 | 41 |
  | `refactor-r3/phase-C` | 1010 | 541 | 205 | 40 |
  | HEAD | 1010 | 541 | 205 | 40 |

  即 R2 期间耦合是**净增长**（SCC 41→50、main 文件 423→467），R3 才回落至 40；40/62 = 65% 的包仍在团内，绝对规模只比起点少 1 个包
- **分析**：与 `docs/ARCHITECTURE.md` §2 声明的单向分层（`ui → (playback,data,domain)`；`playback → (data,domain)`；`service → (playback,data)`；`data → (domain,util)`）相抵。环内包无法独立编译/替换/测试，任何下层改动可反向波及 ui——这是"账面分层、实质环状耦合"。对照架构检查清单"循环依赖"
- **改进方案**：目标把 40 包团拆为 `ui/feature` ↔ `data` ↔ `domain` 三层无环。(1) 先破"根包 ↔ 各层"这组枢纽环（`com.asmr.player` 顶层常量/扩展函数下移 `util`/`domain`）；(2) 再逐对切 2-环，`subtitle ↔ 根包` 与 `ui.theme ↔ ui.common.cover` 是低成本起点；(3) 每收缩一步同步下调 `max_scc_size`；(4) 给 ratchet 加历史单调断言（见 P1-7）

### P0-2 UI/主包反层穿透 149 处（已冻结在 baseline，未偿还）

- **证据**：`tools/import-direction-baseline.txt` 共 205 条，其中 UI/main 侧 203 条 / 63 个文件；主会话按规则归类实测：

  | 类别 | 条数 | 典型实例 |
  |---|---|---|
  | `ui → data.remote.*` | 68 | `ui/library/AlbumDetailViewModel.kt`（单文件 27 条，全仓最高） |
  | `ui` 特征间横穿 | 54 | `ui/hotlistening/HotListeningScreen.kt`→`ui.library/ui.playlists/ui.groups`（8 条） |
  | `ui → data.local.db.*`（含 DAO/实体） | 51 | `ui/downloads/DownloadsViewModel.kt:5-7,22` 直引 `AlbumDao/DownloadDao/TrackDao/AppDatabaseProvider`；`ui/player/PlayerViewModel.kt:12` 注入 `TrackDao` |
  | `ui → cache/work` | 8 | `ui/common/cover/ImageCacheBridge.kt:2-3` |
  | `ui → data.local.datastore` | 4 | `main/ClipboardRjNavigation.kt:20`、`main/MainContainer.kt:73` |
  | `ui → net-stack`（okhttp3/retrofit2） | 3 | `ui/library/LibraryCloudSyncStateHolder.kt:9-10` |
  | `ui → service` | 2 | `ui/common/audio/AudioOutputRouteUi.kt:1` |
  | `main → *`（datastore/service/db） | 13 | `main/MainContainerRuntime.kt:33,36` |
  | `data → 上层`（反向倒挂） | 2 | `data/remote/api/AsmrOneAvailabilityApi.kt:9`→`listentogether.XxHash64`；`data/settings/SettingsRepository.kt:8`→`hotlistening.HotListeningSortMode` |

- **分析**：UI 直接绑定 ORM/DAO/HTTP 设施细节，替换持久化需改 UI；反向倒挂使 `data` 的入参类型被特征业务语义污染（`ARCHITECTURE.md` §2 亦称"已知穿透"）。对照架构检查清单"分层被穿透"。**注意**：相比 2026-10-04（ui→db 110 处）已收敛过半，剩余属"明确冻结的存量欠账"，而非新违规——但仍是 P0 级的结构性事实
- **改进方案**：按 repository 出口逐包收口（`LibraryReadRepository`/`LibraryWriteRepository` 已是可复用样例）；实体类替换为 `domain.model` 投影；`main` 对 `SettingsDataStore`/`PlaybackService` 引入窄端口；`XxHash64` 迁 `util`，`HotListeningSortMode` 迁 `domain.model`。baseline 只许减，且加增长检测

### P0-3 单函数 595–996 行的 God Composable，且不受现有守卫约束

- **证据**（本次主会话逐行核实起止）：

  | 函数 | 位置 | 行数 |
  |---|---|---|
  | `LibraryScreenContent` | `ui/library/LibraryScreen.kt:219-1122` | **904** |
  | `AlbumDetailScreen` | `ui/library/AlbumDetailScreen.kt:106-1102` | **约 997** |
  | `MainContainer` | `main/MainContainer.kt:88-796` | 708 |
  | `NowPlayingScreen` | `ui/player/NowPlayingScreen.kt:146-836` | 690 |
  | `SearchScreenContent` | `ui/search/SearchScreen.kt:265-860` | 595 |

  形参个数：`NowPlayingScreen` **28**、`MainContainer` 22、`AlbumDetailScreen` 20、`SearchScreenContent` 19（对照坏味道清单"长参数列表/数据泥团"）。`ci_guard.py:20` 的 `SIZE_LIMIT` 是**文件级**阈值 1500，对这 5 个 600–1000 行函数**完全不触发**——守卫存在覆盖盲区
- **分析**：God Component 与 git 热点高度重合（该 5 文件变更频次 70–131 次，全仓 TOP7 中占 4 席），"高频变更 ∩ 巨型函数"是回归风险最集中处。对照坏味道清单"上帝类/超长方法"
- **改进方案**：沿用 R3-C4 已验证的区块化手法（把 `remember` 状态以 `MutableState` 注入、抽 header/content/dialog-host 子 Composable），优先级按热点排序：`LibraryScreenContent` → `AlbumDetailScreen` → `NowPlayingScreen`；长参数列表收敛为单一不可变 state 对象。**同时**给守卫补一条"单函数行数 ratchet"（阈值 ~200 行 + baseline），否则拆完仍会回涨

## P1 维护性问题

### P1-1 `data/repository` 零接口，DIP 接缝名存实亡
- **证据**：`app/src/main/java/com/asmr/player/data/repository/` 20 个文件（主会话目录枚举 + 正则扫描）**无任何 `interface`**；`LibraryReadRepository.kt:32`、`LibraryWriteRepository.kt:33`、`OnlineContentRepository.kt:75`、`SearchRepository.kt:30`、`DownloadQueueRepository.kt:29`、`UpdateRepository.kt:40` 均为 `class`，被 VM 构造直接注入
- **分析**：R2/R3 抽 repository 的本意是建依赖倒置接缝，但无接口意味着实现不可替换、无法低成本伪造，测试只能跑真实 Room/网络（对照架构检查清单"依赖倒置"）
- **改进方案**：为库读写 / 在线内容 / 搜索抽最小接口（只暴露调用面），`@Binds` 绑定现存类为唯一实现；接口粒度按消费者（VM）需要裁剪，禁止把内部方法全暴露

### P1-2 「优先非空合并」同一语义 ≥3 套习语 + 魔法 URL 29 处
- **证据**：合并习语——`data/remote/download/DownloadWorker.kt:95-125`（`ifBlank` 8 字段）、`data/repository/LibraryOnlineSaveSupport.kt:49-61`（`takeIf{isNotBlank()} ?:` 11 字段）、`data/repository/OnlineContentRepository.kt:431-436`、`main/MainContainerSupport.kt:390-391`；魔法串——`play.dlsite.com` **29 处 / 8 文件**（主会话实测：`DlsitePlayLibraryClient.kt` 9、`DlsiteLoginClient.kt` 7、`DlsitePlayWorkClient.kt` 4、`DownloadWorker.kt` 3、`LyricsLoader.kt` 3、`OnlineContentRepository.kt`、`AlbumDetailViewModel.kt`、`DlsiteLoginScreen.kt` 各 1）
- **分析**：复制粘贴式演化，规则变更需多点同步（对照坏味道清单"重复代码"）
- **改进方案**：抽 `fun <T> preferNonBlank(old, new)` 或字段合并模板；URL/Referer 收敛为 `DlsiteEndpoints` 常量族

### P1-3 已确认的两处死代码仍未清理
- **证据**：主会话 grep 全仓，`LibraryActionItem`（`ui/library/LibraryScreen.kt:144`）与 `TaskProgressMeta`（`ui/downloads/DownloadTaskCards.kt:556`）均**只命中各自定义行**，零引用；`ARCHITECTURE.md` §7.4 已自认"待清死码"但未执行
- **分析**：死码占据两个热点文件，且 `LibraryScreen.kt` 已是 P0-3 待拆对象（对照坏味道清单"死代码"）
- **改进方案**：直接删除，同步收缩相关基线

### P1-4 `ensureAlbumCoverSaved` 双实现行为分叉（决策待定未清账）
- **证据**：`ui/library/LibraryCloudSyncStateHolder.kt:333-434`（2048 采样 / `ARGB_8888` / 640 缩略图 / 仅网络来源）与 `data/repository/OnlineContentRepository.kt:458-620`（1280 采样 / `RGB_565` / 支持本地 `content://` 来源）两份约 100 行近似逻辑并存；两者都调用统一的 `util/CoverSupport.kt:15 centerCropSquare`，但采样与色彩配置不同
- **分析**：`ARCHITECTURE.md:177` 记为"保留现状、仅记录"（统一属行为变更，需用户决策）；但两份并存会持续漂移，且 UI holder 内嵌了 OkHttp 下载 + Bitmap 编解码（把网络/图像细节带进了 UI 层，与 P0-2 同源）
- **改进方案**：由用户决策后以 repo 版为唯一实现（含本地来源能力），删 UI 版并补回归测试；短期至少在 `behavior-notes/` 立案（当前只有代码注释级记录）

### P1-5 测试分布失衡 + androidTest 只编译不运行 + 迁移测试无框架
- **证据**：`service` 14 主文件 / 2 测试；`ui/calendar`（主文件 1293 行）/ 2 测试；`SubtitleTranslationClient.kt` 1451 行无同名测试；零直测包含 `data.local.db.entities`(27 文件)、`data.local.db.dao`(22)、`domain.model`(16)。`.github/workflows/ci.yml:37` 仅 `:app:assembleDebugAndroidTest`（编译），18 个 androidTest 从不执行。`app/schemas/` 仅 `31.json`，迁移测试为手写 SQL，无 `MigrationTestHelper`
- **分析**：播放服务与迁移是"最易回归且最难人工验证"的两块，恰是自动化最薄处（对照质量属性"可测试性"）
- **改进方案**：补 `service` 层 Robolectric 关键路径测试（前台服务/恢复策略/定时器）；引入 `androidx.room:room-testing` + 保留历史 schema，用 `MigrationTestHelper` 替换手写 SQL；androidTest 至少跑一条多形态冒烟（emulator 或真机矩阵）

### P1-6 ARCHITECTURE §3 家族表刚同步即漏 4 文件、6 处行数漂移
- **证据**（主会话磁盘实测，口径 `(Get-Content file).Count` / python 计数）：
  - §3 表列 23 行、声称合计"约 13 782 行"；实际家族 **27 个 .kt / 15 091 行**——漏 `AlbumDlsiteTabPlaceholders.kt`(412) / `AlbumDlsitePlayTab.kt`(348) / `AlbumDlsiteTabMotion.kt`(283) / `AlbumDlsiteTabEmptyStates.kt`(266)，合计 **1 309 行未入表**
  - 正文称"除前两个位于 `ui/library/` 外其余在 `albumdetail/`"——实际 `AlbumDetailDialogHosts.kt` 与 `AlbumDetailHeroScrollConnection.kt` 也在 `ui/library/`（应为"前四个"）
  - 行数漂移 6 处：`AlbumDetailReducers` 462→**497**、`AlbumDetailScreenSupport` 398→**447**、`AlbumDetailDlsiteTabs` 616→**626**、`AlbumDetailLocalTab` 579→**588**、`AlbumDetailDialogHosts` 246→**258**、`AlbumDetailHeroScrollConnection` 178→**192**（均为文档低于实测，疑似行数口径不同）
  - 一致项（抽样）：`PlayerConnection` 985/986、`PlaybackService` 765/765、`SearchScreen` 1517/1516、测试基线 1010/1010、playback 文件数 31、顶层包 16、`ui.common` 子包 7
- **分析**：HEAD `e8a58a4` 正是一笔"ARCHITECTURE §3 家族表按磁盘实测回填"的提交，落地后即不准确——说明同步是人工一次性动作、无自动校验。对照文档清单"过期架构文档比没有更糟"
- **改进方案**：补 4 行 + 改"前四个" + 重算合计；把"文档行数与磁盘一致性"纳入 `ci_guard.py`（ARCHITECTURE 中形如 `path: 数字` 的行可脚本校验），从一次性人工回填变为持续约束

### P1-7 守护 ratchet 存在可绕过面
- **证据**：`ci_guard.py` 的 `check_scc`（约 :323）只判 `current > limit` 则失败，**无单调性/历史断言**——把 `max_scc_size` 调大即永不失败；import baseline 有失效条目检测（`check_dead_entries`）但**无"条目增长"检测**，新违规可与 baseline 同一次提交写入而通过 CI；`match_imports` 仅匹配 `import ` 行，**全限定名内联引用（`com.asmr.player.x.y()` 直接写）完全绕过**（当前扫描未见内联实例，属潜在漏洞）
- **分析**：ratchet 的价值在于"只减不增"，一旦上界可被单方面抬高，约束退化为自证（对照度量清单"度量被绕过"）
- **改进方案**：将 `max_scc_size` / size pin / import baseline 三项写入"历史记录文件"（含 `git log` 可验的上界序列）并在守护中断言非增；`match_imports` 增加 FQN 字面量扫描

### P1-8 运行时可用性与无障碍缺口
- **证据**：弱网/计费网络仅 toast（`playback/PlayerConnection.kt:134` `NetworkMeteredChecker`），无 metered 阻断或降级；无障碍仅 51 处 `contentDescription`、无任何自动化断言（androidTest 不运行）；`AndroidManifest.xml:13` 声明 `SYSTEM_ALERT_WINDOW` 无用途说明
- **改进方案**：metered 时降预加载/提示"仅 Wi-Fi 下载"开关；补 TalkBack 冒烟；为敏感权限补注释说明必要性

## P2 改进项

1. **benchmark harness 混入 main**：`app/src/main/java/com/asmr/player/benchmark/` 4 个 .kt + `AndroidManifest.xml:59-60` 声明 `BenchmarkHarnessActivity`（以 `res/values/benchmark_harness.xml` 的 `benchmark_harness_enabled=false` 运行期禁用）；建议移入 `debug`/专用源集，main 只留接口
2. **宽目录**：`util` 36 / `subtitle` 33 / `ui/player` 32 / `playback` 31（>30 阈值）→ 按职责子包化；`util` 混装位图、路径、网络文案
3. **单文件小包**：`ui/splash`、`ui/drawer`、`ui/update`、`performance` 各仅 1 文件；`domain/` 下只有 `model/`（单链冗余层级）
4. **`docs/` 平铺无索引**：根目录 11 份文档与 4 份历史体检报告、3 份 refactor-plan 并列，无 `docs/README.md`；`page-translation.md`、`multiline-subtitles.md` **无任何入口链接**（孤儿文档）
5. **根文档三缺**：无 `LICENSE` / `CHANGELOG` / `CONTRIBUTING`；README 自述"100% AI 生成"却无权利声明；16 个 `v*` tag 无对外变更记录
6. **`tutorial.md` 游离版本控制**：被 `.git/info/exclude:9` 排除；行 23 仍称"最大文件 4039→1415 行"，与现状不符
7. **`SubtitleTranslationClient.kt` 单文件 1451 行含 28 个顶层函数**（主会话计数），混杂请求构建（`build*`）、工具定义（`subtitlePolishTools`/`subtitleTranslationTools`）、响应解析（`parse*`）、system prompt → 按三职责拆文件
8. **空 catch**：`data/repository/OnlineContentRepository.kt:505-506` `catch (_: Exception) {}` 无日志无上抛（全仓仅此 1 处）→ 至少 `Log.w` 或返回 `Result`
9. **中段文件膨胀**：`≥1000` 行 22→13（好），但 `≥500` 行 50→68（+36%）而总行数几乎持平（约 111k→117k）——拆分制造了大量 500+ 中文件，需观察是否只是把大文件摊薄
10. **零直测包**：`db.entities`(27)、`db.dao`(22)、`domain.model`(16)、`ui.settings`(8)、`ui.common.reorderable`(8) 无测试文件
11. **构建未开 configuration cache**（仅 `org.gradle.caching=true`，且 `workers.max=1` / `parallel=false` 本机配置）

## 技术债清单

| 类型 | 债务项 | 证据位置 | 优先级 | 建议偿还时机 |
|------|--------|----------|--------|--------------|
| 设计债 | 包级巨型循环团（SCC=40/62） | `tools/package-scc-baseline.txt` | P0 | 专项治理第一阶段，先破根包枢纽环 |
| 设计债 | UI/main 反层穿透 149 处（冻结未还） | `tools/import-direction-baseline.txt` | P0 | 按 repository 出口逐包收口 |
| 代码债 | 5 个 595–996 行 God Composable（无守卫） | `LibraryScreen.kt:219-1122` 等 | P0 | 随迭代拆分，先热点；同时补单函数 ratchet |
| 设计债 | `data/repository` 零接口 | `data/repository/` 全目录 | P1 | 下个版本窗口 |
| 设计债 | `ensureAlbumCoverSaved` 双实现分叉 | `LibraryCloudSyncStateHolder.kt:333-434` / `OnlineContentRepository.kt:458-620` | P1 | 待用户决策 |
| 设计债 | benchmark harness 入 main 生产包 | `app/src/main/.../benchmark/` + `AndroidManifest.xml:59` | P2 | 随手 |
| 代码债 | 合并习语 ≥3 套、魔法 URL 29 处 / 8 文件、空 catch 1 处 | P1-2 / P2-8 清单 | P1/P2 | 即期（小时级） |
| 代码债 | 死码 2 处 | `LibraryScreen.kt:144`、`DownloadTaskCards.kt:556` | P1 | 即期 |
| 代码债 | `SubtitleTranslationClient.kt` 28 顶层函数混职责 | `subtitle/SubtitleTranslationClient.kt` | P2 | 随字幕迭代 |
| 测试债 | service/ui.calendar/translation 覆盖缺口；androidTest 只编译不运行；迁移无 `MigrationTestHelper` | P1-5 清单 | P1 | 第二阶段 |
| 文档债 | ARCHITECTURE §3 漏 4 文件 + 6 处行数漂移 | `docs/ARCHITECTURE.md:65-91` | P1 | 即期（Quick Win）+ 入守护 |
| 文档债 | 缺 LICENSE / CHANGELOG / CONTRIBUTING；docs 无索引；2 篇孤儿子系统文档；tutorial.md 游离陈旧 | 根目录 / `docs/` | P1 | 即期（Quick Win） |
| 文档债 | 需求声明缺前置：DeepSeek Key、asmr.one 自建后端、网页翻译/云同步未声明 | `README.md` Features | P1 | 即期（Quick Win） |

## Quick Wins

1. **删两处死代码**（`LibraryScreen.kt:144` `LibraryActionItem`、`DownloadTaskCards.kt:556` `TaskProgressMeta`）：零引用已复核，删除即减少两个热点文件噪音，并顺带收缩基线
2. **ARCHITECTURE §3 家族表按磁盘回填**：补 4 行（+1309 行）、改"前四个"、重算合计 15 091、修正 6 处行数——文档刚同步即错，成本极低但收益直接（防"文档比没有更糟"）
3. **补 LICENSE + 最小 CHANGELOG**：仓库对外分发签名 APK 却无权利声明，16 个 tag 无变更记录；这两项是纯新增文件、零代码风险
4. **README 补三处前置声明**：AI 翻译需自备 DeepSeek Key、asmr.one 搜索/推荐依赖可配置后端、网页翻译与云同步为已实现但未列出的能力——消除"按图索骥却不可用"的信任损伤
5. **给守护加两条断言**：`max_scc_size`/size pin/import baseline 三项上界非增 + `match_imports` 增扫 FQN 内联——把"可被单方面抬高的上界"变成真实约束，改动局限在 `tools/ci_guard.py`

## 改进路线图

- **第一阶段 — 消除 P0**：①破包级巨型环（先破根包 ↔ 各层枢纽环，再逐对切 2-环，同步下调 `max_scc_size`）；②UI/main 反层穿透按 repository 出口逐包收口（实体→`domain.model` 投影、`main` 引窄端口），baseline 只减；③区块化 5 个 God Composable（先 `LibraryScreenContent`），并补"单函数行数 ratchet"防回涨。预期效果：依赖图恢复单向，热点文件变更不再跨层扩散
- **第二阶段 — 治理 P1**：`data/repository` 抽最小接口（`@Binds` 绑定）；清死码与重复合并习语、收敛魔法 URL；补 `service` / 迁移关键测试，引入 `MigrationTestHelper`，androidTest 接一条可运行冒烟；ARCHITECTURE §3 回填并把行数一致性纳入 CI
- **第三阶段 — 优化 P2**：宽目录/小包/`domain` 冗余层级整理；benchmark harness 移出 main；docs 加索引、孤儿文档挂入口；弱网降级、敏感权限说明、无障碍冒烟
- **腐蚀专项（三策略）**：**预防**——ratchet 加上界单调断言 + FQN 内联扫描 + 单函数行数守卫；**修补**——上述分阶段；**最小化**——对"热点 ∩ ≥800 行"文件优先冻结增长再拆解（现有文件级 ratchet 思路延伸至函数级）

## 方法与口径

- **执行方式**：7 个维度独立子代理并行审查（每个对照 project-quality-review 对应检查清单），主会话汇总并对 P0/P1 逐条复核；机械检查（大文件 / TODO / 深目录 / 宽目录 / 文档缺失）与 git 热点作为输入线索，全部结论须回源码确认后才写入
- **主会话独立复核项（20 项）**：import baseline 205 条按规则归类与文件分布、`data/repository` 零接口、`AppDatabase.kt:93 exportSchema=true`、两处死代码零引用、`centerCropSquare` 唯一实现、`play.dlsite.com` 29 处/8 文件、`LibraryScreenContent` 与 `AlbumDetailScreen` 函数起止行、albumdetail 家族 27 文件/15 091 行、benchmark harness 文件与 manifest 门控 bool、`tutorial.md` 被 exclude、ci_guard `SIZE_LIMIT=1500` 与 `check_scc` 逻辑、历史 4 指标（`git grep -c @Test` / `git ls-tree -r -l` / `git show <tag>:tools/*.txt`）等
- **行数口径**：`(Get-Content file).Count` 或 python `sum(1 for _ in open(...))`（含空行）；**未使用** PowerShell `Measure-Object -Line`（会跳过空行，导致低估）
- **测试口径**：`--rerun` 强制实跑（非缓存）；计数取自 `.build_asmr_player_android/app/test-results/testDebugUnitTest/*.xml` 聚合（187 个 XML）
- **子代理单点来源项（未在本次主会话复现）**：包级 SCC 的历史时点值 41/45/50（`refactor-r3/phase-C` 起的 40 已由 baseline 文件直接证实）；"优先非空合并 ≥3 套习语"的逐处语义等价性；共改文件对 TOP 表
- **本次为只读分析（除用户授权的单测实跑外），未修改任何代码**；唯一写入产物即本报告

---
> 评级标准：A 健康可长期演进 | B 有债务但可控 | C 需要专项治理 | D 存在阻碍演进的结构性问题
