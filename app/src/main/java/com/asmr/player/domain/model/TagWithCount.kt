package com.asmr.player.domain.model

data class TagWithCount(
    val id: Long,
    val name: String,
    val nameNormalized: String,
    val albumCount: Long,
    val userAlbumCount: Long
)

