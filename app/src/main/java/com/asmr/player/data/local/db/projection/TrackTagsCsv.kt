package com.asmr.player.data.local.db.projection

/** C6-3（原 TrackTagDao.kt 内嵌投影归位）：某 track 在指定来源下的 tags CSV 投影。 */
data class TrackTagsCsv(
    val trackId: Long,
    val tagsCsv: String?
)
