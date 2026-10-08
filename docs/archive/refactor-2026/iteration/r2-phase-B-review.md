# R2 阶段 B 门禁报告（2026-10-02）

> 范围：`git diff refactor-r2/phase-A..HEAD`（B1–B6 + 审查补修 + 走查 devnote，HEAD = 96ff938）
> 审查：独立只读子代理一轮通过（1 项 P0 补修后闭环）；实机走查由主会话在小米 14 执行（用户全程旁观，无人工操作污染）

## 任务完成度

| 任务 | 提交 | 状态 |
|---|---|---|
| B1 data→上层 5 条违规模型下沉 | b056a16 等前序 | ✅ import baseline data 条目清零，守卫锁死 |
| B2a/B2b service 中立化 | b056a16 / 57d2372 | ✅ service/subtitle 零 `MainActivity` import |
| B2c 切片后台循环修复 | 9bdba6e | ✅ `awaitFrameCommitOrTimeout(50ms)` 兜底；实机验证通过（见下） |
| B3 PlaybackController 接口 | 00aabb7 | ✅ PlayerConnection 依赖接口 |
| B4a 删除+标签族下沉 | 251f07d | ✅ 十段删除顺序逐字；行为档案建档 |
| B4b 扫描/初始化/聚合 + 清理事务族下沉 | ca831a4 / 187bf37 | ✅ buildOnlineAlbumPath 下沉；枚举名映射保 Gson 载荷不变 |
| B4c 查询族 LibraryReadRepository | 687086c | ✅ 流 6 + PagingSource factory 2 + 一次性查询 9，全透传 |
| B5 AlbumDetailViewModel 数据层重写 | f404443 | ✅ 三事务下沉（deleteAlbumIfMissingLocally / saveOnlineSelectedToLibrary / replaceTrackUserTags），VM 删 database/DAO 注入 |
| B6 收缩 | 745ffb1 | ✅ import baseline 64→56；AlbumDetailViewModelSupport 13 条死 import 清除 |

## 审查发现与闭环

- **P0**：B5 漏暂存 LibraryReadRepository 两个透传方法 → 补提交 9afcccb，闭环确认通过
- **P1**：无
- **P2×3 备忘**：`deleteUserTag` 返回 `toSet()` 微变；`upsertLocalTreeCache` 双轨映射（CacheLeafEntry→ScanCacheLeaf 枚举名转换）冗余；LibraryWriteRepository 1050 行趋 God，阶段 C 拆族

## 行为档案

`docs/behavior-notes/library-delete-family.md` 七节全（入库）。**已知故意保留的原状不对称**（审查时勿报）：deleteAlbumWithContent 不清 track_tag/remote_subtitle_sources/local_tree_cache；pruneMissingDocumentAlbums 整册删分支不清 album_tag；saveOnlineSelectedToLibrary 的 FTS tagsToken 与 upsertAlbumFtsIndex 写法不同；pruneOrphanedAlbums 的 uriOrFileExists vs fileExists 双探查语义。

## 门禁权威数字

- 全量 `:app:testDebugUnitTest`：**906/0/4**（885 → 906，只增不减）
- `python tools/ci_guard.py`：通过（全程绿；存量 56 条 import + 11 个 size pin 全部匹配）

## 实机走查（小米 14，竖屏，5/6 通过）

- ✅ 库页渲染 / 详情页（本地树 + 操作栏 + 在线元信息）
- ✅ 目录树面包屑：根目录 → トラックリスト 子目录，媒体计数随目录变化
- ✅ **切片后台循环（B2c 实机验证成立）**：曲目2 建 3 切片 → 开启仅播放切片 → 跳首切片 → HOME 后台 9.8min → 回前台位置 14:04 ∈ 切片#1[13:48-16:08]（DB 佐证 track_slices）→ 末切片末端 SkipToNext 跳曲目3；全时间轴与墙钟吻合，修复前症状（后台停摆 + 回前台瞬间跳转）未出现
- ✅ 通知点开：桌面 + 通知栏 → MainActivity 前台显示在播内容
- ⛔ 下载全链：asmr-200.com 树请求 ~2.6s 被主动 Canceled → 下载/保存按钮禁用。**定性 phase-A 前既有**（AlbumDetailScreen 零 diff；B5 未触碰加载/取消逻辑），强网环境补测
- 🔍 顺带发现（留档 devnote 96ff938）：`start_route` 表外值冷启动 FATAL（route extra 未校验）；media_session PlaybackState 稀疏更新不能当位置真值；横屏无切片控件且旋转丢 NowPlaying 状态

## 总判定：通过 → tag `refactor-r2/phase-B` @ 9afcccb

## 遗留 / 下一阶段

- CI 双绿待用户 push（时机由用户决定）：ci.yml（守护+测试）与 release.yml（testReleaseUnitTest）
- 测试遗留：曲目2 两个测试切片（id7/id8）未删，切片管理可手动删
- 阶段 C 开工前先做 2026-10-02 体检「第一阶段：补闸门与校准」（size ratchet 收紧 + `ui-to-data-remote` 等新规则 + ARCHITECTURE.md 校准——本报告成文时后者已完成）
