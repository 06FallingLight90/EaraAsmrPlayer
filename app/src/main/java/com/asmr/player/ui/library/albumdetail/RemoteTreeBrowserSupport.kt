package com.asmr.player.ui.library.albumdetail

import com.asmr.player.data.local.tree.sanitizeFolderName
import com.asmr.player.data.remote.api.AsmrOneTrackNodeResponse
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.RemoteSubtitleSource
import com.asmr.player.domain.model.Track
import com.asmr.player.domain.model.TreeFileType
import com.asmr.player.domain.model.treeFileTypeForNode
import com.asmr.player.util.SmartSortKey
import com.asmr.player.util.SubtitleMatchSupport

internal class RemoteTreeNode(
    val name: String,
    val path: String,
    val children: MutableMap<String, RemoteTreeNode> = linkedMapOf(),
    var fileType: TreeFileType = TreeFileType.Other,
    var url: String = "",
    var durationSeconds: Double? = null,
    var subtitleSources: List<RemoteSubtitleSource> = emptyList(),
    var playlistTarget: PlaylistAddTarget? = null,
    var dlsitePlayImageCrypt: Boolean = false,
    var dlsitePlayImageWidth: Int? = null,
    var dlsitePlayImageHeight: Int? = null,
    var dlsitePlayOptimizedName: String? = null
)

internal data class RemoteTreeIndex(
    val root: RemoteTreeNode
)

internal fun findRemoteTreeNode(root: RemoteTreeNode, folderPath: String): RemoteTreeNode? {
    val normalized = folderPath.trim().trim('/')
    if (normalized.isBlank()) return root
    var current = root
    normalized.split('/').filter { it.isNotBlank() }.forEach { segment ->
        current = current.children[segment] ?: return null
    }
    return current
}

internal fun buildRemoteTreeIndex(
    tree: List<AsmrOneTrackNodeResponse>,
    album: Album
): RemoteTreeIndex {
    val root = RemoteTreeNode(name = "", path = "")
    val subtitleExts = setOf("lrc", "srt", "vtt")

    data class LeafFile(
        val rawTitle: String,
        val safeTitle: String,
        val url: String,
        val duration: Double?,
        val fileType: TreeFileType,
        val dlsitePlayImageCrypt: Boolean,
        val dlsitePlayImageWidth: Int?,
        val dlsitePlayImageHeight: Int?,
        val dlsitePlayOptimizedName: String?
    ) {
        val ext: String = rawTitle.substringAfterLast('.', "").lowercase()
        val baseName: String = rawTitle.substringBeforeLast('.')
        val displayTitle: String = sanitizeFolderName(baseName).ifBlank { safeTitle.substringBeforeLast('.') }
    }

    val subtitleCandidates = collectSubtitleCandidates(tree, subtitleExts).map { entry ->
        entry.candidate to LeafFile(
            rawTitle = entry.rawTitle,
            safeTitle = entry.safeTitle,
            url = entry.url,
            duration = entry.duration,
            fileType = treeFileTypeForNode(entry.rawTitle, entry.url, entry.node.type),
            dlsitePlayImageCrypt = entry.node.dlsitePlayImageCrypt,
            dlsitePlayImageWidth = entry.node.dlsitePlayImageWidth,
            dlsitePlayImageHeight = entry.node.dlsitePlayImageHeight,
            dlsitePlayOptimizedName = entry.node.dlsitePlayOptimizedName
        )
    }

    fun walk(
        nodes: List<AsmrOneTrackNodeResponse>,
        parentNode: RemoteTreeNode,
        parentPath: String
    ) {
        val leafFiles = nodes.mapNotNull { node ->
            val children = node.children.orEmpty()
            val url = node.mediaDownloadUrl ?: node.streamUrl
            if (!url.isNullOrBlank() && children.isEmpty()) {
                val rawTitle = node.title?.trim().orEmpty().ifBlank { "item" }
                val safeTitle = sanitizeFolderName(rawTitle)
                LeafFile(
                    rawTitle = rawTitle,
                    safeTitle = safeTitle,
                    url = url,
                    duration = node.duration,
                    fileType = treeFileTypeForNode(rawTitle, url, node.type),
                    dlsitePlayImageCrypt = node.dlsitePlayImageCrypt,
                    dlsitePlayImageWidth = node.dlsitePlayImageWidth,
                    dlsitePlayImageHeight = node.dlsitePlayImageHeight,
                    dlsitePlayOptimizedName = node.dlsitePlayOptimizedName
                )
            } else {
                null
            }
        }

        leafFiles.forEach { leaf ->
            if (leaf.fileType == TreeFileType.Other || leaf.fileType == TreeFileType.Subtitle) return@forEach
            val path = if (parentPath.isBlank()) leaf.safeTitle else "$parentPath/${leaf.safeTitle}"
            val child = parentNode.children.getOrPut(leaf.safeTitle) {
                RemoteTreeNode(name = leaf.safeTitle, path = path)
            }
            val subtitleSources = when (leaf.fileType) {
                TreeFileType.Audio, TreeFileType.Video -> {
                    val matched = SubtitleMatchSupport.matchBest(path.substringBeforeLast('.'), subtitleCandidates.map { it.first })
                    if (matched != null) {
                        subtitleCandidates.firstOrNull { it.first.sourceRef == matched.sourceRef }?.second?.let { subtitleLeaf ->
                            listOf(
                                RemoteSubtitleSource(
                                    url = subtitleLeaf.url,
                                    language = matched.language,
                                    ext = subtitleLeaf.ext.ifBlank { "vtt" }
                                )
                            )
                        }.orEmpty()
                    } else {
                        emptyList()
                    }
                }
                else -> emptyList()
            }
            val playlistTarget = when (leaf.fileType) {
                TreeFileType.Audio -> PlaylistAddTarget.fromTrack(
                    album = album,
                    track = Track(
                        albumId = album.id,
                        title = leaf.displayTitle,
                        path = leaf.url,
                        duration = leaf.duration ?: 0.0,
                        group = path.substringBeforeLast('/', "").substringAfterLast('/', ""),
                        lyricsRelativePathNoExt = path.substringBeforeLast('.')
                    )
                ).copy(remoteSubtitleSources = subtitleSources)
                TreeFileType.Video -> PlaylistAddTarget.fromVideo(album, leaf.displayTitle, leaf.url)
                else -> null
            }
            child.fileType = leaf.fileType
            child.url = leaf.url
            child.durationSeconds = leaf.duration
            child.subtitleSources = subtitleSources
            child.playlistTarget = playlistTarget
            child.dlsitePlayImageCrypt = leaf.dlsitePlayImageCrypt
            child.dlsitePlayImageWidth = leaf.dlsitePlayImageWidth
            child.dlsitePlayImageHeight = leaf.dlsitePlayImageHeight
            child.dlsitePlayOptimizedName = leaf.dlsitePlayOptimizedName
        }

        nodes.forEach { node ->
            val children = node.children.orEmpty()
            if (children.isEmpty()) return@forEach
            val rawTitle = node.title?.trim().orEmpty().ifBlank { "item" }
            val safeTitle = sanitizeFolderName(rawTitle)
            val path = if (parentPath.isBlank()) safeTitle else "$parentPath/$safeTitle"
            val childNode = parentNode.children.getOrPut(safeTitle) {
                RemoteTreeNode(name = safeTitle, path = path)
            }
            walk(children, childNode, path)
        }
    }

    walk(tree, root, "")
    return RemoteTreeIndex(root = root)
}

private fun RemoteTreeNode.toDirectoryFileItem(): DirectoryFileItem {
    return DirectoryFileItem(
        path = path,
        title = name.substringBeforeLast('.'),
        fileType = fileType,
        isPlayable = fileType == TreeFileType.Audio || fileType == TreeFileType.Video,
        isOnline = true,
        durationSeconds = durationSeconds,
        sizeSource = if (url.isNotBlank()) FileSizeSource.Remote(url) else FileSizeSource.None,
        absolutePath = url,
        url = url,
        playlistTarget = playlistTarget,
        subtitleSources = subtitleSources,
        showSubtitleStamp = subtitleSources.isNotEmpty(),
        dlsitePlayImageCrypt = dlsitePlayImageCrypt,
        dlsitePlayImageWidth = dlsitePlayImageWidth,
        dlsitePlayImageHeight = dlsitePlayImageHeight,
        dlsitePlayOptimizedName = dlsitePlayOptimizedName
    )
}

internal fun collectRemoteTreeImageFiles(index: RemoteTreeIndex): List<DirectoryFileItem> {
    val images = mutableListOf<DirectoryFileItem>()

    fun collect(node: RemoteTreeNode) {
        val children = node.children.values
        children.asSequence()
            .filter { it.children.isNotEmpty() }
            .sortedBy { SmartSortKey.of(it.name) }
            .forEach(::collect)
        children.asSequence()
            .filter { child ->
                child.children.isEmpty() &&
                    child.fileType == TreeFileType.Image &&
                    child.url.isNotBlank()
            }
            .sortedBy { SmartSortKey.of(it.name) }
            .mapTo(images) { it.toDirectoryFileItem() }
    }

    collect(index.root)
    return images
}

internal fun buildRemoteDirectoryBrowser(
    index: RemoteTreeIndex,
    currentPath: String
): DirectoryBrowserResult {
    val normalizedPath = currentPath.trim().trim('/')
    val currentNode = findRemoteTreeNode(index.root, normalizedPath) ?: index.root
    val folders = currentNode.children.values
        .asSequence()
        .filter { it.children.isNotEmpty() }
        .sortedBy { SmartSortKey.of(it.name) }
        .map { child ->
            DirectoryFolderItem(
                path = child.path,
                title = child.name
            )
        }
        .toList()
    val files = currentNode.children.values
        .asSequence()
        .filter { it.children.isEmpty() && it.url.isNotBlank() && it.fileType != TreeFileType.Subtitle && it.fileType != TreeFileType.Other }
        .sortedBy { SmartSortKey.of(it.name) }
        .map(RemoteTreeNode::toDirectoryFileItem)
        .toList()
    return DirectoryBrowserResult(
        currentPath = normalizedPath,
        breadcrumbs = buildBreadcrumbSegments(normalizedPath),
        folders = folders,
        files = files
    )
}


internal data class AsmrOneLeafUi(
    val relativePath: String,
    val title: String,
    val url: String,
    val duration: Double?,
    val subtitles: List<com.asmr.player.domain.model.RemoteSubtitleSource>
) {
fun toTrack(): Track {
        val normalizedRelativePath = relativePath.replace('\\', '/').trim().trimStart('/')
        val group = normalizedRelativePath.substringBeforeLast('/', "").substringAfterLast('/', "")
        return Track(
            albumId = 0,
            title = title,
            path = url,
            duration = duration ?: 0.0,
            group = group,
            lyricsRelativePathNoExt = normalizedRelativePath.substringBeforeLast('.'),
            remoteSubtitleSources = subtitles
        )
    }
}

internal fun flattenAsmrOneTracksForUi(tree: List<AsmrOneTrackNodeResponse>): List<AsmrOneLeafUi> {
    val out = mutableListOf<AsmrOneLeafUi>()

    val audioExts = setOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "opus")
    val subtitleExts = setOf("lrc", "srt", "vtt")

    data class LeafFile(
        val rawTitle: String,
        val safeTitle: String,
        val url: String,
        val duration: Double?
    ) {
        val ext: String = rawTitle.substringAfterLast('.', "").lowercase()
        val baseName: String = rawTitle.substringBeforeLast('.')
    }

    val subtitleCandidates = collectSubtitleCandidates(tree, subtitleExts).map { entry ->
        entry.candidate to LeafFile(
            rawTitle = entry.rawTitle,
            safeTitle = entry.safeTitle,
            url = entry.url,
            duration = entry.duration
        )
    }

    fun walk(nodes: List<AsmrOneTrackNodeResponse>, parentPath: String) {
        val leaves = nodes.mapNotNull { node ->
            val children = node.children.orEmpty()
            val url = node.mediaDownloadUrl ?: node.streamUrl
            if (!url.isNullOrBlank() && children.isEmpty()) {
                val rawTitle = node.title?.trim().orEmpty().ifBlank { "item" }
                val safeTitle = sanitizeFolderName(rawTitle)
                LeafFile(rawTitle = rawTitle, safeTitle = safeTitle, url = url, duration = node.duration)
            } else null
        }

        leaves.filter { it.ext.isBlank() || audioExts.contains(it.ext) }.forEach { leaf ->
            val path = if (parentPath.isBlank()) leaf.safeTitle else "$parentPath/${leaf.safeTitle}"
            val displayTitle = sanitizeFolderName(leaf.baseName).ifBlank { leaf.safeTitle }
            val matched = SubtitleMatchSupport.matchBest(path.substringBeforeLast('.'), subtitleCandidates.map { it.first })
            val subs = if (matched != null) {
                subtitleCandidates.firstOrNull { it.first.sourceRef == matched.sourceRef }?.second?.let { subtitleLeaf ->
                    listOf(com.asmr.player.domain.model.RemoteSubtitleSource(url = subtitleLeaf.url, language = matched.language, ext = subtitleLeaf.ext))
                }.orEmpty()
            } else {
                emptyList()
            }
            out.add(
                AsmrOneLeafUi(
                    relativePath = path,
                    title = displayTitle,
                    url = leaf.url,
                    duration = leaf.duration,
                    subtitles = subs
                )
            )
        }

        nodes.forEach { node ->
            val children = node.children.orEmpty()
            if (children.isEmpty()) return@forEach
            val title = node.title?.trim().orEmpty().ifBlank { "item" }
            val safeTitle = sanitizeFolderName(title)
            val path = if (parentPath.isBlank()) safeTitle else "$parentPath/$safeTitle"
            walk(children, path)
        }
    }

    walk(tree, "")
    return out
}

internal fun flattenAsmrOneTreeForUi(
    tree: List<AsmrOneTrackNodeResponse>,
    expanded: Set<String>
): AsmrTreeUiResult {
    val out = mutableListOf<AsmrTreeUiEntry>()

    data class FolderStats(
        var audioCount: Int = 0,
        var videoCount: Int = 0,
        var hasWav: Boolean = false,
        var hasMp4: Boolean = false
    )
    val folderStats = linkedMapOf<String, FolderStats>()
    fun updateFolderStats(parentPath: String, type: TreeFileType, extLower: String) {
        if (parentPath.isBlank()) return
        folderPathPrefixes(parentPath).forEach { folder ->
            val st = folderStats.getOrPut(folder) { FolderStats() }
            when (type) {
                TreeFileType.Audio -> {
                    st.audioCount += 1
                    if (extLower == "wav") st.hasWav = true
                }
                TreeFileType.Video -> {
                    st.videoCount += 1
                    if (extLower == "mp4") st.hasMp4 = true
                }
                else -> Unit
            }
        }
    }

    fun chooseRecommendedExpand(): String? {
        val entries = folderStats.entries.filter { it.value.audioCount > 0 || it.value.videoCount > 0 }
        if (entries.isEmpty()) return null

        fun bestAudio(): Map.Entry<String, FolderStats>? {
            val audioEntries = entries.filter { it.value.audioCount > 0 }
            if (audioEntries.isEmpty()) return null
            val maxAudio = audioEntries.maxOf { it.value.audioCount }
            val threshold = maxOf(1, (maxAudio * 0.7f).toInt())
            val candidates = audioEntries.filter { it.value.audioCount >= threshold }.ifEmpty { audioEntries }
            return candidates
                .sortedWith(
                    compareByDescending<Map.Entry<String, FolderStats>> { it.key.count { ch -> ch == '/' } }
                        .thenByDescending { it.value.audioCount }
                        .thenByDescending { it.value.hasWav }
                        .thenBy { it.key }
                )
                .firstOrNull()
        }

        fun bestVideo(): Map.Entry<String, FolderStats>? {
            val videoEntries = entries.filter { it.value.videoCount > 0 }
            if (videoEntries.isEmpty()) return null
            val maxVideo = videoEntries.maxOf { it.value.videoCount }
            val threshold = maxOf(1, (maxVideo * 0.7f).toInt())
            val candidates = videoEntries.filter { it.value.videoCount >= threshold }.ifEmpty { videoEntries }
            return candidates
                .sortedWith(
                    compareByDescending<Map.Entry<String, FolderStats>> { it.key.count { ch -> ch == '/' } }
                        .thenByDescending { it.value.videoCount }
                        .thenByDescending { it.value.hasMp4 }
                        .thenBy { it.key }
                )
                .firstOrNull()
        }

        val a = bestAudio()
        val v = bestVideo()
        val aCount = a?.value?.audioCount ?: 0
        val vCount = v?.value?.videoCount ?: 0
        return when {
            vCount > aCount -> v?.key
            aCount > vCount -> a?.key
            else -> v?.key ?: a?.key
        }
    }

    fun collectFolderStatsFromFullTree() {
        fun walkAll(nodes: List<AsmrOneTrackNodeResponse>, parentPath: String) {
            nodes.forEach { node ->
                val title = node.title?.trim().orEmpty().ifBlank { "item" }
                val safeTitle = sanitizeFolderName(title)
                val path = if (parentPath.isBlank()) safeTitle else "$parentPath/$safeTitle"
                val children = node.children.orEmpty()
                val url = node.mediaDownloadUrl ?: node.streamUrl
                if (children.isEmpty()) {
                    val type = treeFileTypeForNode(title, url, node.type)
                    if (type == TreeFileType.Other || type == TreeFileType.Subtitle) return@forEach
                    val extLower = title.substringAfterLast('.', "").lowercase()
                    updateFolderStats(parentPath = parentPath, type = type, extLower = extLower)
                } else {
                    walkAll(children, path)
                }
            }
        }
        walkAll(tree, "")
    }

    fun walk(nodes: List<AsmrOneTrackNodeResponse>, parentPath: String, depth: Int) {
        nodes.forEach { node ->
            val title = node.title?.trim().orEmpty().ifBlank { "item" }
            val safeTitle = sanitizeFolderName(title)
            val path = if (parentPath.isBlank()) safeTitle else "$parentPath/$safeTitle"
            val children = node.children.orEmpty()
            val url = node.mediaDownloadUrl ?: node.streamUrl
            if (children.isEmpty()) {
                val type = treeFileTypeForNode(title, url, node.type)
                if (type == TreeFileType.Other || type == TreeFileType.Subtitle) return@forEach
                out.add(
                    AsmrTreeUiEntry.File(
                        path = path,
                        title = safeTitle.substringBeforeLast('.'),
                        depth = depth,
                        fileType = type,
                        isPlayable = type == TreeFileType.Audio && !url.isNullOrBlank(),
                        url = url
                    )
                )
            } else {
                out.add(AsmrTreeUiEntry.Folder(path = path, title = safeTitle, depth = depth))
                if (expanded.contains(path)) {
                    walk(children, path, depth + 1)
                }
            }
        }
    }
    collectFolderStatsFromFullTree()
    walk(tree, "", 0)
    return AsmrTreeUiResult(entries = out)
}

