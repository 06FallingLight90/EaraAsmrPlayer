# 项目质量体检报告（2026-10-02）—— 第二轮重构 R2 阶段 A/B 完成后复审

> 体检范围：工作区 HEAD（tag `refactor-r2/phase-B` @ `9afcccb` + 设备走查提交 `96ff938`）
> 体检日期：2026-10-02
> 代码规模：主源集 **435 个 .kt / 111,207 行**；test 181 文件 / **906 个 @Test / 20,721 行**；androidTest 18 / 36；`:baselineprofile` 4
> 口径：**静态层**（用户确认不运行测试套件，测试结论标注"未重跑"）；**本轮纳入文档化维度**（上一轮经用户同意跳过）；已排除 `.build_asmr_player_android/`、`.gradle-user-home/` 等构建产物目录
> 方法：机械检查脚本 + **Kotlin 依赖图独立复算**（官方 `dependency_metrics.py` 不支持 Kotlin，改用内联 python 复算一级/二级包边）+ 5 维度并行子代理审查 + 关键行号逐条 grep/读文件复核
> 关联：[project-quality-review-20261001.md](project-quality-review-20261001.md)（上轮，总评 C）｜[project-quality-review-20260929.md](project-quality-review-20260929.md)｜`.trae/documents/refactor-plan-r2.md`（本轮重构计划）

## 总体评级：C+ — 需要专项治理，但治理路线已在执行且成效可验证

工程卫生仍显著高于同规模项目（906 单测零 mock 框架、双 CI、ratchet 守护 + 规则自检、KeyStore 凭据加密、baseline profile、行为档案机制）。**上一轮的三条 P0 结构性硬伤中，P0-2（反向依赖/依赖环）与 P0-3（守护失效）已实质修复并留下可复算证据**；但 P0-1 只完成了"DAO/事务"一半——**UI 直连网络层 160 处完全不设防且守护零覆盖**，成为当前最大结构洞；P0-4（God 组件）未动，且新增了"ratchet 配额已还却不收紧"的治理机制缺陷。

| 维度 | 评级 | 摘要 |
|---|---|---|
| 架构设计 | 中（C+） | P0-2/P0-3 已修；UI→`data.remote` **160 处 / 27 文件**无守护；特征模块间 3 组真实环；`PlaybackController` 仍空壳 |
| 需求实现度 | 好（B+） | 13 项功能声明全部闭环；A3 的 retry/超时**已落地**，但 5xx 不重试 |
| 代码坏味道 | 中（C+） | 3 个巨型 Composable（单函数 1103–2437 行）+ 2 个 God VM（90/101 方法）未拆；新增 2 份近同构 upsert |
| 目录结构 | 好（A-） | **A2 目录=包名对齐目标 100% 达成**（main+test 全量比对 0 处不一致）；仅剩 util 抽屉、近空目录等 P2 |
| 文档化 | 中偏弱（C） | ARCHITECTURE.md 17 项可验证数字中 **15 项偏差 + 1 项断言失效**；根文档三缺（LICENSE/CHANGELOG/CONTRIBUTING） |
| 度量与演化 | 中（C+） | 守护已生效但**只剩防回潮、失去推动下降**；import baseline 无自净机制；androidTest/baselineprofile 仍不进 CI |
| 质量属性 | 中（C+） | 性能/可移植性/可复用性改善；**安全性回退**：自定义命令对任意控制器开放 |

### 与上一轮（20261001，总评 C）的 delta

**已修复（有证据）**
- **P0-3 守护三重盲区 → 已修**：[ci_guard.py](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/tools/ci_guard.py#L32-L60) 改为按文件真实 `package` 行匹配、全仓扫描、9 条方向规则 + [guard-selftest/](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/tools/guard-selftest) 9 组反例夹具自检；实跑通过。
- **P0-2 反向依赖 → 已清零**：`service/*`、`subtitle/*` 对 `MainActivity` 的 import **0 处**（新守卫规则 `service-to-root-entry`/`subtitle-to-root-entry` 锁死）；`data → playback/ui/main` 违规 **0 条**（B1 模型下沉）；`playback → service` 边 **0 条**。
- **P0-1 的一半 → 已还**：UI 层 `withTransaction` 由 **33 处降到 6 处**（4 处 `ui/downloads`、2 处 `ui/player`）；两个 God VM 构造已无 `database`/`albumDao`/`trackDao`。
- **P1-1 已还**：`collectSubtitleCandidates` 3 份 → 1 份（[AlbumDetailViewModelSupport.kt:849](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/app/src/main/java/com/asmr/player/ui/library/albumdetail/AlbumDetailViewModelSupport.kt#L849)）。
- **A3 修复类变更已落地**：[NetworkModule.kt:137-139](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/app/src/main/java/com/asmr/player/di/NetworkModule.kt#L137-L139) connect 15s / read 30s / write 30s；[DownloadWorker.kt:423-425](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/app/src/main/java/com/asmr/player/data/remote/download/DownloadWorker.kt#L423-L425) 加 `Result.retry()`（上限 2 次）。

**仍开放**：P0-1（网络侧）、P0-4（God 组件）、P1-3（seam 测试）、P1-4（Scattered Functionality）、P1-6（androidTest 不进 CI）、大部分 P2。

**新增/新发现**：UI→`data.remote` 160 处；特征模块间 3 组新识别真实环；**size ratchet 松弛 805 行**（2 文件）；import baseline 无自净；ARCHITECTURE.md 系统性过期；安全命令无鉴权。

**本轮更正（主会话自查）**：初判"size-guard-baseline 11 条全部未收紧"**不成立**——用 `readlines()` 口径复算后，11 条中 **9 条 cap 精确等于实测**，仅 2 条有松弛（见 P0-3）。原因是我先用了去空行的 `Measure-Object -Line` 口径，与守卫的原始行数口径不可比。另初判"根包被 109 处 import"**表述有误**：109 是根包 3 个文件对外扇出边数，实际**仅 1 处** import 根包类型（[ImagePreviewDialog.kt:78](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/app/src/main/java/com/asmr/player/ui/common/cover/ImagePreviewDialog.kt#L78) 引 `AsmrApp`）。

## 项目画像

单 Activity Compose 应用：Kotlin 1.9.22 / Compose BOM 2024.02 / Media3 1.8.0 / Room 2.6.1（DB v31，26 条迁移）/ Hilt 2.49 / WorkManager / Paging3 / Coil / Retrofit+OkHttp+Gson / Jsoup / pdfbox-android；`:app` + `:baselineprofile` 两模块，minSdk 24 / target 34，versionName 1.2.3。

| 项 | 值 |
|---|---|
| 规模 | 762 提交 / 7 个月，+403,373 / −147,641 行；当前 main 435 文件 / 111,207 行（均值 253 行）；`ui` 占 62.7% |
| 入口 | [AsmrApp.kt](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/app/src/main/java/com/asmr/player/AsmrApp.kt)（Application）、[MainActivity.kt](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/app/src/main/java/com/asmr/player/MainActivity.kt)（NavHost 装配）、[PlaybackService.kt](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/app/src/main/java/com/asmr/player/service/PlaybackService.kt)（MediaSessionService）、[SubtitleTaskService.kt](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/app/src/main/java/com/asmr/player/subtitle/SubtitleTaskService.kt)（前台 dataSync） |
| 测试设施 | 906 单测（Robolectric 4.11.1、mockwebserver、Compose ui-test 位于 test 源集，零 mockk/Mockito）；androidTest 18 文件 36 用例；无 flaky 重试插件 |
| CI | [ci.yml](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/.github/workflows/ci.yml)：`ci_guard.py` + `testDebugUnitTest`；[release.yml](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/.github/workflows/release.yml)：`testReleaseUnitTest` + APK ≤20MB + 禁含 sherpa .so |
| 测试健康度 | **未重跑**（用户选静态层）。静态层：906 @Test / 181 文件；最近一次门禁记录 906 通过 / 0 失败 / 4 跳过（2026-10-02） |

## 质量属性评估

### 内部质量指标

| 指标 | 评级 | 证据 |
|---|---|---|
| 可维护性 | 中 | 单函数 1103–2437 行的巨型 Composable + 90/101 方法的 God VM 未拆；churn Top20 与超大文件仍高度重合 |
| 可重用性 | 中上 | `LibraryWriteRepository` 把平台 IO 全量注入（`fileSizeQuery`/`isMissing`/`canonicalUrl`，[LibraryWriteRepository.kt](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/app/src/main/java/com/asmr/player/data/repository/LibraryWriteRepository.kt)）；但 `LibraryReadRepository` 自认"均为透传"（[LibraryReadRepository.kt:30-73](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/app/src/main/java/com/asmr/player/data/repository/LibraryReadRepository.kt#L30-L73)） |
| 可移植性 | 好 | `data/` 层已无 `ui`/`playback`/`main`/`service` import；本机绝对路径 8 处全部只在 [2026-09-29-env-setup.md](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/docs/devnote/2026-09-29-env-setup.md)，入库构建脚本 0 处污染 |
| 可集成性 | 中上 | MediaSession 契约清晰；镜像接口统一为 `AsmrWorkApi`；但 `ui` 直连 `data.remote` 具体实现类，外部端点替换成本高 |
| 可测试性 | 中 | 906 单测、8.1 test/KLOC；但核心编排类（Service/两个 God VM/4 个 ViewModel）无 seam 用例 |

### 外部质量指标（标注推断）

| 指标 | 支撑度 | 关键证据 / 差距 |
|---|---|---|
| 性能 | 中 | 支撑：baseline/startup profile 齐备、字节预算缓存、刻意关闭位置周期广播（有注释）。差距：[PlaybackService.kt:279](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/app/src/main/java/com/asmr/player/service/PlaybackService.kt#L279) `onCreate` 仍 `runBlocking` 读 DataStore（仅加了 2s 超时，**未按计划异步化**）；`:1382` `onDestroy` 仍 `runBlocking(Dispatchers.IO)`；`androidx.metrics` 有依赖**零消费方** |
| 可用性 | 中 | retry 已落地但只覆盖 `IOException`（[DownloadWorker.kt:423](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/app/src/main/java/com/asmr/player/data/remote/download/DownloadWorker.kt#L423)）；HTTP 5xx 直接终态失败；`setBackoffCriteria` 仅 [SubtitleModelRepository.kt:191](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/app/src/main/java/com/asmr/player/subtitle/SubtitleModelRepository.kt#L191) 一处，下载链未显式设置 |
| 可靠性 | 中 | 支撑：26 条迁移链、有意不用 destructive fallback。差距：finalize 失败被吞并后仍报 success（[DownloadWorker.kt:564-567](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/app/src/main/java/com/asmr/player/data/remote/download/DownloadWorker.kt#L564-L567)）；迁移测试仅覆盖 `30→31` |
| 安全性 | **中偏弱（回退）** | 支撑：Cookie AES/GCM、`allowBackup=false`。差距：`exported="true"` 的 PlaybackService 对**任意非通知控制器**下发 4 条自定义命令（见 P0-4） |
| 易用性(运维) | 中上 | 双 CI + ratchet + 规则自检 + APK 体积/内容门禁；差距：androidTest 与 baselineprofile 不进 CI |

### 关键场景与权衡

| 场景 | 重要性 | 难度 | 当前架构判断 |
|---|---|---|---|
| 冷启动到起播 | H | M | 部分支撑：profile 有，Service `onCreate` 同步等 DataStore 未解 |
| 弱网批量下载 | H | M | 部分达标：连接层重试上限 2 次；5xx 不重试 |
| 在线专辑保存入库（事务一致性） | H | L | **达标**：已下沉 `LibraryWriteRepository` 且有 seam 测试钉住 |
| 旧版本升级保留本地库 | M | M | 达标：迁移链完整 |
| 第三方 App 绑定播放服务 | M | L | **不达标**：自定义命令无包名白名单 |
| 修改专辑详情/本地库功能 | H | H | 高风险：跨 14 文件、单函数 1273 行、`AlbumDetailViewModel` 90 方法 |
| 新增一个在线内容源 | M | M | 中：改动面 6–8 文件（ARCHITECTURE §5 已评估，未招接口） |

- **敏感点**：`ui.library`（扇出 586）、`main`（302）、`data.repository`（66）、`ui.common`（扇入 417）、`data.local`（扇入 268）、`AppDatabaseProvider` 单例（44 处引用）。
- **权衡点（有意识）**：关闭位置周期广播（性能 vs 系统兼容）、不用 destructive fallback（数据安全 vs 兼容）、单 module 快速迭代（速度 vs 边界强制）、不引入 Konsist（成本 vs 收益，**本轮评估建议见 P1-9**）。
- **风险决策（无意识）**：UI 直连 `data.remote` 具体实现类 160 处——无注释/文档痕迹、守护未覆盖，属"未被察觉的架构漂移"。

## 需求实现度

- 需求声明来源：`README.md`、`docs/ARCHITECTURE.md`、`docs/page-translation.md`、`docs/multiline-subtitles.md`、`docs/behavior-notes/`、`.github/workflows/release.yml`（无正式需求文档，功能声明均来自 README/架构文档）

| 声明（来源） | 类型 | 状态 | 证据位置 / 缺口 |
|---|---|---|---|
| 本地库管理/目录浏览（README） | 功能 | 已实现 | `ui/library/`、`LocalTreeCache`、SAF |
| 后台下载与离线播放（README） | 功能 | 已实现 | `data/remote/download/`、`DownloadWorker` |
| 设备端字幕生成 + AI 翻译（README） | 功能 | 已实现 | `subtitle/{SherpaOnnxRuntime,ParakeetEngine,SubtitleModelRepository}`、`translation/` |
| DLsite 抓取与登录（README） | 功能 | 已实现 | `data/remote/scraper/DLSiteScraper`、`auth/KeystoreValueCipher` |
| asmr.one 在线浏览（README） | 功能 | 已实现 | `crawler/AsmrOneCrawler`、`api/AsmrOneApi|AsmrMirrorApi` |
| 一起听 / 在线人数 | 功能 | 已实现 | `listentogether/`（未配置后端时静默关闭） |
| 音效链（ARCHITECTURE §4） | 功能 | 已实现 | 7 个 `*AudioProcessor` + 单一注册点 `AsmrRenderersFactory` |
| 悬浮/多行歌词（multiline-subtitles.md） | 功能 | 已实现 | `service/FloatingLyricsView|Overlay` |
| 收听日历/收听面板（README） | 功能 | 已实现 | `ui/calendar/`、`StatisticsRepository` |
| 歌单 / 分组（README） | 功能 | 已实现 | `PlaylistRepository`、`AlbumGroupRepository` |
| 热听（README） | 功能 | 已实现 | `hotlistening/`、`ui/hotlistening/` |
| 应用内更新（README） | 功能 | 已实现 | `GitHubUpdateClient`、`ui/update/` |
| 备份 / 导出 / 恢复 | 功能 | 未实现 | 无用户级备份；`allowBackup="false"`；**README 未声明，不计为承诺缺口** |
| APK ≤20MB 且不含 sherpa 运行时（release.yml） | 非功能 | 已实现 | `release.yml` 强制校验 |
| 字幕模型按需下载 | 非功能 | 已实现 | `app/build.gradle.kts` + `SubtitleModelRepository` |
| Cookie 加密落盘 | 非功能 | 已实现 | `KeystoreValueCipher` AES/GCM + 明文惰性迁移 |
| 网络超时与下载重试 | 非功能 | 部分实现 | 已配超时 + `IOException` 重试；5xx 不重试（P1-2） |

- 双向脱节信号：**未发现**"声称有但无实现"；"实现但无声明"集中在辅助能力（热听、一起听、日历等仅在 README 功能列表一笔带过）。

## P0 结构性问题

### P0-1 UI 层直连数据/网络：DAO 侧已入闸，网络侧完全裸露

- **证据**：
  - DAO 侧：[import-direction-baseline.txt](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/tools/import-direction-baseline.txt) 56 条中 `ui→dao` **19 条 / 15 文件**；[AlbumDetailScreen.kt:115-116](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/app/src/main/java/com/asmr/player/ui/library/AlbumDetailScreen.kt#L115-L116) import `AppDatabaseProvider`/`LocalTreeCacheEntity`——**守卫只禁 `data.local.db.dao.` 前缀，漏了 `AppDatabaseProvider`**。
  - 网络侧（**本轮新度量**）：`ui → com.asmr.player.data.remote.*` 共 **160 处 / 27 文件**（`AlbumDetailViewModel` 33、`AlbumDetailViewModelSupport` 21、`LibraryViewModel` 11、`DownloadsViewModel` 8、`SearchViewModel`、`SettingsViewModel`、`DlsitePlayViewModel`…）。示例：[SettingsViewModel.kt:12-18](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/app/src/main/java/com/asmr/player/ui/settings/SettingsViewModel.kt#L12-L18) 引 `GitHubUpdateClient`/`DownloadDestinationStore`、[DownloadsViewModel.kt:16-22](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/app/src/main/java/com/asmr/player/ui/downloads/DownloadsViewModel.kt#L16-L22) 引 `DownloadManager`/`DownloadQueueCoordinator`。
- **分析**：对照《分层与依赖方向》——业务层直接依赖 HTTP 客户端/抓取器具体实现，Repository 边界对网络侧形同虚设。上一轮 186 处"UI 直连 DAO/网络"里，DAO 那半已收拢，网络这半**一步未动**；且既有 `ui-to-net-stack` 规则只挡 `okhttp3`/`retrofit2`/`gson` **裸库**，对项目自有 `data.remote.*` 零覆盖，因此这 160 处可以继续无障碍增长。
- **改进方案**：① 立即新增守卫规则 `ui-to-data-remote`（禁 `com.asmr.player.data.remote.`），160 处先入 baseline ratchet；② 按 B4/B5 同法把网络编排下沉 Repository（优先 `SettingsViewModel`/`DownloadsViewModel`/`SearchViewModel` 三个小体量调用方）；③ 守卫补禁 `AppDatabaseProvider`。

### P0-2 特征模块间真实循环依赖（3 组新识别）

- **证据**（逐条 grep 复核）：
  - `main ↔ ui.player`：`ui.player → main`（[PlayerDynamicHue.kt:9](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/app/src/main/java/com/asmr/player/ui/player/PlayerDynamicHue.kt#L9) `toThemeMediaSource`、[NowPlayingScreen.kt:85](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/app/src/main/java/com/asmr/player/ui/player/NowPlayingScreen.kt#L85) `HardwareVolumeOverlay`），`main → ui.player` 34 处 → **特征层反向依赖装配层**。
  - `ui.library ↔ ui.player`：[LibraryScreen.kt:123](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/app/src/main/java/com/asmr/player/ui/library/LibraryScreen.kt#L123) 与 [AlbumDetailScreen.kt:181](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/app/src/main/java/com/asmr/player/ui/library/AlbumDetailScreen.kt#L181) 直接引 `PlayerViewModel`；反向 [NowPlayingScreen.kt:108](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/app/src/main/java/com/asmr/player/ui/player/NowPlayingScreen.kt#L108) 引 `TagAssignDialog`。
  - `ui.library ↔ ui.settings`：[AlbumDetailScreen.kt:182](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/app/src/main/java/com/asmr/player/ui/library/AlbumDetailScreen.kt#L182) ↔ [SettingsScreen.kt:107-108](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/app/src/main/java/com/asmr/player/ui/settings/SettingsScreen.kt#L107-L108)。
  - `ui.library ↔ ui.sidepanel`：`LibraryScreen.kt:136-137` ↔ [RecentAlbumsPanelViewModel.kt:8-10](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/app/src/main/java/com/asmr/player/ui/sidepanel/RecentAlbumsPanelViewModel.kt#L8-L10)（后者反向引 `LibraryQueryBuilder`/`LibraryQuerySpec`/`LibrarySort`）。
- **分析**：对照《模块边界与耦合》——环内模块无法独立复用与测试；`LibraryQuerySpec`/`PlayerViewModel` 这类"查询规格与播放意图"本应由 `main` 装配层以参数注入，现在变成特征包之间的硬引用，导致改库页会牵动播放页与设置页。
- **改进方案**：查询规格/播放意图类下沉 `domain`；跨屏共享的 ViewModel 经 `main` 以参数/事件（或 `AppNavigator` 已有的中立通道）注入；新增守卫规则 `feature-to-feature`（禁 `ui.<A> → ui.<B>`，白名单 `ui.common`/`ui.theme`）。
- **说明（非问题）**：另有 5 组包级"环"经核实**不是真实架构耦合**——`cache↔data.settings`/`cache↔playback` 仅共享常量 `AppCacheLimits`；`data.remote↔listentogether` 仅 [AsmrOneAvailabilityApi.kt:9](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/app/src/main/java/com/asmr/player/data/remote/api/AsmrOneAvailabilityApi.kt#L9) 引工具类 `XxHash64`；`di↔subtitle` 仅 qualifier 常量。搬常量/工具即可消失（见 P2）。

## P1 维护性问题

### P1-1 ARCHITECTURE.md 系统性过期（文档债核心）

- **证据**（逐文件实测对照，17 项可验证数字中 15 项偏差、1 项断言失效）：

| 文档声称 | 位置 | 实测 | 偏差 |
|---|---|---|---|
| 测试基线 875 个用例 | [ARCHITECTURE.md:121](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/docs/ARCHITECTURE.md#L121) | 906 | **+31** |
| 测试基线 840（README） | README.md:56 | 906 | **+66** |
| ui 直连 DAO **16** 文件 | [ARCHITECTURE.md:57](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/docs/ARCHITECTURE.md#L57) | 15 | −1 |
| "AlbumDetailViewModel 注入整个 AppDatabase" | ARCHITECTURE.md:57 | 仅 import `TagWithCount`，**无 AppDatabase** | **断言失效** |
| AlbumDetailViewModel 约 2960 行 | ARCHITECTURE.md:68 | 2996 | +36 |
| AlbumDetailScreen 1415 行 | ARCHITECTURE.md:67 | 1523 | +108 |
| DirectorySupport 3180 行 | ARCHITECTURE.md:69 | 2705 | −475 |
| MainContainer ~2630 行 | ARCHITECTURE.md:21 | 2702 | +72 |
| PlaybackService ~1449 行 | ARCHITECTURE.md:94 | 1465 | +16 |
| PlayerConnection ~959 行 | ARCHITECTURE.md:90 | 986 | +27 |
| data→上层 4 文件 5 处"已记录于 baseline" | ARCHITECTURE.md:58 | baseline 中 **0 条 data 条目**（B1 已清零） | **失效** |
| 家族合计 ~16 400 行 | ARCHITECTURE.md:63 | ~15 446 | −954 |
| 头注"行数均于 2026-09-30 直接核实" | [ARCHITECTURE.md:4](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/docs/ARCHITECTURE.md#L4) | 与实测不符 | **过期声明** |
| §7 按 20260929 报告的 P0-1..P0-4 编号叙述 | [ARCHITECTURE.md:127](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/docs/ARCHITECTURE.md#L127) | 现行编号体系已改为 20261001 报告 | **编号冲突** |
| §7 未提 R2 阶段进行状态 | ARCHITECTURE.md:125-137 | R2 已执行 A/B 阶段 | **缺状态** |

- **分析**：对照《文档化》——"过期架构文档比没有更糟"：它宣称"已核实"，读者会当作现状事实使用（本项目的 R2 计划正是以架构文档为依据制定的）。
- **改进方案**：R2-C7 已计划"ARCHITECTURE.md 全面重写"，建议**提前到阶段 C 开工前**先做一次数字校准，并把 §7 改为"历史偿还 + R2 现状"双栏、改用 symbolic id 而非跨报告编号。

### P1-2 三项静态层可靠性/性能缺口

- **证据**：
  - [PlaybackService.kt:279](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/app/src/main/java/com/asmr/player/service/PlaybackService.kt#L279) `onCreate` 仍 `runBlocking`（包了 2s `withTimeout`）；[:1382](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/app/src/main/java/com/asmr/player/service/PlaybackService.kt#L1382) `onDestroy` 仍 `runBlocking(Dispatchers.IO)`。R2-A3 计划写的是"改 `serviceScope.launch` + 起播门控"，**实际只加了超时上限，异步化未落地**。
  - [DownloadWorker.kt:423](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/app/src/main/java/com/asmr/player/data/remote/download/DownloadWorker.kt#L423) 重试条件仅 `e is IOException`，5xx 走 `Result.failure()`；下载链未显式 `setBackoffCriteria`。
  - [DownloadWorker.kt:564-567](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/app/src/main/java/com/asmr/player/data/remote/download/DownloadWorker.kt#L564-L567) catch 后 `Log.w` 仍返回 `Result.success()`。
- **分析**：对照《可靠性/可用性》——服务创建最多阻塞主线程 2s；弱网语义只覆盖连接层；finalize 失败上报成功使下载完整性状态不可信。
- **改进方案**：`onCreate` 改 `serviceScope.launch` + 起播门控；`onDestroy` flush 改短超时 IO 协程；重试条件纳入 5xx/408 并显式配置指数退避；finalize 失败返 `Result.failure()` 或落可观测事件。

### P1-3 核心编排类无 seam 测试

- **证据**：`app/src/test` 中无同名用例的生产类——`PlaybackService`、`SubtitleTaskService`、`LibraryViewModel`、`AlbumDetailViewModel`、`DownloadsViewModel`、`SearchViewModel`、`SettingsViewModel`、`PlayerViewModel`。`MainContainer` 仅有 [MainContainerSaveableStateTest.kt:50](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/app/src/test/java/com/asmr/player/MainContainerSaveableStateTest.kt) 用测试内 `SaveablePrimaryPage` **复制件**自测框架行为（非生产 seam）。B4/B5 新增的 `LibraryReadRepositoryTest`/`LibraryWriteRepositoryTest` 是本轮质量最高的新 seam。
- **分析**：对照《可测试性》——测试力气集中在 support/纯函数，改 God VM/Service 时没有安全网，这正是阶段 C 拆 God 的最大风险。
- **改进方案**：阶段 C 每个拆分任务前**先补该子功能的 seam 测试**（沿用 B4/B5 模式）；`MainContainerSaveableStateTest` 改为驱动真实生产入口。

### P1-4 Scattered Functionality（专辑操作跨 5 个特性）

- **证据**：专辑操作接线分布于 `ui/library`、`ui/search`、`ui/hotlistening`、`ui/playlists`、`ui/groups`；`AlbumDetailScreen.kt` 单文件即引 `AlbumGroupsViewModel`(:176)、`PlaylistPickerScreen`(:179)、`PlaylistsViewModel`(:180)、`PlayerViewModel`(:181)、`SettingsViewModel`(:182)、`DlsitePlayViewModel`(:131)。
- **分析**：对照《Scattered Functionality》——同一业务概念（"对某专辑做什么"）在 5 处重复接线，缺共享抽象，改动需多点同步。
- **改进方案**：与 P0-2 的装配层注入方案合并解决——抽"专辑动作"统一入口（`AppNavigator` + 事件）由 `main` 装配。

### P1-5 缺失根文档（LICENSE / CHANGELOG / CONTRIBUTING）

- **证据**：仓库根 `git ls-files` 确认无 `LICENSE`、`CHANGELOG.md`、`CONTRIBUTING.md`。项目已在 GitHub 发布 Release（README:40）。
- **分析**：对照《文档化 §5》——无 LICENSE 时法律默认为"保留所有权利"，他人无法合法复用/二次分发；无 CONTRIBUTING 使"怎么跑测试/怎么提 PR"仅散落在 `docs/devnote/`。
- **改进方案**：补 `LICENSE`（需用户选型）、`CHANGELOG.md`（可使用 tag 历史生成首版）、`CONTRIBUTING.md`（可从 `docs/devnote/agent-collab.md` 提炼非本机路径部分）。

### P1-6 androidTest 与 baselineprofile 不进 CI

- **证据**：[ci.yml](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/.github/workflows/ci.yml) 仅 `ci_guard.py` + `testDebugUnitTest`；[release.yml](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/.github/workflows/release.yml) 仅 `testReleaseUnitTest` + APK 门禁。18 个 androidTest（含迁移测试、分页压力测试）与 `:baselineprofile` 模块从不执行。
- **分析**：对照《腐蚀预防》——"写了但从不跑"的资产必然腐烂；迁移测试腐烂会直接威胁升级可靠性。
- **改进方案**：CI 至少增加 `assembleAndroidTest` 与 `:baselineprofile:assemble` 编译门禁（成本低），迁移/分页测试排入定期或 release 前任务。

### P1-7 DI 双轨与全局可变单例

- **证据**：`AppDatabaseProvider` 仍有 **44 处**引用，与 Hilt `di/DatabaseModule` 并存（同库两种取法）；`playback/StereoSpectrumBus`、`PlaybackMediaCache`、`performance/UiFrameWorkCoordinator`、`MediaItemFactory` 仍为可变 `object`。
- **改进方案**：`AppDatabaseProvider` 降级为 Hilt 的 `@Provides` 单一来源；单例随阶段 C 逐步改为注入。

### P1-8 `LibraryWriteRepository` 已成新 God 苗头；`LibraryReadRepository` 属过早抽象

- **证据**：`LibraryWriteRepository.kt` 约 **1050 行**（<1500 未入 size baseline，守卫不拦）；`LibraryReadRepository.kt:30-73` 逐方法 `database.xxxDao().yyy()` 纯透传，KDoc 自认"均为透传"。
- **分析**：对照《过早抽象 / God 类》——透传层只增加一层间接；1050 行写库类将重复 God 化路径。
- **改进方案**：Write 层按"标签族 / 树缓存族 / FTS 族"拆 3 个 repository 并把各自行数纳入 size baseline；Read 层并入调用方或改窄接口（保留 `fileSizeQuery`/`stampProvider` 等真实平台 seam，它们是隔离文件系统的合理注入点）。
- **注**：上一轮审查提到的 `upsertLocalTreeCache` 双轨映射**仍在**（[LibraryViewModel.kt:787-796](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/app/src/main/java/com/asmr/player/ui/library/LibraryViewModel.kt#L787-L796) 做 `CacheLeafEntry→ScanCacheLeaf` + `TreeFileType.valueOf(leaf.fileType.name)` 枚举名转换），应统一 leaf 模型。

### P1-9 无模块边界自动化测试（Konsist/ArchUnit）

- **证据**：全仓无 Konsist/ArchUnit；`ci_guard.py` 是 import 文本匹配，**无法断言结构约束**（如"UI 不得持有 DAO 类型字段"、"Repository 不得返回 UI 类型"）。
- **改进方案**：R2-C6 建议**引入 Konsist**（Kotlin 原生、可用 `testDebugUnitTest` 承载）：断言包依赖方向、`ui` 不得 import `data.local.db.dao`/`data.remote`、`domain` 纯净、命名与目录一致。它是 import 文本规则的结构级补充，且能让 P0-2 的环规则可表达。

## P2 改进项

1. **`util/` 垃圾抽屉**：21 文件横跨错误格式化、断链防护、媒体抽取、字幕解析、排序键、扫描根存储；建议按域下沉（字幕→`subtitle/`、DLsite→`data/remote/dlsite/`、存储→`data/settings/`）。
2. **近空目录成片**：单文件目录 `ui/splash`、`ui/drawer`、`ui/update`、`performance`、`data/remote/crawler`、`data/remote/update`；可并入 `ui/common`/`ui/nav`/`service`。
3. **`benchmark` 包仍编进 main 源集**：4 文件（含 `BenchmarkHarnessActivity`，manifest `exported="true"` 但由 `@bool/benchmark_harness_enabled` 门控，[main 默认 false](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/app/src/main/res/values/benchmark_harness.xml#L3)、benchmark 源集 true → **release 无暴露风险**）；建议移入 `app/src/benchmark` 源集与 `:baselineprofile` 对齐。
4. **`data/local/db/dao/` 混投影 DTO**：`LibraryTrackRow`、`TagWithCount`、`PlaylistStatsRow`、`AlbumGroupStatsRow`、`LibraryTrackAlbumHeaderRow`、`DownloadTaskWithItems` 等；建议移至 `db/projection/` 或 `entities/`。
5. **常量/工具错放导致的假环**：`NetworkHeaders` 由 [util/DlsiteAntiHotlink.kt:3](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/app/src/main/java/com/asmr/player/util/DlsiteAntiHotlink.kt#L3) 反向 import；`AppCacheLimits` 由 [cache/AppCacheManager.kt:5-6](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/app/src/main/java/com/asmr/player/cache/AppCacheManager.kt#L5-L6) 反向 import `data.settings`/`playback`；`XxHash64` 放在 `listentogether` 被 `data.remote` import。三者搬到 `util`/`domain` 即消除 3 组环。
6. **`cache/` 定位含糊**：13 文件与 `data/` 职责重叠，ARCHITECTURE 未明确其为横切基础设施包。
7. **`@Suppress` 19 处**：含 [DownloadWorker.kt:274-275](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/app/src/main/java/com/asmr/player/data/remote/download/DownloadWorker.kt#L274-L275) 两处 `UNUSED_VARIABLE`（疑似死代码）、`MainContainerSupport.kt:432/519/546`、`PlaybackService.kt:801/912` 的 DEPRECATION。
8. **死依赖**：`androidx.metrics:metrics-performance` 无任何消费方（仅 baselineprofile 用 `androidx.benchmark.macro`）；建议接入或移除。
9. **重复逻辑**：`DownloadManager.kt:690 upsertDownloadedAlbumToLibrary`（~152 行）与 `:910 upsertDownloadedDocumentAlbumToLibrary`（~148 行）近同构；建议抽公共 upsert 骨架。
10. **8 张 PNG 入库 + `.gitignore` 白名单式 `*.png`/`*.xml` 规则**：新增截图需再改 `.gitignore`，易误伤。
11. **`docs/devnote/README.md` 索引停在 2026-09-30**，未收录 `2026-10-01-r2-start.md` 与 `2026-10-02-r2-phase-b-finish.md`——新协作者按索引会漏读 R2 现状。
12. **`docs/behavior-notes/` 仅 1 个文件**（`library-delete-family.md`，内容质量高），距"一 seam 一文件"目标覆盖极低。
13. **`tutorial.md`（71KB / 1153 行）位于根目录但被 `.git/info/exclude` 忽略 → 未入库**，却占根目录且与 README 潜在重复；建议正式入库或移出仓库根。
14. **`.trae/documents/`（含 R2 计划与交接文档）整体 gitignore**：与 `docs/devnote/` 职责重叠，交接内容对协作者不可见。
15. **`onDestroy` 落盘仍同步**、**迁移测试仅 30→31**、**无 flaky 重试插件**。

## 技术债清单

| 类型 | 债务项 | 证据位置 | 优先级 | 建议偿还时机 |
|---|---|---|---|---|
| 设计债 | UI 直连 `data.remote` 160 处 / 27 文件，守护零覆盖 | P0-1 | 高 | 阶段 C 前先加规则入 baseline |
| 设计债 | 特征模块间 3 组真实环（main↔ui.player、ui.library↔{ui.player,ui.settings,ui.sidepanel}） | P0-2 | 高 | 阶段 C（与装配层注入同批） |
| 设计债 | 3 巨型 Composable + 2 God VM 未拆 | P0-4 | 高 | R2-C1~C4（已排期） |
| 设计债 | size ratchet 松弛 805 行、import baseline 无自净 | P0-3 | 高 | **立即**（改守卫即含） |
| 设计债 | `LibraryWriteRepository` 1050 行 God 苗头；`LibraryReadRepository` 纯透传 | P1-8 | 中 | 阶段 C 拆族时 |
| 设计债 | DI 双轨（`AppDatabaseProvider` 44 处）、全局可变单例 | P1-7 | 中 | 随阶段 C |
| 代码债 | `DownloadManager` 两份近同构 upsert；`upsertLocalTreeCache` 双轨映射；@Suppress 19 处 | P1-8、P2-9/7 | 中 | 随改动顺路偿还 |
| 代码债 | finalize 失败报 success；5xx 不重试；`onCreate` runBlocking | P1-2 | 中 | 下一次触碰下载链/服务时 |
| 测试债 | 8 个核心编排类无 seam 测试；`MainContainerSaveableStateTest` 用复制件 | P1-3 | 中 | **阶段 C 每个任务前** |
| 测试债 | androidTest / baselineprofile 不进 CI；无 flaky 处理；迁移测试仅 30→31 | P1-6、P2-15 | 中 | 立即加编译门禁 |
| 文档债 | ARCHITECTURE.md 15/17 数字偏差 + 2 断言失效 + 旧编号 | P1-1 | 高 | 阶段 C 开工前校准 |
| 文档债 | 根文档三缺（LICENSE/CHANGELOG/CONTRIBUTING） | P1-5 | 中 | 阶段 C 交付时 |
| 文档债 | devnote 索引未更新；behavior-notes 覆盖 1/N；`.trae/documents` 不入库 | P2-11/12/14 | 低 | 随文档同步 |

## Quick Wins

1. **收紧 size ratchet 并加"只降不升"断言**：改 [ci_guard.py](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/tools/ci_guard.py) 增加"`cap > 实测 + 50` 即失败（或自动重写 cap）"，一次性回收 `AlbumDetailViewModel` 217 + `LibraryViewModel` 588 = **805 行**配额。收益：让 B 阶段成果转化为对下一阶段的下行压力，防止 588 行无声回涨。
2. **新增两条高价值守卫规则**：`ui-to-data-remote`（160 处实证存量，入 baseline）与 `data-to-feature`（已有 1 处实证：`data.remote → listentogether`）。收益：给当前最大结构洞装闸门，成本仅改 `RULES` 列表 + 加夹具。
3. **修 [ARCHITECTURE.md:57-58](file:///e:/Documents/doc/Files/VSCodeCode/EaraAsmrPlayer/docs/ARCHITECTURE.md#L57-L58) 与 §6 用例基线**：这两处是"读者当事实用"的断言（"AlbumDetailViewModel 注入整个 AppDatabase"、"data→上层已记入 baseline"均已失效）。收益：低成本阻止错误信息继续传播到重构决策。
4. **CI 增 `assembleAndroidTest` + `:baselineprofile:assemble` 编译门禁**：防资产腐烂，成本约一次编译。
5. **PlaybackService `onConnect` 增包名白名单**（仅本包 + 系统通知控制器下发 4 条自定义命令），或改 `exported="false"`。收益：消除第三方 App 篡改音效/视频输出的攻击面。

## 改进路线图

- **第一阶段 — 补闸门与校准（阶段 C 开工前，即 P0-3 + Quick Wins）**：收紧 size ratchet 并加下行断言；新增 `ui-to-data-remote` / `data-to-feature` / `feature-to-feature` / `playback-to-service` 规则（存量入 baseline）；修 ARCHITECTURE.md 数字与失效断言；CI 加 androidTest/baselineprofile 编译门禁；`onConnect` 白名单。**预期效果**：守护从"防回潮"恢复为"推下降"，且阶段 C 的改动全部处于被守护状态。
- **第二阶段 — 拆除 P0 结构（R2-C1~C6）**：MainContainer 路由族拆分 + 独立状态宿主（先补路由 seam 测试）；NowPlayingScreen / SettingsScreen 区块化；两个 God VM 方法族拆分；网络侧 160 处随各 ViewModel 下沉 Repository；引入 Konsist 断言包依赖方向。**预期效果**：单函数最长 ≤800 行、UI 层对 `data.*` 直接依赖清零。
- **第三阶段 — 治理 P1/P2 与交付（R2-C7）**：`util`/近空目录/dao DTO/`benchmark` 源集归位；`LibraryWriteRepository` 拆族；单例注入化；补根文档（LICENSE/CHANGELOG/CONTRIBUTING）；devnote 索引与 behavior-notes 补齐；ARCHITECTURE.md 全面重写。**预期效果**：文档与实现一致，目录结构与分层规则自洽。

---

> 评级标准：A 健康可长期演进 ｜ B 有债务但可控 ｜ C 需要专项治理 ｜ D 存在阻碍演进的结构性问题
> 附：本轮为**纯静态审查**，未运行测试套件；测试健康度结论来自 CI 配置、测试资产清点与最近一次门禁记录（906 通过 / 0 失败 / 4 跳过，2026-10-02），未重跑。所有 P0/P1 结论均由审查者独立复算（Kotlin 依赖图内联 python 复算、`readlines()` 行数口径复核、关键行号 grep 复核）。
