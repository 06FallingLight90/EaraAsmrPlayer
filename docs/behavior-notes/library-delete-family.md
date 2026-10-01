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

## 验证
- seam 测试：`app/src/test/java/com/asmr/player/data/repository/LibraryWriteRepositoryTest.kt`（Robolectric + 内存 Room，逐条对应上文契约）。
- 全量测试基线只增不减（885/0/4 起）。
