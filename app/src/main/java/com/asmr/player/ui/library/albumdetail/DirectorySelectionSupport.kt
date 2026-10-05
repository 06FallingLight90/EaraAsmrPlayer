package com.asmr.player.ui.library.albumdetail

import com.asmr.player.data.local.tree.LocalTreeIndex
import com.asmr.player.data.local.tree.LocalTreeNode
import com.asmr.player.domain.model.Track
import com.asmr.player.domain.model.TreeFileType
import com.asmr.player.domain.model.fileExtensionFromName
import com.asmr.player.domain.model.treeFileTypeForName
import com.asmr.player.domain.model.treeFileTypeForNode
import com.asmr.player.util.TrackKeyNormalizer
import com.asmr.player.util.isOnlineTrackPath

internal fun collectLocalSelectionFiles(index: LocalTreeIndex): List<LocalSelectionFileRef> {
    val files = mutableListOf<LocalSelectionFileRef>()

    fun collect(node: LocalTreeNode) {
        if (node.children.isEmpty()) {
            val absolutePath = node.absolutePath?.trim().orEmpty()
            if (node.path.isNotBlank() && absolutePath.isNotBlank()) {
                files += LocalSelectionFileRef(
                    relativePath = node.path,
                    absolutePath = absolutePath,
                    track = node.track
                )
            }
            return
        }
        node.children.values.forEach(::collect)
    }

    collect(index.root)
    return files
}

internal fun resolveExistingRemoteSelectionPaths(
    remoteFiles: List<RemoteSelectionFileRef>,
    localFiles: List<LocalSelectionFileRef>,
    includeOnlineFiles: Boolean
): Set<String> {
    data class MatchCandidate(
        val normalizedRelativePath: String,
        val canonicalUrl: String,
        val fileName: String,
        val trackKey: String,
        val trackKeyWithoutGroup: String,
        val extension: String,
        val fileType: TreeFileType
    )

    data class RemoteCandidate(
        val file: RemoteSelectionFileRef,
        val normalizedRelativePath: String,
        val canonicalUrl: String,
        val fileName: String,
        val trackKey: String,
        val trackKeyWithoutGroup: String,
        val extension: String,
        val fileType: TreeFileType
    )

    fun normalizeTreeRelativePath(path: String): String {
        return path.replace('\\', '/').trim().trim('/').lowercase()
    }

    fun canonicalUrl(value: String): String {
        val trimmed = value.trim()
        if (!isOnlineTrackPath(trimmed)) return ""
        return trimmed.substringBefore('#').substringBefore('?')
    }

    fun fileName(path: String): String {
        return normalizeTreeRelativePath(path).substringAfterLast('/')
    }

    fun remoteTrackKey(path: String, includeGroup: Boolean): String {
        val normalized = path.replace('\\', '/').trim().trim('/')
        val title = normalized.substringAfterLast('/').substringBeforeLast('.')
        val group = if (includeGroup) normalized.substringBeforeLast('/', "") else ""
        return TrackKeyNormalizer.buildKey(title, group, null)
    }

    fun resolvedExtension(relativePath: String, sourcePath: String): String {
        return fileExtensionFromName(relativePath)
            .ifBlank { fileExtensionFromName(sourcePath) }
    }

    fun resolvedFileType(relativePath: String, sourcePath: String): TreeFileType {
        return treeFileTypeForName(relativePath).takeIf { it != TreeFileType.Other }
            ?: treeFileTypeForName(sourcePath)
    }

    val candidates = localFiles.asSequence()
        .filter { local ->
            includeOnlineFiles || !isOnlineTrackPath(local.track?.path.orEmpty().ifBlank { local.absolutePath })
        }
        .map { local ->
            val track = local.track
            val fallbackGroup = local.relativePath.replace('\\', '/').substringBeforeLast('/', "")
            val sourcePath = track?.path.orEmpty().ifBlank { local.absolutePath }
            MatchCandidate(
                normalizedRelativePath = normalizeTreeRelativePath(local.relativePath),
                canonicalUrl = canonicalUrl(sourcePath),
                fileName = fileName(local.relativePath),
                trackKey = track?.let {
                    TrackKeyNormalizer.buildKey(it.title, it.group.ifBlank { fallbackGroup }, null)
                }.orEmpty(),
                trackKeyWithoutGroup = track?.let {
                    TrackKeyNormalizer.buildKey(it.title, "", null)
                }.orEmpty(),
                extension = resolvedExtension(local.relativePath, sourcePath),
                fileType = resolvedFileType(local.relativePath, sourcePath)
            )
        }
        .toList()

    val remotes = remoteFiles.map { remote ->
        RemoteCandidate(
            file = remote,
            normalizedRelativePath = normalizeTreeRelativePath(remote.relativePath),
            canonicalUrl = canonicalUrl(remote.url),
            fileName = fileName(remote.relativePath),
            trackKey = remoteTrackKey(remote.relativePath, includeGroup = true),
            trackKeyWithoutGroup = remoteTrackKey(remote.relativePath, includeGroup = false),
            extension = resolvedExtension(remote.relativePath, remote.url),
            fileType = treeFileTypeForNode(remote.relativePath, remote.url)
        )
    }

    fun buildCandidateIndex(key: (MatchCandidate) -> String): Map<String, List<Int>> {
        val index = linkedMapOf<String, MutableList<Int>>()
        candidates.forEachIndexed { candidateIndex, candidate ->
            val value = key(candidate)
            if (value.isNotBlank()) {
                index.getOrPut(value) { mutableListOf() }.add(candidateIndex)
            }
        }
        return index
    }

    val relativePathIndex = buildCandidateIndex(MatchCandidate::normalizedRelativePath)
    val canonicalUrlIndex = buildCandidateIndex(MatchCandidate::canonicalUrl)
    val trackKeyIndex = buildCandidateIndex(MatchCandidate::trackKey)
    val fileNameIndex = buildCandidateIndex(MatchCandidate::fileName)
    val trackKeyWithoutGroupIndex = buildCandidateIndex(MatchCandidate::trackKeyWithoutGroup)
    val available = BooleanArray(candidates.size) { true }
    val unmatchedRemotes = BooleanArray(remotes.size) { true }
    val matched = linkedSetOf<String>()

    fun consume(
        remoteIndex: Int,
        index: Map<String, List<Int>>,
        key: String,
        isCompatible: (MatchCandidate) -> Boolean = { true }
    ) {
        if (!unmatchedRemotes[remoteIndex] || key.isBlank()) return
        val candidateIndex = index[key]
            ?.firstOrNull { available[it] && isCompatible(candidates[it]) }
            ?: return
        available[candidateIndex] = false
        unmatchedRemotes[remoteIndex] = false
        matched += remotes[remoteIndex].file.relativePath
    }

    fun consumePass(
        index: Map<String, List<Int>>,
        key: (RemoteCandidate) -> String,
        isCompatible: (RemoteCandidate, MatchCandidate) -> Boolean = { _, _ -> true }
    ) {
        remotes.forEachIndexed { remoteIndex, remote ->
            consume(remoteIndex, index, key(remote)) { local ->
                isCompatible(remote, local)
            }
        }
    }

    fun hasCompatibleFormat(remote: RemoteCandidate, local: MatchCandidate): Boolean {
        if (remote.extension.isNotBlank() && local.extension.isNotBlank()) {
            return remote.extension == local.extension
        }
        return remote.fileType != TreeFileType.Other && remote.fileType == local.fileType
    }

    // 先让所有远端条目完成强身份匹配，避免前面的模糊命中占用后续条目的精确候选。
    consumePass(relativePathIndex, RemoteCandidate::normalizedRelativePath)
    consumePass(canonicalUrlIndex, RemoteCandidate::canonicalUrl)

    // 目录与标题仍一致时优先匹配，同时要求具体扩展名兼容。
    consumePass(trackKeyIndex, RemoteCandidate::trackKey, ::hasCompatibleFormat)

    // 完整文件名包含扩展名，可用于目录结构变化后的精确格式兜底。
    consumePass(fileNameIndex, RemoteCandidate::fileName)

    // 忽略目录的标题匹配最宽松，最后执行且同样不得跨格式占用候选。
    consumePass(
        trackKeyWithoutGroupIndex,
        RemoteCandidate::trackKeyWithoutGroup,
        ::hasCompatibleFormat
    )

    return matched
}

