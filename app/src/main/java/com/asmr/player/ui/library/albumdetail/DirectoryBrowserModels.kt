package com.asmr.player.ui.library.albumdetail

import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.RemoteSubtitleSource
import com.asmr.player.domain.model.Track
import com.asmr.player.domain.model.TreeFileType
import com.asmr.player.ui.common.cover.ImagePreviewItem
import com.asmr.player.ui.common.cover.ImagePreviewRequest
import com.asmr.player.util.isOnlineTrackPath
import java.io.File

internal sealed class AsmrTreeUiEntry {
    abstract val path: String
    abstract val title: String
    abstract val depth: Int

    data class Folder(
        override val path: String,
        override val title: String,
        override val depth: Int
    ) : AsmrTreeUiEntry()

    data class File(
        override val path: String,
        override val title: String,
        override val depth: Int,
        val fileType: TreeFileType,
        val isPlayable: Boolean,
        val url: String? = null
    ) : AsmrTreeUiEntry()
}


internal sealed class LocalTreeUiEntry {
    abstract val path: String
    abstract val title: String
    abstract val depth: Int

    data class Folder(
        override val path: String,
        override val title: String,
        override val depth: Int
    ) : LocalTreeUiEntry()

    data class File(
        override val path: String,
        override val title: String,
        override val depth: Int,
        val absolutePath: String,
        val fileType: TreeFileType,
        val track: Track?
    ) : LocalTreeUiEntry()
}

internal fun buildVideoMediaItem(
    title: String,
    uriOrPath: String,
    artworkUri: String,
    artist: String
): MediaItem? {
    val trimmed = uriOrPath.trim()
    if (trimmed.isBlank()) return null
    val uri = if (
        trimmed.startsWith("http", ignoreCase = true) ||
            trimmed.startsWith("content://", ignoreCase = true) ||
            trimmed.startsWith("file://", ignoreCase = true)
    ) {
        trimmed.toUri()
    } else {
        Uri.fromFile(File(trimmed))
    }
    val ext = trimmed.substringBefore('#').substringBefore('?').substringAfterLast('.', "").lowercase()
    val mimeType = when (ext) {
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "mov" -> "video/quicktime"
        else -> "video/*"
    }
    val displayTitle = title.ifBlank { trimmed.substringAfterLast('/').substringAfterLast('\\') }
    val metadata = androidx.media3.common.MediaMetadata.Builder()
        .setTitle(displayTitle)
        .setArtist(artist.trim())
        .setArtworkUri(artworkUri.trim().takeIf { it.isNotBlank() }?.toUri())
        .setExtras(android.os.Bundle().apply { putBoolean("is_video", true) })
        .build()
    return MediaItem.Builder()
        .setMediaId(trimmed)
        .setUri(uri)
        .setMimeType(mimeType)
        .setMediaMetadata(metadata)
        .build()
}

internal sealed class FileSizeSource {
    data object None : FileSizeSource()
    data class Local(val path: String, val sizeBytes: Long? = null) : FileSizeSource()
    data class Remote(val url: String) : FileSizeSource()
}

internal data class DirectoryBreadcrumbSegment(
    val label: String,
    val path: String
)

internal data class DirectoryFolderItem(
    val path: String,
    val title: String,
    val descendantTrackIds: List<Long> = emptyList(),
    val hasLocalContent: Boolean = false,
)

internal data class DirectoryFileItem(
    val path: String,
    val title: String,
    val fileType: TreeFileType,
    val isPlayable: Boolean,
    val isOnline: Boolean = false,
    val durationSeconds: Double? = null,
    val sizeSource: FileSizeSource = FileSizeSource.None,
    val absolutePath: String = "",
    val url: String = "",
    val track: Track? = null,
    val thumbnailModel: Any? = null,
    val playlistTarget: PlaylistAddTarget? = null,
    val subtitleSources: List<RemoteSubtitleSource> = emptyList(),
    val showSubtitleStamp: Boolean = false,
    val dlsitePlayImageCrypt: Boolean = false,
    val dlsitePlayImageWidth: Int? = null,
    val dlsitePlayImageHeight: Int? = null,
    val dlsitePlayOptimizedName: String? = null
)

internal data class LocalTreeDeletionTarget(
    val title: String,
    val relativePath: String,
    val absolutePath: String? = null,
    val isDirectory: Boolean,
    val trackIds: List<Long> = emptyList(),
    val hasLocalContent: Boolean,
)

internal fun isOnlineDirectoryAudio(fileType: TreeFileType, absolutePath: String, track: Track?): Boolean {
    if (fileType != TreeFileType.Audio) return false
    val path = track?.path?.trim().orEmpty().ifBlank { absolutePath.trim() }
    return isOnlineTrackPath(path)
}

internal data class DirectoryBrowserResult(
    val currentPath: String,
    val breadcrumbs: List<DirectoryBreadcrumbSegment>,
    val folders: List<DirectoryFolderItem>,
    val files: List<DirectoryFileItem>
) {
    val batchTargets: List<PlaylistAddTarget>
        get() = files.mapNotNull { file ->
            when (file.fileType) {
                TreeFileType.Audio, TreeFileType.Video -> file.playlistTarget
                else -> null
            }
        }
}

internal fun buildDirectoryImagePreviewRequest(
    files: List<DirectoryFileItem>,
    clickedPath: String,
    toPreviewItem: (DirectoryFileItem) -> ImagePreviewItem?
): ImagePreviewRequest? {
    val imageFiles = files.filter { it.fileType == TreeFileType.Image }
    if (imageFiles.isEmpty()) return null
    val items = imageFiles.mapNotNull(toPreviewItem)
    if (items.isEmpty()) return null
    val initialIndex = items.indexOfFirst { it.key == clickedPath }
    if (initialIndex < 0) return null
    return ImagePreviewRequest(items = items, initialIndex = initialIndex)
}

internal fun buildGalleryImagePreviewRequest(
    galleryItems: List<ImagePreviewItem>,
    clickedKey: String
): ImagePreviewRequest? {
    if (galleryItems.isEmpty()) return null
    val initialIndex = galleryItems.indexOfFirst { it.key == clickedKey }
    if (initialIndex < 0) return null
    return ImagePreviewRequest(items = galleryItems, initialIndex = initialIndex)
}

internal fun buildBreadcrumbSegments(currentPath: String): List<DirectoryBreadcrumbSegment> {
    val normalized = currentPath.trim().trim('/')
    if (normalized.isBlank()) return emptyList()
    val segments = normalized.split('/').filter { it.isNotBlank() }
    val out = mutableListOf<DirectoryBreadcrumbSegment>()
    var path = ""
    segments.forEach { segment ->
        path = if (path.isBlank()) segment else "$path/$segment"
        out += DirectoryBreadcrumbSegment(label = segment, path = path)
    }
    return out
}

internal fun normalizeLocalTreeRelativePath(path: String): String? {
    val segments = path
        .replace('\\', '/')
        .trim()
        .trim('/')
        .split('/')
        .filter { it.isNotBlank() }
    if (segments.isEmpty() || segments.any { it == "." || it == ".." }) return null
    return segments.joinToString("/")
}

internal fun localTreePathMatchesTarget(
    candidatePath: String,
    targetPath: String,
    targetIsDirectory: Boolean,
): Boolean {
    val candidate = normalizeLocalTreeRelativePath(candidatePath) ?: return false
    val target = normalizeLocalTreeRelativePath(targetPath) ?: return false
    return candidate == target || (targetIsDirectory && candidate.startsWith("$target/"))
}

internal fun albumArtistLabel(album: Album): String {
    return when {
        album.cv.isNotBlank() && album.circle.isNotBlank() -> "${album.circle} / ${album.cv}"
        album.cv.isNotBlank() -> album.cv
        album.circle.isNotBlank() -> album.circle
        album.rjCode.isNotBlank() -> album.rjCode
        else -> album.workId
    }.trim()
}

internal fun albumArtworkLabel(album: Album): String {
    return album.coverPath.ifBlank { album.coverUrl }
}

internal data class LocalTreeUiResult(
    val entries: List<LocalTreeUiEntry>
)

internal data class AsmrTreeUiResult(
    val entries: List<AsmrTreeUiEntry>
)

internal fun folderPathPrefixes(path: String): List<String> {
    val segs = path.split('/').filter { it.isNotBlank() }
    if (segs.isEmpty()) return emptyList()
    val out = ArrayList<String>(segs.size)
    var cur = ""
    for (seg in segs) {
        cur = if (cur.isBlank()) seg else "$cur/$seg"
        out.add(cur)
    }
    return out
}

internal data class RemoteSelectionFileRef(
    val relativePath: String,
    val url: String
)

internal data class LocalSelectionFileRef(
    val relativePath: String,
    val absolutePath: String,
    val track: Track?
)

internal data class LocalIncrementalSelectionPaths(
    val downloadedPaths: Set<String> = emptySet(),
    val savedPaths: Set<String> = emptySet()
)


internal fun queryLocalFileSize(context: android.content.Context, path: String): Long? {
    val trimmed = path.trim()
    if (trimmed.isBlank()) return null
    return when {
        trimmed.startsWith("content://", ignoreCase = true) -> {
            runCatching {
                context.contentResolver.query(
                    Uri.parse(trimmed),
                    arrayOf(DocumentsContract.Document.COLUMN_SIZE, OpenableColumns.SIZE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (!cursor.moveToFirst()) {
                        null
                    } else {
                        val documentIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                        val openableIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        when {
                            documentIndex >= 0 && !cursor.isNull(documentIndex) -> cursor.getLong(documentIndex)
                            openableIndex >= 0 && !cursor.isNull(openableIndex) -> cursor.getLong(openableIndex)
                            else -> null
                        }
                    }
                }
            }.getOrNull()
        }
        else -> runCatching { File(trimmed).takeIf { it.exists() }?.length() }.getOrNull()
    }?.takeIf { it > 0L }
}

