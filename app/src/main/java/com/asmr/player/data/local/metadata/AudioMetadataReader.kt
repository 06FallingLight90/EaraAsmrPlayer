package com.asmr.player.data.local.metadata

import android.net.Uri

/** 本地音频元数据读取 seam：扫描走 SAF content Uri。读不到/异常返回 null，不抛。 */
interface AudioMetadataReader {
    fun read(uri: Uri): AudioMetadata?
}
