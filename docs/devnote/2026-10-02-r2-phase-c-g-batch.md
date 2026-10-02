# R2 阶段 C 开工：批次 G 补闸门与校准（2026-10-02）

> 接 2026-10-02-r2-phase-b-finish.md。阶段 C 全程计划在 `.trae/documents/plan-r2-phase-c.md`（本机，gitignored），分 G → C1-C3 → C4-C5 → D 四批次。本篇记批次 G。

## G1+G2 守护收紧（6790d3f）
- **size ratchet 回收 805 行**：AlbumDetailViewModel 3213→2996、LibraryViewModel 3229→2641（readlines 口径实测，其余 9 条 pin 已精确贴合）。
- **松弛断言**：ci_guard 新增 `size_pin_failures()`——cap 超实测 +50（SIZE_SLACK_TOLERANCE）即失败提示收缩 baseline；还债后必须同步收缩 cap。`selftest_size()` 四场景自检（超限失败/松弛失败/容差内通过/贴线通过）。
- **4 条新 import 规则**（存量入 baseline ratchet，偿还路径见计划 C4 批次）：
  - `ui-to-data-remote`：160 处/27 文件（最大结构洞上闸）
  - `feature-to-feature`：67 处/20 文件；结构化白名单 `ui.common`/`ui.theme` 与同特征放行（`ui_feature()` 取包名第 5 段比对）
  - `data-to-feature`：2 处（AsmrOneAvailabilityApi→XxHash64、SettingsRepository→HotListeningSortMode）；禁令含 subtitle/translation/performance/benchmark（当前 0 处，预防性）
  - `playback-to-service`：0 处，预防性
- `ui-to-dao` 补禁 `AppDatabaseProvider`（包名 `com.asmr.player.data.local.db`，9 处入 baseline）——体检 P0-1 指出的守卫漏网。
- baseline 56→294 条（+238）；4 个新规则各配 guard-selftest 反例夹具。

## G3 CI 编译门禁（65b6612）
- ci.yml 增 `./gradlew :app:assembleDebugAndroidTest :baselineprofile:assemble`（体检 P1-6：防 androidTest/baselineprofile 资产腐烂）。

## G4 安全修复（34d6e03）
- `PlaybackService.onCustomCommand` 入口白名单：`controller.packageName != applicationContext.packageName` → `RESULT_ERROR_NOT_SUPPORTED`。4 条自定义命令（GET_AUDIO_SESSION_ID/UPDATE_SESSION_EQ/RELOAD_LYRICS/SET_VIDEO_OUTPUT_ENABLED）不再对第三方控制器开放。**行为变更标注**：第三方 App 发这些命令被拒，本应用内/通知控制器不受影响。实机 smoke 待设备连接。

## 验证
- ci_guard + guard-selftest 绿；全量测试 **906/0/4**（G4 改动后复跑确认）。
- 实机 smoke（起播/通知栏/悬浮歌词命令）**待设备连接补做**——adb devices 当前为空。

## 新增文件
- `.trae/documents/plan-r2-phase-c.md`（**本机**，gitignored）：阶段 C 全程实施计划（用户已批准），分批推进。
