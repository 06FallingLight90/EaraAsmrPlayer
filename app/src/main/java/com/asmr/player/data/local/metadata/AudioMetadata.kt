package com.asmr.player.data.local.metadata

/**
 * §8.3 本地音频元数据值对象；字段全可空 = 对应标签缺失。
 * 唯一调用点：扫描入库链（T3 接线）。
 */
data class AudioMetadata(
    val title: String?,
    val artist: String?,
    val album: String?,
    val embeddedCover: ByteArray?,
)
