package com.asmr.player.data.local.tree

import android.net.Uri
import android.provider.DocumentsContract
import com.asmr.player.data.local.db.AppDatabaseProvider
import com.asmr.player.data.local.db.entities.LocalTreeCacheEntity
import com.asmr.player.domain.model.LocalTreeLeafCacheEntry
import com.asmr.player.data.local.db.entities.OnlineSavedResourceEntity
import com.asmr.player.domain.model.TreeFileType
import com.asmr.player.domain.model.treeFileTypeForName
import com.asmr.player.domain.model.Track
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

private const val LOCAL_TREE_CACHE_PARSER_VERSION = 2L

internal suspend fun loadOrBuildLocalTreeIndex(
    context: android.content.Context,
    albumId: Long,
    albumPaths: List<String>,
    tracks: List<Track>,
    onlineSavedResources: List<OnlineSavedResourceEntity> = emptyList(),
    sources: List<LocalTreeSource> = albumPaths.map { LocalTreeSource(it, LocalTreeSourceKind.Imported) },
): LocalTreeIndex {
    val gson = Gson()
    val normalizedSources = sources.filter { it.path.isNotBlank() }.distinctBy { localTreeSourceIdentity(it.path) }
    val cacheKey = normalizedSources
        .map { "${it.kind.name}:${it.path.trim()}" }
        .sorted()
        .joinToString("|")
    val stamp = computeLocalTreeCacheStamp(context, albumPaths, tracks)
    val dao = AppDatabaseProvider.get(context).localTreeCacheDao()
    val onlineTracks = tracks.filter { it.path.trim().startsWith("http", ignoreCase = true) }
    val onlineUrlSet = onlineTracks.map { it.path.trim() }.filter { it.isNotBlank() }.toSet()
    val savedResourceKeys = onlineSavedResources.mapNotNull { resource ->
        val relativePath = resource.relativePath.replace('\\', '/').trim().trimStart('/')
        val url = resource.url.trim()
        if (relativePath.isBlank() || url.isBlank()) null else relativePath to url
    }.toSet()

    fun guessExtFromUrl(url: String): String {
        val u = url.substringBefore('?').trim()
        val ext = u.substringAfterLast('.', "").lowercase()
        if (ext.isBlank() || ext.length > 6) return ""
        if (ext.contains('/') || ext.contains('\\')) return ""
        return ext
    }

    fun buildOnlineLeaves(): List<LocalTreeLeafCacheEntry> {
        if (onlineTracks.isEmpty()) return emptyList()
        val audioExts = setOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "opus")
        val videoExts = setOf("mp4", "mkv", "webm")
        return onlineTracks.mapNotNull { t ->
            val url = t.path.trim()
            if (url.isBlank()) return@mapNotNull null
            val ext = guessExtFromUrl(url)
            val type = when {
                videoExts.contains(ext) -> TreeFileType.Video
                audioExts.contains(ext) -> TreeFileType.Audio
                else -> TreeFileType.Audio
            }
            val groupPath = t.group.trim()
                .trim('/')
                .split('/')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .joinToString("/") { sanitizeFolderName(it) }

            val baseName = sanitizeFolderName(t.title.ifBlank { "track" })
            val fileName = if (ext.isNotBlank() && !baseName.endsWith(".$ext", ignoreCase = true)) "$baseName.$ext" else baseName
            val rel = if (groupPath.isBlank()) fileName else "$groupPath/$fileName"
            LocalTreeLeafCacheEntry(relativePath = rel, absolutePath = url, fileType = type)
        }
    }

    fun buildSavedResourceLeaves(): List<LocalTreeLeafCacheEntry> {
        return onlineSavedResources.mapNotNull(::onlineSavedResourceTreeLeaf)
    }

    fun mergeLeaves(
        localLeaves: List<LocalTreeLeafCacheEntry>,
        onlineLeaves: List<LocalTreeLeafCacheEntry>,
        savedResourceLeaves: List<LocalTreeLeafCacheEntry>
    ): List<LocalTreeLeafCacheEntry> {
        val filteredLocal = localLeaves.filter { leaf ->
            val abs = leaf.absolutePath.trim()
            !(
                abs.startsWith("http", ignoreCase = true) &&
                    !onlineUrlSet.contains(abs) &&
                    !savedResourceKeys.contains(leaf.relativePath to abs)
            )
        }
        val byRel = linkedMapOf<String, LocalTreeLeafCacheEntry>()
        filteredLocal.forEach { byRel[it.relativePath] = it }

        fun mergeRemoteLeaf(leaf: LocalTreeLeafCacheEntry) {
            val existing = byRel[leaf.relativePath]
            if (existing == null) {
                byRel[leaf.relativePath] = leaf
            } else {
                val abs = existing.absolutePath.trim()
                if (abs.startsWith("http", ignoreCase = true)) byRel[leaf.relativePath] = leaf
            }
        }

        onlineLeaves.forEach(::mergeRemoteLeaf)
        savedResourceLeaves.forEach(::mergeRemoteLeaf)
        return byRel.values.toList()
    }
    val onlineLeaves = buildOnlineLeaves()
    val savedResourceLeaves = buildSavedResourceLeaves()

    val cached = dao.getByAlbumAndKey(albumId = albumId, cacheKey = cacheKey)
    if (cached != null && cached.stamp == stamp && cached.payloadJson.isNotBlank()) {
        val type = object : TypeToken<List<LocalTreeLeafCacheEntry>>() {}.type
        val leaves = runCatching { gson.fromJson<List<LocalTreeLeafCacheEntry>>(cached.payloadJson, type) }
            .getOrDefault(emptyList())
        val merged = mergeLeaves(
            localLeaves = leaves,
            onlineLeaves = onlineLeaves,
            savedResourceLeaves = savedResourceLeaves
        )
        val missingLocalSizeMetadata = leaves.any { leaf ->
            val abs = leaf.absolutePath.trim()
            abs.isNotBlank() &&
                !abs.startsWith("http", ignoreCase = true) &&
                leaf.sizeBytes == null
        }
        if (merged.isNotEmpty() && !missingLocalSizeMetadata) {
            return buildLocalTreeIndexFromLeaves(leaves = merged, tracks = tracks)
        }
    }

    val built = buildLocalTreeIndexByScanningSources(
        context = context,
        sources = normalizedSources,
        tracks = tracks,
    )
    val merged = mergeLeaves(
        localLeaves = built.leaves,
        onlineLeaves = onlineLeaves,
        savedResourceLeaves = savedResourceLeaves
    )
    dao.upsert(
        LocalTreeCacheEntity(
            albumId = albumId,
            cacheKey = cacheKey,
            stamp = stamp,
            payloadJson = gson.toJson(merged),
            updatedAt = System.currentTimeMillis()
        )
    )
    return buildLocalTreeIndexFromLeaves(leaves = merged, tracks = tracks)
}

private fun buildLocalTreeIndexByScanningSources(
    context: android.content.Context,
    sources: List<LocalTreeSource>,
    tracks: List<Track>,
): LocalTreeIndexBuildResult {
    val hasImported = sources.any { it.kind == LocalTreeSourceKind.Imported }
    val hasDownloaded = sources.any { it.kind == LocalTreeSourceKind.Downloaded }
    val separateSources = hasImported && hasDownloaded &&
        sources.map { localTreeSourceIdentity(it.path) }.distinct().size > 1
    if (!separateSources) {
        return buildLocalTreeIndexByScanning(
            context = context,
            albumPaths = sources.map { it.path },
            tracks = tracks,
        )
    }

    val leaves = sources.flatMap { source ->
        val prefix = when (source.kind) {
            LocalTreeSourceKind.Imported -> "导入内容"
            LocalTreeSourceKind.Downloaded -> "下载内容"
        }
        buildLocalTreeIndexByScanning(
            context = context,
            albumPaths = listOf(source.path),
            tracks = tracks,
        ).leaves.map { leaf ->
            leaf.copy(relativePath = "$prefix/${leaf.relativePath}")
        }
    }
    return LocalTreeIndexBuildResult(
        index = buildLocalTreeIndexFromLeaves(leaves, tracks),
        leaves = leaves,
    )
}

internal fun computeAlbumPathsStamp(context: android.content.Context, albumPaths: List<String>): Long {
    val paths = albumPaths.map { it.trim() }.filter { it.isNotBlank() }.sorted()
    var acc = 1469598103934665603L
    paths.forEach { p ->
        val v = if (p.startsWith("content://")) {
            queryDocumentLastModified(context, p)
        } else {
            runCatching { java.io.File(p).lastModified() }.getOrDefault(0L)
        }
        acc = (acc xor v) * 1099511628211L
    }
    return acc
}

internal fun computeLocalTreeCacheStamp(
    context: android.content.Context,
    albumPaths: List<String>,
    tracks: List<Track>
): Long {
    return combineLocalTreeCacheStamp(computeAlbumPathsStamp(context, albumPaths), tracks)
}

internal fun combineLocalTreeCacheStamp(albumPathsStamp: Long, tracks: List<Track>): Long {
    var acc = (albumPathsStamp xor LOCAL_TREE_CACHE_PARSER_VERSION) * 1099511628211L
    val localTrackPaths = tracks.asSequence()
        .map { it.path.trim() }
        .filter { it.isNotBlank() && !it.startsWith("http", ignoreCase = true) }
        .sorted()
        .toList()
    localTrackPaths.forEach { path ->
        acc = (acc xor path.hashCode().toLong()) * 1099511628211L
    }
    acc = (acc xor localTrackPaths.size.toLong()) * 1099511628211L
    return acc
}

internal fun queryDocumentLastModified(context: android.content.Context, uriString: String): Long {
    val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return 0L
    return runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED),
            null,
            null,
            null
        )?.use { cursor ->
            val idx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            if (idx < 0) return@use 0L
            if (!cursor.moveToFirst()) return@use 0L
            cursor.getLong(idx)
        } ?: 0L
    }.getOrDefault(0L)
}

internal fun buildLocalTreeIndexByScanning(
    context: android.content.Context,
    albumPaths: List<String>,
    tracks: List<Track>
): LocalTreeIndexBuildResult {
    val root = LocalTreeNode(name = "", path = "")
    val trackByAbsolutePath = tracks.associateBy { it.path }
    val folderStats = linkedMapOf<String, LocalFolderStats>()

    fun updateFolderStats(segments: List<String>, type: TreeFileType, extLower: String) {
        val folderSegs = segments.dropLast(1)
        if (folderSegs.isEmpty()) return
        var cur = ""
        folderSegs.forEach { seg ->
            cur = if (cur.isBlank()) seg else "$cur/$seg"
            val st = folderStats.getOrPut(cur) { LocalFolderStats() }
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

    albumPaths.forEach { albumPath ->
        if (albumPath.startsWith("content://")) {
            val uri = Uri.parse(albumPath)
            val treeId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: ""
            val rootDocId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: treeId
            val treeUri = if (treeId.isNotBlank()) DocumentsContract.buildTreeDocumentUri(uri.authority, treeId) else uri
            
            fun query(parentDocId: String, parentRel: String) {
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
                context.contentResolver.query(childrenUri, arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE
                ), null, null, null)?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(idIdx)
                        val name = cursor.getString(nameIdx)
                        val mime = cursor.getString(mimeIdx)
                        val sizeBytes = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L
                        val rel = if (parentRel.isEmpty()) name else "$parentRel/$name"
                        
                        val segments = rel.split('/').filter { it.isNotBlank() }
                        var cur = root
                        segments.forEachIndexed { idx, seg ->
                            val nextPath = if (cur.path.isBlank()) seg else "${cur.path}/$seg"
                            val child = cur.children.getOrPut(seg) { LocalTreeNode(name = seg, path = nextPath) }
                            if (idx == segments.lastIndex) {
                                val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id).toString()
                                val ft = if (mime == DocumentsContract.Document.MIME_TYPE_DIR) TreeFileType.Other else treeFileTypeForName(name)
                                child.fileType = ft
                                if (mime != DocumentsContract.Document.MIME_TYPE_DIR && ft != TreeFileType.Other && ft != TreeFileType.Subtitle) {
                                    val track = trackByAbsolutePath[fileUri]
                                    if (ft == TreeFileType.Audio && track == null) {
                                        child.absolutePath = null
                                        child.track = null
                                        child.sizeBytes = null
                                    } else {
                                        child.absolutePath = fileUri
                                        child.track = track
                                        child.sizeBytes = sizeBytes.takeIf { it > 0L }
                                    }
                                    if (ft == TreeFileType.Video || (ft == TreeFileType.Audio && track != null)) {
                                        updateFolderStats(segments, ft, name.substringAfterLast('.', "").lowercase())
                                    }
                                } else {
                                    child.absolutePath = null
                                    child.track = null
                                    child.sizeBytes = null
                                }
                            }
                            cur = child
                        }
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            query(id, rel)
                        }
                    }
                }
            }
            if (rootDocId.isNotBlank()) {
                runCatching { query(rootDocId, "") }
            }
        } else {
            val rootDir = java.io.File(albumPath)
            if (rootDir.exists()) {
                rootDir.walkTopDown().forEach fileLoop@{ file ->
                    if (!file.isFile) return@fileLoop
                    val type = treeFileTypeForName(file.name)
                    if (type == TreeFileType.Other || type == TreeFileType.Subtitle) return@fileLoop
                    val track = trackByAbsolutePath[file.absolutePath]
                    if (type == TreeFileType.Audio && track == null) return@fileLoop

                    val rawRel = runCatching { file.relativeTo(rootDir).path }.getOrElse { file.name }
                    val rel = rawRel.replace('\\', '/').trim().trimStart('/')
                    val segments = rel.split('/').filter { it.isNotBlank() }
                    if (segments.isEmpty()) return@fileLoop

                    var cur = root
                    segments.forEachIndexed { idx, seg ->
                        val nextPath = if (cur.path.isBlank()) seg else "${cur.path}/$seg"
                        val child = cur.children.getOrPut(seg) { LocalTreeNode(name = seg, path = nextPath) }
                        if (idx == segments.lastIndex) {
                            child.absolutePath = file.absolutePath
                            child.fileType = type
                            child.track = track
                            child.sizeBytes = file.length().takeIf { it > 0L }
                            if (type == TreeFileType.Audio || type == TreeFileType.Video) {
                                updateFolderStats(segments, type, file.extension.lowercase())
                            }
                        }
                        cur = child
                    }
                }
            }
        }
    }

    fun collectLeaves(node: LocalTreeNode, out: MutableList<LocalTreeLeafCacheEntry>) {
        if (node.children.isEmpty() && node.absolutePath != null) {
            out.add(
                LocalTreeLeafCacheEntry(
                    relativePath = node.path,
                    absolutePath = node.absolutePath ?: return,
                    fileType = node.fileType,
                    sizeBytes = node.sizeBytes
                )
            )
            return
        }
        node.children.values.forEach { child -> collectLeaves(child, out) }
    }

    val leaves = mutableListOf<LocalTreeLeafCacheEntry>()
    collectLeaves(root, leaves)
    return LocalTreeIndexBuildResult(
        index = LocalTreeIndex(root = root, folderStats = folderStats),
        leaves = leaves
    )
}

internal fun buildLocalTreeIndexFromLeaves(
    leaves: List<LocalTreeLeafCacheEntry>,
    tracks: List<Track>
): LocalTreeIndex {
    val root = LocalTreeNode(name = "", path = "")
    val trackByAbsolutePath = tracks.associateBy { it.path }

    leaves.forEach { leaf ->
        if (leaf.fileType == TreeFileType.Subtitle) return@forEach
        val track = trackByAbsolutePath[leaf.absolutePath]
        if (leaf.fileType == TreeFileType.Audio && track == null) return@forEach
        val rel = leaf.relativePath.trim().trimStart('/')
        if (rel.isBlank()) return@forEach
        val segments = rel.split('/').filter { it.isNotBlank() }
        if (segments.isEmpty()) return@forEach
        var cur = root
        segments.forEachIndexed { idx, seg ->
            val nextPath = if (cur.path.isBlank()) seg else "${cur.path}/$seg"
            val child = cur.children.getOrPut(seg) { LocalTreeNode(name = seg, path = nextPath) }
            if (idx == segments.lastIndex) {
                child.absolutePath = leaf.absolutePath
                child.fileType = leaf.fileType
                child.track = track
                child.sizeBytes = leaf.sizeBytes
            }
            cur = child
        }
    }

    return LocalTreeIndex(root = root, folderStats = emptyMap())
}
