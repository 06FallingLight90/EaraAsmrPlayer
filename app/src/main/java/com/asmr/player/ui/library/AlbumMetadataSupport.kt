package com.asmr.player.ui.library

/**
 * LibraryViewModel 与 AlbumDetailViewModel 共享的专辑元数据纯函数。
 * 行为与两 VM 原有私有实现逐字一致，锁定测试见 AlbumMetadataSupportTest。
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
