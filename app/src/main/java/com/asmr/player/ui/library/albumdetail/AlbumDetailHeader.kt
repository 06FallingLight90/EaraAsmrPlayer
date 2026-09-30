package com.asmr.player.ui.library

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.roundToInt
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.asmr.player.domain.model.Album
import com.asmr.player.data.remote.dlsite.DlsiteLanguageEdition
import androidx.compose.material.icons.automirrored.rounded.Label
import kotlinx.coroutines.delay
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.util.MessageManager





@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun AlbumHeader(
    album: Album,
    dlsiteUrl: String,
    asmrOneUrl: String,
    dlsiteEditions: List<DlsiteLanguageEdition>,
    dlsiteSelectedLang: String,
    onDlsiteLangSelected: (String) -> Unit,
    showSaveAction: Boolean,
    onDownloadClick: () -> Unit,
    showDlsitePlayLossless: Boolean,
    onLosslessDownloadClick: () -> Unit,
    onSaveClick: () -> Unit,
    downloadEnabled: Boolean,
    losslessDownloadEnabled: Boolean,
    saveEnabled: Boolean,
    showGroupButton: Boolean,
    onOpenGroupPicker: (albumId: Long) -> Unit,
    introSessionKey: String,
    animateIntro: Boolean,
    availableWidth: Dp,
    messageManager: MessageManager,
    onMetaLongClick: (String) -> Unit,
    landscapeFloatingActions: Boolean = false
) {
    val copyMeta = rememberAlbumMetaCopyAction(messageManager)

    val headerAnimationScopeKey = remember(introSessionKey) { "albumHeader:$introSessionKey" }

    // 首帧已有的信息直接显示；网络到达后新增的声优与标签分别向下滑入并展开，
    // 避免把整列元信息一次性替换而造成突跳。
    val cvPresentInitially = remember(headerAnimationScopeKey) { album.cv.isNotBlank() }
    val tagsPresentInitially = remember(headerAnimationScopeKey) { album.tags.isNotEmpty() }
    val headerContainerModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = AlbumDetailHorizontalPadding)
    Column(
        modifier = headerContainerModifier.padding(
            top = 4.dp,
            bottom = if (landscapeFloatingActions) 2.dp else 12.dp
        )
        // 不用 spacedBy 控制信息行之间的间距：cv/tags 行在网络数据到达后会以 0 高度组合、再通过
        // AnimatedVisibility 纵向展开，而 spacedBy 的固定间距会在"0 高度的折叠内容刚组合"的那一帧
        // 立即出现，把下方按钮行瞬间下推一截，造成展开前的下沉抖动。改为把行间距/与按钮行的间距作为
        // 每个信息行自身的底部 padding 放进 reveal 内部——这样间距属于被 expandVertically 裁剪的高度，
        // 会随展开动画一起从 0 平滑增长，按钮行始终被平滑下移而非瞬间跳变。
    ) {
        val metaRevealKey = headerAnimationScopeKey + ":meta"
        AlbumHeaderLateMetaReveal(
            revealKey = "$metaRevealKey:cv",
            hasContent = album.cv.isNotBlank(),
            presentInitially = cvPresentInitially,
            delayMillis = AlbumDetailCvRevealDelayMs,
            animationsEnabled = animateIntro
        ) {
            Box(modifier = Modifier.padding(bottom = 8.dp)) {
                AlbumHeaderCvLightweight(
                    cvText = album.cv,
                    emphasized = landscapeFloatingActions,
                    onCvClick = { cv -> copyMeta("声优", cv) },
                    onCvLongClick = onMetaLongClick
                )
            }
        }
        AlbumHeaderLateMetaReveal(
            revealKey = "$metaRevealKey:tags",
            hasContent = album.tags.isNotEmpty(),
            presentInitially = tagsPresentInitially,
            delayMillis = AlbumDetailTagsRevealDelayMs,
            animationsEnabled = animateIntro
        ) {
            Box(modifier = Modifier.padding(bottom = 8.dp)) {
                AlbumHeaderTagsLightweight(
                    tags = album.tags,
                    emphasized = landscapeFloatingActions,
                    onTagClick = { tag -> copyMeta("标签", tag) },
                    onTagLongClick = onMetaLongClick
                )
            }
        }

        AlbumHeaderActionBar(
            groupState = when {
                showDlsitePlayLossless -> AlbumHeaderButtonGroupState.Lossless
                showSaveAction -> AlbumHeaderButtonGroupState.Save
                else -> AlbumHeaderButtonGroupState.DownloadOnly
            },
            onDownloadClick = onDownloadClick,
            onSaveClick = onSaveClick,
            onLosslessDownloadClick = onLosslessDownloadClick,
            downloadEnabled = downloadEnabled,
            saveEnabled = saveEnabled,
            losslessDownloadEnabled = losslessDownloadEnabled,
            showGroupAction = showGroupButton,
            groupEnabled = album.id > 0L,
            onGroupClick = {
                val id = album.id
                if (id > 0L) onOpenGroupPicker(id)
            },
            dlsiteEditions = dlsiteEditions,
            dlsiteSelectedLang = dlsiteSelectedLang,
            onDlsiteLangSelected = onDlsiteLangSelected,
            dlsiteUrl = dlsiteUrl,
            asmrOneUrl = asmrOneUrl,
            availableWidth = availableWidth,
            floating = landscapeFloatingActions,
        )
    }
}

@Composable
private fun AlbumHeaderActionBar(
    groupState: AlbumHeaderButtonGroupState,
    onDownloadClick: () -> Unit,
    onSaveClick: () -> Unit,
    onLosslessDownloadClick: () -> Unit,
    downloadEnabled: Boolean,
    saveEnabled: Boolean,
    losslessDownloadEnabled: Boolean,
    showGroupAction: Boolean,
    groupEnabled: Boolean,
    onGroupClick: () -> Unit,
    dlsiteEditions: List<DlsiteLanguageEdition>,
    dlsiteSelectedLang: String,
    onDlsiteLangSelected: (String) -> Unit,
    dlsiteUrl: String,
    asmrOneUrl: String,
    availableWidth: Dp,
    floating: Boolean = false,
) {
    val context = LocalContext.current
    val colorScheme = AsmrTheme.colorScheme
    val compact = availableWidth < 400.dp
    val shape = RoundedCornerShape(15.dp)
    val containerColor = if (colorScheme.isDark) {
        colorScheme.surfaceVariant.copy(alpha = 0.58f)
    } else {
        colorScheme.surface.copy(alpha = 0.88f)
    }
    val borderColor = colorScheme.onSurfaceVariant.copy(
        alpha = if (colorScheme.isDark) 0.18f else 0.12f
    )
    val langCandidates = remember(dlsiteEditions) {
        dlsiteEditions
            .filter { it.lang in setOf("JPN", "CHI_HANS", "CHI_HANT") }
            .distinctBy { it.lang }
            .sortedWith(compareBy({ it.displayOrder }, { it.lang }))
    }
    val selectedLangLabel = remember(dlsiteSelectedLang, langCandidates) {
        langCandidates.firstOrNull { it.lang.equals(dlsiteSelectedLang, ignoreCase = true) }
            ?.let { dlsiteLanguageButtonLabel(it.lang) }
            ?: dlsiteLanguageButtonLabel(dlsiteSelectedLang)
    }
    var languageMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val secondaryAction = when (groupState) {
        AlbumHeaderButtonGroupState.Save -> Triple("保存", Icons.Rounded.Bookmark, saveEnabled)
        AlbumHeaderButtonGroupState.Lossless -> Triple("无损下载", Icons.Rounded.LibraryMusic, losslessDownloadEnabled)
        AlbumHeaderButtonGroupState.DownloadOnly -> null
    }
    val hasSecondaryAction = secondaryAction != null
    val downloadShape = if (hasSecondaryAction) {
        RoundedCornerShape(topStart = 11.dp, topEnd = 0.dp, bottomEnd = 0.dp, bottomStart = 11.dp)
    } else {
        RoundedCornerShape(11.dp)
    }
    val secondaryShape = RoundedCornerShape(
        topStart = 0.dp,
        topEnd = 11.dp,
        bottomEnd = 11.dp,
        bottomStart = 0.dp,
    )

    if (floating) {
        val floatingShape = RoundedCornerShape(10.dp)
        val floatingInnerStartShape = if (hasSecondaryAction) {
            RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
        } else {
            RoundedCornerShape(8.dp)
        }
        val floatingInnerEndShape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
        val floatingSegmentShape = RoundedCornerShape(6.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .widthIn(min = 160.dp, max = 220.dp)
                    .fillMaxHeight(),
                shape = floatingShape,
                color = containerColor,
                contentColor = colorScheme.textPrimary,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, borderColor),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AlbumHeaderBarAction(
                        label = "下载",
                        icon = Icons.Rounded.Download,
                        showLabel = true,
                        enabled = downloadEnabled,
                        style = AlbumHeaderActionStyle.Primary,
                        shape = floatingInnerStartShape,
                        onClick = onDownloadClick,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                    secondaryAction?.let { (label, icon, enabled) ->
                        AlbumHeaderBarAction(
                            label = label,
                            icon = icon,
                            showLabel = true,
                            enabled = enabled,
                            style = AlbumHeaderActionStyle.Secondary,
                            shape = floatingInnerEndShape,
                            onClick = when (groupState) {
                                AlbumHeaderButtonGroupState.Save -> onSaveClick
                                AlbumHeaderButtonGroupState.Lossless -> onLosslessDownloadClick
                                AlbumHeaderButtonGroupState.DownloadOnly -> ({})
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Surface(
                modifier = Modifier
                    .wrapContentWidth()
                    .fillMaxHeight(),
                shape = floatingShape,
                color = containerColor,
                contentColor = colorScheme.textPrimary,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, borderColor),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showGroupAction) {
                        AlbumHeaderBarAction(
                            label = "分组",
                            icon = Icons.Rounded.CreateNewFolder,
                            showLabel = true,
                            enabled = groupEnabled,
                            onClick = onGroupClick,
                            shape = floatingSegmentShape,
                            modifier = Modifier
                                .widthIn(min = 82.dp)
                                .fillMaxHeight(),
                        )
                    }

                    if (langCandidates.isNotEmpty()) {
                        if (showGroupAction) {
                            VerticalDivider(
                                modifier = Modifier.height(16.dp),
                                thickness = 0.5.dp,
                                color = borderColor,
                            )
                        }
                        val languageSelectable = langCandidates.size > 1
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                        ) {
                            AlbumHeaderBarAction(
                                label = selectedLangLabel,
                                icon = Icons.Rounded.Translate,
                                showLabel = true,
                                enabled = languageSelectable,
                                onClick = { languageMenuExpanded = true },
                                shape = floatingSegmentShape,
                                modifier = Modifier
                                    .widthIn(min = 88.dp)
                                    .fillMaxHeight(),
                            )
                            AlbumHeaderLanguageDropdownMenu(
                                expanded = languageMenuExpanded,
                                candidates = langCandidates,
                                selectedLang = dlsiteSelectedLang,
                                onDismiss = { languageMenuExpanded = false },
                                onSelect = { lang ->
                                    languageMenuExpanded = false
                                    onDlsiteLangSelected(lang)
                                }
                            )
                        }
                    }

                    if (showGroupAction || langCandidates.isNotEmpty()) {
                        VerticalDivider(
                            modifier = Modifier.height(16.dp),
                            thickness = 0.5.dp,
                            color = borderColor,
                        )
                    }
                    AlbumHeaderBarAction(
                        label = "DLsite",
                        showLabel = true,
                        enabled = dlsiteUrl.isNotBlank(),
                        onClick = {
                            if (dlsiteUrl.isNotBlank()) {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(dlsiteUrl))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        shape = floatingSegmentShape,
                        modifier = Modifier
                            .widthIn(min = 78.dp)
                            .fillMaxHeight(),
                    )

                    VerticalDivider(
                        modifier = Modifier.height(16.dp),
                        thickness = 0.5.dp,
                        color = borderColor,
                    )
                    AlbumHeaderBarAction(
                        label = "ONE",
                        showLabel = true,
                        enabled = asmrOneUrl.isNotBlank(),
                        onClick = {
                            if (asmrOneUrl.isNotBlank()) {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(asmrOneUrl))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        shape = floatingSegmentShape,
                        modifier = Modifier
                            .widthIn(min = 66.dp)
                            .fillMaxHeight(),
                    )
                }
            }
        }
        return
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
        shape = shape,
        color = containerColor,
        contentColor = colorScheme.textPrimary,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, borderColor),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AlbumHeaderBarAction(
                    label = "下载",
                    icon = Icons.Rounded.Download,
                    showLabel = true,
                    enabled = downloadEnabled,
                    style = AlbumHeaderActionStyle.Primary,
                    shape = downloadShape,
                    onClick = onDownloadClick,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )

                secondaryAction?.let { (label, icon, enabled) ->
                    AlbumHeaderBarAction(
                        label = label,
                        icon = icon,
                        showLabel = true,
                        enabled = enabled,
                        style = AlbumHeaderActionStyle.Secondary,
                        shape = secondaryShape,
                        onClick = when (groupState) {
                            AlbumHeaderButtonGroupState.Save -> onSaveClick
                            AlbumHeaderButtonGroupState.Lossless -> onLosslessDownloadClick
                            AlbumHeaderButtonGroupState.DownloadOnly -> ({})
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }

            VerticalDivider(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .height(20.dp),
                thickness = 0.5.dp,
                color = borderColor,
            )

            if (showGroupAction) {
                AlbumHeaderBarAction(
                    label = "分组",
                    icon = Icons.Rounded.CreateNewFolder,
                    showLabel = true,
                    enabled = groupEnabled,
                    onClick = onGroupClick,
                    modifier = Modifier
                        .width(70.dp)
                        .fillMaxHeight(),
                )
            }

            if (langCandidates.isNotEmpty()) {
                val languageSelectable = langCandidates.size > 1
                Box(
                    modifier = Modifier
                        .width(if (compact) 60.dp else 88.dp)
                        .fillMaxHeight()
                ) {
                    AlbumHeaderBarAction(
                        label = selectedLangLabel,
                        icon = Icons.Rounded.Translate,
                        showLabel = true,
                        enabled = languageSelectable,
                        onClick = { languageMenuExpanded = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                    AlbumHeaderLanguageDropdownMenu(
                        expanded = languageMenuExpanded,
                        candidates = langCandidates,
                        selectedLang = dlsiteSelectedLang,
                        onDismiss = { languageMenuExpanded = false },
                        onSelect = { lang ->
                            languageMenuExpanded = false
                            onDlsiteLangSelected(lang)
                        }
                    )
                }
            }

            listOf(
                Triple("DLsite", dlsiteUrl, 64.dp),
                Triple("ONE", asmrOneUrl, if (compact) 44.dp else 56.dp),
            ).forEach { (label, url, width) ->
                AlbumHeaderBarAction(
                    label = label,
                    showLabel = true,
                    enabled = url.isNotBlank(),
                    onClick = {
                        if (url.isNotBlank()) {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                    modifier = Modifier
                        .width(width)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

private enum class AlbumHeaderActionStyle {
    Standard,
    Primary,
    Secondary,
}

@Composable
private fun AlbumHeaderBarAction(
    label: String,
    showLabel: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: AlbumHeaderActionStyle = AlbumHeaderActionStyle.Standard,
    shape: RoundedCornerShape = RoundedCornerShape(11.dp),
) {
    val colorScheme = AsmrTheme.colorScheme
    val targetContentColor = when {
        !enabled -> colorScheme.textTertiary.copy(alpha = 0.72f)
        style == AlbumHeaderActionStyle.Primary -> colorScheme.onPrimary
        else -> colorScheme.primaryStrong
    }
    val targetContainerColor = when {
        !enabled -> Color.Transparent
        style == AlbumHeaderActionStyle.Primary -> colorScheme.primaryStrong
        style == AlbumHeaderActionStyle.Secondary -> colorScheme.primary.copy(
            alpha = if (colorScheme.isDark) 0.28f else 0.15f
        )
        else -> Color.Transparent
    }
    // 网络数据到达后，下载/保存按钮从禁用态切到可用态时用约 800ms 渐入，避免状态瞬间“刷新”出来。
    val contentColor by animateColorAsState(
        targetValue = targetContentColor,
        animationSpec = tween(durationMillis = AlbumHeaderActionStateTransitionMillis),
        label = "albumHeaderBarActionContent"
    )
    val containerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = tween(durationMillis = AlbumHeaderActionStateTransitionMillis),
        label = "albumHeaderBarActionContainer"
    )

    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Row(
            modifier = modifier
                .clip(shape)
                .background(containerColor, shape)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(horizontal = if (showLabel && icon != null) 7.dp else 5.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = if (showLabel) null else label,
                    tint = contentColor,
                    modifier = Modifier.size(17.dp),
                )
            }
            if (showLabel) {
                if (icon != null) Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (style == AlbumHeaderActionStyle.Standard) {
                            FontWeight.Medium
                        } else {
                            FontWeight.SemiBold
                        },
                    ),
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun dlsiteLanguageButtonLabel(lang: String): String {
    return when (lang.trim().uppercase()) {
        "CHI_HANS" -> "简中"
        "CHI_HANT" -> "繁中"
        "JPN" -> "日语"
        else -> lang.trim().ifBlank { "日语" }
    }
}

@Composable
private fun AlbumHeaderLanguageDropdownMenu(
    expanded: Boolean,
    candidates: List<DlsiteLanguageEdition>,
    selectedLang: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val materialColorScheme = MaterialTheme.colorScheme
    val menuContainer = if (colorScheme.isDark) {
        colorScheme.surfaceVariant.copy(alpha = 0.98f)
    } else {
        colorScheme.surface.copy(alpha = 0.98f)
    }
    MaterialTheme(
        colorScheme = materialColorScheme.copy(
            surface = menuContainer,
            onSurface = colorScheme.textPrimary,
            surfaceVariant = colorScheme.primary.copy(alpha = if (colorScheme.isDark) 0.22f else 0.12f),
            onSurfaceVariant = colorScheme.textSecondary
        )
    ) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
            modifier = Modifier
                .background(menuContainer, RoundedCornerShape(14.dp))
                .border(
                    width = 0.5.dp,
                    color = colorScheme.primary.copy(alpha = if (colorScheme.isDark) 0.28f else 0.20f),
                    shape = RoundedCornerShape(14.dp)
                )
        ) {
            candidates.forEachIndexed { index, edition ->
                val selected = edition.lang.equals(selectedLang, ignoreCase = true)
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        thickness = 0.5.dp,
                        color = colorScheme.onSurfaceVariant.copy(alpha = if (colorScheme.isDark) 0.22f else 0.16f)
                    )
                }
                DropdownMenuItem(
                    text = {
                        Text(
                            text = dlsiteLanguageButtonLabel(edition.lang),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) colorScheme.primary else colorScheme.textPrimary,
                            maxLines = 1
                        )
                    },
                    onClick = { onSelect(edition.lang) },
                    leadingIcon = {
                        Icon(
                            imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.Translate,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (selected) colorScheme.primary else colorScheme.textSecondary
                        )
                    },
                    trailingIcon = {
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .size(width = 22.dp, height = 6.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(colorScheme.primary.copy(alpha = if (colorScheme.isDark) 0.46f else 0.30f))
                            )
                        }
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = colorScheme.textPrimary,
                        leadingIconColor = colorScheme.textSecondary,
                        trailingIconColor = colorScheme.primary,
                        disabledTextColor = colorScheme.textTertiary,
                        disabledLeadingIconColor = colorScheme.textTertiary,
                        disabledTrailingIconColor = colorScheme.textTertiary
                    )
                )
            }
        }
    }
}

@Composable
private fun AlbumHeaderLateMetaReveal(
    revealKey: String,
    hasContent: Boolean,
    presentInitially: Boolean,
    delayMillis: Int,
    animationsEnabled: Boolean,
    content: @Composable () -> Unit
) {
    val shouldAnimate = shouldAnimateAlbumHeaderMetaReveal(
        presentInitially = presentInitially,
        hasContent = hasContent,
        animationsEnabled = animationsEnabled
    )
    var visible by remember(revealKey) {
        mutableStateOf(presentInitially && hasContent)
    }
    LaunchedEffect(revealKey, hasContent, shouldAnimate) {
        when {
            !hasContent -> visible = false
            !shouldAnimate -> visible = true
            else -> {
                visible = false
                if (delayMillis > 0) delay(delayMillis.toLong())
                withFrameNanos { }
                visible = true
            }
        }
    }
    AnimatedVisibility(
        visible = visible && hasContent,
        enter = fadeIn(animationSpec = AlbumHeaderEnterTweenSpec) + expandVertically(
            animationSpec = AlbumHeaderExpandTweenSpec,
            expandFrom = Alignment.Top
        ) + slideInVertically(
            animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
            initialOffsetY = { fullHeight -> -(fullHeight * 0.55f).roundToInt() }
        ),
        exit = fadeOut(animationSpec = tween(durationMillis = 120)) + shrinkVertically(
            animationSpec = tween(durationMillis = 160, easing = FastOutLinearInEasing),
            shrinkTowards = Alignment.Top
        )
    ) { content() }
}

