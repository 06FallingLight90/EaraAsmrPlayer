package com.asmr.player.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.asmr.player.domain.model.LibrarySort
import com.asmr.player.ui.common.core.ActionButton
import com.asmr.player.ui.common.core.CustomSearchBar
import com.asmr.player.ui.common.list.ActiveDropdownMenuItem
import com.asmr.player.ui.common.list.CollapsibleHeaderState
import com.asmr.player.ui.common.list.collapsibleHeaderUiState
import com.asmr.player.ui.theme.AsmrTheme


internal const val LIBRARY_CHROME_TAG = "library_chrome"
internal const val LIBRARY_SEARCH_INPUT_TAG = "library_search_input"
internal const val LIBRARY_SORT_BUTTON_TAG = "library_sort_button"
internal const val LIBRARY_SORT_LAST_PLAYED_ITEM_TAG = "library_sort_last_played_item"
internal const val LIBRARY_SORT_ADDED_ITEM_TAG = "library_sort_added_item"
internal const val LIBRARY_SORT_TITLE_ITEM_TAG = "library_sort_title_item"
internal const val LIBRARY_FILTER_BUTTON_TAG = "library_filter_button"
internal const val LIBRARY_ALL_SONGS_BUTTON_TAG = "library_all_songs_button"

private val LibraryChromeCollapseOvershoot = 12.dp

@Composable
internal fun LibraryChrome(
    modifier: Modifier = Modifier,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    currentSort: LibrarySort,
    sortMenuExpanded: Boolean,
    onSortMenuExpandedChange: (Boolean) -> Unit,
    onSortLastPlayed: () -> Unit,
    onSortAdded: () -> Unit,
    onSortTitle: () -> Unit,
    onOpenFilterScreen: () -> Unit,
    filterActive: Boolean = false,
    onOpenAllSongs: (() -> Unit)? = null,
    rightPanelToggle: (@Composable (Modifier) -> Unit)?,
    materialColorScheme: androidx.compose.material3.ColorScheme,
    chromeState: CollapsibleHeaderState,
    onMeasured: (IntSize) -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val collapseOvershootPx = with(LocalDensity.current) { LibraryChromeCollapseOvershoot.toPx() }
    val collapseStateDescription by remember(chromeState) {
        derivedStateOf { collapsibleHeaderUiState(chromeState.collapseFraction) }
    }
    val chromeActionContainerColor = lerp(
        colorScheme.surface,
        colorScheme.primarySoft,
        if (colorScheme.isDark) 0.16f else 0.26f
    ).copy(alpha = if (colorScheme.isDark) 0.95f else 0.97f)
        .compositeOver(colorScheme.background)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = LibraryPageHorizontalPadding, vertical = 8.dp)
            .onSizeChanged(onMeasured)
            .graphicsLayer {
                val collapseFraction = chromeState.collapseFraction.coerceIn(0f, 1f)
                translationY = chromeState.offsetPx - (collapseFraction * collapseOvershootPx)
                alpha = 1f - (collapseFraction * 0.1f)
            }
            .semantics { stateDescription = collapseStateDescription }
            .testTag(LIBRARY_CHROME_TAG),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CustomSearchBar(
            value = searchText,
            onValueChange = onSearchTextChange,
            placeholder = "社团 / CV / 标签...",
            modifier = Modifier
                .weight(1f),
            inputTestTag = LIBRARY_SEARCH_INPUT_TAG,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = if (searchText.isNotBlank()) {
                {
                    IconButton(
                        onClick = onClearSearch,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null,
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            } else {
                null
            }
        )
        Spacer(modifier = Modifier.width(12.dp))
        Box {
            ActionButton(
                icon = Icons.Rounded.SwapVert,
                onClick = { onSortMenuExpandedChange(true) },
                modifier = Modifier.testTag(LIBRARY_SORT_BUTTON_TAG)
            )
            MaterialTheme(
                colorScheme = materialColorScheme.copy(
                    surface = chromeActionContainerColor,
                    surfaceContainer = chromeActionContainerColor,
                    surfaceVariant = chromeActionContainerColor
                )
            ) {
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { onSortMenuExpandedChange(false) },
                    modifier = Modifier.background(chromeActionContainerColor)
                ) {
                    ActiveDropdownMenuItem(
                        label = "最近播放",
                        selected = currentSort == LibrarySort.LastPlayedDesc,
                        testTag = LIBRARY_SORT_LAST_PLAYED_ITEM_TAG,
                        activeColor = materialColorScheme.primary,
                        inactiveColor = materialColorScheme.onSurface,
                        onClick = {
                            onSortMenuExpandedChange(false)
                            onSortLastPlayed()
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        thickness = 0.5.dp,
                        color = materialColorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                    ActiveDropdownMenuItem(
                        label = "最近加入",
                        selected = currentSort == LibrarySort.AddedDesc,
                        testTag = LIBRARY_SORT_ADDED_ITEM_TAG,
                        activeColor = materialColorScheme.primary,
                        inactiveColor = materialColorScheme.onSurface,
                        onClick = {
                            onSortMenuExpandedChange(false)
                            onSortAdded()
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        thickness = 0.5.dp,
                        color = materialColorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                    ActiveDropdownMenuItem(
                        label = "专辑标题",
                        selected = currentSort == LibrarySort.TitleAsc,
                        testTag = LIBRARY_SORT_TITLE_ITEM_TAG,
                        activeColor = materialColorScheme.primary,
                        inactiveColor = materialColorScheme.onSurface,
                        onClick = {
                            onSortMenuExpandedChange(false)
                            onSortTitle()
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        ActionButton(
            icon = Icons.Rounded.FilterList,
            onClick = onOpenFilterScreen,
            modifier = Modifier
                .testTag(LIBRARY_FILTER_BUTTON_TAG)
                .semantics { stateDescription = if (filterActive) "筛选已启用" else "筛选未启用" },
            active = filterActive
        )
        // US-05/T6：全部歌曲平铺视图入口（null = 宿主不装配时按钮不渲染）
        if (onOpenAllSongs != null) {
            Spacer(modifier = Modifier.width(8.dp))
            ActionButton(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                onClick = onOpenAllSongs,
                modifier = Modifier
                    .testTag(LIBRARY_ALL_SONGS_BUTTON_TAG)
                    .semantics { stateDescription = "全部歌曲" }
            )
        }
        if (rightPanelToggle != null) {
            Spacer(modifier = Modifier.width(8.dp))
            rightPanelToggle(Modifier.size(50.dp))
        }
    }
}
