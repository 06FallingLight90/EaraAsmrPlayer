package com.asmr.player.domain.model

data class AlbumGroupTrackRow(
    val groupId: Long,
    val mediaId: String,
    val itemOrder: Int,
    val createdAt: Long,
    val trackId: Long,
    val albumId: Long,
    val trackTitle: String,
    val trackDuration: Double,
    val hasSubtitles: Boolean,
    val trackPath: String,
    val trackGroup: String,
    val albumTitle: String?,
    val albumCv: String?,
    val albumRjCode: String?,
    val albumWorkId: String?,
    val albumCoverThumbPath: String?,
    val albumCoverPath: String?,
    val albumCoverUrl: String?
)
