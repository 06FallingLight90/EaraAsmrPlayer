# 2026-10-03 — R2 阶段 C3 收官（SettingsScreen 区块化 + 消环完成）

## 成果

- C3 三提交 + P2 清理：`a976068`（屏蔽词域下沉消环）→ `7d0f45c`（死 import/失效 baseline 清理）→ `f5aafe3`（两刀切分）→ `e9d0923`（P2 清理）。
- SettingsScreen 2675 → **1270 行**，退出 size pin。
- 测试 922/0/0；ci_guard 含规则自检通过。

## 消环（C3a）

- 新建 `ui/common/core/SearchBlockedKeywordsViewModel`（注入 SettingsRepository，三成员逐字透传；置于 common/core 过 feature-to-feature 白名单——ui.search 位置会触发 4 条新违规）。
- SettingsViewModel 删除 4 屏蔽词成员；SettingsScreen（blockedKeywords 分区）、AlbumDetailScreen（settingsViewModel 参数整体替换为 blockedKeywordsViewModel）、HotListening/Library/Search 三 Screen 内部 hiltViewModel 换用。
- MainRouteHost.settingsViewModel 字段改名 blockedKeywordsViewModel（该字段只服务 album_detail byRj/byId 装配）。
- **AlbumDetail ↔ Settings 双向引用消除**（AlbumDetailHero 死 import 一并清）。

## 切分（C3b）

| 文件 | 内容 |
|---|---|
| SettingsScreen.kt | 主函数（列表+detail 双页骨架 + 9 分区装配）1270 行 |
| SettingsScreenSections.kt | 歌词页/翻译/字幕模型/APP 缓存/屏蔽词各分区 composable 族 |
| SettingsScreenScaffold.kt | 列表面板/分区选项/Detail 头卡卡片/通用开关滑杆行/InfoTip/Theme chips |
- SettingsSection enum private→internal。

## 审查结论（子代理）

P0/P1 无；P2×1（两新文件残留未使用 import，已清理 e9d0923）。核验通过项：新 VM 与原四成员逐字一致、五处 collect 口径未变、AlbumDetail settingsViewModel 彻底移除、切分行多重集对比零函数体差异、enum internal 全通。

## 教训（重要）

1. **死 import 批量删除脚本会误删在用 import**（正则提取符号查 body 引用的方案不可靠），且新文件无法从 git 恢复——正确流程：**从 HEAD 重建文件，仅精确删除 guard 命中的规则域 import**（guard 只扫 import 行不查使用，未触规则的死 import 留存无害，可后续按审查清理）。
2. PS 管道枚举数组会展开单元素为 Char——数组重建一律 `List[string]+Add`，勿用 `$out += $L[$range]` 管道写法。
3. 主函数体内 9 分区装配块（LocalLibrary ~190 行等）未提取——backlog，与 C5 ratchet 收紧时一并评估。

## 遗留

- C1+C2+C3 实机走查合并待设备（新增：设置页各分区全交互 + 专辑详情屏蔽词快捷添加）。
- CI 双绿待 push。
