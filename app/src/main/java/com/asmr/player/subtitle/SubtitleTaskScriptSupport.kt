package com.asmr.player.subtitle

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import java.io.File
import com.asmr.player.data.local.tree.LocalTreeNode
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.asmr.player.domain.model.Track
import com.asmr.player.domain.model.TreeFileType
import android.net.Uri
import com.asmr.player.data.local.tree.loadOrBuildLocalTreeIndex
import com.asmr.player.data.local.db.entities.titleForDisplay
import kotlinx.coroutines.withContext

internal data class ScriptFileRef(
    val absolutePath: String,
    val name: String,
    val isPdf: Boolean
)


internal suspend fun SubtitleTaskService.buildSubtitleWorkContext(itemId: String): SubtitleWorkContext? {
        val dao = database.subtitleTaskDao()
        val item = dao.getItem(itemId) ?: return null
        val track = database.trackDao().getTrackByIdOnce(item.trackId) ?: return null
        val album = database.albumDao().getAlbumById(track.albumId)
        return SubtitleWorkContext(
            workTitleJapanese = album?.title.orEmpty(),
            workTitleChinese = album?.titleForDisplay?.takeIf { it != album.title }.orEmpty(),
            trackTitleJapanese = track.title,
            trackTitleChinese = track.titleForDisplay.takeIf { it != track.title }.orEmpty(),
            circle = album?.circle.orEmpty(),
            cv = album?.cv.orEmpty()
        )
    }

    /**
     * 为字幕翻译 agent 构建台本上下文：扫描作品目录下可读的文本文件（台本/剧本等）
     * 与 PDF，让 agent 通过 list/read 工具自行检索查看，辅助统一人名、术语、情节与语气。
     * 未发现任何可读文件时返回 null（此时不会向 agent 暴露台本工具）。
     */
internal suspend fun SubtitleTaskService.buildSubtitleScriptContext(itemId: String): SubtitleScriptContext? {
        val item = database.subtitleTaskDao().getItem(itemId) ?: return null
        val track = database.trackDao().getTrackByIdOnce(item.trackId) ?: return null
        val album = database.albumDao().getAlbumById(track.albumId) ?: return null
        val roots = listOfNotNull(album.path, album.localPath, album.downloadPath)
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("http", ignoreCase = true) && !it.startsWith("web://", ignoreCase = true) }
            .distinct()
        if (roots.isEmpty()) return null
        val tracks = database.trackDao().getTracksForAlbumOrderedOnce(album.id).map { entity ->
            Track(
                id = entity.id,
                albumId = entity.albumId,
                title = entity.title,
                path = entity.path,
                duration = entity.duration,
                group = entity.group
            )
        }
        val index = withContext(Dispatchers.IO) {
            runCatching {
                loadOrBuildLocalTreeIndex(
                    context = applicationContext,
                    albumId = album.id,
                    albumPaths = roots,
                    tracks = tracks
                )
            }.getOrNull()
        } ?: return null
        val scriptFiles = collectScriptFiles(index.root)
        if (scriptFiles.isEmpty()) return null
        val files = scriptFiles.mapIndexed { fileIndex, ref ->
            SubtitleScriptFile(index = fileIndex, name = ref.name)
        }
        val textCache = ConcurrentHashMap<String, String>()
        val reader = SubtitleScriptReader { fileIndex, offset, limit ->
            val ref = scriptFiles.getOrNull(fileIndex) ?: return@SubtitleScriptReader null
            val full = textCache[ref.absolutePath] ?: withContext(Dispatchers.IO) {
                if (ref.isPdf) extractPdfText(ref.absolutePath) else readScriptTextFile(ref.absolutePath)
            }?.also { cached -> textCache[ref.absolutePath] = cached } ?: return@SubtitleScriptReader null
            val safeOffset = offset.coerceIn(0, full.length)
            val end = (safeOffset + limit).coerceAtMost(full.length)
            SubtitleScriptReadResult(
                fileIndex = fileIndex,
                name = ref.name,
                offset = safeOffset,
                totalChars = full.length,
                content = full.substring(safeOffset, end),
                truncated = end < full.length
            )
        }
        return SubtitleScriptContext(files = files, reader = reader)
    }

internal fun SubtitleTaskService.collectScriptFiles(root: LocalTreeNode): List<ScriptFileRef> {
        val out = mutableListOf<ScriptFileRef>()
        fun walk(node: LocalTreeNode) {
            if (node.children.isEmpty()) {
                val absolutePath = node.absolutePath?.trim().orEmpty()
                if (absolutePath.isBlank()) return
                when (node.fileType) {
                    TreeFileType.Text -> out += ScriptFileRef(absolutePath, node.path, isPdf = false)
                    TreeFileType.Pdf -> out += ScriptFileRef(absolutePath, node.path, isPdf = true)
                    else -> Unit
                }
                return
            }
            node.children.values.sortedBy { it.name }.forEach(::walk)
        }
        walk(root)
        return out
    }

internal fun SubtitleTaskService.readScriptTextFile(path: String): String? {
        val bytes = readFileBytes(path) ?: return null
        return SubtitleScriptTextCodec.decode(bytes).let { text ->
            if (text.length > SubtitleTaskService.MAX_SCRIPT_FILE_CHARS) text.take(SubtitleTaskService.MAX_SCRIPT_FILE_CHARS) else text
        }
    }

internal fun SubtitleTaskService.extractPdfText(path: String): String? {
        val bytes = readFileBytes(path) ?: return null
        val text = runCatching {
            val document = PDDocument.load(bytes)
            try {
                PDFTextStripper().getText(document)
            } finally {
                document.close()
            }
        }.getOrNull()?.trim().orEmpty()
        if (text.isEmpty()) return null
        return if (text.length > SubtitleTaskService.MAX_SCRIPT_FILE_CHARS) text.take(SubtitleTaskService.MAX_SCRIPT_FILE_CHARS) else text
    }

internal fun SubtitleTaskService.readFileBytes(path: String): ByteArray? = when {
        path.startsWith("content://", ignoreCase = true) -> {
            val size = queryContentSize(path)
            if (size != null && size > SubtitleTaskService.MAX_SCRIPT_FILE_BYTES) {
                null
            } else {
                runCatching {
                    applicationContext.contentResolver.openInputStream(Uri.parse(path))?.use { it.readBytes() }
                }.getOrNull()
            }
        }
        else -> {
            val file = File(path)
            if (file.length() > SubtitleTaskService.MAX_SCRIPT_FILE_BYTES) {
                null
            } else {
                runCatching { file.readBytes() }.getOrNull()
            }
        }
    }

internal fun SubtitleTaskService.queryContentSize(path: String): Long? = runCatching {
        applicationContext.contentResolver.query(
            Uri.parse(path),
            arrayOf(android.provider.OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) cursor.getLong(index) else null
        }
    }.getOrNull()

    /**
     * 作品级最终润色：读取作品全部音轨的已翻译字幕，交给润色 agent 逐条精修
     * 中文文本后写回 subtitles 表。只精修 chinese，不改结构、不改日文。
     *
     * 非阻塞语义：本方法由独立后台 job 调用，任何异常都不影响已有字幕。
     *
     * @return 实际发生修改的字幕条数
     */
