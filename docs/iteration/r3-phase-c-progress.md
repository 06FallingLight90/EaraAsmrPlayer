# R3 阶段 C 进度留档（2026-10-05：C1 进行中，C1b-ii 已完成）

> 状态：阶段 B 已闭环（tag `refactor-r3/phase-B`，CI 双绿已由用户确认）；阶段 C 执行中。
> 基线：`refactor/architecture-cleanup`，阶段 B tag `refactor-r3/phase-B` 之后 4 个提交。
> 实机走查（B 阶段遗留）仍挂起待设备。

## 1. 已提交进度

| 提交 | 任务 | 要点 |
|---|---|---|
| `008f183` | C1a | 标签族 + 过滤族 State Holder 抽取（LibraryTagStateHolder / LibraryFilterStateHolder）：_querySpec 所有权移 filterHolder；deleteUserTag 的过滤联动经 onFiltersRemoved 回调；loadInheritedTagsForAlbum 实体解引用下沉 LibraryReadRepository 出口；VM 2493→2356。**偏离计划记录：holder 放 ui/library 同包而非 holder/ 子包——SCC 实测独立包被既有 3-环（ui.library→repo→root→ui.library）连同吸入（42>41），同包不新增包级边** |
| `64ef55d` | C1b-i | SAF 树/删除 helper 9 个实现下沉 data/local/tree/SafTreeSupport（DocNode→SafDocNode，context 显式参数），扫描/删除两族共用消双持；VM 保留薄委托；VM 2356→2147 |
| `182e19f` | C1b-ii-a | 任务协调状态抽取 LibraryTaskCoordinator（ui/library 同包）：albumJobs/bulkJob/bulkStartMutex/syncStatus/bulkProgress/cloudSyncSelectionQueue + 12 个任务协调函数逐字搬移；VM 保留 cancelBulkTask/cancelAlbumTask/云同步选择 3 函数转发；5 处 bulkStartMutex 批量入口、runBatchCloudSync、continueSync、syncAlbumMetadata、删除族调用点仅加前缀；onCleared 的队列 cancelAll 随迁（VM 转调）；VM 2147→2067。**初始化顺序注意：taskCoordinator 声明必须在 bulkProgress 等引用它的属性之前** |
| `7ef13d4` | C1b-ii-b | 删除族抽取 LibraryDeleteStateHolder（ui/library 同包）：rescanAlbum/deleteAlbum/deleteAlbumTreeEntry/removeTrackFromAlbum 逐字搬移；任务注册/取消/同步状态经 TaskCoordinator，syncCoordinator 构造透传；rescanAlbum 对扫描底层两函数（scanSingleAlbumFromDocumentUri/scanTracksAndSubtitlesFromFileAlbum）以构造引用过渡（VM 传 `this::`），C1c 换扫描 holder 注入；VM 2067→1846 |

### C1b-ii 附带清账（调用点清单安全网）

- `resolveTreeDocumentUri`：VM 私有零引用死委托（全仓 grep 仅定义处），随删除侧委托清理一并删除。
- `deleteAlbumEntity`：VM 私有 1 行包装，调用点（scanFromDownloadedDir/rescanAlbum）均直调 repo，包装零引用，删除。
- 1 行代理随迁消除（沿用 C1a tagHolder 先例）：holder 内 `upsertAlbumFtsIndex`/`refreshAlbumAudioAggregate` 内联直调 repo；删除侧 SafTreeSupport 委托（deletePathSafely/deleteLocalTreeFile/deleteLocalTreeDirectories/isCanonicalDescendant）直调实现，VM 委托消除；documentExists 委托保留（扫描族仍用）。
- holder 的 `TAG = "LibraryViewModel"` 保持删除族日志输出逐字不变。

## 2. C1 剩余（下一会话复工顺序）

1. **C1c 扫描族**（~280 编排 + ~830 底层）：底层（封面/缓存/SAF/字幕/scanFromDocumentTree/walkTree 消费等）批量搬入扫描 holder；**两件交接事项**：① LibraryDeleteStateHolder 构造函数的 `scanSingleAlbumFromDocumentUri`/`scanTracksAndSubtitlesFromFileAlbum` 两个引用参数换为扫描 holder 注入；② `refreshAlbumAudioAggregate`/`upsertAlbumFtsIndex` 的 VM/holder 双持版本在扫描底层搬离后收敛为一。注意 walkTree/queryChildren/documentExists 已下沉 SafTreeSupport，holder 直调。
2. **C1d 云同步族**（~370 行，纠缠最深）：applyResolvedCloudSync 的 title 覆盖语义必须原样随迁（不可改走 repo 合并）；ensureAlbumCoverSaved 随云族（受"双实现不改动"决策保护）。
3. VM 外壳目标 ≤800（当前 1846，扫描/云两族迁出后预计 ~600-700）；每族一提交，三源集编译 + kapt（Dagger 全图）+ guard 三绿。

## 3. 阶段 C 踩坑（增量）

1. **SCC 吸收（C1a 实测）**：VM 同包抽 holder 是安全的（零新增包级边）；独立 holder 包会被"ui.library→repo→root→ui.library"既有回环连带吸入（root 的 BuildConfig 被广泛引用是 root 入团主因）。计划文档的 `holder/` 子包方案在 ui.library 脱团前不可行。
2. **kapt 缺席**：compileDebugKotlin 不查 Dagger 全图——C1 的 holder 若涉注入，验证命令须含 kapt/assemble。
3. size baseline 松弛容差 50 行：每批抽取后立即贴合收缩（2356→2147 两批连续触发）。

## 4. 阶段 C 后续队列（计划 §8.4）

C1 收官 → C3 LibraryWriteRepository 拆族 → C4 God 文件区块化（7 文件）→ C7 service 拆解 → C8 详情页 VM 重写（前置 AlbumDetailViewModelTest）→ C9 搜索重写（前置四分支 seam 测试）→ C5 ratchet 分级收紧 → C6 收尾 → 门禁 tag `refactor-r3/phase-C`。
