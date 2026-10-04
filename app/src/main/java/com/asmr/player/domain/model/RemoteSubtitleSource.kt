package com.asmr.player.domain.model

/** 远端字幕源描述（Track 领域模型的组成部分；R3-B1e 自 util 下沉，使 domain.model 成为纯叶子包）。 */
data class RemoteSubtitleSource(
    val url: String,
    val language: String = "default",
    val ext: String
)
