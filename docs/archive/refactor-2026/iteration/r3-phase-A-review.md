# R3 阶段 A 门禁审查报告（防线与校准）

- **审查基准**：`git diff 4920f37..41974d1`（R3 阶段 A 共 6 个提交：253c396 计划与体检报告入库、66d3cff A1、3bb50aa A2、84e8bc8 A3、114eb98 A4、41974d1 A5）
- **依据计划**：[docs/refactor-plan-r3.md](../refactor-plan-r3.md)
- **审查方**：独立只读子代理（未参与实现），对每个 hunk 逐条核对；主 agent 复核并闭环 P1。
- **全量测试**：`:app:testDebugUnitTest` 本机实跑 **945 / 0 失败 / 4 跳过**（基线 938 + A4 新增 7；跳过为 2 个 opt-in 延迟基准 + 2 个门控用例）。
- **架构守护**：`python tools/ci_guard.py` 通过（含规则自检），当前 **17 条** import 方向规则、8 条 size pin。
- **总判定**：**有条件通过（无 P0）**；P1 已闭环。实机走查因设备未连接而挂起（见末节，阶段 A 无新增运行期行为）。

## 分节核验

### A1 ci_guard 守护扩面（66d3cff）——通过
- 旧 `ui-to-dao`（仅覆盖 `dao.`+`AppDatabaseProvider`）替换为 `ui-to-db`（覆盖整个 `com.asmr.player.data.local.db.*`，为超集）；新增 `ui-to-datastore` / `ui-to-cache-work` / `translation-to-ui` / `hotlistening-to-ui`。规则源/禁前缀经逐条核对正确。
- 每条新规则均配 `tools/guard-selftest/<rule>/` 反例夹具（含 main 包变体）；`ci_guard.py` 自检实跑 EXIT=0，无空转。
- `import-direction-baseline.txt` +148 条全部为实测存量真实违规（db 实体/查询 84、datastore 12、cache/work 50、反向倒挂 2），无凭空条目；夹具在 `src` 外不参与扫描。

### A2 文档回填（3bb50aa）——通过
- README/landing_zh 去除与实现相反的 m3u8 声明；ARCHITECTURE §2/§3 数字校准；§6/§7.2 规则数 9→17。

### A3 Room schema 导出（84e8bc8）——通过
- `AppDatabase` `exportSchema=true` 与 `build.gradle.kts` 的 `ksp { arg("room.schemaLocation", "$projectDir/schemas") }` 一致；`app/schemas/.../31.json`（version=31）合法入库；无 schema 资产依赖，未见副作用。

### A4 service 安全网（114eb98）——通过
- `nextLyricsTickDelayMs` 与被删的内联逻辑**逐字等价**（默认 2000ms、夹 [200ms, 播放 2000 / 暂停 1500]、`nextStartMs` when 分支）；7 条单测覆盖下一行/首行/末行/空表/上下限，断言无误。

### A5 重复实现收敛（41974d1）——通过
- `centerCropSquare` 三份（data/remote、work、ui/library）确认逐字相同，收敛为 `util/CoverSupport.kt` 单一实现（放中性 util：ui 禁 import data.remote）；两处调用点行为不变。
- `preferredHeaderTitle` 与两处被替换块等价（`isBlank || == "专辑" || equals(rjCode, ignoreCase)` 回退）。
- `AlbumDetailDirectorySupport.normalizeRelativePath` → `normalizeTreeRelativePath` 为**纯改名**；全仓旧名仅剩 `TrackKeyNormalizer.normalizeRelativePath`；新增行为档案 `docs/behavior-notes/path-normalizer-variants.md` 并索引进 ARCHITECTURE §7.3。
- 顺带收紧 size baseline（LibraryViewModel 2521→2500、DirectorySupport 2705→2700），已在提交信息申报，与实测一致。

## 发现与闭环

- **P0**：无。
- **P1-1（已闭环）**：ARCHITECTURE §6 测试基线仍 938 → 改 **945**；§3 `AlbumDetailViewModelSupport` 773 → **781**、家族合计 14 821 → **14 829**（A5 抽 `preferredHeaderTitle` 净增 8 行）。本次提交修复。
- **P2-1（备忘）**：A3 计划中"近 3 版本补迁移测试"未落地（近 3 段迁移测试本已存在，仅完成 schema 导出）；仓内无 `MigrationTestHelper`，迁移测试欠账延续到后续。
- **P2-2（备忘）**：`tutorial.md`（未入库、被 `.git/info/exclude` 排除的生成产物）未处理，留待重新生成（见 backlog）。
- **范围/测试卫生**：无超阶段范围改动，无向巨石文件追加新功能；仅删 1 个旧夹具（ui-to-dao），无既有测试删除，测试 938→945 只增不减。

## 实机走查（挂起）

- 执行 `adb devices` 返回空（无设备/模拟器连接），无法进行实机走查。
- 阶段 A 无新增运行期行为：改动面为守卫脚本、文档、构建配置（schema 导出）、纯函数提取（歌词节拍）、纯改名、重复实现收敛（逐字等价）——均可由单测与本机全量测试覆盖。
- 处置：如实记录挂起；待设备连接后可补走查（播放页悬浮歌词滚动、封面缩略图生成两条链路）。**不阻塞阶段 A 结项**（tag 兼作回退锚点）。

## 下阶段

进入阶段 B（断环 11 组 + ui 四类穿透收口 + baseline 收缩），按计划 §3 逐环/逐族推进。
