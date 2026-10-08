# 阶段 1 审查报告（腐蚀最小化）

- 审查基准：`git diff refactor/start..HEAD`（7 提交：fb4a7c3…9bdb7d7）
- 审查方：独立审查 agent（只读，逐字节比对 + 全仓 grep 独立核验，不信任提交信息）
- 全量测试：840 tests / 0 failures / 4 skipped（门禁前刚跑）
- **总判定：通过**（P0/P1 零发现）

## 行为保持：通过

- 7 个活链函数（DirectoryBrowserPanel / DirectoryBatchBarEmbedded / DirectoryFolderRow / CompactDirectoryBreadcrumbContent / CompactBreadcrumbNode / DirectoryActionGroupButton / DirectoryFileRow）函数体经逐字节比对与 refactor/start 完全一致，仅 4 处签名/调用点重命名；rememberSaveable 字符串键未动
- 被删符号全仓零残留（代码 + 两个 profile 文件）；活链定义唯一、3 个外部调用点同步
- baseline-prof / startup-prof 无旧版本号符号，新名条目齐全；S4/S5 纯 git mv（0 行改动）不影响 profile
- e002a43 的 8 行「新增」确认为 diff 移动对齐产物（净内容为零）

## 测试基建：通过

- user.home 重定向用 layout.buildDirectory，可移植无本机路径；assumeFalse 仅作用于单个测试方法（L73）
- P2 备注：重定向作用于全部单测（含非 Robolectric），依赖真实 user.home 的测试环境会被静默改变——当前 840 全绿，已文档化

## 范围与 git 卫生：通过

- 各提交只动清单内文件；类型+编号前缀齐全；删除与重命名分步；无密钥/生成物入库；ci.yml permissions 最小化

## 文档真实性：基本通过，4 条 P2

1. ARCHITECTURE.md L42「networkmodule」、L77「playerviewmodel.kt」为重命名前旧名（文档提交先于 S5 的时序漂移）→ 已随手修复
2. ARCHITECTURE.md「playback 32 个文件」实为 31 → 已随手修复
3. 提交信息行数口径与实际统计口径不一致（e002a43「3977→3175」vs 实际 4189→3387，净 802 正确；442ff20「83 行」实为 101 行）→ 历史信息不改，后续提交统一按 Split("`n") 口径
4. landing_zh.md:84 版本号 v0.2.2 未随 P0-4 更新 → 已随手修复（v1.2.3）
