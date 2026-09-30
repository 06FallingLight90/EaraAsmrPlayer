package com.asmr.player.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asmr.player.ui.common.cover.AsmrAsyncImage
import com.asmr.player.ui.common.list.rememberCalmScrollableFlingBehavior
import com.asmr.player.ui.theme.AsmrTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheetContent(
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentMediaId by remember(viewModel) {
        viewModel.playback
            .map { snapshot -> snapshot.currentMediaItem?.mediaId.orEmpty() }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = "")
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val colorScheme = AsmrTheme.colorScheme
    val listState = rememberLazyListState()

    val currentIndex = remember(queue, currentMediaId) {
        queue.indexOfFirst { it.mediaId == currentMediaId }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "当前播放队列",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = colorScheme.textSecondary
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true),
            flingBehavior = rememberCalmScrollableFlingBehavior(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            itemsIndexed(queue, key = { idx, it -> "${it.mediaId}#$idx" }) { index, mediaItem ->
                val title = mediaItem.mediaMetadata.title?.toString().orEmpty().ifBlank { mediaItem.mediaId }
                val artist = mediaItem.mediaMetadata.artist?.toString().orEmpty()
                val uriText = mediaItem.localConfiguration?.uri?.toString().orEmpty()
                val sourceLabel = if (uriText.startsWith("http", ignoreCase = true)) "在线" else "本地"
                val selected = index == currentIndex

                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (index in queue.indices) viewModel.playQueueIndex(index)
                                onDismiss()
                            }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsmrAsyncImage(
                            model = mediaItem.mediaMetadata.artworkUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            placeholderCornerRadius = 6,
                            peekAnySizeForInitial = true,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (selected) colorScheme.primary else colorScheme.textPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (artist.isNotBlank()) "$sourceLabel · $artist" else sourceLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { viewModel.removeFromQueue(index) }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "移除",
                                tint = colorScheme.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    if (index < queue.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            thickness = 0.5.dp,
                            color = colorScheme.textSecondary.copy(alpha = 0.18f)
                        )
                    }
                }
            }
        }
    }
}
