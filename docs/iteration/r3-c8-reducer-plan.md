# R3-C8 reducer 重写实施方案（2026-10-06 取证定稿，待用户确认）

> 依据：refactor-plan-r3.md §4 C8（纯 reducer `(State,Event)->State`；步骤"新旧并存 → 暗影比对 → 逐域切换 → 删旧路径"；风险失控回退 C2 纯抽取）。
> 安全网现状：AlbumDetailViewModelTest 15 测（C8-0a/0b），全量 960/0/4。
> VM 现状：2508 行（size pin 2510），`_uiState` 赋值 30 处、`as? Success` 守卫读约 46 处。

## 1. _uiState 赋值点分域清单（取证结论）

| 域 | 赋值行（AlbumDetailViewModel.kt） | 内容 | 复杂度 |
|---|---|---|---|
| A listenTogether | L283 | listenerCount 更新；3 重守卫（in-flight/rj 匹配/count 相同） | 低 |
| B asmrOne 端点失效 | L304 | invalidateAsmrOneEndpointState：asmrOne 四字段重置 | 低 |
| C cancel 族 | L517 / L537 | resetLoadingState 四 isLoading=false；invalidateDlsitePlayAccess 四字段重置（4 条件守卫） | 低 |
| D loadAlbum | L662 / L703 / L713 / L739 | 初始种入 / 主装载 / Error / observeLocalTracks collect 更新 | 高（token+job） |
| E 封面 | L882 | updateCurrentCoverState（底层 withUpdatedLocalCover 已是纯函数） | 低 |
| F ensureDlsiteLoaded | L939 / L997 / L1003 / L1015 / L1084 / L1106 / L1112 | 7 处：开载置位 / target resolve 推进 / workno 空早退×2 / 抓取合并 / enrich job / catch 收口 | 高 |
| G selectDlsiteLanguage | L1138 / L1186 | 18 字段大重置 / 尾部 local 重装载 | 中 |
| H asmrOne | L1209 / L1229 / L1298 / L1349 / L1471 | refreshAsmrOneSection 重置 / finishAsmrOneLoad / 开载置位 / finishWithResolvedAsmrOneTree（闭包）/ originalDetails 合并 | 高（闭包+多终态） |
| I trial | L1251 / L1257 / L1270 | refreshDlsiteTrialSection 三处（workno 匹配守卫在 job 内两处） | 低 |
| J dlsitePlay | L1548 / L1616 / L1629 | 开载置位 / 成功树装载 / catch 收口（attemptKey 判定留在 VM） | 中 |
| K Removed | L1736 | notifyLocalAlbumRemoved 终态 | 低 |

行为保护（已录决策，涉及域内引用时勿动）：
- `applyResolvedCloudSync` title 覆盖语义（refactor-plan-r3.md L143）——本 VM 现无此函数（C1d 已迁 LibraryCloudSyncStateHolder），C8 域内无涉及。
- `ensureAlbumCoverSaved` 双实现只记录不改动（ARCHITECTURE.md L161）——本 VM 无此函数，C8 域内无涉及。

## 2. C8-1 首批方案（低复杂度域收编）

**范围**：域 A、B、C×2、E、I×3、H 的 finishAsmrOneLoad、D 的 observeLocalTracks collect（约 11 处赋值点收编）。

**新文件**：`ui/library/albumdetail/AlbumDetailReducers.kt`（与 AlbumDetailModel 同包，无新增 import 方向，不触守护）。纯函数，无 VM/Android 依赖：

```kotlin
// 形态一：守卫早退语义（null = 不赋值，调用点 return/跳过）
internal fun updateListenTogetherListenerCount(
    model: AlbumDetailModel, rjCode: String, listenerCount: Int
): AlbumDetailModel?
internal fun resetDlsitePlayAccess(model: AlbumDetailModel): AlbumDetailModel?
internal fun finishAsmrOneLoad(model: AlbumDetailModel, keyRj: String, resolved: Boolean): AlbumDetailModel?
internal fun updateLocalTracks(model: AlbumDetailModel, localId: Long, tracks: List<Track>): AlbumDetailModel?

// 形态二：无条件重置（原 as? Success ?: return 守卫由 VM 应用器承担）
internal fun resetAsmrOneEndpointState(model: AlbumDetailModel): AlbumDetailModel
internal fun resetOnlineLoadingFlags(model: AlbumDetailModel): AlbumDetailModel
internal fun setDlsiteTrialLoading(model: AlbumDetailModel, workno: String): AlbumDetailModel  // workno 匹配守卫→返回 null 同形态一
internal fun finishDlsiteTrialLoad(...)  // 成功/失败两变体或参数化
```

**VM 侧统一应用器**（private，消 30 处 `as? Success ?: return` + `Success(model=...)` 样板）：

```kotlin
private fun updateSuccessModel(transform: (AlbumDetailModel) -> AlbumDetailModel?): Boolean {
    val current = _uiState.value as? AlbumDetailUiState.Success ?: return false
    val next = transform(current.model) ?: return false
    _uiState.value = AlbumDetailUiState.Success(model = next)
    return true
}
```

语义纪律：reducer 体内 copy 字段与守卫条件**逐字对齐**原内联逻辑（含注释随迁）；VM 调用点只做"取 model → 调 reducer → 赋值"三步。

## 3. 后续批次（每片一提交，全量测试三绿）

| 批次 | 范围 | 要点 |
|---|---|---|
| C8-2 | 域 G×2 + 域 J 尾段 | 18 字段大重置 reducer 化；dlsitePlay 成功/失败收编（attemptKey 判定留 VM） |
| C8-3 | 域 F 全域 | ensureDlsiteLoaded 7 处；mergePreferNonBlank 已可先下沉纯函数；target resolve 状态推进 reducer 化 |
| C8-4 | 域 H 主体 | finishWithResolvedAsmrOneTree 闭包拆参数化 reducer；多终态（token 失配/未收录/超时）逐字对齐 |
| C8-5 | 域 D 主体 + K | loadAlbum 主装载/初始种入/Error/Removed |
| 单独决策 | 分区 sub-state 取代 AlbumDetailModel 27 字段 + LoadPhase 取代 4 组 token+job | 最高风险（ensure 取消/防抖语义），C8-1..5 完成后评估；风险失控按计划回退 C2 纯抽取 |

## 4. 暗影比对的务实形态

不做机械双跑（需事件总线，成本远超收益）。承载方式：
- **旧路径锚**：AlbumDetailViewModelTest 15 测钉住的行为（装载/幂等/去重/终态/守卫）在每片切换后必须保持全绿——等价于"同一输入序列下旧路径终态不变"。
- **新路径锚**：`AlbumDetailReducersTest` 纯 JVM 表驱动测试（无需 Robolectric），输入为 model+事件参数，断言输出 model 逐字段等于原内联 copy 语义的期望值。
- 每片提交跑全量 `:app:testDebugUnitTest`（960 基线只增不减）+ ci_guard；域切换后该域旧内联路径删除（"逐域切换 → 删旧路径"在 reducer 批次内自然完成）。

## 5. 验证

- C8-1：新增 AlbumDetailReducersTest（表驱动，覆盖每 reducer 的守卫早退与变换分支）+ 全量三绿 + guard 自检。
- size：VM 行数下降（预计 -80~-120），baseline 2510 相应收缩；reducer 新文件 <1500 无需 pin。
- 实机走查：C8 全域完成后随阶段门禁一并执行（B+C 两阶段遗留一起）。
