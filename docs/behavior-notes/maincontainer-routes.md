# MainContainer 路由行为档案

> 录入：2026-10-02，R2-C1（MainContainer 路由族拆分前盘点）。
> 修订：2026-10-10，T13 阶段二（BottomChrome 八页签 → 五页签，绞杀者模式：仅摘入口，旧代码/旧路由保留，删除留待阶段三 DEL）。
> 钉测试：`app/src/test/java/com/asmr/player/MainContainerRouteTest.kt`、`MainNavigationSupportTest.kt`、`MainContainerSaveableStateTest.kt`、`main/BottomChromeTest.kt`。
> 权威源码：`app/src/main/java/com/asmr/player/main/MainNavGraph.kt`（C1b 拆出后）；start_route 分发逻辑在 `MainContainerRuntime.kt`（MainStartupEffects）+ `MainContainer.kt`。

## 冷启动直达路由（start_route）机制

- `MainActivity.onCreate` 读 `intent.getStringExtra("start_route")` 原样透传给 `MainContainer(startRouteFromIntent=...)`，**无白名单校验**（未知路由会在 NavHost navigate 时抛 IllegalArgumentException，行为维持原状）。
- 初始目的地：**恒为 `library`**（T13 修订：原「startRoute == search 时 startDestination 为 search」特判随 search 摘除页签一并删除——search 已非 primary，深链改走二级通道）。
- 分发（`MainStartupEffects`，`LaunchedEffect(navController, startRoute, initialDestination)`）：`startRoute` 为空或等于 initialDestination 时不导航；否则 `isPrimaryRoute(startRoute)` 为真走 `navigatePrimaryRoute`（launchSingleTop + restoreState=false + popUpTo(library) inclusive=false saveState=false），否则走 `navigateSingleTop`（launchSingleTop + restoreState=true）。
- **T13 旧路由深链语义**：`search` / `hot_listening` / `playlist_system/favorites` / `listening_calendar`（及其二级 `search_assist*`）不再是 primary → start_route 深链走 `navigateSingleTop` 二级通道：
  - 路由注册全部保留，导航不崩；但 `search` 的 NavHost 内容是透明回传桥（见下）、`hot_listening` / `listening_calendar` / `playlist_system/{type}=favorites` 的 NavHost 内容是透明占位 Box → 旧页签 UI 不再渲染（原页面内容在 Pager 中，已不进入 pagerRoutes）。
  - 视觉表现 = 停留 lastPrimary 页（默认 library）+ 顶栏显示旧路由标题与返回键 + 底部 chrome 高亮 lastPrimary；返回即回到该页。
  - 页面内旧入口同理降级：如 library 页搜索框（`submitMetaSearchKeyword` → `openPrimaryRoute("search")`，targetPage=-1 → 直接 `navigatePrimaryRoute`）呈现同一降级态。属阶段二已知表现，阶段三删路由时一并清理。
- 走查命令：`adb shell am start --es start_route "<route>"`（运行中实例不生效，需先 force-stop）。

## 主页面（primary）路由族（T13 阶段二：五页签）

主页面由 HorizontalPager 渲染（非 NavHost composable 内容，NavHost 内仅注册占位 Box 以承载返回栈），来源 `bottomChromeNavItems()`（`main/BottomChrome.kt`），**五页签按 US-06 顺序**：

| route | 占位 | 页签 | 说明 |
|---|---|---|---|
| `library` | Box | 库 | 本地目录浏览，Pager 第 0 页；顶栏入口 `allsongs`（二级）归属库 |
| `playlists` | Box | 歌单 | 原我的列表 |
| `groups` | Box | 合集 | 原我的分组 |
| `purchased` | Box（T13 新升） | 已购 | 原**二级**路由升 primary；内容装配随迁至 Pager（`MainPrimaryPagerUi.Routes.Purchased` 分支，复用 PurchasedScreen/PurchasedViewModel 未改内部逻辑）；含登录入口（→dlsite_login）与下载管理入口（→downloads），均走既有二级通道 |
| `settings` | Box | 设置 | |

保留注册但**不再进入 pagerRoutes** 的旧 primary 占位：`search`（内容为 savedStateHandle 回传桥，非透明占位）、`hot_listening`、`listening_calendar`、`playlist_system/{type}`（type=favorites 时占位 Box）。对应 Screen 装配分支仍留在 `MainPrimaryPagerUi` 的 when 中（死分支，阶段三统一删除）。

`isPrimaryRoute` 集合 = 上述 5 条。chrome 侧 `BottomNavPageToggleGroupSize == BottomNavExpandedSlotCount == 5` → 单组平铺，**分组切换（overflow toggle）不再出现**。

- Pager saveable：每页 key 仍为 `primary_route:<route>`；purchased 新增 `primary_route:purchased`；旧页签 key 无消费者（无害）。PurchasedScreen 的 `LaunchedEffect(Unit){bootstrap}` 语义由「每次导航进入」变为「页面组合时」（beyondViewportPageCount=1 内存活期间不重跑），登录页返回不触发自动重载——阶段二接受的差异。
- 已购页签双击滚动到顶：`ScrollToTopSignals.trigger("purchased")` 无 case（PurchasedScreen 无该端口），为 no-op，属预期。

## 二级（secondary）路由族

| route pattern | 装配 | 参数解析（Graph 层负责） | 钉测试 |
|---|---|---|---|
| `library_filter` | LibraryFilterScreen（SecondaryPageBackground 包裹，viewModel=libraryViewModel） | 无 | 待补 |
| `allsongs` | AllSongsScreen（T5/批次 A 新增；库页顶栏入口） | 无 | 待补 |
| `search_assist` | SearchAssistScreen | keyword 默认 "" | 待补 |
| `search_assist?keyword={keyword}` | SearchAssistScreen | `Uri.decode(keyword)`，空则回退 searchAssistInitialRequest | 待补 |
| `album_detail_rj/{rj}?initialTab={initialTab}` | AlbumDetailScreen（AlbumDetailRouteFrame 包裹） | rj 原样（AppNavigator 已 URLEncoder）；initialTab 经 `toAlbumDetailInitialTab`：local→0 / dl→1 / dlsitePlay→2 / 其它→null | MainContainerRouteTest |
| `album_detail/{albumId}?rjCode={rjCode}&initialTab={initialTab}` | AlbumDetailScreen（同上） | albumId LongType；rjCode nullable；initialTab 同上 | MainContainerRouteTest |
| `group/{groupId}/{groupName}` | AlbumGroupDetailScreen（SecondaryPageBackground） | groupId LongType 默认 0；groupName 经 `decodeRouteArg`（URLDecoder，失败原样） | MainContainerRouteTest |
| `playlist/{playlistId}/{playlistName}` | PlaylistDetailScreen（SecondaryPageBackground） | 同上 | MainContainerRouteTest |
| `playlist_system/{type}` | type=="favorites" 占位 Box；否则 SystemPlaylistScreen（SecondaryPageBackground） | type StringType | MainContainerRouteTest |
| `downloads` | DownloadsScreen（SecondaryPageBackground） | 无 | MainContainerRouteTest |
| `dlsite_login` | DlsiteLoginScreen（SecondaryPageBackground） | 无 | MainContainerRouteTest |

T13 修订：`purchased` 自本表移除（升 primary）。

## search_assist → search 结果回传桥（隐性行为，勿顺手清理）

`search` 路由的 NavHost 内容（production 在 MainContainer contents.search）：
1. 从 `backStackEntry.savedStateHandle` 读 11 个 `SEARCH_ASSIST_RESULT_*` 键（keyword/order/purchased/presale/chineseTranslated/collected/hasSubtitle/allAges/collectedSort/locale/signal）；
2. `submittedSignal > 0` 时把 11 值写入 MainContainer 的 `submittedSearch*` 状态（原驱动 Pager 中 SearchScreen 刷新；T13 后 SearchScreen 不再进 pagerRoutes，该状态仍被收集/保存，桥保持原样）；
3. 随后把 savedStateHandle 中 11 键全部复位（signal 归 0、其余回默认值），防止重复消费。

`SearchAssistScreen.onSubmitSearch`（MainContainer `submitSearchAssistRequest`）负责把结果写入 search 条目的 savedStateHandle 并 popBackStack。

## 转场与边界（与路由绑定的表现行为）

- NavHost enter：`isAlbumDetailStackTransition`（album_detail→album_detail 压栈）或目标为二级页（`usesSecondaryPageSlideTransition`，即 `resolveCurrentPrimaryDestinationRoute == null`，**T13 起不再接收 playlistSystemType 参数**）时 `secondaryPageEnterTransition()`，否则 None；exit/popEnter 恒 None；popExit 对称于 enter。
- 二级页有专用背景 `SecondaryPageBackground(topPadding = secondaryPageTopPadding)`；topPadding 由 Scaffold padding 同步（MainContainer 内 SideEffect）。
- `resolvePrimaryRoute`（T13 起签名无 playlistSystemType）把二级路由归属到 primary 祖先：`allsongs`/`library_filter`→library、`group/*`→groups、`playlist/*`→playlists、`album_detail*`→lastPrimaryRoute；旧页签路由（search/hot_listening/favorites/listening_calendar 及 search_assist*）**不再有归属分支**，落 else → lastPrimaryRoute ?: library，驱动 chrome 高亮与 Pager 视觉路由。`resolveCurrentPrimaryDestinationRoute` 同步为五页签集合（purchased 入集，favorites pattern+type 特判删除）。

## 跨屏回调通道（C1b 拆分时保持不变的契约）

- 跨屏回调经 MainContainer 闭包/参数传递：openNowPlaying、requestMiniPlayerPlayFeedback、submitMetaSearchKeyword、submitSearchAssistRequest、manualRj 对话框状态、albumBatchPlaylistPickerRequest、downloadsScrollToTopSignal 等。
- 详情页导航统一走 `AppNavigator`（popUpTo 语义见 `resolveAlbumDetailPopUpToRoute`：从 search/search_assist 系进入时 popUpTo search，从 library popUpTo library，热门收听 popUpTo hot_listening；stacked 变体不 popUpTo）。T13 后 search/hot_listening 入口不可达，popUpTo 目标路由仍在注册表中，语义不变。
- 抽屉（MainDrawerContent，`gesturesEnabled=false` 常闭）仍列旧页签项；其 `onOpenPrimaryRoute` 对不在 pagerRoutes 的路由走 `openPrimaryRoute` else 分支直连 `navigatePrimaryRoute`，呈现同「旧路由深链」降级态，不崩。
