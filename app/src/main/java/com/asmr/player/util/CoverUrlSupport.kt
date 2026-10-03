package com.asmr.player.util

/**
 * R2-C4b-3b：从 ui/library/AlbumMetadataSupport.kt 迁入 util（LibraryViewModel 与
 * data/repository 双方共用，放 neutral 包避免 ui→data.remote 方向违规）。纯搬迁。
 */

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
