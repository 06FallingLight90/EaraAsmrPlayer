# R3 阶段 C 进度留档（2026-10-05：阶段 C 开工，C1 进行中）

> 状态：阶段 B 已闭环（tag `refactor-r3/phase-B`，CI 双绿已由用户确认）；阶段 C 执行中。
> 基线：`refactor/architecture-cleanup`，阶段 B tag `refactor-r3/phase-B` 之后 2 个提交。
> 实机走查（B 阶段遗留）仍挂起待设备。

## 1. 已提交进度

| 提交 | 任务 | 要点 |
|---|---|---|
| `008f183` | C1a | 标签族 + 过滤族 State Holder 抽取（LibraryTagStateHolder / LibraryFilterStateHolder）：_querySpec 所有权移 filterHolder；deleteUserTag 的过滤联动经 onFiltersRemoved 回调；loadInheritedTagsForAlbum 实体解引用下沉 LibraryReadRepository 出口；VM 2493→2356。**偏离计划记录：holder 放 ui/library 同包而非 holder/ 子包——SCC 实测独立包被既有 3-环（ui.library→repo→root→ui.library）连同吸入（42>41），同包不新增包级边** |
| `64ef55d` | C1b-i | SAF 树/删除 helper 9 个实现下沉 data/local/tree/SafTreeSupport（DocNode→SafDocNode，context 显式参数），扫描/删除两族共用消双持；VM 保留薄委托；VM 2356→2147 |

## 2. C1 剩余（下一会话复工顺序）

1. **C1b-ii 删除族**：抽取前须先建 `LibraryTaskCoordinator`（ui/library 同包）——albumJobs/_syncStatus/bulk 进度 5 函数/bulkStartMutex/bulkJob/cloudSyncSelectionQueue 取消路径自外壳迁入；删除族（rescanAlbum/deleteAlbum/deleteAlbumTreeEntry/removeTrackFromAlbum + deleteAlbumEntity，约 400 行）与扫描族批量入口（scanAllRoots 等五处 bulkStartMutex 模式）都依赖它。建议扫描/删除/云同步三 holder 均注入 TaskCoordinator。
2. **C1c 扫描族**（~280 编排 + ~830 底层）：底层（封面/缓存/SAF/字幕/scanFromDocumentTree/walkTree 消费等）随 TaskCoordinator 就位后批量搬；注意 walkTree/queryChildren 已下沉 SafTreeSupport，holder 直调。
3. **C1d 云同步族**（~370 行，纠缠最深）：applyResolvedCloudSync 的 title 覆盖语义必须原样随迁（不可改走 repo 合并）；ensureAlbumCoverSaved 随云族（受"双实现不改动"决策保护）。
4. VM 外壳目标 ≤800；每族一提交，三源集编译 + kapt（Dagger 全图）+ guard 三绿。

## 3. 阶段 C 踩坑（增量）

1. **SCC 吸收（C1a 实测）**：VM 同包抽 holder 是安全的（零新增包级边）；独立 holder 包会被"ui.library→repo→root→ui.library"既有回环连带吸入（root 的 BuildConfig 被广泛引用是 root 入团主因）。计划文档的 `holder/` 子包方案在 ui.library 脱团前不可行。
2. **kapt 缺席**：compileDebugKotlin 不查 Dagger 全图——C1 的 holder 若涉注入，验证命令须含 kapt/assemble。
3. size baseline 松弛容差 50 行：每批抽取后立即贴合收缩（2356→2147 两批连续触发）。

## 4. 阶段 C 后续队列（计划 §8.4）

C1 收官 → C3 LibraryWriteRepository 拆族 → C4 God 文件区块化（7 文件）→ C7 service 拆解 → C8 详情页 VM 重写（前置 AlbumDetailViewModelTest）→ C9 搜索重写（前置四分支 seam 测试）→ C5 ratchet 分级收紧 → C6 收尾 → 门禁 tag `refactor-r3/phase-C`。
