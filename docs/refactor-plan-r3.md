# 第三轮重构计划（R3）—— 断环 · 穿透收口 · 编排层 State Holder 抽取

> 依据：`docs/project-quality-review-20261004.md`（总评 C）+ 本计划制定前的逐条回源码复核。
> 与上轮关系：R2（`docs/refactor-plan-r2.md`，tag `refactor-r2/phase-A/B/C`）完成数据访问层下沉与全局守卫；R3 清 R2 明确遗留的三笔账——**剩余依赖环**、**两个 God VM 无法收紧到 800 行**、**ui 层穿透收口未完成**，并治理主要 P1。
> 状态：待开工。基线：`refactor/architecture-cleanup @ 4920f37`，测试 938/0/4，size pin 8 条，import baseline 196 条。

## 0. 已确认决策（用户 2026-10-04 拍板）

1. **范围 = P0 + 主要 P1**；P2 仅在 `ARCHITECTURE.md §7.4` backlog 备忘，本轮不排期。
2. **两个 God VM 拆解路线 = State Holder 抽取**：保留 ViewModel 外壳（构造/`uiState`/生命周期/组合），职责族抽为专用状态持有者，暴露 `StateFlow`，VM 外壳转发。
3. **不加 LICENSE**（根文档三缺中的 LICENSE 明确不做；CHANGELOG/CONTRIBUTING 亦本轮不做，留 P2）。
4. **`ensureAlbumCoverSaved` 双实现：只记录、不改动**（两版行为不同属行为变更；仅在行为档案与 backlog 记录，不统一）。
5. **不引入 Konsist/ArchUnit**（继续用 python `ci_guard.py`）。

## 1. 总原则（沿用 R2 + 新增）

沿用：TDD 安全网（测试只增不减）、一任务一提交（编号开头）、阶段门禁三件套（全量测试双绿 + 子代理审查 `git diff <上阶段tag>..HEAD` + 实机走查）、git 硬规则（主 agent 唯一写者、禁 reset --hard/force push/rebase）、巨石文件禁追加、行为档案 `docs/behavior-notes/`。

新增：
- **守护先于手术**：A 阶段规则扩面 + baseline 修正完成前，不开始 B/C 重写。
- **数字以实测为准**：报告口径偏差处（环 8→实测 11；ui→cache/work 35/20→实测 50/29）以实测写守卫。
- **消环先取证语义**：`normalizeRelativePath` 两份**语义不同** → 只改名 + 录档案，禁止"相似即合并"。

## 2. 阶段 A —— 防线与校准（前置，禁止跳步）

- **A1 守护扩面**（最先做）
  - 新增 import 方向规则：`ui.*` 禁 `data.local.db.*`（补 `AppDatabaseProvider` 盲区）/`okhttp3`/`retrofit2`/`gson`/`cache.*`/`work.*`/DataStore 直引（`SettingsDataStore`、`SearchCacheStore`）；`translation.*`/`hotlistening.*` 禁 `ui.*`（消反向倒挂）。
  - 每条新规则配反例夹具 `tools/guard-selftest/<rule>/`。
  - 存量按实测入 `tools/import-direction-baseline.txt`。
  - 验证：`python tools/ci_guard.py`（含规则自检 + 全仓扫描）通过。
- **A2 文档回填**（P1-2）
  - README:33 m3u8 声明改为"不支持"（对照 `playback/PlayerConnection.kt:292`）；ARCHITECTURE §2 `MainContainer 约2700`→**798**、§3 家族行数（AlbumDetailViewModel 2996→2510 等）；`landing_zh.md` compileSdk 34→36；§6 测试基线统一 938；`tutorial.md` 包名约定修正并移出 `.git/info/exclude`（或删除）。
- **A3 Room schema 导出**（P1-4）
  - `data/local/db/AppDatabase.kt:92-93` `exportSchema=true` + `schemas/` 入库（v31 起）；近 3 个版本补迁移测试（现 26 段仅测 7 段），旧迁移缺陷单独记录、不夹带修。
- **A4 安全网补测**（P1-3）
  - `service` 包补 PlaybackService seam 单测（现仅 1 个测试文件对应 1478 行）；androidTest 18 文件**已在 CI 编译**，缺口是"不运行"——以 Robolectric 单测替代关键路径断言，避免引入 CI 模拟器重成本（androidTest 运行能力单独评估）。
- **A5 重复实现收敛**（P1-5）
  - `centerCropSquare` 三份（`data/remote/CoverSupport.kt`、`ui/library/LibraryViewModel.kt:1448`、`work/AlbumCoverThumbWorker.kt:62`）合并为单实现；
  - 头部合并"优先非空"规则两份抽公共；
  - `normalizeRelativePath` 两份（`util/TrackKeyNormalizer.kt:50` vs `ui/library/albumdetail/AlbumDetailDirectorySupport.kt:518`）**改名区分 + 录行为档案**，不合并。

**阶段 A 门禁**：全量测试 938 双绿 + 子代理审查（`git diff refactor-r2/phase-C..HEAD`）+ 报告 `docs/iteration/r3-phase-A-review.md` + tag `refactor-r3/phase-A`。

## 3. 阶段 B —— 断环与穿透收口（P0-1 + P0-3）

### B1 依赖环消解（实测 11 组）

| # | 环 | root cause（已核实） | 手段 |
|---|---|---|---|
| 1 | `main ↔ ui.player` | `HardwareVolumeOverlay` 定义在 `main/MainChromeUi.kt:192`，被 6 个 ui/player 文件 import | 下沉 `ui/common/overlay` |
| 2 | `ui.common.cover ↔ ui.theme` | `theme/MonetSeedColor.kt` → ui.common 颜色算法 | 纯色算法下沉 `ui/common/color` |
| 3 | `ui.library ↔ ui.library.albumdetail` | albumdetail 反向 import ui.library helper（5 处/3 文件） | helper 下沉 `ui/library/common` |
| 4 | `cache ↔ data.settings` | `SettingsRepository`→`cache.AppCacheLimits`；`AppCacheManager`→`SettingsRepository` | `AppCacheLimits` 下沉中立包；预算经配置注入 |
| 5 | `cache ↔ playback` | `PlaybackMediaCache`→`cache.AppCacheLimits`；`AppCacheManager`→`PlaybackMediaCache` | 同上 + 依赖倒置 |
| 6 | `di ↔ subtitle` | `NetworkModule`→subtitle 常量；subtitle→`di.DEEPSEEK_HTTP_CLIENT` | DI 常量/qualifier 迁中立包或改 `@Qualifier` |
| 7 | `data.remote ↔ data.repository` | `data.remote` 内部依赖 repository 类型 | 接口倒置：remote 定义接口，repo 实现 |
| 8 | `data.download ↔ data.local.library` | `DownloadManager` → 本地库写入 | 依赖 repository 接口 |
| 9 | `data.download ↔ data.remote.download` | 双向引用下载队列常量/协调器 | 常量下沉中立包 |
| 10 | `ui.player ↔ ui.player.nowplaying` | nowplaying 反向依赖 ui.player | 抽宿主回调/参数化 |
| 11 | `com.asmr.player(root) ↔ ui.common.cover` | 待复核（疑根包共享符号） | 按复核结果处置 |

- **反向倒挂**：`translation/PageTranslationUi.kt`→ui.theme、`hotlistening/ListeningTracker.kt`→ui.player 上移或下沉中立层。
- **如实标注**：`data↔work`/`data↔listentogether`/`data↔hotlistening` 为单向非环，本轮不动。
- 每环**独立提交**，单环失败可独立回退。

### B2–B5 ui 层四类穿透收口

- **B2 ui→`data.local.db`（110 处/35 文件）**：按 repository 出口收口（复用 `LibraryReadRepository`/`LibraryWriteRepository`）；重灾族优先：AlbumDetailViewModel(11)、LibraryViewModel(9)、AlbumDetailDirectorySupport(8)。
- **B3 ui→`cache`/`work`（50 处/29 文件）**：经门面接口暴露。
- **B4 ui→`okhttp3`/`retrofit2`/`gson`（28 处/15 文件）**：序列化/请求下沉 repository。
- **B5 ui→`service`（7 处/6 文件）**：复用 R2-B3 的 `PlaybackController` 接口；DataStore 直引归 repository。

### B6 baseline 收缩

- `import-direction-baseline.txt` 196 → 目标 ≤80；余量随 C 阶段继续收缩。
- `size-guard-baseline.txt` 随 C 阶段收缩（B 阶段不动 size）。

**阶段 B 门禁**：全量测试双绿 + 子代理审查 + 实机走查（库页/详情页/下载页/播放链）+ tag `refactor-r3/phase-B`。

## 4. 阶段 C —— 编排层 State Holder 与 God 收缩（P0-2）

### C1/C2 两个 God VM 的 State Holder 抽取

放置：`ui/library/holder/`、`ui/library/albumdetail/holder/`；构造注入 Repository、暴露 `StateFlow`、VM 内 `by lazy` 或 `@Singleton`；VM 外壳保留 `uiState` 组合与生命周期。

- **C1 `LibraryViewModel` 2521 → 目标 ≤800**：`LibraryScanStateHolder` / `LibraryCloudSyncStateHolder` / `LibraryDeleteStateHolder` / `LibraryFilterStateHolder` / `LibraryTagStateHolder`。
- **C2 `AlbumDetailViewModel` 2510 → 目标 ≤800**：`DlsiteSectionStateHolder` / `AsmrOneSectionStateHolder` / `DownloadSelectionStateHolder` / `TreeStateHolder`。
  - ⚠️ 保留 VM 版 `applyResolvedCloudSync` 的 title 覆盖语义（与 repo 合并规则不同，**不可混用**）。

### C3 `LibraryWriteRepository` 1050 → 拆族
`TagWrite` / `DeleteWrite` / `ScanWrite` / `OnlineSave`。

### C4 God 文件区块化
`AlbumDetailDirectorySupport` 2700、`DownloadsScreen` 2281、`SearchScreen` 2186（`SearchScreenContent` 单函数近千行/19 形参 → 拆子 composable + 状态对象）、`AlbumDetailDlsiteTabs` 1932、`LibraryScreen` 1582（按热点优先级）。

### C5 ratchet 收紧（依赖 C1/C2/C3）
每完成一批拆解即从 `size-guard-baseline.txt` 移除对应 pin 并下调 `SIZE_LIMIT`（1500 → 1200 → 1000 → 800，渐进）；目标 >1500 清零，向 800 逼近但以实际进度为准、不强达。

### C6 结构收尾
`walkTree`/`scanFromDocumentTree` 拆函数、Chrome 概念归包（`main` 与 `ui/nav/BottomChrome.kt`）、dao 投影 DTO 归位。

**阶段 C 门禁**：全量测试双绿 + 子代理审查 + 实机全链 smoke + tag `refactor-r3/phase-C`。

## 5. 行为档案机制

沿用 R2 §5。R3 新增触发条件：消环或抽 State Holder 触及"看似不平衡的既有清理"（如 `deleteAlbum` 不清孤儿表）时，**先录档案再动手**（已录行为勿顺手清理）。

## 6. 风险与缓解

| 风险 | 缓解 |
|---|---|
| State Holder 抽取致 UI 状态机行为回归 | 抽前录行为档案 + 每 holder 钉 seam 测试 + 分批提交 + 实机对照 |
| 消环改动面广（11 组） | 逐环独立提交，单环可独立回退；A 阶段守护先兜底 |
| ui 穿透收口触及大批文件 | 按 repository 出口分族、逐族收缩 baseline，禁止一次性大改 |
| 开启 schema 导出暴露历史迁移缺陷 | 近 3 版本优先补测试，缺陷单独记录不夹带修 |
| ratchet 收紧过急阻塞 | 只在 C1/C2/C3 完成后逐批收紧，不设"一步到 800" |
| 与用户设备走查冲突 | 走查前约定前台切换时机（用户可能正在游戏） |

## 7. 记录但不改动项（用户 2026-10-04 决策）

- **`ensureAlbumCoverSaved` 双实现**：VM 版（仅网络 / 2048 / ARGB_8888）vs repo 版（支持本地来源 / 1280 / RGB_565）——**保留现状**，录行为档案并在 backlog 标注，不统一。
- **LICENSE / CHANGELOG / CONTRIBUTING**：本轮不加不建，留 P2 backlog。
- **Konsist/ArchUnit**：不引入，沿用 python `ci_guard.py`。

## 8. 执行第一步与验收

**执行顺序**（每步一提交，编号开头）：
1. 本文件落盘为 `docs/refactor-plan-r3.md`。
2. A1→A5 实施阶段 A，过门禁打 `refactor-r3/phase-A`。
3. B1 逐环 → B2–B5 穿透收口 → B6 baseline 收缩，过门禁打 `refactor-r3/phase-B`。
4. C1/C2 State Holder → C3/C4 拆族 → C5 ratchet → C6，过门禁打 `refactor-r3/phase-C`。
5. 终态：ARCHITECTURE.md §7 全面同步（阶段完成记录 + backlog 清账）。

**验证方式（每阶段复跑）**：
- `\.gradlew-local.bat -g "C:\Users\24131\.gradle" :app:testDebugUnitTest` — 全绿、只增不减（基线 938）。
- `python tools/ci_guard.py`（含规则自检）通过；两个 baseline 按实测收缩。
- 子代理只读审查 `git diff refactor-r2/phase-C..HEAD`，报告落 `docs/iteration/r3-phase-N-review.md`。
- 实机走查（小米 14）：库页 / 详情页（DL tab / ASMR.ONE）/ 下载页 / 播放链，`adb shell am start --es start_route "<route>"` 直达取证。
