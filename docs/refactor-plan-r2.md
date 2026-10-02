# 第二轮重构计划（R2）—— 绞杀者局部重写路线

> 依据：`docs/project-quality-review-20261001.md`（总评 C）+ 2026-10-01 全项复核（P0/P1 论断全部实证坐实）。
> 与第一轮（`refactor-plan-tdd.md`，S0-S16 已完成）的关系：第一轮完成文件级治理（行数/去重/死代码/拆包/加密/守护框架），本轮解决**依赖级**问题（穿透/环/God 组件/守护失效）。
> 状态：阶段 A 已完成（tag `refactor-r2/phase-A`）；阶段 B 已完成（tag `refactor-r2/phase-B` @ 9afcccb，测试 906/0/4，实机走查 5/6，2026-10-02 收官，报告 docs/iteration/r2-phase-B-review.md）；**阶段 C 待开工**——开工前先执行 2026-10-02 体检报告的「第一阶段：补闸门与校准」（size ratchet 收紧回收 805 行配额 + 新守卫规则 `ui-to-data-remote`/`data-to-feature`/`feature-to-feature` 存量入 baseline + CI 加 androidTest/baselineprofile 编译门禁 + `onConnect` 包名白名单；ARCHITECTURE.md 数字校准已完成于 2026-10-02）。CI 双绿待用户 push。

## 0. 已确认的四项决策（用户 2026-10-01 拍板）

1. **目标口径**：用户无感知（界面/交互/数据不变）、seam 小改动、内部工程化规范化、焕然一新易于读和维护的全面重构。近半年功能更新少，维护工作集中在本次。
2. **路线**：绞杀者局部重写——对热点直接按新分层重写，冷区守护冻结，不做全仓重写。
3. **隐性行为存量随改随文档化**：重写过程中发现的有价值隐性行为（如 DLsite 四级 locale 回退、镜像切换逻辑），当场记入行为清单，保证日后项目内部可感知。
4. **回归兜底双保险**：动手前先补 seam 测试钉住现行为 + 每个重写任务配实机走查清单。

## 1. 总原则（沿用第一轮 + 新增）

沿用：TDD 安全网（测试只增不减）、一任务一提交、阶段门禁（全量测试 + 子代理审查 `git diff <上阶段tag>..HEAD` + 报告落盘 docs/iteration/ + tag `refactor-r2/phase-N`）、git 硬规则（主 agent 唯一写操作人、禁 reset --hard/force push/rebase、push 时机由用户决定）。

新增：
- **行为档案先行**：每个局部重写目标动手前，先产出该 seam 的行为档案（行为描述 + 代码位置 + 钉测试引用），落 `docs/behavior-notes/<seam>.md`。
- **修复类行为变更单独标注**：极少数属"修复"而非"保持"的项（如 DownloadWorker 重试、OkHttp 超时），单独提交 + 单独实机验证，与"用户无感知"主体严格区分。
- **守护先于手术**：A 阶段防线可信之前，不开始 B 阶段任何重写。

## 2. 阶段 A —— 守住底线（P0-3 + Quick Wins）

- **A1 修 ci_guard 三盲区**（Quick Win #1，最先做）
  - import 规则改为读文件**真实 package 行**匹配，废弃目录前缀猜测；
  - 扫描从仅 `/data/` 扩到全仓，新增方向规则（存量入 baseline ratchet）：`ui.*` 禁 import `data.local.db.dao.*` 与 `okhttp3`/`retrofit2`/`gson`；`service.*` 禁 import `ui.*`；`playback.*` 禁 import `ui.*`；保留 data→playback/ui/main 规则并修正 main 匹配（真实包名为根包）；
  - size baseline 升级「路径: 行数上限」格式，存量按当前实测行数 pin，收缩 baseline = 实际还债；
  - **规则自检**：每条内置规则必须命中至少一个反例样本（固定反例夹具目录，如 `tools/guard-selftest/`），防规则空转再次发生。
- **A2 目录≠包名 22 文件统一真实包名**（用户已确认口径）
  - albumdetail/ 12 + nowplaying/ 5 + main/ 5：只改 package 行 + 全仓 import 重写（机械改动，git mv 不需要——文件位置不动，只对齐包名与目录）；
  - 全量测试 + 实机 smoke；同步作废 ARCHITECTURE.md「目录≠包名约定」节；
  - 这是 A1 规则能按包名写对的前提，**先于 A1 的规则生效**（A1 编码时即按新包名）。
- **A3 Quick Wins**
  - `collectSubtitleCandidates` 3→1：先 diff 三份是否逐字相同（S7 教训：相似≠重复），不同先归因；钉测试先行；
  - PlaybackService runBlocking ×2 异步化：onCreate :277 改 serviceScope.launch + 起播门控；onDestroy :1375 改短超时 IO flush；
  - DownloadWorker `Result.retry()` + 退避、主 OkHttp 显式超时（**修复类行为变更**，单独提交 + 实机弱网验证）。
- **阶段 A 门禁**：全量测试（基线 880 只增不减）+ 子代理审查 + 报告 + tag `refactor-r2/phase-A`。

## 3. 阶段 B —— 破环与穿透（局部重写主战场，P0-1/P0-2）

- **B1 data→上层 5 条违规模型下沉**：`AppVolume`、`MediaItemRequest`、`MediaItemFactory` 依赖、`LocalTreeLeafCacheEntry`、`TreeFileType` 下沉 domain/data，`import-direction-baseline.txt` 清零。
- **B2 service→Activity 中立化**：PlaybackService:40 / SubtitleTaskService:22 去除 `import MainActivity`（launcher intent / 专用 alias / 调用方注入 PendingIntent，动手时按证据选型）。
- **B3 playback↔service 抽接口**：`PlaybackController` 接口由 service 实现，PlayerConnection 依赖接口。
- **B4 LibraryViewModel 数据访问层局部重写**：19 处 withTransaction + 16 处 DAO import 收进 Repository；**行为档案 + seam 测试先行**；这是 God VM（82 方法），编排职责不动，只重写数据访问层。
- **B5 AlbumDetailViewModel 数据访问层局部重写**：36 处 DAO import + 7 处事务收进 Repository；体量最大，预计拆 3-4 个子任务（按 asmr.one 树 / DLsite / 本地库三路数据分批）。
- **B6 ui→DAO import 禁令上线**：38 文件存量入 baseline；B4/B5 完成后收缩；其余小文件（≤5 处的）随改随还。
- **阶段 B 门禁**：同 A，另加实机走查（库页/详情页/下载页全链）。

## 4. 阶段 C —— 拆 God 与焕新（P0-4）

- **C1 MainContainer 2360 行路由闭包重写**：按路由族拆独立 NavGraph 文件（专辑详情/搜索/设置/下载/播放列表…），独立状态宿主；**先补路由级 seam 测试 + 实机走查脚本**（第一轮缓办项，本轮主菜）。
- **C2 NowPlayingScreen ~1600 行区块化拆分**。
- **C3 SettingsScreen ~1100 行区块化拆分**。
- **C4 两个 God VM 编排职责拆分**（B4/B5 数据层已重写，此处拆方法族：扫描/标签/下载/在线数据）。
- **C5 ratchet 收紧 1500→800**：渐进，每修复一批收缩一次。
- **C6 Konsist/ArchUnit 边界测试评估引入**（评估后定，不强上）。
- **C7 终态文档**：ARCHITECTURE.md 全面重写（新分层 + 行为清单归档；数字校准已于 2026-10-02 提前完成，此处指结构重写）+ README 同步 + 根文档三缺（LICENSE/CHANGELOG/CONTRIBUTING，需用户选型 LICENSE）。
- **阶段 C 门禁**：全量 + 子代理审查 + 实机全链 smoke + tag `refactor-r2/phase-C`。

## 5. 隐性行为文档化机制（新纪律）

- 位置：`docs/behavior-notes/`（入库），一 seam 一文件；索引进 ARCHITECTURE.md。
- 条目格式：行为描述 / 代码位置 / 触发条件 / 钉测试引用 / 发现于（任务号）。
- 触发时机：读代码取证时发现"只活在代码里"的编排细节即记，不等重写完成。

## 6. 风险与缓解

| 风险 | 缓解 |
|---|---|
| 包名统一波及面大（22 文件 + 全仓 import） | 机械改动 + 编译器引导 + 全量测试 + 实机 smoke |
| 重写 God VM 数据层引入行为回归 | 行为档案 + seam 测试先行 + 三路数据分批 + 实机走查 |
| MainContainer 路由重写破坏导航/深链 | 路由级测试 + start_route 实机直达走查每个路由族 |
| 守护规则再次空转 | 规则自检（反例夹具）进 CI |
| 修复类行为变更（retry/超时）引入新故障模式 | 单独提交、单独实机弱网验证、可独立回退 |
