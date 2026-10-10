# 行为档案：合集三类 seed + 来源自动归类（T7，2026-10-09）

> T7 把「分组」升级为合集语义：合集列表首次进入时幂等 seed 三类默认合集（歌曲/音声/其它音频），
> 存量曲目按 `albums.source` 一次性回填，此后只有**新入库轨**增量归类。
> 实现单点在 `data/repository/AutoClassifySupport.kt`（classify + attach 幂等接口，禁止各入库点自写归类逻辑）。
> 关联：docs/behavior-notes/scan-metadata-sourcing.md（source 单点定性，本档案只消费 source、不写 source）。

## 一、三类默认合集 seed

- **幂等创建**：`AutoClassifySupport.ensureDefaultGroups()`（`AlbumGroupRepository.ensureDefaultGroups()` 门面，
  `AlbumGroupsViewModel.init` 调用，与 `PlaylistsViewModel.ensureSystemPlaylists` 同模式）。
  判存按**固定名称**（`getGroupByNameOnce`，COLLATE NOCASE）：歌曲 / 音声 / 其它音频——已存在则不重建、不改名。
- **名称冲突语义**：用户已自建同名合集（含大小写差异）时视作默认合集已存在，不重建也不再回填该类
  （判存与回填绑定在同一次判定上）。
- **用户删除默认合集**：删除后增量挂载查不到目标组 → 静默跳过（不即时重建）；下次合集列表 VM init 的
  seed 按名称判存会**重建该合集并触发一次该类回填**（见 §二），自愈但意味着"删掉的默认合集会复活"。
- 触发条件：合集列表 VM（AlbumGroupsViewModel）每次 init；钉测试 `AutoClassifySupportTest`（seed 幂等：两次
  ensureDefaultGroups 只建一次、名称集合不变）。

## 二、存量回填（仅首次创建时一次性）

- **触发条件**：本次 `ensureDefaultGroups` 中**有**默认合集是新创建的 → 仅对**本次新建的类别**做一次存量回填
  （join tracks→albums：`dlsite_download`→音声、`local_scan`→歌曲、source null/空白→其它音频；
  判存与回填绑定同一次判定，未新建的类别——含同名自建——不回填）。
  三类都已存在 → 不回填（后续新入库轨走增量，不在 seed 时补）。
- **去重与插入**：`(groupId, mediaId)` 为主键，批量 `insertItemsIgnoringConflicts`（OnConflictStrategy.IGNORE）
  既有条目不重复插入、不覆写 itemOrder/createdAt；回填条目 itemOrder 取组内 0..n-1（按 path 排序，默认合集
  首建为空组）；整段在 `withTransaction` 内一次 `IGNORE` 批量插入（万级曲目一次 seed，无逐条事务）。
- **入库文件 = track 粒度**：回填/挂载的 mediaId 都是 `track.path`（与既有 `addAlbumToGroup` 展开语义一致）。
- 代码位置：`AutoClassifySupport.ensureDefaultGroups` + `AlbumGroupItemDao.getAllTrackSourceRowsOnce`
  （投影 POJO `dao/TrackSourceRow.kt`）；钉测试：三来源各归其组 + 重复 mediaId 去重 + 二次 seed 不追加回填。

## 三、增量归类（只挂新入库轨，三个入库点全走单点接口）

- **闸门语义**：与 `LibraryScanMetadataSupport.readForNewTrack` 同一增量判定——只有进入
  `tracksToInsert`（本次 path-diff 判定为新插）的轨才调
  `AutoClassifySupport.attachTracksToDefaultGroups(albumId, paths, source)` / `attachTrackToDefaultGroup(path, source)`；
  二扫/update 分支零挂载。source 参数一律传**专辑实体的最终定性值**（扫描管线已过 `resolveAlbumSource`），
  attach 不读写 albums.source（"source 永不覆盖"不受影响）。
- **入库面与调用点**（三类入库面、四个调用点；调用点只做两行委托，归类逻辑零重复）：
  1. 扫描管线 File 分支：`LibraryScanWriteSupport.syncScannedLocalAlbumTracks` 事务内 insertTracks 之后
     （album source 由事务内读 albumDao 得到——下载目录管线 RJ 合并专辑 source 保留 dlsite_download，
     新插轨按实际 source 归音声而非按管线归歌曲）。
  2. 扫描管线 SAF 分支：`LibraryScanWriteSupport.upsertScannedDocumentAlbum` 事务内
     （source = 入参 entity.source，已由 `buildDocumentAlbumEntity` 解析）。
  3. 下载 upsert：`DownloadLibraryUpsert` File/SAF 两分支 insertTracks 之后（source = entity.source；
     File 分支新专辑定性 dlsite_download → 音声，已有专辑保留原 source；SAF 分支新专辑定性原状留 null
     → 其它音频，本任务不改其 source 定性行为）。
  4. 在线保存：`LibraryOnlineSaveSupport.saveOnlineSelectedToLibrary` 事务内（新专辑 source 留 null →
     其它音频；仅挂本次真正插入成功的 track id 对应 path）。
- **itemOrder**：增量挂载按组内**同专辑** max(itemOrder)+1 连续递增（与 `addAlbumToGroup` 追加语义同族，
  详情页展示排序键为专辑标题 → itemOrder，跨专辑不受影响）；已存在 `(groupId, mediaId)` 由 IGNORE 跳过。
- **默认合集不存在则跳过**：目标组名查不到（被用户删除且 seed 未跑）→ 不挂载、不重建。

## 四、边界（移除/重扫不对称）

- **用户在合集中移除的自动归类曲目不被回加**：移除只删 `album_group_items` 行，tracks 仍在库；
  后续扫描对已存在轨只走 update 分支（不进 tracksToInsert）→ 不再触发挂载。增量只针对新入库。
- **同轨删除后重新入库视作新插，会回加（预期行为）**：轨被删（文件消失 prune/整册删除/下载重下）后
  再次扫描/下载入库 → tracks 重新插入 = 新入库轨 → 按当时专辑 source 重新归类回默认合集。
  轨删除时组内条目随 `DownloadDirectoryCoordinator.deleteTrackRecords`→`deleteByMediaIds` 一并清除（原状）。
- **单册重扫不挂载**：`rescanDocumentAlbum`（scanSingleAlbumFromDocumentUri，"刷新单册"）是先删后整册重插、
  无 path-diff 新插判定，不调 attach——刷新单册不会把用户移除过的条目整批带回来
  （代价：该册重插后的轨在默认合集中失去归类，直到下次批量扫描/下载按新插轨语义补挂，现状接受）。
- **下载目录管线 File 分支的既有轨更新**：`scanFromDownloadedDir` 反复扫描同一下载目录，已存在轨走
  update 分支，不重复挂载。

## 五、钉测试

- `app/src/test/java/com/asmr/player/data/repository/AutoClassifySupportTest.kt`
  - classify 纯函数三映射（dlsite_download→音声、local_scan→歌曲、null/未知→其它音频）
  - seed 幂等（两次 init 只建一次、名称集合不变、不触碰用户自建合集）
  - 三来源回填正确 + (groupId, mediaId) 去重 + 二次 seed 不追加回填
  - 扫描事务集成：新插轨入对应默认合集、二扫（update 分支）不回加
  - 移除后不被回加（删组内条目 → 无新插轨的扫描 → 条目不回来）
  - 增量挂载幂等（重复 attach 同轨不重复插入、不覆写既有 itemOrder）
- 发现于：T7（Eara Player 二开批次 B）。
