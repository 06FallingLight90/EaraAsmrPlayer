package com.asmr.player.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.asmr.player.ui.nav.Routes

/**
 * MainContainer 的路由装配契约：Graph 层（本文件）只负责 NavHost 注册、参数解析、
 * 占位主页面与转场；每个二级路由的屏幕装配经 [MainNavGraphContents] 回调由宿主提供。
 * 路由清单与隐性行为见 docs/behavior-notes/maincontainer-routes.md。
 */
internal class MainNavGraphContents(
    val libraryFilter: @Composable () -> Unit,
    val search: @Composable (NavBackStackEntry) -> Unit,
    val searchAssist: @Composable (initialKeyword: String) -> Unit,
    val albumDetailByRj: @Composable (entry: NavBackStackEntry, rj: String, initialTab: Int?) -> Unit,
    val albumDetailById: @Composable (
        entry: NavBackStackEntry,
        albumId: Long,
        rjCode: String?,
        initialTab: Int?
    ) -> Unit,
    val groupDetail: @Composable (groupId: Long, groupName: String) -> Unit,
    val playlistDetail: @Composable (playlistId: Long, playlistName: String) -> Unit,
    val playlistSystem: @Composable (type: String) -> Unit,
    val downloads: @Composable () -> Unit,
    val dlsiteLogin: @Composable () -> Unit,
    val purchased: @Composable () -> Unit
)

@Composable
internal fun MainNavGraph(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
    contents: MainNavGraphContents
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            if (isAlbumDetailStackTransition(
                    initialRoute = initialState.destination.route,
                    targetRoute = targetState.destination.route
                ) || targetState.usesSecondaryPageSlideTransition()
            ) {
                secondaryPageEnterTransition()
            } else {
                EnterTransition.None
            }
        },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = {
            if (isAlbumDetailStackTransition(
                    initialRoute = initialState.destination.route,
                    targetRoute = targetState.destination.route
                ) || initialState.usesSecondaryPageSlideTransition()
            ) {
                secondaryPagePopExitTransition()
            } else {
                ExitTransition.None
            }
        },
        modifier = modifier
    ) {
        // ---- 主页面（primary）路由：内容由 HorizontalPager 渲染，NavHost 仅注册占位 ----
        composable(Routes.Library) {
            PrimaryRoutePlaceholder()
        }
        composable(Routes.Search) { entry ->
            contents.search(entry)
        }
        composable(Routes.HotListening) {
            PrimaryRoutePlaceholder()
        }
        composable("playlists") {
            PrimaryRoutePlaceholder()
        }
        composable("groups") {
            PrimaryRoutePlaceholder()
        }
        composable("settings") {
            PrimaryRoutePlaceholder()
        }
        composable("listening_calendar") {
            PrimaryRoutePlaceholder()
        }

        // ---- 二级（secondary）路由族 ----
        composable("library_filter") {
            contents.libraryFilter()
        }
        composable(route = Routes.SearchAssist) {
            contents.searchAssist("")
        }
        composable(
            route = Routes.SearchAssistPattern,
            arguments = listOf(
                navArgument("keyword") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val initialKeyword = android.net.Uri.decode(
                backStackEntry.arguments?.getString("keyword").orEmpty()
            )
            contents.searchAssist(initialKeyword)
        }
        composable(
            route = Routes.AlbumDetailByRjPattern,
            arguments = listOf(
                navArgument("rj") { defaultValue = "" },
                navArgument("initialTab") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            val rj = backStackEntry.arguments?.getString("rj").orEmpty()
            contents.albumDetailByRj(
                backStackEntry,
                rj,
                backStackEntry.arguments
                    ?.getString("initialTab")
                    .toAlbumDetailInitialTab()
            )
        }
        composable(
            route = Routes.AlbumDetailByIdPattern,
            arguments = listOf(
                navArgument("albumId") { type = NavType.LongType },
                navArgument("rjCode") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("initialTab") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            contents.albumDetailById(
                backStackEntry,
                backStackEntry.arguments?.getLong("albumId") ?: 0L,
                backStackEntry.arguments?.getString("rjCode"),
                backStackEntry.arguments
                    ?.getString("initialTab")
                    .toAlbumDetailInitialTab()
            )
        }
        composable(
            route = "group/{groupId}/{groupName}",
            arguments = listOf(
                navArgument("groupId") { type = NavType.LongType; defaultValue = 0L },
                navArgument("groupName") { defaultValue = "" }
            )
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getLong("groupId") ?: 0L
            val groupName = decodeRouteArg(backStackEntry.arguments?.getString("groupName").orEmpty())
            contents.groupDetail(groupId, groupName)
        }
        composable(
            route = "playlist/{playlistId}/{playlistName}",
            arguments = listOf(
                navArgument("playlistId") { type = NavType.LongType; defaultValue = 0L },
                navArgument("playlistName") { defaultValue = "" }
            )
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: 0L
            val playlistName = decodeRouteArg(backStackEntry.arguments?.getString("playlistName").orEmpty())
            contents.playlistDetail(playlistId, playlistName)
        }
        composable("playlist_system/{type}") { backStackEntry ->
            val type = backStackEntry.arguments?.getString("type").orEmpty()
            contents.playlistSystem(type)
        }
        composable("downloads") {
            contents.downloads()
        }
        composable("dlsite_login") {
            contents.dlsiteLogin()
        }
        composable(Routes.Purchased) {
            contents.purchased()
        }
    }
}

@Composable
private fun PrimaryRoutePlaceholder() {
    Box(modifier = Modifier.fillMaxSize())
}
