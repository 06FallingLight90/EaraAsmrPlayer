# 2026-10-03 — R2 阶段 C2 收官（NowPlayingScreen 拆分完成）

## 成果

- C2 两提交 + P0 修复：`66643d6`（辅助区四文件 + TagAssignDialog 消环）→ `0a4d16f`（主体三分支/覆盖层下沉）→ `d3922ce`（审查 P0 修复）。
- NowPlayingScreen 2902 → **836 行**，退出 size pin（守卫下行断言触发移除）。
- 测试 922/0/0；ci_guard 含规则自检通过。

## 新增文件（全部入库，`com.asmr.player.ui.player` 包）

| 文件 | 职责 |
|---|---|
| NowPlayingLayoutSupport.kt | 布局度量/静态播放快照/常量（原 private 降 internal） |
| NowPlayingSurfaceSupport.kt | PlaybackProgressContent/视频比例/表面切换动画 |
| NowPlayingListenTogetherAudience.kt | 一起听听众呈现族 |
| NowPlayingIdentity.kt | 横竖屏身份区/艺术家解析/滑动提示 |
| NowPlayingOverlays.kt | 标签对话框/切片 sheet+时间编辑/均衡器 sheet（含音量浮层） |
| NowPlayingLandscapeLayouts.kt | 平板 split + 手机横屏双分支 |
| NowPlayingPortraitLayout.kt | 竖屏 classic/expanded 双形态 + homeLayout 过渡动画 |

- TagAssignDialog 迁 `ui/common/dialog/`：消除 `ui.player → ui.library` 引用环；baseline-prof/startup-prof 二进制路径同步；import-direction baseline 条目迁移。

## 审查结论（子代理，diff refactor-r2/phase-B..HEAD）

**P0×1（已修复 d3922ce）**：主体三分支提取时主文件条件漏 `phoneLandscape`，手机横屏错落竖屏布局——修 `if (split || phoneLandscape)`。教训：**多分支体逐段提取时，包裹分支的条件本身留在主文件，重构后必须逐分支核对路由条件**；单测无 composable 路由覆盖，未拦截。

P2 备忘：
1. `changeNowPlayingHomeLayoutMode` remember key 由值改传 MutableState 对象——闭包内读写等价，仅闭包 identity 不再随 hint 值变化重建（理论性差异：拖拽进行中恰逢 hint 变化才可观察）。
2. 括号结构/AnimatedContent 骨架/LaunchedEffect key 逐行核对一致。
3. TagAssignDialog 迁移完整。
4. ~~PortraitLayout 重复 import alpha~~（已顺手清理）。
5. 建议（backlog）：为三分支路由补 composable 级测试。

## 切片搬移技法沉淀

- PowerShell 花括号平衡追踪定位闭合错位（本次 landscape/portrait 各错位一次，手工修 `} else if` / 多余 fun 闭合）。
- 分支体提取流程：先文本替换 setter 通道（`{ videoFullscreen = true }`→`onOpenVideoFullscreen`、`showSliceSheet = true`→`setShowSliceSheet(true)` 等），再编译迭代修 unresolved（isVideo/playerPageAccentColor 等漏传参数）。
- 跨行文本 Replace 失败时直接行级定位修（onGloballyPositioned 块）。
- 字符串 replace 顺序会造成 `.valueState.value` 污染——替换后必须 grep 复核。
- paramBlock 单引号 here-string 中 `` `r`n `` 不展开，须双引号。
- by 委托状态改显式 MutableState 传参时：**remember key 语义从"值"变"对象"**（恒定不重建）——需逐个评估闭包重建依赖。

## 遗留

- C1+C2 实机走查待设备（清单 docs/behavior-notes/maincontainer-routes.md + 正在播放页全交互：横竖屏/平板 split/歌词 surface/切片 sheet/均衡器/音量控制/视频全屏/一起听听众条）。
- CI 双绿待 push。
