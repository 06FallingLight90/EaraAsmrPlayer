# devnote/ — 开发记录

本目录记录二次开发过程中**新增的文件**、**本机环境变更**与**踩坑解法**，以及 **agent 协作约定**。

## 内容索引

| 文档 | 内容 | 何时查阅 |
|---|---|---|
| [2026-09-29-env-setup.md](2026-09-29-env-setup.md) | 首次环境搭建：新增文件清单、构建链路验证、踩坑与解法、常用命令 | 排查构建 / 安装 / 设备问题时 |
| [2026-09-30-refactor-start.md](2026-09-30-refactor-start.md) | 重构启动：S0 绿基线（840 tests）与测试基建修复、S1 CI 门禁 | 排查测试 / CI 问题时 |
| [2026-10-01-r2-start.md](2026-10-01-r2-start.md) | 第二轮重构 R2 启动：阶段 A（目录=包名、ci_guard 重写、A3 修复类变更）与切片 bug #322 定位 | 了解 R2 计划 / 阶段 A 成果时 |
| [2026-10-02-r2-phase-b-finish.md](2026-10-02-r2-phase-b-finish.md) | R2 阶段 B 收官：数据访问层下沉 LibraryRead/WriteRepository、B6 门禁（906 tests）、实机走查 5/6 与调试手段留档 | 了解 repository 边界 / 实机走查方法时 |
| [2026-10-02-r2-phase-c-g-batch.md](2026-10-02-r2-phase-c-g-batch.md) | R2 阶段 C 批次 G：size ratchet 收紧+松弛断言、4 条新 import 规则入 baseline、CI 编译门禁、PlaybackService 自定义命令白名单 | 了解守护规则 / 安全修复时 |
| [2026-10-03-r2-phase-c1-finish.md](2026-10-03-r2-phase-c1-finish.md) | R2 阶段 C1 收官：MainContainer 2375→796 拆分、五运行时/状态文件职责表、审查 P2 备忘、状态 holder 提取模式沉淀 | 拆分 God 组合函数 / 核对 C1 审查结论时 |
| [2026-10-03-r2-phase-c2-finish.md](2026-10-03-r2-phase-c2-finish.md) | R2 阶段 C2 收官：NowPlayingScreen 2902→836 拆分、七文件职责表、P0 路由条件修复教训、分支体提取技法沉淀 | 拆分多分支组合函数 / 核对 C2 审查结论时 |
| [2026-10-03-r2-phase-c3-finish.md](2026-10-03-r2-phase-c3-finish.md) | R2 阶段 C3 收官：SettingsScreen 2675→1270 拆分、屏蔽词域下沉消双向引用、死 import 清理教训 | 拆分设置分区 / 核对 C3 审查结论时 |
| [2026-10-04-r2-phase-c-finish.md](2026-10-04-r2-phase-c-finish.md) | R2 阶段 C 收官：C1–C5 全程（三 God 组合函数拆分、3 环消除、4 类 repository 下沉、两 God VM 2996→2510 / 2641→2521）、门禁审查 P1 闭环、CI baselineprofile 门禁修复 | 核对阶段 C 成果 / 下沉 repository 边界 / CI 门禁踩坑时 |
| [agent-collab.md](agent-collab.md) | **agent 协作总纲（唯一）**：硬规则与 git 纪律、入库可移植性、构建验证循环、重构任务循环与范围纪律、阶段门禁三件套、行为档案机制、守护修改流程 | agent 开始改代码前（重构任务亦然，§6-§9） |

## 记录约定

每次开发产生以下内容时，**当日日期命名的新笔记**中追加（如 `2026-10-05-xxx.md`）：

- 新增的非源码文件（脚本、配置、临时工具）— 注明路径、用途、归属（**入库** 或 **本机**）
- 本机专属配置的变更（JDK、SDK、Gradle 主目录等）
- 排查过的坑与最终解法 — 保留「为什么」，让后人无需重蹈

约定归属标记：**入库** = 提交进仓库、其他机器可直接用；**本机** = 只在本机存在，换机器需重建。
