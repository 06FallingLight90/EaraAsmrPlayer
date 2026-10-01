package com.asmr.player.data.local.db.entities

/**
 * 本地目录树扫描时对文件类型的分类；序列化进 [LocalTreeCacheEntity.payloadJson]
 * （gson 按枚举名持久化，改名会破坏存量缓存反序列化——legacyFileTypeToTreeFileType
 * 负责旧格式字符串到该枚举的映射）。
 */
internal enum class TreeFileType {
    Audio,
    Video,
    Image,
    Subtitle,
    Text,
    Pdf,
    Archive,
    Document,
    Spreadsheet,
    Presentation,
    Code,
    Ebook,
    Font,
    AppPackage,
    Other
}

/** 本地树索引的叶子缓存条目，与 [TreeFileType] 一同作为 LocalTreeCacheEntity 的 payload 模型。 */
internal data class LocalTreeLeafCacheEntry(
    val relativePath: String,
    val absolutePath: String,
    val fileType: TreeFileType,
    val sizeBytes: Long? = null
)
