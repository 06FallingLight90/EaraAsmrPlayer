package com.asmr.player.util

import androidx.media3.common.MediaItem

/** R3-B2 倒挂消解自 ui/player/PlayerMediaItemSupport.kt 迁入（hotlistening 与 ui.player 共用的纯判定）。 */
internal fun MediaItem?.isOnlineMedia(): Boolean {
    val item = this ?: return false
    val uri = item.localConfiguration?.uri?.toString().orEmpty().trim()
    val mediaId = item.mediaId.trim()
    return uri.startsWith("http://", ignoreCase = true) ||
        uri.startsWith("https://", ignoreCase = true) ||
        mediaId.startsWith("http://", ignoreCase = true) ||
        mediaId.startsWith("https://", ignoreCase = true)
}
