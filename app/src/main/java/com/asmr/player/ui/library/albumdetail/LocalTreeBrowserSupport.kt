package com.asmr.player.ui.library.albumdetail

import java.util.ArrayDeque
import com.asmr.player.data.local.tree.LocalTreeIndex
import com.asmr.player.data.local.tree.LocalTreeNode
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.Track
import com.asmr.player.domain.model.TreeFileType
import com.asmr.player.subtitle.SubtitleGenerationPolicy
import com.asmr.player.util.SmartSortKey
import com.asmr.player.util.isOnlineTrackPath

internal fun findLocalTreeNode(root: LocalTreeNode, folderPath: String): LocalTreeNode? {
    val normalized = folderPath.trim().trimStart('/').trimEnd('/')
    if (normalized.isBlank()) return root
    var cur: LocalTreeNode = root
    val segments = normalized.split('/').filter { it.isNotBlank() }
    for (seg in segments) {
        val next = cur.children[seg] ?: return null
        cur = next
    }
    return cur
}

private fun collectLocalTreeLeafNodes(root: LocalTreeNode): List<LocalTreeNode> {
    val leaves = mutableListOf<LocalTreeNode>()
    val pending = ArrayDeque<LocalTreeNode>()
    pending.add(root)
    while (pending.isNotEmpty()) {
        val node = pending.removeLast()
        if (node.children.isEmpty()) {
            if (node.absolutePath != null) leaves += node
        } else {
            node.children.values.forEach(pending::addLast)
        }
    }
    return leaves
}

internal fun siblingAudioTracksForEntry(index: LocalTreeIndex, entryPath: String): List<Track> {
    val folderPath = entryPath.substringBeforeLast('/', "")
    val node = findLocalTreeNode(index.root, folderPath) ?: index.root
    return node.children.values
        .asSequence()
        .filter { it.children.isEmpty() && it.absolutePath != null && it.fileType == TreeFileType.Audio && it.track != null }
        .sortedBy { SmartSortKey.of(it.name) }
        .mapNotNull { it.track }
        .toList()
}

internal fun siblingPlayableNodesForEntry(index: LocalTreeIndex, entryPath: String): List<LocalTreeNode> {
    val folderPath = entryPath.substringBeforeLast('/', "")
    val node = findLocalTreeNode(index.root, folderPath) ?: index.root
    return node.children.values
        .asSequence()
        .filter { it.children.isEmpty() && it.absolutePath != null && (it.fileType == TreeFileType.Audio || it.fileType == TreeFileType.Video) }
        .filter { it.fileType != TreeFileType.Audio || it.track != null }
        .sortedBy { SmartSortKey.of(it.name) }
        .toList()
}

internal fun isSupportedSubtitleGenerationAudioName(name: String): Boolean {
    return SubtitleGenerationPolicy.supportsFileName(name)
}

internal fun subtitleGenerationTrackForFile(
    file: DirectoryFileItem,
    unavailableTrackIds: Set<Long>
): Track? {
    val track = file.track ?: return null
    return track.takeIf {
        file.fileType == TreeFileType.Audio &&
            isSupportedSubtitleGenerationAudioName(file.path) &&
            it.id > 0L &&
            !file.isOnline &&
            file.sizeSource is FileSizeSource.Local &&
            !isOnlineTrackPath(it.path) &&
            it.id !in unavailableTrackIds
    }
}

internal fun collectSubtitleGenerationTracks(
    index: LocalTreeIndex,
    currentPath: String,
    unavailableTrackIds: Set<Long>
): List<Track> {
    val currentNode = findLocalTreeNode(index.root, currentPath) ?: return emptyList()
    val tracks = mutableListOf<Track>()

    fun collect(node: LocalTreeNode) {
        if (node.children.isEmpty()) {
            val track = node.track
            val absolutePath = node.absolutePath.orEmpty()
            if (
                node.fileType == TreeFileType.Audio &&
                isSupportedSubtitleGenerationAudioName(node.name) &&
                track != null &&
                track.id > 0L &&
                !isOnlineTrackPath(track.path) &&
                !absolutePath.startsWith("http", ignoreCase = true) &&
                track.id !in unavailableTrackIds
            ) {
                tracks += track
            }
            return
        }
        node.children.values
            .sortedBy { SmartSortKey.of(it.name) }
            .forEach(::collect)
    }

    collect(currentNode)
    return tracks.distinctBy { it.id }
}

internal fun subtitleTranslationTrackForFile(
    file: DirectoryFileItem,
    localSubtitleTrackIds: Set<Long>
): Track? {
    val track = file.track ?: return null
    return track.takeIf {
        file.fileType == TreeFileType.Audio &&
            it.id > 0L &&
            it.id in localSubtitleTrackIds &&
            !file.isOnline &&
            file.sizeSource is FileSizeSource.Local &&
            !isOnlineTrackPath(it.path)
    }
}

internal fun buildLocalDirectoryBrowser(
    index: LocalTreeIndex,
    currentPath: String,
    album: Album,
    shouldShowSubtitleStamp: (Track?) -> Boolean
): DirectoryBrowserResult {
    val normalizedPath = currentPath.trim().trim('/')
    val currentNode = findLocalTreeNode(index.root, normalizedPath) ?: index.root
    val folders = currentNode.children.values
        .asSequence()
        .filter { it.children.isNotEmpty() }
        .sortedBy { SmartSortKey.of(it.name) }
        .map { child ->
            val descendantLeaves = collectLocalTreeLeafNodes(child)
            DirectoryFolderItem(
                path = child.path,
                title = child.name,
                descendantTrackIds = descendantLeaves
                    .mapNotNull { it.track?.id?.takeIf { id -> id > 0L } }
                    .distinct(),
                hasLocalContent = descendantLeaves.any { node ->
                    node.absolutePath?.startsWith("http", ignoreCase = true) == false
                },
            )
        }
        .toList()
    val files = currentNode.children.values
        .asSequence()
        .filter { it.children.isEmpty() && it.absolutePath != null }
        .sortedBy { SmartSortKey.of(it.name) }
        .mapNotNull { child ->
            val absolutePath = child.absolutePath ?: return@mapNotNull null
            val isRemoteResource = absolutePath.startsWith("http", ignoreCase = true)
            val displayTitle = child.track?.title?.ifBlank { child.name.substringBeforeLast('.') }
                ?: child.name.substringBeforeLast('.')
            val playlistTarget = when (child.fileType) {
                TreeFileType.Audio -> child.track?.let { PlaylistAddTarget.fromTrack(album, it) }
                TreeFileType.Video -> PlaylistAddTarget.fromVideo(album, displayTitle, absolutePath)
                else -> null
            }
            DirectoryFileItem(
                path = child.path,
                title = displayTitle,
                fileType = child.fileType,
                isPlayable = child.track != null || child.fileType == TreeFileType.Video,
                isOnline = isOnlineDirectoryAudio(child.fileType, absolutePath, child.track),
                durationSeconds = child.track?.duration?.takeIf { it > 0.0 },
                sizeSource = if (isRemoteResource) {
                    FileSizeSource.Remote(absolutePath)
                } else {
                    FileSizeSource.Local(path = absolutePath, sizeBytes = child.sizeBytes)
                },
                absolutePath = absolutePath,
                url = absolutePath,
                track = child.track,
                thumbnailModel = if (child.fileType == TreeFileType.Image) absolutePath else null,
                playlistTarget = playlistTarget,
                showSubtitleStamp = shouldShowSubtitleStamp(child.track)
            )
        }
        .toList()
    return DirectoryBrowserResult(
        currentPath = normalizedPath,
        breadcrumbs = buildBreadcrumbSegments(normalizedPath),
        folders = folders,
        files = files
    )
}

internal fun canSetDirectoryImageAsLocalCover(file: DirectoryFileItem): Boolean {
    return file.fileType == TreeFileType.Image &&
        !file.absolutePath.startsWith("http", ignoreCase = true)
}

internal fun downloadableOnlineAudioTrack(file: DirectoryFileItem): Track? {
    if (file.fileType != TreeFileType.Audio || !file.isOnline) return null
    return file.track?.takeIf { isOnlineTrackPath(it.path) }
}


internal fun flattenLocalTreeIndex(
    index: LocalTreeIndex,
    expanded: Set<String>
): LocalTreeUiResult {
    fun nodeSortKey(n: LocalTreeNode): SmartSortKey = SmartSortKey.of(n.name)
    val out = mutableListOf<LocalTreeUiEntry>()

    fun flatten(node: LocalTreeNode, depth: Int) {
        val folders = node.children.values.filter { it.children.isNotEmpty() }.sortedBy(::nodeSortKey)
        val files = node.children.values.filter { it.children.isEmpty() && it.absolutePath != null }.sortedBy(::nodeSortKey)

        folders.forEach { child ->
            out.add(LocalTreeUiEntry.Folder(path = child.path, title = child.name, depth = depth))
            if (expanded.contains(child.path)) {
                flatten(child, depth + 1)
            }
        }
        files.forEach { child ->
            val title = child.track?.title?.ifBlank { child.name.substringBeforeLast('.') }
                ?: child.name.substringBeforeLast('.')
            out.add(
                LocalTreeUiEntry.File(
                    path = child.path,
                    title = title,
                    depth = depth,
                    absolutePath = child.absolutePath ?: return@forEach,
                    fileType = child.fileType,
                    track = child.track
                )
            )
        }
    }

    flatten(index.root, 0)
    return LocalTreeUiResult(entries = out)
}

