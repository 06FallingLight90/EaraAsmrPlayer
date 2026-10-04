package com.asmr.player.util

/** 音频输出路由分类（扬声器/耳机）；R3-B1 自 service 下沉为中立类型（ui/main/service 共用）。 */
enum class AudioOutputRouteKind {
    Speaker,
    Headphones
}
