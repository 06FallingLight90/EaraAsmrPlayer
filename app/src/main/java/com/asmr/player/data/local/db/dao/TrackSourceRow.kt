package com.asmr.player.data.local.db.dao

/** T7 回填投影：存量曲目 path × 所属专辑来源（albums.source，可空 = 未定性）。 */
data class TrackSourceRow(
    val path: String,
    val source: String?
)
