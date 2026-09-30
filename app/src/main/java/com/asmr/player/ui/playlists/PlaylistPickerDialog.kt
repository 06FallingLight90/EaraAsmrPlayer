package com.asmr.player.ui.playlists

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import com.asmr.player.ui.common.dialog.CollectionPickerContent
import com.asmr.player.ui.common.dialog.CollectionPickerRow

@Composable
fun PlaylistPickerScreen(
    windowSizeClass: WindowSizeClass,
    items: List<MediaItem>,
    onBack: () -> Unit,
    embeddedInDialog: Boolean = false,
    viewModel: PlaylistsViewModel = hiltViewModel()
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val userPlaylists = remember(playlists) { playlists.filter { it.category == "user" } }
    val screenActive = remember { mutableStateOf(true) }
    var createName by rememberSaveable { mutableStateOf("") }
    var addingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val isAdding = addingId != null

    BackHandler(enabled = isAdding) {}
    DisposableEffect(Unit) {
        onDispose { screenActive.value = false }
    }

    CollectionPickerContent(
        windowSizeClass = windowSizeClass,
        title = "选择列表",
        createPlaceholder = "新建列表名称",
        createName = createName,
        onCreateNameChange = { createName = it },
        onCreate = {
            val trimmed = createName.trim()
            if (trimmed.isNotBlank()) {
                viewModel.createPlaylist(trimmed)
                createName = ""
            }
        },
        onBack = onBack,
        embeddedInDialog = embeddedInDialog,
        isAdding = isAdding,
        isEmpty = userPlaylists.isEmpty(),
        emptyText = "暂无可选列表",
        selectionSummary = if (items.size > 1) "将添加 ${items.size} 项" else null,
    ) {
        items(userPlaylists, key = { it.id }) { playlist ->
            CollectionPickerRow(
                name = playlist.name,
                artworkUri = playlist.firstArtworkUri,
                summary = "共 ${playlist.itemCount} 首",
                enabled = !isAdding,
                isAdding = addingId == playlist.id,
                onClick = {
                    addingId = playlist.id
                    viewModel.addItemsToPlaylistInBackground(
                        playlistId = playlist.id,
                        items = items,
                        onComplete = {
                            if (screenActive.value) {
                                addingId = null
                                onBack()
                            }
                        },
                        onFailure = {
                            if (screenActive.value) {
                                addingId = null
                            }
                        }
                    )
                }
            )
        }
    }
}
