# 第三轮重构计划（R3）—— 断环 · 穿透收口 · 编排层 State Holder 抽取

> 依据：`docs/project-quality-review-20261004.md`（总评 C）+ 本计划制定前的逐条回源码复核。
> 与上轮关系：R2（`docs/refactor-plan-r2.md`，tag `refactor-r2/phase-A/B/C`）完成数据访问层下沉与全局守卫；R3 清 R2 明确遗留的三笔账——**剩余依赖环**、**两个 God VM 无法收紧到 800 行**、**ui 层穿透收口未完成**，并治理主要 P1。
> 状态：阶段 A 已完成（tag `refactor-r3/phase-A`，测试 945/0/4）；阶段 B 开工前评估见 [dependency-forecast](refactor-plan-r3-dependency-forecast.md)。**B0–B5 全部提交**（B4 环7/环8+NetworkHeaders 迁移；B5a/c/d 穿透收口三批；详见 [进度留档](iteration/r3-phase-b-progress.md)）。实测：import baseline **344 → 205**，SCC **50 → 41**（B5 后底层团不变），2-环 18 → 13；全量测试 B6 门禁复跑中。基线：`refactor/architecture-cleanup`，size pin 8 条（LibraryViewModel 2494→2493）。

## 0. 已确认决策（用户 2026-10-04 拍板）

1. **范围 = P0 + 主要 P1**；P2 仅在 `ARCHITECTURE.md §7.4` backlog 备忘，本轮不排期。
2. **两个 God VM 拆解路线 = State Holder 抽取**：保留 ViewModel 外壳（构造/`uiState`/生命周期/组合），职责族抽为专用状态持有者，暴露 `StateFlow`，VM 外壳转发。
3. **不加 LICENSE**（根文档三缺中的 LICENSE 明确不做；CHANGELOG/CONTRIBUTING 亦本轮不做，留 P2）。
4. **`ensureAlbumCoverSaved` 双实现：只记录、不改动**（两版行为不同属行为变更；仅在行为档案与 backlog 记录，不统一）。
5. **不引入 Konsist/ArchUnit**（继续用 python `ci_guard.py`）。
6. **不引入新依赖**（2026-10-04 追加）：本轮不新增任何第三方依赖（含 coroutines-test/Turbine）——C8 重写测试沿用项目既有的 `runBlocking` + 真实时间小超时模式（§7）。

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

**阶段 A 门禁（已完成）**：全量测试 **945**/0/4 双绿（本机实跑）+ 子代理审查（无 P0，P1 已闭环）+ 报告 `docs/iteration/r3-phase-A-review.md` + tag `refactor-r3/phase-A`（实机走查因设备未连接挂起，已记录）。

## 3. 阶段 B —— 断环与穿透收口（P0-1 + P0-3）

> **前置评估（2026-10-04，开工前对 11 组环做符号级复评）**：现 17 条守护规则**不含** `cache`/`di`/`data` 内部规则，亦无 `main↔ui` 规则——故 11 组环**多数不被 CI 守护**，修与不修不产生"过闸"差异；修的价值是**包边界单向化**而非守卫合规。据此按"**改动风险 × 收益**"重排为"做 / 缓 / 不动"。

### 3.0 逐环真相与处置（证据见开工前的三份子代理复评）

| # | 环 | 守卫可见 | 真相 | 处置 |
|---|---|---|---|---|
| 1 | `main↔ui.player` | 否 | 真实：`main/MainChromeUi.kt:192 HardwareVolumeOverlay` + `service.AudioOutputRouteKind` | **做**（低风险，连带 ui→service −5）|
| 2 | `ui.common.cover↔ui.theme` | 反向白名单放行 | 单向：`theme/MonetSeedColor.kt:10 → cover` 颜色算法（唯一消费者）| **做**（1 处）|
| 3 | `ui.library↔ui.library.albumdetail` | 否（同 feature `parts[4]` 豁免）| 账面环（同 feature 合法）| **缓**（~17 文件 + baseline 重写，零守卫收益）|
| 4 | `cache↔data.settings` | 否 | 真实，与 #5 共享 `cache.AppCacheLimits` | **做**（下沉即消两环，连带 −3）|
| 5 | `cache↔playback` | 否 | 同 #4 | **做**（同上）|
| 6 | `di↔subtitle` | 否 | 真实（2 常量：`DEEPSEEK_HTTP_CLIENT`、`DEEPSEEK_TRANSLATION_CONCURRENCY`）| **做**（常量下沉 `util`）|
| 7 | `data.remote↔data.repository` | 否 | 真实（`TrafficStatsInterceptor → StatisticsRepository`）| **中风险做**（接口倒置）|
| 8 | `data.download↔data.local.library` | 否 | 真实（`DownloadManager ↔ LocalAlbumMergeService`）| **中风险做**（端口接口）|
| 9 | `data.download↔data.remote.download` | 否 | 真实**双向**；常量下沉**不足**（主体是 7+ 支持函数双向引用）| **缓**（需抽共享下载内核）|
| 10 | `ui.player↔ui.player.nowplaying` | 否（同 feature）| **非违规**（`nowplaying` 是 `ui.player` 同 feature 子包）| **不动** |
| 11 | `root↔ui.common.cover` | 否 | 真实边为 `ui/common/cover/ImagePreviewDialog.kt:78 → AsmrApp`（排除 `R`）| **低风险做**（改 `EntryPointAccessors`）|
| 倒挂 | `hotlistening→ui.player`、`translation→ui.theme` | 是（已在 baseline）| `isOnlineMedia` 纯函数；`PageTranslationUi` 是唯一含 `@Composable` 的 translation 文件 | **做**（前者下沉 `util` −1；后者归 `ui.translation` −1）|

`data↔work`/`data↔listentogether`/`data↔hotlistening` 为单向非环，不动。

> **多跳环（开工前评估新增）**：包级 SCC 实测为 **1 个 48 包巨型连通团**，11 组 2-环只是其可读子集；只消 2-环**不会解散该团**。本轮策略：**消 2-环 + 以 SCC ratchet 冻结规模**（见 B0），并打断 3 条代表性长环——`data.download→data.remote→data.repository→data.download`（B4 扩到 `repository→download` 边）、`data.remote→data.settings→hotlistening→data.remote`（常量下沉）；`root→ui.*→root`（经 `R`，结构性，接受）。

### B0 基线与检测前置（先于一切移包）—— ✅ 已完成（2026-10-04）
- 新增 **包级 SCC ratchet** 进 `ci_guard.py`：当前 48 包团规模设为上界，只许减不许增（这是"断环"的可验收指标——否则消环无验收手段）。**落地实测**：最大连通团为 **50 包**（含根包；计划估值 48，以实测为准），上界写入 `tools/package-scc-baseline.txt`；含 Tarjan 合成图自检（防空转）。
- baseline **失效条目（dead entry）检测**：移包后旧条目静默遗留会被报出。**落地**：按"当前违规键集合"判定，覆盖 ①导入/文件已删 ②规则或白名单变更后不再违规 两类；缺行号（`<rel> <fq>` 短键）容忍拆文件改行号。
- 提供**机械重写 baseline** 脚本：新增 `tools/refresh_baseline.py`（`import`/`size`/`scc` 三子命令；保留原序以最小化 diff）。移包 → 全仓 import 重写 → 重键 `import-direction-baseline.txt`；拆文件同理重键 `size-guard-baseline.txt`。
- **附带清账**：失效检测当场报出 5 条历史 stale 条目（4 条导入已删 + 1 条因 `ui.common` 白名单不再违规），已移除，`import-direction-baseline.txt` **344 → 339**。`python tools/ci_guard.py` 全绿。

### B1 纯类型下沉批次（收益最大、零行为风险，最先做；净 ≈−83 条）
把跨层共享的**纯类型/常量/纯函数**从 data/cache 提到中立包（`domain`/`util`）——不改逻辑，仅改 `package` 与全仓 import：
- `TreeFileType` 族（枚举 + 6 纯函数；序列化按枚举名，移包不破坏存量数据）**23 条** → `domain`
- `TagSource` **3 条** → `domain`
- `LibraryQuerySpec`/`LibrarySort`/`LibrarySourceFilter` **12 条** → `domain`（⚠️ 与 SQL 的 `LibraryQueryBuilder` **同文件** `query/LibraryQuerySpec.kt:6/37/44/59`，须**先拆文件**，builder 留 data）
- cache 纯类型 `CachePolicy`/`CacheImageModel`/`AppCacheLimits` **22 条** → `domain`/`util`（**不可放 `ui.common.cover`**：cache 层自身也引用）
- 纯投影 DTO（`TagWithCount`/`LibraryTrackRow`/`LibraryTrackAlbumHeaderRow`/`AlbumGroupStatsRow`/`AlbumGroupTrackRow`/`PlaylistStatsRow`/`AlbumListeningRow`）**15 条** → `domain`
- `AudioOutputRouteKind` **5 条** → `util`（顺带消环 #1 一半）
- **预期结果**：baseline ≈344 → **≈261**（ui→db −52、ui→cache/work −26、ui→service −5）；零新违规、零行为变更。
- ⚠️ 本轮不动：`titleForDisplay`（`entities/DisplayTitleSupport.kt`，@Entity 扩展，ui 用 7 处、data/subtitle 也用）随实体走，留 backlog；每个迁移任务**须同步 test/androidTest 的 import（≥20 处）**，否则 androidTest 编译红。

### B2 低风险消环批次（独立提交，逐环）
- 环1：`AudioOutputRouteKind`→`util`（B1 已含）+ `HardwareVolumeOverlay`→`ui/common/audio`（main 与 ui.player 共用）；
- 环2：`computeCenterWeightedHintColorInt`→`util`；
- 环4/5：`AppCacheLimits`→中立包（B1 已含）即消两环；`AppCacheManager → SettingsRepository/PlaybackMediaCache` 的**反向依赖仅记录、不做倒置**（静态 object 适配器 + 启动时序风险高、收益低）；
- 环6：`DEEPSEEK_HTTP_CLIENT`、`DEEPSEEK_TRANSLATION_CONCURRENCY`→`util`；
- 环11/倒挂：`isOnlineMedia`→`util`（−1）；`PageTranslationUi`→`ui.translation`（−1）；`ImagePreviewDialog` 改用 `EntryPointAccessors.fromApplication`。
- **预期结果**：环 #1/#2/#4/#5/#6/#11 消解（#3/#9 缓、#10 不动）；baseline 净 **−2**（倒挂）。

### B3 守卫补强（防回潮）
- 扩 `ui-to-service` 源包含 `main`：复查发现 **main 侧 10 条 `service.*` 盲区**（`AudioOutputRouteKind`×6、`PlaybackService`×4，如 `MainContainer.kt:70`）未入 baseline 也不被拦截；存量入 baseline（**净 +10，须与 B6 目标合账**：目标相应上调为 ≤110，或记为"补检测费"）。
- 评估新增 `cache`/`data` 内部方向规则（会把现存单向边判为违规，需连带倒置或入 baseline）——单独立项决策，不在本轮强上。

### B4 中风险消环（接口倒置）—— ✅ 代码完成，工作树挂起（见进度留档）
- 环7：✅ `util` 定义 `NetworkTrafficSink`，`StatisticsRepository` 实现并经 `di/StatisticsModule` 绑定，`TrafficStatsInterceptor` 注入接口（纯倒置）；
- 环8：✅ 抽 `DownloadStorage` 端口（定义于消费方 `data/local/library/DownloadStoragePort.kt`），`DownloadStorageGateway` 实现（手动构造点无需 Hilt 绑定）；
- **计划外**：NetworkHeaders → `util`（环7 倒置后新 2-环 data.remote↔util 的常量下沉修复；引发 SCC 48 → 41 级联塌缩）；环9、环3：**缓**（记 backlog）。

### B5 ui 穿透收口（承接 B1 后的剩余）
- **DTO/repository 出口**：实体类（`AlbumEntity`/`TrackEntity` 等）经 repository 出领域模型（需新映射，中风险）；B1 后各 VM 仅余约 2–4 条实体引用。
- **cache/work 剩余（24 条）**：`ImageCacheEntryPoint`（`cache/ImageCacheManager.kt:456`）**非 UI 专用**（`work/AlbumCoverThumbWorker`、`service/LyricMediaNotificationProvider`、`MainActivity` 也消费）→ **不可搬 `ui.common.cover`**，须抽**中立门面/接口**；`LazyListPreloader`/`LazyStaggeredGridPreloader` 自身依赖 `cache.ImageCacheManager` → 先抽接口再搬（否则只是把违规挪到新路径并新增 `ui.common.cover→cache`）。
- **net-stack seam（28 条）**：`LibraryPresetStore` 整体下沉 data（−5）；AlbumDetail 家族 Gson 解析下沉（需核实解析对象）；OkHttp（图片下载/保存、站点探测）下沉 repository；`CloudSyncSelectionDialog.toHttpUrlOrNull`→util 纯函数；`SearchViewModel.retrofit2.HttpException`→repository 转 domain 错误。
- **DataStore 收口（12 条）**：main 8 条改经 `SettingsRepository`；搜索 3 条经 `SearchRepository`。
- **额外**：`LibraryTrackQuery.kt`（ui 构建 Room SQL）整体迁 `data/local/db/query`（−3）。

### B6 baseline 与守卫
- `import-direction-baseline.txt` ≈344 → 目标 **≤110**（B1 −83、倒挂 −2、DataStore −11、net −28 等；含 B3 补检测 +10）。
- `size-guard-baseline.txt` 不动（C 阶段）。
- **执行顺序约束**：B0 → B1 → B2/B3/B4/B5；**B5（repository 出口）须先于 §4 的 C1/C2**——否则 holder 以新路径复用旧穿透（旧 baseline 条目 dead + 新路径违规），等于"把违规换目录"而非真消。

**阶段 B 门禁**：全量测试双绿（基线只增不减）+ 子代理审查 `git diff refactor-r3/phase-A..HEAD` + 实机走查（库页/详情页/下载页/播放链）+ tag `refactor-r3/phase-B`。

## 4. 阶段 C —— 编排层 State Holder / 局部重写 与 God 收缩（P0-2）

### C0 重写 vs 抽取：取舍判定（开工前评估）

> 判据：只有当"**结构已结构性纠缠（增量修补只会继续加分支）+ 存在可一举降复杂度的目标架构 + 有/可建测试安全网 + 收益明显大于重写回归风险**"时，**内部重写**才优于抽取。

| 区域 | 现状证据（实测） | 判定 | 理由 |
|---|---|---|---|
| **AlbumDetailViewModel 状态机** | 2510 行/83 fun；**71 个状态读写点**（`Success` 28 写 + `as? Success` 43 读）；4 条 `ensure*Loaded` 并行状态机交织 token/job；26 字段 `AlbumDetailModel` | **重写（条件式）→ C8** | 抽取只是"把散落 mutableState 装进 4 个盒子"，71 读写点与 4 分支原样保留，可测性不增 |
| **搜索编排（Screen + VM）** | `SearchScreenContent` **单函数 ~1093 行**/54 remember·LaunchedEffect/20+ rememberSaveable 双向同步；`SearchViewModel` 四分支集中在 `fetchPage` 单链 + 15 mutable var | **重写 → C9** | 单函数 + 双向同步的补丁只会继续加 remember |
| LibraryViewModel 编排 | 2500 行/101 fun；四族混居，但共享树 helper 是天然 seam；已有删除族行为档案 + 仓储测试；状态面小（3 态） | **抽取**（C1 维持） | 重写删除族回归高、状态面小、无收益 |
| PlaybackService | 1471 行/59 fun，焦点/通知/歌词/统计强耦合；已有 3 个 seam 测试 | **抽取**（C7 维持） | 核心播放重写回归面极大、无目标架构收益 |
| SubtitleTaskService | 1429 行/71 fun，但 `SubtitleTaskState` **已是显式状态机** + 测试 | **抽取**（C7 维持） | 状态机已达标，是良好 seam |
| DownloadManager | 1122 行但仅 18 fun；`DownloadQueueCoordinator` 已解耦干净 | **抽取大纯函数**（C7 维持） | 协调器已达标 |
| main / Chrome | R2 已拆 16 文件；`MainContainer` 仅 2 fun | **修补/微抽取** | R2 已重写 |
| AlbumDetailScreen / DirectorySupport / DlsiteTabs | Screen 单函数 1271 行但 Hero/Header 已抽出（抽取在途）；DlsiteTabs 无 `viewModel` 引用（纯无状态） | **抽取**（C4 维持） | 延续既有抽取路线，重写无净收益 |

**结论：真正"重写 > 修补"的只有 C8（详情页 VM 状态机）与 C9（搜索编排）；其余维持抽取/修补，不扩为重写以避免过度重构。**

### C1/C2 两个 God VM 的 State Holder 抽取

放置：`ui/library/holder/`、`ui/library/albumdetail/holder/`；构造注入 Repository、暴露 `StateFlow`、VM 内 `by lazy` 或 `@Singleton`；VM 外壳保留 `uiState` 组合与生命周期。

- **C1 `LibraryViewModel` 2500 → 目标 ≤800**（A5 后实测）：`LibraryScanStateHolder` / `LibraryCloudSyncStateHolder` / `LibraryDeleteStateHolder` / `LibraryFilterStateHolder` / `LibraryTagStateHolder`。
- **C2 `AlbumDetailViewModel` 2510 → 目标 ≤800**：`DlsiteSectionStateHolder` / `AsmrOneSectionStateHolder` / `DownloadSelectionStateHolder` / `TreeStateHolder`。
  - ⚠️ 保留 VM 版 `applyResolvedCloudSync` 的 title 覆盖语义（与 repo 合并规则不同，**不可混用**）。

### C3 `LibraryWriteRepository` 1050 → 拆族
`TagWrite` / `DeleteWrite` / `ScanWrite` / `OnlineSave`。

### C4 God 文件区块化（≥1500 全部纳入）
`AlbumDetailDirectorySupport` 2700、`DownloadsScreen` 2281、`SearchScreen` 2186（`SearchScreenContent` 单函数 **1086 行**/19 形参 → 拆子 composable + 状态对象；**该文件的完整重写见 C9**）、`AlbumDetailDlsiteTabs` 1932、`LibraryScreen` 1582（`LibraryScreenContent` 914）。
**补充（开工前评估发现，否则 C5 不可达）**：`AlbumDetailScreen` 1518（单函数 **1271 行**，是 ">1500 清零" 的必达前提）、`ui/settings/SettingsScreen` 1270（单函数 1101）、`ui/common/audio/EqualizerPanel` 1190（单函数 1140）——三者同为"单巨型 Composable"，拆法同构（区块化 + 状态对象）。

### C5 ratchet 收紧（依赖 C1/C2/C3/C4/C7/C8/C9；分级目标）
- **第一级**：**>1500 清零**（含新纳入的 `AlbumDetailScreen`）；每拆完一批即从 `size-guard-baseline.txt` 移除对应 pin 并贴实测收缩 cap。
- **第二级**：处理 **1000–1500 区间**（14 个文件，现完全不受守卫，见 forecast §4）后，把 `SIZE_LIMIT` 从 1500 下调。
- **第三级**：向 800 逼近，以实际进度为准、不强达。
- ⚠️ **不可直接降 `SIZE_LIMIT` 到 ≤1000**：会令上述 14 个未纳入文件全部违约；须先保证"不再新增超限"，再分批下调。

### C6 结构收尾
`walkTree`/`scanFromDocumentTree` 拆函数、Chrome 概念归包（`main` 与 `ui/nav/BottomChrome.kt`）、dao 投影 DTO 归位。

### C7 service 层 God 拆解（开工前评估新增）
`service/PlaybackService` 1471（59 fun，MediaSession/播放链/DB）、`subtitle/SubtitleTaskService` 1429（71 fun，前台服务/DB/SAF）、`data/download/DownloadManager` 1122（下载/DB/SAF）——三者强耦合、原计划只字未拆，须与 A4 seam 测试合并立项（无拆解则 A4 无稳定测点，测试欠账无从偿还）。

### C8 详情页 VM 状态机重写（条件式；行为安全网先行）
- **目标**：单一不可变 `AlbumDetailUiState`（分区 sub-state 取代 26 字段 `AlbumDetailModel`）+ `LoadPhase{Idle,Loading,Loaded,Failed}` 取代 4 组 token+job+bool；holder 各持**纯 reducer** `(State,Event)->State`；树/滚动抽 `TreeSessionHolder`。目标消除 71 个状态读写点的大半。
- **前置（硬性）**：① 新建 `AlbumDetailViewModelTest`（fake repo 收 `uiState`，钉三路 `ensure*Loaded` 的时序/幂等/去重/token 竞态——**现不存在，VM 本体无直测**）；② 录行为档案（`applyResolvedCloudSync` title 覆盖、双 `ensureAlbumCoverSaved`）；③ **测试基建已定**：遵守"不引入新依赖"，不新增 coroutines-test/Turbine，沿用既有 `runBlocking` + 真实时间小超时模式（见 §0-6 / §7）。
- **步骤**：新旧 reducer 并存 → 暗影比对（同一 Event 驱动、比对 state）→ 逐 tab 切换 → 删旧路径。
- **风险**：ensure 防抖/取消语义、listentogether 60s 轮询、云同步 title 覆盖语义。
- **验证**：reducer 表驱动测试 + 实机（`start_route` 直达 DL tab / ASMR.ONE / 本地 tab）。
- **与 C2 的关系**：C8 **取代** C2 的"纯抽取"；风险不可控时回退为 C2 抽取。

### C9 搜索编排重写
- **目标**：`SearchScreenContent`（单函数 ~1093 行/19 形参）→ 区块化子 composable + 状态对象；`SearchViewModel` 四分支（purchased/collected/直 RJ/默认）→ 策略/UseCase 分层（消除 15 mutable var 交织）。
- **前置**：补四分支 seam 测试（各分支命中与 locale 回退链）。
- **风险**：`rememberSaveable` 双向同步、筛选状态回填。
- **验证**：分支表驱动测试 + 实机走查搜索四态。
- **与 C4/B5 的关系**：C9 **取代** C4 中 SearchScreen 的"区块化"表述，并承接 B5 的 `SearchViewModel` 穿透收口。

**阶段 C 门禁**：全量测试双绿 + 子代理审查 + 实机全链 smoke + tag `refactor-r3/phase-C`。

## 5. 行为档案机制

沿用 R2 §5。R3 新增触发条件：消环或抽 State Holder 触及"看似不平衡的既有清理"（如 `deleteAlbum` 不清孤儿表）时，**先录档案再动手**（已录行为勿顺手清理）。

## 6. 风险与缓解

| 风险 | 缓解 |
|---|---|
| State Holder 抽取致 UI 状态机行为回归 | 抽前录行为档案 + 每 holder 钉 seam 测试 + 分批提交 + 实机对照 |
| 消环改动面广（11 组）+ 多跳环未断 | 逐环独立提交，单环可独立回退；B0 加 SCC ratchet 冻结规模，明确"本轮只消 2-环 + 冻结大团" |
| 移包后 baseline 条目失配（dead / 报错） | B0 增失效条目检测 + 机械重写脚本；每个迁移任务把"同步 test/androidTest import + 重键 baseline"列为验收项 |
| B5 未清穿透即做 C1/C2（违规换目录） | 顺序约束：B5 先于 C1/C2；holder 落包前确认无 dao/entity/okhttp 残留 |
| ui 穿透收口触及大批文件 | 按 repository 出口分族、逐族收缩 baseline，禁止一次性大改 |
| 开启 schema 导出暴露历史迁移缺陷 | 近 3 版本优先补测试，缺陷单独记录不夹带修 |
| ratchet 收紧过急阻塞 | 只在 C1/C2/C3/C4/C7/C8/C9 完成后逐批收紧（分级目标见 §4 C5），不设"一步到 800" |
| 重写（C8/C9）致行为回归 | C8/C9 均**前置 seam 测试 + 行为档案**，新旧并存暗影比对后再切换；风险不可控则 C8 回退为 C2 抽取 |
| 与用户设备走查冲突 | 走查前约定前台切换时机（用户可能正在游戏） |

## 7. 记录但不改动项（用户 2026-10-04 决策）

- **`ensureAlbumCoverSaved` 双实现**：VM 版（仅网络 / 2048 / ARGB_8888）vs repo 版（支持本地来源 / 1280 / RGB_565）——**保留现状**，录行为档案并在 backlog 标注，不统一。
- **LICENSE / CHANGELOG / CONTRIBUTING**：本轮不加不建，留 P2 backlog。
- **Konsist/ArchUnit**：不引入，沿用 python `ci_guard.py`。
- **重写测试基建（C8 前置）**：**已定（2026-10-04）**——遵守"不引入新依赖"，不新增 coroutines-test/Turbine；沿用项目既有 `runBlocking` + 真实时间小超时模式（与 B2c 一致）。

## 8. 执行第一步与验收

**执行顺序**（每步一提交，编号开头）：
1. 本文件落盘为 `docs/refactor-plan-r3.md`。
2. A1→A5 实施阶段 A，过门禁打 `refactor-r3/phase-A`。
3. B1 逐环 → B2–B5 穿透收口 → B6 baseline 收缩，过门禁打 `refactor-r3/phase-B`。
4. C1/C2 State Holder → C3/C4/C7 拆族 → C8（详情页 VM 重写，条件式）→ C9（搜索重写）→ C5 ratchet → C6，过门禁打 `refactor-r3/phase-C`。
5. 终态：ARCHITECTURE.md §7 全面同步（阶段完成记录 + backlog 清账）。

**验证方式（每阶段复跑）**：
- `\.gradlew-local.bat -g "C:\Users\24131\.gradle" :app:testDebugUnitTest` — 全绿、只增不减（当前基线 **945**）。
- `python tools/ci_guard.py`（含规则自检）通过；两个 baseline 按实测收缩。
- 子代理只读审查 `git diff <上阶段tag>..HEAD`，报告落 `docs/iteration/r3-phase-N-review.md`。
- 实机走查（小米 14）：库页 / 详情页（DL tab / ASMR.ONE）/ 下载页 / 播放链，`adb shell am start --es start_route "<route>"` 直达取证。
