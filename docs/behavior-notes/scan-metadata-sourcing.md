# 行为档案：扫描入库的来源回填与元数据增量读取（T3'，2026-10-09；T11 修订）

> T3'（原 T3+T4 合并）把 `albums.source` 来源回填与 `AudioMetadataReader` 元数据回填接入扫描管线。
> 实现单点在 `data/repository/LibraryScanMetadataSupport.kt`；接线点：
> `ui/library/LibraryScanPipeline.kt`（T11 起扫描管线本体自 LibraryScanStateHolder 下沉至此，
> File 分支编排 + SAF 分支编排；与批量入口 Worker 同包——扫描依赖的 data.repository/playback
> 均在既有巨型连通团内，新包收管线会撑大 ci_guard 的 SCC ratchet，故零新增包级边）、
> `LibraryScanWriteSupport.upsertScannedDocumentAlbum`（SAF 事务内新插轨读取，T11-P2 起事务前预读）。
> 关联：docs/ARCHITECTURE.md §8.3/§8.4/ADR-3（Room 32 的 source/artist/albumTag 列）；风险 R2（增量约束）、R4（来源归类）。

## 一、来源回填（albums.source）

- **规则：按"进入哪条扫描管线"定性，不按 asDownloadRoot 参数翻转。**
  - `scanFromDownloadedDir` 管线（DLsite 作品落盘的下载目录）→ `SOURCE_DLSITE_DOWNLOAD`。含两个分支：
    File 下载目录分支（entity 构造于 holder 内联）与其 SAF 委托分支（`scanFromDocumentTree` 传
    `pipelineSource = dlsite_download`；`importAll=true` 整体导入同样算 dlsite_download——被扫描对象仍是下载落盘）。
  - 直接调用 `scanFromDocumentTree`（扫描根刷新：`scanAllRoots`/`scanSingleRoot`）→ 默认 `SOURCE_LOCAL_SCAN`
    （参数默认值，调用点零改动即获得）。
- **已有非空 source 保留、永不覆盖**：`resolveAlbumSource(existing?.source, pipelineSource)`——首管线定性后固定；
  空白串视同缺失。RJ 合并场景（同一作品先 local 后 download 合并为一条专辑）source 保持首次写入值。
- **重扫路径不碰 source**：`scanSingleAlbumFromDocumentUri`/`rescanDocumentAlbum` 与
  `scanTracksAndSubtitlesFromFileAlbum`（LibraryDeleteStateHolder 重扫也调用）不读写 source；下载入库
  （DownloadLibraryUpsert）另有自己的回填点，不在本档案范围。
- 代码位置：holder 的两处 AlbumEntity 构造（`scanFromDownloadedDir` 内联 + `buildDocumentAlbumEntity`）；
  解析纯函数 `LibraryScanMetadataSupport.resolveAlbumSource`（单测钉住）。

## 二、元数据回填（tracks.artist / tracks.albumTag）

- **增量语义（R2 约束）：仅对本次扫描"新出现"的 path 读取元数据，已存在轨零 MediaMetadataRetriever 打开。**
  复用 path diff 的 insert/update 判定：
  - File 路径：`scanTracksAndSubtitlesFromFileAlbum` 中 `existingTrack == null` 分支才调 `readForNewTrack`（读取在写事务外）。
  - SAF 路径（**T11-P2 修订**）：`upsertScannedDocumentAlbum` 事务内**不再**调用 `readForNewTrack`——新插轨元数据由
    `prepareDocumentTrackMetadata` 在**写事务前**按同一 path-diff 批量预读（新专辑全量预读；已有专辑仅预读新插），
    以 `metadataByPath` 映射传入事务、事务内纯写。触发条件（仅新插轨读取）与计数语义不变；已知代价：预读判
    "已存在"、进事务前被删的轨以 null 元数据落库，不再事务内补读（单册极端并发，接受）。
- **已存在轨永不回填/覆盖元数据**：update 分支只覆写 title/group（`updatedTrackEntity`），artist/albumTag
  保持库中现值——即使新一次扫描读到了不同标签（钉测试：二扫换标签断言库值不变）。
- **内容变更不检测**：现状 path diff 无 lastModified/hash 信号，同 path 内容改了标签不会触发重读（原状延
  伸，非本次引入）。同 path 轨被删后重扫（path 重现）视为新插，会重新读取。
- **字段规则**：album 标签 → `track.albumTag`（≠ 目录专辑）；读取失败/null/空白一律留 null，展示层文件名
  兜底（US-02），不造默认值。metadata.title 不回填 track.title（title 保持文件名双轨现状）。
- **path→Uri 路由**：`content://` 前缀走 `Uri.parse`，其余按 File 绝对路径走 `Uri.fromFile`——两分支共用同一
  读取单点。
- 代码位置：`LibraryScanMetadataSupport.readForNewTrack`（**增量闸门唯一入口**）+ `newTrackEntity`/
  `updatedTrackEntity`；Hilt 绑定 `di/MetadataModule.kt`，经 `LibraryWriteRepository` 构造注入
  （默认 null 参数仅服务既有测试构造点，reader 缺席时读取整体退化为 null）。

## 三、T11 批量扫描下沉（进度通道与触发面）

- **批量扫描三入口（scanAllRoots / scanCurrentDownloadDestinationAsImport / scanSingleRoot）改经
  `ui/library/LibraryScanWorker`（CoroutineWorker）调度**：触发侧（设置页手动 + VM init 自动）只做
  `SyncCoordinator` 全局门预检（被占→showSyncBusy 同文案提示并丢弃）+ `enqueueUniqueWork(KEEP)` 幂等入队；
  KEEP 对齐原 scanMutex/syncCoordinator"互斥不排队"语义，竞态窗口内的重复触发静默丢弃（原实现会
  立即 busy 提示——仅此亚秒级窗口有差异）。Worker 运行时仍走 tryBegin 全局门，与云同步/删除族互斥。
- **bulkProgress StateFlow 归属变更**：原 VM 级 `LibraryTaskCoordinator` 内部状态，现挂应用级单例
  `util/BulkProgressStore`（Worker 无法触达 VM 实例）；协调器保留原方法签名委托至此，VM/Worker
  双侧更新同一 flow——VM 中途重建也能读到进行中的进度（原实现做不到，属预期改善）。
  VM.cancelBulkTask 先 `cancelUniqueWork` 撤销运行中/排队中的扫描 Worker，再走原协调器取消路径。
- **T7 归类挂载不受影响**：新插轨 attach 仍在 LibraryScanWriteSupport 事务内（插入成功之后），
  与元数据预读移位无耦合；source 单点定性链路（buildDocumentAlbumEntity/resolveAlbumSource）原样随迁。

## 四、封面回填（与文件夹图共存）

- **触发条件不变（现状保持）**：专辑 `coverPath` 为空才走内嵌回填——即**文件夹图优先（pickCoverFile/
  pickCoverNode 先行落 coverPath）、内嵌兜底**。音声场景既有封面行为不被破坏。
- **内嵌图源两级**：优先 `AudioMetadata.embeddedCover`（`decodeEmbeddedCover` 解码 ByteArray），为 null
  回退既有 `EmbeddedMediaExtractor.extractArtwork`。
  - SAF 路径：首条**非空**新插轨的 embeddedCover 经 `DocumentScanResult.firstInsertedCoverBytes` 从事务透出
    （增量读取的副产品，零额外 MMR 打开）；首轨非新插时该值为 null → 回退 extractArtwork。
  - File 路径：封面回填段对首音轨独立调 `readForNewTrack`（该段与原 extractArtwork 同为"coverPath 为空才
    探查"，无增量语义，现状即如此）。已知不对称：新专辑首扫且封面缺失时，首轨会被读两次元数据
    （封面段一次 + 音轨新插一次），量级 1 文件，为保持封面回填先于音轨同步的原时序而接受。

## 五、钉测试

- `app/src/test/java/com/asmr/player/data/repository/LibraryScanMetadataSupportTest.kt`
  - source 保留/回填规则（空白视同缺失、null pipeline 透传）
  - readForNewTrack：File/content Uri 路由、字段 trim+空白归 null、读不到/异常/reader 缺席退 null
  - newTrackEntity 填充 vs 缺失留 null；updatedTrackEntity 仅覆写 title/group
  - 事务集成：首扫每新插轨恰读一次（fake 计数）+ artist/albumTag 落库 + firstInsertedCoverBytes 透出 +
    source 落库；二扫零读取（增量）且不覆盖既有值；标签全缺 + dlsite_download 落库
  - T11-P2：prepareDocumentTrackMetadata 新专辑逐规格恰读一次、已有专辑仅读新插 path、
    upsertScannedDocumentAlbum 写事务内零 MMR 读取且字段/封面字节照常落库
- `app/src/test/java/com/asmr/player/util/BulkProgressStoreTest.kt`
  - 应用级进度通道口径：start 重置节流/currentFile、update 只动 current/title、
    currentFile 120ms 节流、无进度时 update no-op、finish 清空
- 发现于：T3'（Eara Player 二开批次 B）；T11 修订（Eara Player 二开批次 B）。
