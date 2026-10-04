package com.asmr.player.data.local.tree

import android.net.Uri
import android.provider.DocumentsContract
import com.asmr.player.domain.model.LocalTreeLeafCacheEntry
import com.asmr.player.data.local.db.entities.OnlineSavedResourceEntity
import com.asmr.player.domain.model.TreeFileType
import com.asmr.player.domain.model.isLibraryResourceSavableTreeFileType
import com.asmr.player.domain.model.isPlayableTreeFileType
import com.asmr.player.domain.model.treeFileTypeForNode
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.Track
import java.io.File

internal class LocalTreeNode(
    val name: String,
    val path: String,
    val children: MutableMap<String, LocalTreeNode> = linkedMapOf(),
    var absolutePath: String? = null,
    var fileType: TreeFileType = TreeFileType.Other,
    var track: Track? = null,
    var sizeBytes: Long? = null
)

internal data class LocalFolderStats(
    var audioCount: Int = 0,
    var videoCount: Int = 0,
    var hasWav: Boolean = false,
    var hasMp4: Boolean = false
)

internal data class LocalTreeIndex(
    val root: LocalTreeNode,
    val folderStats: Map<String, LocalFolderStats>
)

internal fun onlineSavedResourceTreeLeaf(
    resource: OnlineSavedResourceEntity
): LocalTreeLeafCacheEntry? {
    val relativePath = resource.relativePath.replace('\\', '/').trim().trimStart('/')
    val url = resource.url.trim()
    if (relativePath.isBlank() || !url.startsWith("http", ignoreCase = true)) return null
    val fileType = runCatching { TreeFileType.valueOf(resource.fileType) }
        .getOrElse { treeFileTypeForNode(relativePath, url) }
    if (!isLibraryResourceSavableTreeFileType(fileType) || isPlayableTreeFileType(fileType)) return null
    return LocalTreeLeafCacheEntry(
        relativePath = relativePath,
        absolutePath = url,
        fileType = fileType
    )
}

internal data class LocalTreeIndexBuildResult(
    val index: LocalTreeIndex,
    val leaves: List<LocalTreeLeafCacheEntry>
)

internal enum class LocalTreeSourceKind {
    Imported,
    Downloaded,
}

internal data class LocalTreeSource(
    val path: String,
    val kind: LocalTreeSourceKind,
)

internal fun localTreeSourcesForAlbum(album: Album): List<LocalTreeSource> {
    val imported = buildList {
        val path = album.path.trim()
        if (path.isNotBlank() && !path.startsWith("http", ignoreCase = true) && !path.startsWith("web://", ignoreCase = true)) {
            add(path)
        }
        album.localPath?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
    }.distinctBy(::localTreeSourceIdentity)
    val downloaded = listOfNotNull(album.downloadPath?.trim()?.takeIf { it.isNotBlank() })
    val importedIdentities = imported.map(::localTreeSourceIdentity).toSet()
    return buildList {
        imported.forEach { add(LocalTreeSource(it, LocalTreeSourceKind.Imported)) }
        downloaded.filterNot { localTreeSourceIdentity(it) in importedIdentities }
            .forEach { add(LocalTreeSource(it, LocalTreeSourceKind.Downloaded)) }
    }.distinctBy { localTreeSourceIdentity(it.path) }
}

internal fun localTreeSourceIdentity(path: String): String {
    val trimmed = path.trim()
    if (!trimmed.startsWith("content://", ignoreCase = true)) {
        return runCatching { File(trimmed).canonicalPath }.getOrDefault(File(trimmed).absolutePath)
    }
    val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return trimmed
    val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
        ?: runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        ?: return trimmed
    return "${uri.authority.orEmpty()}:${documentId.replace('\\', '/').trimEnd('/')}"
}

/**
 * 将标题规整为可安全用作下载/保存树路径段的名字：
 * 去首尾空白 → 空则回退 "item" → 非法路径字符（\ / : * ? " < > |）替换为 "_"。
 */
internal fun sanitizeFolderName(name: String): String =
    name.trim().ifEmpty { "item" }.replace(Regex("""[\\/:*?"<>|]"""), "_")
