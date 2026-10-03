# 2026-10-03 — R2 阶段 C1 收官（MainContainer 拆分完成）

## 成果

- C1 批次三提交：`98e1fcc`（五区块 UI 提取，2375→1552）→ `36964f6`（死 import 清理 + searchBridge 提取，→1344）→ `15ea5e5`（状态/效果簇下沉五文件，→**796**）。
- MainContainer 退出 size-guard pin（baseline 行已删除；G1 下行断言规则触发"低于阈值即移除"而非收紧）。
- 测试 922/0/0 与基线持平；ci_guard 含规则自检通过。

## 新增文件（全部入库，`com.asmr.player.main` 包）

| 文件 | 职责 |
|---|---|
| MainSystemUiEffects.kt | 系统栏捕获/应用 + 方向锁定 effects |
| AutomaticUpdateEffects.kt | 自动更新簇（pendingAutomaticInstallPath 私有 saveable 状态一并搬入） |
| NowPlayingPresentation.kt | 播放页呈现状态 holder + 背景透明度 + 竖屏退出收尾 |
| MainNavigationRuntime.kt | primary 导航编排 / touch-block / scrollToTop 信号 / 已提交搜索条件 |
| MainContainerRuntime.kt | 硬件音量浮层 / 启动副作用 / mini 播放条 / 共享封面背景 / 返回键 / 对话框宿主 |

## 审查结论（子代理，refactor-r2/phase-B..HEAD）

P0/P1 均无；**P2 备忘 4 条**（后续勿"顺手"改）：

1. `MainNavigationRuntime.kt:53` `navigationRequestId` 由快照状态降为普通 var——仅协程内比较、主线程单写者，行为等价；若未来在组合中读取会静默不重组。
2. `MainNavigationRuntime.kt:136` `DisposableEffect(Unit)` → `DisposableEffect(state)`——key 实际恒定等价，语义略宽。
3. `MainContainerRuntime.kt:147` 音量复位 effect 注册顺序移到音量键 effect 之后——仅"开播放页与音量键 tick 严格同帧"时结果相反，输入时序不可达。
4. `MainContainerRuntime.kt:230` miniPlayer 同步 effect 注册时机提前——只写自身字段，无交叉写。

核对通过项（摘）：saveable 口径逐项一致（visible/backdropActive/backdropExitDurationMs/lastNonZeroAppVolumePercent/submitted*/pendingAutomaticInstallPath）；navigationJob 保留 `mutableStateOf<Job?>` 且 4 个 LaunchedEffect key 逐字一致；MainSearchAssistBridge 11 键观察/复位逐字等价。

## 提取模式沉淀（供 C2/C3 复用）

- 状态 holder 模式：`@Composable` 工厂内逐个 `remember/rememberSaveable` 建 state → `remember { Holder(...) }` 打包；类内用 `var x by state` 委托。**凡原实现是 rememberSaveable 的字段必须保持 saveable**（本轮曾漏 signal，编译前自查补上）。
- 参与其它 LaunchedEffect key 的字段必须保持快照状态（如 navigationJob），否则 effect 不再随其变化重启。
- `State<T>` 只读入参用 `val x by state`（不能 var）；`rememberXxxBackdropAlpha` 类函数返回裸 Float 时调用处用 `=` 不能 `by`。
- Effect 注册顺序尽量保持原相对位置（原文件中该簇位于哪些 effect 之间就插回哪里）。

## 遗留

- C1 门禁实机走查待设备在线（冷启动直达路由清单见行为档案 `docs/behavior-notes/maincontainer-routes.md`）。
- 阶段 C 审查报告落 `docs/iteration/r2-phase-C-review.md`（gitignored，随 C 批次推进追加）。
- CI 双绿待用户 push。
