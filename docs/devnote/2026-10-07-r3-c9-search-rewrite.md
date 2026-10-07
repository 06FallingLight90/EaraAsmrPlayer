# R3-C9 搜索编排重写收官（2026-10-07）

> 计划定义：docs/refactor-plan-r3.md §4 C9。C9 取代 C4 中 SearchScreen 的"区块化"表述，承接 B5 的 SearchViewModel 穿透收口。
> 提交链：5c80908（C9-1）→ 128336a（C9-2）→ 99956ce（C9-3）→ a16bae3（C9-4a）→ 1fdb97e（C9-4b）。
> 测试基线：994→**1010/0/4**（C9-2 +16 seam 测）；SCC ratchet 41→**40**；SearchScreen 2186→**1517**。

## 1. 切片路线与机制

原计划 C9 是"完整重写"，实际按风险拆为**策略层 → 测试 → 状态收敛 → UI 区块化**四片，行为逐字随迁为主，没有发生计划里担心的"重写致行为回归"——seam 测试先行把四分支语义钉死后，后续每片都在保护网内。

| 片 | 提交 | 内容 |
|---|---|---|
| C9-1 | `5c80908` | fetchPage 四分支（purchased/collected/直RJ/默认）逐行搬入 ui/search/SearchQueryStrategy.kt 的 executeSearchQuery；**SearchQueryPort 窄接口**（VM 侧唯一依赖面，为 seam 测试而立）；VM 留薄委托 + SearchRepository 适配器；blockedKeywordsProvider 保持已购分支**不读屏蔽词的惰性语义**（行为档案级约束，表驱动测试钉住）；私有 SearchPageResult 移为策略文件 internal |
| C9-2 | `128336a` | fake SearchQueryPort 表驱动 **16 测**（SearchQueryStrategyTest）钉住四分支：purchased 命中+屏蔽词惰性不读 / collected 参数+offset+canGoNext+空结果直RJ兜底 / 直RJ 命中+locale 透传+五类拦截（预售/屏蔽词/工作筛选/翻页/非workNo）+miss 回退 / 默认分支 order+筛选透传+locale 解析链 |
| C9-3 | `99956ce` | 9 个 mutable var（order/collectedSort/四互斥过滤/hasSubtitle/allAges/locale）→ **单一不可变 SearchRequestState**（入 SearchQueryStrategy.kt，normalized() 与原 normalizeSearchFilters 逐字等价）；bootstrap/search/updateSearchOptions/requestPage 回滚/applyCachedState 全写点原子化；fetchPage 收窄为 keyword/page/state 三参；**15 var 净减至 7**；SCC ratchet 41→40 随实测收紧 |
| C9-4a | `a16bae3` | uiState 渲染 when 整体抽出同包 SearchResultsContent.kt：Loading/空态/Error/else 薄分支 + Success 拆 SearchResultListContent/SearchResultGridContent 两个 private 子 composable，行渲染与骨架判定逐字随迁；SearchResultPlacementSpring/searchResultItemKey/骨架模式族随迁；SearchPageHorizontalPadding 升 internal 供双文件共享；SearchScreen 2179→1924 |
| C9-4b | `1fdb97e` | pull 手势状态机收敛同包 SearchPullGesture.kt：SearchPullGestureState holder（持久 5 var + latest 快照 + 派生 getter）+ rememberSearchPullGestureState 工厂 + dragModifier（pointerInput/nestedScroll/clipToBounds 手势体逐字）+ 两 hint 浮层；常量族与 searchRubberBandOffset/SearchPullActionHint 随迁；SearchScreen 1924→**1517** |

## 2. C9-4 切片设计与等价性论证（留档备查）

**范围决策：SearchChrome 调用点（~73 行）与 metaAction 对话框宿主（26 行）不再抽出。** SearchChrome 本就是独立 composable，调用点只是接线（包装仅参数搬家，净收益 ~35 行）；且 onOptionsChanged 回写 9 个 rememberSaveable 变量耦合深，包装收益低于风险。C9-4 范围即渲染 + 手势两刀，剩余 SearchScreenContent = 状态声明 + 行为函数 + 胶水（9 个 rememberSaveable 变量、搜索词/分页/滚动联动），不再有可低风险整迁的区块。

**手势 holder 等价性**（1fdb97e 核心论证，实机走查时重点对照）：
- holder 以 `remember(resultScrollKey, viewMode)` 键控重建——与原"翻页/tab 切换时局部 var 归零"生命周期一致；
- `rememberPullToRefreshState()` 留在键控 remember **之外**——保持原"跨键存活"的 refresh 状态语义；
- latest 快照 = plain var 每次重组由工厂参数回填，等价于原 rememberUpdatedState("始终读最新值、不重启 effect")；
- refresh 两个业务 effect（引 viewModel/uiState/latestKeyword）留主文件；三个纯手势 effect（enabled 复位 / scroll lock 上报 / awaitCancellation finally）迁入 remember 工厂；
- pullRefreshStartedAtMs 留主文件（原本就是无键 remember）；
- SearchPullRefreshMinFeedbackMillis 唯一升 internal（主文件刷新 effect 引用），其余常量 private 随迁。

## 3. 踩坑（C9 增量）

1. **混合行尾致精确匹配失败（B刀）**：文件主体 CRLF 但存在孤立 LF（Edit 工具写入所致，1923 CRLF + 1 LF），`split('\r\n')` 会让一行尾含 `\n` 导致锚点精确匹配失败 → 切分用 `re.split(r'\r\n|\n')`，写回统一 CRLF。行号手术脚本标配。
2. **锚点不唯一（B刀）**：`                        Box(` 在 L911/L1030 两处同文本 → 取 `min(i for i in ... if i > m6_start)` 选后一个；手术脚本的锚点断言应先报"多匹配"而不是静默取首个。
3. **`@file:OptIn` 需要显式 import（C4 坑 17 复现）**：SearchPullGesture.kt 缺 import 时满屏 ExperimentalMaterial3Api 报错定位在第 1 行注解处——file 注解先于 package 解析，不从 wildcard import 获益。
4. **Python 断言 0-based/1-based 混淆（A刀）**：`lines[199]` 期望 scrollKey 实为 itemKey——切片脚本断言行号统一按 0-based 核对，锚点内容校验兜住。
5. **编译三错（A刀）**：①IntOffset import 应为 `androidx.compose.ui.unit.IntOffset`（写成 ui.graphics）；②缺 `foundation.layout.padding`；③SearchPageHorizontalPadding 主文件需恢复 `internal val`（新文件放 private 副本同名冲突；Chrome/Toolbar 仍引用主文件声明）。
6. **PowerShell 无 heredoc（再次踩）**：`<<'PYEOF'` 不支持——行号手术脚本必须写临时 .py 文件执行（C7 已录，本次复发）。
7. **grep 子串误报**：删除 import 后查残留引用会命中子串（`LazyListState@384`=rememberSaveablePrefetchedLazyListState、`Offset@625`=firstVisibleItemScrollOffset、listStretchOffsetPx）——逐行人工核对后再定死码。

## 4. 遗留与后续

- SearchViewModel.kt（49KB/~1300 行）本轮未动行数——15 var 收敛到 7 已消解"四分支交织"的维护痛点；进一步拆分（如 Screen 侧 rememberSaveable 族下沉）无计划需求，留待后续真实痛点驱动。
- 实机走查搜索四态（列表/网格/已购/已收录）归阶段门禁，需用户配合切前台到播放器。
