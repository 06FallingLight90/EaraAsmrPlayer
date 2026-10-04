package com.asmr.player.util

import com.asmr.player.domain.model.RemoteSubtitleSource
import java.util.concurrent.ConcurrentHashMap

object OnlineLyricsStore {
    private val byMediaId = ConcurrentHashMap<String, List<RemoteSubtitleSource>>()

    fun replaceAll(map: Map<String, List<RemoteSubtitleSource>>) {
        byMediaId.clear()
        byMediaId.putAll(map)
    }

    fun set(mediaId: String, sources: List<RemoteSubtitleSource>) {
        if (mediaId.isBlank()) return
        byMediaId[mediaId] = sources
    }

    fun get(mediaId: String): List<RemoteSubtitleSource> {
        if (mediaId.isBlank()) return emptyList()
        return byMediaId[mediaId].orEmpty()
    }

    fun clear() {
        byMediaId.clear()
    }
}
