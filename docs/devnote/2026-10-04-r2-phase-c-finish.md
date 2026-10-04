# 2026-10-04 — R2 阶段 C 收官（C1–C5 + 门禁 + CI 闭环）

## 新增文件（入库）
- `data/repository/OnlineContentRepository.kt`、`UpdateRepository.kt`、`SearchRepository.kt`、`DownloadQueueRepository.kt`（C4a/C4b 下沉产物）
- `data/download/`（DownloadManager/DownloadDestination/DownloadDirectoryCoordinator 迁入）、`data/local/db/query/`（LibraryQuerySpec 族）
- `data/remote/crawler/AsmrOneTracksSupport.kt`、`data/remote/RemoteFileSize.kt`、`data/remote/CoverSupport.kt`、`util/CoverUrlSupport.kt`
- `docs/behavior-notes/online-content-caching-scope.md`（ASMR.ONE 缓存进程级共享 + 并发语义档案）
- `tools/guard-selftest/ui-to-data-remote/MainPackageRuleCheckSample.kt`（main 包规则反例夹具）

## 阶段 C 成果一览（详见 iteration/r2-phase-C-review.md，tag refactor-r2/phase-C @ 814627b）
- C1/C2/C3：MainContainer 2375→796、NowPlayingScreen 2902→836、SettingsScreen 2675→1270，均退出 size pin。
- 消环：ui.player→ui.library、AlbumDetail↔Settings、ui.sidepanel→ui.library。
- C4：4 类新 repository 下沉（在线内容编排/应用更新/搜索/下载队列）；AlbumDetailViewModel 2996→2510、LibraryViewModel 2641→2521；import-direction baseline 265→196 区间多轮收缩。
- C5 结论：1500→800 需先做编排层 state holder 重构（云同步/删除/扫描族是 UI 状态机），列为后续方向。
- 门禁：测试 906→938 全绿；子代理审查无 P0、P1×2 闭环（缓存并发安全、ci_guard main 包盲区）；实机走查通过；CI 双绿。

## 本日修复
- P1-1：OnlineContentRepository 缓存并发（repo 化后多 IO 协程并发写无锁 linkedMapOf）→ ConcurrentHashMap + cacheMutex 串行复合读写，在途去重 putIfAbsent 原子化（f8206cc）。
- P1-2：ci_guard `ui-to-data-remote` 源包扩 `com.asmr.player.main`（C1 拆分文件携带 data.remote import 逃逸 ratchet），补反例夹具 + 4 条存量入 baseline（f8206cc）。
- CI：`:baselineprofile:assemble` 在无设备 runner 必挂（androidx.baselineprofile 插件 assemble 连带生成 profile 需真机）→ 改 `:baselineprofile:assembleAndroidTest`（9593019）。教训：G3 加此步时本机一直连着小米 14，从未暴露；**CI 门禁步骤新增后应在无设备环境 dry-run 任务图**。
- 杂项：清误入库的 `__pycache__` pyc + 补 ignore 规则（ffca9ae）。

## 新证据 / 决策记录
- `ensureAlbumCoverSaved` 双实现分叉（VM 旧版仅网络/2048/ARGB_8888 vs repo 版支持本地来源/1280/RGB_565）：统一属行为变更，**待用户决策**，已入 ARCHITECTURE.md backlog。
- `applyResolvedCloudSync`（VM，title 覆盖）与 `applyManualCloudSyncSuccess`（repo，title 保留）为两条有意不同的规则，勿合并。
- rescanDocumentAlbum/syncScannedLocalAlbumTracks 不内嵌树缓存写（stampProvider 由 VM 注入）为已录行为，勿顺手统一。
- ui-to-dao/ui-to-net-stack/ui-to-service 对 main 包同结构盲区：当前无实际违规，待有需求再扩（防规则空转）。
- Robolectric PageTranslationUiTest 偶发 "component not displayed" flake：单跑复验即可，非回归。

## 踩坑
- 后台 Shell 跑 `.\gradlew-local.bat` 会因 cwd 漂移报 "not recognized" 且 **exit 0 是假象**（PowerShell 非终止错误）——构建命令前台运行或用绝对路径调用；脚本文件名无前导点。
- ci_guard.py 自带规则自检（"含规则自检"），`tools/guard-selftest/` 只是夹具目录，不能 `python tools/guard-selftest` 直接执行。
