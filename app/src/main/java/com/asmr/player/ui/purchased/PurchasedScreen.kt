package com.asmr.player.ui.purchased

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.asmr.player.domain.model.Album
import com.asmr.player.ui.common.list.LocalBottomOverlayPadding
import com.asmr.player.ui.common.list.rememberCalmScrollableFlingBehavior
import com.asmr.player.util.DlsiteAntiHotlink
import kotlin.math.absoluteValue

private val PurchasedRowCornerRadius = 12.dp
private val PurchasedRowHorizontalPadding = 12.dp
private val PurchasedRowTopPadding = 4.dp
private val PurchasedRowBottomPadding = 8.dp
private val PurchasedRowCoverContentSpacing = 10.dp
private const val PurchasedLoadMoreVisibleThreshold = 6

@Composable
fun PurchasedScreen(
    onOpenLogin: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenAlbum: (Album) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PurchasedViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 每次进入本页（含从 dlsite_login / 作品详情返回）都会重跑：
    // 刷新登录态；Ready 态保持不动，登录回来/失败回来则自动重载首屏。
    LaunchedEffect(Unit) {
        viewModel.bootstrap()
    }

    Column(modifier = modifier.fillMaxSize()) {
        PurchasedTopBar(
            isRefreshing = (uiState as? PurchasedUiState.Ready)?.isRefreshing == true,
            onRefresh = viewModel::refresh,
            onOpenLogin = onOpenLogin,
            onOpenDownloads = onOpenDownloads
        )
        when (val state = uiState) {
            is PurchasedUiState.Bootstrapping, is PurchasedUiState.Loading -> PurchasedLoadingContent()
            is PurchasedUiState.NotLoggedIn -> PurchasedStateView(
                icon = Icons.Rounded.ShoppingBag,
                headline = "登录 DLsite 后查看已购作品",
                footer = { PurchasedActionButton(text = "去登录", onClick = onOpenLogin) }
            )
            is PurchasedUiState.Error -> PurchasedStateView(
                icon = Icons.Rounded.WifiOff,
                headline = state.message,
                footer = { PurchasedActionButton(text = "重试", onClick = viewModel::retry) }
            )
            is PurchasedUiState.Ready -> if (state.isEmpty) {
                PurchasedStateView(
                    icon = Icons.Rounded.ShoppingBag,
                    headline = "暂无已购作品",
                    footer = { PurchasedActionButton(text = "刷新", onClick = viewModel::refresh) }
                )
            } else {
                PurchasedListContent(
                    state = state,
                    onLoadMore = viewModel::loadMore,
                    onOpenAlbum = onOpenAlbum
                )
            }
        }
    }
}

@Composable
private fun PurchasedTopBar(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenDownloads: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "已购曲库",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        if (isRefreshing) {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp
                )
            }
        } else {
            IconButton(onClick = onRefresh) {
                Icon(imageVector = Icons.Rounded.Refresh, contentDescription = "刷新")
            }
        }
        IconButton(onClick = onOpenDownloads) {
            Icon(imageVector = Icons.Rounded.Download, contentDescription = "下载管理")
        }
        IconButton(onClick = onOpenLogin) {
            Icon(imageVector = Icons.Rounded.Person, contentDescription = "DLsite 登录")
        }
    }
}

@Composable
private fun PurchasedLoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(40.dp),
            strokeWidth = 3.dp
        )
    }
}

/** 页内空/错误/未登录态：视觉对齐 EaraBrandedEmptyState（圆形底 + 图标 + 标语 + 操作）。 */
@Composable
private fun PurchasedStateView(
    icon: ImageVector,
    headline: String,
    modifier: Modifier = Modifier,
    footer: (@Composable () -> Unit)? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(
                        color = colorScheme.primary.copy(alpha = 0.10f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colorScheme.primary.copy(alpha = 0.65f),
                    modifier = Modifier.size(48.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = headline,
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            footer?.let {
                Spacer(modifier = Modifier.height(16.dp))
                it()
            }
        }
    }
}

@Composable
private fun PurchasedActionButton(text: String, onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    FilledTonalButton(
        onClick = onClick,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = colorScheme.primaryContainer,
            contentColor = colorScheme.onPrimaryContainer
        )
    ) {
        Text(text)
    }
}

@Composable
private fun PurchasedListContent(
    state: PurchasedUiState.Ready,
    onLoadMore: () -> Unit,
    onOpenAlbum: (Album) -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.canLoadMore) {
        if (!state.canLoadMore) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisibleIndex = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 &&
                lastVisibleIndex >= info.totalItemsCount - PurchasedLoadMoreVisibleThreshold
        }.collect { nearEnd ->
            if (nearEnd) onLoadMore()
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        flingBehavior = rememberCalmScrollableFlingBehavior(),
        contentPadding = PaddingValues(
            top = 4.dp,
            bottom = LocalBottomOverlayPadding.current + 16.dp
        )
    ) {
        items(
            items = state.items,
            key = { purchasedItemKey(it) },
            contentType = { "purchasedAlbum" }
        ) { album ->
            PurchasedAlbumRow(
                album = album,
                onClick = { onOpenAlbum(album) }
            )
        }
        if (state.isLoadingMore) {
            item(key = "purchased-loading-more") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }
}

/**
 * 已购行组件：对齐搜索结果行（ui/library AlbumItem）的视觉风格——左方形封面
 * （宽 24% 屏宽、12dp 圆角）+ 右侧标题/RJ·社团/CV/标签/统计列。
 * AlbumItem/AsmrAsyncImage/EaraBrandedEmptyState 等均在 ci_guard 包级 SCC 大连通团
 * 内（ui.library / ui.common.cover / ui.common.status），新包引用会入环被 ratchet
 * 拦截，且搜索将来整体删除，故按任务预案自持薄实现，仅复用 ui.common.list 与
 * util（已验证不在连通团内）。
 */
@Composable
private fun PurchasedAlbumRow(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val coverSize = (screenWidthDp.dp * 0.24f).coerceIn(112.dp, 140.dp)
    val context = LocalContext.current
    val coverModel = remember(album.coverThumbPath, album.coverPath, album.coverUrl) {
        purchasedCoverRequest(context, album)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = PurchasedRowHorizontalPadding,
                top = PurchasedRowTopPadding,
                end = PurchasedRowHorizontalPadding,
                bottom = PurchasedRowBottomPadding
            )
            .heightIn(min = coverSize)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(coverSize)
                .clip(RoundedCornerShape(PurchasedRowCornerRadius))
        ) {
            AsyncImage(
                model = coverModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(coverSize)
            )
        }
        Spacer(modifier = Modifier.width(PurchasedRowCoverContentSpacing))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = album.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val rj = album.rjCode.ifBlank { album.workId }.trim().uppercase()
            val metaLine = listOf(rj, album.circle.trim())
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (metaLine.isNotEmpty()) {
                Text(
                    text = metaLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (album.cv.isNotBlank()) {
                Text(
                    text = album.cv,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val tagsLine = album.tags.joinToString(" / ")
            if (tagsLine.isNotBlank()) {
                Text(
                    text = tagsLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val statsLine = remember(album.id, album.rjCode, album.workId) {
                purchasedStatsText(album)
            }
            if (statsLine.isNotBlank()) {
                Text(
                    text = statsLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** 封面 model：路径优先级对齐 albumCoverImageModel（_v2 缩略图 → 本地图 → URL），
 *  DLsite 图链补防防盗链头（coils 侧经 ImageRequest header 传入）。 */
private fun purchasedCoverRequest(context: android.content.Context, album: Album): Any? {
    val data = album.coverThumbPath
        .takeIf { it.isNotBlank() && it.contains("_v2") }
        ?: album.coverPath.takeIf { it.isNotBlank() }
        ?: album.coverUrl.takeIf { it.isNotBlank() }
        ?: return null
    val headers = if (data.startsWith("http", ignoreCase = true)) {
        DlsiteAntiHotlink.headersForImageUrl(data)
    } else {
        emptyMap()
    }
    return ImageRequest.Builder(context)
        .data(data)
        .crossfade(150)
        .apply {
            headers.forEach { (name, value) -> addHeader(name, value) }
        }
        .build()
}

private fun purchasedStatsText(album: Album): String {
    return buildList {
        album.ratingValue?.takeIf { it > 0.0 }?.let {
            add("★%s (%d)".format(java.util.Locale.US, it, album.ratingCount))
        }
        if (album.dlCount > 0) add("${album.dlCount} 销量")
        if (album.releaseDate.isNotBlank()) add(album.releaseDate)
        if (album.priceJpy > 0) add("¥${album.priceJpy}")
    }.joinToString(" · ")
}

private fun purchasedItemKey(album: Album): String {
    album.asmrOneWorkId?.takeIf { it > 0 }?.let { return "purchased:asmr-one:$it" }
    val id = album.rjCode.ifBlank { album.workId }.trim()
    if (id.isNotEmpty()) return "purchased:$id"
    val seed = "${album.coverUrl}|${album.title}|${album.circle}|${album.cv}"
    return "purchased:h${seed.hashCode().absoluteValue}"
}
