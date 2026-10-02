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

## 实机走查（B6 门禁，2026-10-02 15:30 收官）
结果 **5/6 通过，1 项被既有问题阻塞**（非 B5 回归，diff 佐证）：
- ✅ 库页渲染/详情页（本地树+操作栏+在线元信息）
- ✅ 目录树面包屑：根目录→トラックリスト 子目录进入正常，媒体计数随目录变化（根 0 项/子目录 5 项）
- ✅ **切片后台循环（B2c 修复实机验证通过）**：曲目2 建 3 切片→开启仅播放切片→跳首切片 4:14 ✓→后台 9.8min→回前台位置 14:04 ∈ 切片#1[13:48-16:08]（引擎后台持续运转）→至末切片末端 SkipToNext 跳曲目3（无切片→线性）——全时间轴与墙钟吻合；修复前症状（后台停摆+回前台瞬间跳转）未出现。DB 佐证：track_slices 8 行（本次测试新增 id=7/8 两切片在曲目2，**未清理，可在切片管理手动删**）
- ✅ 通知点开：桌面+通知栏点播放器通知 → MainActivity 前台显示在播内容
- ⛔ 下载全链：本地专辑的下载/保存按钮 enabled=false（asmrOneTree 为空）。根因：`api.asmr-200.com/api/search/...` 请求 ~2.6s 后被主动 Canceled（dlsite sign url 同样被取消），在线树拉不下来——**phase-A 前既有现象**（`git diff refactor-r2/phase-A..HEAD -- AlbumDetailScreen.kt` 零改动；B5 对 VM 的改动是纯数据访问改写，加载/取消逻辑零触碰）。强网环境或换专辑可能可测，留待后续
- 🔍 顺带发现（非重构问题，留档）：① `am start --es start_route now_playing` 直接 FATAL——`now_playing` 是 MainContainer overlay 不是导航图路由，navigateSingleTop 抛 IllegalArgumentException 崩溃（route extra 未校验，冷启动 route 表外值会崩）；② media_session 的 PlaybackState 稀疏更新（仅 seek/播放态变化时），**不能**当实时位置真值，UI 时间标签才是；③ 迷你播放器 CoverOnly 模式=右下角圆形封面钮，点击只切 Expanded，标题区点击才开 NowPlaying；④ 横屏布局不提供裁剪按钮/切片开关（`!landscapeControls` 分支），且旋转导致 activity 重建会丢失 NowPlaying overlay 状态（remember 非 saveable）——测试必须竖屏；⑤ run-as 拉库：PowerShell 文本管道损坏二进制，须 `adb exec-out` + python subprocess 字节写入，再 python sqlite3 读（MIUI 无 sqlite3 且 run-as 不能写 /sdcard）；⑥ 含二次元封面的 screencap 过图像审查会被拦，UI 验证走 uiautomator dump 文本。
