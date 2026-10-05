package com.asmr.player.data.local.tree

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.asmr.player.util.SubtitleEntry
import com.asmr.player.util.SubtitleParser
import com.asmr.player.util.isScannableLocalDirectoryName
import java.io.File

/**
 * R3-C1b：SAF 文档树与文件删除 helper（自 LibraryViewModel 私有实现逐字搬移，
 * 供扫描/删除两族 holder 与 VM 共用；context 改为显式参数，逻辑未改）。
 */

/** SAF 子节点投影（原 LibraryViewModel.DocNode）。 */
data class SafDocNode(
    val documentId: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long = 0L,
    val relativePath: String = ""
)

object SafTreeSupport {

    fun queryChildren(context: Context, treeUri: Uri, parentDocumentId: String, parentRelativePath: String = ""): List<SafDocNode> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )
        val result = mutableListOf<SafDocNode>()
        val resolver = context.contentResolver
        runCatching {
            resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                while (cursor.moveToNext()) {
                    val id = cursor.getString(idIndex)
                    val name = cursor.getString(nameIndex).orEmpty()
                    val mime = cursor.getString(mimeIndex).orEmpty()
                    val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else 0L
                    val relPath = if (parentRelativePath.isEmpty()) name else "$parentRelativePath/$name"
                    result.add(SafDocNode(documentId = id, displayName = name, mimeType = mime, sizeBytes = size, relativePath = relPath))
                }
            }
        }
        return result
    }

    fun documentExists(context: Context, treeUri: Uri, documentId: String): Boolean {
        val resolver = context.contentResolver
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        return runCatching {
            resolver.query(docUri, projection, null, null, null)?.use { cursor ->
                cursor.moveToFirst()
            } ?: false
        }.getOrDefault(false)
    }

    fun walkTree(context: Context, treeUri: Uri, rootDocumentId: String): List<SafDocNode> {
        val result = mutableListOf<SafDocNode>()
        val queue = ArrayDeque<SafDocNode>()
        queryChildren(context, treeUri, rootDocumentId)
            .filterNot { node ->
                node.mimeType == DocumentsContract.Document.MIME_TYPE_DIR &&
                    !isScannableLocalDirectoryName(node.displayName)
            }
            .forEach { queue.add(it) }
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            result.add(node)
            if (node.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                queryChildren(context, treeUri, node.documentId, node.relativePath)
                    .filterNot { child ->
                        child.mimeType == DocumentsContract.Document.MIME_TYPE_DIR &&
                            !isScannableLocalDirectoryName(child.displayName)
                    }
                    .forEach { queue.add(it) }
            }
        }
        return result
    }

    fun readSubtitleFromUri(context: Context, treeUri: Uri, documentId: String, displayName: String): List<SubtitleEntry> {
        val resolver = context.contentResolver
        val subUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        val ext = displayName.substringAfterLast('.', "lrc").lowercase()
        val tempFile = File(context.cacheDir, "sub_${System.currentTimeMillis()}.$ext")
        return try {
            runCatching {
                resolver.openInputStream(subUri)?.use { input ->
                    tempFile.outputStream().use { out -> input.copyTo(out) }
                }
            }
            SubtitleParser.parse(tempFile.absolutePath)
        } finally {
            runCatching { tempFile.delete() }
        }
    }

    fun resolveTreeDocumentUri(context: Context, rootUriString: String, relativePath: String): Result<Uri?> = runCatching {
        val rootUri = Uri.parse(rootUriString)
        val authority = requireNotNull(rootUri.authority) { "目录授权地址无效" }
        val treeId = runCatching { DocumentsContract.getTreeDocumentId(rootUri) }.getOrDefault("")
        var documentId = runCatching { DocumentsContract.getDocumentId(rootUri) }
            .getOrDefault(treeId)
            .ifBlank { treeId }
        check(documentId.isNotBlank()) { "无法读取目录授权" }
        val treeUri = if (treeId.isNotBlank()) {
            DocumentsContract.buildTreeDocumentUri(authority, treeId)
        } else {
            rootUri
        }

        for (segment in relativePath.split('/')) {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
            val cursor = context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                ),
                null,
                null,
                null,
            ) ?: throw IllegalStateException("无法访问目录，请重新授权存储权限")
            val childId = cursor.use {
                val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                var match: String? = null
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex)
                    val mime = cursor.getString(mimeIndex)
                    if (name == segment && mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        match = cursor.getString(idIndex)
                        break
                    }
                }
                match
            }
            if (childId == null) return@runCatching null
            documentId = childId
        }
        DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
    }

    fun isCanonicalDescendant(target: File, root: File): Boolean {
        if (target == root) return false
        val rootPrefix = root.path.trimEnd(File.separatorChar) + File.separator
        return target.path.startsWith(rootPrefix)
    }

    fun deletePathSafely(context: Context, path: String): Boolean {
        if (path.isBlank()) return false
        val externalBase = context.getExternalFilesDir(null)
        val allowedRoots = listOfNotNull(
            externalBase,
            context.filesDir,
            context.cacheDir
        )
            .mapNotNull { runCatching { it.canonicalFile }.getOrNull() ?: it.absoluteFile }

        val target = runCatching { File(path).canonicalFile }.getOrNull() ?: File(path).absoluteFile
        val isAllowed = allowedRoots.any { root -> isCanonicalDescendant(target, root) }
        if (!isAllowed) return false
        if (!target.exists()) return false

        return if (target.isDirectory) {
            runCatching { target.deleteRecursively() }.getOrDefault(false)
        } else {
            runCatching { target.delete() }.getOrDefault(false)
        }
    }

    fun deleteLocalTreeFile(context: Context, albumRoots: List<String>, absolutePath: String): Boolean {
        val normalizedPath = absolutePath.trim()
        if (normalizedPath.isBlank()) return false
        if (normalizedPath.startsWith("content://", ignoreCase = true)) {
            val targetUri = runCatching { Uri.parse(normalizedPath) }.getOrNull() ?: return false
            val allowedAuthority = albumRoots.asSequence()
                .filter { it.startsWith("content://", ignoreCase = true) }
                .mapNotNull { runCatching { Uri.parse(it).authority }.getOrNull() }
                .any { it == targetUri.authority }
            if (!allowedAuthority) return false
            return runCatching {
                DocumentsContract.deleteDocument(context.contentResolver, targetUri)
            }.getOrDefault(false)
        }
        if (normalizedPath.startsWith("http", ignoreCase = true) || normalizedPath.startsWith("web://", ignoreCase = true)) {
            return false
        }

        val targetFile = runCatching { File(normalizedPath).canonicalFile }.getOrNull() ?: return false
        val allowed = albumRoots.asSequence()
            .filterNot { it.startsWith("content://", ignoreCase = true) }
            .filterNot { it.startsWith("http", ignoreCase = true) || it.startsWith("web://", ignoreCase = true) }
            .mapNotNull { root -> runCatching { File(root).canonicalFile }.getOrNull() }
            .any { root -> isCanonicalDescendant(targetFile, root) }
        if (!allowed) return false
        if (!targetFile.exists()) return true
        if (!targetFile.isFile) return false
        return runCatching { targetFile.delete() }.getOrDefault(false)
    }

    fun deleteLocalTreeDirectories(context: Context, albumRoots: List<String>, relativePath: String): Boolean {
        var hasUsableRoot = false
        var allDeleted = true
        val fileTargets = linkedSetOf<File>()

        albumRoots.distinct().forEach { rawRoot ->
            when {
                rawRoot.startsWith("content://", ignoreCase = true) -> {
                    hasUsableRoot = true
                    val resolved = resolveTreeDocumentUri(context, rawRoot, relativePath)
                    if (resolved.isFailure) {
                        allDeleted = false
                    } else {
                        resolved.getOrNull()?.let { targetUri ->
                            val deleted = runCatching {
                                DocumentsContract.deleteDocument(context.contentResolver, targetUri)
                            }.getOrDefault(false)
                            if (!deleted) allDeleted = false
                        }
                    }
                }
                rawRoot.startsWith("http", ignoreCase = true) || rawRoot.startsWith("web://", ignoreCase = true) -> Unit
                rawRoot.isNotBlank() -> {
                    val root = runCatching { File(rawRoot).canonicalFile }.getOrNull() ?: return@forEach
                    val target = runCatching {
                        File(root, relativePath.replace('/', File.separatorChar)).canonicalFile
                    }.getOrNull() ?: return@forEach
                    if (isCanonicalDescendant(target, root)) {
                        hasUsableRoot = true
                        if (target.exists()) fileTargets += target
                    }
                }
            }
        }

        fileTargets
            .sortedByDescending { it.path.length }
            .forEach { target ->
                if (!target.isDirectory || !runCatching { target.deleteRecursively() }.getOrDefault(false)) {
                    allDeleted = false
                }
            }
        return hasUsableRoot && allDeleted
    }
}
