package com.asmr.player

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.asmr.player.main.MainNavGraph
import com.asmr.player.main.MainNavGraphContents
import com.asmr.player.main.navigateSingleTop
import com.asmr.player.ui.nav.Routes
import com.asmr.player.main.isPrimaryRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URLEncoder
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 钉住 MainContainer 的路由装配契约（Graph 层）：
 * 给定 route → 调用对应的 contents 装配回调，并携带解析后的参数。
 * 行为档案：docs/behavior-notes/maincontainer-routes.md
 */
@RunWith(RobolectricTestRunner::class)
class MainContainerRouteTest {
    @get:Rule
    val composeRule = createComposeRule()

    private data class Invocation(val name: String, val args: List<String?>)

    private val invocations = CopyOnWriteArrayList<Invocation>()

    @Composable
    private fun Marker(name: String) {
        Text(text = name, modifier = Modifier.testTag(ROUTE_MARKER_TAG))
    }

    private fun record(name: String, vararg args: Any?) {
        val invocation = Invocation(name, args.map { it?.toString() })
        // 重组会重复调用 contents 回调；连续相同调用合并，只关心"路由 → 参数"契约本身。
        if (invocations.lastOrNull() != invocation) {
            invocations.add(invocation)
        }
    }

    private fun setContent(): NavHostController {
        lateinit var navController: NavHostController
        composeRule.setContent {
            navController = rememberNavController()
            MainNavGraph(
                navController = navController,
                startDestination = Routes.Library,
                contents = MainNavGraphContents(
                    libraryFilter = {
                        record("libraryFilter")
                        Marker("libraryFilter")
                    },
                    search = { entry ->
                        record("search", entry.destination.route)
                        Marker("search")
                    },
                    searchAssist = { initialKeyword ->
                        record("searchAssist", initialKeyword)
                        Marker("searchAssist")
                    },
                    albumDetailByRj = { _, rj, initialTab ->
                        record("albumDetailByRj", rj, initialTab)
                        Marker("albumDetailByRj")
                    },
                    albumDetailById = { _, albumId, rjCode, initialTab ->
                        record("albumDetailById", albumId, rjCode, initialTab)
                        Marker("albumDetailById")
                    },
                    groupDetail = { groupId, groupName ->
                        record("groupDetail", groupId, groupName)
                        Marker("groupDetail")
                    },
                    playlistDetail = { playlistId, playlistName ->
                        record("playlistDetail", playlistId, playlistName)
                        Marker("playlistDetail")
                    },
                    playlistSystem = { type ->
                        record("playlistSystem", type)
                        Marker("playlistSystem")
                    },
                    downloads = {
                        record("downloads")
                        Marker("downloads")
                    },
                    dlsiteLogin = {
                        record("dlsiteLogin")
                        Marker("dlsiteLogin")
                    }
                )
            )
        }
        composeRule.waitForIdle()
        return navController
    }

    private fun assertInvokedOnce(name: String, vararg args: String?) {
        val matched = invocations.filter { it.name == name }
        assertTrue("expected at least one invocation of $name, got $invocations", matched.isNotEmpty())
        matched.forEach { invocation ->
            assertEquals(args.toList(), invocation.args)
        }
    }

    @Test
    fun startDestination_isLibrary_withoutContentInvocation() {
        val navController = setContent()
        assertEquals(Routes.Library, navController.currentDestination?.route)
        assertTrue(invocations.isEmpty())
    }

    @Test
    fun secondaryRoutes_dispatchToContents() {
        val navController = setContent()
        composeRule.runOnIdle { navController.navigateSingleTop("downloads") }
        composeRule.waitForIdle()
        assertEquals("downloads", navController.currentDestination?.route)
        assertInvokedOnce("downloads")
    }

    @Test
    fun secondaryRoutes_dispatchLibraryFilterAndDlsiteLogin() {
        val navController = setContent()
        composeRule.runOnIdle { navController.navigateSingleTop("library_filter") }
        composeRule.waitForIdle()
        assertInvokedOnce("libraryFilter")

        composeRule.runOnIdle { navController.navigateSingleTop("dlsite_login") }
        composeRule.waitForIdle()
        assertInvokedOnce("dlsiteLogin")
    }

    @Test
    fun searchAssist_withoutKeyword_passesEmptyKeyword() {
        val navController = setContent()
        composeRule.runOnIdle { navController.navigateSingleTop(Routes.SearchAssist) }
        composeRule.waitForIdle()
        assertInvokedOnce("searchAssist", "")
    }

    @Test
    fun searchAssist_pattern_decodesKeyword() {
        val navController = setContent()
        val route = Routes.searchAssist("夜想曲")
        composeRule.runOnIdle { navController.navigateSingleTop(route) }
        composeRule.waitForIdle()
        assertEquals(Routes.SearchAssistPattern, navController.currentDestination?.route)
        assertInvokedOnce("searchAssist", "夜想曲")
    }

    @Test
    fun albumDetailByRj_parsesRjAndInitialTab() {
        val navController = setContent()
        val route = Routes.albumDetailByRj("RJ01162269", initialTab = "dlsitePlay")
        composeRule.runOnIdle { navController.navigateSingleTop(route) }
        composeRule.waitForIdle()
        assertEquals(Routes.AlbumDetailByRjPattern, navController.currentDestination?.route)
        assertInvokedOnce("albumDetailByRj", "RJ01162269", "2")
    }

    @Test
    fun albumDetailByRj_withoutInitialTab_passesNull() {
        val navController = setContent()
        composeRule.runOnIdle { navController.navigateSingleTop(Routes.albumDetailByRj("RJ01162269")) }
        composeRule.waitForIdle()
        assertInvokedOnce("albumDetailByRj", "RJ01162269", null)
    }

    @Test
    fun albumDetailById_parsesIdRjCodeAndInitialTab() {
        val navController = setContent()
        composeRule.runOnIdle {
            navController.navigateSingleTop("album_detail/42?rjCode=RJ01162269&initialTab=dl")
        }
        composeRule.waitForIdle()
        assertEquals(Routes.AlbumDetailByIdPattern, navController.currentDestination?.route)
        assertInvokedOnce("albumDetailById", "42", "RJ01162269", "1")
    }

    @Test
    fun groupRoute_decodesNameAndId() {
        val navController = setContent()
        val encodedName = URLEncoder.encode("夜想曲", "UTF-8")
        composeRule.runOnIdle { navController.navigateSingleTop("group/7/$encodedName") }
        composeRule.waitForIdle()
        assertEquals("group/{groupId}/{groupName}", navController.currentDestination?.route)
        assertInvokedOnce("groupDetail", "7", "夜想曲")
    }

    @Test
    fun playlistRoute_decodesNameAndId() {
        val navController = setContent()
        composeRule.runOnIdle { navController.navigateSingleTop("playlist/3/MyList") }
        composeRule.waitForIdle()
        assertInvokedOnce("playlistDetail", "3", "MyList")
    }

    @Test
    fun playlistSystemRoute_passesTypeThrough() {
        val navController = setContent()
        composeRule.runOnIdle { navController.navigateSingleTop("playlist_system/download") }
        composeRule.waitForIdle()
        assertInvokedOnce("playlistSystem", "download")
    }

    @Test
    fun primaryRoutes_renderPlaceholdersWithoutSecondaryContents() {
        val navController = setContent()
        val primaryRoutes = listOf(
            Routes.Library,
            "playlists",
            "groups",
            "settings",
            "listening_calendar",
            Routes.HotListening
        )
        primaryRoutes.forEach { route ->
            composeRule.runOnIdle { navController.navigateSingleTop(route) }
            composeRule.waitForIdle()
            assertEquals(route, navController.currentDestination?.route)
        }
        assertTrue("primary placeholders must not invoke secondary contents, got $invocations", invocations.isEmpty())
    }

    @Test
    fun primarySearchRoute_invokesSearchBridgeContent() {
        val navController = setContent()
        composeRule.runOnIdle { navController.navigateSingleTop(Routes.Search) }
        composeRule.waitForIdle()
        assertEquals(Routes.Search, navController.currentDestination?.route)
        assertInvokedOnce("search", Routes.Search)
    }

    @Test
    fun startRouteDispatch_primaryVsSecondaryClassification() {
        val primary = listOf(
            Routes.Library,
            Routes.Search,
            Routes.HotListening,
            "playlist_system/favorites",
            "playlists",
            "groups",
            "settings",
            "listening_calendar"
        )
        primary.forEach { route ->
            assertTrue("'$route' must be primary", isPrimaryRoute(route))
        }
        listOf(
            "library_filter",
            Routes.SearchAssist,
            Routes.AlbumDetailByIdPattern,
            Routes.AlbumDetailByRjPattern,
            "downloads",
            "dlsite_login",
            "group/{groupId}/{groupName}",
            "playlist/{playlistId}/{playlistName}",
            "playlist_system/{type}"
        ).forEach { route ->
            assertFalse("'$route' must not be primary", isPrimaryRoute(route))
        }
    }

    private companion object {
        const val ROUTE_MARKER_TAG = "route_marker"
    }
}
