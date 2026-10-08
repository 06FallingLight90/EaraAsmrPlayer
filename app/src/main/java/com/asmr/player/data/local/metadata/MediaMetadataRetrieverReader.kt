package com.asmr.player.data.local.metadata

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * §8.3 实现：平台 MediaMetadataRetriever 提取 title/artist/album 与内嵌封面。
 * key→字段映射与兜底在 [AudioMetadataMapping]（纯函数），本类只做 retriever 调用与装配。
 * 整个 read 包 try/catch，读不到/异常一律返回 null，不抛。
 */
class MediaMetadataRetrieverReader @Inject constructor(
    @ApplicationContext private val context: Context,
) : AudioMetadataReader {

    override fun read(uri: Uri): AudioMetadata? {
        return try {
            RetrieverHandle().use { handle ->
                val retriever = handle.retriever
                retriever.setDataSource(context, uri)
                val extracted = mapOf(
                    MediaMetadataRetriever.METADATA_KEY_TITLE to retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                    MediaMetadataRetriever.METADATA_KEY_ARTIST to retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                    MediaMetadataRetriever.METADATA_KEY_ALBUM to retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                )
                AudioMetadataMapping.fromExtracted(extracted, retriever.embeddedPicture)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * minSdk 24 < 29：MediaMetadataRetriever 自 API 29 起才实现 AutoCloseable，
     * 对其直接 use{} 在低版本会因 invokeinterface 抛 ICCE；经此包装让 use{} 在全版本可用。
     */
    private class RetrieverHandle : AutoCloseable {
        val retriever = MediaMetadataRetriever()
        override fun close() {
            retriever.release()
        }
    }
}
