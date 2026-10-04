# 项目质量体检报告（2026-10-04）— EaraAsmrPlayer

> 独立质量体检（project-quality-review 流程）。按委托要求**未参看任何重构工作过程文档**：`docs/devnote/`、`docs/iteration/`、`docs/behavior-notes/`、`docs/refactor-plan-r2.md`、既往 `docs/project-quality-review-*.md` 均未读取；`docs/ARCHITECTURE.md` 仅采用 §1–6 作结构索引且逐条回源码核实（§7 起未采用）。全部结论以源码、构建/CI 配置与实跑测试为证据。

- **审查基线**：分支 `refactor/architecture-cleanup` @ `4920f37`，工作树干净
- **代码规模**：`app/src/main` 467 个 .kt（单 module `:app` + `:baselineprofile`）；单测 184 文件、androidTest 18 文件
- **测试健康度**：`:app:testDebugUnitTest --rerun` 实跑成功——**938 用例 / 0 失败 / 4 跳过**（1m32s；跳过 = 2 个 opt-in 延迟基准 + 2 个门控用例）
- **方法**：7 个维度（架构设计 / 需求实现度 / 质量属性 / 代码坏味道 / 目录结构 / 文档化 / 度量与演化）独立子代理并行审查，主会话对 P0/P1 级声明做 20+ 项人工复核；只读分析，未修改任何代码。

## 总体评级

**C — 需要专项治理**：工程护栏扎实（CI 架构守护、938 用例全绿、Baseline Profile、发布校验完备），但结构层已达需专项治理的量级：**8 组包级依赖环**（含 data↔cache↔playback 核心三角）、**约 20 个 ≥1000 行文件（≈4%）**、**ui 层四类穿透**、**文档与实现系统性漂移**。

| 维度 | 评级 | 摘要 |
|---|---|---|
| 架构设计 | C | 分层主体方向被维持（包名规范、数据层反向依赖少）；但 8 组包级环、ui 四类穿透、God 文件聚集 |
| 需求实现度 | B | README 25 条功能声明多数有可定位实现；1 条与实现相反（m3u8）、3 项隐含前置未注明 |
| 代码坏味道 | C | 上帝类与单函数近千行的巨型 Composable、若干跨文件重复实现；死代码/注释代码未成规模 |
| 目录结构 | B | 目录-包名 100% 对齐、ignore 规则干净；宽目录、benchmark 源集错位、docs 平铺待细化 |
| 文档化 | C | README 准确；ARCHITECTURE 行数漂移、tutorial 陈旧（且未入库）、缺 LICENSE/CHANGELOG、子系统文档无入口 |
| 度量与演化 | C | 依赖环 8 组；≥1000 行约 4%；高频变更∩超大文件交集明显；测试分布失衡 |

## 项目画像

Kotlin 1.9.22 + Compose（BOM 2024.02/M3）+ Media3 1.8.0 + Hilt 2.49 + Room 2.6.1（DB v31）+ WorkManager + Paging3 + Retrofit/OkHttp/Jsoup/PDFBox。`compileSdk 36 / target 34 / min 24`，版本 v1.2.3（versionCode 10203）。CI 双工作流：`ci.yml`（架构守护 → androidTest/baselineprofile 编译 → `testDebugUnitTest`）；`release.yml`（`testReleaseUnitTest` → 签名 Release → APK ≤20MB 且禁含 sherpa-onnx 运行时校验）。git 801 commits（2026-03-10 起）。入口：`MainActivity` → `main/MainContainer`（路由宿主）→ `ui/**`；播放链 `PlayerConnection` → `PlaybackService`（MediaSessionService）。

## 质量属性评估

### 内部质量指标

| 指标 | 评级 | 证据 |
|---|---|---|
| 可维护性 | 差 | 约 20 个 ≥1000 行文件、单函数近千行；8 组包级环；热点∩超大交集明显 |
| 可重用性 | 中 | 音效插件链（`RuntimeAudioProcessor` 7 实现、注册点唯一）、repository 出口是好接缝；translation/hotlistening 反向依赖 UI 阻断复用 |
| 可移植性 | 中 | 构建配置全参数化（URL/签名/模型源可覆盖）；本机脚本（`gradlew-local.bat`）与可移植脚本分离 |
| 可集成性 | 中 | CI 完善；但 Room 无 schema 导出（`exportSchema=false`，`app/src/main/java/com/asmr/player/data/local/db/AppDatabase.kt:92-93`），迁移漂移无编译期拦截 |
| 可测试性 | 好 | 938 用例全绿、Robolectric+MockWebServer 基建、纯函数支撑层与接缝测试有先例 |

### 外部质量指标（运行期，标注推断）

| 指标 | 架构支撑度 | 关键证据 / 场景 |
|---|---|---|
| 性能 | 好（含 1 项短板） | Baseline Profile 7 场景采集、Paging3、缓存预算制；短板：首帧等待 DataStore 串行读（`MainActivity.kt:238-247`，推断） |
| 可靠性 | 中 | 播放恢复指数退避、下载断点续传、字幕任务可检查点；短板：SleepTimer 仅内存态、恢复耗尽仅 Log（`PlaybackService.kt:854-860`）、弱网无降级 |
| 可用性 | 中 | 大屏/横屏适配成体系；短板：断网仅提示、`allowBackup=false` 且无导出路径 |
| 安全性 | 中 | Release 校验（体积/运行时自检）+ keystore 优先级链清晰；`allowBackup=false` 属隐私向决策；无 LICENSE 致权利状态不明 |
| 易用性（运维） | 中 | 发布流程固定；缺 CHANGELOG/CONTRIBUTING，无版本演进对外说明 |

### 关键场景支撑度（简化质量效用）

| 场景 | 支撑度 | 关键证据 |
|---|---|---|
| 冷启动/首屏 | 强 | BaselineProfileGenerator 7 场景、startup-prof 入库 |
| 大列表滚动 | 强 | Paging3（pageSize 40/prefetch 10/cachedIn）、key+contentType、@Immutable/@Stable 34 处 |
| 播放长稳/中断恢复 | 强 | 音频焦点全态、PlaybackRecoveryPolicy、SleepTimer、前台通知 |
| 弱网/断网 | 中 | LRU 缓存 50–1000MB、Range 断点续传；无 metered 降级分支 |
| 大屏/多形态 | 强 | EaraWindowSize 三族断点 + 横竖屏专用布局 |
| 后台长任务 | 中 | 前台 dataSync+wakelock+可检查点；空载耗电与杀进程丢任务风险 |
| 持久化/迁移 | 中 | 6 段迁移测试；无 schema 快照、v1–v19 无回归证 |
| 无障碍 | 中 | contentDescription 约 227 处/40 文件、触控目标 49 处；无自动化断言 |

- **敏感点**：播放焦点/音量状态机（PlaybackService）、缓存预算与 Settings 的耦合、AlbumDetail 三路加载状态模型。
- **权衡点**：单 module（构建简单 ↔ 编译面大）；缓存字节预算制（内存 ↔ 流量/速度）——均为有意识选择。
- **风险决策**：`exportSchema=false` + `allowBackup=false` 组合（迁移与换机双风险）；DeepSeek Key 用户自备（可用性门槛未在 Features 言明）。

## 需求实现度

- 声明来源：README.md Features（约 25 条）+ 构建配置；无独立需求文档。

| 声明（分组） | 状态 | 证据要点 |
|---|---|---|
| Media3 播放 / Compose UI / 响应式三形态 | 已实现 | PlayerConnection、PlaybackService、EaraWindowSize、横屏/平板专用布局 |
| 本地库管理 / 播放列表分组 | 已实现 | LibraryScreen/VM、LibraryFilterSheet、playlists/、groups/ |
| 在线发现（DLsite/asmr.one）/ 推荐 | 已实现 | SearchViewModel 四分支、DLSiteScraper、AsmrOneCrawler、推荐会话缓存 |
| 歌词（LRC/VTT/SRT）/ 字幕生成与 AI 翻译 | 已实现（含前置） | SubtitleParser、ParakeetEngine、SubtitleTaskRepository resume/retry；前置：运行时/模型按需下载、DeepSeek Key 自备 |
| 音效链 / 双声道频谱 / 切片循环 | 已实现 | 7 个 AudioProcessor、StereoFftAnalyzer、SliceLoopEngine |
| 后台下载 / 收听面板 / 一起听 / 主题防烧屏 | 已实现 | DownloadManager、ListeningCalendarScreen、ListenTogetherRepository、OledBurnInProtection |
| 视频播放含 **m3u8 流** | **部分实现（声明不符）** | `playback/PlayerConnection.kt:292-293` 明确拒绝 m3u8（"当前不支持 m3u8 流媒体"），依赖无 `media3-exoplayer-hls` |
| 睡眠定时 / 通知后台控制 / 应用内更新 | 已实现 | SleepTimerManager、通知 Provider、UpdateRepository |

- **双向脱节信号**：声称有而实现相反 1 项（m3u8）；实现而无声明未发现。全仓 TODO/FIXME 为 0，`placeholder` 命中均为设计性占位。

## P0 结构性问题

### P0-1 跨包依赖环 8 组（核心三角：data ↔ cache ↔ playback）

- **证据**（均已核实边）：
  - 三角环：`cache/AppCacheManager.kt:5-6` → data.settings.SettingsRepository / playback.PlaybackMediaCache；`data/settings/SettingsRepository.kt:6` → cache.AppCacheLimits；`data/repository/OnlineContentRepository.kt:9` → cache.AppCacheManager；`playback/PlaybackMediaCache.kt:9` → cache.AppCacheLimits
  - `di ↔ subtitle`：`di/NetworkModule.kt:26` ↔ `subtitle/SubtitleTaskService.kt:35`、`subtitle/DeepSeekAccountRepository.kt:5`
  - `data ↔ work`：`data/download/DownloadManager.kt:28` ↔ work.AlbumCoverThumbWorker
  - `data ↔ listentogether`：`data/remote/api/AsmrOneAvailabilityApi.kt:9` ↔ `listentogether/ListenTogetherRepository.kt:5-6`
  - `data ↔ hotlistening`
  - `main ↔ ui.player`：main 10 文件 67 处 import ui.player；ui/player 7 文件 import main.HardwareVolumeOverlay（如 `ui/player/NowPlayingScreen.kt:85`）
  - `ui.common ↔ ui.theme`：`ui/theme/MonetSeedColor.kt:10` → ui.common（正向 22 处）
  - `ui.library ↔ ui.library.albumdetail`
  - 另有反向倒挂：`translation/PageTranslationUi.kt:21` → ui.theme；`hotlistening/ListeningTracker.kt:5` → ui.player
- **分析**：与项目自身声明的分层方向（`ui → playback/data/domain`、`data → domain/util` 等）相抵；环内包无法独立测试/替换，改动双向扩散。data 是最高扇入层（被 15+ 包引用），与 cache/playback 双向绑定会全系统传染。对照架构检查清单"循环依赖"判定。
- **改进方案**：接口倒置——缓存预算/失效抽象下沉 `domain`；`HardwareVolumeOverlay`、`toThemeMediaSource`、`isOnlineMedia` 等下沉 `ui.common`/`util`；DI 常量（`DEEPSEEK_HTTP_CLIENT` 等）迁中立包或改 qualifier 注入；worker 反向依赖改回调/接口。目标：8 组环归零并以 CI 规则锁死。

### P0-2 God 文件群：约 20 个 ≥1000 行文件、单函数近千行

- **证据**（总行数口径）：`ui/library/albumdetail/AlbumDetailDirectorySupport.kt` 2700、`ui/library/LibraryViewModel.kt` 2521、`ui/library/AlbumDetailViewModel.kt` 2510、`ui/downloads/DownloadsScreen.kt` 2281、`ui/search/SearchScreen.kt` 2186、`albumdetail/AlbumDetailDlsiteTabs.kt` 1932、`ui/library/LibraryScreen.kt` 1582、`ui/library/AlbumDetailScreen.kt` 1518、`service/PlaybackService.kt` 1478、`subtitle/SubtitleTranslationClient.kt` 1451、`subtitle/SubtitleTaskService.kt` 1429 等；`SearchScreenContent` 单函数自 `SearchScreen.kt:367` 起约 1000 行、形参 19 个。
- **分析**：God 文件与 git 热点高度重合（AlbumDetailScreen 126 次、LibraryViewModel 79、AlbumDetailViewModel 78）——"高频变更 ∩ 超大文件"是复杂度与回归风险的集中区；对照坏味道清单"上帝类（>500 行且多职责）"。
- **改进方案**：按职责抽 state holder/use case（数据加载、封面、下载入队、扫描、字幕工具），目标单文件 ≤800 行；优先级按热点排序；先拆两边 2.5K 行 VM 与 `SearchScreenContent`/`LibraryScreenContent` 巨型 Composable。

### P0-3 ui 层四类穿透

- **证据**：① ui → `data.local.db.*` **110 处 import / 35 文件**（重灾区 AlbumDetailViewModel 11、LibraryViewModel 9、AlbumDetailDirectorySupport 8；含 Room 实体/查询类型，DAO/AppDatabase 直引为子集）；② ui → 网络栈 `okhttp3/retrofit2` **12 处 / 7 文件**（如 `ui/library/LibraryViewModel.kt:87-88`）；③ ui → `cache/work` **35 处 / 20 文件**；④ ui → `service` 7 处 / 6 文件，另有 DataStore 直引（SearchCacheStore、SettingsDataStore）。
- **分析**：违反项目声明的分层（分层被穿透属结构性 P0）；UI 与存储/网络/服务实现绑定，替换与测试成本上升。
- **改进方案**：按 repository 出口逐包收口（数据层已有 `LibraryReadRepository`/`LibraryWriteRepository` 样例可复用）；CI 导入守护扩展覆盖 `cache/work/okhttp/retrofit` 与 main 包盲区。

## P1 维护性问题

1. **需求声明与实现不符 + 前置未注明**：m3u8 声明不符（见需求矩阵）；DeepSeek Key 自备、模型按需下载未在 Features 提示；建议修正 README 并加空态引导。
2. **文档-实现系统性漂移**：ARCHITECTURE §2 称 MainContainer 约 2700 行（实测 798）、§3 家族行数大面积漂移（ViewModel 2996→2510 等，合计 15450→约 14821）；tutorial.md 包名约定错误（"main/ 声明 com.asmr.player"）、基线 880 vs 938、行号引用失效；landing_zh.md `compileSdk=34` vs 实际 36。建议一次回填并以符号引用替代行号。
3. **测试分布失衡**：service 仅 1 个测试文件（对应 1478 行 PlaybackService）、translation 4 个；data 约 28%、playback 约 35% 的测试/主文件比；androidTest 18 文件仅编译不运行；无 e2e/无障碍自动化。
4. **Room 无 schema 导出**（`exportSchema=false` + v31 + 迁移测试仅覆盖 6 段）：迁移漂移无编译期拦截；建议 `exportSchema=true`、schema JSON 入库并纳入 CI diff。
5. **跨文件重复实现**：`centerCropSquare` 三份（`data/remote/CoverSupport.kt:13` / `ui/library/LibraryViewModel.kt:1448` / `work/AlbumCoverThumbWorker.kt:62`）；头部合并"优先非空"规则两份（AlbumDetailViewModelSupport.kt:329-350 与 401-418）；`normalizeRelativePath` 两份且语义不同（`util/TrackKeyNormalizer.kt:50` / `albumdetail/AlbumDetailDirectorySupport.kt:518`）。
6. **运行时可用性缺口**（质量属性项合并）：弱网无降级分支；播放恢复耗尽仅 `Log.e`（无用户反馈/手动重试）；SleepTimer 仅内存态；字幕前台服务全生命周期 wakelock。

## P2 改进项

1. 首帧等待 DataStore 串行读（`MainActivity.kt:238-247`）→ 先 `setContent`、主题后置。
2. 魔法 URL/字符串未收敛（`https://play.dlsite.com/...` 8+ 处、`asmr.one/work/$id`，`NetworkHeaders` 已有常量未用全）。
3. 浅包装/命名失真：`normalizeWorkNo`→实调 `extractWorkNo`；`extractWorkNo` 直转。
4. 长参数列表（`SearchScreenContent` 19 参）→ 状态对象。
5. 注释代码残留（`ui/common/audio/EqualizerPanel.kt:1072` 单行）；空 catch 无注释（`data/repository/OnlineContentRepository.kt:503-506`）。
6. 宽目录：`ui/player` 33 文件、`playback` 31 文件 → 按 nowplaying/cover/lyrics、processor/spectrum 子包化。
7. benchmark Harness 4 文件位于 main 源集（专用源集仅 res 覆盖）→ 迁 `benchmark` 源集。
8. `docs/` 平铺无索引；缺 LICENSE/CHANGELOG/CONTRIBUTING；子系统文档（page-translation、multiline-subtitles）无任何入口。
9. tutorial.md 被 `.git/info/exclude` 排除（游离版本控制）且内容陈旧 → 入库刷新或删除。
10. 根目录本机脚本（`gradlew-local.bat`、`gradle-clean-local-cache.bat`）与素材（example_screen/、logo）组织细化。
11. 单文件小包碎片化（ui/splash、ui/drawer、ui/update、performance、data/local/db/query）。
12. 无障碍/多形态无自动化断言（androidTest 18 文件仅编译）。

## 技术债清单

| 类型 | 债务项 | 证据位置 | 优先级 | 建议偿还时机 |
|---|---|---|---|---|
| 设计债 | 8 组包级依赖环 + ui 四类穿透 | P0-1 / P0-3 各边 | P0 | 专项治理第一阶段 |
| 代码债 | ~20 个 ≥1000 行文件、单函数近千行 | P0-2 清单 | P0 | 随迭代拆分，先热点 |
| 设计债 | Room 无 schema 导出、SleepTimer 非持久 | `AppDatabase.kt:92-93`；SleepTimerManager.kt | P1/P2 | 下个版本窗口 |
| 代码债 | centerCropSquare ×3、合并规则 ×2、归一化 ×2 | P1-5 清单 | P1 | 即期（小时级） |
| 测试债 | service/translation/data 覆盖缺口；androidTest 不运行 | P1-3 | P1 | 第二阶段 |
| 文档债 | ARCHITECTURE/landing/tutorial 漂移；缺 LICENSE/CHANGELOG | P1-2；P2-8 | P1 | 即期（Quick Win） |

## Quick Wins

1. **修 README m3u8 声明**（删声明或引入 HLS 依赖）：消除用户预期与实现相反的信任损伤。
2. **回填 ARCHITECTURE §2/§3 行数 + 统一测试基线（938）**：防"文档比没有更糟"。
3. **补 LICENSE + 最小 CHANGELOG/CONTRIBUTING**：仓库对外分发 APK，权利状态与变更入口缺失是低成本可补项。
4. **`centerCropSquare` 三份合并为 `CoverSupport` 单一实现**：已有测试网，改动小、消除三处漂移源。
5. **空 catch 补注释/日志 + `play.dlsite.com` 魔法串收敛到 `NetworkHeaders`**：纯机械收敛，降低故障排查成本。

## 改进路线图

- **第一阶段 — 消除 P0**：依赖环消解（接口倒置 + CI 环检测规则）；ui 穿透按 repository 收口；God 文件按热点优先级拆解（两 God VM、`SearchScreenContent`/`LibraryScreenContent` → 单文件 ≤800）。预期效果：包边界恢复单一方向，热点文件变更不再跨包扩散。
- **第二阶段 — 治理 P1**：Room schema 导出入库；为 service/data/translation 补关键测试；重复实现合并；文档一次回填 + LICENSE/CHANGELOG；androidTest 关键路径接入运行（至少多形态与无障碍冒烟）。
- **第三阶段 — 优化 P2**：宽目录/源集/文档目录细化；弱网降级、播放恢复反馈、SleepTimer 持久化等运行时改进；无障碍自动化断言。
- **腐蚀专项（三策略）**：预防——CI 守护扩环检测与 ui 出口禁令（覆盖 main 包盲区）；修补——上述分阶段；最小化——对热点 ∩ 超大文件先冻结增长（现有行数 ratchet 思路可延伸），再拆解。

## 方法与口径

- **执行方式**：7 个维度独立子代理并行审查（每维度对照 project-quality-review 对应检查清单），主会话汇总；机械检查（大文件/TODO/深目录/文档缺失）与 git 热点作为输入线索。
- **复核说明**：对 P0/P1 级声明做 20+ 项独立复核（依赖环各边、m3u8 拒绝、ui 穿透计数、exportSchema、首帧串行、空 catch、重复实现、文档漂移抽样等），全部属实；子代理 2 处口径差异（行数含空行 vs 非空行、ui.library 文件计数含/不含子包）已统一。
- **行数口径**：含空行总行数（与项目 mechanical 扫描一致）；"约 20 个 ≥1000 行"为度量统计（基数约 465 文件）。
- **测试口径**：`--rerun` 强制实跑（非缓存结果）；跳过 4 例为 2 个 opt-in 延迟基准 + 2 个门控用例。
- **本次为只读分析，未修改任何代码。**