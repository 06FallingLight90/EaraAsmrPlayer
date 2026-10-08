package com.asmr.player.data.local.metadata

import android.media.MediaMetadataRetriever

/**
 * key→字段映射与兜底（纯函数，便于 JVM 单测）：
 * 文本 trim 后为空视为标签缺失，归一为 null；空封面字节数组视为缺失，归一为 null。
 */
object AudioMetadataMapping {

    fun fromExtracted(extracted: Map<Int, String?>, cover: ByteArray?): AudioMetadata {
        fun text(key: Int): String? = extracted[key]?.trim()?.takeIf { it.isNotEmpty() }
        return AudioMetadata(
            title = text(MediaMetadataRetriever.METADATA_KEY_TITLE),
            artist = text(MediaMetadataRetriever.METADATA_KEY_ARTIST),
            album = text(MediaMetadataRetriever.METADATA_KEY_ALBUM),
            embeddedCover = cover?.takeIf { it.isNotEmpty() },
        )
    }
}
