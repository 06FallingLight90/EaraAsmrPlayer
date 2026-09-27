package com.asmr.player.ui.groups

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
import com.asmr.player.ui.common.CollectionPickerContent
import com.asmr.player.ui.common.CollectionPickerRow

@Composable
fun AlbumGroupPickerScreen(
    windowSizeClass: WindowSizeClass,
    albumId: Long,
    onBack: () -> Unit,
    embeddedInDialog: Boolean = false,
    viewModel: AlbumGroupsViewModel = hiltViewModel()
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
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
        title = "选择分组",
        createPlaceholder = "新建分组名称",
        createName = createName,
        onCreateNameChange = { createName = it },
        onCreate = {
            val trimmed = createName.trim()
            if (trimmed.isNotBlank()) {
                viewModel.createGroup(trimmed)
                createName = ""
            }
        },
        onBack = onBack,
        embeddedInDialog = embeddedInDialog,
        isAdding = isAdding,
        isEmpty = groups.isEmpty(),
        emptyText = "暂无可选分组",
    ) {
        items(groups, key = { it.id }) { group ->
            CollectionPickerRow(
                name = group.name,
                artworkUri = group.firstArtworkUri,
                summary = "共 ${group.albumCount} 张专辑 · ${group.itemCount} 首",
                enabled = !isAdding,
                isAdding = addingId == group.id,
                onClick = {
                    addingId = group.id
                    viewModel.addAlbumToGroupInBackground(
                        groupId = group.id,
                        albumId = albumId,
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
