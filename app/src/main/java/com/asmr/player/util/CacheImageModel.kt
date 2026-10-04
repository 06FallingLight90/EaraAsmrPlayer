package com.asmr.player.util

data class CacheImageModel(
    val data: Any,
    val headers: Map<String, String> = emptyMap(),
    val keyTag: String = ""
)

