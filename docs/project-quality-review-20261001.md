# 项目质量体检报告（2026-10-01）—— 第一次重构（S0–S16）完成后复审

> 体检范围：commit `941206b`（tag `refactor/phase-3` @ `c0dfef1`，S0–S16 重构完成、工作区干净）
> 方法：机械检查脚本 + 5 维度并行审查（架构 / 代码坏味道 / 目录结构 / 度量与演化 / 质量属性与实现度），关键结论由审查者独立复算（python 依赖图复算、grep 复核、git 历史回溯）后写入
> 口径：按用户要求**忽略全部项目文档**、**不运行测试套件**，结论仅来自代码、git 历史与构建配置；文中路径为仓库根相对路径
> 用途：第一次重构后的结构复审，P0-x / P1-x / P2 编号可沿用到后续整改任务与 commit
> 关联：S0–S16 重构的阶段门禁报告位于 `docs/iteration/phase-1|2|3-review.md`（gitignored）；上次全量体检见 [project-quality-review-20260929.md](project-quality-review-20260929.md)

## 总体评级：C — 需要专项治理

工程卫生显著高于同规模项目（177 个单测且零 mock 框架、双 CI、ratchet 守护、KeyStore 凭据加密、baseline profile）；但**分层被系统性穿透、存在多组真实循环依赖、且自建的架构守护在关键规则上实际失效**，结构约束处于"名义存在、实际不受保护"的状态。不构成 D（演进仍在高速发生），但已超出"债务可控"。

| 维度 | 评级 | 摘要 |
|---|---|---|
| 架构设计 | 中偏弱 | UI 直连 DAO/网络（38 文件 186 处）；service→Activity 反向依赖；一级包 13 组双向依赖 |
| 需求实现度（推断） | 好 | 抽查 8 个功能链闭环完整，缺口集中在边界场景（均为 P2） |
| 代码坏味道 | 中 | 单函数 2360 行的巨型 Composable、2 个 God VM、3 份重复逻辑；无 TODO、异常处理整体规范 |
| 目录结构 | 中 | 主体按特性分层合理；3 个目录包名不一致（22 文件）、util 垃圾抽屉、多处近空目录 |
| 度量与演化 | 中偏弱 | 守护三重盲区；变更热点与超大文件重合；无模块边界自动化测试 |
| 文档化 | — | 按用户要求未评估 |
| 质量属性 | 好（外部）/ 中（可维护性） | 缓存预算、profile、加密、metrics 齐备；可靠性配置有缺口 |

## 1. 项目画像

单 Activity Compose 应用（Kotlin 1.9.22 / Compose BOM 2024.02 / Media3 1.8.0 / Room 2.6.1 v31 / Hilt 2.49 / WorkManager / Paging3 / Coil / Retrofit+OkHttp+Gson / Jsoup / pdfbox-android），`:app` + `:baselineprofile` 两个模块，minSdk 24 / target 34，versionName 1.2.3。

| 项 | 值 |
|---|---|
| 规模 | 630 个 .kt：main 431（约 11 万行）/ test 177 / androidTest 18 / baselineprofile 4 |
| 入口 | `app/src/main/java/com/asmr/player/AsmrApp.kt`（Application）、`MainActivity.kt`（NavHost + 全量 Screen 装配）、`service/PlaybackService.kt`（MediaSessionService）、`subtitle/SubtitleTaskService.kt`（前台 dataSync） |
| 测试设施 | 177 单测（Robolectric 4.11.1、mockwebserver、Compose ui-test-junit4 位于 test 源集，零 mockk/Mockito）；androidTest 18（迁移、分页压力、真机链路） |
| CI | `.github/workflows/ci.yml:29-32` 每次 push 跑架构守护 + `testDebugUnitTest`；`release.yml:29-30` 发版前 `testReleaseUnitTest` + APK ≤20MB + 禁含 sherpa .so |
| 构建可移植性 | 入库脚本无本机绝对路径（仅 `docs/devnote` 有本机记录，属预期）；buildDir 重定向可移植（根 `build.gradle.kts:10-18`） |

## 2. 质量属性评估

### 内部质量

| 指标 | 评级 | 证据 |
|---|---|---|
| 可维护性 | 中 | 热点与超大文件重合：10 个 >1500 行文件（`tools/size-guard-baseline.txt`）；专辑详情功能横跨约 14 文件 |
| 可重用性 | 中 | 数据层被 UI 类型污染（`data/lyrics/LyricsLoader.kt:17-18`、`data/settings/SettingsRepository.kt:7-8`），无法独立复用 |
| 可移植性 | 好 | 无 OS/机器路径硬编码；buildDir 重定向可移植 |
| 可集成性 | 中 | 外部端点部分可配置（BuildConfig 注入），但 scraper 域名/接口散落硬编码（`data/remote/scraper/DLSiteScraper.kt:31`） |
| 可测试性 | 好 | 177 单测、零 mock 框架、Robolectric + in-memory Room + mockwebserver；扣分项见 P1-3 |

### 外部质量（标注推断）

| 指标 | 支撑度 | 关键证据 / 差距 |
|---|---|---|
| 性能 | 中 | 支撑：Paging3、图片/Disk 缓存预算、baseline/startup profile、androidx.metrics、刻意关闭媒体位置周期广播（`service/PlaybackService.kt:988-991`，有注释、属有意识权衡）；差距：`onCreate` 主线程 `runBlocking` 读 DataStore（:277）、主 OkHttp 无显式超时（di 目录 grep "timeout" 零命中） |
| 可用性 | 中 | 下载失败无 `Result.retry()`/退避（`data/remote/download/DownloadWorker.kt`，全文仅 failure/success） |
| 可靠性 | 中 | 支撑：DB 迁移链 4→31 连续、有意移除 destructive fallback 保护数据（commit 876bac1；已发布最低版本 DB=18，实测无缺口）；差距：元数据/FTS 失败被静默吞并后上报 success、KeyStore 异常未兜底 |
| 安全性 | 中上 | 支撑：Cookie 走 `KeystoreValueCipher` AES/GCM 落盘、`allowBackup=false` 隐私友好；差距：`exported=true` 的 MediaSessionService 对非通知控制器放行自定义命令（`service/PlaybackService.kt:1003-1012`） |
| 易用性(运维) | 好 | 双 CI + 架构守护 + APK 体积/内容门禁；差距：androidTest 与 baselineprofile 均不进 CI |

### 关键场景与权衡

| 场景 | 重要性/难度 | 当前架构判断 |
|---|---|---|
| 冷启动后立即起播（性能） | H/M | 部分支撑：profile 存在，但服务创建同步等 DataStore，首帧被拖慢 |
| 弱网下载大量曲目（可靠性） | H/M | 不达标：无自动重试/退避，需用户手动 |
| 旧版本升级保留本地库（可靠性） | H/L | 达标：迁移链完整且有意的"不销毁数据"决策 |
| 第三方应用绑定播放服务（安全） | M/L | 部分达标：自定义命令对任意控制器开放 |
| 修改专辑详情/本地库功能（可维护性） | H/H | 高风险：跨 14 文件、3000 行级 God 文件与跨页共改 |

- **敏感点**：`ui.library`（扇出 580）、`ui.player`（139）、playback↔service 边界、`AppDatabaseProvider` 单例。
- **权衡点**：关闭位置周期广播（性能 vs 系统兼容，有意识）；移除 destructive fallback（数据安全 vs 兼容，有意识）；单模块 + 快速迭代（速度 vs 边界强制，有意识但代价已显形）。
- **风险决策（无意识）**：UI 层直接持有 DB 事务与网络编排——无注释/文档痕迹，且守护脚本未覆盖；守护规则失效自身未被发现，说明缺"守护的守护"。

## 3. 需求实现度（从代码推断，无文档声明）

| 推断功能 | 实现证据 | 状态 |
|---|---|---|
| 本地媒体库/目录浏览 | LibraryScreen + LibraryViewModel + LocalTreeCache + SAF | 已实现（闭环） |
| 下载与队列 | DownloadsScreen/VM + DownloadWorker + DownloadQueueCoordinator | 已实现（失败路径弱，见 P2） |
| 字幕识别/翻译 | SubtitleTaskService + `subtitle/*` + 模型按需下载 | 已实现；断网/无密钥时不可用（P2，推断） |
| DLSite 抓取/登录/云端库 | DLSiteScraper + Dlsite{Login,Play}* + DlsiteAuthStore | 已实现（闭环） |
| Listen-together | `listentogether/*` + NowPlaying 集成 | 已实现；后端未配置时静默关闭（P2，推断） |
| 均衡器/音效/悬浮歌词/日历/歌单/分组/热听/更新检查 | `playback/*AudioProcessor`、FloatingLyricsOverlay、ListeningCalendarScreen、playlists、groups、hotlistening、GitHubUpdateClient | 已实现（闭环） |
| 备份/导出/恢复 | 仅字幕 LRC 导出与站点备份；`allowBackup=false` | 缺口（P2，推断） |

双向脱节信号：未发现"声称有但无实现"（也无声明可查）；"实现但无声明"即上表全集，属文档侧问题（本次不评估）。

## 4. 分级问题清单

### P0 结构性问题

#### P0-1 分层被系统性穿透：UI 层直接持有 DAO、事务与网络

- **证据**：`app/src/main/java/com/asmr/player/ui/` 下 38 个文件 `import data.local.db.dao / data.remote` 共 186 处；`withTransaction` 全仓 63 处（11 个源码文件），其中 UI/VM 层 33 处——`ui/library/LibraryViewModel.kt` 19 处、`ui/library/AlbumDetailViewModel.kt` 7 处、`ui/downloads/DownloadsViewModel.kt` 4 处、`ui/player/NowPlayingTagViewModel.kt` 2 处、`ui/library/albumdetail/AlbumDetailViewModelSupport.kt` 1 处。
- **分析**：对照《分层与依赖方向》——业务层直接操作 SQL/HTTP 被明令禁止；Repository 形同虚设，事务边界散落在 ViewModel，无法独立测试与替换数据源。这是本仓最大的一致性反例。
- **改进方案**：把"事务 + DAO + 网络解析"收进 Repository/UseCase（先从 withTransaction 数量最多的两个 VM 切入），ui 包新增 lint/守护规则禁止 import DAO 与 OkHttp/Gson 类型。

#### P0-2 真实循环依赖（多组，含 service→Activity 反向依赖）

- **证据**：`service/PlaybackService.kt:40` 与 `subtitle/SubtitleTaskService.kt:22` 直接 `import com.asmr.player.MainActivity` 构造 Intent，而 ui/main 反向依赖 service 类型，构成 service↔ui/main 环；`playback/PlayerConnection.kt` ↔ `service/PlaybackService.kt`（playback↔service）。独立复算：一级包 16 节点 / 72 条边中 **13 组双向依赖**（data↔ui 2/341、data↔playback 3/13、subtitle↔ui 3/33、translation↔ui 1/17、data↔util 32/1、cache↔data、cache↔playback…）。
- **分析**：对照《模块边界与耦合》——任何依赖环都使环内模块无法独立复用与测试；`data → ui` 方向倒置是数据层无法独立演进的根本原因。
- **改进方案**：Service 用中立方式拉起 App（launcher intent / 专用 alias Activity），不引用具体 Activity；playback 抽 `PlaybackController` 接口由 service 实现；data 侧 5 条存量违规通过模型下沉（`TreeFileType`、`LocalTreeLeafCacheEntry`、`AppVolume` 常量移入 domain/data）清零后收缩 baseline。

#### P0-3 架构守护三重盲区（规则名义存在、实际失效）

- **证据**：① `tools/ci_guard.py:21` 禁止 data 引用 `com.asmr.player.main.`，但全仓**不存在任何 `package com.asmr.player.main`**（grep 零命中，main/ 目录文件声明根包），该规则永不命中；② `:51` 只扫 `/data/` 文件，ui→service、playback→service 等越层全盲；③ `:40-43` 的 size ratchet 只比对路径不比对行数，存量文件可无限膨胀（`ui/library/albumdetail/AlbumDetailDirectorySupport.kt` 已达约 3377 行仍在 baseline 内）。
- **分析**：对照《腐蚀预防》——守护是唯一自动化边界保障（Konsist/ArchUnit 均未引入），其失效意味着 P0-1/P0-2 可无阻碍继续增长。
- **改进方案**：规则改为按目录/实际包名匹配并覆盖 ui/playback/service；size baseline 升级为"路径:行数上限"；CI 增加断言"每条内置规则必须至少能命中一个反例样本"，防止规则再次空转。

#### P0-4 God 组件与变更热点重合

- **证据**：`main/MainContainer.kt:263` 起单个 `fun MainContainer` 延续到文件尾（约 2360 行，文件共约 2700 行）；`ui/player/NowPlayingScreen.kt:1177` 起单函数约 1600 行、`ui/settings/SettingsScreen.kt:167` 起约 1100 行；>1500 行文件 10 个、>2000 行 8 个。git churn Top10 与超大文件高度重合（AlbumDetailScreen 121 次、MainContainer 114 次、SearchScreen 81 次、LibraryScreen 76 次、AlbumDetailViewModel 66 次）。
- **分析**：对照《上帝类 / 超长方法》——修改一个功能需理解整个文件，且这些文件恰是改动最频繁处，维护成本被持续放大。
- **改进方案**：MainContainer 按路由/区块抽子 Composable + 独立状态宿主；两个 God VM（LibraryViewModel 约 82 个方法、AlbumDetailViewModel 约 78 个方法）按"扫描/标签/下载/在线数据"拆；ratchet 上限从 1500 逐步收紧到 800。

### P1 维护性问题

- **P1-1 重复逻辑**：`collectSubtitleCandidates` 存在 3 份定义（`ui/library/albumdetail/AlbumDetailDirectorySupport.kt:1072`、`:1833`、`ui/library/AlbumDetailViewModel.kt:2789`），改一处易漏两处。→ 上提至公共支持类。
- **P1-2 生命周期内 runBlocking**：`service/PlaybackService.kt:277`（onCreate 主线程等 DataStore）与 `:1375`（onDestroy 内 IO flush），与项目其余 `serviceScope.launch` 惯例不一致，有 ANR/卡顿风险。→ 异步化。
- **P1-3 核心 VM/Service 无 seam 测试**：177 个单测中仅 3 个与 VM/Service 同名（`AlbumDetailViewModelSupportTest`、`ListeningCalendarViewModelTest`、`LocalAlbumMergeServiceTest`），两个 God VM、PlaybackService、SubtitleTaskService 本体无用例；测试力气集中在 support/util 函数。
- **P1-4 Scattered Functionality**：专辑操作在 library/search/hotlistening/playlists/groups 5 个特性重复接线；子代理实算共改 Top 对：LibraryScreen+SearchScreen 48 次、HotListening+Search 28 次、AlbumDetailScreen+AlbumDetailViewModel 35 次 → 缺共享抽象。
- **P1-5 DI 双轨**：Hilt（`di/DatabaseModule.kt`）与手工单例（`data/local/db/AppDatabaseProvider.get`、`SubtitleTaskRepository` companion）并存，同库两种取法，生命周期不受 DI 管理。
- **P1-6 测试资产不进 CI**：androidTest（18 文件，含迁移测试、分页压力测试）与 baselineprofile 模块（性能基准）均无 CI 任务，长期存在"写了但从不跑"的腐烂风险。

### P2 改进项

1. `data/remote/download/DownloadWorker.kt` 无 `Result.retry()`/退避；主 OkHttp 无超时配置 —— 弱网可靠性缺口。
2. 元数据/FTS 失败被静默吞并后仍上报 success（DownloadWorker/DownloadManager 多段 `catch(Exception){Log.w}`）。
3. `@Suppress` 21 处 / 16 文件（DEPRECATION/UNCHECKED_CAST 等），多为抑制而非修复；TODO/FIXME 全仓为 0。
4. 目录≠包名 22 文件（albumdetail 12 + nowplaying 5 + main 5，已核实）；既是可读性问题，也是 P0-3 根因。
5. `util/` 21 文件垃圾抽屉（字幕解析、DLSite 工具、网络探测、格式化混杂）；`cache/` 与 data 职责重叠。
6. 近空目录冗余：`ui/splash`、`ui/drawer`、`ui/update`、`work`、`ui/dlsite` 等 1-3 文件目录。
7. DAO 包混入投影 DTO（`LibraryTrackRow`、`TagWithCount` 等）；benchmark 代码（4 文件）编译进 main 源集。
8. vendored `com/k2fsa/sherpa/onnx`（4 文件）与项目包根并列，来源/许可无可追溯标记。
9. 全局可变单例（`playback/StereoSpectrumBus`、`PlaybackMediaCache`、`performance/UiFrameWorkCoordinator` 等 object）跨模块读写。
10. 构建配置：`android.enableJetifier=true` 已无必要；`workers.max=1/parallel=false` 拖慢迭代（可评估放开）。
11. 8 张 PNG 截图入库（`example_screen/`）；`.gitignore` 全局 `*.png`/`*.xml` 白名单式规则易误伤。
12. `tools/import-direction-baseline.txt` 无行号、无整改期限，存量违规"合法化"后不推动偿还。

## 5. 技术债清单

| 类型 | 债务项 | 证据 | 优先级 | 建议偿还时机 |
|---|---|---|---|---|
| 设计债 | UI 直连 DAO/事务/网络；service↔ui/main 与 playback↔service 环；守护三重盲区 | P0-1~P0-3 | 高 | 下一轮专项重构（分三阶段） |
| 设计债 | God 组件 10 个 >1500 行；DI 双轨；端点硬编码 | P0-4、P1-5、P2 | 高 | 与 God 拆分同批，逐文件收缩 baseline |
| 代码债 | 字幕候选逻辑 3 份；生命周期 runBlocking；@Suppress 21 处；全局可变单例 | P1-1、P1-2、P2 | 中 | 随相关文件改动"顺路偿还" |
| 测试债 | God VM/Service 无 seam 用例；androidTest/baselineprofile 不进 CI；`MainContainerSaveableStateTest` 用测试内复制件自测框架行为而非生产 seam | P1-3、P1-6 | 中 | 阶段三前补齐核心 seam 用例 |
| 文档债 | 按用户要求未评估；仅记录工具侧配置债务：守护 baseline 无行数/期限 | P0-3、P2-12 | 低 | 修复守护时一并处理 |

## 6. Quick Wins（低投入高收益）

1. **修 ci_guard 三重盲区**（main 规则改为目录/真实包匹配、size baseline 加行数、加规则自检）——直接恢复唯一的自动化边界防线。
2. **统一 22 个文件的包名与目录**——机械改动，消除 P0-3 根因与 IDE 长期告警。
3. **两处 runBlocking 异步化**（onCreate → serviceScope.launch；onDestroy 落盘改为短超时 IO）。
4. **DownloadWorker 加 `Result.retry` + 退避、主 OkHttp 显式超时**——弱网体验立竿见影。
5. **`collectSubtitleCandidates` 3 → 1**——消灭最明显的复制粘贴债，风险低。

## 7. 改进路线图

- **第一阶段 — 守住底线（消除 P0-3）**：修复守护并扩大到 ui/playback/service 边界规则；baseline 升级为"路径:行数"并加规则自检；同时完成 Quick Wins 2-5。
- **第二阶段 — 破除环与穿透（消除 P0-1/P0-2）**：Service 改用中立启动入口；playback↔service 抽接口；data 的 5 条存量违规下沉模型后清零 baseline；把 withTransaction 最多的两个 VM 的 DB/网络访问收进 Repository，并新增 ui→DAO/OkHttp 的 import 禁令。
- **第三阶段 — 拆 God 与持续治理（消除 P0-4）**：MainContainer/NowPlayingScreen/SettingsScreen 巨型 Composable 按区块拆分；两个 God VM 按职责拆；ratchet 上限 1500→800 逐步收紧；引入 Konsist 类模块边界测试 + 依赖环/超大文件计数的历史快照趋势（腐蚀三策略：最小化=评审清单、预防=守护+边界测试、修补=专项重构+ratchet 收缩）。

---

## 附：复验说明

- 全部 P0/P1 结论均经独立复核：依赖图用 python 对 `import com.asmr.player.*` 复算（一级包 16 节点/72 边、二级包 68 节点/287 边）；`withTransaction`/`MainActivity` 反向依赖/`runBlocking`/`collectSubtitleCandidates`/守卫规则空转均以 grep 直接验证；`MainContainer`、`NowPlayingScreen`、`SettingsScreen` 的函数跨度以行号实测。
- 已剔除两条子代理误报：① "import-direction-baseline 无整改期限/格式不一致"经复查 baseline 5 条格式统一，仅保留"无行号/无期限"；② "DB 迁移缺口（<4 / 11→12）"经 git 回溯证实为非问题（已发布最低 DB=18，迁移链 4→31 连续，11→12 从未发布，destructive fallback 为 commit `876bac1` 有意移除）。
- 本次为纯静态审查，未运行测试；测试健康度结论来自 CI 配置与测试资产清点，运行层证据缺失。