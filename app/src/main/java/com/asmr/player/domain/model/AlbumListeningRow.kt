package com.asmr.player.domain.model

/** 单个作品的累计收听投影，用于"最常收听作品"排行（ListeningSessionDao.topAlbums）。 */
data class AlbumListeningRow(
    val albumId: Long,
    val rjCode: String,
    val title: String,
    val circle: String,
    val cv: String,
    val coverUrl: String,
    val coverPath: String,
    val coverThumbPath: String,
    val durationMs: Long,
    val sessionCount: Int
)
