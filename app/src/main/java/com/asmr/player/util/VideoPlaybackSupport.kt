package com.asmr.player.util

import androidx.media3.common.MediaItem

private val VideoPlaybackExtensions = setOf("mp4", "m4v", "webm", "mkv", "mov")

/** R3-B2 环1 消解支撑：视频播放判定（自 main/MainNavigationSupport.kt 迁入，main 与 ui.player 共用）。 */
internal fun isVideoPlaybackSource(
    uriText: String,
    mimeType: String,
    metadataFlag: Boolean
): Boolean {
    val fileExtension = uriText
        .substringBefore('#')
        .substringBefore('?')
        .substringAfterLast('.', "")
        .lowercase()
    return metadataFlag || mimeType.startsWith("video/") || fileExtension in VideoPlaybackExtensions
}

internal fun MediaItem?.isVideoPlaybackItem(): Boolean {
    val item = this ?: return false
    return isVideoPlaybackSource(
        uriText = item.localConfiguration?.uri?.toString().orEmpty(),
        mimeType = item.localConfiguration?.mimeType.orEmpty(),
        metadataFlag = item.mediaMetadata.extras?.getBoolean("is_video") == true
    )
}
