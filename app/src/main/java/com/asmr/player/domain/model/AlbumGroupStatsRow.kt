package com.asmr.player.domain.model

data class AlbumGroupStatsRow(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val itemCount: Int,
    val albumCount: Int,
    val firstArtworkUri: String?
)
