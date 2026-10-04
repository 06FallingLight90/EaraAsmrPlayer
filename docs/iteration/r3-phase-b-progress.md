# R3 阶段 B 中途停工留档（2026-10-05）

> 状态：阶段 B 执行至 B4 完成（代码层面），**全量测试未跑**（B6 门禁项），B4 收尾因一处编译错误挂起。
> 基线：`refactor/architecture-cleanup`，阶段 A tag `refactor-r3/phase-A` 之后新增 8 个提交。

## 1. 已提交进度（B0 → B3）

| 提交 | 任务 | 要点 | baseline 变化 |
|---|---|---|---|
| `cd86980` | B0 检测前置 | ci_guard 新增包级 SCC ratchet（实测 50 包团上界，含 Tarjan 自检）+ baseline 失效条目检测 + `tools/refresh_baseline.py` 机械重写脚本；清 5 条历史 stale | 344 → 339 |
| `9075933` | B1a | TreeFileType 族（枚举+LocalTreeLeafCacheEntry+6 纯函数）→ `domain.model`，15 文件 42 处 import 重写 | 339 → 315 |
| `03ca0b6` | B1b/B1c | TagSource → `domain.model`；CachePolicy/CacheImageModel/AppCacheLimits → `util`（cache 包内 6 同包裸用 + 2 同包测试补 import） | 315 → 290 |
| `11e3d56` | B1d | LibraryQuerySpec/Sort/SourceFilter → `domain.model`（拆文件）；**LibraryQueryBuilder 迁 `data.repository`**（偏离计划"留 db.query"：SCC ratchet 实测 db.query→domain.model + repository→db.query 会把 db.query 拽入 50 团）；LibraryReadRepository 新增出口 `albumsPaged(spec)`/`observeAlbumsByLastPlayed()`，ui 不再构建 Room SQL；LibraryViewModel 通配换型 2500→2494（pin 同步收缩） | 290 → 276 |
| `d9148d9` | B1e | 7 个投影 DTO（TagWithCount/LibraryTrackRow/LibraryTrackAlbumHeaderRow/AlbumGroupStatsRow/AlbumGroupTrackRow/PlaylistStatsRow/AlbumListeningRow）→ `domain.model`；**RemoteSubtitleSource 自 util → domain.model（domain.model 出边清零、退出连通团，dao/db 级联脱团）** | 276 → 262 |
| `d9e319d` | B1f | AudioOutputRouteKind → `util`（main 侧 7 条盲区同步合法，B3 补检测费 10→4） | 262 → 257 |
| `98993af` | B2 | 环1（HardwareVolumeOverlay→ui/common/audio + ThemeMediaSource/toThemeMediaSource→ui/player 计划外新证据 + MediaItem.isVideoPlaybackItem→util）；环2（computeCenterWeightedHintColorInt→util/CenterHintColor.kt）；环6（DEEPSEEK 两常量→util/DeepSeekSupport.kt）；环11（ImagePreviewDialog 弃 AsmrApp 强转改 Hilt EntryPoint，net-stack +1 显式入 baseline 留 B5）；倒挂（isOnlineMedia→util/OnlineMediaSupport.kt；PageTranslationUi→ui.translation 包 + ui.translation 入共享白名单） | 257 → 256 |
| `4c2b1f3` | B3 | ui-to-service 规则源包含 main + MainPackageRuleCheckSample 夹具；存量 PlaybackService×4 入 baseline | 256 → 260 |

## 2. 工作树中未提交的 B4 改动（代码编译红，禁提交——先测试后提交纪律）

> 暂存状态：全部 B4 改动均在工作树**未暂存**（曾误随文档提交，已 soft reset 纠正并从 index 移除；未 push，无历史污染）。

环7（data.remote↔data.repository）与环8（data.download↔data.local.library）代码已完成，另计划外完成 **NetworkHeaders 下沉 util**：

- **环7**：`util/NetworkTrafficSink.kt`（新接口）+ `di/StatisticsModule.kt`（@Binds 绑定）+ `StatisticsRepository` 实现（override addNetworkTraffic）+ `TrafficStatsInterceptor` 注入接口。
- **环8**：`data/local/library/DownloadStoragePort.kt`（新端口 `DownloadStorage`）+ `LocalAlbumMergeService` 参数改端口类型 + `DownloadStorageGateway` 实现（override stableIdentity）；构造点均为手动构造（DownloadManager 两处 + 测试一处），无需 Hilt 绑定。
- **NetworkHeaders 迁移（计划外）**：环7 倒置后出现新 2-环 `data.remote↔util`（DlsiteAntiHotlink→NetworkHeaders 常量既有边 + TrafficStatsInterceptor→util 新边成环）；`NetworkHeaders.kt` 迁 `util/` + 29 文件 import 重写。**该迁移引发 SCC 大幅塌缩 48 → 41**（7 包级联脱团），`package-scc-baseline.txt` 已收缩至 41。
- baseline 已刷新：260 → 256（ui→data.remote 段 −4）。

**挂起的唯一已知编译错误**（最后一次三源集编译 BUILD FAILED，仅此一处）：
- `app/src/main/java/com/asmr/player/data/remote/RemoteFileSize.kt` 第 22/30 行裸用 `NetworkHeaders.HEADER_SILENT_IO_ERROR/SILENT_IO_ERROR_ON`——原与定义同包免 import，迁 util 后需补 `import com.asmr.player.util.NetworkHeaders`（1 行）。
- 修复后须重跑：`:app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin` 三源集 + `python tools/ci_guard.py`，绿后方可提交 B4。

## 3. 环状实测快照（B4 代码完成后、未提交时）

- **2-环：18 → 13**。消解：环1 main↔ui.player、环2 cover↔theme、环4/5（B1c 连带）、环6 di↔subtitle、环7、环8、环11 root↔cover、双倒挂、data.remote↔util。剩余：10 个 root↔X 结构性（经 R，接受）+ 环3 library↔albumdetail（缓）+ 环9 download↔remote.download（缓）+ ui.player↔nowplaying（非违规同 feature）。
- **SCC：50 → 41**（B1e domain.model 纯叶子化 −1 批 + NetworkHeaders 迁移级联 −7）。
- **import baseline：344 → 256**；size pin：LibraryViewModel 2500 → 2494，其余 7 条未动。
- **测试基线**：945/0/4 为阶段 A 数值；阶段 B 各提交仅做三源集编译验证，**全量 `:app:testDebugUnitTest` 尚未复跑**（B6 门禁必做）。

## 4. 复工顺序建议

1. 补 RemoteFileSize.kt 的 NetworkHeaders import → 三源集编译 + guard 双绿 → 提交 B4。
2. B5 穿透收口（repository 出口 / ImageCacheEntryPoint 中立门面 / net-stack 28 条 / DataStore 12 条；ImagePreviewDialog 的 net-stack +1 与 PreviewImageGallerySaver 条目一并下沉）。
3. B6 门禁：全量测试（基线只增不减 945）+ 子代理审查 `git diff refactor-r3/phase-A..HEAD` + 实机走查（库/详情/下载/播放链）+ tag `refactor-r3/phase-B`。
