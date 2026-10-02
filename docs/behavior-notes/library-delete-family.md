# 行为档案：LibraryViewModel 删除/标签族（R2-B4a）

> B4a 把删除族与标签族的数据访问下沉到 `data/repository/LibraryWriteRepository.kt`。
> 本文记录下沉前后的行为契约。**编排（消息提示、FTS 刷新时机、进度/回调、querySpec 更新）留在 VM 不动。**

## 一、标签族（4 个事务）

### replaceAlbumUserTags（原 setUserTagsForAlbum 事务）
- 事务边界：`database.withTransaction` 包整段「删+插」。
- 顺序：`deleteAlbumTagsByAlbumIdAndSource(albumId, USER)` → 若 tags 非空：`insertTags`（IGNORE 冲突）→ `getTagsByNormalized` 回查 → 按 normalized 建 `AlbumTagEntity(source=USER)` → `insertAlbumTags`。
- 语义：只动 USER 源，其它源（AUTO 等）不动；tag 行按归一名复用（IGNORE 去重）。
- 事务后（VM 侧，不在事务内）：`upsertAlbumFtsIndex(albumId, entity)`，entity 为事务前读到的专辑行（专辑不存在则整个方法早退，无任何写）。

### replaceTrackUserTags（原 setUserTagsForTrack 事务）
- 同上，对象换成 `track_tag`（`deleteTrackTagsByTrackIdAndSource` → `insertTrackTags`）。事务后无 FTS 刷新（原实现即如此）。

### renameUserTag（原 renameUserTag 事务）
- 事务前：`getAlbumIdsForTag(tagId)` 捕获专辑 id 集合（toMutableSet）。
- 事务内：`getTagById` 为 null → 早退（无写）；`TagNormalizer.normalize(newName)` 为空 → 早退；查 `getTagByNormalized(newNormalized)`：
  - 冲突存在且 id 不同：`getAlbumIdsForTag(conflict.id)` **并入返回集合** → `moveAlbumTagsToAnotherTag` → `moveTrackTagsToAnotherTag`（track_tag）→ `deleteAlbumTagsByTagId` → `deleteTrackTagsByTagId` → `deleteTag`。
  - 无冲突（或 id 相同）：`updateTag(existing.id, trimmed, newNormalized)`。
- 返回值：受影响专辑 id 集合。**早退路径也返回事务前捕获的集合**（原实现 FTS 刷新不区分早退）。
- 事务后（VM 侧）：对返回集合逐个 `getAlbumById` + `upsertAlbumFtsIndex`（读不到专辑的 id 跳过）。
- VM 侧前置：`newName.trim()` 为空时整个方法不启动协程（保持在 VM）。

### deleteUserTag（原 deleteUserTag 事务）
- 事务前捕获 `getAlbumIdsForTag(tagId)`。
- 事务内：`deleteAlbumTagsByTagId` → `deleteTrackTagsByTagId` → `deleteTag`。
- 返回事务前专辑 id 集合。
- 事务后（VM 侧编排，不下沉）：若被删 tag 在当前过滤器 include/exclude 中，更新 `_querySpec` 并持久化；再逐专辑 FTS 刷新。

## 二、专辑/音轨删除族

### deleteAlbumWithContent（原 deleteAlbum 事务）
- 事务边界：`withTransaction` 包全段；事务前由 VM 读专辑行（null 则整体早退）。
- 事务内顺序（固定）：`deleteSubtitlesForAlbum` → `deleteTracksForAlbum` → `deleteAlbumEntity`（= `onlineSavedResourceDao().deleteByAlbumId` → `albumDao.deleteAlbum`）→ `tagDao().deleteAlbumTagsByAlbumId` → `albumFtsDao().deleteByAlbumId`。
- ⚠️ 本路径**不删** `remote_subtitle_sources`、`track_tag`、`local_tree_cache`（音轨级远程字幕源/音轨标签/目录树缓存行会残留为孤儿，原实现即如此，只清 album_tag 与 online_saved_resources；与 `deleteVerifiedTracksAndResources`/`deleteTrackCompletely` 的行为不对称是有意保留的现状，B4a 不"顺手"清理）。
- 事务后（VM 侧）：下载任务取消与文件删除（downloadDao 查询、DownloadQueueCoordinator.cancelWorksByTag、deletePathSafely），与事务无关。

### deleteVerifiedTracksAndResources（原 deleteAlbumTreeEntry 事务）
- 入参：已验证 trackIds（VM 侧先按 `albumId == track.albumId` 过滤）、resourceIds（VM 侧按 relativePath 匹配过滤）。
- 事务内：trackIds 非空时 `remoteSubtitleSourceDao().deleteByTrackIds` → `deleteSubtitlesForTracks` → `trackTagDao().deleteTrackTagsByTrackIds` → `deleteTracksByIds`；resourceIds 非空时 `onlineSavedResourceDao().deleteByIds`；最后 `localTreeCacheDao().deleteByAlbum(albumId)`（无条件）。
- 事务后（VM 侧）：trackIds 非空时 `refreshAlbumAudioAggregate(albumId)` + 消息 + onComplete 回调。

### deleteTrackCompletely（原 removeTrackFromAlbum 事务）
- 事务内每步都 `runCatching` 吞错（不中断后续步骤）：`remoteSubtitleSourceDao().deleteByTrackId` → `deleteSubtitlesForTrack` → `trackTagDao().deleteTrackTagsByTrackId` → `deleteTrackById` → `localTreeCacheDao().deleteByAlbum(albumId)`。
- 事务后（VM 侧）：`refreshAlbumAudioAggregate` + 消息。物理文件删除判定（allowedRoots/canonicalFile）在 VM，事务前完成。

### 无事务组合删（原 removeScanRootAndDeleteAlbums / rescanAlbum 内联调用，原样下沉，不加事务）
- `deleteAlbumTracksAndSubtitles(albumId)`：`deleteSubtitlesForAlbum` → `deleteTracksForAlbum`。
- `deleteTrackWithSubtitlesById(trackId)`：`deleteSubtitlesForTrack` → `deleteTrackById`（目录树清理循环逐轨调用）。
- `deleteTracksWithSubtitles(trackIds)`：`deleteSubtitlesForTracks` → `deleteTracksByIds`（调用侧保留 isNotEmpty 守卫，原实现即有）。
- `deleteAlbumEntity(entity)`：`onlineSavedResourceDao().deleteByAlbumId` → `albumDao.deleteAlbum`（原本是 VM private，转公开）。
- ⚠️ 这些路径**无事务**，删除中断会留下半删状态——与原实现一致，B4a 不"顺手"加事务。

## 三、留在 VM 的部分（B4b/B4c 再下沉）
- 查询：`getAlbumById` / `getAllAlbumsOnce` / `getTracksForAlbumOnce` / `getTracksByIdsOnce` / `onlineSavedResourceDao().getForAlbumOnce`。
- `albumDao.updateAlbum`（目录树清理改写 path/localPath/coverPath）+ `upsertAlbumFtsIndex`。
- `refreshAlbumAudioAggregate`（重算聚合三字段）。
- 全部消息/进度/回调编排；下载任务清理；文件系统删除（deletePathSafely）。

## 四、附录（R2-B4b 下沉的扫描/初始化/聚合写族）
- `upsertAlbumFtsIndex(albumId, entity)`：USER 标签 CSV 与 entity.tags 合并 → buildTagsToken → albumFtsDao.upsert（REPLACE）。纯数据，逐字下沉。
- `upsertAlbumTagsFromCsv(albumId, tagsCsv, source)`：空 CSV 早退；insertTags（IGNORE）→ getTagsByNormalized → **deleteAlbumTagsByAlbumIdExceptSource(albumId, USER)（保留 USER 源！）** → insertAlbumTags(指定 source)。
- `seedAutoTagsFromAlbumTags(albums)`：原 ensureTagTablesInitialized 的写段。首现归一形建 tag 行（IGNORE）→ 单事务内逐专辑 deleteAlbumTagsByAlbumIdExceptSource(USER) + 插 AUTO refs。空表早退（countTags 判定在调用方）。
- `updateAlbum(entity)`：透传单行更新。
- `computeAlbumAudioAggregate(specs, fileSizeQuery)`：总字节经注入的 fileSizeQuery 逐轨探查（IO 在 Dispatchers.IO），null 记 0；数量/时长纯求和。
- `refreshAlbumAudioAggregate(albumId, fileSizeQuery)`：albumId≤0 早退、专辑行缺失早退 → 读全轨 → compute → 回写三字段。
- `backfillLegacyOnlineSavedAlbumRoots(albums, resolveLegacyDir)`：**单事务包全部专辑**（原样）。逐专辑：localPath/downloadPath 均非空跳过 → 读轨 + shouldBackfillLegacyOnlineSavedAlbumRoot 判定 → resolveLegacyDir（File IO，注入；不可回滚，与原实现同在事务内）→ updateAlbum(localPath) → runCatching 清目录树缓存 → FTS 刷新。
- 平台接缝（fileSizeQuery / resolveLegacyDir / exists 探查）由 VM 注入，Repository 不反向依赖 ui。

## 五、附录（R2-B4b-2 扫描/清理事务族）
- `upsertLocalTreeCache(albumId, albumPaths, leaves, stampProvider)`：路径 trim/去空/去重 → Gson 载荷 → key=排序 join("|") → stamp 经注入（File/Document lastModified，平台 IO）。leaves 用 TreeFileType，枚举名与原 VM 私有 CacheTreeFileType 一致，**载荷 JSON 不变**。
- `pruneMissingDownloadedAlbums(missing)`：单事务。localPath 空且 path 非 content:// → 字幕+轨+专辑实体；否则删 root 前缀轨 + path 改写（startsWith→localPath 优先）+ downloadPath 置 null + coverPath 清空（若前缀命中）+ FTS。missing 筛选（File.exists）在调用方，原样。
- `pruneDocumentDownloadAlbum(entity, root)`：**无事务**（原样）。删 root 前缀轨（含远程字幕源/轨标签）→ downloadPath 置 null → 清目录树缓存。
- `pruneMissingDocumentAlbums(missing, root)`：单事务。downloadPath 空：无在线 → 字幕+轨+专辑实体（**不删 album_tag，孤儿残留原状**）；有在线 → 删 root 前缀轨 + path 在线化（buildOnlineAlbumPath）+ localPath/coverPath 前缀清理 + FTS。downloadPath 非空：删 root 前缀轨 + path→downloadPath（若前缀命中）+ localPath/coverPath 清理 + FTS，**downloadPath 不动**。
- `pruneOrphanedAlbums(albums, uriOrFileExists, fileExists, resolveLegacyDir)`：单事务。**探查双语义保留**：local/main 用 uriOrFileExists（content:// 走 DocumentsContract），download 仅用 fileExists（File.exists）。缓存 tracks/hasOnline 探查结果（每专辑最多读一次轨表）。回填判定→在线化→最终 stillMissing 判定→updated != entity 才写回 + FTS。resolveLegacyDir 注入（File IO 在事务内，原样）。
- `syncScannedLocalAlbumTracks(...)`：单事务。updateTracks → insertTracks → 按 (existingTrackId 映射 + 音轨 path 映射) 建字幕表 → 先删后插字幕 → removedIds 清理（字幕+远程字幕源+轨标签+轨）。diff/解析在调用方。
- `upsertScannedDocumentAlbum(entity, scanRootPath, ...)`：单事务。insertAlbum → FTS → SCAN 标签 → root 前缀旧轨清理（含远程源/轨标签）→ 轨 diff（insert/update）→ 字幕按 path 匹配写入 → **在线→本地字幕合并**（目标无字幕才拷贝）→ refreshAlbumAudioAggregate → 目录树缓存。entity/leaves/specs 为调用方预计算纯数据（外提自事务，行为等价）。
- `rescanDocumentAlbum(albumId, coverPath, treePrefix, ...)`：单事务。专辑缺失早退（persistedPaths 保持空表）→ coverPath 非空更新 → persistedPaths 计算 → treePrefix 旧轨清理（**只删字幕+轨，不删远程源/轨标签**——原样）→ 插入 specs + 字幕。
- 平台接缝（fileSizeQuery/stampProvider/uriOrFileExists/fileExists/resolveLegacyDir）由 VM 注入。

## 验证
- seam 测试：`app/src/test/java/com/asmr/player/data/repository/LibraryWriteRepositoryTest.kt`（Robolectric + 内存 Room，逐条对应上文契约）。
- 全量测试基线只增不减（895/0/4 起，B4a 后）。
