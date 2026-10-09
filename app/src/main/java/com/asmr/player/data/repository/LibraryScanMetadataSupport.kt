package com.asmr.player.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.asmr.player.data.local.db.entities.TrackEntity
import com.asmr.player.data.local.metadata.AudioMetadataReader
import java.io.File

/**
 * T3'：扫描入库的元数据/来源回填支持（单点可测，扫描管线两分支共用）。
 * - 来源回填：[resolveAlbumSource]——已有非空 source 保留（首管线定性），否则回填当前扫描管线来源
 *   （SAF 扫描根 → local_scan；下载目录管线含其 SAF 委托分支 → dlsite_download）。
 * - 元数据回填：[readForNewTrack] 是增量语义的**唯一闸门**——只允许对本次扫描新出现的 path 调用，
 *   已存在轨零 MediaMetadataRetriever 打开；读不到/reader 缺席一律 null，字段留 null（展示层文件名兜底）。
 * - 内嵌封面：[decodeEmbeddedCover] 解码 AudioMetadata.embeddedCover，进入既有封面回填链
 *   （触发条件仍是 coverPath 为空——文件夹图优先、内嵌兜底，现状保持）。
 * 行为契约见 docs/behavior-notes/scan-metadata-sourcing.md。
 */
internal class LibraryScanMetadataSupport(private val metadataReader: AudioMetadataReader?) {

    /** 扫描读得的音轨元数据投影（字段全可空 = 对应标签缺失）。 */
    data class ScannedTrackMetadata(
        val artist: String?,
        val albumTag: String?,
        val embeddedCover: ByteArray?,
    )

    /** 专辑来源单点解析：existingSource 非空保留；空白/null 才回填 pipelineSource。 */
    fun resolveAlbumSource(existingSource: String?, pipelineSource: String?): String? =
        existingSource?.takeIf { it.isNotBlank() } ?: pipelineSource

    /**
     * 新插音轨的元数据读取（增量语义单点闸门：仅对本次扫描新出现的 path 调用）。
     * path 为 File 绝对路径或 content:// Uri 串；返回 null = 标签缺失/reader 缺席/读取异常。
     */
    fun readForNewTrack(path: String): ScannedTrackMetadata? {
        val reader = metadataReader ?: return null
        val uri = pathToUri(path) ?: return null
        val meta = runCatching { reader.read(uri) }.getOrNull() ?: return null
        return ScannedTrackMetadata(
            artist = meta.artist?.trim()?.takeIf { it.isNotEmpty() },
            albumTag = meta.album?.trim()?.takeIf { it.isNotEmpty() },
            embeddedCover = meta.embeddedCover?.takeIf { it.isNotEmpty() },
        )
    }

    /** 由扫描规格构造新插轨实体：artist/albumTag 来自元数据，缺失留 null（不造默认值）。 */
    fun newTrackEntity(
        albumId: Long,
        title: String,
        path: String,
        group: String,
        metadata: ScannedTrackMetadata?,
    ): TrackEntity = TrackEntity(
        albumId = albumId,
        title = title,
        path = path,
        duration = 0.0,
        group = group,
        artist = metadata?.artist,
        albumTag = metadata?.albumTag,
    )

    /** 已存在轨的扫描更新实体：仅覆写 title/group，artist/albumTag 等既有字段一律不动。 */
    fun updatedTrackEntity(existing: TrackEntity, title: String, group: String): TrackEntity =
        existing.copy(title = title, group = group)

    /** 内嵌封面字节解码为 Bitmap；解码失败返回 null（调用方回退既有提取链）。 */
    fun decodeEmbeddedCover(bytes: ByteArray?): Bitmap? = bytes?.let {
        runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull()
    }

    private fun pathToUri(path: String): Uri? {
        val v = path.trim()
        if (v.isEmpty()) return null
        return if (v.startsWith("content://")) Uri.parse(v) else Uri.fromFile(File(v))
    }
}
