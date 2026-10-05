# R3 阶段 B 进度留档（2026-10-05 更新：B0–B5 全部提交，B6 门禁进行中）

> 状态：**B0–B5 代码全部提交**（13 个提交），B6 门禁执行中（全量测试/子代理审查/实机走查/tag）。
> 基线：`refactor/architecture-cleanup`，阶段 A tag `refactor-r3/phase-A` 之后 13 个提交。

## 1. 已提交进度（B0 → B5）

| 提交 | 任务 | 要点 | baseline 变化 |
|---|---|---|---|
| `cd86980` | B0 检测前置 | ci_guard 新增包级 SCC ratchet（实测 50 包团上界，含 Tarjan 自检）+ baseline 失效条目检测 + `tools/refresh_baseline.py` 机械重写脚本；清 5 条历史 stale | 344 → 339 |
| `9075933` | B1a | TreeFileType 族（枚举+LocalTreeLeafCacheEntry+6 纯函数）→ `domain.model`，15 文件 42 处 import 重写 | 339 → 315 |
| `03ca0b6` | B1b/B1c | TagSource → `domain.model`；CachePolicy/CacheImageModel/AppCacheLimits → `util` | 315 → 290 |
| `11e3d56` | B1d | QuerySpec 三件 → `domain.model`；LibraryQueryBuilder 迁 `data.repository`（SCC 取证偏离计划）；`albumsPaged(spec)` 出口；VM 2500→2494 | 290 → 276 |
| `d9148d9` | B1e | 7 投影 DTO → `domain.model`；RemoteSubtitleSource 随迁；**domain.model 出边清零、退出连通团，dao/db 级联脱团** | 276 → 262 |
| `d9e319d` | B1f | AudioOutputRouteKind → `util`（main 侧 7 条盲区合法化） | 262 → 257 |
| `98993af` | B2 | 环1/环2/环6/环11 消解 + 双倒挂（细节见 git log） | 257 → 256 |
| `4c2b1f3` | B3 | ui-to-service 规则源包含 main + 夹具 | 256 → 260 |
| `81754f7` | 留档 | 中途停工留档文档（B4 当时挂起） | — |
| `e54e0b6` | B4 | **环7**（util.NetworkTrafficSink 接口 + di/StatisticsModule @Binds + StatisticsRepository 实现）+ **环8**（data.local.library.DownloadStorage 端口 + DownloadStorageGateway）+ **NetworkHeaders 迁 util（计划外，29 文件重写，SCC 级联塌缩 48→41）** | 260 → 256 |
| `8f88b8d` | B5a | LibraryPresetStore 迁 data/local/datastore（UI 纯类型 LibraryFilterPreset/PersistedLibraryFilters 拆入 domain.model）；**LibraryViewModel 真**收口：删手动构造 LibraryPreferencesStore，读/写经 LibraryRead/WriteRepository 出口**（消 ui-to-datastore 新违规路径）**；LibraryTrackQuery 迁 data/repository + spec 出口重载；CloudSyncSelectionDialog HttpUrl 摘要下沉 util；VM 顺删 unused paging.map 2494→2492 | 256 → 253 |
| `d023b98` | B5b/c | 死 import 清理 18 条+1 构造参数（AlbumDetail 家族 Gson×14 / Support okhttp×3 / AlbumDetailViewModel okhttp+@Named / main 4 文件 SettingsDataStore）；SearchViewModel toUserMessage 迁 data/repository/SearchErrorMessages.kt（消 retrofit2）；DrawerStatus 探测迁 util/SiteLatencyProbe；Saver 远程流迁 util/PreviewImageRemoteStream（EntryPoint 改出该门面）；LibraryViewModel 封面下载受"双实现不改动"决策保护 | 253 → 225 |
| `00b3bd0` | B5d | LazyListPreloader 双件迁 ui/common/cover（消 cache 包反向依赖）；ImageCacheBridge.rememberAppImageCacheManager 唯一 seam（preloader 3 屏 6 处 + loadImage 类 5 文件 6 处全改桥；NowPlaying 4 死 import 删）；AppCacheState 下沉 util（**刻意不进 domain.model 防出边破坏纯叶子**）；**补 B4 遗漏的 DownloadStorage @Binds 绑定**（compileDebugKotlin 不触发 Dagger 全图检查）；恢复误删的 paging.map 2493 | 225 → 205 |

## 2. 实测快照（B5d 后）

- **import baseline：344 → 205**（计划目标 ≤110 未达——见 §3 未尽项）。
- **SCC：50 → 41**（B1e 纯叶子化 + B4 NetworkHeaders 级联；B5 收口删的是 ui→底层边，不动底层团，41 维持）。
- **2-环：18 → 13**（剩余：10 个 root↔X 结构性 + 环3 library↔albumdetail（缓）+ 环9 download↔remote.download（缓）+ ui.player↔nowplaying（非违规））。
- **size pin：LibraryViewModel 2500 → 2493**（B5a 误删 paging.map 恢复 +1 后净收缩 7 行），其余 7 条未动。
- **测试**：945/0/4 为阶段 A 基数；B6 门禁复跑中（见 §4）。

## 3. B5 未尽项与理由（留 C 阶段或后续）

- **实体类穿透（~40 条）**：ui VM/Screen 直接消费 `AlbumEntity`/`TrackEntity`/`PlaylistItemEntity` 等 data.local.db.entities——领域模型化需逐族建映射，属 C1/C2 State Holder 与 C8/C9 重写的自然改造面，B5 不做（避免过度重构 + 中风险）。
- **main→SettingsDataStore 全量收口（8 条存量）**：main 4 文件实际读写（ClipboardRjNavigation/MainContainer/MainContainerRuntime/MainOverlayUi）+ MainActivity 自身 14 处；SettingsRepository 与 SettingsDataStore 是平级独立类，收口须先给 Repository 增补 ~10 组 theme/layout/clipboard 类 API，属结构性中风险改造，B6 后评估。
- **SearchViewModel 本地缓存（2 条）**：LastSearchStateV1/SearchCacheStore 经 SearchRepository 收口须先给它增补 last-state/history 职责。
- **VM→Worker 类引用（3 条）**：WorkManager 泛型 tag + KEY 常量，机制固有，下沉 enqueue 助手收益低。
- **AppCacheManager 注入（2 条）**：SettingsViewModel 状态直通 + AlbumDetailViewModel 回调，收口需包装层，随 C 阶段 settings 改造一并。

## 4. B6 门禁（进行中）

1. 全量 `:app:testDebugUnitTest`（基线只增不减：≥945 通过）。
2. 子代理审查 `git diff refactor-r3/phase-A..HEAD`。
3. 实机走查（库页/详情页/下载页/播放链）——需设备配合。
4. tag `refactor-r3/phase-B`。

## 5. 阶段 B 踩坑（增量，勿重犯）

1. **compileDebugKotlin 不触发 Dagger 全图检查**：B4 环8 的 DownloadStorage 端口漏 @Binds 绑定直到 B5d 全量构建才暴露——三源集验证任务须补 kapt/assemble 链或 `:app:kaptDebugKotlin`。
2. **unused import 判定的"同名方法掩盖"变体**：`androidx.paging.map`（PagingData.map）被 `kotlinx.coroutines.flow.*` 的 map 掩盖——grep 掩盖检测会误判"已使用"或漏看调用点；B5a 误删导致 LibraryScreen 类型推断连锁失败（错误表现在远离根因的 Screen 端）。恢复后 VM 净行数 +1。
3. **EntryPoint 接口方法上的限定符注解**会限定返回类型本身（`@Named("image") fun previewImageRemoteStream()` 被解析为要 @Named("image") 的门面实例），迁移 client 获取路径时记得摘掉。
4. **PowerShell**：不支持 `&&` 与 heredoc；gradlew-local.bat 无前导点；Measure-Object -Line 只数非空行（行数统计用 `(Get-Content f).Count`）。
5. **行尾幽灵改动**：autocrlf=true 下部分文件 git status 显示 M 但 diff 为空——`git add` 规范化后自动消失，不进提交。
6. **SCC 吸收陷阱（B4 复确认）**：NetworkHeaders 迁 util 引发 48→41 级联塌缩，direction baseline 与 scc baseline 必须同提交收缩。
