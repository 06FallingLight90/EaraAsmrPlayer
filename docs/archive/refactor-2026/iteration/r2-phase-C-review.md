# R2 阶段 C 门禁报告（随批次推进追加）

## C1 — MainContainer 拆分（2026-10-03）

### 范围
- 提交链：`98e1fcc` → `36964f6` → `15ea5e5`（diff 基准 `refactor-r2/phase-B`）
- 成果：MainContainer 2375 → 796 行，退出 size pin；区块 UI 五文件 + 状态/效果簇五文件（职责表见 devnote 2026-10-03）
- 新增路由测试：MainContainerRouteTest 钉全部路由；SaveableStateTest 改驱动生产入口

### 三件套
| 项 | 结果 |
|---|---|
| 全量测试 | 922/0/0，与基线持平（只增不减口径） |
| 子代理审查 | **通过**。P0=0，P1=0，P2×4（navigationRequestId 降级普通 var / DisposableEffect key 放宽 / 音量复位 effect 顺序 / miniPlayer effect 时机——均行为等价，备忘勿顺手改） |
| 实机走查 | **待设备**（start_route 冷启动全路由清单见 docs/behavior-notes/maincontainer-routes.md） |
| ci_guard | 通过（含规则自检）；MainContainer 行从 size baseline 移除 |

### 决策记录
- `pendingAutomaticInstallPath` 仅自动更新簇内使用，随 AutomaticUpdateEffects 一并搬入（saveable 口径保持）。
- `navigationJob` 保持 `mutableStateOf<Job?>`：它是同文件两个 LaunchedEffect 的 key，降级会破坏重启语义。
- 已提交搜索条件（submittedSearch* 11+1 项）打包为 SubmittedSearchState，saveable 工厂模式；signal 补回 rememberSaveable。

### 遗留
- 实机走查（待设备在线）
- CI 双绿待 push

## C2 — NowPlayingScreen 区块化（2026-10-03）

### 范围
- 提交链：`66643d6` → `0a4d16f` → `d3922ce`（P0 修复）
- 成果：NowPlayingScreen 2902 → 836 行，退出 size pin；辅助区四文件 + 主体三分支/覆盖层三文件；TagAssignDialog 迁 ui/common/dialog 消 ui.player→ui.library 环

### 三件套
| 项 | 结果 |
|---|---|
| 全量测试 | 922/0/0，与基线持平 |
| 子代理审查 | P0×1（三分支路由漏 phoneLandscape，手机横屏错落竖屏布局——已修 d3922ce）；P1 无；P2×4 备忘（homeLayout remember key 由值改 state 对象等价但闭包 identity 变化 / 括号结构核对无误 / TagAssignDialog 迁移完整 / 重复 import 已清）；建议 backlog：三分支路由 composable 级测试 |
| 实机走查 | 待设备（与 C1 合并：正在播放页全交互——横竖屏/平板 split/歌词 surface/切片 sheet/均衡器/音量/视频全屏/一起听听众条） |
| ci_guard | 通过；NowPlayingScreen 退出 pin；AudioOutputRouteKind baseline 条目随拆分迁移（Screen/PortraitLayout/Overlays 三条） |

### 决策记录
- homeLayout 提示簇 4 状态改显式 MutableState 传参（saveable 口径不变）；remember key 由值变对象，闭包 identity 不再随值重建——审查判定等价（理论性差异仅拖拽中恰逢 hint 变化）。
- motion 修饰符（routeTransition.nowPlayingMotionModifier）随各分支内部自行获取，主文件不再集中声明。
- 覆盖层（tag/slice/equalizer）提取为独立 Host composable，PlaybackProgressContent 复用保持原结构。

### 遗留
- 实机走查（与 C1 合并，待设备）
- CI 双绿待 push

## C3 — SettingsScreen 区块化 + 消环（2026-10-03）

### 范围
- 提交链：`a976068` → `7d0f45c` → `f5aafe3` → `e9d0923`
- 成果：SettingsScreen 2675 → 1270 行，退出 size pin；屏蔽词域下沉 ui/common/core/SearchBlockedKeywordsViewModel，**AlbumDetail↔Settings 双向引用消除**（五 Screen + MainRouteHost 全部换用新 VM）

### 三件套
| 项 | 结果 |
|---|---|
| 全量测试 | 922/0/0（一次 PageTranslationUiTest flake 为 backlog 已知，单独重跑通过） |
| 子代理审查 | **通过**。P0/P1 无；P2×1（两新文件残留未使用 import，已清 e9d0923）。核验：新 VM 与原成员逐字一致、五处 collect 口径未变、切分行多重集对比零函数体差异 |
| 实机走查 | 待设备（新增：设置页各分区全交互 + 专辑详情屏蔽词快捷添加） |
| ci_guard | 通过；SettingsScreen 退出 pin |

### 决策记录
- 新 VM 置于 ui/common/core（共享层白名单）而非 ui.search——后者会触发 4 条 feature-to-feature 新违规。
- MainRouteHost.settingsViewModel 字段改名 blockedKeywordsViewModel（该字段仅服务 album_detail byRj/byId 装配）。
- 主函数体内 9 分区装配块未提取——backlog，与 C5 一并评估。

### 遗留
- 实机走查（C1+C2+C3 合并，待设备）
- CI 双绿待 push

## C1+C2+C3 实机走查（2026-10-03，小米 14 / Android 14，7f264f85）

环境：HEAD f5aafe3 构建 assembleDebug（adb install -r Success）。方法：start_route 冷启动直达 + uiautomator dump 文本验证（截图被图像审核拦截改用文本）；走查中途设备自动息屏一次（Dozing 导致空 dump），`svc power stayon usb` 常亮后恢复。

| 项 | 结果 |
|---|---|
| 8 条 primary 路由（library/search/hot_listening/favorites/playlists/groups/listening_calendar/settings） | ✅ 全部正常渲染、零崩溃（settings 列表=C3 拆分后 Scaffold 骨架） |
| 二级路由 downloads / dlsite_login / album_detail_rj | ✅ 全部打开（downloads 任务管理区、dlsite 登录页凭证掩码、详情页头卡+标签+「1 人正在听」） |
| 正在播放页竖屏（C2 PortraitLayout） | ✅ 头卡（标题/社团/RJ/CV/标签）+ 控制行全套 desc（片段裁剪/收藏/添加到播放列表/标签管理/音效面板/切片播放/播放模式/播控组） |
| 歌词 surface 切换 | ✅ 手动绑定歌词入口出现（仅 LYRICS surface 提供的既有行为） |
| 切片管理 sheet（C2 SliceOverlaysHost） | ✅ 标题/清空/切片条目（09:04→13:46 时间编辑）/概览条拖动手柄/播放与删除切片按钮 |
| 均衡器 sheet（C2 EqualizerSheetHost→EqualizerPanel） | ✅ 音效器/响度均衡/阈值/场景预设/效果强度 |
| **手机横屏（C2 审查 P0 修复路径）** | ✅ cur=2670x1200 横屏正确进入 PHONE_LANDSCAPE 分支：左栏封面+右栏身份区（社团/CV 格式）+歌词预览，控制行无竖屏专属按钮——`if (split || phoneLandscape)` 修复生效 |
| 设置 detail 分区（C3） | ✅ 本地库（下载目录/刷新/云同步/添加目录）+ 屏蔽词（输入/添加/空态） |
| 屏蔽词读写通路（C3 新 VM） | ✅ 用户手动验证添加出 chip、点 chip 移除正常 |
| 崩溃/ANR | 全程无 FATAL EXCEPTION / ANR |

结论：C1+C2+C3 实机走查全部通过。备注：start_route 直达二级页时 uiautomator dump 前几条文本是底层 library 内容，需看全量文本判断（初次误判已纠正）。

## C4 批次收官（2026-10-04，81faac6 + 62f092b + f519a1e + ed8f81f/967bc7a/ea6bd69/8911221/5e90980/54f32ad/1cdcfbe）

### C4a/C4b/C4c 成果
| 批次 | 内容 | VM 行数 |
|---|---|---|
| C4a | DownloadDestination 族迁 data/download、UpdateRepository、SearchRepository、DownloadQueueRepository | baseline 160→134 |
| C4b-1/2 | AlbumDetail 家族 44 处 stale import 清理、DownloadManager 整体迁 data/download | 265→213 |
| C4b-3/3b/3c/3d | OnlineContentRepository 下沉（ASMR.ONE 解析族/云同步族/封面补全/文件体积/推荐富化）、DlsiteAuthStore Hilt 单例化 | AlbumDetailVM 2996→2518 |
| C4b-4 | LibraryQuerySpec 族迁 data/local/db/query，消 ui.sidepanel→ui.library 环 | — |
| C4c-1 | LibraryViewModel 依赖通道化（删 dlsiteScraper/dlsiteProductInfoClient/asmrOneApi，下载队列走 repo） | LibraryVM 2641→2617 |
| C4c-2 | LibraryViewModel 死代码清理（字幕候选族等 3 处零引用成员） | 2617→2521 |

测试 938/0/4 全绿；ci_guard 含规则自检通过。

### C5 评估结论
- ratchet 按实际收缩持续还债（AlbumDetailVM 2996→2518、LibraryVM 2641→2521）。
- **1500 收紧不可达**：剩余主体是云同步编排（_syncStatus/选择队列/消息深耦合）、删除族（SAF+消息）、扫描编排（mutex/bulk 进度）——均为 UI 状态机性质，下沉只搬运代码+回调透传，与 C4b-3d 评估同性质。全额收紧需编排层重构（state holder 化），超出阶段 C 范围，列为后续方向。

### 行为差异备忘（有意保留）
- ensureAlbumCoverSaved 双实现分叉（VM 版仅网络/2048/ARGB_8888；repo 版支持本地来源/1280/RGB_565）——统一需用户决策。
- applyResolvedCloudSync（title 覆盖）vs applyManualCloudSyncSuccess（title 保留）两条规则并存。

### 踩坑
- 后台 Shell 跑 `.\gradlew-local.bat` cwd 漂移报 not recognized 且 exit 0 假象（PS 非终止错误）——前台或绝对路径调用。
- Robolectric PageTranslationUiTest 偶发 "component not displayed" flake，单跑复验即可。

### 门禁状态
- 全量测试本机绿：✅（938/0/4）
- 子代理审查：进行中（diff refactor-r2/phase-B..HEAD）
- 实机走查：待设备（C4 面向：详情页云同步/封面补全/DLsite Play 登录态、库页扫描/云同步/删除、下载队列取消）
- CI 双绿：待用户 push

## 阶段 C 门禁审查结果（2026-10-04，diff refactor-r2/phase-B..HEAD，31 commits）

**结论：无 P0，2 P1 + 4 P2；核心下沉逐行等价成立。**

已核对等价（审查确认）：resolveAsmrOneWork TTL/去重、getAsmrOneTracksCached、applyManualCloudSyncSuccess 合并规则、ensureAlbumCoverSaved、DownloadQueueRepository 转发、SearchRepository/UpdateRepository、VM 退出取消语义；新增测试为行为级 seam 测试，无 TDD 反模式。

| 级别 | 项 | 处置 |
|---|---|---|
| P1-1 | OnlineContentRepository 缓存并发：原 Main.immediate 单线程→repo IO 多线程并发写无锁 linkedMapOf | ✅ 已修（f8206cc）：ConcurrentHashMap + cacheMutex 串行复合读写，在途去重 putIfAbsent 原子化 |
| P1-2 | ci_guard ui-to-data-remote 不覆盖 main 包，4 处存量搬迁逃逸 ratchet | ✅ 已修（f8206cc）：源包扩 com.asmr.player.main + 反例夹具 MainPackageRuleCheckSample + 4 条存量入 baseline |
| P2-1 | AlbumDetailViewModel 两个零调用私有包装 | ✅ 已删（f8206cc），baseline 2518→2510 |
| P2-2 | OnlineContentRepository 仍直接实例化 DlsiteAuthStore | ✅ 已注入单例（f8206cc） |
| P2-3 | repo 单例化缓存共享语义漂移未录档 | ✅ 补 behavior-notes/online-content-caching-scope.md（814627b） |
| P2-4 | LibraryViewModel 全限定名调用 isLikelyPlaceholderCover（风格） | 备忘，不改（改则 pin +1 行，纯风格无风险） |

审查备注：其他 ui-* 规则（ui-to-dao/ui-to-net-stack/ui-to-service）对 main 包存在同样结构盲区，但当前 main 包无此类 import，未扩（防规则空转需夹具，待有实际需求再扩）。

门禁后全量测试 938/0 复验绿；守护含规则自检通过。

## C4 实机走查（2026-10-04，小米 14 / Android 14，HEAD f8206cc）

环境：assembleDebug + adb install -r Success（installDebug）。方法：start_route 冷启动直达 + uiautomator dump 文本验证；`svc power stayon usb` 常亮。

| 项 | 结果 |
|---|---|
| 冷启动 library 路由 + 库页渲染 | ✅ 专辑卡/标签/顶部 tab 全部正常 |
| 详情页 DL tab（initialTab=1，DLSite 在线加载） | ✅ 头卡/DLSite 与 ONE 源 chip/下载保存按钮/语言行全部渲染 |
| ASMR.ONE 解析路径（C4b-3 下沉核心，resolveAsmrOneWork 端到端） | ✅ 「ONE 暂未收录」= 搜索→未收录→缓存 全链完成 |
| 库页长按菜单（C4c-1 面向） | ✅ 删除/标签管理/添加到分组/本地同步/云同步 正常弹出 |
| 云同步链路（C4c-1 换道后） | ✅ 触发后正确发出 DLSite 请求（product/info/ajax）；dlsite.com 本机网络不可达 15s 超时——失败路径 reportSyncAlbumMetadataFailure 无崩溃，成功分支受限未走查 |
| DLsite Play tab / authStore 链路（C4b-3d） | ✅ initialTab=2 打开无崩溃，凭证读取链（VM 注入单例）正常 |
| 崩溃/ANR | 全程无 FATAL / ANR |

受限项（本机网络 dlsite.com 不可达）：DLSite 在线内容成功分支、在线下载全链、封面补全成功分支。以审查逐行等价结论 + 938 单测补足证据。

结论：C4 实机走查通过（含如实记录的受限项）。阶段 C 门禁三件套：本机测试绿 ✅ / 子代理审查 P1 已闭环 ✅ / 实机走查 ✅（CI 双绿待用户 push）。
