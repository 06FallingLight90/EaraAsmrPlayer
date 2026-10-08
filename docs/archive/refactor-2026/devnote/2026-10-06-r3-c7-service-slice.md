# 2026-10-06 — R3-C7 service 拆解（DownloadManager / SubtitleTaskService / PlaybackService）

## 任务

C7 service 层 God 拆解：`data/download/DownloadManager`（C7-1，1184→516+2）、`subtitle/SubtitleTaskService`（C7-2，1430→204+4）、`service/PlaybackService`（C7-3，1471→765+5）。C7 至此 3/3 收官。

## 新增非源码文件（本机 %TEMP%，不入库）

- `C:\Users\24131\AppData\Local\Temp\c7_dl_slice.py` — DownloadManager 切片（内容锚定行号 + 括号平衡自检）
- `C:\Users\24131\AppData\Local\Temp\c7_sub_analyze.py` — SubtitleTaskService 切片引用分析（成员/import/常量按区间扫描）
- `C:\Users\24131\AppData\Local\Temp\c7_sub_slice.py` — SubtitleTaskService 切片（成员→顶层扩展函数转换、companion 常量限定、private→internal 放宽、import 自动重推导）
- `C:\Users\24131\AppData\Local\Temp\c7_pb_slice.py` — PlaybackService 切片（同 c7_sub_slice 机制 + plain 顶层扩展（PlaybackException/MediaItem 接收者）去缩进 + dry-run 预览模式 + 主文件空行收敛）
- `C:\Users\24131\AppData\Local\Temp\c7-1-msg.txt` / `c7-2-msg.txt` / `c7-3-msg.txt` — 提交消息文件（PowerShell 不支持 heredoc，`git commit -F` 用）

## 踩坑与解法

1. **PowerShell 不支持 heredoc**：`git commit -m "$(cat <<'EOF'...)"` 直接语法错误——改写消息文件后 `git commit -F`。
2. **切片脚本断言先于切片跑**：锚点相对顺序断言写错（companion 与 reqclient 顺序颠倒）会误杀正确定位；主文件区间组装错误（切片 B 尾部多带）要靠 `git checkout -- <file>` 恢复后重跑——脚本写主文件前先打印行数可提前暴露。
3. **`lateinit var` 字段放宽正则**：`private (val|var)` 匹配不到 `private lateinit var`——需 `private (lateinit var|val|var)`；companion 常量放宽要限定 8 空格缩进，否则 4 空格缩进的 `private val networkCallback`（类字段）被误放宽。
4. **字符串模板内 `$CONST` 会漏 import 推导**：`"翻译中 $translating/$DEEPSEEK_TRANSLATION_CONCURRENCY"` 的符号在字符串感知剥离（strip）后不可见，推导出的 import 缺失——编译期 `Unresolved reference` 兜住后手工补。
5. **继承常量在扩展函数内需限定**：成员函数里的 `stopForeground(STOP_FOREGROUND_REMOVE)` 转为扩展函数后失去继承作用域，须改 `Service.STOP_FOREGROUND_REMOVE`。
6. **`max(` 计数为 0 的 import 是纯 stale**：DownloadManager 主文件 11 条历史 stale import（DlsiteAuthStore/NetworkHeaders/coroutines×5 等）按"符号在保留体出现与否"清除。
7. **companion 常量 + 顶层扩展函数**：搬移体内常量统一加 `SubtitleTaskService.` 限定（字符串感知替换，字面量零触碰），规避"扩展函数内 unqualified companion 成员是否解析"的语义不确定性，同时放宽常量 private→internal。
8. **编译缓存快照缺失警告**：`Failed to restore task outputs ... 0.zip` 为 Gradle 缓存恢复告警，自动降级全量重编，非代码问题。

## C7-3 增量（PlaybackService）

9. **属性初始化器调用的成员函数预防性留守**：监听器字段（audioFocusChangeListener/outputBroadcastReceiver/audioDeviceCallback）的初始化 lambda 与 spectrumOutputBufferSizeProvider 回调内调用的 5 个成员（4 handler + updateSpectrumVisualDelay）不转扩展——属性初始化器语境下无括号调用的接收者解析存在语义不确定性，留成员零风险（C7-2"留守函数"先例同型决策，非编译失败实测）。
10. **继承常量 AUDIO_SERVICE（坑 5 同型实测）**：`getSystemService(AUDIO_SERVICE)` 的 AUDIO_SERVICE 是 Context 继承常量，扩展体内失去继承作用域，需 `Context.AUDIO_SERVICE` 限定 + 补 import android.content.Context。
11. **编译器同型错误只报部分**：5 处 AUDIO_SERVICE 未限定只报了 4 处（CLIXML 错误流还有逐行截断换行）——修同型错误时按 grep 全量计数处理，勿按报错条数。
12. **PowerShell `Set-Content -Encoding UTF8` 带 BOM**（总纲 §5 已录，本次实测复现）：行内正则替换后落盘引入 EF BB BF——用 `[IO.File]::WriteAllText($p, $t, (New-Object System.Text.UTF8Encoding($false)))` 重写消除。
13. **区间删除后主文件残留连续空行**：函数区间被移除后其前后空行叠加成 2-6 连空——收尾按原文件风格（单空行）收敛 blank-run 到 1，纯空白变更不需重跑全量测试（括号平衡复查即可）。

## C8-0a 增量（AlbumDetailViewModelTest 直测 harness，首次建立）

14. **viewModelScope(Main.immediate) 在 Robolectric 的驱动方式（无 coroutines-test）**：Robolectric 测试线程即主线程——`vm.loadAlbum(...)` 内联执行协程至首个挂起点，之后的恢复经主 Looper 队列投递；以 `shadowOf(Looper.getMainLooper()).idle()` + `Thread.sleep(10)` 的泵循环 + 真实时间 5s 超时（`awaitUiState` 谓词等待）驱动至目标状态。**不能用 `runBlocking { loadAlbumAndAwait() }`**——runBlocking 阻塞主线程后，Main 队列永不泵送，join 永久死锁。runBlocking 只用于 DAO 种入（Room suspend insert）。
15. **@ApplicationContext 参数在手工装配时必须显式传**：Hilt 构造在测试外注入，直接 new 时 `DlsitePlayWorkClient(OkHttpClient())` 报 "No value passed for parameter 'context'"——所有带 @ApplicationContext 的构造（DlsitePlayWorkClient/DownloadManager/LyricsLoader/AppCacheManager/ManualLyricsSourceRepository）都要补 context 实参；`DlsiteProductInfoClient(OkHttpClient())` 则无 context 参数（单参）。
16. **Room DAO suspend insert 在种入 helper 里需 runBlocking 包裹**；`insertAlbum` 返回自增 id 供 track 的 albumId 使用。
17. **AlbumDetailUiState/AlbumDetailModel 定义在 `ui/library/albumdetail` 子包**（Support 文件），VM 在 `ui/library`——测试 import 两个包名都要写全。
18. **ImageCacheManager 可测构造**（CacheModule 同构）：MemoryCache(bytes)/DiskCache(dir,maxBytes,ttlMs)/CacheStats()/CacheConfig(cacheVersion="test")/ImageLoaderFacade(context,okHttp,Dispatchers.Default) 均为简单构造，无需 Coil 真实依赖。
19. **Truth 不在测试类路径**：断言一律 org.junit.Assert（项目先例统一）。

## C8-0b 增量（三路 ensure*Loaded 时序/幂等/去重钉测，+9 测）

20. **DlsitePlayWorkClient/DlsiteProductInfoClient 域名硬编码但接受 OkHttpClient 注入 → 拦截器重定向**：测试 OkHttp 加 application interceptor 把任意请求改写 scheme/host/port 指向 MockWebServer，全链路可控（请求序 editions → sign → ziptree）。DLSiteScraper 走 Jsoup 自建连接无注入口——Robolectric/离线下 404+`ignoreHttpErrors(true)` 走确定性失败兜底，不落 MockWebServer。
21. **DlsitePlayWorkClient 内部自建 `DlsiteAuthStore(context)`（Keystore 默认 cipher），与测试注入的存储不是同一密钥体系**：测试 cipher 写入的"密文"在客户端侧 decrypt 失败 → readCookie 静默清 pref → 视为未登录 → fetchPlayableTree 抛 IllegalStateException → loop 吞掉 lastError → 表现为"sign 请求根本没发出"（拦截器 println 探针落 XML system-out 定位）。cookie 注入必须走 **legacy 明文 pref 键**（`cookie_play`，镜像 private 常量 KEY_COOKIE_PLAY）：readCookie 迁移路径对"迁移加密失败"原样返回明文。且测试 cipher 的 **encrypt 必须抛错**阻止 VM 侧读时的迁移写入——否则写入的密文会被客户端 Keystore cipher 判损坏清空。
22. **VM 的 `resolveInitialDlsiteLoadTarget(model)` 包装（L901）在 baseRjCode 非空时经 productInfoClient 发 editions 预取请求**：MockWebServer 无入队响应时请求线程在 QueueDispatcher 阻塞（take() 挂起）→ ensure 永不完成 → awaitUiState 超时。凡触发该包装的测试须先 enqueue editions 响应（`{}` 即可：parse 找不到 productId 键 → 空列表）。
23. **ensureAsmrOneLoaded 未收录路径实际发两次 search**：preferInitial 直解 + directRjs 兜底循环——`resolveAsmrOneWork(throwOnRequestFailure=true)` 的缓存 TTL 检查带 `cached.second != null` 条件，null 缓存不消费 → 需 enqueue 两份空 works。
24. **selectDlsiteLanguage 尾部同时重发 ensureDlsiteLoaded + ensureAsmrOneLoaded**（两请求竞争 FIFO 入队响应）→ 入队多份无害响应（`{}` 对 editions → 空列表、对 search → 空 works，双向降级）规避到达顺序不确定性。
25. **loadAlbum 不自动触发三路 ensure\***（调用点仅 selectDlsiteLanguage 尾部 / refreshAsmrOneSection / invalidateAsmrOneEndpointState）→ C8-0b 的时序完全由测试编排控制，请求计数断言据此设计。

## C8-1 增量（首批低复杂度域 reducer 收编，+10 测）

26. **同包顶层 reducer 函数与 VM 成员同名会被 member-shadowing 吞掉**：plan 草案里的 `finishAsmrOneLoad(model,...)` 与 VM 私有成员 `finishAsmrOneLoad(keyRj,...)` 同名——类内调用解析到成员（import 被遮蔽），会变成无限递归陷阱；reducer 改名 `markAsmrOneLoadFinished`。凡后批 reducer 化，先 grep VM 成员名再定名。
27. **带消息副作用的赋值点（trial catch 分支）守卫不能全折进 reducer**：原语义是"workno 匹配 → 先 showError 再赋值"；若 workno 守卫只存在于 reducer（赋值时才判），mismatch 时消息已发出而赋值被跳过——副作用顺序发散。处理：token 判定 + workno 前置判定留在 VM 调用点（为消息门控），reducer 内守卫保留（冗余但受表驱动测试钉住）。
28. **`resetAsmrOneContent` 放弃 plan 草案的"形态二无条件版"**：invalidateAsmrOneEndpointState 的 keyRj 空白守卫挡的是赋值+重装载，refreshAsmrOneSection 的空白守卫挡的是 attemptedRj.remove/forget 副作用——空白判定留在调用点（副作用顺序保持原位），reducer 为纯无条件五字段重置；两处调用点共用同一 reducer。
29. **reducer 表驱动测试零 Robolectric**：AlbumDetailModel/Album/Track/AsmrOneTrackNodeResponse/DlsiteRecommendations 全为带默认值纯数据类，测试 harness 一个 `model(可覆盖字段…)` 工厂即可；断言沿用 org.junit.Assert（坑 19）。
30. **gradlew-local.bat 文件名无前导点**（坑复现）：AGENTS.md 的 `\.gradlew-local.bat` 写法有误导，实际是 `gradlew-local.bat`；后台 Shell 调用会被推后台且 exit 0 假象场景依旧，前台跑或后台+日志轮询均可。

## C8-2 增量（域 G×2 + 域 J 尾段 reducer 收编，+5 测）

31. **归一化语义逐字对齐要区分环节**：`finishDlsitePlayLoad` 原内联对 pickedWorkno 仅 `trim().orEmpty()` 不转大写——大写归一发生在候选推导处（editionWorknos `.uppercase()` / normalizeCandidates）。表驱动测试首轮误写期望 `"RJ456"`（ComparisonFailure），实际 `" rj456 "` → `"rj456"`。教训：copy 字段的归一化在哪个环节发生，测试期望就断言哪个环节的输出。
32. **reducer 文件引入 data.remote 类型触发 ui-to-data-remote 守卫**：域 G 大重置与域 J 树装载的 copy 字段涉及 `AsmrOneTrackNodeResponse`（dlsitePlayTree）与 `DlsiteRecommendations`，AlbumDetailReducers.kt 新增两条 ui→data-remote import——C8-1"无新增 import 方向"的前提只对域 A-I 的 `Track`（domain.model，白名单方向）成立。处理：与同包 AlbumDetailViewModelSupport.kt / AlbumDetailDialogs.kt 先例同型，两条入 tools/import-direction-baseline.txt（守卫先报再入 baseline 的既有流程）。
33. **`Measure-Object -Line` 不计空行**：size baseline 对齐口径是文件总行数，须用 `(Get-Content file).Count`；`Measure-Object -Line` 给出 2233（非空行），与真实 2402 差 ~170，直接拿去收缩 baseline 会误伤后续 ratchet。

## C8-3 增量（域 F 全域 reducer 收编，+10 测）

34. **target resolve 段的 `targetChanged` 双别名在参数化时收拢**：原内联 `val targetChanged = shouldReload...; val mustReloadAsmrOne = targetChanged` 两个 val 指同一值，reducer 化时合并为单参数 `mustReloadAsmrOne`；`keepAsmrOneContentDuringTargetSwitch` 推导留在调用点（因 token++/attemptedRj.clear 副作用以它为门控）。注意一处"看似不一致但是原状"：`displayAlbum` 重合并的 asmrOneWorkId 参数只感知 mustReload（为 null）不感知 keep——keep 时 workId 保留在 model 字段但合并走 null，表驱动测试按原语义钉死，勿"顺手统一"。
35. **import baseline 一次片内可双向变动**：域 F 把 VM 局部函数 `mergePreferNonBlank` 下沉到 Reducers.kt 后，VM 的 `DlsiteRecommendedWork` import 失效删除 → guard 的失效条目检测会报 baseline 死条目须同步移除；同时 Reducers.kt 新增同型条目（坑 32 先例）。净零变动，但两处都必须同片完成，漏掉任一侧 guard 即失败。
36. **`python tools/guard-selftest` 不是可执行入口**：guard-selftest/ 是反例夹具目录（无 `__main__`），规则自检已内置 ci_guard.py（运行输出"架构守护通过（含规则自检）"即覆盖）；AGENTS.md 该行已修正。夹具组按规则名组织（如 ui-to-data-remote/），改规则时在那里加反例。

## C8-4 增量（域 H 主体 reducer 收编，+4 测）

37. **闭包拆参数化的首读守卫等价改写**：原 `finishWithResolvedAsmrOneTree` 闭包首行 `val updated = ... ?: return true` 承担"非 Success 提前返回且不触发 token 副作用"语义，reducer 化后 `updated` 不再被 copy 使用——改写为 `if (_uiState.value !is AlbumDetailUiState.Success) return true`（同为提前 return true、跳过 token 判定，语义逐位等价，且消未使用变量警告）；直接赋值 `_uiState.value = ...copy(...)` 换 `updateSuccessModel { reducer }` 后，二次读状态与原快照无挂起点间隔，等价。
38. **失效条目检测只覆盖"曾在 baseline 的键"**：域 H 拆闭包使 VM 的 `mergeAsmrOneHeaderAlbum` import 失效删除，但它是 ui→ui 同 feature 方向本就不在 baseline——无需清理也不会报死条目；而 Reducers.kt 新增 `WorkDetailsResponse` import 则必须 +1 条 baseline（ui-to-data-remote 同型第三条，坑 32/35 同族）。即：**跨 feature 方向的增删都过 baseline，同 feature 方向的增删完全不经 baseline**，判断依据是方向而非文件新旧。

## C8-5 增量（域 D 主体 + K 整态替换收编，+5 测）

39. **域 D/K 的赋值点不是 Success.copy 而是整态替换**：loadAlbum 初始种入/主装载是构造新 model 整体换入 Success，Error/Removed 是换态——Model 级 `(Model, …) -> Model?` 形态不适用，收编切到 **UiState 级 `(AlbumDetailUiState, …) -> AlbumDetailUiState?`**；`createInitialAlbumDetailModel` 随之下沉 Reducers.kt 顶层（原 VM private，逐字随迁）。C8-1..4 的"全部 30 处赋值点收编"至此完成，但 **manualSetRjAndSync 的 `_uiState.value = Loading` 是同类整态换态而不在 plan 30 点清单**——按范围纪律未动，留作 sub-state/LoadPhase 重写时的同类收编对象。
40. **整态替换 reducer 吸收局部合并逻辑后，调用点残留 val 必须同步删**：`applyAlbumDetailLoaded` 吸收 withPreservedListenTogetherListenerCount 合并与相等守卫后，VM 调用点的 currentModel/loadedModel 两个局部 val 变死代码——未使用变量仅是 warning 不挡构建，容易漏删；切换赋值点后应立即重读调用点上下文而非只看编译结果。
