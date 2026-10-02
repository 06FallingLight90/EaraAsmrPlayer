# R2 阶段 B 收官（2026-10-02）

> 接 docs/devnote/2026-10-01-r2-start.md。本篇记 B4c/B5/B6-收缩 与阶段门禁执行。

## B4c 查询族（687086c）
- 新建 `data/repository/LibraryReadRepository.kt`（@Singleton，注入 AppDatabase）：流直出 6 + PagingSource factory 参数化 2 + 一次性查询 9，**全部透传**，映射/合并/Pager 编排留 VM。
- LibraryViewModel 删除 database/albumDao/trackDao 注入；DAO import 仅剩流式出口 Row 类型 3 条（LibraryTrackRow/LibraryTrackAlbumHeaderRow/TagWithCount）+ okhttp 2 条（阶段 C）。
- write repo 补透传：insertAlbum / deleteDownloadTaskWithItems（先条目后任务，各自 runCatching）。
- VM 的 upsertLocalTreeCache（file-album 路径）改调 write repo internal 版：CacheLeafEntry→ScanCacheLeaf 经 `TreeFileType.valueOf(name)` 枚举名映射，**Gson 载荷 JSON 不变**；stampProvider 注入。
- seam 测试 +3（LibraryReadRepositoryTest：一次性查询/流首值/getExistingTagIds）。

## B5 AlbumDetailViewModel（f404443）
- 三个事务下沉 write repo：`deleteAlbumIfMissingLocally`（事务内复核 isMissing 注入；十段删除顺序逐字；⚠️ 不删 playlist_item/listening_sessions 原状）、`saveOnlineSelectedToLibrary`（⚠️ FTS tagsToken=tags.replace(',',' ').trim() 与 upsertAlbumFtsIndex 两处独立写法原样保留，勿统一；canonicalUrl 注入；leaf 以 OnlineSaveTrackSpec/ResourceSpec 投影传入）、`replaceTrackUserTags`（与 B4a 同实现直接复用）。
- upsertAlbumFtsIndex/upsertAlbumTagsFromCsv 私有副本与 B4a 逐字一致 → 删副本改委托；refreshAlbumAudioAggregate 同 B4 模式（fileSizeQuery 注入）。
- read repo 增 getAlbumByWorkIdOnce/observeTracksForAlbum(Flow)。VM 删 database/albumDao/trackDao 注入。
- 行为档案：docs/behavior-notes/library-delete-family.md 第七节。

## B6 收缩（745ffb1）
- baseline 64→56：删 8 条失效（两 God VM 的 AlbumDao/TrackDao/Gson）+ AlbumDetailViewModelSupport.kt 13 条死 import（DAO 访问早已搬空，import 遗留——**死 import 检测方法：grep import 行的符号在文件内零使用点，编译兜底验证**）。
- 剩余 56 条分布：TagWithCount 等 Row 流式出口类型（阶段 C 随 UI 解耦清）、okhttp/retrofit（阶段 C）、gson（preset 持久化，阶段 C）。

## 测试与门禁
- 测试 885 → **906/0/4**（B4a +10、B4b +5、B4c +3、B5 复用 B4 的 repo 测试；只增不减）。
- ci_guard 绿全程保持。

## 本轮新踩坑
1. **PS5.1 无 `??` 运算符**：聚合测试报告脚本里用 `??` 直接 ParserError，老老实实 if/else。
2. **理想化断言三连**：三次测试失败全是我把"原实现没做的事"当成应有行为（remote_subtitle_sources→track_tag→album_tag 孤儿残留）。教训：**逐字下沉时测试断言必须对着原实现写，不能对着直觉写**；行为档案同步记录这些不对称。
3. **internal 暴露**：public 方法/嵌套类暴露 internal TreeFileType 参数（ScanCacheLeaf/OnlineSaveResourceSpec）→ 相关成员改 internal，勿动 TreeFileType 可见性。
4. **AlbumTagsCsv/TrackTagsCsv 在 dao 包不在 entities 包**——import 前先 grep 定义位置。
5. Edit 工具 old_string 里误带行号前缀 ": " 导致匹配失败；old_string 含重复行（import TagNormalizer×2）时 replace 顺序要小心。
