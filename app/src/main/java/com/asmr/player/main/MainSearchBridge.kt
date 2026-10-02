package com.asmr.player.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import com.asmr.player.ui.search.SEARCH_ASSIST_RESULT_ALL_AGES_KEY
import com.asmr.player.ui.search.SEARCH_ASSIST_RESULT_CHINESE_TRANSLATED_ONLY_KEY
import com.asmr.player.ui.search.SEARCH_ASSIST_RESULT_COLLECTED_ONLY_KEY
import com.asmr.player.ui.search.SEARCH_ASSIST_RESULT_COLLECTED_SORT_KEY
import com.asmr.player.ui.search.SEARCH_ASSIST_RESULT_HAS_SUBTITLE_KEY
import com.asmr.player.ui.search.SEARCH_ASSIST_RESULT_KEY
import com.asmr.player.ui.search.SEARCH_ASSIST_RESULT_LOCALE_KEY
import com.asmr.player.ui.search.SEARCH_ASSIST_RESULT_ORDER_KEY
import com.asmr.player.ui.search.SEARCH_ASSIST_RESULT_PRESALE_ONLY_KEY
import com.asmr.player.ui.search.SEARCH_ASSIST_RESULT_PURCHASED_ONLY_KEY
import com.asmr.player.ui.search.SEARCH_ASSIST_RESULT_SIGNAL_KEY
import com.asmr.player.ui.search.SearchAssistSearchRequest

/**
 * search 路由的 savedStateHandle 回传桥。SearchAssist 提交的搜索条件经
 * backStackEntry.savedStateHandle 流式回传，signal > 0 时整批下发一次并复位。
 * 从 MainContainer 提取（R2-C1b），行为保持不变；宿主经 onSubmitted 接收。
 */
internal class MainSubmittedSearchValues(
    val keyword: String,
    val orderName: String,
    val purchasedOnly: Boolean,
    val presaleOnly: Boolean,
    val chineseTranslatedOnly: Boolean,
    val collectedOnly: Boolean,
    val hasSubtitle: Boolean,
    val allAges: Boolean,
    val collectedSortName: String,
    val locale: String,
    val signal: Long
)

@Composable
internal fun MainSearchAssistBridge(
    backStackEntry: NavBackStackEntry,
    onSubmitted: (MainSubmittedSearchValues) -> Unit
) {
    val submittedKeyword by backStackEntry.savedStateHandle
        .getStateFlow(SEARCH_ASSIST_RESULT_KEY, "")
        .collectAsStateWithLifecycle()
    val submittedOrderName by backStackEntry.savedStateHandle
        .getStateFlow(SEARCH_ASSIST_RESULT_ORDER_KEY, SearchAssistSearchRequest().orderName)
        .collectAsStateWithLifecycle()
    val submittedPurchasedOnly by backStackEntry.savedStateHandle
        .getStateFlow(SEARCH_ASSIST_RESULT_PURCHASED_ONLY_KEY, SearchAssistSearchRequest().purchasedOnly)
        .collectAsStateWithLifecycle()
    val submittedPresaleOnly by backStackEntry.savedStateHandle
        .getStateFlow(SEARCH_ASSIST_RESULT_PRESALE_ONLY_KEY, SearchAssistSearchRequest().presaleOnly)
        .collectAsStateWithLifecycle()
    val submittedChineseTranslatedOnly by backStackEntry.savedStateHandle
        .getStateFlow(
            SEARCH_ASSIST_RESULT_CHINESE_TRANSLATED_ONLY_KEY,
            SearchAssistSearchRequest().chineseTranslatedOnly
        )
        .collectAsStateWithLifecycle()
    val submittedCollectedOnly by backStackEntry.savedStateHandle
        .getStateFlow(SEARCH_ASSIST_RESULT_COLLECTED_ONLY_KEY, SearchAssistSearchRequest().collectedOnly)
        .collectAsStateWithLifecycle()
    val submittedHasSubtitle by backStackEntry.savedStateHandle
        .getStateFlow(SEARCH_ASSIST_RESULT_HAS_SUBTITLE_KEY, SearchAssistSearchRequest().hasSubtitle)
        .collectAsStateWithLifecycle()
    val submittedAllAges by backStackEntry.savedStateHandle
        .getStateFlow(SEARCH_ASSIST_RESULT_ALL_AGES_KEY, SearchAssistSearchRequest().allAges)
        .collectAsStateWithLifecycle()
    val submittedCollectedSortName by backStackEntry.savedStateHandle
        .getStateFlow(
            SEARCH_ASSIST_RESULT_COLLECTED_SORT_KEY,
            SearchAssistSearchRequest().collectedSortName
        )
        .collectAsStateWithLifecycle()
    val submittedLocale by backStackEntry.savedStateHandle
        .getStateFlow(SEARCH_ASSIST_RESULT_LOCALE_KEY, SearchAssistSearchRequest().locale)
        .collectAsStateWithLifecycle()
    val submittedSignal by backStackEntry.savedStateHandle
        .getStateFlow(SEARCH_ASSIST_RESULT_SIGNAL_KEY, 0L)
        .collectAsStateWithLifecycle()

    LaunchedEffect(
        submittedSignal,
        submittedKeyword,
        submittedOrderName,
        submittedPurchasedOnly,
        submittedPresaleOnly,
        submittedChineseTranslatedOnly,
        submittedCollectedOnly,
        submittedHasSubtitle,
        submittedAllAges,
        submittedCollectedSortName,
        submittedLocale
    ) {
        if (submittedSignal <= 0L) return@LaunchedEffect
        onSubmitted(
            MainSubmittedSearchValues(
                keyword = submittedKeyword,
                orderName = submittedOrderName,
                purchasedOnly = submittedPurchasedOnly,
                presaleOnly = submittedPresaleOnly,
                chineseTranslatedOnly = submittedChineseTranslatedOnly,
                collectedOnly = submittedCollectedOnly,
                hasSubtitle = submittedHasSubtitle,
                allAges = submittedAllAges,
                collectedSortName = submittedCollectedSortName,
                locale = submittedLocale,
                signal = submittedSignal
            )
        )
        backStackEntry.savedStateHandle[SEARCH_ASSIST_RESULT_KEY] = ""
        backStackEntry.savedStateHandle[SEARCH_ASSIST_RESULT_ORDER_KEY] =
            SearchAssistSearchRequest().orderName
        backStackEntry.savedStateHandle[SEARCH_ASSIST_RESULT_PURCHASED_ONLY_KEY] =
            SearchAssistSearchRequest().purchasedOnly
        backStackEntry.savedStateHandle[SEARCH_ASSIST_RESULT_PRESALE_ONLY_KEY] =
            SearchAssistSearchRequest().presaleOnly
        backStackEntry.savedStateHandle[SEARCH_ASSIST_RESULT_CHINESE_TRANSLATED_ONLY_KEY] =
            SearchAssistSearchRequest().chineseTranslatedOnly
        backStackEntry.savedStateHandle[SEARCH_ASSIST_RESULT_COLLECTED_ONLY_KEY] =
            SearchAssistSearchRequest().collectedOnly
        backStackEntry.savedStateHandle[SEARCH_ASSIST_RESULT_HAS_SUBTITLE_KEY] =
            SearchAssistSearchRequest().hasSubtitle
        backStackEntry.savedStateHandle[SEARCH_ASSIST_RESULT_ALL_AGES_KEY] =
            SearchAssistSearchRequest().allAges
        backStackEntry.savedStateHandle[SEARCH_ASSIST_RESULT_COLLECTED_SORT_KEY] =
            SearchAssistSearchRequest().collectedSortName
        backStackEntry.savedStateHandle[SEARCH_ASSIST_RESULT_LOCALE_KEY] =
            SearchAssistSearchRequest().locale
        backStackEntry.savedStateHandle[SEARCH_ASSIST_RESULT_SIGNAL_KEY] = 0L
    }

    Box(modifier = Modifier.fillMaxSize())
}
