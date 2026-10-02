# R2 阶段 A 门禁报告（2026-10-01）

> 范围：`git diff refactor/phase-3..HEAD`（8 提交，HEAD = ff01d2e）
> 审查：独立只读子代理两轮（首审不通过 → 修复 → 闭环确认通过）

## 任务完成度

| 任务 | 提交 | 状态 |
|---|---|---|
| A2a main/ 5 文件包名对齐 | 47a2ad4 | ✅ kt diff 程序化核验 100% 机械行 |
| A2b nowplaying/ 5 文件 | 225347e | ✅ 同上；profile 零引用 |
| A2c albumdetail/ 12 文件 | d0c15f3 | ✅ 含 LyricsLoader/SubtitleTaskService 跨层 import 更新 |
| A1 ci_guard 重写 | 49a2fac | ✅ 9 规则 + 反例自检 + size pin；实测通过 |
| A3-1 collectSubtitleCandidates 收敛 | c146366 | ✅ 与三份原实现逐字等价（含 extOf quirk），钉测试 ×2 |
| A3-2/3 可靠性修复 | 9148295 + ff01d2e | ✅ 复审确认入库 |

## 首审发现与闭环

- **P0**：NetworkModule 超时改动未入暂存（git add 路径大小写笔误）→ 补提交 ff01d2e
- **P1**：A3-2 Edit 误删 applyPlaybackRuntimeSettings 同步调用 → 已恢复，死代码消除
- P2：超时回退默认值逐字段核对一致；onDestroy 吞异常为注释声明的设计，可接受

## 门禁权威数字

- 全量 `:app:testDebugUnitTest`：**882/0/4**（880 → 882，只增不减；+2 = CollectSubtitleCandidatesTest）
- `python tools/ci_guard.py`：通过（含规则自检；存量 74 条 import + 11 个 size pin 全部匹配）

## 总判定：通过 → tag `refactor-r2/phase-A` @ ff01d2e

## 遗留 / 下一阶段

- 修复类行为变更（重试/超时）待实机弱网验证（B 阶段实机走查一并做）
- B1-B6 按计划推进：data 模型下沉 → service 中立化 → PlaybackController 接口 → 两个 God VM 数据层局部重写 → ui→DAO 禁令渐进收缩
