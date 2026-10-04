# R3 依赖与 God 类整治：现状 → 预测 对比 + 计划校准

> 目的：在动手前整理**当前**全项目依赖、**预测**改动后的依赖，并据此查计划疏漏、优化方案。方法：脚本构图（包级 import 图 + Tarjan SCC + 符号直方图）+ 3 组只读子代理复评。基线：`refactor-r3/phase-A`（测试 945/0/4，import baseline 344 条，size pin 8 条）。

## 1. 当前依赖快照（实测）

- **规模**：61 个包（含根包 `com.asmr.player`）；内部 import 边数百。
- **最高扇入**（被多少包 import）：`util` 37 · `domain.model` 23 · `ui.theme` 22 · `data.settings` 21 · `db.entities` 21 · `data.remote` 20 · `db` 17 · `ui.common.core` 17 · `ui.common.cover` 17 · `db.dao` 15 · `cache` 15。
- **最高扇出**：`ui.library` 37 · `main` 31 · 根包 30 · `ui.library.albumdetail` 28 · `benchmark` 22。
- **包级 SCC = 1 个 48 包巨型连通团**（`cache/data.*/di/domain.model/hotlistening/listentogether/main/playback/service/subtitle/translation/ui.*/util/work` 互相可达）。
  - ⚠️ **关键**：体检报告的"11 组环"只是其中可读的 **2-环子集**；真实结构是"一个大环团"。只消 2-环**不会解散该团**。
- **11 个 2-环**：`main↔ui.player`、`ui.common.cover↔ui.theme`、`ui.library↔ui.library.albumdetail`、`cache↔data.settings`、`cache↔playback`、`di↔subtitle`、`data.remote↔data.repository`、`data.download↔data.local.library`、`data.download↔data.remote.download`、`ui.player↔ui.player.nowplaying`、根包`↔ui.common.cover`。

## 2. 现状 → 预测（按 R3 阶段 B 的动作）

| 动作 | 消除的边 / 环 | baseline 净变化 | SCC 影响 |
|---|---|---|---|
| **B1 纯类型下沉** | ui→db 110→**58**（−52）；ui→cache/work 50→**24**（−26）；ui→service 7→**2**（−5） | **−83** | 不影响（边变弱，不断团） |
| **B2 低风险消环** | 断环 #1/#2/#4/#5/#6/#11 | −2（倒挂） | **团仍在**（多跳环未断） |
| **B3 守卫补强** | 扩 `ui-to-service` 源含 `main` | **+10（反增）** | 无 |
| **B4 接口倒置** | 断环 #7/#8 | 0 | 团仍在 |
| **B5 穿透收口** | ui→db 余 58、ui→cache/work 余 24、ui→net-stack 28、ui→datastore 12 | 逐步下降 | 部分断 |
| **B6 目标** | — | 344 → **≤100** | — |

**预测结论（重要）**：即使 B1–B6 全部完成，**包级 SCC 仍大概率保持为一个大团**——因为存在多跳环（示例，均实证）：
- `data.download → data.remote → data.repository → data.download`（B4 只倒置 traffic 边，未断 `repository→download`）；
- `data.remote → data.settings → hotlistening → data.remote`；
- `root → ui.* → root`（经 `R`/`BuildConfig`，结构性，无法消）。

→ **"消环"若无可验收指标，等于无目标**。计划必须补：**包级 SCC ratchet**（把当前规模设为上界，只许减不许增）作为断环的验收手段。

## 3. 计划疏漏清单

### P0
1. **多跳环完全未覆盖**：计划只消 2-环，且 `ci_guard.py` **无任何包级环检测**。→ 新增 SCC 检测规则（当前 48 包团设为上界 ratchet），并在 B4/B5 中列出至少 3 条代表性长环的打断路径。
2. **C5 目标不可达**：`ui/library/AlbumDetailScreen.kt`（1518 行，含**单函数 `AlbumDetailScreen` ≈1271 行**）**未被 C1/C2/C4 纳入** → ">1500 清零"必不达标。
3. **baseline 重键步骤缺失**：`import-direction-baseline.txt` 为"文件路径 + fq"字符串集合；B1 把符号移到新包后，**旧条目变 dead（静默遗留）、新 fq 不在 baseline 即 CI 失败**。计划未写"机械重写 baseline"；`size-guard-baseline.txt`（路径:cap 键）在 C4/C6 拆文件后同理需重键（且失配仅 print 不 fail → 无检测）。→ 增"失效条目检测"与重键步骤。

### P1
4. **迁移连带未评估**：① `LibraryQuerySpec.kt` 与 SQL 的 `LibraryQueryBuilder` **同文件**（`query/LibraryQuerySpec.kt:6/37/44/59`，含 `androidx.sqlite` import）→ 下沉必须先拆文件；② `titleForDisplay`（`entities/DisplayTitleSupport.kt`）是**@Entity 扩展**，ui 用 7 处、data/subtitle 也用 → 实体不迁则此扩展无法迁 domain，ui→data 残留；③ `domain.model` **非纯叶子**（`domain/model/Track.kt`→`util.RemoteSubsystemSubtitleSource`），且已在 SCC 内，塞类型进 domain 不保证消环。
5. **B5 手段需修正**：`ImageCacheEntryPoint`（`cache/ImageCacheManager.kt:456`）**非 UI 专用**（`work/AlbumCoverThumbWorker.kt:10`、`service/LyricMediaNotificationProvider.kt:23`、`MainActivity.kt:310` 均消费）→ **不可搬 `ui.common.cover`**（会造 work/service/main→ui 违规），应留 cache 或抽中立门面；`LazyListPreloader.kt`（含 `LazyStaggeredGridPreloader`）自身 `import cache.ImageCacheManager` → 仅搬文件是"把违规挪到新路径 + 新增 `ui.common.cover→cache`"，须先抽接口。→ B5 中 cache/work 的"门面"路线需具体化，不能简单搬类型。
6. **测试 import 同步未列**：test + androidTest **≥20 处**引用被迁符号（如 `TreeFileTypeResolverTest`、`AlbumDetailDirectorySupportTest`、`androidTest/LibraryPagingStressTest`）→ 移包必须同步，androidTest 在 CI 编译，漏改即红。
7. **B3 与 B6 未合账**：扩 `ui-to-service` 含 main 会先使 baseline **+10**，与"≤100"目标冲突，须显式合账。
8. **C1/C2 顺序依赖 B5 未标**：holder 落 `ui.library[.albumdetail].holder`，若 B5 未清 dao/entity/okhttp，holder 会**以新路径复用旧穿透**（旧 baseline dead + 新路径违规）→ 实质是把违规换目录。故 **C1/C2 依赖 B5 出口**。

### P2
9. **文档数字漂移**：计划 §0"baseline 196" vs §B"≈344"；§8"938" vs 阶段 A 实为 **945**；§4 C1/C2 标 VM 2521/2510 vs 实为 **2500/2510**（A5 后）。
10. **14 个 God 文件未纳入**（见 §4）。

## 4. God 类：现状 → 预测

**计量校准**：`SIZE_LIMIT=1500`，**仅 >1500 的 8 个文件被 pin**；1000–1500 的 14 个文件**完全不受守卫**。`PlayerConnection.kt` 实测 916、`PlayerViewModel.kt` 实测 908（均 <1000，非 God）。

| 文件 | 现状 | 计划覆盖 | 拆后预测 | 缺口 |
|---|---|---|---|---|
| AlbumDetailScreen | 1518（单函数 1271）| **未提** | 不拆则恒 >1500 | **C5 不可达的根因** |
| LibraryViewModel | 2500（101 fun）| C1 明确 | ≤800 + 5 holder | 依赖 B5 出口 |
| AlbumDetailViewModel | 2510（83 fun）| C2 明确 | ≤800 + 4 holder | 依赖 B5 出口 |
| AlbumDetailDirectorySupport | 2700（78 fun）| C4 泛泛 | 多文件，部分仍 >800 | SAF/树/DB 强耦合 |
| DownloadsScreen | 2281 | C4 明确 | 可行 | — |
| SearchScreen | 2186（`SearchScreenContent` 1086 行/19 参）| C4 点名 | 拆后 <800 可期 | — |
| AlbumDetailDlsiteTabs | 1932 | C4 明确 | 子文件恐仍 >800 | — |
| LibraryScreen | 1582（`LibraryScreenContent` 914）| C4 明确 | <800 可期 | — |
| PlaybackService | 1471（59 fun）| **仅 A4 补测** | 未列拆 | 播放链/DB 强耦合，最该拆却未列 |
| SubtitleTranslationClient | 1451（20 类）| 未提 | — | 中风险 |
| SubtitleTaskService | 1429（71 fun）| 未提 | — | 前台服务/DB/SAF，高耦合 |
| BottomChrome | 1378（`PillSurface` 347）| C6 仅"归包" | 不拆函数 | — |
| SearchViewModel | 1347 | 仅 B5 穿透 | 未列拆 | — |
| NowPlayingControls | 1298 | 未提 | — | — |
| ListeningCalendarScreen | 1293 | 未提 | 已细粒度，风险低 | — |
| SettingsScreen | 1270（**单函数 1101**）| 未提 | — | 与 AlbumDetailScreen 同构 |
| EqualizerPanel | 1190（**单函数 1140**）| 未提 | — | 同上 |
| DownloadManager | 1122 | C3 邻域未列 | — | 下载/DB/SAF，高耦合 |
| AlbumDetailDialogs | 1067 | 未提 | — | — |
| SettingsScreenSections | 1035 | 未提 | — | — |
| LibraryWriteRepository | 1050 | C3 明确 | 拆 4 族 | 标签/删除/扫描事务 |
| AlbumItem | 1003 | 未提 | — | — |

**结论**：① C5 ">1500 清零" 因 `AlbumDetailScreen` 未纳入而**必不达标**；② 若把 `SIZE_LIMIT` 降到 ≤1000，上述 14 个文件**全部违约**而无拆解路径；③ 三个 Service/DB/SAF 强耦合文件（`PlaybackService`/`SubtitleTaskService`/`DownloadManager`）**只字未拆**。

## 5. 计划优化（落地到 `refactor-plan-r3.md`）

1. **§3 增 B0「基线与检测前置」**：① 新增 **包级 SCC ratchet** 进 `ci_guard.py`（当前 48 包团为上界）；② baseline **失效条目检测**（dead entry 报警）；③ 新增"机械重写 baseline"脚本步骤（移包后同步重键）。
2. **§3 增「多跳环」说明**：明确本轮只保证消 2-环 + 冻结 SCC 规模；列出 3 条代表性长环的打断路径（B4 扩到 `repository→download`、`settings↔hotlistening`、接受 `root↔ui` 结构环）。
3. **§3 B1 修正**：`LibraryQuerySpec` 需先拆文件（builder 留 data）；`titleForDisplay` 随实体（本轮不动，记 backlog）；数字由"−80"改"−83"，并标注**净**效果。
4. **§3 B3 修正**：扩源 +10 与 B6 目标**合账**（目标上调为 ≤110 或把 +10 记为"补检测费"）。
5. **§3 B5 修正**：`ImageCacheEntryPoint` 走**中立门面/接口**（不可搬 ui）；预加载器先抽接口；补"测试 import 同步"为每个迁移任务的验收项。
6. **§3 增「顺序约束」**：B5（repository 出口）先于 C1/C2；B0 先于一切移包。
7. **§4 增**：God 文件全覆盖（把 `AlbumDetailScreen`/`SettingsScreen`/`EqualizerPanel` 并入 C4；新增 **C7「service 拆解」** 覆盖 `PlaybackService`/`SubtitleTaskService`/`DownloadManager`）；C5 改**分级目标**（>1500 清零 → 逐步降，不设 1000 硬线；先保证"不再新增超限"）。
8. **修文档漂移**：§0/§8/§4 数字对齐（945、344、2500/2510）。
