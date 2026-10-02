# 阶段 2 审查报告（TDD 落地 + 结构收敛）

- 审查基准：`git diff refactor/phase-1..HEAD`（9 提交：0e30a40…71e12a2）
- 审查方：独立审查 agent（只读，逐提交 `git show` + 多重集行比对 + 全仓含跨模块 grep 独立核验，不信任提交信息）
- 全量测试：854 tests / 0 failures / 4 skipped（:app:testDebugUnitTest，fbb1efd 全量 12m48s 绿；71e12a2 仅动 baselineprofile 模块字符串，app 测试 UP-TO-DATE 复用）
- **总判定：通过**（首轮 P1 一项，修复闭环后 P0/P1 零发现；P2 提交信息口径微瑕带过）

## TDD 落地：通过

- S6 `sanitizeFolderName`：测试钉住 trim→空回退 "item"→非法字符替换全链，含执行顺序判别用例（`" / "`→`"_"` 而非 `"item"`），期望值来自行为规格非照抄实现；全仓唯一定义点，10 处调用点替换零逻辑改动
- S7 buildTagsToken/parseAlbumTags/isLikelyPlaceholderCover：6 个私有函数逐字一致外提，测试期望由 TagNormalizer 规格独立推导
- 红→绿证据：276f7da（2 个 MockWebServer 锁定测试）先于 03ee359（重构）独立提交；S6/S7 新 seam 红 = 父提交编译期 Unresolved reference（机械可推），属合理改编
- S9 纯移动属实：7920679 删除行/新增行多重集比对（545 vs 569 唯一行），差异仅 package/import 头 + 恰好 4 处 private→internal（hasDlsitePlayImageTransform、finalizeDlsiteLosslessArchiveIfNeeded、finalizeDlsiteLosslessArchiveInStorageIfNeeded、upsertDownloadedAlbumToLibrary），其余逐行匹配

## 行为保持：通过

- 39b376a 仅日志：`catch (_)` → `catch (e) { Log.w(...) }`，返回值与控制流不变
- 03ee359 请求形状不变：@GET 路径、@Query 默认值、HEADER、三 BASE_URL、selectedApi 分支映射逐字保留
- fbb1efd 等价：cancelWorkAndUpdateState 两条 runCatching 与原调用点逐条对应；条件状态更新保留在调用点；delete 流程 cancel 不带状态更新与原一致
- 71e12a2（门禁修复）：场景语义等价替换，详见下节

## 门禁发现与闭环（P1 一项）

**发现**：49d7505 零引用清单只 grep 了 app 模块，漏扫 baselineprofile 模块——`BenchmarkDriver.startAlbumDetailDlTabExample`（BenchmarkDriver.kt:91）仍引用已删路由 `album_detail_online/{rj}`，该函数被 BaselineProfileGenerator.kt:90 与 LongListPerformanceBenchmark.kt:205 实际调用，基准场景会导航到不存在路由（baseline profile 生成与长列表性能场景失真）。不破坏编译与单测，故 P1 非 P0。

**修复**（71e12a2）：startRoute 改为 `album_detail_rj/$rjCode?initialTab=dl`。语义链独立验证：toAlbumDetailInitialTab（MainNavigationSupport.kt:205）`"dl"→tab 1`（DL 页）；start_route extra 经 navigateSingleTop（MainContainer.kt:964）导航，query 参数匹配 AlbumDetailByRjPattern（AppNavigator.kt:17）。baselineprofile 两变体编译通过。

**闭环复核**：diff 仅一行无夹带；全仓（排除 .md）grep `album_detail_online` 零匹配；baselineprofile 其余路由字符串仅 `"library"`/`"search"` 均为现存合法路由。

**纪律沉淀**：门禁清单补「零引用清单必须跨模块 grep（含 baselineprofile/macrobenchmark 等全部模块，排除 docs）」。

## 测试完整性：通过

新增：SanitizeFolderNameTest 5 用例、AlbumMetadataSupportTest 新文件 7 用例、AsmrOneCrawlerEndpointRoutingTest +2 用例，共 +14 @Test 0 删除（MainNavigationSupportTest 仅删 1 断言换 1 输入）。840→854，4 skipped 恒定。

## 范围与 git 卫生：通过

一任务一提交、P0-x/P1-x 编号齐全；无密钥/生成物/本机路径入库；baseline-prof.txt/startup-prof.txt 随删码同步且删除行与被删符号对应；SearchSource/Asmr100/200/300Api 全仓零残留。

## P2（带过，无需回流）

提交信息口径微瑕：655cf88「853 tests」实为 852（devnote 已自更正）、da58a9e「8 用例」实为 7、fbb1efd「7 处」实为 8 处调用点收口。不影响代码与测试。

## 下阶段冷启动

- 阶段3（S10-S16）：S10 EaraWindowSize（测试先行 + 禁盲替）可立即开始；S11 前置（纯逻辑提取钉测试）与本报告无耦合
- 阶段3门禁 diff 基准：`git diff refactor/phase-2..HEAD`
