package com.asmr.player

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.asmr.player.main.PRIMARY_PAGER_SAVEABLE_KEY
import com.asmr.player.main.primaryRouteSaveableKey
import com.asmr.player.main.bottomChromeNavItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PRIMARY_PAGE_POSITION_TAG = "primary_page_position"

/**
 * 钉住 MainContainer 主页面 Pager 的可保存状态契约：嵌套
 * `SaveableStateProvider(PRIMARY_PAGER_SAVEABLE_KEY)` → `SaveableStateProvider(primaryRouteSaveableKey(route))`
 * 卸载再挂载后，页面内 rememberSaveable 的状态（如 LazyListState）必须恢复。
 * key 字面量与生产代码共享（MainNavigationSupport），防止契约无声漂移。
 */
@RunWith(AndroidJUnit4::class)
class MainContainerSaveableStateTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun primaryRouteSaveableKey_matchesProductionScheme() {
        assertEquals("primary_route:search", primaryRouteSaveableKey("search"))
        assertEquals("primary_route:library", primaryRouteSaveableKey("library"))
    }

    @Test
    fun primaryChromeRoutes_areAllCoveredBySaveableScheme() {
        val routes = bottomChromeNavItems().map { it.route }
        assertTrue(routes.size >= 8)
        routes.forEach { route ->
            assertTrue(
                "route key must be prefixed scheme: $route",
                primaryRouteSaveableKey(route).startsWith("primary_route:")
            )
        }
    }

    @Test
    fun primaryPagerSaveableHost_restoresLazyListStateAfterRemount() {
        lateinit var setPrimaryVisible: (Boolean) -> Unit
        lateinit var requestScroll: (Int, Int) -> Unit

        composeRule.setContent {
            var showPrimaryPage by remember { mutableStateOf(true) }
            var pendingScroll by remember { mutableStateOf<Pair<Int, Int>?>(null) }
            val stateHolder = rememberSaveableStateHolder()

            setPrimaryVisible = { showPrimaryPage = it }
            requestScroll = { index, offset -> pendingScroll = index to offset }

            stateHolder.SaveableStateProvider(PRIMARY_PAGER_SAVEABLE_KEY) {
                if (showPrimaryPage) {
                    stateHolder.SaveableStateProvider(primaryRouteSaveableKey("search")) {
                        SaveablePrimaryPage(
                            pendingScroll = pendingScroll,
                            onScrollHandled = { pendingScroll = null }
                        )
                    }
                }
            }
        }

        composeRule.runOnIdle {
            requestScroll(12, 24)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PRIMARY_PAGE_POSITION_TAG).assertTextContains("12:24")

        composeRule.runOnIdle {
            setPrimaryVisible(false)
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            setPrimaryVisible(true)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PRIMARY_PAGE_POSITION_TAG).assertTextContains("12:24")
    }
}

@Composable
private fun SaveablePrimaryPage(
    pendingScroll: Pair<Int, Int>?,
    onScrollHandled: () -> Unit
) {
    val listState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState(0, 0)
    }

    LaunchedEffect(pendingScroll) {
        val request = pendingScroll ?: return@LaunchedEffect
        listState.scrollToItem(request.first, request.second)
        onScrollHandled()
    }

    Text(
        text = "${listState.firstVisibleItemIndex}:${listState.firstVisibleItemScrollOffset}",
        modifier = Modifier.testTag(PRIMARY_PAGE_POSITION_TAG)
    )
    LazyColumn(
        state = listState,
        modifier = Modifier.height(120.dp)
    ) {
        items((0 until 40).toList()) { index ->
            Text(
                text = "Item $index",
                modifier = Modifier.height(36.dp)
            )
        }
    }
}
