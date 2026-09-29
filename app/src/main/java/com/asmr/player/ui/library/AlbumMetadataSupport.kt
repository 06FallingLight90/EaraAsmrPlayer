package com.asmr.player.ui.library

import com.asmr.player.util.TagNormalizer

/**
 * LibraryViewModel 与 AlbumDetailViewModel 共享的专辑元数据纯函数。
 * 行为与两 VM 原有私有实现逐字一致，锁定测试见 AlbumMetadataSupportTest。
 */

/** 标签 CSV → 归一、去重后的空格分隔 token（用于 FTS 检索）。 */
internal fun buildTagsToken(tagsCsv: String): String {
    return tagsCsv.split(",")
        .map { TagNormalizer.normalize(it) }
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(" ")
}

/** 标签 CSV →（原样名, 归一名）列表，按归一形去重。 */
internal fun parseAlbumTags(tagsCsv: String): List<Pair<String, String>> {
    return tagsCsv.split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { it to TagNormalizer.normalize(it) }
        .filter { it.second.isNotBlank() }
        .distinctBy { it.second }
}

/** 封面 URL 是否大概率是占位图（非 http、无图占位、/0.jpg 等默认帧）。 */
internal fun isLikelyPlaceholderCover(url: String): Boolean {
    val s = url.trim().lowercase()
    if (!s.startsWith("http")) return true
    return s.contains("noimage") ||
        s.contains("no_image") ||
        s.contains("no-image") ||
        s.contains("placeholder") ||
        s.endsWith("/0.jpg") ||
        s.endsWith("/0.png")
}
