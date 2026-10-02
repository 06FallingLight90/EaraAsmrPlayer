package com.asmr.player.data.local.library

import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.util.DlsiteWorkNo

/** 在线化专辑 path：web://rj/WORKKEY（原 LibraryViewModel private buildOnlineAlbumPath 逐字下沉）。 */
fun buildOnlineAlbumPath(entity: AlbumEntity): String? {
    val rj = DlsiteWorkNo.extractWorkNo(entity.rjCode.ifBlank { entity.workId }.ifBlank { entity.title })
    val workKey = rj.ifBlank { entity.workId.trim() }.ifBlank { entity.title.trim() }.trim()
    if (workKey.isBlank()) return null
    return "web://rj/${workKey.uppercase()}"
}
