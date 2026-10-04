package com.asmr.player.domain.model

data class PlaylistStatsRow(
    val id: Long,
    val name: String,
    val category: String,
    val createdAt: Long,
    val itemCount: Int,
    val firstArtworkUri: String?,
    val firstItemUri: String?
)
