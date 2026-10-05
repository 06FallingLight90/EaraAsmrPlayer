# R3 阶段 C 进度留档（2026-10-06：C1 + C3 + C4-1/C4-2/C4-3 完成，VM 463 行 / Repo 门面 238 行）

> 状态：阶段 B 已闭环（tag `refactor-r3/phase-B`）；阶段 C 执行中——C1 六 holder 分治、C3 写仓拆族、C4-1/C4-2/C4-3 God 文件区块化完成（7 文件中 3 个）。
> 基线：`refactor/architecture-cleanup`，阶段 B tag 之后 13 个提交（C1a..C1d、docs ×2、C3、C4-1、C4-2、docs、C4-3）。
> 实机走查（B 阶段遗留 + C 阶段六族/写仓拆族）仍挂起待设备。

## 1. 已提交进度

| 提交 | 任务 | 要点 |
|---|---|---|
| `008f183` | C1a | 标签族 + 过滤族 State Holder 抽取（LibraryTagStateHolder / LibraryFilterStateHolder）：_querySpec 所有权移 filterHolder；deleteUserTag 的过滤联动经 onFiltersRemoved 回调；loadInheritedTagsForAlbum 实体解引用下沉 LibraryReadRepository 出口；VM 2493→2356。**偏离计划记录：holder 放 ui/library 同包而非 holder/ 子包——SCC 实测独立包被既有 3-环（ui.library→repo→root→ui.library）连同吸入（42>41），同包不新增包级边** |
| `64ef55d` | C1b-i | SAF 树/删除 helper 9 个实现下沉 data/local/tree/SafTreeSupport（DocNode→SafDocNode，context 显式参数），扫描/删除两族共用消双持；VM 保留薄委托；VM 2356→2147 |
| `182e19f` | C1b-ii-a | 任务协调状态抽取 LibraryTaskCoordinator（ui/library 同包）：albumJobs/bulkJob/bulkStartMutex/syncStatus/bulkProgress/cloudSyncSelectionQueue + 12 个任务协调函数逐字搬移；VM 保留 cancelBulkTask/cancelAlbumTask/云同步选择 3 函数转发；5 处 bulkStartMutex 批量入口、runBatchCloudSync、continueSync、syncAlbumMetadata、删除族调用点仅加前缀；onCleared 的队列 cancelAll 随迁（VM 转调）；VM 2147→2067。**初始化顺序注意：taskCoordinator 声明必须在 bulkProgress 等引用它的属性之前** |
| `7ef13d4` | C1b-ii-b | 删除族抽取 LibraryDeleteStateHolder（ui/library 同包）：rescanAlbum/deleteAlbum/deleteAlbumTreeEntry/removeTrackFromAlbum 逐字搬移；任务注册/取消/同步状态经 TaskCoordinator，syncCoordinator 构造透传；VM 2067→1846 |
| `31dc76d` | C1c-a | 扫描底层下沉 LibraryScanStateHolder（ui/library 同包）：封面挑选/CacheTreeFileType 树缓存叶/computePathsStamp/extractWorkNo/legacyOnlineSavedAlbumDir/backfill/scanFromDownloadedDir/scanTracksAndSubtitlesFromFileAlbum/scanFromDocumentTree/scanSingleAlbumFromDocumentUri/prune 族 4 个/WorkManager 入队/resolveAndMergeAlbumForRj 逐字搬移；SafTreeSupport 委托随迁消除；**交接项①完成**（deleteHolder 的扫描两函数引用参数换 scanHolder 注入）；refreshAlbumAudioAggregate VM 版删除（双持收敛）；import baseline：VM work.* 2 死条目删、holder 新增 4 条存量（work ×2 + db entities ×2）；VM 1846→1140 |
| `89c2355` | C1c-b | 扫描根管理 + 三批量入口迁入 scanHolder：addScanRoot/isSubdirectory/removeScanRoot/removeScanRootAndDeleteAlbums/scanAllRoots/scanCurrentDownloadDestinationAsImport/scanSingleRoot + scanRootsStore/_scanRoots/scanRoots 流/scanMutex 所有权随迁；VM init 三处改经 holder（restoreScanRootsFromStore/getRootsFromStore/scanAllRoots）；**LibraryViewModel 836 < 1500 退出 size pin**（baseline 8→7 条）；VM 1140→836 |
| `d6533b4` | C1d | 云同步族迁入 LibraryCloudSyncStateHolder（ui/library 同包）：syncMetadata/syncMetadataForRoot/syncAlbumMetadata/runBatchCloudSync/syncAlbumMetadataInternal/resolveAlbumCloudSync/resolveSelectedAlbumCloudSync/applyResolvedCloudSync/continueSyncAlbumMetadataAfterSelection/reportSyncAlbumMetadataFailure/ensureAlbumCoverSaved 逐字搬移；**applyResolvedCloudSync 的 title 覆盖语义原样随迁（与 repo 版 title 保留规则不可混用）；ensureAlbumCoverSaved 受"双实现只记录不改动"决策保护**；upsertAlbumFtsIndex/upsertAlbumTagsFromCsv VM 委托随迁消除（**交接项②完成**）；import baseline：VM Request 死条目删、dlsite 3 条换键 holder、holder 新增 okhttp3 ×2 + AlbumEntity；VM 836→**463** |
| `206bafd` | C3 | LibraryWriteRepository 拆族（**1069→238 门面**）：新建 4 个 internal support（同包 data/repository，实现逐字搬移、消费者零改动）——LibraryTagWriteSupport（标签/FTS/CSV/播种）、LibraryDeleteWriteSupport（删专辑/音轨族/下载任务/缺失整册清除）、LibraryScanWriteSupport（聚合/回填/目录树缓存/prune 族 4/扫描同步/upsertScanned/rescan，跨族调用改显式依赖 deleteWrite./tagWrite.，事务体逐字不变）、LibraryOnlineSaveSupport（saveOnlineSelectedToLibrary）；门面保留全部签名与嵌套数据投影类型（Gson 载荷 JSON 不变）；行为档案 ⚠️ 注释逐字随迁 |
| `8390a72` | C4-1 | AlbumDetailDirectorySupport 区块化拆族（**2698 行单文件 → 7 同包主题文件** ui/library/albumdetail，全部 <1500 无需 pin）：DirectoryBrowserModels 308 / DirectorySelectionSupport 202 / LocalTreeBrowserSupport 247 / RemoteTreeBrowserSupport 475 / DirectoryFileTypeStyling 74 / DirectoryBrowserRows 642 / DirectoryBrowserPanel 744；顺带删 ~45 个 stale import；import baseline 205→200（删 6 失效条 + 增 RemoteTreeBrowserSupport→AsmrOneTrackNodeResponse 1 条）；size baseline 仅移除 DirectorySupport pin |
| `5230f08` | C4-2 | DownloadsScreen 区块化拆族（**2281 行单文件 → 4 同包主题文件** ui/downloads，主文件 505）：DownloadTranslationSection 783（翻译内容区+行 Composable+状态辅助群）/ DownloadTaskCards 580（任务卡+树展开+行+汇总）/ DownloadTaskGroupUi 251（卡头共享原语 TaskGroupHeader 族+StableProgressSlot+CompactProgressBar）/ SwipeRevealActions 302（滑动容器+Controller）；可见性放宽 private→internal 共 10 处（同包跨文件所需）；TaskProgressMeta 随迁归位卡文件（**main/test 零引用疑似死码，留 devnote 待清**）；同包测试 5 文件零改动；size baseline 删 DownloadsScreen pin（6→5 条）；import baseline 无变化 |
| `dffe8e1` | C4-3 | AlbumDetailDlsiteTabs 区块化拆族（**1930 行单文件 → 4 同包主题文件** ui/library/albumdetail，主文件 623）：主文件保留 DlsiteGalleryImage 模型族 + AlbumDlsiteInfoBreadcrumbTabV2；AlbumDlsiteTabPlaceholders 412（加载占位族）/ AlbumDlsiteTabEmptyStates 265（空态+三段 DrawScope 手绘）/ AlbumDlsiteTabMotion 283（fade 状态/区块入场/目录树过渡/推荐加载卡/loading 判定）/ AlbumDlsitePlayTab 349（Play tab 整体迁移）；private→internal 仅同包跨文件所需 15 处；size baseline 删 pin（5→4 条，剩 AlbumDetailScreen 1518 / AlbumDetailViewModel 2510 / LibraryScreen 1582 / SearchScreen 2186）；import baseline 8→5 条（删主文件 5 stale + PlayTab 增 2：AsmrOneTrackNodeResponse/DlsiteAuthStore）；三绿 945/0/4 |

### C1b-ii 附带清账（调用点清单安全网）

- `resolveTreeDocumentUri`：VM 私有零引用死委托（全仓 grep 仅定义处），随删除侧委托清理一并删除。
- `deleteAlbumEntity`：VM 私有 1 行包装，调用点（scanFromDownloadedDir/rescanAlbum）均直调 repo，包装零引用，删除。
- 1 行代理随迁消除（沿用 C1a tagHolder 先例）：holder 内 `upsertAlbumFtsIndex`/`refreshAlbumAudioAggregate` 内联直调 repo；删除侧 SafTreeSupport 委托（deletePathSafely/deleteLocalTreeFile/deleteLocalTreeDirectories/isCanonicalDescendant）直调实现，VM 委托消除；documentExists 委托保留（扫描族仍用，C1c-a 随迁）。
- 全部 holder 的 `TAG = "LibraryViewModel"` 保持日志输出逐字不变。

## 2. C1 收官状态

- **六 holder 同包分治**：LibraryTagStateHolder / LibraryFilterStateHolder / LibraryTaskCoordinator / LibraryDeleteStateHolder / LibraryScanStateHolder（1118 行）/ LibraryCloudSyncStateHolder（434 行）；VM 外壳 **463 行**（2493→463，目标 ≤800 达成）。
- VM 剩余职责：构造与 holder 装配、uiState/pagedAlbums/pagedTrackAlbumHeaders/expandedTrack 族/scanRoots 等流组合与转发、toAlbum 映射、UI 转发函数。
- 每族一提交，验证 = assembleDebug（含 kapt/Dagger 全图）+ testDebugUnitTest 945/0/4 + ci_guard 三绿。
- **踩坑（C1c/C1d 增量）**：①KDoc 内写 `resolve*/` 会以 `*/` 提前终止注释致"Expecting a top level declaration"；②新 holder 文件的 work.*/db entities/okhttp3 import 会命中 ui-to-cache-work/ui-to-db/ui-to-net 规则——存量入 baseline（与原 VM 条目同性质）；③尾随 lambda 无括号调用（`scanFromDownloadedDir { ... }`）不会被 `xx(` 前缀替换覆盖，replace_all 后需复查无括号形式。

## 3. 下一任务队列（计划 §8.4 剩余）

1. **C4**：God 文件区块化（AlbumDetailScreen 1518 / ~~DownloadsScreen 2281~~✅C4-2 / SearchScreen 2186 / ~~AlbumDetailDlsiteTabs 1932~~✅C4-3 / LibraryScreen 1582 / SettingsScreen 1270 / EqualizerPanel 1190）——单巨型 Composable 拆子 composable + 状态对象，每文件一提交；**C4-1（AlbumDetailDirectorySupport 2698）已先期完成**；SearchScreen 的完整重写归 C9。
2. **C7**：service 层拆解（PlaybackService 1471 / SubtitleTaskService 1429 / DownloadManager 1122）。
3. **C8**（条件式重写，前置 AlbumDetailViewModelTest + 行为档案）/ **C9**（搜索重写，前置四分支 seam 测试）。
4. **C5** ratchet 分级收紧（>1500 清零 → 1000-1500 区间 14 文件 → 降 SIZE_LIMIT）→ **C6** 结构收尾 → 门禁（全量测试双绿 + 子代理审查 + 实机走查）→ tag `refactor-r3/phase-C`。

## 4. 阶段 C 踩坑（增量）

1. **SCC 吸收（C1a 实测）**：VM 同包抽 holder 是安全的（零新增包级边）；独立 holder 包会被"ui.library→repo→root→ui.library"既有回环连带吸入（root 的 BuildConfig 被广泛引用是 root 入团主因）。计划文档的 `holder/` 子包方案在 ui.library 脱团前不可行。
2. **kapt 缺席**：compileDebugKotlin 不查 Dagger 全图——C1 的 holder 若涉注入，验证命令须含 kapt/assemble。
3. size baseline 松弛容差 50 行：每批抽取后立即贴合收缩（2356→2147 两批连续触发）。
4. **KDoc 内 `resolve*/` 提前终止注释（C1d）**：`*/` 会终止块注释，报"Expecting a top level declaration"满屏——注释内避免 `*/` 字面序列。
5. **新 holder 文件的 import 命中守护规则（C1c/C1d）**：work.*/db entities/okhttp3 import 分别命中 ui-to-cache-work/ui-to-db/ui-to-net——按"存量入 baseline + 记录偿还计划"处理（与原 VM 条目同性质，净违规数不变）。
6. **replace_all 漏覆盖尾随 lambda（C1c-b）**：`xx(` 前缀替换不匹配 `xx { ... }` 无括号形式，替换后须 grep 复查。
7. **Kotlin 属性初始化顺序（C1b-ii-a）**：holder 声明必须先于引用它的属性（如 `bulkProgress = taskCoordinator.bulkProgress`），否则构造期 NPE。
8. **Read 工具显示行号与磁盘/git 真实行号可能偏差 1（C4-3 实测）**：按 Read/grep 显示行号写切片区间，gallery_model 区块少切 1 行（buildDlsiteGalleryImages 的关闭 `}` 落在下一行）→ 编译期 "Expecting '}'" 兜住。教训：**切片脚本的边界行号必须用 Python 直接读磁盘文件按符号行（grep 真实行号）定位**，切片后跑 brace 平衡自检再编译；后续 C4-4..C4-7 沿用。

## 5. 阶段 C 后续队列（计划 §8.4）

C1 收官（✅）→ C3 LibraryWriteRepository 拆族 → C4 God 文件区块化（7 文件）→ C7 service 拆解 → C8 详情页 VM 重写（前置 AlbumDetailViewModelTest）→ C9 搜索重写（前置四分支 seam 测试）→ C5 ratchet 分级收紧 → C6 收尾 → 门禁 tag `refactor-r3/phase-C`。
