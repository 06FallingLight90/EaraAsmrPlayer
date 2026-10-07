package com.asmr.player.data.local.db.projection

/** C6-3（原 ListeningSessionDao.kt 内嵌投影归位）：[ListeningSessionDao.tagDurationTotals] 的投影——某 tags 快照对应的累计时长。 */
data class TagDurationRow(
    val tags: String,
    val durationMs: Long
)

/** 每小时时段的累计时长投影，用于时段偏好分析。 */
data class HourDurationRow(
    val hour: Int,
    val durationMs: Long
)
