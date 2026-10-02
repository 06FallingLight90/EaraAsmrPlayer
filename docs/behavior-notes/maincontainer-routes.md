# MainContainer 路由行为档案

> 录入：2026-10-02，R2-C1（MainContainer 路由族拆分前盘点）。
> 钉测试：`app/src/test/java/com/asmr/player/MainContainerRouteTest.kt`。
> 权威源码：`app/src/main/java/com/asmr/player/main/MainNavGraph.kt`（C1b 拆出后）；start_route 分发逻辑在 `MainContainer.kt`。

## 冷启动直达路由（start_route）机制

- `MainActivity.onCreate` 读 `intent.getStringExtra("start_route")` 原样透传给 `MainContainer(startRouteFromIntent=...)`，**无白名单校验**（未知路由会在 NavHost navigate 时抛 IllegalArgumentException，行为维持原状）。
- 初始目的地：`startRoute == "search"` 时 NavHost startDestination 为 `search`，否则恒为 `library`（MainContainer `initialDestination`）。
- 分发（`LaunchedEffect(navController, startRoute, initialDestination)`）：`startRoute` 为空或等于 initialDestination 时不导航；否则 `isPrimaryRoute(startRoute)` 为真走 `navigatePrimaryRoute`（launchSingleTop + restoreState=false + popUpTo(library) inclusive=false saveState=false），否则走 `navigateSingleTop`（launchSingleTop + restoreState=true）。
- 走查命令：`adb shell am start --es start_route "<route>"`（运行中实例不生效，需先 force-stop）。

## 主页面（primary）路由族

主页面由 HorizontalPager 渲染（非 NavHost composable 内容，NavHost 内仅注册占位 Box 以承载返回栈），来源 `bottomChromeNavItems()`（`ui/nav/BottomChrome.kt`）：

| route | 占位 | 说明 |
|---|---|---|
| `library` | Box | 本地库，Pager 第 0 页 |
| `search` | Box + savedStateHandle 桥 | 在线搜索；NavHost 内实现 search_assist → search 的筛选结果回传桥（见下） |
| `hot_listening` | Box | 热门收听 |
| `playlist_system/favorites` | 经 `playlist_system/{type}` 路由，type=favorites 时占位 Box | 我的收藏 |
| `playlists` | Box | 我的列表 |
| `groups` | Box | 我的分组 |
| `listening_calendar` | Box | ASMR 看板 |
| `settings` | Box | 设置 |

`isPrimaryRoute` 集合 = 上述 8 条（`playlist_system/favorites` 是完整 route 字符串，但 NavHost 注册的是 pattern `playlist_system/{type}`）。

## 二级（secondary）路由族

| route pattern | 装配 | 参数解析（Graph 层负责） | 钉测试 |
|---|---|---|---|
| `library_filter` | LibraryFilterScreen（SecondaryPageBackground 包裹，viewModel=libraryViewModel） | 无 | 待补 |
| `search_assist` | SearchAssistScreen | keyword 默认 "" | 待补 |
| `search_assist?keyword={keyword}` | SearchAssistScreen | `Uri.decode(keyword)`，空则回退 searchAssistInitialRequest | 待补 |
| `album_detail_rj/{rj}?initialTab={initialTab}` | AlbumDetailScreen（AlbumDetailRouteFrame 包裹） | rj 原样（AppNavigator 已 URLEncoder）；initialTab 经 `toAlbumDetailInitialTab`：local→0 / dl→1 / dlsitePlay→2 / 其它→null | 待补 |
| `album_detail/{albumId}?rjCode={rjCode}&initialTab={initialTab}` | AlbumDetailScreen（同上） | albumId LongType；rjCode nullable；initialTab 同上 | 待补 |
| `group/{groupId}/{groupName}` | AlbumGroupDetailScreen（SecondaryPageBackground） | groupId LongType 默认 0；groupName 经 `decodeRouteArg`（URLDecoder，失败原样） | 待补 |
| `playlist/{playlistId}/{playlistName}` | PlaylistDetailScreen（SecondaryPageBackground） | 同上 | 待补 |
| `playlist_system/{type}` | type=="favorites" 占位 Box；否则 SystemPlaylistScreen（SecondaryPageBackground） | type StringType | 待补 |
| `downloads` | DownloadsScreen（SecondaryPageBackground） | 无 | 待补 |
| `dlsite_login` | DlsiteLoginScreen（SecondaryPageBackground） | 无 | 待补 |

## search_assist → search 结果回传桥（隐性行为，勿顺手清理）

`search` 路由的 NavHost 内容（production 在 MainContainer contents.search）：
1. 从 `backStackEntry.savedStateHandle` 读 11 个 `SEARCH_ASSIST_RESULT_*` 键（keyword/order/purchased/presale/chineseTranslated/collected/hasSubtitle/allAges/collectedSort/locale/signal）；
2. `submittedSignal > 0` 时把 11 值写入 MainContainer 的 `submittedSearch*` 状态（驱动 Pager 中 SearchScreen 刷新）；
3. 随后把 savedStateHandle 中 11 键全部复位（signal 归 0、其余回默认值），防止重复消费。

`SearchAssistScreen.onSubmitSearch`（MainContainer `submitSearchAssistRequest`）负责把结果写入 search 条目的 savedStateHandle 并 popBackStack。

## 转场与边界（与路由绑定的表现行为）

- NavHost enter：`isAlbumDetailStackTransition`（album_detail→album_detail 压栈）或目标为二级页（`usesSecondaryPageSlideTransition`）时 `secondaryPageEnterTransition()`，否则 None；exit/popEnter 恒 None；popExit 对称于 enter。
- 二级页有专用背景 `SecondaryPageBackground(topPadding = secondaryPageTopPadding)`；topPadding 由 Scaffold padding 同步（MainContainer 内 SideEffect）。
- `resolvePrimaryRoute` / `resolveCurrentPrimaryDestinationRoute` 把二级路由归属到 primary 祖先（如 `library_filter`→library、`group/*`→groups、`playlist/*`→playlists、`album_detail*`→lastPrimaryRoute），驱动 bottom chrome 高亮与 Pager 视觉路由。

## 跨屏回调通道（C1b 拆分时保持不变的契约）

- 跨屏回调经 MainContainer 闭包/参数传递：openNowPlaying、requestMiniPlayerPlayFeedback、submitMetaSearchKeyword、submitSearchAssistRequest、manualRj 对话框状态、albumBatchPlaylistPickerRequest、downloadsScrollToTopSignal 等。
- 详情页导航统一走 `AppNavigator`（popUpTo 语义见 `resolveAlbumDetailPopUpToRoute`：从 search/search_assist 系进入时 popUpTo search，从 library popUpTo library，热门收听 popUpTo hot_listening；stacked 变体不 popUpTo）。
