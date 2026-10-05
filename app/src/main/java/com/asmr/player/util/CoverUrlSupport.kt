package com.asmr.player.util

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

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

/**
 * R3-B5a：从 ui/library/CloudSyncSelectionDialog.kt 迁入 util
 * （消 ui→okhttp3 方向违规）。纯搬迁，日志摘要语义未改。
 */
internal fun summarizeCloudSyncCandidateCoverSource(label: String, url: String): String {
    val parsed = url.toHttpUrlOrNull()
    val summary = if (parsed != null) {
        "${parsed.host}${parsed.encodedPath}"
    } else {
        url.take(160)
    }
    return "$label:$summary"
}
